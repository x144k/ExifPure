package com.pureframe.exif.data.local

/**
 * Thrown when an input image exceeds the maximum allowed export size.
 * Used uniformly across [MetadataStripper], [FallbackStripper], and
 * [com.pureframe.exif.ui.screens.share.ShareViewModel] so that callers
 * can surface a consistent user-facing message.
 */
class ImageTooLargeException(message: String = "Image exceeds 200 MB size limit") : Exception(message)
