package com.pureframe.exif.ui

import androidx.compose.runtime.compositionLocalOf
import com.pureframe.exif.di.AppContainer

val LocalAppContainer = compositionLocalOf<AppContainer> {
    error("AppContainer not provided")
}
