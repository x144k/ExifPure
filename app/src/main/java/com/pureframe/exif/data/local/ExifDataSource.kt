package com.pureframe.exif.data.local

import android.content.ContentResolver
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.pureframe.exif.data.model.ExifMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ExifDataSource(private val resolver: ContentResolver) {

    suspend fun getMetadata(uri: Uri): ExifMetadata = withContext(Dispatchers.IO) {
        resolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            ExifMetadata(
                make = exif.getAttribute(ExifInterface.TAG_MAKE),
                model = exif.getAttribute(ExifInterface.TAG_MODEL),
                lens = exif.getAttribute(ExifInterface.TAG_LENS_MODEL),
                dateTimeOriginal = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL),
                exposureTime = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME),
                fNumber = exif.getAttribute(ExifInterface.TAG_F_NUMBER),
                iso = exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY),
                focalLength = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH),
                gpsLatitude = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE),
                gpsLongitude = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE),
                gpsAltitude = exif.getAttribute(ExifInterface.TAG_GPS_ALTITUDE),
                imageWidth = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0).takeIf { it > 0 },
                imageLength = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0).takeIf { it > 0 },
                orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED),
                hasExif = exif.getAttribute(ExifInterface.TAG_MAKE) != null ||
                        exif.getAttribute(ExifInterface.TAG_MODEL) != null ||
                        exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) != null ||
                        exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE) != null ||
                        exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE) != null ||
                        exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME) != null ||
                        exif.getAttribute(ExifInterface.TAG_F_NUMBER) != null ||
                        exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY) != null
            )
        } ?: ExifMetadata(
            null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, false
        )
    }
}
