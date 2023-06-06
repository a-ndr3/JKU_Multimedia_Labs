package at.jku.students.multimediasystemtextrecognition

import android.graphics.Bitmap
import android.graphics.Point
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    private val _filterSettingsState =
        MutableStateFlow<FilterSettingsUiState>(FilterSettingsUiState.FiltersEnabled())
    private val _imageFilterState =
        MutableStateFlow<ImageFilterUiState>(ImageFilterUiState.Empty)
    private val _textRecognitionState = MutableStateFlow<TextRecognitionUiState>(TextRecognitionUiState.Empty)
    private val _homographyState = MutableStateFlow<HomographyUiState>(HomographyUiState.NotShown)
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

                if (_imageFilterState.value == ImageFilterUiState.Empty) return@collect

                _processingJob?.let {
                    if (it.isActive) {
                        it.cancel()
                        Log.d("ViewModel", "canceled previous execution")
                    }
                }
                _imageFilterState.update {
                    when (it) {
                        is ImageFilterUiState.ImageLoaded ->
                            ImageFilterUiState.ImageLoading(it.sourceImage)
                        is ImageFilterUiState.FiltersApplied ->
                            ImageFilterUiState.ImageLoading(it.filteredImage)
                        is ImageFilterUiState.ImageLoading ->
                            ImageFilterUiState.ImageLoading(it.oldImage)
                        else ->
                            ImageFilterUiState.ImageLoading()
                    }
                }
                _textRecognitionState.update {
                    TextRecognitionUiState.Loading
                }

                _processingJob =  viewModelScope.launch prj@ {
                    val filteredImage = applyFilters()
                    if (filteredImage == null) {
                        _imageFilterState.update {
                            ImageFilterUiState.FilterApplicationFailed
                        }
                        return@prj
                    } else {
                        _imageFilterState.update {
                            ImageFilterUiState.FiltersApplied(
                                filteredImage,
                            )
                        }
                    }
                    val detectedText = runDetection(filteredImage)
                    _textRecognitionState.update {
                        TextRecognitionUiState.Recognized(
                            detectedText,
                        )
                    }
                }
            }
        }
    }

    val uiState = combine(
        _imageFilterState,
        _textRecognitionState,
        _homographyState,
        _filterSettingsState
    ) { imageFilter, recognition, homography, filterSettings ->
        RecognitionUiState(
            filterSettings,
            imageFilter,
            recognition,
            homography
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RecognitionUiState()
    )

    fun setSourceImage(image: Bitmap) {
        Log.d("ViewModel", "set source image (${image.width} x ${image.height})")
        _sourceImage = if (image.width > 1080 || image.height > 1080) {
            val factor = if (image.width > image.height) {
                1080f / image.width
            } else {
                1080f / image.height
            }
            Log.d("ViewModel", "factor $factor")
            image.scale((image.width * factor).toInt(), (image.height * factor).toInt())
        } else {
            image
        }

        Log.d("ViewModel", "set source image (${_sourceImage!!.width} x ${_sourceImage!!.height})")
        _imageFilterState.update {
            ImageFilterUiState.ImageLoaded(
                _sourceImage!!,
            )
        }
        requestFilters()
    }

    private fun requestFilters() {
        _requestFilterReload.update { it + 1 }
    }

    fun addFilter(filter: FilterType) {
        val newIndex = _appliedFilters.size
        val newFilter = AppliedFilter(filter, filter.defaultStrength)
        _appliedFilters.add(newFilter)
        Log.d("ViewModel", "add filter")
        _filterSettingsState.update {
            FilterSettingsUiState.FilterSelected(
                _appliedFilters.toList(),
                FilterToConfigure(newIndex, newFilter)
            )
        }
        requestFilters()
    }

    fun selectFilterToConfigure(index: Int) {
        assert(index in 0 until _appliedFilters.size)
        Log.d("ViewModel", "select filter to configure idx $index")
        _filterSettingsState.update {
            FilterSettingsUiState.FilterSelected(
                _appliedFilters.toList(),
                FilterToConfigure(index, _appliedFilters[index])
            )
        }
    }

    fun changeStrength(index: Int, strength: Int) {
        assert(index in 0 until _appliedFilters.size)
        Log.d("ViewModel", "change strength to $strength at idx $index")
        _appliedFilters[index].strength = strength
        _filterSettingsState.update {
            when (it) {
                is FilterSettingsUiState.FilterSelected ->
                    it.copy(
                        appliedFilters = _appliedFilters.toList()
                    )

                is FilterSettingsUiState.FiltersEnabled ->
                    it.copy(
                        appliedFilters = _appliedFilters.toList()
                    )
            }
        }
        requestFilters()
    }

    fun removeFilter(index: Int) {
        _appliedFilters.removeAt(index)
        Log.d("ViewModel", "remove filter $index")
        _filterSettingsState.update {
            FilterSettingsUiState.FiltersEnabled(
                appliedFilters = _appliedFilters.toList(),
            )
        }
        requestFilters()
    }

    private suspend fun applyFilters(): Bitmap? {
        Log.d("ViewModel", "apply filters ${_sourceImage == null}")
        if (_sourceImage == null) return null
        return withContext(Dispatchers.IO) {
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
    }

    private suspend fun runDetection(image: Bitmap) : String {
        Log.d("ViewModel", "run detection")
        return withContext(Dispatchers.IO) {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            val inp = InputImage.fromBitmap(image, 0)

            try {
                val res = Tasks.await(recognizer.process(inp))
                return@withContext res.text
            } catch (e: ExecutionException) {
                Log.e(LOG_TAG, e.toString())
                return@withContext e.toString()
            }
        }
    }
}

data class RecognitionUiState(
    val filterSettings: FilterSettingsUiState = FilterSettingsUiState.FiltersEnabled(),
    val imageFilter: ImageFilterUiState = ImageFilterUiState.Empty,
    val textRecognition: TextRecognitionUiState = TextRecognitionUiState.Empty,
    val homography: HomographyUiState = HomographyUiState.NotShown,
)

sealed interface FilterSettingsUiState {
    val appliedFilters: List<AppliedFilter>

    data class FiltersEnabled(
        override val appliedFilters: List<AppliedFilter> = listOf(),
    ) : FilterSettingsUiState

    data class FilterSelected(
        override val appliedFilters: List<AppliedFilter> = listOf(),
        val filterToConfigure: FilterToConfigure = FilterToConfigure(),
    ) : FilterSettingsUiState

    val enabledFilters: Array<FilterType>
        get() = appliedFilters.map { it.type }.toTypedArray()
}

sealed interface HomographyUiState {
    object NotShown : HomographyUiState

    data class Selecting(
        val points: List<Point>
    ) : HomographyUiState
}

sealed interface ImageFilterUiState {
    object Empty : ImageFilterUiState

    data class ImageLoading(
        val oldImage: Bitmap? = null,
    ) : ImageFilterUiState

    data class ImageLoaded(
        val sourceImage: Bitmap,
    ) : ImageFilterUiState

    data class FiltersApplied(
        val filteredImage: Bitmap,
    ) : ImageFilterUiState

    object FilterApplicationFailed : ImageFilterUiState
}

sealed interface TextRecognitionUiState {
    object Empty : TextRecognitionUiState

    object Loading : TextRecognitionUiState

    data class Recognized (
        val text: String
    ) : TextRecognitionUiState

    data class ErrorOccurred (
        val message: String
    ) : TextRecognitionUiState
}

data class FilterToConfigure(
    val index: Int = 0,
    val filter: AppliedFilter = AppliedFilter()
)

data class AppliedFilter(val type: FilterType = FilterType.BLACK_WHITE, var strength: Int = 50)

data class HomographySettings(
    val topLeft: Offset = Offset(0f, 0f),
    val topRight: Offset = Offset(100f, 0f),
    val bottomLeft: Offset= Offset(0f, 100f),
    val bottomRight: Offset= Offset(100f, 100f),
) {
    companion object {
        fun fromSize(width: Float, height: Float) : HomographySettings {
            return HomographySettings(
                Offset(0f, 0f),
                Offset(width, 0f),
                Offset(0f, height),
                Offset(width, height),
            )
        }
    }

    val path: Path
        get() {
            val path = Path()
            path.moveTo(topLeft.x, topLeft.y)
            path.lineTo(topRight.x, topRight.y)
            path.lineTo(bottomRight.x, bottomRight.y)
            path.lineTo(bottomLeft.x, bottomLeft.y)
            path.close()
            return path
        }
}