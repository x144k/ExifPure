package com.pureframe.exif.data.local.stripper

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.OutputStream

object FallbackStripper {

    /** Maximum dimension (width or height) for decoded bitmaps to prevent OOM. */
    private const val MAX_BITMAP_DIMENSION = 8192

    fun strip(contentResolver: ContentResolver, uri: Uri, output: OutputStream, quality: Int = 95) {
        val (width, height) = contentResolver.openInputStream(uri)?.use { stream ->
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(stream, null, options)
            options.outWidth to options.outHeight
        } ?: (0 to 0)

        val sampleSize = if (width > 0 && height > 0) {
            calculateInSampleSize(width, height, MAX_BITMAP_DIMENSION, MAX_BITMAP_DIMENSION)
        } else 1

        val bitmap = contentResolver.openInputStream(uri)?.use { stream ->
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeStream(stream, null, options)
        } ?: throw IllegalArgumentException("Could not decode image")

        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, output)
    }

    private fun calculateInSampleSize(
        srcWidth: Int, srcHeight: Int,
        reqWidth: Int, reqHeight: Int
    ): Int {
        var inSampleSize = 1
        while (srcWidth / inSampleSize > reqWidth || srcHeight / inSampleSize > reqHeight) {
            inSampleSize *= 2
        }
        return inSampleSize
    }
}
