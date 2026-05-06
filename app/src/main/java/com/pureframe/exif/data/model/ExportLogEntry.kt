package com.pureframe.exif.data.model

data class ExportLogEntry(
    val id: String,
    val originalName: String,
    val exportedName: String,
    val stripMode: String,
    val timestamp: Long,
    val mimeType: String
)
