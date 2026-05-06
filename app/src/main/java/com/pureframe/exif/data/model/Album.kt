package com.pureframe.exif.data.model

import android.net.Uri

/**
 * Represents a photo album (bucket) on the device.
 *
 * @param bucketId Stable identifier for the album, derived from MediaStore.BUCKET_ID.
 * @param bucketDisplayName Human-readable album name (e.g. "Camera", "Screenshots").
 * @param coverPhotoUri URI of the most recently added photo in the album, used as the cover thumbnail.
 * @param photoCount Total number of photos in the album.
 */
data class Album(
    val bucketId: String,
    val bucketDisplayName: String?,
    val coverPhotoUri: Uri,
    val photoCount: Int
)
