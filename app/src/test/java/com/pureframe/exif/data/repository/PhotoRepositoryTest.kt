package com.pureframe.exif.data.repository

import android.net.Uri
import com.pureframe.exif.data.local.DeleteResult
import com.pureframe.exif.data.local.ExifDataSource
import com.pureframe.exif.data.local.ExportResult
import com.pureframe.exif.data.local.MediaStoreDataSource
import com.pureframe.exif.data.local.MetadataStripper
import com.pureframe.exif.data.local.PreferenceStorage
import com.pureframe.exif.data.model.ExifMetadata
import com.pureframe.exif.data.model.ExportLogEntry
import com.pureframe.exif.data.model.Photo
import com.pureframe.exif.data.model.StripMode
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.mock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoRepositoryTest {

    private class FakeMediaStore : MediaStoreDataSource {
        var photos = emptyList<Photo>()
        override suspend fun getPhotos() = photos
        override suspend fun getPhotoById(id: Long) = photos.find { it.id == id }
        override suspend fun delete(uris: List<Uri>) = DeleteResult.Success(uris.size)
    }

    private class FakeExif : ExifDataSource {
        var metadata = ExifMetadata(
            make = null, model = null, lens = null,
            dateTimeOriginal = null, exposureTime = null,
            fNumber = null, iso = null, focalLength = null,
            gpsLatitude = null, gpsLongitude = null, gpsAltitude = null,
            imageWidth = null, imageLength = null,
            orientation = null, hasExif = false
        )
        override suspend fun getMetadata(uri: Uri) = metadata
    }

    private class FakeStripper : MetadataStripper {
        var shouldFail = false
        var delayMs = 0L

        override fun createCleanCopy(
            photo: Photo,
            mode: StripMode,
            outputDir: String
        ): Result<ExportResult> {
            if (delayMs > 0) Thread.sleep(delayMs)
            return if (shouldFail) {
                Result.failure(Exception("strip failed"))
            } else {
                Result.success(
                    ExportResult(
                        mock(Uri::class.java),
                        "test_${photo.id}.jpg"
                    )
                )
            }
        }
    }

    private class FakePrefs : PreferenceStorage {
        override var outputDirName = "EXIFPure/Clean"
        override var defaultStripMode = "all"
        override var sortOrder = "date_added_desc"
        override var galleryViewMode = "photos"
        override var gridSize = "medium"
        override var fallbackQuality = 95
        override var hapticEnabled = true

        private val favorites = mutableSetOf<Long>()
        private val logs = mutableListOf<ExportLogEntry>()

        override fun getFavoriteIds() = favorites.toSet()
        override fun toggleFavorite(id: Long) {
            if (favorites.contains(id)) favorites.remove(id) else favorites.add(id)
        }
        override fun isFavorite(id: Long) = favorites.contains(id)
        override fun addExportLog(entry: ExportLogEntry) {
            logs.add(entry)
        }
        override fun getExportLogs() = logs.toList()
        override fun clearExportLogs() = logs.clear()
    }

    private fun createPhoto(id: Long) = Photo(
        id = id,
        uri = mock(Uri::class.java),
        displayName = "photo_$id.jpg",
        dateAdded = id,
        dateModified = id,
        width = 100,
        height = 100,
        size = 1000L,
        mimeType = "image/jpeg",
        bucketId = "1",
        bucketDisplayName = "Camera"
    )

    @Test
    fun batchExport_processesAllPhotos() = runBlocking {
        val repo = PhotoRepository(FakeMediaStore(), FakeExif(), FakeStripper(), FakePrefs())
        val photos = listOf(createPhoto(1), createPhoto(2), createPhoto(3))

        val results = repo.batchExport(photos, StripMode.ALL)

        assertEquals(3, results.size)
        assertTrue(results.all { it.isSuccess })
    }

    @Test
    fun batchExport_handlesFailures() = runBlocking {
        val stripper = FakeStripper().apply { shouldFail = true }
        val repo = PhotoRepository(FakeMediaStore(), FakeExif(), stripper, FakePrefs())
        val photos = listOf(createPhoto(1), createPhoto(2))

        val results = repo.batchExport(photos, StripMode.ALL)

        assertEquals(2, results.size)
        assertTrue(results.all { it.isFailure })
    }

    @Test
    fun batchExport_logsSuccessfulExports() = runBlocking {
        val prefs = FakePrefs()
        val repo = PhotoRepository(FakeMediaStore(), FakeExif(), FakeStripper(), prefs)

        repo.batchExport(listOf(createPhoto(1)), StripMode.ALL)

        assertEquals(1, prefs.getExportLogs().size)
    }

    @Test
    fun batchExport_chunksLargeLists() = runBlocking {
        val repo = PhotoRepository(FakeMediaStore(), FakeExif(), FakeStripper(), FakePrefs())
        val photos = (1..120).map { createPhoto(it.toLong()) }

        val results = repo.batchExport(photos, StripMode.ALL)

        assertEquals(120, results.size)
        assertTrue(results.all { it.isSuccess })
    }

    @Test
    fun exportClean_returnsUriOnSuccess() = runBlocking {
        val repo = PhotoRepository(FakeMediaStore(), FakeExif(), FakeStripper(), FakePrefs())

        val result = repo.exportClean(createPhoto(1), StripMode.ALL)

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull() != null)
    }

    @Test
    fun preferenceAccessors_delegateToStorage() = runBlocking {
        val prefs = FakePrefs()
        val repo = PhotoRepository(FakeMediaStore(), FakeExif(), FakeStripper(), prefs)

        prefs.hapticEnabled = false
        assertFalse(repo.getHapticEnabled())

        prefs.defaultStripMode = "gps"
        assertEquals("gps", repo.getDefaultStripMode())
    }

    @Test
    fun deletePhotos_delegatesToMediaStore() = runBlocking {
        val mediaStore = FakeMediaStore()
        val repo = PhotoRepository(mediaStore, FakeExif(), FakeStripper(), FakePrefs())
        val photos = listOf(createPhoto(1), createPhoto(2))

        val result = repo.deletePhotos(photos)

        assertTrue(result is DeleteResult.Success)
        assertEquals(2, (result as DeleteResult.Success).count)
    }
}
