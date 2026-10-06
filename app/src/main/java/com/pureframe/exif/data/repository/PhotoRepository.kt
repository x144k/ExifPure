package com.pureframe.exif.data.repository

import android.net.Uri
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
    fun getPhotos(): Flow<List<Photo>> = flow { emit(mediaStore.getPhotos()) }

    suspend fun getPhoto(id: Long): Photo? = mediaStore.getPhotoById(id)

    suspend fun getExif(photo: Photo): ExifMetadata = exif.getMetadata(photo.uri)

    suspend fun exportClean(photo: Photo, mode: StripMode = StripMode.ALL): Result<Uri> {
        val result = stripper.createCleanCopy(photo, mode, outputDir = prefs.outputDirName)
        val exportResult = result.getOrNull()
        if (exportResult != null) {
            logExport(photo, exportResult.filename, mode)
        }
        return result.map { it.uri }
    }

    suspend fun batchExport(photos: List<Photo>, mode: StripMode): List<Result<Uri>> {
        return photos.map { photo ->
            val result = stripper.createCleanCopy(photo, mode, outputDir = prefs.outputDirName)
            val exportResult = result.getOrNull()
            if (exportResult != null) {
                logExport(photo, exportResult.filename, mode)
            }
            result.map { it.uri }
        }
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
}
