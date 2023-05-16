import java.awt.image.BufferedImage

interface IFilter {
    fun apply(image: BufferedImage, filterStrength: Int, additionalData: List<Any> = emptyList()) : BufferedImage
}
