package com.pureframe.exif.ui.screens.gallery

import android.Manifest
import android.app.PendingIntent
import android.content.pm.PackageManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.IntentSenderRequest
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
import androidx.compose.ui.input.pointer.*
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import android.util.Log
import coil.compose.AsyncImage
import com.pureframe.exif.ExifPureApplication
import com.pureframe.exif.data.local.ImageTooLargeException
import com.pureframe.exif.data.model.Album
import com.pureframe.exif.data.model.ExifSummary
import com.pureframe.exif.data.model.Photo
import com.pureframe.exif.data.model.StripMode
import com.pureframe.exif.data.repository.PhotoRepository
import com.pureframe.exif.ui.components.ShimmerBox
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock

/**
 * ViewModel for the gallery screen.
 *
 * Manages five distinct UI surfaces:
 * 1. **Photo grid** - loading, sorting, filtering (search, favorites, EXIF presence, GPS presence).
 * 2. **Selection mode** - long-press to enter; tap to toggle selection; batch actions (delete, share clean).
 * 3. **EXIF cache** - on-demand background scanning of EXIF/GPS presence for filter chips.
 * 4. **Batch export** - asynchronous export of multiple photos with progress and result reporting.
 * 5. **Album view** - group photos by bucket/album; browse albums and drill into individual albums.
 *
 * Mutable state is held in [MutableStateFlow] and exposed as read-only [StateFlow].
 * The [exifCache] is populated lazily when EXIF/GPS filters are first enabled.
 */
private const val SORT_DATE_ADDED_DESC = "date_added_desc"
private const val SORT_DATE_ADDED_ASC = "date_added_asc"
private const val SORT_NAME_ASC = "name_asc"
private const val SORT_NAME_DESC = "name_desc"
private const val SORT_SIZE_DESC = "size_desc"

private const val GRID_SMALL = "small"
private const val GRID_MEDIUM = "medium"
private const val GRID_LARGE = "large"

private const val VIEW_PHOTOS = "photos"
private const val VIEW_ALBUMS = "albums"

private const val STRIP_ALL = "all"
private const val STRIP_GPS = "gps"

private const val UNKNOWN_BUCKET_ID = "unknown"

private const val TAG = "GalleryViewModel"

