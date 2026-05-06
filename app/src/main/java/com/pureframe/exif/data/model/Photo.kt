package com.pureframe.exif.data.model

import android.net.Uri

data class Photo(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val dateAdded: Long,
    val dateModified: Long,
    val width: Int,
    val height: Int,
    val size: Long,
    val mimeType: String,
    val bucketDisplayName: String?,
    val bucketId: String?
)
