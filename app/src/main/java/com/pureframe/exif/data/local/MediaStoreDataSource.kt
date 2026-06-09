package com.pureframe.exif.data.local

import android.net.Uri
import com.pureframe.exif.data.model.Photo

/**
 * Queries the MediaStore for photos and handles deletion.
 */
interface MediaStoreDataSource {
    suspend fun getPhotos(): List<Photo>
    suspend fun getPhotoById(id: Long): Photo?
    suspend fun delete(uris: List<Uri>): DeleteResult
}
