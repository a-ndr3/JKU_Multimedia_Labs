package at.jku.students.multimediasystemtextrecognition.filter

// equivalent for awt.Color
import android.graphics.Color

// using androids Bitmap instead of java BufferedImage
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.graphics.red
import androidx.core.graphics.set


import kotlinx.coroutines.*
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class FilterTypes {
    BINARY,
    CONTRAST,
    SHARPEN,
    MEDIAN,
    AVERAGING,
    BLACK_WHITE,
    BRIGHTNESS_HSV,
    EDGE_COLORING,
    SATURATION_HSV,
    HUE_HSV,
}

// TODO Rewrite all filter to function with androids Bitmap instead of java BufferedImage
class BrightnessHSVIFilter : IFilter{

    @RequiresApi(Build.VERSION_CODES.Q) // needed for image.getColor()
    override fun apply(image: Bitmap, filterStrength: Int, additionalData: List<Any>): Bitmap {

        val width = image.width
        val height = image.height
        //val result = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val mutableImage = image.copy(Bitmap.Config.RGBA_F16, true)
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.RGBA_F16)

        //pixel by pixel -> get rgb -> convert to hsv -> increase brightness value -> normalize filter's strength
        for (j in 0 until height) {
            for (i in 0 until width) {
                //val color = Color(image.getRGB(i, j))
                val color = mutableImage.getColor(i, j)
                val hsv = FloatArray(3)
                //Color.RGBtoHSB(color.red, color.green, color.blue, hsv)
                Color.RGBToHSV(color.red().toInt(), color.green().toInt(), color.blue().toInt(), hsv)

                hsv[2] += filterStrength / 255.0f
                hsv[2] = max(0.0f, min(hsv[2], 1.0f))  //value within [0, 1]

                //val newColor = Color(Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]))
                val newColor = Color.HSVToColor(hsv)

                //result.setRGB(i, j, newColor.rgb)
                result[i, j] = newColor
            }
        }

        return result
    }
}


/*class SaturationFromHSVIFilter : IFilter{
    override fun apply(image: Bitmap, filterStrength: Int, additionalData: List<Any>): Bitmap {

        val width = image.width
        val height = image.height
        val result = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)

        for (j in 0 until height) {
            for (i in 0 until width) {
                val color = Color(image.getRGB(i, j))
                val hsv = FloatArray(3)
                Color.RGBtoHSB(color.red, color.green, color.blue, hsv)

                hsv[1] += filterStrength / 255.0f
                hsv[1] = max(0.0f, min(hsv[1], 1.0f))

                val newColor = Color(Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]))
                result.setRGB(i, j, newColor.rgb)
            }
        }

        return result
    }
}

class HueFromHSVIFilter : IFilter{
    override fun apply(image: BufferedImage, filterStrength: Int, additionalData: List<Any>): BufferedImage {

        val width = image.width
        val height = image.height
        val result = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)

        for (j in 0 until height) {
            for (i in 0 until width) {
                val color = Color(image.getRGB(i, j))
                val hsv = FloatArray(3)
                Color.RGBtoHSB(color.red, color.green, color.blue, hsv)

                hsv[0] += filterStrength / 255.0f
                hsv[0] = max(0.0f, min(hsv[0], 1.0f))

                val newColor = Color(Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]))
                result.setRGB(i, j, newColor.rgb)
            }
        }

        return result
    }
}

class EdgeColoringIFilter : IFilter{
    private fun grayscale(rgb: Int): Double {
        val red = (rgb shr 16) and 0xFF
        val green = (rgb shr 8) and 0xFF
        val blue = rgb and 0xFF

        return 0.2989 * red + 0.5870 * green + 0.1140 * blue
    }

    override fun apply(image: BufferedImage, filterStrength: Int, additionalData: List<Any>): BufferedImage {
        val width = image.width
        val height = image.height
        val result = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)

        val sobelX = arrayOf(
            arrayOf(-1, 0, 1),
            arrayOf(-2, 0, 2),
            arrayOf(-1, 0, 1)
        )

        val sobelY = arrayOf(
            arrayOf(-1, -2, -1),
            arrayOf(0, 0, 0),
            arrayOf(1, 2, 1)
        )

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var gx = 0.0
                var gy = 0.0

                for (i in -1..1) {
                    for (j in -1..1) {
                        val color = grayscale(image.getRGB(x + i, y + j))

                        gx += sobelX[j + 1][i + 1] * color
                        gy += sobelY[j + 1][i + 1] * color
                    }
                }

                val magnitude = hypot(gx, gy).roundToInt().coerceIn(0, 255)
                val edgeColor = Color(magnitude, 0, 0).rgb
                result.setRGB(x, y, edgeColor)
            }
        }

        return result
    }
}

class BinaryIFilter : IFilter {

    override fun apply(image: BufferedImage, filterStrength: Int, additionalData: List<Any>): BufferedImage {
        require(filterStrength in 0..255)
        { throw IllegalArgumentException("Filter strength must be between 0 and 255.")}

        val width = image.width
        val height = image.height

        for (y in 0 until height) {
            for (x in 0 until width) {

                //get colors
                val color = Color(image.getRGB(x, y))
                val r = color.red
                val g = color.green
                val b = color.blue

                //apply filter
                val newR = if (r <= filterStrength) 0 else 255
                val newG = if (g <= filterStrength) 0 else 255
                val newB = if (b <= filterStrength) 0 else 255

                val newColor = Color(newR, newG, newB).rgb
                image.setRGB(x, y, newColor)
            }
        }
        return image
    }
}

/**
 * Averaging filter aka box blur filter
 * best results with filter strength 1-9
 */
