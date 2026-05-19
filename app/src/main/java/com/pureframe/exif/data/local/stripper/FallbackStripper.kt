package com.pureframe.exif.data.local.stripper

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.pureframe.exif.data.local.ImageTooLargeException
import com.pureframe.exif.data.local.MetadataStripper
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

object FallbackStripper {

    private const val MAX_BITMAP_DIMENSION = 4096

    fun strip(
        contentResolver: ContentResolver,
        uri: Uri,
        output: OutputStream,
        quality: Int = 95,
        mimeType: String? = null
    ) {
        val temp = File.createTempFile("fallback", ".tmp")
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temp).use { out ->
                    // Copy with a hard byte limit to prevent DoS from
                    // malicious content providers that lie about size.
                    var copied = 0L
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        if (read == 0) throw IOException("Stream returned zero bytes")
                        copied += read
                        if (copied > MetadataStripper.MAX_EXPORT_BYTES) {
                            throw ImageTooLargeException()
                        }
                        out.write(buffer, 0, read)
                    }
                }
            } ?: throw IllegalArgumentException("Could not read image")

            val (width, height) = FileInputStream(temp).use { stream ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(stream, null, options)
                options.outWidth to options.outHeight
            }

            val sampleSize = if (width > 0 && height > 0) {
                calculateInSampleSize(width, height, MAX_BITMAP_DIMENSION, MAX_BITMAP_DIMENSION)
            } else 1

            val bitmap = FileInputStream(temp).use { stream ->
                val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                BitmapFactory.decodeStream(stream, null, options)
            } ?: throw IllegalArgumentException("Could not decode image")

            val format = when {
                mimeType?.equals("image/png", ignoreCase = true) == true -> Bitmap.CompressFormat.PNG
                mimeType?.equals("image/webp", ignoreCase = true) == true -> {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        Bitmap.CompressFormat.WEBP_LOSSY
                    } else {
                        @Suppress("DEPRECATION")
                        Bitmap.CompressFormat.WEBP
                    }
                }
                else -> Bitmap.CompressFormat.JPEG
            }

            // compress() returns false when the output stream fails mid-write
            // (e.g. disk full). Treat this as a fatal error rather than silently
            // producing a truncated file. Recycle in finally so the bitmap is
            // always freed even if the OutputStream throws during compression.
            try {
                if (!bitmap.compress(format, quality, output)) {
                    throw IOException("Bitmap compression failed")
                }
            } finally {
                // Native bitmap memory is not reclaimed until GC on API < 30.
                // Explicitly recycle to prevent native OOM during batch exports.
                bitmap.recycle()
            }
        } finally {
            if (!temp.delete()) {
                temp.deleteOnExit()
            }
        }
    }

    private fun calculateInSampleSize(
        srcWidth: Int, srcHeight: Int,
        reqWidth: Int, reqHeight: Int
    ): Int {
        var inSampleSize = 1
        // Cap inSampleSize before it overflows for pathological inputs.
        while (inSampleSize < Int.MAX_VALUE / 2 &&
            (srcWidth / inSampleSize > reqWidth || srcHeight / inSampleSize > reqHeight)
        ) {
            inSampleSize *= 2
        }
        return inSampleSize
    }
}
