package com.pureframe.exif.data.local

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.pureframe.exif.data.local.stripper.FallbackStripper
import com.pureframe.exif.data.local.stripper.JpegGpsStripper
import com.pureframe.exif.data.local.stripper.JpegStripper
import com.pureframe.exif.data.local.stripper.PngGpsStripper
import com.pureframe.exif.data.local.stripper.PngStripper
import com.pureframe.exif.data.model.Photo
import com.pureframe.exif.data.model.StripMode
import com.pureframe.exif.util.FilenameGenerator
import android.util.Log
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

data class ExportResult(val uri: Uri, val filename: String)

class MetadataStripper(
    private val resolver: ContentResolver,
    private val prefs: EncryptedPreferenceStorage
) {
    /**
     * Creates a clean copy of [photo] with metadata stripped according to [mode].
     *
     * **Dispatcher contract:** This function performs blocking I/O and must be
     * called from a coroutine running on [Dispatchers.IO] (or an equivalent
     * I/O dispatcher). Callers are responsible for dispatching; this method
     * does **not** wrap itself in [withContext] so that concurrency limits
     * imposed by the caller (e.g. [Dispatchers.IO.limitedParallelism]) are
     * respected rather than silently bypassed.
     */
    fun createCleanCopy(
        photo: Photo,
        mode: StripMode = StripMode.ALL,
        outputDir: String = "EXIFPure/Clean"
    ): Result<ExportResult> {
        val safeDir = sanitizeOutputDir(outputDir)

        var insertedUri: android.net.Uri? = null
        var success = false

        return try {
            // Create the temp file inside the try block so that any failure
            // here is wrapped in Result.failure rather than thrown raw.
            val temp = File.createTempFile("clean", ".tmp")
            try {
                resolver.openInputStream(photo.uri)?.use { stream ->
                    FileOutputStream(temp).use { out ->
                        // Enforce a hard byte limit during read to prevent malicious
                        // content providers from streaming unlimited data.
                        val limited = LimitedInputStream(stream, MAX_EXPORT_BYTES)
                        when (mode) {
                            StripMode.ALL -> when {
                                photo.mimeType.equals("image/jpeg", true) -> JpegStripper.strip(limited, out)
                                photo.mimeType.equals("image/png", true) -> PngStripper.strip(limited, out)
                                else -> FallbackStripper.strip(resolver, photo.uri, out, prefs.fallbackQuality, photo.mimeType)
                            }
                            StripMode.GPS_ONLY -> when {
                                photo.mimeType.equals("image/jpeg", true) -> JpegGpsStripper.strip(limited, out)
                                photo.mimeType.equals("image/png", true) -> PngGpsStripper.strip(limited, out)
                                else -> FallbackStripper.strip(resolver, photo.uri, out, prefs.fallbackQuality, photo.mimeType)
                            }
                        }
                    }
                } ?: return Result.failure(Exception("Cannot read source image"))

                val extension = photo.displayName.substringAfterLast(".", "")
                val randomName = FilenameGenerator.generate(extension)

                val relativePath = "${Environment.DIRECTORY_PICTURES}/$safeDir"
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, randomName)
                    put(MediaStore.Images.Media.MIME_TYPE, photo.mimeType)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }

                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

                val uri = resolver.insert(collection, values)
                    ?: return Result.failure(Exception("MediaStore insert failed"))
                insertedUri = uri

                val output = resolver.openOutputStream(uri)
                    ?: run {
                        resolver.delete(uri, null, null)
                        insertedUri = null
                        return Result.failure(Exception("Cannot write to MediaStore"))
                    }

                output.use { out ->
                    FileInputStream(temp).use { it.copyTo(out) }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }

                success = true
                Result.success(ExportResult(uri, randomName))
            } finally {
                // Clean up the MediaStore entry if anything went wrong, including
                // cancellation. Without this, pending entries pollute the gallery.
                val orphan = insertedUri
                if (!success && orphan != null) {
                    try {
                        resolver.delete(orphan, null, null)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.w(TAG, "Failed to delete orphan MediaStore entry: ${e.javaClass.simpleName}")
                    }
                }
                if (!temp.delete()) {
                    temp.deleteOnExit()
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    companion object {
        const val MAX_EXPORT_BYTES = 200L * 1024 * 1024
        private val SAFE_DIR_PATTERN = Regex("^[a-zA-Z0-9._ -]+")
        private const val TAG = "MetadataStripper"

        /**
         * Sanitizes a user-supplied output directory path.
         *
         * Rejects absolute paths, backslashes, empty components, and path
         * traversal (`.` or `..`). Each path segment must contain only
         * alphanumeric characters, dots, underscores, spaces, or hyphens.
         *
         * The returned value is the sanitized directory path *without* the
         * top-level media directory (e.g. `EXIFPure/Clean`). Callers must
         * prepend `Environment.DIRECTORY_PICTURES` when constructing a
         * MediaStore [RELATIVE_PATH].
         */
        fun sanitizeOutputDir(dir: String): String {
            val trimmed = dir.trim()
            if (trimmed.isEmpty()) return "EXIFPure/Clean"
            if (trimmed.startsWith("/")) return "EXIFPure/Clean"
            if (trimmed.contains("\\")) return "EXIFPure/Clean"

            val parts = trimmed.split("/")
            if (parts.any { it.isEmpty() }) return "EXIFPure/Clean"
            if (parts.any { it == "." || it == ".." }) return "EXIFPure/Clean"
            if (parts.any { !SAFE_DIR_PATTERN.matches(it) }) return "EXIFPure/Clean"

            return trimmed
        }
    }

    /**
     * Wraps an [InputStream] to throw [IOException] once [maxBytes] have been
     * read. This prevents malicious content providers from lying about size
     * metadata and then streaming unlimited data.
     */
    private class LimitedInputStream(
        private val wrapped: InputStream,
        private val maxBytes: Long
    ) : InputStream() {
        private var bytesRead = 0L

        override fun read(): Int {
            if (bytesRead >= maxBytes) throw ImageTooLargeException()
            val b = wrapped.read()
            if (b != -1) bytesRead++
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val remaining = maxBytes - bytesRead
            if (remaining <= 0) throw ImageTooLargeException()
            // Guard against overflow if maxBytes ever exceeds Int.MAX_VALUE.
            val toRead = len.coerceAtMost(remaining.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            if (toRead == 0) return 0
            val read = wrapped.read(b, off, toRead)
            if (read == -1) return -1
            if (read == 0) throw IOException("Stream returned zero bytes")
            if (read > 0) bytesRead += read
            return read
        }

        override fun available(): Int {
            val remaining = (maxBytes - bytesRead).coerceAtLeast(0)
            return wrapped.available().coerceAtMost(
                remaining.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            )
        }

        override fun skip(n: Long): Long {
            val toSkip = n.coerceAtMost(maxBytes - bytesRead).coerceAtLeast(0)
            // If the caller wants to skip but the limit is already hit,
            // throw so upstream code reports a size error instead of a
            // misleading EOF.
            if (toSkip == 0L && n > 0) {
                throw ImageTooLargeException()
            }
            if (toSkip == 0L) return 0L
            val skipped = wrapped.skip(toSkip)
            if (skipped < 0) throw IOException("Invalid skip return from underlying stream")
            bytesRead += skipped
            return skipped
        }

        override fun close() = wrapped.close()
    }
}
