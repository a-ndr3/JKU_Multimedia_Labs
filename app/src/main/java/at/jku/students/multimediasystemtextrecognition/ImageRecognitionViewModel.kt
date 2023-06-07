package at.jku.students.multimediasystemtextrecognition

import android.graphics.Bitmap
import android.graphics.Matrix
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.Float.max
import java.lang.Float.min
import java.util.concurrent.ExecutionException
import kotlin.math.hypot
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
    private var _homographySettings: HomographySettings? = null

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
                                _sourceImage!!,
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
        reapply()
    }

    private fun reapply() {
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
        reapply()
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
        reapply()
    }

    fun removeFilter(index: Int) {
        _appliedFilters.removeAt(index)
        Log.d("ViewModel", "remove filter $index")
        _filterSettingsState.update {
            FilterSettingsUiState.FiltersEnabled(
                appliedFilters = _appliedFilters.toList(),
            )
        }
        reapply()
    }

    private suspend fun applyFilters(): Bitmap? {
        Log.d("ViewModel", "apply filters ${_sourceImage == null}")
        if (_sourceImage == null) return null
        return withContext(Dispatchers.IO) {
            var fb = _sourceImage!!.copy(Bitmap.Config.RGBA_F16, true)

            _homographySettings?.let {
                Log.d("FilterApply", "applying homography now... ${it.topLeft}")
                fb = it.applyToBitmap(fb)
            }

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

    fun setHomographySelection(active: Boolean = true) {
        _homographyState.update {
            if (active) {
                HomographyUiState.Selecting
            } else {
                HomographyUiState.NotShown
            }
        }
    }

    fun applyHomography(settings: HomographySettings) {
        _homographySettings = settings
        _homographyState.update {
            HomographyUiState.Selected(settings)
        }
        reapply()
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

    object Selecting : HomographyUiState

    data class Selected(
        val settings: HomographySettings
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
        val sourceImage: Bitmap,
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
    val onDisplayWidth: Float = 100f,
    val onDisplayHeight: Float = 100f,
    val scaleToImage: Float = 1f
) {
    companion object {
        fun fromSize(imageWidth: Float, imageHeight: Float, scale: Float) : HomographySettings {
            return HomographySettings(
                Offset(0f, 0f),
                Offset(imageWidth * scale, 0f),
                Offset(0f, imageHeight * scale),
                Offset(imageWidth * scale, imageHeight * scale),
                imageWidth,
                imageHeight,
                scale,
            )
        }
    }

    fun applyToBitmap(image: Bitmap) : Bitmap {
        val m = Matrix()
        val originalPoints = arrayOf(
            0f, 0f, // top left x, y
            image.width.toFloat(), 0f, // top right x, y
            0f, image.height.toFloat(), // left bottom x, y
            image.width.toFloat(), image.height.toFloat() // right bottom x, y
        ).toFloatArray()
        val mappedPoints = arrayOf(
            topLeft.x, -topLeft.y,
            topRight.x, topRight.y,
            bottomLeft.x, bottomLeft.y,
            bottomRight.x, bottomRight.y,
        ).toFloatArray()

        m.setPolyToPoly(originalPoints, 0, mappedPoints, 0, 4)
        Log.d("HomographySettings", "applying $m")

        val newHeight = (max(bottomLeft.y - topLeft.y, bottomRight.y - topRight.y) / scaleToImage).toInt()
        val newWidth = (max(bottomRight.x - bottomLeft.x, topRight.x - topLeft.x) / scaleToImage).toInt()
        val morphed = Bitmap.createBitmap(image, 0, 0, image.width, image.height, m, true)
        return Bitmap.createBitmap(morphed, (topLeft.x / scaleToImage).toInt(), (topLeft.y / scaleToImage).toInt(), image.width, image.height)

    }

    fun updateNearest(position: Offset) : HomographySettings {
        val dragDistance = max(onDisplayWidth / 12f, onDisplayHeight / 12f)

        return if (topLeft.near(position, dragDistance)) {
            copy(topLeft = clamp(position))
        } else if (topRight.near(position, dragDistance)) {
            copy(topRight = clamp(position))
        } else if (bottomLeft.near(position, dragDistance)) {
            copy(bottomLeft = clamp(position))
        } else if (bottomRight.near(position, dragDistance)) {
            copy(bottomRight = clamp(position))
        } else {
            this
        }
    }

    private fun clamp(position: Offset) : Offset {
        var clamped = position
        if (position.x < 0) {
            clamped = clamped.copy(x = 0f)
        }
        if (position.y < 0) {
            clamped = clamped.copy(y = 0f)
        }
        if (position.x > onDisplayWidth) {
            clamped = clamped.copy(x = onDisplayWidth)
        }
        if (position.y > onDisplayHeight) {
            clamped = clamped.copy(y = onDisplayHeight)
        }
        return clamped
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

fun Offset.near(other: Offset, dragDistance: Float = 30f) : Boolean {
    return hypot((x - other.x).toDouble(), (y - other.y).toDouble()) < dragDistance
}