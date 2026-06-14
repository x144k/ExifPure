package com.pureframe.exif.data.model

/**
 * Lightweight summary of EXIF presence for a single photo.
 *
 * @property scanFailed True when reading EXIF failed for this photo. Failed entries
 *           are treated as uncached so the next scan retries them instead of
 *           permanently caching a "no EXIF" result.
 */
data class ExifSummary(
    val photoId: Long,
    val hasExif: Boolean,
    val hasGps: Boolean,
    val cameraModel: String?,
    val scanFailed: Boolean = false
)
