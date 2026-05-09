package com.pureframe.exif.ui.screens.settings

import android.os.Build
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.pureframe.exif.BuildConfig
import com.pureframe.exif.ExifPureApplication
import com.pureframe.exif.R
import com.pureframe.exif.data.local.EncryptedPreferenceStorage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onHistoryClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as ExifPureApplication).container }
    val prefs = container.prefs

    var theme by remember { mutableStateOf(prefs.themeMode) }
    var outputDir by remember { mutableStateOf(prefs.outputDirName) }
    var defaultStrip by remember { mutableStateOf(prefs.defaultStripMode) }
    var sortOrder by remember { mutableStateOf(prefs.sortOrder) }
    var gridSize by remember { mutableStateOf(prefs.gridSize) }
    var quality by remember { mutableFloatStateOf(prefs.fallbackQuality.toFloat()) }
    var haptic by remember { mutableStateOf(prefs.hapticEnabled) }
    var silentShare by remember { mutableStateOf(prefs.silentShareEnabled) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Card(modifier = Modifier.padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.settings_appearance_layout),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(12.dp))

                    Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    ThemeRow(stringResource(R.string.theme_system), EncryptedPreferenceStorage.VALUE_SYSTEM, theme) { theme = it; container.setThemeMode(it) }
                    ThemeRow(stringResource(R.string.theme_light), EncryptedPreferenceStorage.VALUE_LIGHT, theme) { theme = it; container.setThemeMode(it) }
                    ThemeRow(stringResource(R.string.theme_dark), EncryptedPreferenceStorage.VALUE_DARK, theme) { theme = it; container.setThemeMode(it) }

                    Spacer(Modifier.height(16.dp))

                    Text(stringResource(R.string.settings_grid_size), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    ThemeRow(stringResource(R.string.grid_small), EncryptedPreferenceStorage.GRID_SMALL, gridSize) { gridSize = it; prefs.gridSize = it }
                    ThemeRow(stringResource(R.string.grid_medium), EncryptedPreferenceStorage.GRID_MEDIUM, gridSize) { gridSize = it; prefs.gridSize = it }
                    ThemeRow(stringResource(R.string.grid_large), EncryptedPreferenceStorage.GRID_LARGE, gridSize) { gridSize = it; prefs.gridSize = it }

                    Spacer(Modifier.height(16.dp))

                    Text(stringResource(R.string.settings_sort_order), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    ThemeRow(stringResource(R.string.sort_date_added_desc), EncryptedPreferenceStorage.SORT_DATE_ADDED_DESC, sortOrder) { sortOrder = it; prefs.sortOrder = it }
                    ThemeRow(stringResource(R.string.sort_date_added_asc), EncryptedPreferenceStorage.SORT_DATE_ADDED_ASC, sortOrder) { sortOrder = it; prefs.sortOrder = it }
                    ThemeRow(stringResource(R.string.sort_name_asc), EncryptedPreferenceStorage.SORT_NAME_ASC, sortOrder) { sortOrder = it; prefs.sortOrder = it }
                    ThemeRow(stringResource(R.string.sort_name_desc), EncryptedPreferenceStorage.SORT_NAME_DESC, sortOrder) { sortOrder = it; prefs.sortOrder = it }
                    ThemeRow(stringResource(R.string.sort_size_desc), EncryptedPreferenceStorage.SORT_SIZE_DESC, sortOrder) { sortOrder = it; prefs.sortOrder = it }
                }
            }

            Card(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_haptic), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.settings_haptic_desc), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = haptic,
                        onCheckedChange = { haptic = it; prefs.hapticEnabled = it }
                    )
                }
            }

            Card(modifier = Modifier.padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.settings_export_behavior),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(12.dp))

                    Text(stringResource(R.string.settings_default_strip), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.settings_default_strip_desc), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    ThemeRow(stringResource(R.string.strip_all), EncryptedPreferenceStorage.STRIP_ALL, defaultStrip) { defaultStrip = it; prefs.defaultStripMode = it }
                    ThemeRow(stringResource(R.string.strip_gps), EncryptedPreferenceStorage.STRIP_GPS, defaultStrip) { defaultStrip = it; prefs.defaultStripMode = it }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_silent_share), style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(R.string.settings_silent_share_desc), style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = silentShare,
                            onCheckedChange = { silentShare = it; prefs.silentShareEnabled = it }
                        )
                    }
                }
            }

            Card(modifier = Modifier.padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.settings_export_output),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(12.dp))

                    Text(stringResource(R.string.settings_save_location), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.settings_save_location_desc), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = outputDir,
                        onValueChange = { outputDir = it },
                        label = { Text(stringResource(R.string.settings_subdir_name)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            prefs.outputDirName = outputDir
                            Toast.makeText(
                                context,
                                context.getString(R.string.settings_save_location_confirmed),
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(stringResource(R.string.save))
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(stringResource(R.string.settings_fallback_quality), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.settings_fallback_quality_desc), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    Slider(
                        value = quality,
                        onValueChange = { quality = it },
                        valueRange = 50f..100f,
                        steps = 49,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(stringResource(R.string.settings_quality_percent, quality.toInt()), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { prefs.fallbackQuality = quality.toInt() },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
            }

            Card(modifier = Modifier.padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.settings_history), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onHistoryClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.settings_view_history))
                    }
                }
            }

            Card(modifier = Modifier.padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.settings_security), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    LockSettings()
                }
            }

            Card(modifier = Modifier.padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.settings_data), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { prefs.clearFavorites() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.settings_clear_favorites))
                    }
                }
            }

            Card(modifier = Modifier.padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.about_tagline), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.about_privacy), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun LockSettings() {
    val context = LocalContext.current
    val prefs = remember { (context.applicationContext as ExifPureApplication).container.prefs }

    var lockEnabled by remember { mutableStateOf(prefs.appLockEnabled) }
    var showAuthError by remember { mutableStateOf<String?>(null) }

    val canAuthStrong = remember {
        if (Build.VERSION.SDK_INT >= 29) {
            val biometricManager = BiometricManager.from(context)
            biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS
        } else false
    }

    fun requestAuthBeforeToggle(targetState: Boolean, onResult: (Boolean) -> Unit) {
        val activity = context as? FragmentActivity ?: run {
            onResult(false)
            return
        }
        val executor = ContextCompat.getMainExecutor(context)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onResult(true)
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onResult(false)
                }
                override fun onAuthenticationFailed() {
                    onResult(false)
                }
            }
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(context.getString(R.string.lock_title))
                .setSubtitle(context.getString(R.string.lock_verify_to_change))
                .setNegativeButtonText(context.getString(R.string.lock_cancel))
                .build()
        )
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_biometric_lock), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.settings_biometric_lock_desc), style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = lockEnabled,
                enabled = canAuthStrong || lockEnabled,
                onCheckedChange = { checked ->
                    if (!canAuthStrong && !checked) {
                        prefs.appLockEnabled = false
                        lockEnabled = false
                        showAuthError = null
                    } else {
                        requestAuthBeforeToggle(checked) { success ->
                            if (success) {
                                prefs.appLockEnabled = checked
                                lockEnabled = checked
                                showAuthError = null
                            } else {
                                showAuthError = context.getString(R.string.lock_auth_failed)
                            }
                        }
                    }
                }
            )
        }
        if (!canAuthStrong && !lockEnabled) {
            Text(
                stringResource(R.string.lock_no_strong_biometric),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        showAuthError?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun ThemeRow(label: String, value: String, current: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = current == value,
                onClick = { onSelect(value) }
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = current == value,
            onClick = { onSelect(value) }
        )
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
