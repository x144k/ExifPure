package com.pureframe.exif.di

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.pureframe.exif.data.local.EncryptedPreferenceStorage
import com.pureframe.exif.data.local.ExifDataSourceImpl
import com.pureframe.exif.data.local.MediaStoreDataSourceImpl
import com.pureframe.exif.data.local.MetadataStripperImpl
import com.pureframe.exif.data.repository.PhotoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    // EncryptedPreferenceStorage constructor is now lightweight because the
    // heavy keystore work is deferred to the first internal property access.
    // The init block below forces that first access on a background thread.
    val prefs = EncryptedPreferenceStorage(appContext)
    val mediaStore by lazy { MediaStoreDataSourceImpl(appContext.contentResolver) }
    val exif by lazy { ExifDataSourceImpl(appContext.contentResolver) }
    val stripper by lazy { MetadataStripperImpl(appContext.contentResolver, prefs) }

    val repository by lazy {
        PhotoRepository(mediaStore, exif, stripper, prefs)
    }

    // Start with the system default so the first frame is never blocked waiting
    // for EncryptedSharedPreferences keystore initialization.
    private val _themeModeFlow = MutableStateFlow(EncryptedPreferenceStorage.VALUE_SYSTEM)
    val themeModeFlow: StateFlow<String> = _themeModeFlow.asStateFlow()

    // Guard against the background pre-warm thread overwriting an explicit
    // theme change that happens before the thread posts its result.
    @Volatile
    private var hasExplicitThemeOverride = false

    init {
        // EncryptedSharedPreferences triggers AES-256 MasterKey creation on the
        // first read. That blocks the main thread for 100-300 ms on cold start.
        // Force the first access on a background thread so the UI never pays
        // the full cost on the main thread.
        Thread {
            val mode = prefs.themeMode
            Handler(Looper.getMainLooper()).post {
                if (!hasExplicitThemeOverride) {
                    _themeModeFlow.value = mode
                }
            }
        }.apply {
            name = "ExifPure-PrefsInit"
            isDaemon = true
            start()
        }
    }

    fun setThemeMode(mode: String) {
        hasExplicitThemeOverride = true
        prefs.themeMode = mode
        _themeModeFlow.value = mode
    }
}
