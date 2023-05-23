package at.jku.students.multimediasystemtextrecognition

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Displaying edge-to-edge
        //WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            MaterialTheme {
                Welcome()
            }

        }
    }
}

@Composable
@Preview
fun Welcome() {
    var detectedText by remember { mutableStateOf("") }

    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    val img = ImageBitmap.imageResource(id = R.drawable.test)

    val inp = InputImage.fromBitmap(img.asAndroidBitmap(), 0)

    recognizer.process(inp)
        .addOnSuccessListener {
            detectedText = it.text
        }
        .addOnFailureListener{
            print(it.message)
        }

    Column {
        Text(
            text = "Hello World! $detectedText",
            color = Color.Black
        )
        Image(
            painter = painterResource(R.drawable.test),
            contentDescription = ""
        )
    }
}