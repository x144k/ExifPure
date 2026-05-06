package com.pureframe.exif.data.model

data class ExifMetadata(
    val make: String?,
    val model: String?,
    val lens: String?,
    val dateTimeOriginal: String?,
    val exposureTime: String?,
    val fNumber: String?,
    val iso: String?,
    val focalLength: String?,
    val gpsLatitude: String?,
    val gpsLongitude: String?,
    val gpsAltitude: String?,
    val imageWidth: Int?,
    val imageLength: Int?,
    val orientation: Int?,
    val hasExif: Boolean
)
