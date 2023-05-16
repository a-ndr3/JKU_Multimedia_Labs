import ImageProcessingExceptions.ImageProcessingException
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import java.awt.Color
import kotlin.math.roundToInt

enum class ImageSupportedTypes {
    PNG,
    JPG,
    BMP,
    JPEG
}

/**
 * Class handles image processing operations
 * and provides additional information about image
 */
class ImageProcessor {

    private var imageBuffer: BufferedImage? = null

    fun readImage(file: File) {
         try {
            imageBuffer = ImageIO.read(file)
        } catch (e: Exception) {
             throw ImageProcessingException(
                 e,
                 "Image reading error",
                 e.stackTrace
             )
        }
    }

    fun writeImage(file: File, type: ImageSupportedTypes) {
        if (imageBuffer == null) {
             throw ImageProcessingException(
                 Exception(),
                 "Image buffer is null",
                 Exception().stackTrace
             )
        }
         try {
            ImageIO.write(imageBuffer, type.name, file)
        } catch (e: Exception) {
            throw ImageProcessingException(
                e,
                "Image writing error",
                e.stackTrace
            )
        }
    }

    fun applyFilter(filterType: FilterTypes, filterStrength: Int): Boolean {

        try{
        val filterFactory = FilterFactory()
        val filter = filterFactory.createFilter(filterType)


            return if (imageBuffer != null) {
            imageBuffer = filter.apply(imageBuffer!!, filterStrength)
            true
                } else {
            false
            }
        }
        catch (e: Exception){
            throw ImageProcessingException(
                e,
                "Filter application error",
                e.stackTrace
            )
        }
    }

    fun isMonochrome(): Boolean {

        if (imageBuffer == null) {
            return false
        }

        val width = imageBuffer!!.width
        val height = imageBuffer!!.height

        for (y in 0 until height) {
            for (x in 0 until width) {
                val color = Color(imageBuffer!!.getRGB(x, y))
                val r = color.red
                val g = color.green
                val b = color.blue

                if (r != g || r != b || g != b) {
                    return false
                }
            }
        }

        return true
    }
}

/*fun main() {

    val imageProcessor = ImageProcessor()
    val inputFile = File("C:/Users/abloh/Desktop/testsImages/122.jpg")
    val outputFile = File("C:/Users/abloh/Desktop/testsImages/123.jpg")

    try{
        imageProcessor.readImage(inputFile)
        imageProcessor.applyFilter(FilterTypes.MEDIAN, 11)
        imageProcessor.writeImage(outputFile, ImageSupportedTypes.JPG)
    }
    catch (e: Exception){
        throw ImageProcessingExceptions.ImageProcessingEx(e.message!!)
    }
}*/