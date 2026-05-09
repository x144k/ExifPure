package com.pureframe.exif.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class EncryptedPreferenceStorage(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "exifpure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var themeMode: String
        get() = prefs.getString(KEY_THEME, VALUE_SYSTEM) ?: VALUE_SYSTEM
        set(v) = prefs.edit().putString(KEY_THEME, v).apply()

    var outputDirName: String
        get() = prefs.getString(KEY_OUTPUT_DIR, "EXIFPure/Clean") ?: "EXIFPure/Clean"
        set(v) = prefs.edit().putString(KEY_OUTPUT_DIR, v).apply()

    var defaultStripMode: String
        get() = prefs.getString(KEY_DEFAULT_STRIP, STRIP_ALL) ?: STRIP_ALL
        set(v) = prefs.edit().putString(KEY_DEFAULT_STRIP, v).apply()

    var sortOrder: String
        get() = prefs.getString(KEY_SORT, SORT_DATE_ADDED_DESC) ?: SORT_DATE_ADDED_DESC
        set(v) = prefs.edit().putString(KEY_SORT, v).apply()

    var galleryViewMode: String
        get() = prefs.getString(KEY_GALLERY_VIEW, VIEW_PHOTOS) ?: VIEW_PHOTOS
        set(v) = prefs.edit().putString(KEY_GALLERY_VIEW, v).apply()

    var gridSize: String
        get() = prefs.getString(KEY_GRID, GRID_MEDIUM) ?: GRID_MEDIUM
        set(v) = prefs.edit().putString(KEY_GRID, v).apply()

    var fallbackQuality: Int
        get() = prefs.getInt(KEY_QUALITY, 95)
        set(v) = prefs.edit().putInt(KEY_QUALITY, v).apply()

    var hapticEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC, true)
        set(v) = prefs.edit().putBoolean(KEY_HAPTIC, v).apply()

    var silentShareEnabled: Boolean
        get() = prefs.getBoolean(KEY_SILENT_SHARE, false)
        set(v) = prefs.edit().putBoolean(KEY_SILENT_SHARE, v).apply()

    fun getFavoriteIds(): Set<Long> {
        return prefs.getStringSet(KEY_FAVORITES, emptySet())?.map { it.toLong() }?.toSet() ?: emptySet()
    }

    fun toggleFavorite(id: Long) {
        val current = prefs.getStringSet(KEY_FAVORITES, emptySet())?.toMutableSet() ?: mutableSetOf()
        val key = id.toString()
        if (current.contains(key)) current.remove(key) else current.add(key)
        prefs.edit().putStringSet(KEY_FAVORITES, current).apply()
    }

    fun isFavorite(id: Long): Boolean {
        return prefs.getStringSet(KEY_FAVORITES, emptySet())?.contains(id.toString()) ?: false
    }

    fun clearFavorites() {
        prefs.edit().remove(KEY_FAVORITES).apply()
    }

    fun addExportLog(entry: com.pureframe.exif.data.model.ExportLogEntry) {
        val current = getExportLogs().toMutableList()
        current.add(0, entry)
        if (current.size > 50) current.removeAt(current.lastIndex)
        val json = current.joinToString("\n") {
            "${it.id}|${it.originalName}|${it.exportedName}|${it.stripMode}|${it.timestamp}|${it.mimeType}"
        }
        prefs.edit().putString(KEY_EXPORT_LOG, json).apply()
    }

    fun getExportLogs(): List<com.pureframe.exif.data.model.ExportLogEntry> {
        val raw = prefs.getString(KEY_EXPORT_LOG, "") ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return raw.split("\n").mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size == 6) {
                try {
                    com.pureframe.exif.data.model.ExportLogEntry(
                        id = parts[0],
                        originalName = parts[1],
                        exportedName = parts[2],
                        stripMode = parts[3],
                        timestamp = parts[4].toLong(),
                        mimeType = parts[5]
                    )
                } catch (_: Exception) { null }
            } else null
        }
    }

    fun clearExportLogs() {
        prefs.edit().remove(KEY_EXPORT_LOG).apply()
    }

    var appLockEnabled: Boolean
        get() = prefs.getBoolean(KEY_APP_LOCK, false)
        set(v) = prefs.edit().putBoolean(KEY_APP_LOCK, v).apply()

    var appLockPinHash: String?
        get() = prefs.getString(KEY_APP_LOCK_PIN, null)
        set(v) = prefs.edit().putString(KEY_APP_LOCK_PIN, v).apply()

    var useBiometric: Boolean
        get() = prefs.getBoolean(KEY_USE_BIOMETRIC, false)
        set(v) = prefs.edit().putBoolean(KEY_USE_BIOMETRIC, v).apply()

    companion object {
        const val KEY_THEME = "theme"
        const val KEY_OUTPUT_DIR = "output_dir"
        const val KEY_DEFAULT_STRIP = "default_strip"
        const val KEY_SORT = "sort_order"
        const val KEY_GALLERY_VIEW = "gallery_view_mode"
        const val KEY_GRID = "grid_size"
        const val KEY_QUALITY = "fallback_quality"
        const val KEY_HAPTIC = "haptic_enabled"
        const val KEY_SILENT_SHARE = "silent_share"
        const val KEY_EXPORT_LOG = "export_log"
        const val KEY_APP_LOCK = "app_lock"
        const val KEY_APP_LOCK_PIN = "app_lock_pin"
        const val KEY_USE_BIOMETRIC = "use_biometric"
        const val KEY_FAVORITES = "favorites"

        const val VALUE_SYSTEM = "system"
        const val VALUE_LIGHT = "light"
        const val VALUE_DARK = "dark"

        const val STRIP_ALL = "all"
        const val STRIP_GPS = "gps"

        const val SORT_DATE_ADDED_DESC = "date_added_desc"
        const val SORT_DATE_ADDED_ASC = "date_added_asc"
        const val SORT_NAME_ASC = "name_asc"
        const val SORT_NAME_DESC = "name_desc"
        const val SORT_SIZE_DESC = "size_desc"

        const val GRID_SMALL = "small"
        const val GRID_MEDIUM = "medium"
        const val GRID_LARGE = "large"

        const val VIEW_PHOTOS = "photos"
        const val VIEW_ALBUMS = "albums"
    }
}