class GalleryViewModel(
    private val repository: PhotoRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    // Core photo list
    private val _photos = MutableStateFlow<List<Photo>>(emptyList())
    val photos: StateFlow<List<Photo>> = _photos.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Album list
    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    private val _currentAlbum = MutableStateFlow<Album?>(null)
    val currentAlbum: StateFlow<Album?> = _currentAlbum.asStateFlow()

    // Selection mode. Restored from encrypted preferences so it survives both
    // system process death and user-initiated app kills (e.g. swipe from recents).
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    val isSelectionMode: StateFlow<Boolean> = _selectedIds
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Favorites
    private val _favoriteIds = MutableStateFlow<Set<Long>>(emptySet())
    val favoriteIds: StateFlow<Set<Long>> = _favoriteIds.asStateFlow()

    // Batch export
    private val _batchProgress = MutableStateFlow(false)
    val batchProgress: StateFlow<Boolean> = _batchProgress.asStateFlow()

    private val _batchResult = MutableStateFlow<String?>(null)
    val batchResult: StateFlow<String?> = _batchResult.asStateFlow()

    private val _shareUris = MutableStateFlow<List<android.net.Uri>>(emptyList())
    val shareUris: StateFlow<List<android.net.Uri>> = _shareUris.asStateFlow()

    // EXIF cache
    private val _exifCache = MutableStateFlow<Map<Long, ExifSummary>>(emptyMap())
    val exifCache: StateFlow<Map<Long, ExifSummary>> = _exifCache.asStateFlow()

    private val _isScanningExif = MutableStateFlow(false)
    val isScanningExif: StateFlow<Boolean> = _isScanningExif.asStateFlow()

    // Filter state
    private val _showFavoritesOnly = MutableStateFlow(false)
    val showFavoritesOnly: StateFlow<Boolean> = _showFavoritesOnly.asStateFlow()

    private val _filterWithExif = MutableStateFlow(false)
    val filterWithExif: StateFlow<Boolean> = _filterWithExif.asStateFlow()

    private val _filterHasGps = MutableStateFlow(false)
    val filterHasGps: StateFlow<Boolean> = _filterHasGps.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _showDeleteConfirm = MutableStateFlow(false)
    val showDeleteConfirm: StateFlow<Boolean> = _showDeleteConfirm.asStateFlow()

    private val _deleteConsentIntent = MutableStateFlow<PendingIntent?>(null)
    val deleteConsentIntent: StateFlow<PendingIntent?> = _deleteConsentIntent.asStateFlow()

    private val _deleteProgress = MutableStateFlow(false)
    val deleteProgress: StateFlow<Boolean> = _deleteProgress.asStateFlow()

    // True while either batch export or delete is running. Used to disable
    // selection-mode action buttons so the two operations cannot race.
    val isBusy: StateFlow<Boolean> = combine(_batchProgress, _deleteProgress) { exporting, deleting ->
        exporting || deleting
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Gallery view mode
    private val _galleryViewMode = MutableStateFlow(VIEW_PHOTOS)
    val galleryViewMode: StateFlow<String> = _galleryViewMode.asStateFlow()

    // Preference caches (loaded from repository in init and refreshed on resume)
    private val _sortOrder = MutableStateFlow(SORT_DATE_ADDED_DESC)
    val sortOrderFlow: StateFlow<String> = _sortOrder.asStateFlow()
    val sortOrder: String get() = _sortOrder.value

    private val _gridSize = MutableStateFlow(GRID_MEDIUM)
    val gridSizeFlow: StateFlow<String> = _gridSize.asStateFlow()
    val gridSize: String get() = _gridSize.value

    private val _hapticEnabled = MutableStateFlow(true)
    val hapticEnabledFlow: StateFlow<Boolean> = _hapticEnabled.asStateFlow()
    val hapticEnabled: Boolean get() = _hapticEnabled.value

    private val _defaultStripMode = MutableStateFlow(STRIP_ALL)
    val defaultStripModeFlow: StateFlow<String> = _defaultStripMode.asStateFlow()
    val defaultStripMode: String get() = _defaultStripMode.value

    // Derived photo list computed off the main thread.
    val displayedPhotos: StateFlow<List<Photo>> = combine(
        combine(_photos, _sortOrder, _searchQuery, ::Triple),
        combine(_showFavoritesOnly, _filterWithExif, _filterHasGps, ::Triple),
        combine(_favoriteIds, _exifCache, _currentAlbum, ::Triple)
    ) { (photos, sortOrder, query), (favoritesOnly, withExif, hasGps), (favoriteIds, exifCache, currentAlbum) ->
        val sorted = when (sortOrder) {
            SORT_DATE_ADDED_ASC -> photos.sortedBy { it.dateAdded }
            SORT_NAME_ASC -> photos.sortedBy { it.displayName.lowercase() }
            SORT_NAME_DESC -> photos.sortedByDescending { it.displayName.lowercase() }
            SORT_SIZE_DESC -> photos.sortedByDescending { it.size }
            else -> photos.sortedByDescending { it.dateAdded }
        }

        val trimmed = query.trim().lowercase()
        sorted.filter { photo ->
            val matchesSearch = trimmed.isEmpty() ||
                    photo.displayName.lowercase().contains(trimmed) ||
                    (photo.bucketDisplayName?.lowercase()?.contains(trimmed) ?: false)

            val matchesAlbum = currentAlbum == null ||
                    (photo.bucketId ?: UNKNOWN_BUCKET_ID) == currentAlbum.bucketId

            val matchesFavorites = !favoritesOnly || photo.id in favoriteIds

            val matchesExif = if (withExif) {
                exifCache[photo.id]?.hasExif == true
            } else true

            val matchesGps = if (hasGps) {
                exifCache[photo.id]?.hasGps == true
            } else true

            matchesSearch && matchesAlbum && matchesFavorites && matchesExif && matchesGps
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Serialize favorite mutations and EXIF cache launch so concurrent calls cannot
    // corrupt local state or start redundant scans.
    private val favoriteMutex = Mutex()
    private val exifCacheMutex = Mutex()

    // Count active EXIF scan jobs so a cancelled scan's finally block cannot
    // clear the indicator while a newer scan is still running.
    private val activeExifScans = java.util.concurrent.atomic.AtomicInteger(0)

    init {
        refresh()
        viewModelScope.launch(Dispatchers.IO) {
            loadPreference(
                name = "favorites",
                reader = { favoriteMutex.withLock { repository.getFavoriteIds() } },
                setter = { _favoriteIds.value = it },
                setError = true
            )
            loadPreference("gallery view mode", { repository.getGalleryViewMode() }, { _galleryViewMode.value = it }, setError = true)
            loadPreference("sort order", { repository.getSortOrder() }, { _sortOrder.value = it }, setError = true)
            loadPreference("grid size", { repository.getGridSize() }, { _gridSize.value = it }, setError = true)
            loadPreference("haptic setting", { repository.getHapticEnabled() }, { _hapticEnabled.value = it }, setError = true)
            loadPreference("default strip mode", { repository.getDefaultStripMode() }, { _defaultStripMode.value = it }, setError = true)

            // Selection is loaded last because it must survive user-initiated
            // kills that do not save instance state (swipe from recents). The
            // observer is started only after the initial load so the empty default
            // value cannot overwrite the persisted set before it is read back.
            loadPreference(
                name = "selected ids",
                reader = { repository.getSelectedIds() },
                setter = { _selectedIds.value = it },
                setError = false
            )

            // Persist selection changes to encrypted storage so they outlive the process.
            _selectedIds
                .onEach { repository.setSelectedIds(it) }
                .launchIn(viewModelScope)
        }
    }

    private var refreshJob: Job? = null

    /**
     * Reloads the full photo list and album list from MediaStore.
     *
     * Called on init, on user pull-to-refresh, and after batch delete.
     * Stale EXIF cache entries and selected IDs are removed to match the new list,
     * and active EXIF/GPS filters trigger a fresh scan.
     */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val list = repository.getPhotos()
                _photos.value = list
                _albums.value = buildAlbums(list)

                val currentIds = list.map { it.id }.toSet()

                // Invalidate EXIF cache entries for photos that no longer exist.
                _exifCache.value = _exifCache.value.filterKeys { it in currentIds }

                // Drop selections that point to deleted photos.
                _selectedIds.value = _selectedIds.value.filter { it in currentIds }.toSet()

                if (_filterWithExif.value || _filterHasGps.value) ensureExifCache(force = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = "Could not load photos"
                Log.e(TAG, "Failed to load photos", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Builds album list from an in-memory photo list.
     *
     * Photos with a null bucket ID are grouped under a stable "unknown" sentinel
     * so the album filter can match them later.
     */
    private fun buildAlbums(photos: List<Photo>): List<Album> {
        return photos
            .groupBy { it.bucketId ?: UNKNOWN_BUCKET_ID }
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

    /** Changes the sort order, persists it, and reloads the grid. */
    fun setSortOrder(order: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repository.setSortOrder(order) }
                _sortOrder.value = order
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist sort order", e)
            }
        }
    }

    /** Toggles the gallery home view between flat photos and album grid. */
    fun toggleGalleryViewMode() {
        val newMode = if (_galleryViewMode.value == VIEW_PHOTOS) VIEW_ALBUMS else VIEW_PHOTOS
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repository.setGalleryViewMode(newMode) }
                _galleryViewMode.value = newMode
                _currentAlbum.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist gallery view mode", e)
            }
        }
    }

    /** Opens the given album in detail view and clears transient filters. */
    fun openAlbum(album: Album) {
        _currentAlbum.value = album
        clearSearch()
        _showFavoritesOnly.value = false
        _filterWithExif.value = false
        _filterHasGps.value = false
    }

    /** Closes the current album detail and returns to the top-level grid. */
    fun closeAlbum() {
        _currentAlbum.value = null
    }

    /** Toggles the favorite status of a single photo and syncs local state. */
    fun toggleFavorite(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            favoriteMutex.withLock {
                val previous = _favoriteIds.value
                try {
                    repository.toggleFavorite(id)
                    _favoriteIds.value = repository.getFavoriteIds()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to toggle favorite", e)
                    _favoriteIds.value = previous
                }
            }
        }
    }

    /**
     * Reloads favorite IDs and preference values from persistent storage.
     *
     * Called when the gallery screen resumes so that changes made from
     * the detail screen, settings, or other flows are reflected in local state.
     */
    fun refreshFavorites() {
        viewModelScope.launch(Dispatchers.IO) {
            loadPreference(
                name = "favorites",
                reader = { favoriteMutex.withLock { repository.getFavoriteIds() } },
                setter = { _favoriteIds.value = it }
            )
            loadPreference("gallery view mode", { repository.getGalleryViewMode() }, { _galleryViewMode.value = it })
            loadPreference("sort order", { repository.getSortOrder() }, { _sortOrder.value = it })
            loadPreference("grid size", { repository.getGridSize() }, { _gridSize.value = it })
            loadPreference("haptic setting", { repository.getHapticEnabled() }, { _hapticEnabled.value = it })
            loadPreference("default strip mode", { repository.getDefaultStripMode() }, { _defaultStripMode.value = it })
        }
    }

    /**
     * Reads a single preference value and applies it, swallowing non-fatal errors
     * so a corrupted preference store cannot crash the gallery.
     *
     * Cancellation is re-thrown so coroutines clean up correctly.
     */
    private suspend fun <T> loadPreference(
        name: String,
        reader: suspend () -> T,
        setter: (T) -> Unit,
        setError: Boolean = false
    ) {
        try {
            setter(reader())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load $name", e)
            if (setError) _error.value = "Could not load preferences"
        }
    }

    /** Toggles the favorites-only filter. */
    fun toggleShowFavoritesOnly() {
        _showFavoritesOnly.value = !_showFavoritesOnly.value
    }

    /** Toggles the "With EXIF" filter. Triggers lazy scan if cache is empty. */
    fun toggleFilterWithExif() {
        _filterWithExif.value = !_filterWithExif.value
        if (_filterWithExif.value) ensureExifCache()
    }

    /** Toggles the "Has GPS" filter. Triggers lazy scan if cache is empty. */
    fun toggleFilterHasGps() {
        _filterHasGps.value = !_filterHasGps.value
        if (_filterHasGps.value) ensureExifCache()
    }

    /**
     * Ensures the EXIF cache is populated.
     *
     * This is a lazy background operation guarded by a mutex so rapid toggles
     * cannot start redundant scans. It iterates over the current photo list,
     * reads EXIF for each, and stores a lightweight [ExifSummary].
     *
     * @param force When true, any active scan is cancelled and all current photos
     *              (including previously failed reads) are reconsidered for scanning.
     *              Used by [refresh] so newly added photos are not skipped because
     *              a scan from an earlier filter toggle is still running.
     */
    private var exifCacheJob: Job? = null

    private fun ensureExifCache(force: Boolean = false) {
        viewModelScope.launch(Dispatchers.Default) {
            exifCacheMutex.withLock {
                if (force) {
                    exifCacheJob?.cancel()
                    exifCacheJob = null
                }

                val currentIds = _photos.value.map { it.id }.toSet()
                val cache = _exifCache.value
                val missingIds = currentIds.filter { id ->
                    val entry = cache[id]
                    entry == null || entry.scanFailed
                }.toSet()

                if (missingIds.isEmpty() || exifCacheJob?.isActive == true) return@withLock

                exifCacheJob?.cancel()
                exifCacheJob = viewModelScope.launch(Dispatchers.IO) {
                    activeExifScans.incrementAndGet()
                    _isScanningExif.value = true
                    try {
                        val newEntries = mutableMapOf<Long, ExifSummary>()
                        _photos.value.filter { it.id in missingIds }.forEach { photo ->
                            if (!isActive) return@launch
                            try {
                                val meta = repository.getExif(photo)
                                newEntries[photo.id] = ExifSummary(
                                    photoId = photo.id,
                                    hasExif = meta.hasExif,
                                    hasGps = meta.gpsLatitude != null,
                                    cameraModel = meta.model
                                )
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                // Mark the read as failed so the next scan retries
                                // this ID instead of leaving it permanently uncached.
                                newEntries[photo.id] = ExifSummary(
                                    photoId = photo.id,
                                    hasExif = false,
                                    hasGps = false,
                                    cameraModel = null,
                                    scanFailed = true
                                )
                            }
                        }
                        val currentIds = _photos.value.map { it.id }.toSet()
                        _exifCache.update { cache ->
                            (cache + newEntries).filterKeys { it in currentIds }
                        }
                    } finally {
                        if (activeExifScans.decrementAndGet() == 0) {
                            _isScanningExif.value = false
                        }
                    }
                }
            }
        }
    }

    /** Updates the search query. */
    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    /** Clears the current search query. */
    fun clearSearch() {
        _searchQuery.value = ""
    }

    /** Shows or hides the delete confirmation dialog. */
    fun setShowDeleteConfirm(show: Boolean) {
        _showDeleteConfirm.value = show
    }

    /** Toggles selection of a single photo ID. Exits selection mode if the set becomes empty. */
    fun toggleSelection(id: Long) {
        _selectedIds.value = _selectedIds.value.toMutableSet().apply {
            if (contains(id)) remove(id) else add(id)
        }
    }

    /** Enters selection mode and selects the initial photo (triggered by long-press). */
    fun enterSelectionMode(id: Long) {
        _selectedIds.value = setOf(id)
    }

    /** Clears all selections and exits selection mode. */
    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    /**
     * Permanently deletes all selected photos via MediaStore.
     *
     * Shows a confirmation dialog before invocation ([showDeleteConfirm]).
     * On API 30+ this uses MediaStore.createDeleteRequest() which shows a
     * system confirmation dialog. On API 29 it handles RecoverableSecurityException.
     * Concurrent calls are ignored while a delete is already in progress.
     */
    fun deleteSelected() {
        if (_deleteProgress.value || _batchProgress.value || _selectedIds.value.isEmpty()) return
        viewModelScope.launch {
            _deleteProgress.value = true
            try {
                val photos = _photos.value.filter { it.id in _selectedIds.value }
                val uris = photos.map { it.uri }
                savedStateHandle["pendingDeleteUris"] = ArrayList(uris)
                when (val result = repository.deletePhotos(photos)) {
                    is com.pureframe.exif.data.local.DeleteResult.Success -> {
                        savedStateHandle["pendingDeleteUris"] = null
                        _batchResult.value = "Deleted ${result.count} photos"
                        clearSelection()
                        refresh()
                    }
                    is com.pureframe.exif.data.local.DeleteResult.NeedsConsent -> {
                        _deleteConsentIntent.value = result.pendingIntent
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                savedStateHandle["pendingDeleteUris"] = null
                _batchResult.value = "Could not delete photos"
                Log.e(TAG, "Delete failed", e)
            } finally {
                _deleteProgress.value = false
            }
        }
    }

    fun consumeDeleteConsentIntent() {
        _deleteConsentIntent.value = null
    }

    /**
     * Called after the system consent dialog is dismissed.
     * On API 30+ the system handles deletion itself; we just refresh.
     * On API 29 we retry the delete only if the user confirmed.
     */
    fun retryDeleteAfterConsent(resultCode: Int = android.app.Activity.RESULT_OK) {
        if (_deleteProgress.value || _batchProgress.value) return
        viewModelScope.launch {
            _deleteProgress.value = true
            try {
                val uris = savedStateHandle.get<ArrayList<android.net.Uri>>("pendingDeleteUris")
                    ?: emptyList()
                savedStateHandle["pendingDeleteUris"] = null
                if (uris.isEmpty()) return@launch

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (resultCode == android.app.Activity.RESULT_OK) {
                        _batchResult.value = "Photos deleted"
                    } else {
                        _batchResult.value = "Deletion cancelled"
                    }
                    clearSelection()
                    refresh()
                } else {
                    if (resultCode != android.app.Activity.RESULT_OK) {
                        _batchResult.value = "Deletion cancelled"
                        clearSelection()
                        return@launch
                    }
                    try {
                        when (val result = repository.deletePhotosByUri(uris)) {
                            is com.pureframe.exif.data.local.DeleteResult.Success -> {
                                _batchResult.value = "Deleted ${result.count} photos"
                                clearSelection()
                                refresh()
                            }
                            else -> {
                                _batchResult.value = "Could not delete photos"
                                clearSelection()
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        savedStateHandle["pendingDeleteUris"] = null
                        _batchResult.value = "Could not delete photos"
                        Log.e(TAG, "Retry delete failed", e)
                    }
                }
            } finally {
                _deleteProgress.value = false
            }
        }
    }

    /**
     * Exports all selected photos as clean copies and queues them for sharing.
     *
     * Uses the user's default strip mode. Progress is shown via [_batchProgress];
     * results are posted to [_batchResult] and the share sheet is triggered via [_shareUris].
     * Concurrent calls are ignored while an export is already running.
     */
    fun batchExportAndShare(mode: StripMode) {
        if (_batchProgress.value || _deleteProgress.value || _selectedIds.value.isEmpty()) return
        viewModelScope.launch {
            _batchProgress.value = true
            try {
                val photos = _photos.value.filter { it.id in _selectedIds.value }
                val results = repository.batchExport(photos, mode)
                val success = results.count { it.isSuccess }
                val failed = results.size - success
                val uris = results.mapNotNull { it.getOrNull() }
                val firstException = results.firstOrNull { it.isFailure }?.exceptionOrNull()
                val reason = when {
                    firstException is ImageTooLargeException -> "Image exceeds 200 MB size limit"
                    firstException == null -> "File may be corrupted or unsupported"
                    firstException.message?.contains("EOF", ignoreCase = true) == true -> "File appears corrupted or incomplete"
                    firstException.message?.contains("Invalid JPEG", ignoreCase = true) == true -> "Unsupported or damaged image format"
                    firstException.message?.contains("MediaStore", ignoreCase = true) == true -> "Unable to save to device storage"
                    else -> "File may be corrupted or unsupported"
                }
                _batchResult.value = when {
                    success == 0 && failed > 0 -> "Export failed: $reason"
                    success > 0 && failed > 0 -> "Exported $success, $failed failed: $reason"
                    else -> "Exported $success clean copies"
                }
                _shareUris.value = uris
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _batchResult.value = "Export failed"
                Log.e(TAG, "Batch export failed", e)
            } finally {
                _batchProgress.value = false
                clearSelection()
            }
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
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras
                ): T {
                    val app = context.applicationContext as ExifPureApplication
                    val handle = extras.createSavedStateHandle()
                    return GalleryViewModel(app.container.repository, handle) as T
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
    val isBusy by viewModel.isBusy.collectAsState()
    val batchResult by viewModel.batchResult.collectAsState()
    val shareUris by viewModel.shareUris.collectAsState()
    val exifCache by viewModel.exifCache.collectAsState()
    val isScanningExif by viewModel.isScanningExif.collectAsState()
    val currentAlbum by viewModel.currentAlbum.collectAsState()
    val galleryViewMode by viewModel.galleryViewMode.collectAsState()
    val deleteConsentIntent by viewModel.deleteConsentIntent.collectAsState()

    val showFavoritesOnly by viewModel.showFavoritesOnly.collectAsState()
    val filterWithExif by viewModel.filterWithExif.collectAsState()
    val filterHasGps by viewModel.filterHasGps.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val showDeleteConfirm by viewModel.showDeleteConfirm.collectAsState()
    val sortOrder by viewModel.sortOrderFlow.collectAsState()
    val gridSize by viewModel.gridSizeFlow.collectAsState()
    val hapticEnabled by viewModel.hapticEnabledFlow.collectAsState()
    val defaultStripMode by viewModel.defaultStripModeFlow.collectAsState()
    val displayedPhotos by viewModel.displayedPhotos.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var sortMenuExpanded by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    BackHandler(enabled = isSelectionMode || currentAlbum != null || isSearchActive) {
        when {
            isSearchActive -> {
                isSearchActive = false
                viewModel.clearSearch()
                focusManager.clearFocus()
            }
            isSelectionMode -> viewModel.clearSelection()
            currentAlbum != null -> viewModel.closeAlbum()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) viewModel.refresh()
    }

    val intentSenderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.retryDeleteAfterConsent(result.resultCode)
    }

    val requiredPerms = remember {
        when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
            Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            // WRITE_EXTERNAL_STORAGE is only declared up to API 28 in the manifest.
            // Requesting it on API 29-32 makes hasPermission permanently false.
            Build.VERSION.SDK_INT >= 29 -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            else -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }

    val hasPermission = if (Build.VERSION.SDK_INT >= 34) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
    } else {
        requiredPerms.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission && photos.isEmpty() && !isLoading) viewModel.refresh()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshFavorites()
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
                    clipData = ClipData.newRawUri("", shareUris.first())
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "image/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(shareUris))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData = ClipData.newRawUri("", shareUris.first()).apply {
                        shareUris.drop(1).forEach { addItem(ClipData.Item(it)) }
                    }
                }
            }
            try {
                val chooser = Intent.createChooser(intent, "Share clean copies").apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(chooser)
            } catch (_: ActivityNotFoundException) {
                // No app can handle the share intent.
            } catch (_: Exception) {
                // Ignore other launch failures.
            } finally {
                viewModel.consumeShareUris()
            }
        }
    }

    LaunchedEffect(deleteConsentIntent) {
        deleteConsentIntent?.let { pending ->
            intentSenderLauncher.launch(
                IntentSenderRequest.Builder(pending.intentSender).build()
            )
            viewModel.consumeDeleteConsentIntent()
        }
    }

    val gridMinSize = when (gridSize) {
        GRID_SMALL -> 80.dp
        GRID_LARGE -> 160.dp
        else -> 120.dp
    }

    val photoCount = displayedPhotos.size
    val totalCount = photos.size
    val isAlbumsHome = galleryViewMode == VIEW_ALBUMS && currentAlbum == null

    Scaffold(
        topBar = {
            when {
                isSelectionMode -> SelectionTopAppBar(
                    selectedCount = selectedIds.size,
                    isBusy = isBusy,
                    defaultStripMode = defaultStripMode,
                    onClearSelection = { viewModel.clearSelection() },
                    onDeleteClick = { viewModel.setShowDeleteConfirm(true) },
                    onShareClick = { mode -> viewModel.batchExportAndShare(mode) }
                )
                currentAlbum != null -> AlbumTopAppBar(
                    album = currentAlbum!!,
                    photoCount = photoCount,
                    sortOrder = sortOrder,
                    onBackClick = { viewModel.closeAlbum() },
                    onSearchClick = { isSearchActive = true },
                    onSortOrderChange = { viewModel.setSortOrder(it) },
                    onSortMenuDismiss = { sortMenuExpanded = false },
                    sortMenuExpanded = sortMenuExpanded,
                    onSortMenuExpand = { sortMenuExpanded = true },
                    onRefresh = { viewModel.refresh() }
                )
                isSearchActive -> SearchTopAppBar(
                    query = searchQuery,
                    onQueryChange = { viewModel.onSearchQueryChange(it) },
                    onClearQuery = { viewModel.clearSearch() },
                    onClose = {
                        isSearchActive = false
                        viewModel.clearSearch()
                        focusManager.clearFocus()
                    }
                )
                else -> GalleryTopAppBar(
                    isAlbumsHome = isAlbumsHome,
                    photoCount = photoCount,
                    totalCount = totalCount,
                    albumCount = albums.size,
                    sortOrder = sortOrder,
                    onToggleViewMode = { viewModel.toggleGalleryViewMode() },
                    onSearchClick = { isSearchActive = true },
                    onSortOrderChange = { viewModel.setSortOrder(it) },
                    onSortMenuDismiss = { sortMenuExpanded = false },
                    sortMenuExpanded = sortMenuExpanded,
                    onSortMenuExpand = { sortMenuExpanded = true },
                    onRefresh = { viewModel.refresh() },
                    onSettingsClick = onSettingsClick
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (!hasPermission) {
            Box(modifier = Modifier.padding(padding)) {
                PermissionPrompt(onRequest = { permissionLauncher.launch(requiredPerms) })
            }
        } else {
            PullToRefreshBox(
                isRefreshing = isLoading,
                onRefresh = { viewModel.refresh() },
                state = rememberPullToRefreshState(),
                modifier = Modifier.padding(padding)
            ) {
                when {
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
                    else -> PhotoGrid(
                        displayedPhotos = displayedPhotos,
                        selectedIds = selectedIds,
                        favoriteIds = favoriteIds,
                        exifCache = exifCache,
                        isSelectionMode = isSelectionMode,
                        isScanningExif = isScanningExif,
                        hapticEnabled = hapticEnabled,
                        gridMinSize = gridMinSize,
                        showFavoritesOnly = showFavoritesOnly,
                        filterWithExif = filterWithExif,
                        filterHasGps = filterHasGps,
                        onPhotoClick = onPhotoClick,
                        onEnterSelectionMode = { viewModel.enterSelectionMode(it.id) },
                        onToggleSelection = { viewModel.toggleSelection(it.id) },
                        onFavoriteClick = { viewModel.toggleFavorite(it.id) },
                        onToggleShowFavoritesOnly = { viewModel.toggleShowFavoritesOnly() },
                        onToggleFilterWithExif = { viewModel.toggleFilterWithExif() },
                        onToggleFilterHasGps = { viewModel.toggleFilterHasGps() }
                    )
                }
            }
            if (batchProgress) {
                BatchProgressOverlay()
            }
            if (showDeleteConfirm) {
                DeleteConfirmDialog(
                    selectedCount = selectedIds.size,
                    onConfirm = {
                        viewModel.deleteSelected()
                        viewModel.setShowDeleteConfirm(false)
                    },
                    onDismiss = { viewModel.setShowDeleteConfirm(false) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GalleryTopAppBar(
    isAlbumsHome: Boolean,
    photoCount: Int,
    totalCount: Int,
    albumCount: Int,
    sortOrder: String,
    onToggleViewMode: () -> Unit,
    onSearchClick: () -> Unit,
    onSortOrderChange: (String) -> Unit,
    onSortMenuDismiss: () -> Unit,
    sortMenuExpanded: Boolean,
    onSortMenuExpand: () -> Unit,
    onRefresh: () -> Unit,
    onSettingsClick: () -> Unit
) {
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
                        "$albumCount album${if (albumCount != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onToggleViewMode) {
                Icon(
                    imageVector = if (isAlbumsHome) Icons.Filled.PhotoLibrary else Icons.Filled.PhotoAlbum,
                    contentDescription = if (isAlbumsHome) "Show Photos" else "Show Albums"
                )
            }
            if (!isAlbumsHome) {
                IconButton(onClick = onSearchClick) {
                    Icon(Icons.Filled.Search, contentDescription = "Search")
                }
                SortDropdownMenu(
                    sortOrder = sortOrder,
                    expanded = sortMenuExpanded,
                    onExpand = onSortMenuExpand,
                    onDismiss = onSortMenuDismiss,
                    onSortOrderChange = onSortOrderChange
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumTopAppBar(
    album: Album,
    photoCount: Int,
    sortOrder: String,
    onBackClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSortOrderChange: (String) -> Unit,
    onSortMenuDismiss: () -> Unit,
    sortMenuExpanded: Boolean,
    onSortMenuExpand: () -> Unit,
    onRefresh: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(album.bucketDisplayName ?: "Unknown Album")
                Text(
                    "$photoCount photo${if (photoCount != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            IconButton(onClick = onSearchClick) {
                Icon(Icons.Filled.Search, contentDescription = "Search")
            }
            SortDropdownMenu(
                sortOrder = sortOrder,
                expanded = sortMenuExpanded,
                onExpand = onSortMenuExpand,
                onDismiss = onSortMenuDismiss,
                onSortOrderChange = onSortOrderChange
            )
            IconButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopAppBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onClose: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    TopAppBar(
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
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
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
            }
        },
        actions = {
            // When a query exists, the close button clears it so the user can
            // refine their search without leaving search mode. When the query is
            // already empty, the same affordance closes search mode entirely.
            if (query.isNotEmpty()) {
                IconButton(onClick = onClearQuery) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear")
                }
            } else {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close search")
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopAppBar(
    selectedCount: Int,
    isBusy: Boolean,
    defaultStripMode: String,
    onClearSelection: () -> Unit,
    onDeleteClick: () -> Unit,
    onShareClick: (StripMode) -> Unit
) {
    TopAppBar(
        title = { Text("$selectedCount selected") },
        navigationIcon = {
            IconButton(onClick = onClearSelection) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Clear")
            }
        },
        actions = {
            IconButton(
                onClick = onDeleteClick,
                enabled = !isBusy
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete")
            }
            IconButton(
                onClick = {
                    val mode = if (defaultStripMode == STRIP_GPS) {
                        StripMode.GPS_ONLY
                    } else {
                        StripMode.ALL
                    }
                    onShareClick(mode)
                },
                enabled = !isBusy
            ) {
                Icon(Icons.Filled.Share, contentDescription = "Share Clean")
            }
        }
    )
}

@Composable
private fun SortDropdownMenu(
    sortOrder: String,
    expanded: Boolean,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    onSortOrderChange: (String) -> Unit
) {
    Box {
        IconButton(onClick = onExpand) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss
        ) {
            DropdownMenuItem(
                text = { Text("Date Added (newest)") },
                onClick = { onSortOrderChange(SORT_DATE_ADDED_DESC); onDismiss() }
            )
            DropdownMenuItem(
                text = { Text("Date Added (oldest)") },
                onClick = { onSortOrderChange(SORT_DATE_ADDED_ASC); onDismiss() }
            )
            DropdownMenuItem(
                text = { Text("Name (A-Z)") },
                onClick = { onSortOrderChange(SORT_NAME_ASC); onDismiss() }
            )
            DropdownMenuItem(
                text = { Text("Name (Z-A)") },
                onClick = { onSortOrderChange(SORT_NAME_DESC); onDismiss() }
            )
            DropdownMenuItem(
                text = { Text("Size (largest)") },
                onClick = { onSortOrderChange(SORT_SIZE_DESC); onDismiss() }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoGrid(
    displayedPhotos: List<Photo>,
    selectedIds: Set<Long>,
    favoriteIds: Set<Long>,
    exifCache: Map<Long, ExifSummary>,
    isSelectionMode: Boolean,
    isScanningExif: Boolean,
    hapticEnabled: Boolean,
    gridMinSize: Dp,
    showFavoritesOnly: Boolean,
    filterWithExif: Boolean,
    filterHasGps: Boolean,
    onPhotoClick: (Photo) -> Unit,
    onEnterSelectionMode: (Photo) -> Unit,
    onToggleSelection: (Photo) -> Unit,
    onFavoriteClick: (Photo) -> Unit,
    onToggleShowFavoritesOnly: () -> Unit,
    onToggleFilterWithExif: () -> Unit,
    onToggleFilterHasGps: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (!isSelectionMode) {
            FilterChips(
                showFavoritesOnly = showFavoritesOnly,
                filterWithExif = filterWithExif,
                filterHasGps = filterHasGps,
                onToggleShowFavoritesOnly = onToggleShowFavoritesOnly,
                onToggleFilterWithExif = onToggleFilterWithExif,
                onToggleFilterHasGps = onToggleFilterHasGps
            )
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
                PhotoGridItem(
                    photo = photo,
                    isSelected = selectedIds.contains(photo.id),
                    isFavorite = favoriteIds.contains(photo.id),
                    hasGps = exifCache[photo.id]?.hasGps == true,
                    isSelectionMode = isSelectionMode,
                    hapticEnabled = hapticEnabled,
                    gridMinSize = gridMinSize,
                    onPhotoClick = { onPhotoClick(photo) },
                    onEnterSelectionMode = { onEnterSelectionMode(photo) },
                    onToggleSelection = { onToggleSelection(photo) },
                    onFavoriteClick = { onFavoriteClick(photo) }
                )
            }
        }
    }
}

@Composable
private fun FilterChips(
    showFavoritesOnly: Boolean,
    filterWithExif: Boolean,
    filterHasGps: Boolean,
    onToggleShowFavoritesOnly: () -> Unit,
    onToggleFilterWithExif: () -> Unit,
    onToggleFilterHasGps: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = showFavoritesOnly,
            onClick = onToggleShowFavoritesOnly,
            label = { Text("Favorites") }
        )
        FilterChip(
            selected = filterWithExif,
            onClick = onToggleFilterWithExif,
            label = { Text("With EXIF") }
        )
        FilterChip(
            selected = filterHasGps,
            onClick = onToggleFilterHasGps,
            label = { Text("Has GPS") }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoGridItem(
    photo: Photo,
    isSelected: Boolean,
    isFavorite: Boolean,
    hasGps: Boolean,
    isSelectionMode: Boolean,
    hapticEnabled: Boolean,
    gridMinSize: Dp,
    onPhotoClick: () -> Unit,
    onEnterSelectionMode: () -> Unit,
    onToggleSelection: () -> Unit,
    onFavoriteClick: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val thumbPx = with(density) { gridMinSize.roundToPx() }

    Box {
        Box(
            modifier = Modifier
                .padding(4.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else Color.Transparent
                )
                .combinedClickable(
                    onClick = {
                        if (isSelectionMode) onToggleSelection() else onPhotoClick()
                    },
                    onLongClick = {
                        if (hapticEnabled) {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        }
                        if (isSelectionMode) onToggleSelection() else onEnterSelectionMode()
                    }
                )
        ) {
            AsyncImage(
                model = coil.request.ImageRequest.Builder(context)
                    .data(photo.uri)
                    .crossfade(true)
                    .size(thumbPx, thumbPx)
                    .memoryCacheKey(photo.uri.toString())
                    .build(),
                contentDescription = photo.displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        if (!isSelectionMode) {
            if (hasGps) {
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(20.dp)
                        .background(MaterialTheme.colorScheme.tertiary, CircleShape)
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
                    if (hapticEnabled) {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    }
                    onFavoriteClick()
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (isFavorite) "Unfavorite" else "Favorite",
                    tint = if (isFavorite) MaterialTheme.colorScheme.error else Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        if (isSelected) {
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .size(24.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .align(Alignment.TopEnd),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun BatchProgressOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    // Consume all pointer events while the batch is running so
                    // the user cannot interact with the grid underneath.
                    // Loop forever; the coroutine is cancelled automatically
                    // when the overlay leaves composition.
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun DeleteConfirmDialog(
    selectedCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete $selectedCount photos?") },
        text = { Text("This cannot be undone. Photos will be permanently deleted.") },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
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
    val density = LocalDensity.current
    val coverPx = with(density) { 140.dp.roundToPx() }
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
                    .size(coverPx, coverPx)
                    .memoryCacheKey(album.coverPhotoUri.toString())
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
