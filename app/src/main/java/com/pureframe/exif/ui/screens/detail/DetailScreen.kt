package com.pureframe.exif.ui.screens.detail

import android.content.Intent
import android.text.format.Formatter
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pureframe.exif.ExifPureApplication
import com.pureframe.exif.data.local.EncryptedPreferenceStorage
import com.pureframe.exif.data.model.ExifMetadata
import com.pureframe.exif.data.model.Photo
import com.pureframe.exif.data.model.StripMode
import com.pureframe.exif.data.repository.PhotoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ViewModel for the photo detail screen.
 *
 * Orchestrates three primary flows:
 * 1. **Metadata inspection** — loads the photo and its EXIF data on entry.
 * 2. **Export & share** — creates a clean copy via [MetadataStripper], logs the export,
 *    and triggers the system share sheet with the new file URI.
 * 3. **Comparison** — after export, optionally loads the clean copy's EXIF and renders
 *    a side-by-side diff showing which fields were removed vs. preserved.
 *
 * ## Result consumption pattern
 * [exportResult] and [exportedUri] are **single-shot events** modeled as StateFlow
 * (rather than SharedFlow or Channel) to survive configuration changes. The UI
 * consumes them via [consumeResult] / [consumeExportedUri] after handling,
 * preventing duplicate snackbars or duplicate share sheets on rotation.
 *
 * ## Strip mode
 * The user selects a mode via radio buttons in the UI. The default is pulled from
 * [EncryptedPreferenceStorage.defaultStripMode] on ViewModel creation.
 */
