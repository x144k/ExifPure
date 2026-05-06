package com.pureframe.exif.di

import android.content.Context
import com.pureframe.exif.data.local.EncryptedPreferenceStorage
import com.pureframe.exif.data.local.ExifDataSource
import com.pureframe.exif.data.local.MediaStoreDataSource
import com.pureframe.exif.data.local.MetadataStripper
import com.pureframe.exif.data.repository.PhotoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val prefs by lazy { EncryptedPreferenceStorage(appContext) }
    val mediaStore by lazy { MediaStoreDataSource(appContext.contentResolver) }
    val exif by lazy { ExifDataSource(appContext.contentResolver) }
    val stripper by lazy { MetadataStripper(appContext.contentResolver, prefs) }

    val repository by lazy {
        PhotoRepository(mediaStore, exif, stripper, prefs)
    }

    private val _themeModeFlow = MutableStateFlow(prefs.themeMode)
    val themeModeFlow: StateFlow<String> = _themeModeFlow.asStateFlow()

    fun setThemeMode(mode: String) {
        prefs.themeMode = mode
        _themeModeFlow.value = mode
    }
}
