package at.jku.students.multimediasystemtextrecognition.filter
import android.graphics.Bitmap

/**
 * Functional interface for defining the common apply method for each filter implementing this interface
 */
@FunctionalInterface
interface IFilter {
    fun apply(image: Bitmap, filterStrength: Int, additionalData: List<Any> = emptyList()) : Bitmap
}