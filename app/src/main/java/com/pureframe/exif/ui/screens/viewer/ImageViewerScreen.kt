package com.pureframe.exif.ui.screens.viewer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.pureframe.exif.ExifPureApplication
import com.pureframe.exif.R
import com.pureframe.exif.data.model.Photo
import com.pureframe.exif.data.repository.PhotoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ImageViewerViewModel(
    private val repository: PhotoRepository,
    private val photoId: Long
) : ViewModel() {
    private val _photo = MutableStateFlow<Photo?>(null)
    val photo: StateFlow<Photo?> = _photo.asStateFlow()

    init {
        viewModelScope.launch {
            _photo.value = repository.getPhoto(photoId)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewerScreen(
    photoId: Long,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: ImageViewerViewModel = viewModel(
        key = photoId.toString(),
        factory = remember(photoId) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val app = context.applicationContext as ExifPureApplication
                    return ImageViewerViewModel(app.container.repository, photoId) as T
                }
            }
        }
    )
    val photo by viewModel.photo.collectAsState()

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val coroutineScope = rememberCoroutineScope()

    val animatedScale = remember { Animatable(1f) }
    val animatedOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

    fun resetZoom() {
        coroutineScope.launch {
            animatedScale.animateTo(1f, tween(200))
            animatedOffset.animateTo(Offset.Zero, tween(200))
            scale = 1f
            offset = Offset.Zero
        }
    }

    fun zoomIn() {
        coroutineScope.launch {
            val target = if (scale < 2f) 3f else 1f
            animatedScale.animateTo(target, tween(200))
            if (target == 1f) {
                animatedOffset.animateTo(Offset.Zero, tween(200))
            }
            scale = target
            if (target == 1f) offset = Offset.Zero
        }
    }

    val displayMetrics = remember { context.resources.displayMetrics }
    val screenWidth = displayMetrics.widthPixels.toFloat()
    val screenHeight = displayMetrics.heightPixels.toFloat()

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 5f)
        val newOffset = if (newScale > 1f) {
            val maxX = (newScale - 1) * screenWidth * 0.5f
            val maxY = (newScale - 1) * screenHeight * 0.5f
            Offset(
                x = (offset.x + panChange.x * newScale).coerceIn(-maxX, maxX),
                y = (offset.y + panChange.y * newScale).coerceIn(-maxY, maxY)
            )
        } else Offset.Zero

        scale = newScale
        offset = newOffset
        coroutineScope.launch {
            animatedScale.snapTo(newScale)
            animatedOffset.snapTo(newOffset)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(photo?.displayName ?: stringResource(R.string.viewer_title)) },
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            photo?.let { p ->
                AsyncImage(
                    model = p.uri,
                    contentDescription = p.displayName,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = { zoomIn() }
                            )
                        }
                        .graphicsLayer {
                            scaleX = animatedScale.value
                            scaleY = animatedScale.value
                            translationX = animatedOffset.value.x
                            translationY = animatedOffset.value.y
                        }
                        .transformable(state = transformState),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}
