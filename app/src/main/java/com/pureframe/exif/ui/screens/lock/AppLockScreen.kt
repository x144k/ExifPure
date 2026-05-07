package com.pureframe.exif.ui.screens.lock

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.pureframe.exif.ExifPureApplication

@Composable
fun AppLockScreen(
    onUnlock: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { (context.applicationContext as ExifPureApplication).container.prefs }

    var status by remember { mutableStateOf<LockStatus>(LockStatus.Checking) }

    val biometricAvailable = remember {
        if (Build.VERSION.SDK_INT >= 29) {
            val biometricManager = BiometricManager.from(context)
            biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
                BiometricManager.BIOMETRIC_SUCCESS
        } else false
    }

    fun launchPrompt() {
        if (!biometricAvailable) {
            status = LockStatus.NoHardware
            return
        }

        val activity = context as? FragmentActivity
        if (activity == null) {
            status = LockStatus.Error("Activity context required")
            return
        }

        status = LockStatus.Authenticating
        val executor = ContextCompat.getMainExecutor(context)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    status = LockStatus.Success
                    onUnlock()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    status = when (errorCode) {
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_USER_CANCELED -> LockStatus.Cancelled
                        else -> LockStatus.Error(errString.toString())
                    }
                }
                override fun onAuthenticationFailed() {
                    // Keep alive for retry on single bad read
                }
            }
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock EXIF Pure")
                .setSubtitle("Verify your identity")
                .setNegativeButtonText("Cancel")
                .build()
        )
    }

    LaunchedEffect(Unit) {
        launchPrompt()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Fingerprint,
                contentDescription = "Biometric",
                tint = Color.White,
                modifier = Modifier.size(64.dp)
            )

            Text(
                "EXIF Pure",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 2.sp
            )

            Text(
                "Authentication required",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp
            )

            when (status) {
                LockStatus.Checking,
                LockStatus.Authenticating -> {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                LockStatus.NoHardware -> {
                    Text(
                        "No biometric hardware enrolled",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp
                    )
                    Button(onClick = {
                        prefs.appLockEnabled = false
                        onUnlock()
                    }) {
                        Text("Disable Lock")
                    }
                }
                LockStatus.Cancelled -> {
                    Text(
                        "Authentication cancelled",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp
                    )
                    Button(onClick = { launchPrompt() }) {
                        Text("Retry")
                    }
                }
                is LockStatus.Error -> {
                    Text(
                        (status as LockStatus.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp
                    )
                    Button(onClick = { launchPrompt() }) {
                        Text("Retry")
                    }
                }
                LockStatus.Success -> { /* Unlocking; screen will unmount */ }
            }
        }
    }
}

private sealed class LockStatus {
    object Checking : LockStatus()
    object Authenticating : LockStatus()
    object NoHardware : LockStatus()
    object Cancelled : LockStatus()
    object Success : LockStatus()
    data class Error(val message: String) : LockStatus()
}
