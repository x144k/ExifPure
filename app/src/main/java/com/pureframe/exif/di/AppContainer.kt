package com.pureframe.exif.di

import android.content.Context
import com.pureframe.exif.data.local.EncryptedPreferenceStorage
import com.pureframe.exif.data.local.ExifDataSource
import com.pureframe.exif.data.local.MediaStoreDataSource
import com.pureframe.exif.data.local.MetadataStripper
import com.pureframe.exif.data.repository.PhotoRepository

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val prefs by lazy { EncryptedPreferenceStorage(appContext) }
    val mediaStore by lazy { MediaStoreDataSource(appContext.contentResolver) }
    val exif by lazy { ExifDataSource(appContext.contentResolver) }
    val stripper by lazy { MetadataStripper(appContext.contentResolver, prefs) }

    val repository by lazy {
        PhotoRepository(mediaStore, exif, stripper, prefs)
    }
}
