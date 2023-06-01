package at.jku.students.multimediasystemtextrecognition.filter
import android.graphics.Bitmap

interface IFilter {
    fun apply(image: Bitmap, filterStrength: Int, additionalData: List<Any> = emptyList()) : Bitmap
}