package com.pureframe.exif.ui.screens.share

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp


@Composable
fun ShareProcessingScreen(
    viewModel: ShareViewModel,
    onFinish: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var shareError by rememberSaveable { mutableStateOf<String?>(null) }

    // Only auto-start when the ViewModel is truly idle. After a config
    // change the state may already be Success or Error; re-firing would
    // restart processing without user action.
    LaunchedEffect(Unit) {
        if (viewModel.uiState.value is ShareUiState.Idle) {
            viewModel.startProcessing()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            AnimatedContent(
                targetState = uiState,
                contentKey = { it::class.simpleName },
                label = "share_state"
            ) { state ->
                when (state) {
                    is ShareUiState.Idle -> {
                        IdleContent()
                    }
                    is ShareUiState.Processing -> {
                        ProcessingContent(state.current, state.total)
                    }
                    is ShareUiState.Success -> {
                        SuccessContent(
                            results = state.results,
                            shareError = shareError,
                            onShare = { cleanUris ->
                                shareError = null
                                val intent = if (cleanUris.size == 1) {
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "image/*"
                                        putExtra(Intent.EXTRA_STREAM, cleanUris.first())
                                    }
                                } else {
                                    Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                        type = "image/*"
                                        putParcelableArrayListExtra(
                                            Intent.EXTRA_STREAM,
                                            ArrayList(cleanUris)
                                        )
                                    }
                                }
                                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                val chooser = Intent.createChooser(intent, "Share clean copy").apply {
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                try {
                                    context.startActivity(chooser)
                                } catch (_: android.content.ActivityNotFoundException) {
                                    shareError = "No app available to share"
                                } catch (e: Exception) {
                                    shareError = "Unable to share right now"
                                }
                            },
                            onDone = {
                                shareError = null
                                onFinish()
                            }
                        )
                    }
                    is ShareUiState.Error -> {
                        ErrorContent(
                            message = state.message,
                            onRetry = {
                                shareError = null
                                viewModel.startProcessing()
                            },
                            onCancel = onFinish
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IdleContent() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ProcessingContent(current: Int, total: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            strokeWidth = 4.dp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Stripping metadata...",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Image $current of $total",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SuccessContent(
    results: List<ShareResult>,
    shareError: String?,
    onShare: (List<Uri>) -> Unit,
    onDone: () -> Unit
) {
    val successCount = results.count { it.success }
    val failedCount = results.size - successCount
    val cleanUris = results.mapNotNull { it.cleanUri }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Export succeeded",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                val firstError = results.firstOrNull { !it.success }?.error
                val title = when {
                    successCount == 1 && failedCount == 0 -> "Clean copy saved"
                    successCount == 0 && failedCount == 1 -> "Export failed"
                    successCount > 0 && failedCount == 0 -> "$successCount clean copies saved"
                    else -> "$successCount saved, $failedCount failed"
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                if (failedCount > 0) {
                    Text(
                        text = firstError ?: "Export failed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        if (results.size > 1) {
            Spacer(modifier = Modifier.height(12.dp))
            LazyColumn(
                modifier = Modifier.height(120.dp)
            ) {
                items(results) { result ->
                    ResultRow(result)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (cleanUris.isNotEmpty()) {
            Button(
                onClick = { onShare(cleanUris) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Share clean copies")
            }
            if (shareError != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = shareError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        OutlinedButton(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Done")
        }
    }
}

@Composable
private fun ResultRow(result: ShareResult) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (result.success) Icons.Default.CheckCircle else Icons.Default.Error,
            contentDescription = if (result.success) "Export succeeded" else "Export failed",
            tint = if (result.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = result.originalName,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Processing failed",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Retry")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel")
        }
    }
}
