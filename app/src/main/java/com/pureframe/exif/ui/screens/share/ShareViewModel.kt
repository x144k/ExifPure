package com.pureframe.exif.ui.screens.share

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pureframe.exif.data.local.EncryptedPreferenceStorage
import com.pureframe.exif.data.model.Photo
import com.pureframe.exif.data.model.StripMode
import com.pureframe.exif.data.repository.PhotoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ShareUiState {
    data object Idle : ShareUiState()
    data class Processing(val current: Int, val total: Int) : ShareUiState()
    data class Success(val results: List<ShareResult>) : ShareUiState()
    data class Error(val message: String) : ShareUiState()
}

data class ShareResult(
    val originalName: String,
    val success: Boolean,
    val cleanUri: Uri? = null,
    val error: String? = null
)

class ShareViewModel(
    private val repository: PhotoRepository,
    private val resolver: ContentResolver,
    private val uris: List<Uri>
) : ViewModel() {

    private val _uiState = MutableStateFlow<ShareUiState>(ShareUiState.Idle)
    val uiState: StateFlow<ShareUiState> = _uiState.asStateFlow()

    private var isProcessing = false

    fun startProcessing() {
        if (isProcessing) return
        isProcessing = true
        viewModelScope.launch {
            _uiState.value = ShareUiState.Processing(0, uris.size)
            val results = mutableListOf<ShareResult>()

            try {
                uris.forEachIndexed { index, uri ->
                    _uiState.value = ShareUiState.Processing(index + 1, uris.size)
                    val photo = try {
                        createPhotoFromUri(uri)
                    } catch (e: Exception) {
                        results.add(
                            ShareResult(
                                originalName = uri.lastPathSegment ?: "unknown",
                                success = false,
                                error = e.message ?: "Failed to read image"
                            )
                        )
                        return@forEachIndexed
                    }

                    val mode = if (repository.prefs.defaultStripMode == EncryptedPreferenceStorage.STRIP_GPS) {
                        StripMode.GPS_ONLY
                    } else {
                        StripMode.ALL
                    }
                    val result = repository.exportClean(photo, mode)
                    results.add(
                        ShareResult(
                            originalName = photo.displayName,
                            success = result.isSuccess,
                            cleanUri = result.getOrNull(),
                            error = result.exceptionOrNull()?.message
                        )
                    )
                }

                _uiState.value = ShareUiState.Success(results)
            } catch (e: Exception) {
                _uiState.value = ShareUiState.Error(e.message ?: "Processing failed")
            }
        }
    }

    private fun createPhotoFromUri(uri: Uri): Photo {
        var displayName = uri.lastPathSegment ?: "shared_image"
        var mimeType = "image/jpeg"
        var size = 0L
        var width = 0
        var height = 0

        try {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                        .takeIf { it >= 0 }
                        ?.let { displayName = cursor.getString(it) ?: displayName }
                    cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
                        .takeIf { it >= 0 }
                        ?.let { cursor.getString(it)?.let { m -> mimeType = m } }
                    cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
                        .takeIf { it >= 0 }
                        ?.let { size = cursor.getLong(it) }
                    cursor.getColumnIndex(MediaStore.Images.Media.WIDTH)
                        .takeIf { it >= 0 }
                        ?.let { width = cursor.getInt(it) }
                    cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT)
                        .takeIf { it >= 0 }
                        ?.let { height = cursor.getInt(it) }
                }
            }
        } catch (_: Exception) { }

        if (displayName == uri.lastPathSegment) {
            try {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                .takeIf { it >= 0 }
                                ?.let { displayName = cursor.getString(it) ?: displayName }
                        }
                    }
            } catch (_: Exception) { }
        }
        if (size == 0L) {
            try {
                resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
                    ?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            cursor.getColumnIndex(OpenableColumns.SIZE)
                                .takeIf { it >= 0 }
                                ?.let { size = cursor.getLong(it) }
                        }
                    }
            } catch (_: Exception) { }
        }

        if (width == 0 || height == 0) {
            try {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }
                width = options.outWidth
                height = options.outHeight
            } catch (_: Exception) { }
        }

        return Photo(
            id = -1L,
            uri = uri,
            displayName = displayName,
            dateAdded = System.currentTimeMillis(),
            dateModified = System.currentTimeMillis(),
            width = width,
            height = height,
            size = size,
            mimeType = mimeType,
            bucketDisplayName = null,
            bucketId = null
        )
    }

    class Factory(
        private val repository: PhotoRepository,
        private val resolver: ContentResolver,
        private val uris: List<Uri>
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ShareViewModel(repository, resolver, uris) as T
        }
    }
}
