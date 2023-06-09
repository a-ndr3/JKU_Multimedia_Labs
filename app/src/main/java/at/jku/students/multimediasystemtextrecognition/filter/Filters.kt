package at.jku.students.multimediasystemtextrecognition.filter
import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.*
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt


/**
 * Enum for defining filter name, display text, filterStrength range and default strength
 * for each filter
 */
enum class FilterType(val displayName: String,
                      val text: String,
                      val strengthRange: IntRange,
                      val defaultStrength: Int = 5
) {
    BINARY("Binary", "Bi", 1..255, 155),
    CONTRAST("Contrast", "Co", -200..500),
    SHARPEN("Sharpen", "Sh", 1..15),
    MEDIAN("Median", "Me", 1..20),
    AVERAGING("Averaging", "Av", 1..15),
    BLACK_WHITE("Black/White", "BW", 1..254),
    BRIGHTNESS_HSV("Brightness", "Br", -210..250),
    EDGE_COLORING("Edge Coloring", "EC", 0..1, 1),
    SATURATION_HSV("Saturation", "Sa", -250..250),
    HUE_HSV("Hue", "Hu", -255..254),
}

fun IntRange.toFloatRange(): ClosedFloatingPointRange<Float> {
    val s = this.first / 10f
    val e = this.last / 10f
    return s..e
}

/**
 * Filter factory for creating a new filter by defining the FilterType
 */
class FilterFactory {
    companion object {
        fun createFilter(filterType: FilterType): IFilter {
            return when (filterType) {
                FilterType.BINARY -> BinaryFilter()
                FilterType.CONTRAST -> ContrastFilter()
                FilterType.MEDIAN -> MedianFilter()
                FilterType.AVERAGING -> AveragingFilter()
                FilterType.BLACK_WHITE -> BlackWhiteFilter()
                FilterType.SHARPEN -> SharpenFilter()
                FilterType.BRIGHTNESS_HSV -> BrightnessHSVFilter()
                FilterType.EDGE_COLORING -> EdgeColoringFilter()
                FilterType.SATURATION_HSV -> SaturationFromHSVFilter()
                FilterType.HUE_HSV -> HueFromHSVFilter()
            }
        }
    }
}

/**
 * Brightness filter for brightening up all pixels of an image. This is done by
 * converting the image to the hsv color space, in order to get brightness information
 * of each pixel.
 */
class BrightnessHSVFilter : IFilter {
    override fun apply(image: Bitmap, filterStrength: Int, additionalData: List<Any>): Bitmap {
        val width = image.width
        val height = image.height
        val mutableImage = image.copy(Bitmap.Config.ARGB_8888, true)
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        //pixel by pixel -> get rgb -> convert to hsv -> increase brightness value -> normalize filter's strength
        for (j in 0 until height) {
            for (i in 0 until width) {
                val pixel = mutableImage.getPixel(i, j)
                val hsv = FloatArray(3)
                Color.colorToHSV(pixel, hsv)

                hsv[2] += filterStrength / 255.0f
                hsv[2] = max(0.0f, min(hsv[2], 1.0f))  //value within [0, 1]

                val newPixel = Color.HSVToColor(hsv)
                result.setPixel(i, j, newPixel)
            }
        }

        return result
    }
}

/**
 * Manipulates only the saturation value of an image by converting the image into the hsv color model.
 */
class SaturationFromHSVFilter : IFilter {
    override fun apply(image: Bitmap, filterStrength: Int, additionalData: List<Any>): Bitmap {
        val width = image.width
        val height = image.height
        val mutableImage = image.copy(Bitmap.Config.ARGB_8888, true)
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (j in 0 until height) {
            for (i in 0 until width) {
                val pixel = mutableImage.getPixel(i, j)
                val hsv = FloatArray(3)
                // converting to hsv
                Color.colorToHSV(pixel, hsv)

                // 1 is the index for the saturation in the hSv array
                // manipulate saturation
                hsv[1] += filterStrength / 255.0f
                hsv[1] = max(0.0f, min(hsv[1], 1.0f))  //value within [0, 1]

                val newPixel = Color.HSVToColor(hsv)
                result.setPixel(i, j, newPixel)
            }
        }
        return result
    }
}

/**
 * Manipulates only the hue value of an image, by converting the image to the hsv color space.
 */
class HueFromHSVFilter : IFilter {
    override fun apply(image: Bitmap, filterStrength: Int, additionalData: List<Any>): Bitmap {
        val width = image.width
        val height = image.height
        val mutableImage = image.copy(Bitmap.Config.ARGB_8888, true)
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (j in 0 until height) {
            for (i in 0 until width) {
                val pixel = mutableImage.getPixel(i, j)
                val hsv = FloatArray(3)
                // converting
                Color.colorToHSV(pixel, hsv)

                hsv[0] += filterStrength / 255.0f
                hsv[0] %= 1.0f  // Hue is an angle in degrees divided by 360, in range [0, 1]

                val newPixel = Color.HSVToColor(hsv)
                result.setPixel(i, j, newPixel)
            }
        }

        return result
    }
}

