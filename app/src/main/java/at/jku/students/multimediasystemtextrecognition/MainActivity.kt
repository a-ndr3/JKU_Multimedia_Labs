package at.jku.students.multimediasystemtextrecognition

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import at.jku.students.multimediasystemtextrecognition.filter.FilterTypes
import at.jku.students.multimediasystemtextrecognition.filter.Filters.FilterFactory

// each filter gets applied on the original image
var originalImage: Bitmap? = null

class MainActivity : AppCompatActivity() {

    var image : ImageView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout)

        val imageView = findViewById<ImageView>(R.id.img)
        image = imageView

        imageView.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, 1)
        }
    }


    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 1 && resultCode == RESULT_OK && data != null) {
            val imageUri: Uri? = data.data
            image!!.setImageURI(imageUri)
            originalImage = (image!!.drawable as BitmapDrawable).bitmap

            recognizeText()
        }
    }

    // create menu
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.filter_menu, menu)
        return true
    }

    // applies a selected filter on the original bitmap
    // TODO user input option to set filter strength
    // TODO implement generic way to apply a filter. Right now it looks disgusting
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        var filteredBitmap : Bitmap? = null
        if (image == null) {
            // TODO alert the user if no image is selected
            Log.i("INFO", "No image selected")
        } else {
            val filter = FilterFactory()

            when (item.itemId) {
                R.id.brightHSV -> {
                    filteredBitmap =
                        filter.createFilter(FilterTypes.BRIGHTNESS_HSV).apply(originalImage!!, 10)
                    image!!.setImageBitmap(filteredBitmap)
                }

                R.id.bw -> {
                    filteredBitmap =
                        filter.createFilter(FilterTypes.BLACK_WHITE).apply(originalImage!!, 10)
                    image!!.setImageBitmap(filteredBitmap)
                }

                R.id.avg -> {
                    filteredBitmap =
                        filter.createFilter(FilterTypes.AVERAGING).apply(originalImage!!, 10)
                    image!!.setImageBitmap(filteredBitmap)
                }

                R.id.med -> {
                    filteredBitmap =
                        filter.createFilter(FilterTypes.MEDIAN).apply(originalImage!!, 1)
                    image!!.setImageBitmap(filteredBitmap)
                }

                R.id.hueHSV -> {
                    filteredBitmap =
                        filter.createFilter(FilterTypes.HUE_HSV).apply(originalImage!!, 10)
                    image!!.setImageBitmap(filteredBitmap)
                }

                R.id.satHSV -> {
                    filteredBitmap =
                        filter.createFilter(FilterTypes.SATURATION_HSV).apply(originalImage!!, 10)
                    image!!.setImageBitmap(filteredBitmap)
                }

                R.id.con -> {
                    filteredBitmap =
                        filter.createFilter(FilterTypes.CONTRAST).apply(originalImage!!, 10)
                    image!!.setImageBitmap(filteredBitmap)
                }

                R.id.edgeC -> {
                    filteredBitmap =
                        filter.createFilter(FilterTypes.EDGE_COLORING).apply(originalImage!!, 10)
                    image!!.setImageBitmap(filteredBitmap)
                }

                R.id.bin -> {
                    filteredBitmap =
                        filter.createFilter(FilterTypes.BINARY).apply(originalImage!!, 10)
                    image!!.setImageBitmap(filteredBitmap)
                }

                R.id.sha -> {
                    filteredBitmap =
                        filter.createFilter(FilterTypes.SHARPEN).apply(originalImage!!, 10)
                    image!!.setImageBitmap(filteredBitmap)
                }

                else -> Log.i("INFO", "NOT YET IMPLEMENTED")

            }
        }
        recognizeText()
        return true
    }

    fun recognizeText() {
        // applying google ml kit
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val inp = InputImage.fromBitmap((image!!.drawable as BitmapDrawable).bitmap, 0)

        // textView reference to update
        val textView: TextView = findViewById(R.id.txt)

        recognizer.process(inp)
            .addOnSuccessListener {
                textView.text = it.text
            }
            .addOnFailureListener{
                textView.text = it.toString()
            }
    }
}
