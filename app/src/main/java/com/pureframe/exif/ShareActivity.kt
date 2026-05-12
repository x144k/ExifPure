package com.pureframe.exif

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.pureframe.exif.data.local.EncryptedPreferenceStorage
import com.pureframe.exif.di.AppContainer
import com.pureframe.exif.ui.screens.lock.AppLockScreen
import com.pureframe.exif.ui.screens.share.ShareProcessingScreen
import com.pureframe.exif.ui.screens.share.ShareUiState
import com.pureframe.exif.ui.screens.share.ShareViewModel
import com.pureframe.exif.ui.theme.ExifPureTheme
import kotlinx.coroutines.launch

class ShareActivity : androidx.fragment.app.FragmentActivity() {

    // Same background detection pattern as MainActivity:
    // ON_PAUSE is more reliable than ON_STOP for detecting genuine backgrounding
    // on API < 28 and during split-screen transitions.
    private var wasBackgrounded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        wasBackgrounded = savedInstanceState?.getBoolean("was_bg", false) ?: false

        val container = (application as ExifPureApplication).container
        val uris = extractUrisFromIntent(intent)

        if (uris.isEmpty()) {
            Toast.makeText(this, getString(R.string.share_no_images), Toast.LENGTH_SHORT).show()
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

        val silentMode = container.prefs.silentShareEnabled && uris.size == 1

        if (silentMode && !container.prefs.appLockEnabled) {
            handleSilentPath(viewModel)
        } else {
            showShareUi(viewModel, container)
        }
    }

    private fun handleSilentPath(viewModel: ShareViewModel) {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is ShareUiState.Success -> {
                        val cleanUris = state.results.mapNotNull { it.cleanUri }
                        if (cleanUris.isNotEmpty()) {
                            Toast.makeText(
                                this@ShareActivity,
                                getString(R.string.share_metadata_stripped),
                                Toast.LENGTH_SHORT
                            ).show()
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/*"
                                putExtra(Intent.EXTRA_STREAM, cleanUris.first())
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            val chooser = Intent.createChooser(
                                intent,
                                getString(R.string.share_clean_copy)
                            ).apply {
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            try {
                                startActivity(chooser)
                            } catch (_: android.content.ActivityNotFoundException) {
                                Toast.makeText(
                                    this@ShareActivity,
                                    getString(R.string.share_error_generic),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else {
                            val failed = state.results.firstOrNull { !it.success }
                            val message = resolveErrorMessage(failed?.error)
                            Toast.makeText(this@ShareActivity, message, Toast.LENGTH_SHORT).show()
                        }
                        finish()
                    }
                    is ShareUiState.Error -> {
                        Toast.makeText(
                            this@ShareActivity,
                            state.message,
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    }
                    else -> { }
                }
            }
        }
        viewModel.startProcessing()
    }

    private fun resolveErrorMessage(error: String?): String {
        return when {
            error.isNullOrEmpty() -> getString(R.string.share_error_generic)
            error.contains("EOF", ignoreCase = true) ->
                getString(R.string.share_error_corrupted)
            error.contains("Invalid JPEG", ignoreCase = true) ->
                getString(R.string.share_error_unsupported)
            error.contains("MediaStore", ignoreCase = true) ->
                getString(R.string.share_error_storage)
            else -> getString(R.string.share_error_generic)
        }
    }

    private fun showShareUi(viewModel: ShareViewModel, container: AppContainer) {
        setContent {
            var isLocked by rememberSaveable { mutableStateOf(container.prefs.appLockEnabled) }
            val themeMode by container.themeModeFlow.collectAsState()
            val darkTheme = when (themeMode) {
                EncryptedPreferenceStorage.VALUE_DARK -> true
                EncryptedPreferenceStorage.VALUE_LIGHT -> false
                else -> isSystemInDarkTheme()
            }

            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_PAUSE -> {
                            val activity = lifecycleOwner as? android.app.Activity
                            if (activity?.isChangingConfigurations != true) {
                                wasBackgrounded = true
                            }
                        }
                        Lifecycle.Event.ON_RESUME -> {
                            if (wasBackgrounded && container.prefs.appLockEnabled) {
                                isLocked = true
                            }
                            wasBackgrounded = false
                        }
                        else -> { /* no-op */ }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("was_bg", wasBackgrounded)
    }

    @Suppress("DEPRECATION")
    private fun extractUrisFromIntent(intent: Intent): List<Uri> {
        val uris = mutableListOf<Uri>()

        intent.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) {
                clip.getItemAt(i).uri?.let { uris.add(it) }
            }
        }

        when (intent.action) {
            Intent.ACTION_SEND -> {
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.add(it) }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.addAll(it) }
            }
        }
        return uris.distinct().filter { uri ->
            if (uri.scheme != "content") return@filter false
            val mime = try {
                contentResolver.getType(uri)
            } catch (_: SecurityException) {
                return@filter false
            }
            mime?.startsWith("image/") == true
        }
    }
}
