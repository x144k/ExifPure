package com.pureframe.exif.ui.screens.gallery

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.pureframe.exif.ExifPureApplication
import com.pureframe.exif.data.local.EncryptedPreferenceStorage
import com.pureframe.exif.data.model.Album
import com.pureframe.exif.data.model.ExifSummary
import com.pureframe.exif.data.model.Photo
import com.pureframe.exif.data.model.StripMode
import com.pureframe.exif.data.repository.PhotoRepository
import com.pureframe.exif.ui.components.ShimmerBox
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the gallery screen.
 *
 * Manages four distinct UI surfaces:
 * 1. **Photo grid** — loading, sorting, filtering (search, favorites, EXIF presence, GPS presence).
 * 2. **Selection mode** — long-press to enter; tap to toggle selection; batch actions (delete, share clean).
 * 3. **EXIF cache** — on-demand background scanning of EXIF/GPS presence for filter chips.
 * 4. **Batch export** — asynchronous export of multiple photos with progress and result reporting.
 * 5. **Album view** — group photos by bucket/album; browse albums and drill into individual albums.
 *
 * ## State flow architecture
 * All mutable state is held in `MutableStateFlow` properties exposed as read-only `StateFlow`.
 * Compose collects these via `collectAsState()`. No external MVI library is used.
 *
 * ## Selection mode lifecycle
 * ```
 * Normal tap ──► navigate to detail
 * Long-press ──► enter selection mode, select pressed item
 * Tap in mode ──► toggle selection
 * Action bar back ──► clear selection, exit mode
 * ```
 *
 * ## Album view lifecycle
 * ```
 * Toggle view ──► switch between flat photo grid and album grid
 * Tap album ──► enter album detail (photo grid filtered by bucket)
 * Back in album ──► return to album grid
 * ```
 *
 * ## EXIF caching strategy
 * The [exifCache] map is populated lazily when the user enables the "With EXIF" or
 * "Has GPS" filter chips. It is **not** pre-populated on gallery load to avoid blocking
 * the UI for large libraries. Each photo is scanned once and the result is memoized.
 */
class GalleryViewModel(private val repository: PhotoRepository) : ViewModel() {

    // ── Core photo list ──────────────────────────────────────────────────
    /** All photos from MediaStore, unsorted and unfiltered. */
    private val _photos = MutableStateFlow<List<Photo>>(emptyList())
    val photos: StateFlow<List<Photo>> = _photos.asStateFlow()

    /** True while the initial MediaStore query is in flight. */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** Error message from the last failed refresh, or null. */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // ── Album list ───────────────────────────────────────────────────────
    /** All albums (buckets) from MediaStore. */
    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    /** The album currently being viewed in detail. Null when at the top-level grid. */
    private val _currentAlbum = MutableStateFlow<Album?>(null)
    val currentAlbum: StateFlow<Album?> = _currentAlbum.asStateFlow()

    // ── Selection mode ─────────────────────────────────────────────────
    /** True when the user is in multi-select mode (triggered by long-press). */
    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    /** IDs of photos currently selected for batch action. */
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    // ── Favorites ────────────────────────────────────────────────────────
    /** Set of photo IDs marked as favorite. Kept in sync with [EncryptedPreferenceStorage]. */
    private val _favoriteIds = MutableStateFlow<Set<Long>>(emptySet())
    val favoriteIds: StateFlow<Set<Long>> = _favoriteIds.asStateFlow()

    // ── Batch export ─────────────────────────────────────────────────────
    /** True while a batch export operation is in progress. Blocks the action bar. */
    private val _batchProgress = MutableStateFlow(false)
    val batchProgress: StateFlow<Boolean> = _batchProgress.asStateFlow()

    /** Human-readable result of the last batch operation (e.g. "Exported 5 clean copies"). */
    private val _batchResult = MutableStateFlow<String?>(null)
    val batchResult: StateFlow<String?> = _batchResult.asStateFlow()

    /** URIs of successfully exported photos, queued for the system share sheet. */
    private val _shareUris = MutableStateFlow<List<android.net.Uri>>(emptyList())
    val shareUris: StateFlow<List<android.net.Uri>> = _shareUris.asStateFlow()

