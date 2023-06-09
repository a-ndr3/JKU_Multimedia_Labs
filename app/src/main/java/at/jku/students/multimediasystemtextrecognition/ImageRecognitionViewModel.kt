package at.jku.students.multimediasystemtextrecognition

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.core.graphics.scale
import androidx.lifecycle.AndroidViewModel
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
import java.io.File
import java.io.File.separator
import java.io.FileOutputStream
import java.io.OutputStream
import java.lang.Float.max
import java.lang.Float.min
import java.time.LocalDateTime
import java.util.concurrent.ExecutionException
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
class ImageRecognitionViewModel(application: Application) : AndroidViewModel(application) {
    private val _filterSettingsState =
        MutableStateFlow<FilterSettingsUiState>(FilterSettingsUiState.FiltersEnabled())
    private val _imageFilterState =
        MutableStateFlow<ImageFilterUiState>(ImageFilterUiState.Empty)
    private val _textRecognitionState =
        MutableStateFlow<TextRecognitionUiState>(TextRecognitionUiState.Empty)
    private val _homographyState = MutableStateFlow<HomographyUiState>(HomographyUiState.NotShown)
    private val _requestFilterReload = MutableStateFlow(0)

    private val _appliedFilters = mutableListOf<AppliedFilter>()

    private var _sourceImage: Bitmap? = null
    private var _filteredImage: Bitmap? = null

    private var _homographySettings: HomographySettings? = null


    private var _processingJob: Job? = null

