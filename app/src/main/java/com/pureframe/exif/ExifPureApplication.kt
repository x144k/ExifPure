package com.pureframe.exif

import android.app.Application
import com.pureframe.exif.di.AppContainer

class ExifPureApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
