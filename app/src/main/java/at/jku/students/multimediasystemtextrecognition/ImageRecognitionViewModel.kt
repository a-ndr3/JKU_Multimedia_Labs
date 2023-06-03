package at.jku.students.multimediasystemtextrecognition

import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.scale
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.jku.students.multimediasystemtextrecognition.filter.FilterFactory
import at.jku.students.multimediasystemtextrecognition.filter.FilterType
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutionException
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
class ImageRecognitionViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ImageRecognitionUiState())
    private val _requestFilterReload = MutableStateFlow(0)

    private val _appliedFilters = mutableListOf<AppliedFilter>()
    private var _sourceImage: Bitmap? = null

    private var _processingJob: Job? = null

    init {
        viewModelScope.launch {
            _requestFilterReload.debounce {
                if (_processingJob?.isActive == true) {
                    250.milliseconds
                } else {
                    0.milliseconds
                }
            }.collect {
                Log.d("ViewModel", "collect filter requests")
                _processingJob?.let {
                    if (it.isActive) {
                        it.cancel()
                        Log.d("ViewModel", "canceled previous execution")
                    }
                }
                _uiState.update {
                    it.copy(
                        loadingImage = true,
                        loadingText = true,
                    )
                }

                _processingJob = viewModelScope.launch {
                    applyFilters()
                    runDetection()
                }
            }
        }
    }

    val uiState = _uiState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ImageRecognitionUiState()
    )

    fun setSourceImage(image: Bitmap) {
        Log.d("ViewModel", "set source image (${image.width} x ${image.height})")
        _sourceImage = if (image.width > 1080 || image.height > 1080) {
            val factor = if(image.width > image.height) { 1080f / image.width} else { 1080f / image.height }
            Log.d("ViewModel", "factor $factor")
            image.scale((image.width * factor).toInt(), (image.height * factor).toInt())
        } else { image }

        Log.d("ViewModel", "set source image (${_sourceImage!!.width} x ${_sourceImage!!.height})")
        _uiState.update {
            it.copy(
                filteredImage = _sourceImage,
            )
        }
        requestFilters()
    }

    private fun requestFilters() {
        _requestFilterReload.update { it + 1 }
    }

    fun addFilter(filter: FilterType) {
        _appliedFilters.add(AppliedFilter(filter, filter.defaultStrength))
        Log.d("ViewModel", "add filter")
        _uiState.update {
            it.copy(
                appliedFilters = _appliedFilters.toList()
            )
        }
        requestFilters()
    }

    fun selectFilterToConfigure(index: Int) {
        assert(index in 0 until _appliedFilters.size)
        Log.d("ViewModel", "select filter to configure idx $index")
        _uiState.update {
            it.copy(
                filterToConfigure = FilterToConfigure(index, _appliedFilters[index])
            )
        }
    }

    fun changeStrength(index: Int, strength: Int) {
        assert(index in 0 until _appliedFilters.size)
        Log.d("ViewModel", "change strength to $strength at idx $index")
        _appliedFilters[index].strength = strength
        _uiState.update {
            it.copy(
                appliedFilters = _appliedFilters.toList()
            )
        }
        requestFilters()
    }

    fun removeFilter(index: Int) {
        _appliedFilters.removeAt(index)
        Log.d("ViewModel", "remove filter $index")
        _uiState.update {
            it.copy(
                appliedFilters = _appliedFilters.toList(),
                filterToConfigure = null
            )
        }
        requestFilters()
    }

    private suspend fun applyFilters() {
        Log.d("ViewModel", "apply filters ${_sourceImage == null}")
        if (_sourceImage == null) return
        val filteredImage = withContext(Dispatchers.IO) {
            var fb = _sourceImage!!.copy(Bitmap.Config.RGBA_F16, true)

            Log.d("FilterApply", "applying filters now...")
            _appliedFilters.forEach {
                if (!isActive) return@withContext null
                val filter = FilterFactory.createFilter(it.type)
                fb = filter.apply(fb, it.strength)
            }
            Log.d("FilterApply", "finished")

            return@withContext fb
        }
        filteredImage?.let {image ->
            _uiState.update {
                it.copy(
                    filteredImage = image,
                    loadingImage = false,
                )
            }
        }
    }

    private suspend fun runDetection() {
        Log.d("ViewModel", "run detection ${_uiState.value.filteredImage == null}")
        if (_uiState.value.filteredImage == null) return
        val detectedText = withContext(Dispatchers.IO) {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            val inp = InputImage.fromBitmap(_uiState.value.filteredImage!!, 0)

            try {
                val res = Tasks.await(recognizer.process(inp))
                return@withContext res.text
            } catch (e: ExecutionException) {
                Log.e(LOG_TAG, e.toString())
                return@withContext e.toString()
            }
        }
        _uiState.update {
            it.copy(
                recognizedText = detectedText,
                loadingText = false,
            )
        }
    }
}

data class ImageRecognitionUiState(
    val filteredImage: Bitmap? = null,
    val recognizedText: String = "",
    val appliedFilters: List<AppliedFilter> = listOf(),
    val filterToConfigure: FilterToConfigure? = null,
    val loadingImage: Boolean = false,
    val loadingText: Boolean = false
) {
    val hasText: Boolean
        get() = recognizedText != ""

    val enabledFilters: Array<FilterType>
        get() = appliedFilters.map { it.type }.toTypedArray()

    val hasFilterToConfigure: Boolean
        get() = filterToConfigure != null

    val hasImage: Boolean
        get() = filteredImage != null
}

data class FilterToConfigure(
    val index: Int = 0,
    val filter: AppliedFilter = AppliedFilter()
)

data class AppliedFilter(val type: FilterType = FilterType.BLACK_WHITE, var strength: Int = 50)