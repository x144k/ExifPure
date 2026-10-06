package com.pureframe.exif.ui.screens.detail

import android.content.Intent
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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

    // ── Photo & metadata ───────────────────────────────────────────────
    /** The photo being inspected. Loaded asynchronously on init. */
    private val _photo = MutableStateFlow<Photo?>(null)
    val photo: StateFlow<Photo?> = _photo.asStateFlow()

    /** EXIF metadata of the original (unmodified) photo. */
    private val _exif = MutableStateFlow<ExifMetadata?>(null)
    val exif: StateFlow<ExifMetadata?> = _exif.asStateFlow()

    // ── Export state ───────────────────────────────────────────────────
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

    // ── Favorite state ───────────────────────────────────────────────────
    /** True if this photo is in the user's favorites. */
    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    // ── Preference delegates ────────────────────────────────────────────
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
    // ... (composable body unchanged)
    Text("DetailScreen composable — see source file for full implementation")
}