    // ── EXIF cache ───────────────────────────────────────────────────────
    /**
     * Map of photo ID → [ExifSummary], populated on first use of EXIF/GPS filters.
     * Null entries indicate the photo has not yet been scanned.
     */
    private val _exifCache = MutableStateFlow<Map<Long, ExifSummary>>(emptyMap())
    val exifCache: StateFlow<Map<Long, ExifSummary>> = _exifCache.asStateFlow()

    /** True while the background EXIF scan is running. Shown as a progress indicator. */
    private val _isScanningExif = MutableStateFlow(false)
    val isScanningExif: StateFlow<Boolean> = _isScanningExif.asStateFlow()

    // ── Filter state (plain mutableState for Compose recomposition) ──────
    /** When true, only favorite photos are shown. */
    var showFavoritesOnly by mutableStateOf(false)

    /** When true, only photos with EXIF metadata are shown. Triggers lazy scan. */
    var filterWithExif by mutableStateOf(false)

    /** When true, only photos containing GPS coordinates are shown. Triggers lazy scan. */
    var filterHasGps by mutableStateOf(false)

    /** Free-text search query matched against display name and album name. */
    var searchQuery by mutableStateOf("")

    /** Controls visibility of the delete confirmation dialog. */
    var showDeleteConfirm by mutableStateOf(false)

    // ── Gallery view mode ──────────────────────────────────────────────
    private val _galleryViewMode = MutableStateFlow(repository.prefs.galleryViewMode)
    val galleryViewMode: StateFlow<String> = _galleryViewMode.asStateFlow()

    // ── Preference delegates ────────────────────────────────────────────
    val sortOrder: String get() = repository.prefs.sortOrder
    val gridSize: String get() = repository.prefs.gridSize
    val hapticEnabled: Boolean get() = repository.prefs.hapticEnabled

    init {
        refresh()
        _favoriteIds.value = repository.getFavoriteIds()
    }

