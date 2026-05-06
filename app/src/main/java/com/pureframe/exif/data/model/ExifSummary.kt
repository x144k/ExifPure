package com.pureframe.exif.data.model

data class ExifSummary(
    val photoId: Long,
    val hasExif: Boolean,
    val hasGps: Boolean,
    val cameraModel: String?
)
