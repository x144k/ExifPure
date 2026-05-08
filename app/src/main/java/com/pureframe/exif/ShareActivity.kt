package com.pureframe.exif

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import com.pureframe.exif.data.local.EncryptedPreferenceStorage
import com.pureframe.exif.ui.screens.lock.AppLockScreen
import com.pureframe.exif.ui.screens.share.ShareProcessingScreen
import com.pureframe.exif.ui.screens.share.ShareViewModel
import com.pureframe.exif.ui.theme.ExifPureTheme

class ShareActivity : androidx.fragment.app.FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as ExifPureApplication).container
        val uris = extractUrisFromIntent(intent)

        if (uris.isEmpty()) {
            finish()
            return
        }

        val viewModel = ViewModelProvider(
            this,
            ShareViewModel.Factory(
                repository = container.repository,
                resolver = contentResolver,
                uris = uris
            )
        )[ShareViewModel::class.java]

        setContent {
            var isLocked by rememberSaveable { mutableStateOf(container.prefs.appLockEnabled) }
            val themeMode by container.themeModeFlow.collectAsState()
            val darkTheme = when (themeMode) {
                EncryptedPreferenceStorage.VALUE_DARK -> true
                EncryptedPreferenceStorage.VALUE_LIGHT -> false
                else -> isSystemInDarkTheme()
            }
            ExifPureTheme(darkTheme = darkTheme) {
                if (isLocked) {
                    AppLockScreen(onUnlock = { isLocked = false })
                } else {
                    ShareProcessingScreen(
                        viewModel = viewModel,
                        onFinish = { finish() }
                    )
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun extractUrisFromIntent(intent: Intent): List<Uri> {
        val uris = mutableListOf<Uri>()
        when (intent.action) {
            Intent.ACTION_SEND -> {
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.add(it) }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.addAll(it) }
            }
        }
        return uris
    }
}
