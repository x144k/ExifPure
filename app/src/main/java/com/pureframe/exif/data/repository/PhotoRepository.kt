package com.pureframe.exif.data.repository

import android.net.Uri
import android.util.Log
import com.pureframe.exif.data.local.DeleteResult
import com.pureframe.exif.data.local.EncryptedPreferenceStorage
import com.pureframe.exif.data.local.ExifDataSource
import com.pureframe.exif.data.local.MediaStoreDataSource
import com.pureframe.exif.data.local.MetadataStripper
import com.pureframe.exif.data.model.ExifMetadata
import com.pureframe.exif.data.model.ExportLogEntry
import com.pureframe.exif.data.model.Photo
import com.pureframe.exif.data.model.StripMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.util.UUID

class PhotoRepository(
    private val mediaStore: MediaStoreDataSource,
    private val exif: ExifDataSource,
    private val stripper: MetadataStripper,
    val prefs: EncryptedPreferenceStorage
) {
    // Singleton dispatcher so the 4-way concurrency cap is respected across
    // multiple concurrent batchExport calls, not just within one invocation.
    private val exportDispatcher = Dispatchers.IO.limitedParallelism(4)

    fun getPhotos(): Flow<List<Photo>> = flow { emit(mediaStore.getPhotos()) }

    suspend fun getPhoto(id: Long): Photo? = mediaStore.getPhotoById(id)

    suspend fun getExif(photo: Photo): ExifMetadata = exif.getMetadata(photo.uri)

    suspend fun exportClean(photo: Photo, mode: StripMode = StripMode.ALL): Result<Uri> {
        return withContext(exportDispatcher) {
            val result = stripper.createCleanCopy(photo, mode, outputDir = prefs.outputDirName)
            val exportResult = result.getOrNull()
            if (exportResult != null) {
                try {
                    logExport(photo, exportResult.filename, mode)
                } catch (e: Exception) {
                    // Logging must not crash or break the export Result contract.
                    // Re-throw cancellation so callers can clean up properly.
                    if (e is CancellationException) throw e
                    Log.w(TAG, "Export logging failed", e)
                }
            }
            result.map { it.uri }
        }
    }

    suspend fun batchExport(photos: List<Photo>, mode: StripMode): List<Result<Uri>> {
        val results = coroutineScope {
            photos.map { photo ->
                async(exportDispatcher) {
                    stripper.createCleanCopy(photo, mode, outputDir = prefs.outputDirName)
                }
            }.awaitAll()
        }
        results.forEachIndexed { index, result ->
            result.getOrNull()?.let { exportResult ->
                try {
                    logExport(photos[index], exportResult.filename, mode)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w(TAG, "Export logging failed: ${e.javaClass.simpleName}")
                }
            }
        }
        return results.map { it.map { r -> r.uri } }
    }

    private fun logExport(photo: Photo, exportedName: String, mode: StripMode) {
        prefs.addExportLog(ExportLogEntry(
            id = UUID.randomUUID().toString(),
            originalName = photo.displayName,
            exportedName = exportedName,
            stripMode = if (mode == StripMode.ALL) "All Metadata" else "GPS Only",
            timestamp = System.currentTimeMillis(),
            mimeType = photo.mimeType
        ))
    }

    fun getExportLogs(): List<ExportLogEntry> = prefs.getExportLogs()

    fun clearExportLogs() = prefs.clearExportLogs()

    fun getFavoriteIds(): Set<Long> = prefs.getFavoriteIds()

    fun toggleFavorite(photoId: Long) = prefs.toggleFavorite(photoId)

    fun isFavorite(photoId: Long): Boolean = prefs.isFavorite(photoId)

    suspend fun deletePhotos(photos: List<Photo>): DeleteResult {
        return mediaStore.delete(photos.map { it.uri })
    }

    companion object {
        private const val TAG = "PhotoRepository"
    }
}