class AveragingIFilter : IFilter {
    companion object {
        const val BLOCK_SIZE = 100
    }

    override fun apply(image: BufferedImage, filterStrength: Int, additionalData: List<Any>): BufferedImage {
        val width = image.width
        val height = image.height

        val result = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)

        runBlocking {
            val jobs = mutableListOf<Job>()

            for (blockStartY in 0 until height step BLOCK_SIZE) {
                for (blockStartX in 0 until width step BLOCK_SIZE) {
                    jobs += launch {
                        val blockEndX = minOf(blockStartX + BLOCK_SIZE, width)
                        val blockEndY = minOf(blockStartY + BLOCK_SIZE, height)

                        for (y in blockStartY until blockEndY) {
                            for (x in blockStartX until blockEndX) {
                                var sumR = 0
                                var sumG = 0
                                var sumB = 0
                                var count = 0

                                for (i in -filterStrength..filterStrength) {
                                    for (j in -filterStrength..filterStrength) {
                                        val posX = x + i
                                        val posY = y + j

                                        if (posX in 0 until width && posY in 0 until height) {
                                            val color = Color(image.getRGB(posX, posY))
                                            val r = color.red
                                            val g = color.green
                                            val b = color.blue

                                            sumR += r
                                            sumG += g
                                            sumB += b
                                            count++
                                        }
                                    }
                                }

                                val avgR = sumR / count
                                val avgG = sumG / count
                                val avgB = sumB / count

                                val newColor = Color(avgR, avgG, avgB).rgb

                                result.setRGB(x, y, newColor)
                            }
                        }
                    }
                }
            }

            jobs.forEach { it.join() }
        }

        return result
    }
}

class ContrastIFilter : IFilter {
    override fun apply(image: BufferedImage, filterStrength: Int, additionalData: List<Any>): BufferedImage {
        val width = image.width
        val height = image.height

        // Calculate contrast adjustment factor
        val factor = (259.0 * (filterStrength + 255)) / (255.0 * (259 - filterStrength))

        for (j in 0 until height) {
            for (i in 0 until width) {
                val color = Color(image.getRGB(i, j))

                val r = color.red
                val g = color.green
                val b = color.blue

                val newR = (((r - 128) * factor) + 128).coerceIn(0.0, 255.0).toInt()
                val newG = (((g - 128) * factor) + 128).coerceIn(0.0, 255.0).toInt()
                val newB = (((b - 128) * factor) + 128).coerceIn(0.0, 255.0).toInt()

                val newColor = Color(newR, newG, newB).rgb
                image.setRGB(i, j, newColor)
            }
        }

        return image
    }
}

/**
 *
 * https://nomis80.org/ctmf.pdf
 */
class MedianIFilter : IFilter {
    companion object {
        const val BLOCK_SIZE = 100
        const val MAX_COLOR_VALUE = 255
    }

    override fun apply(image: BufferedImage, filterStrength: Int, additionalData: List<Any>): BufferedImage {
        val width = image.width
        val height = image.height

        val result = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)

        val halfAperture = filterStrength / 2
        val apertureArea = filterStrength * filterStrength
        val medianIndex = apertureArea / 2

