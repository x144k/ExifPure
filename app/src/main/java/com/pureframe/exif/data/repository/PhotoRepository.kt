package com.pureframe.exif.data.repository

import android.net.Uri
import android.util.Log
import com.pureframe.exif.data.local.DeleteResult
import com.pureframe.exif.data.local.ExifDataSource
import com.pureframe.exif.data.local.MediaStoreDataSource
import com.pureframe.exif.data.local.MetadataStripper
import com.pureframe.exif.data.local.PreferenceStorage
import com.pureframe.exif.data.local.ExportResult
import com.pureframe.exif.data.model.ExifMetadata
import com.pureframe.exif.data.model.ExportLogEntry
import com.pureframe.exif.data.model.Photo
import com.pureframe.exif.data.model.StripMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.UUID

class PhotoRepository(
    private val mediaStore: MediaStoreDataSource,
    private val exif: ExifDataSource,
    private val stripper: MetadataStripper,
    private val prefs: PreferenceStorage
) {
    // Singleton dispatcher so the 4-way concurrency cap is respected across
    // multiple concurrent batchExport calls, not just within one invocation.
    private val exportDispatcher = Dispatchers.IO.limitedParallelism(4)

    suspend fun getPhotos(): List<Photo> = withContext(Dispatchers.IO) {
        mediaStore.getPhotos()
    }

    suspend fun getPhoto(id: Long): Photo? = withContext(Dispatchers.IO) {
        mediaStore.getPhotoById(id)
    }

    suspend fun getExif(photo: Photo): ExifMetadata = withContext(Dispatchers.IO) {
        exif.getMetadata(photo.uri)
    }

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
        // Process in chunks of 50 so that selecting hundreds of photos does not
        // create an unbounded number of coroutine objects. The exportDispatcher
        // still limits active concurrency to 4 within each chunk.
        val exportResults = photos.chunked(50).flatMap { chunk ->
            coroutineScope {
                chunk.map { photo ->
                    async(exportDispatcher) {
                        try {
                            stripper.createCleanCopy(photo, mode, outputDir = prefs.outputDirName)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Result.failure<ExportResult>(e)
                        }
                    }
                }.awaitAll()
            }
        }

        withContext(Dispatchers.IO) {
            exportResults.forEachIndexed { index, result ->
                result.getOrNull()?.let { exportResult ->
                    try {
                        logExport(photos[index], exportResult.filename, mode)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.w(TAG, "Export logging failed: ${e.javaClass.simpleName}")
                    }
                }
            }
        }

        return exportResults.map { it.map { r -> r.uri } }
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

    suspend fun getExportLogs(): List<ExportLogEntry> = withContext(Dispatchers.IO) {
        prefs.getExportLogs()
    }

    suspend fun clearExportLogs() = withContext(Dispatchers.IO) {
        prefs.clearExportLogs()
    }

    suspend fun getFavoriteIds(): Set<Long> = withContext(Dispatchers.IO) {
        prefs.getFavoriteIds()
    }

    suspend fun toggleFavorite(photoId: Long) = withContext(Dispatchers.IO) {
        prefs.toggleFavorite(photoId)
    }

    suspend fun isFavorite(photoId: Long): Boolean = withContext(Dispatchers.IO) {
        prefs.isFavorite(photoId)
    }

    suspend fun getSelectedIds(): Set<Long> = withContext(Dispatchers.IO) {
        prefs.getSelectedIds()
    }

    suspend fun setSelectedIds(ids: Set<Long>) = withContext(Dispatchers.IO) {
        prefs.setSelectedIds(ids)
    }

    suspend fun deletePhotos(photos: List<Photo>): DeleteResult = withContext(Dispatchers.IO) {
        mediaStore.delete(photos.map { it.uri })
    }

    suspend fun deletePhotosByUri(uris: List<Uri>): DeleteResult = withContext(Dispatchers.IO) {
        mediaStore.delete(uris)
    }

    // Typed preference accessors so callers do not bypass the repository.

    suspend fun getDefaultStripMode(): String = withContext(Dispatchers.IO) {
        prefs.defaultStripMode
    }

    suspend fun getHapticEnabled(): Boolean = withContext(Dispatchers.IO) {
        prefs.hapticEnabled
    }

    suspend fun getGalleryViewMode(): String = withContext(Dispatchers.IO) {
        prefs.galleryViewMode
    }

    suspend fun getSortOrder(): String = withContext(Dispatchers.IO) {
        prefs.sortOrder
    }

    suspend fun getGridSize(): String = withContext(Dispatchers.IO) {
        prefs.gridSize
    }

    suspend fun setSortOrder(order: String) = withContext(Dispatchers.IO) {
        prefs.sortOrder = order
    }

    suspend fun setGalleryViewMode(mode: String) = withContext(Dispatchers.IO) {
        prefs.galleryViewMode = mode
    }

    companion object {
        private const val TAG = "PhotoRepository"
    }
}