    /**
     * Reloads the full photo list and album list from MediaStore.
     *
     * This is called on init, on user pull-to-refresh, and after batch delete.
     * The existing [exifCache] is **not** invalidated; new photos will be scanned
     * on-demand when filters are next enabled.
     */
    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                repository.getPhotos().collect { list ->
                    _photos.value = list
                    _albums.value = buildAlbums(list)
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Builds album list from an in-memory photo list.
     *
     * This avoids a second MediaStore query since the photos already contain
     * bucket metadata.
     */
    private fun buildAlbums(photos: List<Photo>): List<Album> {
        return photos
            .groupBy { it.bucketId ?: "unknown" }
            .map { (bucketId, photoList) ->
                Album(
                    bucketId = bucketId,
                    bucketDisplayName = photoList.first().bucketDisplayName,
                    coverPhotoUri = photoList.maxByOrNull { it.dateAdded }?.uri
                        ?: photoList.first().uri,
                    photoCount = photoList.size
                )
            }
            .sortedWith(compareByDescending<Album> { it.photoCount }.thenBy { it.bucketDisplayName })
    }

    /** Changes the sort order and triggers a re-sort via [refresh]. */
    fun setSortOrder(order: String) {
        repository.prefs.sortOrder = order
        refresh()
    }

    /** Toggles the gallery home view between flat photos and album grid. */
    fun toggleGalleryViewMode() {
        val newMode = if (galleryViewMode.value == EncryptedPreferenceStorage.VIEW_PHOTOS) {
            EncryptedPreferenceStorage.VIEW_ALBUMS
        } else {
            EncryptedPreferenceStorage.VIEW_PHOTOS
        }
        repository.prefs.galleryViewMode = newMode
        _galleryViewMode.value = newMode
        _currentAlbum.value = null
    }

    /** Opens the given album in detail view. */
    fun openAlbum(album: Album) {
        _currentAlbum.value = album
        // Clear filters when entering an album for clean state
        searchQuery = ""
        showFavoritesOnly = false
        filterWithExif = false
        filterHasGps = false
    }

    /** Closes the current album detail and returns to the top-level grid. */
    fun closeAlbum() {
        _currentAlbum.value = null
    }

    /** Toggles the favorite status of a single photo and syncs local state. */
    fun toggleFavorite(id: Long) {
        repository.toggleFavorite(id)
        _favoriteIds.value = repository.getFavoriteIds()
    }

    /** Toggles the favorites-only filter. */
    fun toggleShowFavoritesOnly() {
        showFavoritesOnly = !showFavoritesOnly
    }

    /** Toggles the "With EXIF" filter. Triggers lazy scan if cache is empty. */
    fun toggleFilterWithExif() {
        filterWithExif = !filterWithExif
        if (filterWithExif) ensureExifCache()
    }

    /** Toggles the "Has GPS" filter. Triggers lazy scan if cache is empty. */
    fun toggleFilterHasGps() {
        filterHasGps = !filterHasGps
        if (filterHasGps) ensureExifCache()
    }

    /**
     * Ensures the EXIF cache is populated.
     *
     * This is a **lazy, one-time** background operation. It iterates over the current
     * photo list, reads EXIF for each, and stores a lightweight [ExifSummary].
     * Large libraries (10k+ photos) may take several seconds; the UI shows a progress
     * indicator via [_isScanningExif].
     */
    private fun ensureExifCache() {
        if (_exifCache.value.isNotEmpty()) return
        viewModelScope.launch {
            _isScanningExif.value = true
            val cache = mutableMapOf<Long, ExifSummary>()
            _photos.value.forEach { photo ->
                try {
                    val meta = repository.getExif(photo)
                    cache[photo.id] = ExifSummary(
                        photoId = photo.id,
                        hasExif = meta.hasExif,
                        hasGps = meta.gpsLatitude != null,
                        cameraModel = meta.model
                    )
                } catch (_: Exception) {
                    cache[photo.id] = ExifSummary(photo.id, false, false, null)
                }
            }
            _exifCache.value = cache
            _isScanningExif.value = false
        }
    }

    /** Toggles selection of a single photo ID. Exits selection mode if the set becomes empty. */
    fun toggleSelection(id: Long) {
        _selectedIds.value = _selectedIds.value.toMutableSet().apply {
            if (contains(id)) remove(id) else add(id)
        }
        if (_selectedIds.value.isEmpty()) _isSelectionMode.value = false
    }

    /** Enters selection mode and selects the initial photo (triggered by long-press). */
    fun enterSelectionMode(id: Long) {
        _isSelectionMode.value = true
        _selectedIds.value = setOf(id)
    }

    /** Clears all selections and exits selection mode. */
    fun clearSelection() {
        _selectedIds.value = emptySet()
        _isSelectionMode.value = false
    }

    /**
     * Permanently deletes all selected photos via MediaStore.
     *
     * Shows a confirmation dialog before invocation ([showDeleteConfirm]).
     * On Android 10+ (API 29+), this may trigger a system confirmation dialog
     * for scoped-storage deletion.
     */
    fun deleteSelected() {
        viewModelScope.launch {
            val photos = _photos.value.filter { it.id in _selectedIds.value }
            val count = repository.deletePhotos(photos)
            _batchResult.value = "Deleted $count photos"
            clearSelection()
            refresh()
        }
    }

    /**
     * Exports all selected photos as clean copies and queues them for sharing.
     *
     * Uses the user's default strip mode ([EncryptedPreferenceStorage.defaultStripMode]).
     * Progress is shown via [_batchProgress]; results are posted to [_batchResult]
     * and the share sheet is triggered via [_shareUris].
     */
    fun batchExportAndShare(mode: StripMode) {
        viewModelScope.launch {
            _batchProgress.value = true
            val photos = _photos.value.filter { it.id in _selectedIds.value }
            val results = repository.batchExport(photos, mode)
            val success = results.count { it.isSuccess }
            val failed = results.size - success
            val uris = results.mapNotNull { it.getOrNull() }
            _batchResult.value = "Exported $success clean copies${if (failed > 0) ", $failed failed" else ""}"
            _batchProgress.value = false
            clearSelection()
            _shareUris.value = uris
        }
    }

    /** Clears the pending share URIs after the system share sheet has been launched. */
    fun consumeShareUris() { _shareUris.value = emptyList() }

    /** Clears the last batch result after the snackbar has been shown. */
    fun consumeBatchResult() { _batchResult.value = null }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(
    onPhotoClick: (Photo) -> Unit,
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: GalleryViewModel = viewModel(
        factory = remember {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val app = context.applicationContext as ExifPureApplication
                    return GalleryViewModel(app.container.repository) as T
                }
            }
        }
    )
    val photos by viewModel.photos.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val batchProgress by viewModel.batchProgress.collectAsState()
    val batchResult by viewModel.batchResult.collectAsState()
    val shareUris by viewModel.shareUris.collectAsState()
    val exifCache by viewModel.exifCache.collectAsState()
    val isScanningExif by viewModel.isScanningExif.collectAsState()
    val currentAlbum by viewModel.currentAlbum.collectAsState()
    val galleryViewMode by viewModel.galleryViewMode.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler(enabled = isSelectionMode || currentAlbum != null) {
        when {
            isSelectionMode -> viewModel.clearSelection()
            currentAlbum != null -> viewModel.closeAlbum()
        }
    }
    var isSearchActive by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val view = LocalView.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) viewModel.refresh()
    }

    val requiredPerms = remember {
        when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
            Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    val hasPermission = requiredPerms.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission && photos.isEmpty() && !isLoading) viewModel.refresh()
    }

    LaunchedEffect(batchResult) {
        batchResult?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeBatchResult()
        }
    }

    LaunchedEffect(shareUris) {
        if (shareUris.isNotEmpty()) {
            val intent = if (shareUris.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, shareUris.first())
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "image/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(shareUris))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            context.startActivity(Intent.createChooser(intent, "Share clean copies"))
            viewModel.consumeShareUris()
        }
    }

    val sortedPhotos = remember(photos, viewModel.sortOrder) {
        when (viewModel.sortOrder) {
            EncryptedPreferenceStorage.SORT_DATE_ADDED_ASC -> photos.sortedBy { it.dateAdded }
            EncryptedPreferenceStorage.SORT_NAME_ASC -> photos.sortedBy { it.displayName.lowercase() }
            EncryptedPreferenceStorage.SORT_NAME_DESC -> photos.sortedByDescending { it.displayName.lowercase() }
            EncryptedPreferenceStorage.SORT_SIZE_DESC -> photos.sortedByDescending { it.size }
            else -> photos.sortedByDescending { it.dateAdded }
        }
    }

    val displayedPhotos = remember(
        photos,
        viewModel.sortOrder,
        currentAlbum,
        viewModel.searchQuery,
        viewModel.showFavoritesOnly,
        viewModel.filterWithExif,
        viewModel.filterHasGps,
        favoriteIds,
        exifCache
    ) {
        sortedPhotos.filter { photo ->
            val query = viewModel.searchQuery.trim().lowercase()
            val matchesSearch = query.isEmpty() ||
                    photo.displayName.lowercase().contains(query) ||
                    (photo.bucketDisplayName?.lowercase()?.contains(query) ?: false)

            val matchesAlbum = currentAlbum == null || photo.bucketId == currentAlbum?.bucketId

            val matchesFavorites = !viewModel.showFavoritesOnly || photo.id in favoriteIds

            val matchesExif = if (viewModel.filterWithExif) {
                exifCache[photo.id]?.hasExif == true
            } else true

            val matchesGps = if (viewModel.filterHasGps) {
                exifCache[photo.id]?.hasGps == true
            } else true

            matchesSearch && matchesAlbum && matchesFavorites && matchesExif && matchesGps
        }
    }

    val gridMinSize = when (viewModel.gridSize) {
        EncryptedPreferenceStorage.GRID_SMALL -> 80.dp
        EncryptedPreferenceStorage.GRID_LARGE -> 160.dp
        else -> 120.dp
    }

    val photoCount = displayedPhotos.size
    val totalCount = photos.size
    val isAlbumsHome = galleryViewMode == EncryptedPreferenceStorage.VIEW_ALBUMS && currentAlbum == null

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("${selectedIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Clear")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.showDeleteConfirm = true },
                            enabled = !batchProgress
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                        IconButton(
                            onClick = { viewModel.batchExportAndShare(StripMode.ALL) },
                            enabled = !batchProgress
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = "Share Clean")
                        }
                    }
                )
            } else if (currentAlbum != null) {
                TopAppBar(
                    title = {
                        Column {
                            Text(currentAlbum?.bucketDisplayName ?: "Unknown Album")
                            Text(
                                "$photoCount photo${if (photoCount != 1) "s" else ""}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.closeAlbum() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search")
                        }
                        Box {
                            IconButton(onClick = { sortMenuExpanded = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                            }
                            DropdownMenu(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { sortMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Date Added (newest)") },
                                    onClick = { viewModel.setSortOrder(EncryptedPreferenceStorage.SORT_DATE_ADDED_DESC); sortMenuExpanded = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Date Added (oldest)") },
                                    onClick = { viewModel.setSortOrder(EncryptedPreferenceStorage.SORT_DATE_ADDED_ASC); sortMenuExpanded = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Name (A-Z)") },
                                    onClick = { viewModel.setSortOrder(EncryptedPreferenceStorage.SORT_NAME_ASC); sortMenuExpanded = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Name (Z-A)") },
                                    onClick = { viewModel.setSortOrder(EncryptedPreferenceStorage.SORT_NAME_DESC); sortMenuExpanded = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Size (largest)") },
                                    onClick = { viewModel.setSortOrder(EncryptedPreferenceStorage.SORT_SIZE_DESC); sortMenuExpanded = false }
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                        }
                    }
                )
            } else if (isSearchActive) {
                TopAppBar(
                    title = {
                        TextField(
                            value = viewModel.searchQuery,
                            onValueChange = { viewModel.searchQuery = it },
                            placeholder = { Text("Search photos...") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = { focusManager.clearFocus() }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSearchActive = false
                            viewModel.searchQuery = ""
                            focusManager.clearFocus()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
                        }
                    },
                    actions = {
                        if (viewModel.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.searchQuery = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear")
                            }
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(if (isAlbumsHome) "Albums" else "EXIF Pure")
                            if (!isAlbumsHome && totalCount > 0) {
                                Text(
                                    "$photoCount / $totalCount photo${if (totalCount != 1) "s" else ""}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            } else if (isAlbumsHome) {
                                Text(
                                    "${albums.size} album${if (albums.size != 1) "s" else ""}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.toggleGalleryViewMode() }
                        ) {
                            Icon(
                                imageVector = if (isAlbumsHome) Icons.Filled.PhotoLibrary else Icons.Filled.PhotoAlbum,
                                contentDescription = if (isAlbumsHome) "Show Photos" else "Show Albums"
                            )
                        }
                        if (!isAlbumsHome) {
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(Icons.Filled.Search, contentDescription = "Search")
                            }
                            Box {
                                IconButton(onClick = { sortMenuExpanded = true }) {
                                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                                }
                            DropdownMenu(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { sortMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Date Added (newest)") },
                                    onClick = { viewModel.setSortOrder(EncryptedPreferenceStorage.SORT_DATE_ADDED_DESC); sortMenuExpanded = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Date Added (oldest)") },
                                    onClick = { viewModel.setSortOrder(EncryptedPreferenceStorage.SORT_DATE_ADDED_ASC); sortMenuExpanded = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Name (A-Z)") },
                                    onClick = { viewModel.setSortOrder(EncryptedPreferenceStorage.SORT_NAME_ASC); sortMenuExpanded = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Name (Z-A)") },
                                    onClick = { viewModel.setSortOrder(EncryptedPreferenceStorage.SORT_NAME_DESC); sortMenuExpanded = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Size (largest)") },
                                    onClick = { viewModel.setSortOrder(EncryptedPreferenceStorage.SORT_SIZE_DESC); sortMenuExpanded = false }
                                )
                            }
                            }
                        }
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                    }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when {
                !hasPermission -> PermissionPrompt(
                    onRequest = { permissionLauncher.launch(requiredPerms) }
                )
                isLoading && photos.isEmpty() -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = gridMinSize),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        items(24) { ShimmerBox() }
                    }
                }
                photos.isEmpty() -> EmptyState(
                    message = error ?: "No photos found on device",
                    onRetry = { viewModel.refresh() }
                )
                isAlbumsHome -> AlbumGrid(
                    albums = albums,
                    isLoading = isLoading,
                    onAlbumClick = { viewModel.openAlbum(it) }
                )
                else -> Column {
                    if (!isSelectionMode) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = viewModel.showFavoritesOnly,
                                onClick = { viewModel.toggleShowFavoritesOnly() },
                                label = { Text("Favorites") }
                            )
                            FilterChip(
                                selected = viewModel.filterWithExif,
                                onClick = { viewModel.toggleFilterWithExif() },
                                label = { Text("With EXIF") }
                            )
                            FilterChip(
                                selected = viewModel.filterHasGps,
                                onClick = { viewModel.toggleFilterHasGps() },
                                label = { Text("Has GPS") }
                            )
                        }
                        AnimatedVisibility(visible = isScanningExif) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text("Scanning EXIF data...", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = gridMinSize),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        items(displayedPhotos, key = { it.id }) { photo ->
                            val isSelected = selectedIds.contains(photo.id)
                            val isFavorite = favoriteIds.contains(photo.id)
                            val hasGps = exifCache[photo.id]?.hasGps == true
                            Box {
                                AsyncImage(
                                    model = coil.request.ImageRequest.Builder(context)
                                        .data(photo.uri)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = photo.displayName,
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .combinedClickable(
                                            onClick = {
                                                if (isSelectionMode) {
                                                    viewModel.toggleSelection(photo.id)
                                                } else {
                                                    onPhotoClick(photo)
                                                }
                                            },
                                            onLongClick = {
                                                if (viewModel.hapticEnabled) {
                                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                                }
                                                if (!isSelectionMode) {
                                                    viewModel.enterSelectionMode(photo.id)
                                                } else {
                                                    viewModel.toggleSelection(photo.id)
                                                }
                                            }
                                        )
                                        .then(
                                            if (isSelected) Modifier.background(
                                                Color.Blue.copy(alpha = 0.3f)
                                            ) else Modifier
                                        ),
                                    contentScale = ContentScale.Crop
                                )
                                if (!isSelectionMode) {
                                    if (hasGps) {
                                        Box(
                                            modifier = Modifier
                                                .padding(4.dp)
                                                .size(20.dp)
                                                .background(Color(0xFF4CAF50), CircleShape)
                                                .align(Alignment.BottomStart),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Filled.LocationOn,
                                                contentDescription = "Has GPS",
                                                tint = Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            if (viewModel.hapticEnabled) {
                                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                            }
                                            viewModel.toggleFavorite(photo.id)
                                        },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(32.dp)
                                            .padding(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                            contentDescription = if (isFavorite) "Unfavorite" else "Favorite",
                                            tint = if (isFavorite) Color.Red else Color.White.copy(alpha = 0.8f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .padding(8.dp)
                                            .size(24.dp)
                                            .background(Color.Blue, CircleShape)
                                            .align(Alignment.TopEnd),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (batchProgress) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            if (viewModel.showDeleteConfirm) {
                AlertDialog(
                    onDismissRequest = { viewModel.showDeleteConfirm = false },
                    title = { Text("Delete ${selectedIds.size} photos?") },
                    text = { Text("This cannot be undone. Photos will be permanently deleted.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.deleteSelected()
                                viewModel.showDeleteConfirm = false
                            }
                        ) {
                            Text("Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.showDeleteConfirm = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun AlbumGrid(
    albums: List<Album>,
    isLoading: Boolean,
    onAlbumClick: (Album) -> Unit
) {
    if (isLoading && albums.isEmpty()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 140.dp),
            contentPadding = PaddingValues(8.dp)
        ) {
            items(12) { ShimmerBox() }
        }
        return
    }

    if (albums.isEmpty()) {
        EmptyState(message = "No albums found", onRetry = {})
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        contentPadding = PaddingValues(8.dp)
    ) {
        items(albums, key = { it.bucketId }) { album ->
            AlbumGridItem(album = album, onClick = { onAlbumClick(album) })
        }
    }
}

@Composable
private fun AlbumGridItem(
    album: Album,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Card(
        onClick = onClick,
        modifier = Modifier
            .padding(6.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column {
            AsyncImage(
                model = coil.request.ImageRequest.Builder(context)
                    .data(album.coverPhotoUri)
                    .crossfade(true)
                    .build(),
                contentDescription = album.bucketDisplayName,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    text = album.bucketDisplayName ?: "Unknown",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${album.photoCount} photos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PermissionPrompt(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Photo access required", style = MaterialTheme.typography.titleMedium)
        Button(onClick = onRequest, modifier = Modifier.padding(top = 16.dp)) {
            Text("Grant Permission")
        }
    }
}

@Composable
private fun EmptyState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
            Text("Retry")
        }
    }
}
