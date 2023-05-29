package com.multimedia.imagefilters
import android.graphics.Bitmap

interface IFilter {
    fun apply(image: Bitmap, filterStrength: Int, additionalData: List<Any> = emptyList()) : Bitmap
}