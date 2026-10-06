package com.pureframe.exif.data.local

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.ContentUris
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.pureframe.exif.data.model.Photo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class DeleteResult {
    data class Success(val count: Int) : DeleteResult()
    data class NeedsConsent(val pendingIntent: PendingIntent) : DeleteResult()
}

class MediaStoreDataSourceImpl(private val resolver: ContentResolver) : MediaStoreDataSource {

    override suspend fun getPhotos(): List<Photo> = withContext(Dispatchers.IO) {
        val result = mutableListOf<Photo>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.BUCKET_ID
        )

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        resolver.query(
            collection,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { c ->
            val idxId = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val idxName = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val idxAdded = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val idxMod = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val idxW = c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val idxH = c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val idxSize = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val idxMime = c.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val idxBucket = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val idxBucketId = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)

            while (c.moveToNext()) {
                val id = c.getLong(idxId)
                result += Photo(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    displayName = c.getString(idxName),
                    dateAdded = c.getLong(idxAdded),
                    dateModified = c.getLong(idxMod),
                    width = c.getInt(idxW),
                    height = c.getInt(idxH),
                    size = c.getLong(idxSize),
                    mimeType = c.getString(idxMime),
                    bucketDisplayName = c.getString(idxBucket),
                    bucketId = c.getString(idxBucketId)
                )
            }
        }
        result
    }

    override suspend fun getPhotoById(id: Long): Photo? = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.BUCKET_ID
        )
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        resolver.query(
            ContentUris.withAppendedId(collection, id),
            projection, null, null, null
        )?.use { c ->
            if (c.moveToFirst()) parsePhoto(c, collection) else null
        }
    }

    /**
     * Attempts to delete the given URIs.
     *
     * On API < 29 this performs a direct delete (WRITE_EXTERNAL_STORAGE is required).
     * On API 29 it catches RecoverableSecurityException and returns the consent intent.
     * On API 30+ it uses MediaStore.createDeleteRequest() so the system shows a
     * confirmation dialog and handles the deletion itself.
     */
    override suspend fun delete(uris: List<Uri>): DeleteResult = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext DeleteResult.Success(0)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pendingIntent = MediaStore.createDeleteRequest(resolver, uris)
            DeleteResult.NeedsConsent(pendingIntent)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var deleted = 0
            var consentIntent: PendingIntent? = null
            for (uri in uris) {
                try {
                    val rows = resolver.delete(uri, null, null)
                    if (rows > 0) deleted++
                } catch (e: android.app.RecoverableSecurityException) {
                    consentIntent = e.userAction.actionIntent
                    break
                } catch (_: Exception) {
                    // skip
                }
            }
            consentIntent?.let { DeleteResult.NeedsConsent(it) }
                ?: DeleteResult.Success(deleted)
        } else {
            var deleted = 0
            for (uri in uris) {
                try {
                    val rows = resolver.delete(uri, null, null)
                    if (rows > 0) deleted++
                } catch (_: Exception) {
                    // skip
                }
            }
            DeleteResult.Success(deleted)
        }
    }


    private fun parsePhoto(c: Cursor, collection: Uri): Photo {
        val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
        return Photo(
            id = id,
            uri = ContentUris.withAppendedId(collection, id),
            displayName = c.getString(c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)),
            dateAdded = c.getLong(c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)),
            dateModified = c.getLong(c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)),
            width = c.getInt(c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)),
            height = c.getInt(c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)),
            size = c.getLong(c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)),
            mimeType = c.getString(c.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)),
            bucketDisplayName = c.getString(c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)),
            bucketId = c.getString(c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID))
        )
    }
}