    // configures the default update behavior of each ui state
    init {
        viewModelScope.launch {
            // Only apply the filter if there is a specific timeout where nothing in the ui changes.
            // This prevents applying all filters upon every step of the strength slider
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

                _processingJob = viewModelScope.launch prj@{
                    _filteredImage = applyFilters()
                    if (_filteredImage == null) {
                        _imageFilterState.update {
                            ImageFilterUiState.FilterApplicationFailed
                        }
                        return@prj
                    } else {

                        _imageFilterState.update {
                            if (_appliedFilters.size > 0 || _homographyState.value is HomographyUiState.Selected) {
                                ImageFilterUiState.FiltersApplied(
                                    _sourceImage!!,
                                    _filteredImage!!,
                                )
                            } else {
                                ImageFilterUiState.ImageLoaded(
                                    _sourceImage!!,
                                )
                            }
                        }
                    }
                    val detectedText = runDetection(_filteredImage!!)
                    _textRecognitionState.update {
                        TextRecognitionUiState.Recognized(
                            detectedText,
                        )
                    }
                }
            }
        }
    }

    // combines all the different ui states to one uiState for easier handling
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

    /**
     * Sets the source image, which is picked with the "Pick image" button. Rescales the image to fit to the canvas
     */
    // TODO Images are sometimes out of bounds in the right screen edge, especially when using external images with a different resolution than screenshots
    fun setSourceImage(image: Bitmap) {
        Log.d("ViewModel", "set source image (${image.width} x ${image.height})")

        val factor = if (image.width > image.height) {
            1080f / image.width
        } else {
            1080f / image.height
        }
        Log.d("ViewModel", "factor $factor")

        _sourceImage = image.scale((image.width * factor).toInt(), (image.height * factor).toInt())

        Log.d("ViewModel", "set source image (${_sourceImage!!.width} x ${_sourceImage!!.height})")
        _imageFilterState.update {
            ImageFilterUiState.ImageLoaded(
                _sourceImage!!,
            )
        }
        _filteredImage = null
        _homographySettings = null
        _homographyState.update {
            HomographyUiState.NotShown
        }
        reapply()
    }

    /**
     * Request a filter reload
     */
    private fun reapply() {
        _requestFilterReload.update { it + 1 }
    }

    /**
     * Adds a filter from the Available to the Enabled list
     */
    fun addFilter(filter: FilterType) {
        val newIndex = _appliedFilters.size
        val newFilter = AppliedFilter(filter, filter.defaultStrength)
        _appliedFilters.add(newFilter)
        Log.d("ViewModel", "add filter")
        // update ui
        _filterSettingsState.update {
            FilterSettingsUiState.FilterSelected(
                _appliedFilters.toList(),
                FilterToConfigure(newIndex, newFilter)
            )
        }
        reapply()
    }

    /**
     * Selects an enabled filter to configure and updates the ui accordingly
     */
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

    /**
     * Change the filterStrength @param strength of specified filter with @param index.
     */
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

    /**
     * Removes a filter with a specific @param index
     */
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

    /**
     * Applies all enabled filters in the selected order with selected filter strengths
     */
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

    /**
     * Runs the actual text recognition by calling the Google ML Kit API
     */
    private suspend fun runDetection(image: Bitmap): String {
        Log.d("ViewModel", "run detection")
        return withContext(Dispatchers.IO) {
            // calling Google ML Kit API
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            val inp = InputImage.fromBitmap(image, 0)

            // await result from API and apply to the Ui
            try {
                val res = Tasks.await(recognizer.process(inp))
                return@withContext res.text
            } catch (e: ExecutionException) {
                Log.e(LOG_TAG, e.toString())
                return@withContext e.toString()
            }
        }
    }

    /**
     * Updating the @see HomographyUiState to select homography points
     */
    fun setHomographySelection(active: Boolean = true) {
        _homographyState.update {
            if (active) {
                HomographyUiState.Selecting
            } else {
                HomographyUiState.NotShown
            }
        }
    }

    /**
     * apply given homography settings and update the state with @param settings
     */
    fun applyHomography(settings: HomographySettings) {
        _homographySettings = settings
        _homographyState.update {
            HomographyUiState.Selected(settings)
        }
        reapply()
    }

    /**
     * Saves the filtered bitmap to the phones gallery. This task depend on the exact Android API,
     * however both ways are implemented.
     */
    fun saveImage() {
        val context = getApplication<Application>().applicationContext
        val folderName = context.applicationInfo
        val bitmap = _filteredImage
        var successWrite = false
        Log.e("SAVE", "SAving")
        if (Build.VERSION.SDK_INT >= 29) {
            val values = contentValues()    // set metadata
            // set path
            values.put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "Pictures/$folderName"
            )   // API >= 29 keeps track of the file name
            values.put(MediaStore.Images.Media.IS_PENDING, true)

            // resolve metadata values
            val uri: Uri? = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            )
            if (uri != null) {
                // write to device
                saveImageToStream(bitmap!!, context.contentResolver.openOutputStream(uri))
                values.put(MediaStore.Images.Media.IS_PENDING, false)
                context.contentResolver.update(uri, values, null, null)
                successWrite = true
            }
        } else {
            // set path
            val directory = File(
                Environment.getExternalStorageDirectory().toString() + separator + folderName
            )
            if (!directory.exists()) {
                directory.mkdirs()
            }
            // API < 29 needs a defined file name
            val fileName = "FilteredImage_${LocalDateTime.now()}"
            val file = File(directory, fileName)
            // write to device
            saveImageToStream(bitmap!!, FileOutputStream(file))
            val values = contentValues()
            values.put(MediaStore.Images.Media.DATA, file.absolutePath)
            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            successWrite = true
        }
        // inform the user if the export was successful
        if (successWrite) {
            Toast.makeText(context, "Image saved", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Image could not be saved", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Sets metadata values for Android to recognize the image
     */
    private fun contentValues(): ContentValues {
        val values = ContentValues()
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        values.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000);
        values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
        return values
    }

    /**
     * Helper function to compress and write the bitmap to the given outputStream
     */
    private fun saveImageToStream(bitmap: Bitmap, outputStream: OutputStream?) {
        if (outputStream != null) {
            try {
                // compress image and write to stream
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                outputStream.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

/**
 * All possible states the text recognition section can be in:
 */
data class RecognitionUiState(
    val filterSettings: FilterSettingsUiState = FilterSettingsUiState.FiltersEnabled(),
    val imageFilter: ImageFilterUiState = ImageFilterUiState.Empty,
    val textRecognition: TextRecognitionUiState = TextRecognitionUiState.Empty,
    val homography: HomographyUiState = HomographyUiState.NotShown,
)

/**
 * This interface declares all possible states the Filter Enabled section can be in.
 * The possible states are:
 * - FiltersEnabled: One or more filter is currently enabled
 * - FilterSelected: One or more filter is currently enabled and one is selected for changing settings
 */
sealed interface FilterSettingsUiState {
    val appliedFilters: List<AppliedFilter>

    data class FiltersEnabled(
        override val appliedFilters: List<AppliedFilter> = listOf(),
    ) : FilterSettingsUiState

    data class FilterSelected(
        override val appliedFilters: List<AppliedFilter> = listOf(),
        val filterToConfigure: FilterToConfigure = FilterToConfigure(),
    ) : FilterSettingsUiState

    // holds all enabled filters in an array and maps it to the filter type
    val enabledFilters: Array<FilterType>
        get() = appliedFilters.map { it.type }.toTypedArray()
}

/**
 * Declares the possible states of the homography ui. It is either:
 * - not shown: NotShown
 * or
 * - currently selecting points: Selecting
 * or
 * - applied: Selected with specific setting (e.g point position)
 */
sealed interface HomographyUiState {
    object NotShown : HomographyUiState

    object Selecting : HomographyUiState

    data class Selected(
        val settings: HomographySettings
    ) : HomographyUiState
}

/**
 * An interface declaring a state in which a loaded picture can be. The image can be
 * - not selected: empty
 * - selected, but still loading: ImageLoading
 * - selected and loaded: ImageLoaded
 * - selected and already filtered: FiltersApplied
 */
sealed interface ImageFilterUiState {
    object Empty : ImageFilterUiState


    // data classes for all states an image can be in
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

/**
 * Ui state only for the text recognition part. This is for example used to display
 * a loading indicator for the recognized text
 */
sealed interface TextRecognitionUiState {
    object Empty : TextRecognitionUiState   // no text recognized

    object Loading : TextRecognitionUiState // text is being recognized

    /**
     * Data for the actual recognized text
     */
    data class Recognized(
        val text: String
    ) : TextRecognitionUiState

    /**
     * Data for an error message received by Google ML Kit
     */
    data class ErrorOccurred(
        val message: String
    ) : TextRecognitionUiState
}

/**
 * Class for one specific enabled filter
 */
data class FilterToConfigure(
    val index: Int = 0,     // index of the filter in the enabled list
    val filter: AppliedFilter = AppliedFilter() // instance of the applied filter
)

/**
 * Class for one applied filter
 */
data class AppliedFilter(val type: FilterType = FilterType.BLACK_WHITE, var strength: Int = 50)

/**
 * default values for homography
 */
data class HomographySettings(
    val topLeft: Offset = Offset(0f, 0f),
    val topRight: Offset = Offset(100f, 0f),
    val bottomLeft: Offset = Offset(0f, 100f),
    val bottomRight: Offset = Offset(100f, 100f),
    val onDisplayWidth: Float = 100f,
    val onDisplayHeight: Float = 100f,
    val scaleToImage: Float = 1f
) {
    companion object {
        /**
         * Returns the homography settings (initial values) for given image dimensions
         */
        fun fromSize(onDisplayWidth: Float, onDisplayHeight: Float, scale: Float): HomographySettings {
            return HomographySettings(
                Offset(0f, 0f),
                Offset(onDisplayWidth, 0f),
                Offset(0f, onDisplayHeight),
                Offset(onDisplayWidth, onDisplayHeight),
                onDisplayWidth,
                onDisplayHeight,
                scale,
            )
        }
    }

    /**
     * Calculates the homography between the bitmap and the defined points.
     * Returns the transformed (cropped, rotated, tilted) image.
     */
    fun applyToBitmap(image: Bitmap): Bitmap {
        val m = Matrix()
        val newHeight =
            (max(bottomLeft.y - topLeft.y, bottomRight.y - topRight.y) / scaleToImage).toInt()
        val newWidth =
            (max(bottomRight.x - bottomLeft.x, topRight.x - topLeft.x) / scaleToImage).toInt()
        val newLeft = max(topLeft.x, bottomLeft.x) / scaleToImage
        val newTop = max(topLeft.y, topRight.y) / scaleToImage
        val originalPoints = arrayOf(
            newLeft, newTop, // top left x, y
            newWidth.toFloat(), newTop, // top right x, y
            newLeft, newHeight.toFloat(), // left bottom x, y
            newWidth.toFloat(), newHeight.toFloat() // right bottom x, y
        ).toFloatArray()
        var mappedPoints = arrayOf(
            topLeft.x, topLeft.y,
            topRight.x, topRight.y,
            bottomLeft.x, bottomLeft.y,
            bottomRight.x, bottomRight.y,
        ).toFloatArray()

        mappedPoints = mappedPoints.map { it / scaleToImage }.toFloatArray()

        m.setPolyToPoly(mappedPoints, 0, originalPoints, 0, 4)
        Log.d("HomographySettings", "applying $m")

        val morphed = Bitmap.createBitmap(image, 0, 0, image.width, image.height, m, true)

        val boundaries =  arrayOf(
            0f, 0f, // top left x, y
            image.width.toFloat(), 0f, // top right x, y
            0f, image.height.toFloat(), // left bottom x, y
            image.width.toFloat(), image.height.toFloat() // right bottom x, y
        ).toFloatArray()
        m.mapPoints(boundaries)

        val cropLeft = abs(boundaries[0] - boundaries[4])
        val cropTop = abs(boundaries[1] - boundaries[3])
        val cropRight = abs(boundaries[2] - boundaries[6])
        val cropBottom = abs(boundaries[5] - boundaries[7])

        return Bitmap.createBitmap(
            morphed,
            cropLeft.toInt(),
            cropTop.toInt(),
            (morphed.width - cropLeft - cropRight).toInt(),
            (morphed.height - cropTop - cropBottom).toInt(),
        )
    }

    /**
     * Update the nearest homography defining point to the click position
     */
    fun updateNearest(position: Offset): HomographySettings {
        val dragDistance = max(onDisplayWidth / 6f, onDisplayHeight / 6f)

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

    /**
     * Clamps the homography points to the image. This way the user can not drag
     * the points out of the image.
     */
    private fun clamp(position: Offset): Offset {
        var clamped = position
        if (position.x < 0) {
            clamped = clamped.copy(x = 0f)
        }
        if (position.y < 0) {
            clamped = clamped.copy(y = 0f)
        }
        // TODO clamping working not correctly after moving point topRight and botRigth
        if (position.x > onDisplayWidth) {
            clamped = clamped.copy(x = onDisplayWidth)
        }
        if (position.y > onDisplayHeight) {
            clamped = clamped.copy(y = onDisplayHeight)
        }
        return clamped
    }

    // Path lines between the points to form the desired rectangle
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

/**
 * Function to determine if a point is with a specific distance
 */
fun Offset.near(other: Offset, dragDistance: Float = 30f): Boolean {
    // for a triangle between one point and the other and calculate the hypotenuse to get the distance
    return hypot((x - other.x).toDouble(), (y - other.y).toDouble()) < dragDistance
}