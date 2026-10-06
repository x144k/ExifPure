package com.pureframe.exif.ui.screens.settings

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
                        onClick = { prefs.outputDirName = outputDir },
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Biometric Lock", style = MaterialTheme.typography.bodyMedium)
            Text("Require fingerprint/face to open", style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = lockEnabled,
            onCheckedChange = { checked ->
                prefs.appLockEnabled = checked
                lockEnabled = checked
            }
        )
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
