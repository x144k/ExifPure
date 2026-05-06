package com.pureframe.exif.data.local.stripper

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.OutputStream

object FallbackStripper {
    fun strip(contentResolver: ContentResolver, uri: Uri, output: OutputStream, quality: Int = 95) {
        val bitmap = contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: throw IllegalArgumentException("Could not decode image")
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, output)
    }
}
