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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

data class ExportResult(val uri: Uri, val filename: String)

class MetadataStripper(
    private val resolver: ContentResolver,
    private val prefs: EncryptedPreferenceStorage
) {
    suspend fun createCleanCopy(
        photo: Photo,
        mode: StripMode = StripMode.ALL,
        outputDir: String = "EXIFPure/Clean"
    ): Result<ExportResult> = withContext(Dispatchers.IO) {
        val temp = File.createTempFile("clean", ".tmp")
        try {
            resolver.openInputStream(photo.uri)?.use { input ->
                FileOutputStream(temp).use { out ->
                    when (mode) {
                        StripMode.ALL -> when {
                            photo.mimeType.equals("image/jpeg", true) -> JpegStripper.strip(input, out)
                            photo.mimeType.equals("image/png", true) -> PngStripper.strip(input, out)
                            else -> FallbackStripper.strip(resolver, photo.uri, out, prefs.fallbackQuality)
                        }
                        StripMode.GPS_ONLY -> when {
                            photo.mimeType.equals("image/jpeg", true) -> JpegGpsStripper.strip(input, out)
                            photo.mimeType.equals("image/png", true) -> PngGpsStripper.strip(input, out)
                            else -> FallbackStripper.strip(resolver, photo.uri, out, prefs.fallbackQuality)
                        }
                    }
                }
            }

            val extension = photo.displayName.substringAfterLast(".", "")
            val randomName = FilenameGenerator.generate(extension)

            val relativePath = "${Environment.DIRECTORY_PICTURES}/$outputDir"
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
                ?: return@withContext Result.failure(Exception("MediaStore insert failed"))

            resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(temp).use { it.copyTo(out) }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }

            Result.success(ExportResult(uri, randomName))
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            temp.delete()
        }
    }
}
