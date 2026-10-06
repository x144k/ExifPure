package com.pureframe.exif.ui.screens.lock

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.pureframe.exif.ExifPureApplication
import com.pureframe.exif.R
import kotlinx.coroutines.delay

/**
 * Biometric lock screen. Prompts for strong biometric authentication and
 * handles all edge cases (missing hardware, unenrolled biometrics, lockout,
 * activity death during prompt, and stale callbacks after disposal/timeout).
 */
@Composable
fun AppLockScreen(
    onUnlock: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { (context.applicationContext as ExifPureApplication).container.prefs }

    var status by remember { mutableStateOf<LockStatus>(LockStatus.Checking) }

    // Holds the active prompt so we can cancel it on disposal or timeout,
    // and so stale callbacks from an orphaned prompt are ignored.
    val promptHolder: MutableState<BiometricPrompt?> = remember { mutableStateOf(null) }

    // Cancel any active prompt when the screen leaves composition to prevent
    // leaked callbacks and to clear the prompt from the system UI.
    DisposableEffect(Unit) {
        onDispose {
            val old = promptHolder.value
            promptHolder.value = null
            try {
                old?.cancelAuthentication()
            } catch (_: RuntimeException) {
                // ignored
            }
        }
    }

    // Safety net: if the framework never calls back (OEM bug, buried dialog),
    // cancel the prompt and surface a cancellable error after 30 seconds.
    LaunchedEffect(status) {
        if (status == LockStatus.Authenticating) {
            delay(30_000)
            if (status == LockStatus.Authenticating) {
                val old = promptHolder.value
                promptHolder.value = null
                try {
                    old?.cancelAuthentication()
                } catch (_: RuntimeException) {
                    // ignored
                }
                status = LockStatus.Cancelled
            }
        }
    }

    fun launchPrompt() {
        if (status == LockStatus.Authenticating) return

        // Evaluate fresh every time the user retries; hardware state can change
        // between attempts (e.g. sensor covered, device locked by admin).
        // BIOMETRIC_STRONG is required because WEAK can fall back to screen lock
        // credentials, which defeats the purpose of a biometric app lock.
        val availability = if (Build.VERSION.SDK_INT >= 29) {
            val biometricManager = BiometricManager.from(context)
            when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
                BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.Available
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricAvailability.NoHardware
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NotEnrolled
                else -> BiometricAvailability.Unavailable
            }
        } else BiometricAvailability.NoHardware

        when (availability) {
            BiometricAvailability.Available -> { /* proceed */ }
            BiometricAvailability.NoHardware -> {
                status = LockStatus.NoHardware
                return
            }
            BiometricAvailability.NotEnrolled -> {
                status = LockStatus.NotEnrolled
                return
            }
            BiometricAvailability.Unavailable -> {
                status = LockStatus.Error(context.getString(R.string.lock_unavailable))
                return
            }
        }

        val activity = context as? FragmentActivity
        if (activity == null) {
            status = LockStatus.Error(context.getString(R.string.lock_activity_required))
            return
        }
        // Avoid IllegalStateException when the activity is already dying or
        // its state has been saved (e.g. during config change or back press).
        if (activity.isFinishing) {
            return
        }
        if (activity.supportFragmentManager.isStateSaved) {
            status = LockStatus.Error(context.getString(R.string.lock_unavailable))
            return
        }

        status = LockStatus.Authenticating
        val executor = ContextCompat.getMainExecutor(context)
        var prompt: BiometricPrompt? = null
        prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    // Ignore callbacks from a prompt that was already cancelled or timed out.
                    if (promptHolder.value != prompt) return
                    promptHolder.value = null
                    status = LockStatus.Success
                    onUnlock()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // Ignore callbacks from a prompt that was already cancelled or timed out.
                    if (promptHolder.value != prompt) return
                    promptHolder.value = null
                    status = when (errorCode) {
                        // ERROR_CANCELED covers OEM variants that do not send USER_CANCELED.
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_CANCELED -> LockStatus.Cancelled
                        BiometricPrompt.ERROR_LOCKOUT,
                        BiometricPrompt.ERROR_LOCKOUT_PERMANENT ->
                            LockStatus.LockedOut
                        else -> LockStatus.Error(errString.toString())
                    }
                }
                override fun onAuthenticationFailed() {
                    // Framework handles attempt limiting and lockout.
                }
            }
        )
        promptHolder.value = prompt
        try {
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(context.getString(R.string.lock_title))
                    .setSubtitle(context.getString(R.string.lock_subtitle))
                    .setNegativeButtonText(context.getString(R.string.lock_cancel))
                    .build()
            )
        } catch (_: RuntimeException) {
            promptHolder.value = null
            status = LockStatus.Error(context.getString(R.string.lock_unavailable))
        }
    }

    LaunchedEffect(Unit) {
        launchPrompt()
    }

    val onPrimary = MaterialTheme.colorScheme.onPrimary
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary)
            .windowInsetsPadding(WindowInsets.systemBars),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Fingerprint,
                contentDescription = stringResource(R.string.lock_biometric_icon),
                tint = onPrimary,
                modifier = Modifier.size(64.dp)
            )

            Text(
                stringResource(R.string.app_name),
                color = onPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )

            Text(
                stringResource(R.string.lock_required),
                color = onPrimary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            when (status) {
                LockStatus.Checking,
                LockStatus.Authenticating -> {
                    CircularProgressIndicator(
                        color = onPrimary,
                        modifier = Modifier
                            .size(24.dp)
                            .semantics {
                                contentDescription = context.getString(R.string.lock_authenticating)
                            }
                    )
                }
                LockStatus.NoHardware -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                stringResource(R.string.lock_no_hardware),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                            Button(onClick = {
                                prefs.appLockEnabled = false
                                onUnlock()
                            }) {
                                Text(stringResource(R.string.lock_disable))
                            }
                        }
                    }
                }
                LockStatus.NotEnrolled -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                stringResource(R.string.lock_none_enrolled),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                            Button(onClick = { launchPrompt() }) {
                                Text(stringResource(R.string.lock_retry))
                            }
                        }
                    }
                }
                LockStatus.Cancelled -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                stringResource(R.string.lock_cancelled),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                            Button(onClick = { launchPrompt() }) {
                                Text(stringResource(R.string.lock_retry))
                            }
                        }
                    }
                }
                LockStatus.LockedOut -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                stringResource(R.string.lock_too_many_attempts),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                            Button(onClick = { launchPrompt() }) {
                                Text(stringResource(R.string.lock_retry))
                            }
                        }
                    }
                }
                is LockStatus.Error -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                (status as LockStatus.Error).message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                            Button(onClick = { launchPrompt() }) {
                                Text(stringResource(R.string.lock_retry))
                            }
                        }
                    }
                }
                LockStatus.Success -> { /* Unlocking; screen will unmount */ }
            }
        }
    }
}

private sealed class BiometricAvailability {
    object Available : BiometricAvailability()
    object NoHardware : BiometricAvailability()
    object NotEnrolled : BiometricAvailability()
    object Unavailable : BiometricAvailability()
}

private sealed class LockStatus {
    object Checking : LockStatus()
    object Authenticating : LockStatus()
    object NoHardware : LockStatus()
    object NotEnrolled : LockStatus()
    object Cancelled : LockStatus()
    object Success : LockStatus()
    object LockedOut : LockStatus()
    data class Error(val message: String) : LockStatus()
}
