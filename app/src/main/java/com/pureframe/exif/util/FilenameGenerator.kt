package com.pureframe.exif.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object FilenameGenerator {
    fun generate(extension: String): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(Date())
        val uuid = UUID.randomUUID().toString().take(8)
        // Sanitize the extension to prevent path-injection via malicious
        // DISPLAY_NAME values such as "photo.jpg/../../../evil".
        val ext = extension.trimStart('.').lowercase()
            .replace(Regex("[^a-z0-9]"), "")
            .takeIf { it.isNotEmpty() && it != "." && it != ".." } ?: ""
        return if (ext.isEmpty()) {
            "EXIFPure_${timestamp}_${uuid}"
        } else {
            "EXIFPure_${timestamp}_${uuid}.$ext"
        }
    }
}
