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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import at.jku.students.multimediasystemtextrecognition.filter.FilterFactory
import at.jku.students.multimediasystemtextrecognition.filter.FilterType
import at.jku.students.multimediasystemtextrecognition.filter.toFloatRange
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

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

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Displaying edge-to-edge
        //WindowCompat.setDecorFitsSystemWindows(window, false)


//        requestCameraPermission()
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

//                if (permissionGranted) {
//                    Log.i(LOG_TAG, "Showing camera")
//                    CameraView()
//                }
            }

        }
    }

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

@Composable
fun RecognitionUiRoot(viewModel: ImageRecognitionViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    RecognitionUi(uiState, viewModel::addFilter, viewModel::selectFilterToConfigure,
            viewModel::changeStrength, viewModel::removeFilter, viewModel::setSourceImage)
}

@Composable
fun RecognitionUi(
    uiState: ImageRecognitionUiState,
    onFilterAdd: (FilterType) -> Unit,
    onFilterSelected: (Int) -> Unit,
    onFilterStrengthChanged: (Int, Int) -> Unit,
    onFilterRemove: (Int) -> Unit,
    onImageSelected: (Bitmap) -> Unit
) {

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        FilterPicker(label = "Available:", onFilterSelected = {
            onFilterAdd(FilterType.values()[it])
        })
        FilterPicker(label = "Enabled:", onFilterSelected = onFilterSelected, enabledFilters = uiState.enabledFilters)
        if (uiState.hasFilterToConfigure) {
            val f = uiState.filterToConfigure!!
            SelectedFilterParameters(f.filter,
                onStrengthChange = {
                    onFilterStrengthChanged(f.index, it)
                },
                onRemove = {
                    onFilterRemove(f.index)
                }
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        ImagePicker(onImageSelected)
        Spacer(modifier = Modifier.height(12.dp))
        if (uiState.hasText) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = uiState.recognizedText,
                    color = Color.Black
                )
                if (uiState.loadingText) {
                    CircularProgressIndicator()
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (uiState.hasImage) {
            val alpha = if (uiState.loadingImage) { 0.5f  } else { 1f }
            Box(contentAlignment = Alignment.Center,
                modifier = Modifier
                    .background(Color.Black)) {
                Image(
                    bitmap = uiState.filteredImage!!.asImageBitmap(),
                    contentDescription = "",
                    Modifier.alpha(alpha)
                )
                if (uiState.loadingImage) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}



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
            enabledFilters.forEachIndexed { i, t ->
                IconButton(onClick = { onFilterSelected(i) }) {
                    Icon(
                        t.icon,
                        contentDescription = "${t.displayName} Filter"
                    )
                }
            }
        }
    }
}

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

@Composable
fun ImagePicker(setBitmap: (Bitmap) -> Unit) {
    var imageUri by remember {
        mutableStateOf<Uri?>(null)
    }
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract =
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        imageUri = uri

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
    Column {
        Button(onClick = {
            launcher.launch("image/*")
        }) {
            Text(text = "Pick image")
        }
    }
}


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