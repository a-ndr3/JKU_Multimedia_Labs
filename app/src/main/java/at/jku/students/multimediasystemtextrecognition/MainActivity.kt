package at.jku.students.multimediasystemtextrecognition

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import at.jku.students.multimediasystemtextrecognition.filter.FilterType
import at.jku.students.multimediasystemtextrecognition.filter.toFloatRange
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.max
import kotlin.math.min

const val LOG_TAG = "MMS"

class MainActivity : ComponentActivity() {

    var permissionGranted by mutableStateOf(false)

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.i(LOG_TAG, "Permission granted")
            permissionGranted = true
        } else {
            Log.i(LOG_TAG, "Permission denied")
        }
    }

    /**
     * First function called when activity is created
     */
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text("Multimedia")
                            })
                    }
                ) { contentPadding ->
                    Box(
                        modifier = Modifier
                            .padding(contentPadding)
                            .padding(18.dp)
                    ) { RecognitionUiRoot() }
                }
            }
        }
    }

    /**
     * Former function for requesting the camera permission for the app
     */
    private fun requestCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                Log.i(LOG_TAG, "Permission previously granted")
                permissionGranted = true
            }

            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.CAMERA
            ) -> Log.i(LOG_TAG, "Show camera permissions dialog")

            else -> requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
}

/**
 * Root component for the Recognition Ui
 */
@Composable
fun RecognitionUiRoot(viewModel: ImageRecognitionViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RecognitionUi(uiState, viewModel::addFilter, viewModel::selectFilterToConfigure,
            viewModel::changeStrength, viewModel::removeFilter, viewModel::setSourceImage,
        viewModel::setHomographySelection, viewModel::applyHomography, viewModel
    )
}

/**
 * Layout of the ui
 */
@Composable
fun RecognitionUi(
    uiState: RecognitionUiState,
    onFilterAdd: (FilterType) -> Unit,
    onFilterSelected: (Int) -> Unit,
    onFilterStrengthChanged: (Int, Int) -> Unit,
    onFilterRemove: (Int) -> Unit,
    onImageSelected: (Bitmap) -> Unit,
    setHomographySelection: (Boolean) -> Unit,
    applyHomography: (HomographySettings) -> Unit,
    viewModel: ImageRecognitionViewModel
) {
    val setHomographyButton = if (uiState.imageFilter is ImageFilterUiState.FiltersApplied || uiState.imageFilter is ImageFilterUiState.ImageLoaded)
        {{ setHomographySelection(true) }}
    else
        null

    val exportImage = if (uiState.imageFilter is ImageFilterUiState.FiltersApplied &&
            uiState.homography !is HomographyUiState.Selecting)
        {{ viewModel.saveImage() }}
    else
        null

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        if (uiState.homography !is HomographyUiState.Selecting) {
            FilterPicker(label = "Available:", onFilterSelected = {
                onFilterAdd(FilterType.values()[it])
            })
            FilterPicker(
                label = "Enabled:",
                onFilterSelected = onFilterSelected,
                enabledFilters = uiState.filterSettings.enabledFilters
            )
            FilterSettings(uiState.filterSettings, onFilterStrengthChanged, onFilterRemove)
            Spacer(modifier = Modifier.height(12.dp))
            ButtonRow(
                onImageSelected,
                onSetHomography = setHomographyButton,
                onExportImage = exportImage,
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextRecognitionResult(uiState.textRecognition)
            Spacer(modifier = Modifier.height(12.dp))
        }
        ImagePreview(uiState.imageFilter, uiState.homography) { settings ->
            if (settings == null) {
                setHomographySelection(false)
            } else {
                applyHomography(settings)
            }
        }
    }
}

/**
 * Ui for the specific filter settings
 */
@Composable
fun FilterSettings(
    uiState: FilterSettingsUiState,
    onFilterStrengthChanged: (Int, Int) -> Unit,
    onFilterRemove: (Int) -> Unit,
) {
    if (uiState !is FilterSettingsUiState.FilterSelected) return

    val f = uiState.filterToConfigure.filter
    val idx = uiState.filterToConfigure.index
    SelectedFilterParameters(f,
        onStrengthChange = {
            onFilterStrengthChanged(idx, it)
        },
        onRemove = {
            onFilterRemove(idx)
        }
    )
}

/**
 * Space for the recognized text
 */
