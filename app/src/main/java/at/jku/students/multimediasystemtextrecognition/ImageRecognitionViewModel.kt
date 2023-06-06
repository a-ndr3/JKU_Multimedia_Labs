package at.jku.students.multimediasystemtextrecognition

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
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
import java.time.LocalDateTime
import java.util.concurrent.ExecutionException
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
class ImageRecognitionViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(ImageRecognitionUiState())  // current ui state
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

    // init the ui state
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

    /**
     * Adds a filter from to available list to the selected list
     */
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

    /**
     * Selects the filter to update the UI to set the filter's settings
     */
    fun selectFilterToConfigure(index: Int) {
        assert(index in 0 until _appliedFilters.size)
        Log.d("ViewModel", "select filter to configure idx $index")
        _uiState.update {
            it.copy(
                filterToConfigure = FilterToConfigure(index, _appliedFilters[index])
            )
        }
    }

    /**
     * Changes the corresponding filter strength of the selected filter
     * @param index Index of the filter in the selected list
     * @param strength Corresponding strength value of a filter. The value range is individual to each filter
     */
    fun changeStrength(index: Int, strength: Int) {
        assert(index in 0 until _appliedFilters.size)
        Log.d("ViewModel", "change strength to $strength at idx $index")
        // apply settings
        _appliedFilters[index].strength = strength
        _uiState.update {
            it.copy(
                appliedFilters = _appliedFilters.toList()
            )
        }
        requestFilters()
    }

    /**
     * Removes the filter and updates the UI.
     */
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

    /**
     * Applies the filters to the chosen image. This method applies all filters in their designated order,
     * each time a new filter is chose, removed or changed
     */
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

    /**
     * Runs the actual text recognition by calling the Google ML Kit api.
     */
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


    /**
     * Saves the filtered bitmap to the phones gallery. This task depend on the exact Android API,
     * however both ways are implemented.
     */
    fun saveImage() {
        val context = getApplication<Application>().applicationContext
        val folderName = context.applicationInfo
        val bitmap = uiState.value.filteredImage
        var successWrite = false
        if (bitmap != null) {
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
                    saveImageToStream(bitmap, context.contentResolver.openOutputStream(uri))
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
                saveImageToStream(bitmap, FileOutputStream(file))
                val values = contentValues()
                values.put(MediaStore.Images.Media.DATA, file.absolutePath)
                context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                successWrite = true
            }
            // inform the user if the export was successful
            if (successWrite) {
                Toast.makeText(context, "Image saved", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context,"Image could not be saved", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Sets metadata values for Android to recognize the image
     */
    private fun contentValues() : ContentValues {
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
 * Class for the current UiState of the app. This data class stores all the
 * relevant object the UI keeps track of.
 */
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