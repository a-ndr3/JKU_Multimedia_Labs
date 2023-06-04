import java.awt.image.BufferedImage

interface IFilter {
    fun apply(image: BufferedImage, filterStrength: Float, additionalData: List<Any> = emptyList()) : BufferedImage
}
