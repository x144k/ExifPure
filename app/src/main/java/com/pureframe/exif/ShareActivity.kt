package com.pureframe.exif

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
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
import androidx.lifecycle.repeatOnLifecycle
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
    private var lastPauseTime = 0L
    // Prevents silent-path chooser/Toast from re-firing on every config change.
    private var silentResultHandled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        wasBackgrounded = savedInstanceState?.getBoolean("was_bg", false) ?: false
        silentResultHandled = savedInstanceState?.getBoolean("silent_handled", false) ?: false
        // Use -1 as a sentinel for missing lastPauseTime so a fresh launch or
        // restore from an older version does not treat epoch zero as a long
        // backgrounding period (which would always trigger re-authentication).
        lastPauseTime = if (savedInstanceState?.containsKey("last_pause") == true) {
            savedInstanceState.getLong("last_pause")
        } else {
            -1L
        }

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
                // Use the application resolver to avoid leaking the Activity
                // across configuration changes.
                resolver = applicationContext.contentResolver,
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
        if (silentResultHandled) {
            if (!isFinishing) finish()
            return
        }
        lifecycleScope.launch {
            // Collect only while the activity is started so the coroutine does
            // not hold the activity alive while backgrounded.
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is ShareUiState.Success -> {
                            if (silentResultHandled) {
                                if (!isFinishing) finish()
                                return@collect
                            }
                            silentResultHandled = true
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
                                } catch (e: Exception) {
                                    // Starting an activity from a background or
                                    // finishing context throws on API 29+.
                                    Log.w(TAG, "Failed to launch share chooser: ${e.javaClass.simpleName}")
                                }
                            } else {
                                val failed = state.results.firstOrNull { !it.success }
                                val message = resolveErrorMessage(failed?.error)
                                Toast.makeText(this@ShareActivity, message, Toast.LENGTH_SHORT).show()
                            }
                            if (!isFinishing) finish()
                        }
                        is ShareUiState.Error -> {
                            if (silentResultHandled) {
                                if (!isFinishing) finish()
                                return@collect
                            }
                            silentResultHandled = true
                            Toast.makeText(
                                this@ShareActivity,
                                state.message,
                                Toast.LENGTH_SHORT
                            ).show()
                            if (!isFinishing) finish()
                        }
                        else -> { }
                    }
                }
            }
        }
        // Avoid restarting processing if the ViewModel already finished (e.g.
        // after a config change) or if the silent path was already handled
        // before process death. This prevents wasted I/O and duplicate toasts.
        if (!silentResultHandled && viewModel.uiState.value is ShareUiState.Idle) {
            viewModel.startProcessing()
        }
    }

    private fun resolveErrorMessage(error: String?): String {
        return when {
            error.isNullOrEmpty() -> getString(R.string.share_error_generic)
            error.contains("corrupted", ignoreCase = true) ->
                getString(R.string.share_error_corrupted)
            error.contains("storage", ignoreCase = true) ->
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
                                lastPauseTime = System.currentTimeMillis()
                            }
                        }
                        Lifecycle.Event.ON_RESUME -> {
                            val goneFor = System.currentTimeMillis() - lastPauseTime
                            if (wasBackgrounded && lastPauseTime >= 0 && container.prefs.appLockEnabled && goneFor > 2000) {
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
        outState.putBoolean("silent_handled", silentResultHandled)
        outState.putLong("last_pause", lastPauseTime)
    }

    @Suppress("DEPRECATION")
    private fun extractUrisFromIntent(intent: Intent): List<Uri> {
        val uris = mutableListOf<Uri>()

        // Only accept URIs from the standard share actions. Processing clipData
        // regardless of action allows nuisance vectors from unexpected intents.
        when (intent.action) {
            Intent.ACTION_SEND -> {
                intent.clipData?.let { clip ->
                    for (i in 0 until clip.itemCount) {
                        clip.getItemAt(i).uri?.let { uris.add(it) }
                    }
                }
                // Use the class-aware overload on API 33+ to avoid type-erasure
                // surprises; fall back to a safe cast on older versions.
                val streamUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                }
                streamUri?.let { uris.add(it) }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                intent.clipData?.let { clip ->
                    for (i in 0 until clip.itemCount) {
                        clip.getItemAt(i).uri?.let { uris.add(it) }
                    }
                }
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.addAll(it) }
            }
        }
        return uris.filterIsInstance<Uri>().distinct().filter { uri ->
            if (uri.scheme != "content") return@filter false
            val mime = try {
                contentResolver.getType(uri)
            } catch (_: SecurityException) {
                return@filter false
            }
            mime?.startsWith("image/") == true
        }
    }

    companion object {
        private const val TAG = "ShareActivity"
    }
}