        runBlocking {
            val jobs = mutableListOf<Job>()

            for (blockStartY in 0 until height step BLOCK_SIZE) {
                for (blockStartX in 0 until width step BLOCK_SIZE) {
                    jobs += launch {
                        val blockEndX = minOf(blockStartX + BLOCK_SIZE, width)
                        val blockEndY = minOf(blockStartY + BLOCK_SIZE, height)

                        for (j in blockStartY until blockEndY) {
                            for (i in blockStartX until blockEndX) {
                                val histogramR = IntArray(MAX_COLOR_VALUE + 1)
                                val histogramG = IntArray(MAX_COLOR_VALUE + 1)
                                val histogramB = IntArray(MAX_COLOR_VALUE + 1)

                                for (m in 0 until filterStrength) {
                                    for (n in 0 until filterStrength) {
                                        val w = (i - halfAperture) + n
                                        val h = (j - halfAperture) + m
                                        val r: Int
                                        val g: Int
                                        val b: Int

                                        if (w < 0 || w >= width || h < 0 || h >= height) {
                                            r = 0
                                            g = 0
                                            b = 0
                                        } else {
                                            val color = Color(image.getRGB(w, h))
                                            r = color.red
                                            g = color.green
                                            b = color.blue
                                        }

                                        histogramR[r]++
                                        histogramG[g]++
                                        histogramB[b]++
                                    }
                                }

                                val medianR = findMedian(histogramR, medianIndex)
                                val medianG = findMedian(histogramG, medianIndex)
                                val medianB = findMedian(histogramB, medianIndex)

                                val newColor = Color(medianR, medianG, medianB).rgb
                                result.setRGB(i, j, newColor)
                            }
                        }
                    }
                }
            }
            jobs.forEach { it.join() }
        }
        return result
    }

    private fun findMedian(histogram: IntArray, medianIndex: Int): Int {
        var count = 0
        for (i in histogram.indices) {
            count += histogram[i]
            if (count > medianIndex) {
                return i
            }
        }
        return 0
    }

}

class SharpenIFilter : IFilter {
    override fun apply(image: BufferedImage, filterStrength: Int, additionalData: List<Any>): BufferedImage {
        val width = image.width
        val height = image.height
        val result = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)

        val blurredImage = MedianIFilter().apply(image, filterStrength)

        for (j in 0 until height) {
            for (i in 0 until width) {
                val originalColor = Color(image.getRGB(i, j))
                val blurredColor = Color(blurredImage.getRGB(i, j))

                val r = originalColor.red - blurredColor.red
                val g = originalColor.green - blurredColor.green
                val b = originalColor.blue - blurredColor.blue

                val sharpeningAmount = filterStrength.toDouble()
                val sharpenedColor = Color(
                    (originalColor.red + r * sharpeningAmount).roundToInt().coerceIn(0, 255),
                    (originalColor.green + g * sharpeningAmount).roundToInt().coerceIn(0, 255),
                    (originalColor.blue + b * sharpeningAmount).roundToInt().coerceIn(0, 255)
                ).rgb

                result.setRGB(i, j, sharpenedColor)
            }
        }

        return result
    }
}

class BlackWhite : IFilter {
    override fun apply(image: BufferedImage, filterStrength: Int, additionalData: List<Any>): BufferedImage {

        val width = image.width
        val height = image.height

        for (y in 0 until height) {
            for (x in 0 until width) {
                val color = Color(image.getRGB(x, y))
                val r = color.red
                val g = color.green
                val b = color.blue

                val gray = (0.3 * r + 0.59 * g + 0.11 * b).toInt()
                val grayscaleColor = Color(gray, gray, gray).rgb

                image.setRGB(x, y, grayscaleColor)
            }
        }

        BinaryIFilter().apply(image, filterStrength)

        return image
    }
}*/

class FilterFactory {
    fun createFilter(filterType: FilterTypes): IFilter {
        return when (filterType) {
            //FilterTypes.BINARY -> BinaryIFilter()
            //FilterTypes.CONTRAST -> ContrastIFilter()
            //FilterTypes.MEDIAN -> MedianIFilter()
            //FilterTypes.AVERAGING -> AveragingIFilter()
            //FilterTypes.BLACK_WHITE -> BlackWhite()
            //FilterTypes.SHARPEN -> SharpenIFilter()
            FilterTypes.BRIGHTNESS_HSV -> BrightnessHSVIFilter()
            //FilterTypes.EDGE_COLORING -> EdgeColoringIFilter()
            //FilterTypes.SATURATION_HSV -> SaturationFromHSVIFilter()
            //FilterTypes.HUE_HSV -> HueFromHSVIFilter()
            else -> {
                // always return this filter before implementation is complete
                return BrightnessHSVIFilter()
            }
        }
    }
}