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
import androidx.compose.ui.unit.dp
import com.pureframe.exif.ExifPureApplication
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                    Text("Appearance", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    ThemeRow("System default", EncryptedPreferenceStorage.VALUE_SYSTEM, theme) { theme = it; container.setThemeMode(it) }
                    ThemeRow("Light", EncryptedPreferenceStorage.VALUE_LIGHT, theme) { theme = it; container.setThemeMode(it) }
                    ThemeRow("Dark", EncryptedPreferenceStorage.VALUE_DARK, theme) { theme = it; container.setThemeMode(it) }
                }
            }

            Card(modifier = Modifier.padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Grid Size", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    ThemeRow("Small", EncryptedPreferenceStorage.GRID_SMALL, gridSize) { gridSize = it; prefs.gridSize = it }
                    ThemeRow("Medium", EncryptedPreferenceStorage.GRID_MEDIUM, gridSize) { gridSize = it; prefs.gridSize = it }
                    ThemeRow("Large", EncryptedPreferenceStorage.GRID_LARGE, gridSize) { gridSize = it; prefs.gridSize = it }
                }
            }

            Card(modifier = Modifier.padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Default Strip Mode", style = MaterialTheme.typography.titleMedium)
                    Text("Applies to all exports and shares unless you change it for a specific photo", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    ThemeRow("Remove All Metadata", EncryptedPreferenceStorage.STRIP_ALL, defaultStrip) { defaultStrip = it; prefs.defaultStripMode = it }
                    ThemeRow("Remove GPS Only", EncryptedPreferenceStorage.STRIP_GPS, defaultStrip) { defaultStrip = it; prefs.defaultStripMode = it }
                }
            }

            Card(modifier = Modifier.padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Sort Order", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    ThemeRow("Date Added (newest first)", EncryptedPreferenceStorage.SORT_DATE_ADDED_DESC, sortOrder) { sortOrder = it; prefs.sortOrder = it }
                    ThemeRow("Date Added (oldest first)", EncryptedPreferenceStorage.SORT_DATE_ADDED_ASC, sortOrder) { sortOrder = it; prefs.sortOrder = it }
                    ThemeRow("Name (A-Z)", EncryptedPreferenceStorage.SORT_NAME_ASC, sortOrder) { sortOrder = it; prefs.sortOrder = it }
                    ThemeRow("Name (Z-A)", EncryptedPreferenceStorage.SORT_NAME_DESC, sortOrder) { sortOrder = it; prefs.sortOrder = it }
                    ThemeRow("Size (largest first)", EncryptedPreferenceStorage.SORT_SIZE_DESC, sortOrder) { sortOrder = it; prefs.sortOrder = it }
                }
            }

            Card(modifier = Modifier.padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Export Directory", style = MaterialTheme.typography.titleMedium)
                    Text("Clean copies are saved inside Pictures/ on your device", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = outputDir,
                        onValueChange = { outputDir = it },
                        label = { Text("Subdirectory name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { prefs.outputDirName = outputDir },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Save")
                    }
                }
            }

            Card(modifier = Modifier.padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Fallback Export Quality", style = MaterialTheme.typography.titleMedium)
                    Text("For WEBP/HEIF formats that require re-encoding", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Slider(
                        value = quality,
                        onValueChange = { quality = it },
                        valueRange = 50f..100f,
                        steps = 49,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("${quality.toInt()}%", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { prefs.fallbackQuality = quality.toInt() },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Save")
                    }
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
                        Text("Haptic Feedback", style = MaterialTheme.typography.titleMedium)
                        Text("Vibrate on actions", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = haptic,
                        onCheckedChange = { haptic = it; prefs.hapticEnabled = it }
                    )
                }
            }

            Card(modifier = Modifier.padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Security", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    LockSettings()
                }
            }

            Card(modifier = Modifier.padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("History", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onHistoryClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("View Export History")
                    }
                }
            }

            Card(modifier = Modifier.padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Data", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { prefs.clearFavorites() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Clear All Favorites")
                    }
                }
            }

            Card(modifier = Modifier.padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("About", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("EXIF Pure v1.3.0", style = MaterialTheme.typography.bodyMedium)
                    Text("Privacy-first local photo organizer", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    Text("No accounts. No ads. No tracking. All data stays on your device.", style = MaterialTheme.typography.bodySmall)
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