@Composable
fun TextRecognitionResult(
    uiState: TextRecognitionUiState
) {
    when (uiState) {
        TextRecognitionUiState.Empty -> Unit
        is TextRecognitionUiState.ErrorOccurred ->
            Text(
                text = uiState.message,
                color = Color.Red
            )
        TextRecognitionUiState.Loading ->
            CircularProgressIndicator()
        is TextRecognitionUiState.Recognized ->
            Text(
                text = uiState.text,
                color = Color.Black
            )
    }
}

/**
 * Ui style and logic for showing the image in different ui states
 */
@Composable
fun ImagePreview(
    filterUiState: ImageFilterUiState,
    homographyUiState: HomographyUiState,
    onHomographyCompleted: (HomographySettings?) -> Unit
) {
    var homographySettings by remember {
        mutableStateOf<HomographySettings?>(null)
    }

    if (homographyUiState is HomographyUiState.Selected) {
        homographySettings = homographyUiState.settings
    }

    val defaultImageBox = @Composable { image: Bitmap ->
        val cornerHandleRadius = 18f
        val maxWidth = with(LocalConfiguration.current) { this.screenWidthDp * 0.7f }
        val maxHeight = with(LocalDensity.current) { 200.dp.toPx() }

        val imageRatio = image.width.toFloat() / image.height.toFloat()
        var onDisplayWidth: Dp
        var onDisplayHeight: Dp

        if (image.width > image.height) {
            onDisplayWidth = maxWidth.dp
            onDisplayHeight = onDisplayWidth / imageRatio
        } else {
            onDisplayHeight = maxHeight.dp
            onDisplayWidth = onDisplayHeight * imageRatio
        }

        if (onDisplayWidth > maxWidth.dp) {
            onDisplayWidth = maxWidth.dp
            onDisplayHeight = onDisplayWidth / imageRatio
        }
        if (onDisplayHeight > maxHeight.dp) {
            onDisplayHeight = maxHeight.dp
            onDisplayWidth = onDisplayHeight * imageRatio
        }

        val physicalWidth = with(LocalDensity.current) { onDisplayWidth.toPx() }
        val physicalHeight = with(LocalDensity.current) { onDisplayHeight.toPx() }

        val scale = min(physicalWidth / image.width, physicalHeight / image.height)

        homographySettings = HomographySettings.fromSize(physicalWidth, physicalHeight, scale)


        Box(contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(onDisplayHeight)
                .horizontalScroll(rememberScrollState())) {
            Canvas(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(onDisplayWidth)
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            homographySettings = homographySettings!!.updateNearest(change.position)
                        }
                    }
            ) {
                translate (0f, cornerHandleRadius) {
                    scale(scale, Offset.Zero) {
                        drawImage(image.asImageBitmap())
                    }

                    if (homographyUiState !is HomographyUiState.Selecting) return@Canvas
                    drawCircle(Color.Red, cornerHandleRadius, homographySettings!!.topLeft)
                    drawCircle(Color.Red, cornerHandleRadius, homographySettings!!.topRight)
                    drawCircle(Color.Red, cornerHandleRadius, homographySettings!!.bottomLeft)
                    drawCircle(Color.Red, cornerHandleRadius, homographySettings!!.bottomRight)
                    drawPath(homographySettings!!.path, Color.Red, style = Stroke(3.0f))
                }
            }
        }
    }

    when (filterUiState) {
        ImageFilterUiState.Empty -> Unit
        ImageFilterUiState.FilterApplicationFailed ->
            Text(
                text = "There was an error while applying the filters.",
                color = Color.Red
            )
        is ImageFilterUiState.FiltersApplied -> {
            defaultImageBox(
                if (homographyUiState is HomographyUiState.Selecting) {
                    // always show source image when re-selecting homography points
                    filterUiState.sourceImage
                } else {
                    filterUiState.filteredImage
                }
            )
        }
        is ImageFilterUiState.ImageLoaded -> {
            defaultImageBox(filterUiState.sourceImage)
        }
        is ImageFilterUiState.ImageLoading -> {

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .background(Color.Black)
            ) {
                filterUiState.oldImage?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "",
                        Modifier.alpha(0.5f)
                    )
                }
                CircularProgressIndicator()
            }
        }
    }

    if (homographyUiState is HomographyUiState.Selecting) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { onHomographyCompleted(homographySettings) }, ) {
                Text("Apply")
            }
            Spacer(Modifier.width(12.dp))
            TextButton(onClick = { onHomographyCompleted(null) }, ) {
                Text("Cancel")
            }
        }
    }
}

