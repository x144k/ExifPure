package com.pureframe.exif.data.local

import android.net.Uri
import com.pureframe.exif.data.model.Photo
import com.pureframe.exif.data.model.StripMode

data class ExportResult(val uri: Uri, val filename: String)

interface MetadataStripper {
    fun createCleanCopy(
        photo: Photo,
        mode: StripMode = StripMode.ALL,
        outputDir: String = "EXIFPure/Clean"
    ): Result<ExportResult>

    companion object {
        const val MAX_EXPORT_BYTES = 200L * 1024 * 1024
    }
}