class DetailViewModel(
    private val repository: PhotoRepository,
    private val photoId: Long
) : ViewModel() {

    // Photo & metadata
    /** The photo being inspected. Loaded asynchronously on init. */
    private val _photo = MutableStateFlow<Photo?>(null)
    val photo: StateFlow<Photo?> = _photo.asStateFlow()

    /** EXIF metadata of the original (unmodified) photo. */
    private val _exif = MutableStateFlow<ExifMetadata?>(null)
    val exif: StateFlow<ExifMetadata?> = _exif.asStateFlow()

    // Export state
    /** True while [exportClean] is awaiting the stripper and MediaStore insertion. */
    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    /**
     * Human-readable result of the last export attempt.
     * Non-null indicates a completed export; consumed by the UI via [consumeResult].
     */
    private val _exportResult = MutableStateFlow<String?>(null)
    val exportResult: StateFlow<String?> = _exportResult.asStateFlow()

    /**
     * URI of the successfully exported clean copy.
     * Non-null triggers the system share sheet; consumed via [consumeExportedUri].
     */
    private val _exportedUri = MutableStateFlow<android.net.Uri?>(null)
    val exportedUri: StateFlow<android.net.Uri?> = _exportedUri.asStateFlow()

    /**
     * EXIF metadata of the exported clean copy, loaded for the comparison feature.
     * Null until the user exports and the comparison card is expanded.
     */
    private val _exportedExif = MutableStateFlow<ExifMetadata?>(null)
    val exportedExif: StateFlow<ExifMetadata?> = _exportedExif.asStateFlow()

    // Favorite state
    /** True if this photo is in the user's favorites. */
    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    // Preference delegates
    /** Default strip mode from preferences (ALL or GPS_ONLY). */
    val defaultStripMode: StripMode
        get() = if (repository.prefs.defaultStripMode == EncryptedPreferenceStorage.STRIP_GPS)
            StripMode.GPS_ONLY else StripMode.ALL

    /** Whether haptic feedback is enabled in settings. */
    val hapticEnabled: Boolean
        get() = repository.prefs.hapticEnabled

    init {
        load()
        _isFavorite.value = repository.isFavorite(photoId)
    }

    /**
     * Loads the photo and its EXIF metadata.
     *
     * Called once in [init]. If the photo was deleted between gallery navigation
     * and detail opening, [_photo] will remain null and the UI shows empty state.
     */
    private fun load() {
        viewModelScope.launch {
            val p = repository.getPhoto(photoId)
            _photo.value = p
            p?.let { _exif.value = repository.getExif(it) }
        }
    }

    /** Toggles the favorite status of this photo. */
    fun toggleFavorite() {
        repository.toggleFavorite(photoId)
        _isFavorite.value = repository.isFavorite(photoId)
    }

    /**
     * Exports a clean copy of the current photo and queues it for sharing.
     *
     * The flow is:
     * 1. Set [_isExporting] true (blocks FAB, shows progress).
     * 2. Call [MetadataStripper.createCleanCopy] with the chosen [mode].
     * 3. On success: log the export, set [_exportedUri] (triggers share sheet),
     *    and load the clean copy's EXIF into [_exportedExif] for comparison.
     * 4. On failure: set [_exportResult] to the error message.
     * 5. Set [_isExporting] false.
     *
     * @param mode [StripMode.ALL] removes all metadata; [StripMode.GPS_ONLY] removes only GPS.
     */
    fun exportClean(mode: StripMode) {
        viewModelScope.launch {
            _isExporting.value = true
            val p = _photo.value ?: return@launch
            val result = repository.exportClean(p, mode)
            _exportResult.value = result.fold(
                onSuccess = { "Clean copy saved" },
                onFailure = { it.message ?: "Export failed" }
            )
            _exportedUri.value = result.getOrNull()
            result.getOrNull()?.let { uri ->
                try {
                    // Load EXIF of the exported file for comparison view
                    _exportedExif.value = repository.getExif(
                        Photo(0, uri, "", 0, 0, 0, 0, 0, "", null, null)
                    )
                } catch (_: Exception) {
                    _exportedExif.value = null
                }
            }
            _isExporting.value = false
        }
    }

    /** Consumes the export result message after the snackbar has been shown. */
    fun consumeResult() { _exportResult.value = null }

    /** Consumes the exported URI after the share sheet has been launched. */
    fun consumeExportedUri() { _exportedUri.value = null }

    /** Consumes the exported EXIF after the comparison card is dismissed. */
    fun consumeExportedExif() { _exportedExif.value = null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    photoId: Long,
    onBack: () -> Unit,
    onImageClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val view = LocalView.current
    val repository = remember {
        (context.applicationContext as ExifPureApplication).container.repository
    }
    val viewModel: DetailViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DetailViewModel(repository, photoId) as T
            }
        }
    )

    val photo by viewModel.photo.collectAsState()
    val exif by viewModel.exif.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()
    val exportResult by viewModel.exportResult.collectAsState()
    val exportedUri by viewModel.exportedUri.collectAsState()
    val exportedExif by viewModel.exportedExif.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()

    var selectedStripMode by remember { mutableStateOf(viewModel.defaultStripMode) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()

    // Show snackbar when an export result is available
    LaunchedEffect(exportResult) {
        exportResult?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeResult()
        }
    }

    // Launch system share sheet when a clean copy URI is available
    LaunchedEffect(exportedUri) {
        exportedUri?.let { uri ->
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = photo?.mimeType ?: "image/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share clean copy"))
            viewModel.consumeExportedUri()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(photo?.displayName ?: "Photo Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (viewModel.hapticEnabled) {
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            }
                            viewModel.toggleFavorite()
                        }
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = if (isFavorite) "Unfavorite" else "Favorite",
                            tint = if (isFavorite) androidx.compose.ui.graphics.Color.Red
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (viewModel.hapticEnabled) {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    }
                    viewModel.exportClean(selectedStripMode)
                },
                icon = {
                    if (isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Filled.Share, contentDescription = null)
                    }
                },
                text = { Text(if (isExporting) "Exporting..." else "Export Clean") },
                expanded = true
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (photo == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .verticalScroll(scrollState)
                    .padding(padding)
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // Photo preview
                Card(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(photo!!.uri)
                            .crossfade(true)
                            .build(),
                        contentDescription = photo!!.displayName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = { onImageClick() })
                            },
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // File info
                PhotoInfoCard(photo = photo!!)

                Spacer(modifier = Modifier.height(16.dp))

                // EXIF metadata
                ExifCard(exif = exif)

                Spacer(modifier = Modifier.height(16.dp))

                // Strip mode selection
                StripModeCard(
                    selected = selectedStripMode,
                    onSelect = { selectedStripMode = it }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Export comparison
                if (exportedExif != null && exif != null) {
                    ComparisonCard(
                        original = exif!!,
                        clean = exportedExif!!,
                        onDismiss = { viewModel.consumeExportedExif() }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Extra bottom padding so the FAB never obscures content
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}



@Composable
private fun PhotoInfoCard(photo: Photo) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy \u2022 HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "File Info",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            InfoRow("Name", photo.displayName)
            InfoRow("Dimensions", "${photo.width} \u00D7 ${photo.height}")
            InfoRow("Size", Formatter.formatShortFileSize(context, photo.size))
            InfoRow("Type", photo.mimeType)
            InfoRow("Date", dateFormat.format(Date(photo.dateAdded * 1000L)))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ExifCard(exif: ExifMetadata?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "EXIF Metadata",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (exif == null || !exif.hasExif) {
                Text(
                    "No EXIF data found",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                ExifRow("Camera", listOfNotNull(exif.make, exif.model).joinToString(" "))
                ExifRow("Lens", exif.lens)
                ExifRow("Date Taken", exif.dateTimeOriginal)
                ExifRow("Exposure", exif.exposureTime)
                ExifRow("Aperture", exif.fNumber)
                ExifRow("ISO", exif.iso)
                ExifRow("Focal Length", exif.focalLength)
                ExifRow(
                    "GPS",
                    buildString {
                        if (exif.gpsLatitude != null && exif.gpsLongitude != null) {
                            append("${exif.gpsLatitude}, ${exif.gpsLongitude}")
                            if (exif.gpsAltitude != null) append(" \u2022 ${exif.gpsAltitude}")
                        }
                    }.takeIf { it.isNotEmpty() }
                )
                ExifRow(
                    "Orientation",
                    exif.orientation?.let {
                        when (it) {
                            1 -> "Normal"
                            3 -> "Rotated 180\u00B0"
                            6 -> "Rotated 90\u00B0 CW"
                            8 -> "Rotated 90\u00B0 CCW"
                            else -> "Value $it"
                        }
                    }
                )
                ExifRow(
                    "Image Size",
                    listOfNotNull(exif.imageWidth, exif.imageLength)
                        .takeIf { it.size == 2 }
                        ?.let { "${it[0]} \u00D7 ${it[1]}" }
                )
            }
        }
    }
}

@Composable
private fun ExifRow(label: String, value: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value ?: "Not available",
            style = MaterialTheme.typography.bodyMedium,
            color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.6f)
        )
    }
}

