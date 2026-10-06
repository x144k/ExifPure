package com.pureframe.exif.data.local

import com.pureframe.exif.data.model.ExportLogEntry

/**
 * Abstract storage for user preferences, favorites, and export history.
 *
 * Implementations may be backed by encrypted SharedPreferences, in-memory
 * maps (for testing), or other persistence layers.
 */
interface PreferenceStorage {
    var outputDirName: String
    var defaultStripMode: String
    var sortOrder: String
    var galleryViewMode: String
    var gridSize: String
    var fallbackQuality: Int
    var hapticEnabled: Boolean

    fun getFavoriteIds(): Set<Long>
    fun toggleFavorite(id: Long)
    fun isFavorite(id: Long): Boolean

    fun getSelectedIds(): Set<Long>
    fun setSelectedIds(ids: Set<Long>)

    fun addExportLog(entry: ExportLogEntry)
    fun getExportLogs(): List<ExportLogEntry>
    fun clearExportLogs()
}
