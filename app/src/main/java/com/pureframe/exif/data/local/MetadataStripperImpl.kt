package com.pureframe.exif.data.local

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.pureframe.exif.data.local.stripper.FallbackStripper
import com.pureframe.exif.data.local.stripper.JpegGpsStripper
import com.pureframe.exif.data.local.stripper.JpegStripper
import com.pureframe.exif.data.local.stripper.PngGpsStripper
import com.pureframe.exif.data.local.stripper.PngStripper
import com.pureframe.exif.data.model.Photo
import com.pureframe.exif.data.model.StripMode
import com.pureframe.exif.util.FilenameGenerator
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

class MetadataStripperImpl(
    private val resolver: ContentResolver,
    private val prefs: PreferenceStorage
) : MetadataStripper {

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
    override fun createCleanCopy(
        photo: Photo,
        mode: StripMode,
        outputDir: String
    ): Result<ExportResult> {
        val safeDir = sanitizeOutputDir(outputDir)

        var insertedUri: android.net.Uri? = null
        var success = false

        return try {
            val temp = File.createTempFile("clean", ".tmp")
            try {
                resolver.openInputStream(photo.uri)?.use { stream ->
                    FileOutputStream(temp).use { out ->
                        val limited = LimitedInputStream(stream, MetadataStripper.MAX_EXPORT_BYTES)
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
        private val SAFE_DIR_PATTERN = Regex("^[a-zA-Z0-9._ -]+")
        private const val TAG = "MetadataStripper"

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