/**
 * TODO
 */
class EdgeColoringFilter : IFilter {
    private fun grayscale(rgb: Int): Double {
        val red = (rgb shr 16) and 0xFF
        val green = (rgb shr 8) and 0xFF
        val blue = rgb and 0xFF

        return 0.2989 * red + 0.5870 * green + 0.1140 * blue
    }

    override fun apply(image: Bitmap, filterStrength: Int, additionalData: List<Any>): Bitmap {
        val width = image.width
        val height = image.height
        val mutableImage = image.copy(Bitmap.Config.ARGB_8888, true)
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

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

        var edgeColorR = 255
        var edgeColorG = 0
        var edgeColorB = 0

        if (additionalData.size >= 3) {
            edgeColorR = (additionalData[0] as? Int) ?: edgeColorR
            edgeColorG = (additionalData[1] as? Int) ?: edgeColorG
            edgeColorB = (additionalData[2] as? Int) ?: edgeColorB
        }

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var gx = 0.0
                var gy = 0.0

                for (i in -1..1) {
                    for (j in -1..1) {
                        val color = grayscale(mutableImage.getPixel(x + i, y + j))

                        gx += sobelX[j + 1][i + 1] * color
                        gy += sobelY[j + 1][i + 1] * color
                    }
                }

                val magnitude = hypot(gx, gy).roundToInt().coerceIn(0, 255)
                val edgeColor = Color.rgb(edgeColorR * magnitude / 255, edgeColorG * magnitude / 255, edgeColorB * magnitude / 255)
                result.setPixel(x, y, edgeColor)
            }
        }

        return result
    }
}

/**
 * Averaging filter aka box blur filter
 * best results with filter strength 1-9
 */
class AveragingFilter : IFilter {
    companion object {
        // default block size
        const val BLOCK_SIZE = 100
    }

    override fun apply(image: Bitmap, filterStrength: Int, additionalData: List<Any>): Bitmap {
        val width = image.width
        val height = image.height

        val mutableImage = image.copy(Bitmap.Config.ARGB_8888, true)
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        // computing every block in parallel to increase performance
        runBlocking {
            val jobs = mutableListOf<Job>()

            // slicing up the picture into block
            for (blockStartY in 0 until height step BLOCK_SIZE) {
                for (blockStartX in 0 until width step BLOCK_SIZE) {
                    // spawn worker threads
                    jobs += launch {
                        val blockEndX = min(blockStartX + BLOCK_SIZE, width)
                        val blockEndY = min(blockStartY + BLOCK_SIZE, height)

                        // iterating through each block
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
                                            val pixel = mutableImage.getPixel(posX, posY)
                                            val r = Color.red(pixel)
                                            val g = Color.green(pixel)
                                            val b = Color.blue(pixel)

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

                                val newPixel = Color.rgb(avgR, avgG, avgB)

                                result.setPixel(x, y, newPixel)
                            }
                        }
                    }
                }
            }

            // accumulate result
            jobs.forEach { it.join() }
        }

        return result
    }
}

/**
 * Filter to apply contrast with a factor calculation.
 */
class ContrastFilter : IFilter {
    override fun apply(image: Bitmap, filterStrength: Int, additionalData: List<Any>): Bitmap {
        val width = image.width
        val height = image.height

        val mutableImage = image.copy(Bitmap.Config.ARGB_8888, true)
        // Calculate contrast adjustment factor
        val factor = (259.0 * (filterStrength + 255)) / (255.0 * (259 - filterStrength))

        for (j in 0 until height) {
            for (i in 0 until width) {
                val pixel = mutableImage.getPixel(i, j)

                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                val newR = (((r - 128) * factor) + 128).coerceIn(0.0, 255.0).toInt()
                val newG = (((g - 128) * factor) + 128).coerceIn(0.0, 255.0).toInt()
                val newB = (((b - 128) * factor) + 128).coerceIn(0.0, 255.0).toInt()

                val newPixel = Color.rgb(newR, newG, newB)
                mutableImage.setPixel(i, j, newPixel)
            }
        }

        return mutableImage
    }
}

/**
 * Median filter which runs in parallel to speed up computation time. This filter uses
 * coroutines for parallel computation. The image is sliced into blocks beforehand, which
 * are then computed in parallel.
 */
class MedianFilter : IFilter {
    // default block size of 100 blocks per image
    companion object {
        const val BLOCK_SIZE = 100
        const val MAX_COLOR_VALUE = 255
    }

