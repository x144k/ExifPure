package com.pureframe.exif.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object FilenameGenerator {
    fun generate(extension: String): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val uuid = UUID.randomUUID().toString().take(8)
        val ext = extension.trimStart('.').lowercase()
        return "EXIFPure_${timestamp}_${uuid}.$ext"
    }
}
