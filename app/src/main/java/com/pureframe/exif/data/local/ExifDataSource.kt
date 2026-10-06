package com.pureframe.exif.data.local

import android.net.Uri
import com.pureframe.exif.data.model.ExifMetadata

/**
 * Reads EXIF metadata from an image [Uri].
 */
interface ExifDataSource {
    suspend fun getMetadata(uri: Uri): ExifMetadata
}
