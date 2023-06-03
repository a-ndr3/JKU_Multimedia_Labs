package at.jku.students.multimediasystemtextrecognition

import android.Manifest
import android.annotation.SuppressLint
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
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material3.Button
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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import at.jku.students.multimediasystemtextrecognition.filter.FilterType
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.time.format.TextStyle
import java.util.logging.Filter
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.roundToInt

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
                    Box(modifier = Modifier
                        .padding(contentPadding)
                        .padding(18.dp)) { Welcome() }
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

@SuppressLint("MutableCollectionMutableState")
@Composable
fun Welcome() {

    val bitmap = remember {
        mutableStateOf<Bitmap?>(null)
    }

    var detectedText by remember { mutableStateOf("") }

    if (bitmap.value != null) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        val inp = InputImage.fromBitmap(bitmap.value!!, 0)

        recognizer.process(inp)
            .addOnSuccessListener {
                detectedText = it.text
            }
            .addOnFailureListener {
                Log.e(LOG_TAG, it.toString())
                detectedText = it.toString()
            }
    }



    var selectedFilters = remember {
        mutableStateListOf<FilterType>()
    }
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        FilterPicker(label = "Available:", onFilterSelected = {
            selectedFilters.add(it)
        })
        FilterPicker(label = "Selected:", onFilterSelected = {}, enabledFilters = selectedFilters.toTypedArray())
        SelectedFilterParameters()
        ImagePicker(bitmap = bitmap)
        if (bitmap.value != null) {
            Box {
                Image(
                    bitmap = bitmap.value!!.asImageBitmap(),
                    contentDescription = ""
                )
            }
            Text(
                text = detectedText,
                color = Color.Black
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
@androidx.compose.ui.tooling.preview.Preview
fun FilterPicker(
    label: String = "Available:",
    onFilterSelected: (FilterType) -> Unit = {},
    enabledFilters: Array<FilterType> = FilterType.values(),
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label)

        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            for (t in FilterType.values()) {
                if (t in enabledFilters) {
                    IconButton(onClick = { onFilterSelected(FilterType.BINARY) }) {
                        Icon(
                            t.icon,
                            contentDescription = "${t.displayName} Filter"
                        )
                    }
                }
            }
        }
    }
}

@Composable
@androidx.compose.ui.tooling.preview.Preview
fun SelectedFilterParameters(
    selected: FilterType = FilterType.BLACK_WHITE,
    onStrengthChange: (Int) -> Unit = {},
    onRemove: () -> Unit = {},
) {
    Column {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Selected: ${selected.displayName}")
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, "remove")
            }
        }
        var sliderPosition by remember { mutableStateOf(0.5f) }

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = (sliderPosition * 10).toInt().toString(),
            )
            Slider(value = sliderPosition, onValueChange = {
                sliderPosition = it
                onStrengthChange(it.toInt() * 10)
            }, steps = 11)
        }
    }
}

@Composable
fun ImagePicker(bitmap: MutableState<Bitmap?>) {
    var imageUri by remember {
        mutableStateOf<Uri?>(null)
    }
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract =
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        imageUri = uri
    }
    Column() {
        Button(onClick = {
            launcher.launch("image/*")
        }) {
            Text(text = "Pick image")
        }

        Spacer(modifier = Modifier.height(12.dp))

        imageUri?.let {
            if (Build.VERSION.SDK_INT < 28) {
                bitmap.value = MediaStore.Images
                    .Media.getBitmap(context.contentResolver, it)

            } else {
                val source = ImageDecoder
                    .createSource(context.contentResolver, it)
                bitmap.value = ImageDecoder.decodeBitmap(source)
            }
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