    override fun apply(image: Bitmap, filterStrength: Int, additionalData: List<Any>): Bitmap {
        val width = image.width
        val height = image.height

        val mutableImage = image.copy(Bitmap.Config.ARGB_8888, true)
        val result = image.copy(Bitmap.Config.ARGB_8888, true)

        // aperture values used for determining neighbors in a block
        val halfAperture = filterStrength / 2
        val apertureArea = filterStrength * filterStrength
        val medianIndex = apertureArea / 2

        runBlocking {
            val jobs = mutableListOf<Job>()

            // slicing up the picture into blocks
            for (blockStartY in 0 until height step BLOCK_SIZE) {
                for (blockStartX in 0 until width step BLOCK_SIZE) {
                    // spawn one worker thread per block
                    jobs += launch {
                        val blockEndX = min(blockStartX + BLOCK_SIZE, width)
                        val blockEndY = min(blockStartY + BLOCK_SIZE, height)

                        for (j in blockStartY until blockEndY) {
                            for (i in blockStartX until blockEndX) {

                                // histogram array counting the pixels in the block with the same color value
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
                                            val pixel = mutableImage.getPixel(w, h)
                                            r = Color.red(pixel)
                                            g = Color.green(pixel)
                                            b = Color.blue(pixel)
                                        }

                                        histogramR[r]++
                                        histogramG[g]++
                                        histogramB[b]++
                                    }
                                }

                                val medianR = findMedian(histogramR, medianIndex)
                                val medianG = findMedian(histogramG, medianIndex)
                                val medianB = findMedian(histogramB, medianIndex)

                                val newPixel = Color.rgb(medianR, medianG, medianB)
                                result.setPixel(i, j, newPixel)
                            }
                        }
                    }
                }
            }
            // accumulate all the blocks to form the result
            jobs.forEach { it.join() }
        }
        return result
    }

    /**
     * Function for finding the median of a histogram array and returns the median value as an integer
     */
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

/**
 * A filter used to sharpen a given image. This filter uses the @see MedianFilter to form
 * a sharper image.
 */
class SharpenFilter : IFilter {
    override fun apply(image: Bitmap, filterStrength: Int, additionalData: List<Any>): Bitmap {
        val width = image.width
        val height = image.height
        val mutableImage = image.copy(Bitmap.Config.ARGB_8888, true)
        val result = image.copy(Bitmap.Config.ARGB_8888, true)

        // get the blurred image
        val blurredImage = MedianFilter().apply(image, filterStrength)

        for (j in 0 until height) {
            for (i in 0 until width) {
                val originalPixel = mutableImage.getPixel(i, j)
                val blurredPixel = blurredImage.getPixel(i, j)

                // extracting each color channel
                val originalR = Color.red(originalPixel)
                val originalG = Color.green(originalPixel)
                val originalB = Color.blue(originalPixel)

                val blurredR = Color.red(blurredPixel)
                val blurredG = Color.green(blurredPixel)
                val blurredB = Color.blue(blurredPixel)

                // compare blurred and original color channels
                val r = originalR - blurredR
                val g = originalG - blurredG
                val b = originalB - blurredB

                // sharpening amount based on filter strength
                val sharpeningAmount = filterStrength.toDouble()

                // clamp the sharpened amount between 0 and 255
                val sharpenedPixel = Color.rgb(
                    (originalR + r * sharpeningAmount).roundToInt().coerceIn(0, 255),
                    (originalG + g * sharpeningAmount).roundToInt().coerceIn(0, 255),
                    (originalB + b * sharpeningAmount).roundToInt().coerceIn(0, 255)
                )
                // store sharpened pixels in a result bitmap
                result.setPixel(i, j, sharpenedPixel)
            }
        }

        return result
    }
}

/**
 * Binary filter, setting each rgb channel value based on the @param filterStrength.
 * Each color is either 0 or 255, depending on being above or below the filterStrength
 */
class BinaryFilter : IFilter {
    override fun apply(image: Bitmap, filterStrength: Int, additionalData: List<Any>): Bitmap {
        require(filterStrength in 0..255)
        { throw IllegalArgumentException("Filter strength must be between 0 and 255.") }

        val width = image.width
        val height = image.height

        val result = image.copy(Bitmap.Config.ARGB_8888, true)

        // iterating through each pixel
        for (y in 0 until height) {
            for (x in 0 until width) {

                //get colors
                val pixel = image.getPixel(x, y)
                // extract color channel information
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                //apply filter
                val newR = if (r <= filterStrength) 0 else 255
                val newG = if (g <= filterStrength) 0 else 255
                val newB = if (b <= filterStrength) 0 else 255

                val newColor = Color.rgb(newR, newG, newB)
                // putting new pixel in the result bitmap
                result.setPixel(x, y, newColor)
            }
        }
        return result
    }
}

/**
 * Black and White Filter aka Greyscale filter calculates the average of each rgb channel.
 */
class BlackWhiteFilter : IFilter {
    override fun apply(image: Bitmap, filterStrength: Int, additionalData: List<Any>): Bitmap {

        val width = image.width
        val height = image.height

        val mutableImage = image.copy(Bitmap.Config.ARGB_8888, true)
        val result = image.copy(Bitmap.Config.ARGB_8888, true)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = mutableImage.getPixel(x, y)
                // extracting rgb channels
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                // formula for calculating a gray value
                val gray = (0.3 * r + 0.59 * g + 0.11 * b).toInt()
                val grayscaleColor = Color.rgb(gray, gray, gray)

                result.setPixel(x, y, grayscaleColor)
            }
        }
        return result
    }
}