@Composable
private fun StripModeCard(
    selected: StripMode,
    onSelect: (StripMode) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Strip Mode",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Choose what metadata to remove from the exported copy",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            StripModeRow(
                label = "Remove All Metadata",
                description = "Strip every EXIF tag including camera info, dates, and GPS",
                selected = selected == StripMode.ALL,
                onClick = { onSelect(StripMode.ALL) }
            )
            StripModeRow(
                label = "Remove GPS Only",
                description = "Preserve camera info and dates; only strip location data",
                selected = selected == StripMode.GPS_ONLY,
                onClick = { onSelect(StripMode.GPS_ONLY) }
            )
        }
    }
}

@Composable
private fun StripModeRow(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ComparisonCard(
    original: ExifMetadata,
    clean: ExifMetadata,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Export Comparison",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                TextButton(onClick = onDismiss) {
                    Text("Dismiss", color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            val diffs = listOf(
                Triple("GPS Location", original.gpsLatitude != null, clean.gpsLatitude != null),
                Triple(
                    "Camera Info",
                    original.make != null || original.model != null,
                    clean.make != null || clean.model != null
                ),
                Triple("Lens", original.lens != null, clean.lens != null),
                Triple("Date Taken", original.dateTimeOriginal != null, clean.dateTimeOriginal != null),
                Triple("Exposure", original.exposureTime != null, clean.exposureTime != null),
                Triple("Aperture", original.fNumber != null, clean.fNumber != null),
                Triple("ISO", original.iso != null, clean.iso != null),
                Triple("Focal Length", original.focalLength != null, clean.focalLength != null)
            )

            diffs.forEach { (label, orig, cleaned) ->
                ComparisonRow(label, orig, cleaned)
            }
        }
    }
}

@Composable
private fun ComparisonRow(label: String, original: Boolean, clean: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = when {
                original && !clean -> "Removed"
                original && clean -> "Preserved"
                else -> "Not present"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = when {
                original && !clean -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onPrimaryContainer
            }
        )
    }
}
