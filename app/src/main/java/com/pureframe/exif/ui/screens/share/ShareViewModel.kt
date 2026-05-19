package com.pureframe.exif.ui.screens.share

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pureframe.exif.data.local.EncryptedPreferenceStorage
import com.pureframe.exif.data.local.ImageTooLargeException
import com.pureframe.exif.data.local.MetadataStripper
import com.pureframe.exif.data.model.Photo
import com.pureframe.exif.data.model.StripMode
import com.pureframe.exif.data.repository.PhotoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

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

    private val isProcessing = AtomicBoolean(false)

    fun startProcessing() {
        // Single concurrency gate: AtomicBoolean CAS. We intentionally do NOT
        // pre-check _uiState.value because that creates a TOCTOU race between
        // the read and the CAS. The AtomicBoolean alone is sufficient.
        if (!isProcessing.compareAndSet(false, true)) return

        viewModelScope.launch {
            try {
                if (uris.isEmpty()) {
                    _uiState.value = ShareUiState.Error("No images to process")
                    return@launch
                }
                if (uris.size > MAX_URI_COUNT) {
                    _uiState.value = ShareUiState.Error("Too many images (max $MAX_URI_COUNT)")
                    return@launch
                }

                _uiState.value = ShareUiState.Processing(0, uris.size)
                val defaultMode = repository.prefs.defaultStripMode
                val results = mutableListOf<ShareResult>()

                uris.forEachIndexed { index, uri ->
                    _uiState.value = ShareUiState.Processing(index + 1, uris.size)
                    val result = withContext(Dispatchers.IO) {
                        val photo = try {
                            createPhotoFromUri(uri)
                        } catch (e: SecurityException) {
                            return@withContext ShareResult(
                                originalName = uri.lastPathSegment ?: "unknown",
                                success = false,
                                error = "Access denied by the sharing app"
                            )
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            Log.w(TAG, "Failed to read image #${index + 1}: ${e.javaClass.simpleName}")
                            return@withContext ShareResult(
                                originalName = uri.lastPathSegment ?: "unknown",
                                success = false,
                                error = when (e) {
                                    is IllegalArgumentException -> "Unsupported or corrupted image"
                                    else -> "Failed to read image"
                                }
                            )
                        }

                        ensureActive()

                        val mode = if (defaultMode == EncryptedPreferenceStorage.STRIP_GPS) {
                            StripMode.GPS_ONLY
                        } else {
                            StripMode.ALL
                        }
                        val exportResult = repository.exportClean(photo, mode)
                        val exception = exportResult.exceptionOrNull()
                        if (exception != null) {
                            Log.w(TAG, "Export failed for image #${index + 1}: ${exception.javaClass.simpleName}")
                        }
                        ShareResult(
                            originalName = photo.displayName,
                            success = exportResult.isSuccess,
                            cleanUri = exportResult.getOrNull(),
                            error = when {
                                exception == null -> null
                                exception is ImageTooLargeException -> "Image exceeds 200 MB size limit"
                                exception is IllegalArgumentException -> "Unsupported or corrupted image"
                                exception is IllegalStateException -> "Unsupported or corrupted image"
                                exception is java.io.EOFException -> "Unsupported or corrupted image"
                                exception.message?.contains("MediaStore", ignoreCase = true) == true ->
                                    "Unable to save to device storage"
                                else -> "Export failed"
                            }
                        )
                    }
                    results.add(result)
                }

                _uiState.value = ShareUiState.Success(results)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Batch processing failed: ${e.javaClass.simpleName}")
                _uiState.value = ShareUiState.Error("Processing failed")
            } finally {
                isProcessing.set(false)
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
                        ?.let { displayName = (cursor.getString(it) ?: displayName).take(255) }
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
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Primary query failed for image: ${e.javaClass.simpleName}")
        }

        if (displayName == uri.lastPathSegment) {
            try {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                .takeIf { it >= 0 }
                                ?.let { displayName = (cursor.getString(it) ?: displayName).take(255) }
                        }
                    }
            } catch (e: SecurityException) {
                throw e
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "OpenableColumns query failed for image: ${e.javaClass.simpleName}")
            }
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
            } catch (e: SecurityException) {
                throw e
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "Size query failed for image: ${e.javaClass.simpleName}")
            }
        }

        if (size == 0L) {
            try {
                resolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    afd.length.takeIf { it > 0 }?.let { size = it }
                }
            } catch (e: SecurityException) {
                throw e
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "AssetFileDescriptor probe failed for image: ${e.javaClass.simpleName}")
            }
        }

        // Reject unknown or explicitly negative sizes (-1 from some providers).
        if (size < 0L || size > MetadataStripper.MAX_EXPORT_BYTES) {
            throw ImageTooLargeException("Image exceeds 200 MB size limit or size is unknown")
        }

        // BitmapFactory returns -1 for unknown dimensions; treat non-positive
        // values as missing so we attempt a fallback decode.
        if (width <= 0 || height <= 0) {
            try {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }
                width = options.outWidth
                height = options.outHeight
            } catch (e: SecurityException) {
                throw e
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "Bounds decode failed for image: ${e.javaClass.simpleName}")
            }
        }

        // If dimensions are still unknown after the fallback decode, reject the
        // image so downstream code does not divide by zero computing aspect ratio.
        if (width <= 0 || height <= 0) {
            throw IllegalArgumentException("Cannot determine image dimensions")
        }

        val nowSec = System.currentTimeMillis() / 1000
        return Photo(
            id = -1L,
            uri = uri,
            displayName = displayName,
            dateAdded = nowSec,
            dateModified = nowSec,
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

    companion object {
        private const val TAG = "ShareViewModel"
        private const val MAX_URI_COUNT = 50
    }
}
