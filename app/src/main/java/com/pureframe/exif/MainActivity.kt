package com.pureframe.exif

import android.os.Bundle
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pureframe.exif.ui.LocalAppContainer
import com.pureframe.exif.ui.navigation.Screen
import com.pureframe.exif.ui.screens.gallery.GalleryScreen
import com.pureframe.exif.ui.screens.detail.DetailScreen
import com.pureframe.exif.ui.screens.history.ExportHistoryScreen
import com.pureframe.exif.ui.screens.lock.AppLockScreen
import com.pureframe.exif.ui.screens.settings.SettingsScreen
import com.pureframe.exif.ui.screens.splash.SplashFlareScreen
import com.pureframe.exif.ui.screens.viewer.ImageViewerScreen
import com.pureframe.exif.data.local.EncryptedPreferenceStorage
import com.pureframe.exif.ui.theme.ExifPureTheme

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        val container = (application as ExifPureApplication).container

        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                val themeMode by container.themeModeFlow.collectAsState()
                val darkTheme = when (themeMode) {
                    EncryptedPreferenceStorage.VALUE_DARK -> true
                    EncryptedPreferenceStorage.VALUE_LIGHT -> false
                    else -> isSystemInDarkTheme()
                }
                ExifPureTheme(darkTheme = darkTheme) {
                    var showFlare by rememberSaveable { mutableStateOf(true) }
                    var isLocked by rememberSaveable { mutableStateOf(container.prefs.appLockEnabled) }
                    val navController = rememberNavController()

                    val lifecycleOwner = LocalLifecycleOwner.current
                    var hasHandledFirstResume by rememberSaveable { mutableStateOf(false) }
                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                if (hasHandledFirstResume && container.prefs.appLockEnabled) {
                                    isLocked = true
                                }
                                hasHandledFirstResume = true
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }

                    if (showFlare) {
                        SplashFlareScreen(onFinished = { showFlare = false })
                    } else if (isLocked) {
                        AppLockScreen(onUnlock = { isLocked = false })
                    } else {
                        NavHost(navController, startDestination = Screen.Gallery.route) {
                            composable(Screen.Gallery.route) {
                                GalleryScreen(
                                    onPhotoClick = { photo ->
                                        navController.navigate(Screen.Detail.createRoute(photo.id)) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onSettingsClick = {
                                        navController.navigate(Screen.Settings.route) {
                                            launchSingleTop = true
                                        }
                                    }
                                )
                            }
                            composable(Screen.Detail.route) { backStack ->
                                val id = backStack.arguments?.getString("photoId")?.toLongOrNull()
                                if (id != null) {
                                    DetailScreen(
                                        photoId = id,
                                        onBack = { navController.popBackStack() },
                                        onImageClick = {
                                            navController.navigate(Screen.Viewer.createRoute(id)) {
                                                launchSingleTop = true
                                            }
                                        }
                                    )
                                }
                            }
                            composable(Screen.Viewer.route) { backStack ->
                                val id = backStack.arguments?.getString("photoId")?.toLongOrNull()
                                if (id != null) {
                                    ImageViewerScreen(
                                        photoId = id,
                                        onBack = { navController.popBackStack() }
                                    )
                                }
                            }
                            composable(Screen.History.route) {
                                ExportHistoryScreen(onBack = { navController.popBackStack() })
                            }
                            composable(Screen.Settings.route) {
                                SettingsScreen(
                                    onBack = { navController.popBackStack() },
                                    onHistoryClick = {
                                        navController.navigate(Screen.History.route) {
                                            launchSingleTop = true
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