/**
 * Ui style for all available filters
 */
@Composable
@androidx.compose.ui.tooling.preview.Preview
fun FilterPicker(
    label: String = "Available:",
    onFilterSelected: (Int) -> Unit = {},
    enabledFilters: Array<FilterType> = FilterType.values(),
) {

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label)

        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            // designing each button with the settings of the filter type enum
            enabledFilters.forEachIndexed { i, t ->
                IconButton(onClick = { onFilterSelected(i) }) {
                    Icon(
                        Icons.Default.RadioButtonUnchecked,
                        contentDescription = "${t.displayName} Filter",
                        modifier = Modifier.size(32.dp),

                        )
                    Text(
                        t.text,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Ui for Filter Parameters
 */
@Composable
@androidx.compose.ui.tooling.preview.Preview
fun SelectedFilterParameters(
    selected: AppliedFilter = AppliedFilter(),
    onStrengthChange: (Int) -> Unit = {},
    onRemove: () -> Unit = {},
) {
    val displayName = selected.type.displayName
    val strength = selected.strength
    var sliderValue by remember {
        mutableStateOf(strength / 10f)
    }
    val sliderSteps = selected.type.strengthRange.last - 1
    val sliderRange = selected.type.strengthRange.toFloatRange()

    Column {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Editing: $displayName")
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, "remove")
            }
        }
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = strength.toString(),
            )
            Slider(value = sliderValue, onValueChange = {
                onStrengthChange((it * 10).toInt())
                sliderValue = it
            }, steps = sliderSteps, valueRange = sliderRange)
        }
    }
}

/**
  * Button Row for the action buttons next to each other
  */
@Composable
fun ButtonRow(
    setBitmap: (Bitmap) -> Unit,
    onSetHomography: (() -> Unit)?,
    onExportImage: (() -> Unit)?,
) {
    var imageUri by remember {
        mutableStateOf<Uri?>(null)
    }
    val context = LocalContext.current

    // setting up the launcher for picking an image of the gallery
    val launcher = rememberLauncherForActivityResult(
        contract =
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        imageUri = uri

        // differentiate between SDK Versions
        uri?.let {
            if (Build.VERSION.SDK_INT < 28) {
                setBitmap(
                    MediaStore.Images
                        .Media.getBitmap(context.contentResolver, it)
                )
            } else {
                val source = ImageDecoder
                    .createSource(context.contentResolver, it)
                setBitmap(ImageDecoder.decodeBitmap(source))
            }
        }
    }

    val smallPadding = PaddingValues(12.dp, 8.dp)
    val smallText = 12.sp

    Row(Modifier.horizontalScroll(rememberScrollState())) {
        Button(
            onClick = { launcher.launch("image/*") },
            contentPadding = smallPadding,
        ) {
            Text(text = "Pick image", fontSize = smallText)
        }

        if (onSetHomography != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onSetHomography,
                contentPadding = smallPadding,
            ) {
                Text(text = "Set edge points", fontSize = smallText)
            }
        }

        if (onExportImage != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onExportImage,
                contentPadding = smallPadding,
            ) {
                Text(text = "Export image", fontSize = smallText)
            }
        }
    }
}


// Not anymore used functions. These were used for testing live video feed for the camera
@Composable
fun CameraView() {
    val lensFacing = CameraSelector.LENS_FACING_BACK
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val preview = Preview.Builder().build()
    val previewView = remember { PreviewView(ctx) }
    val cameraSelector = CameraSelector.Builder()
        .requireLensFacing(lensFacing)
        .build()

    LaunchedEffect(lensFacing) {

        val cameraProvider = ctx.getCameraProvider()
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            preview
        )

        preview.setSurfaceProvider(previewView.surfaceProvider)
    }

    Box(contentAlignment = Alignment.BottomCenter) {
        AndroidView({ previewView })
    }

}

private suspend fun Context.getCameraProvider(): ProcessCameraProvider =
    suspendCoroutine { continuation ->
        ProcessCameraProvider.getInstance(this).also { cameraProvider ->
            cameraProvider.addListener({
                continuation.resume(cameraProvider.get())
            }, ContextCompat.getMainExecutor(this))
        }
    }