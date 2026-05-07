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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.startProcessing()
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
                            onShare = { cleanUris ->
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
                                val chooser = Intent.createChooser(intent, "Share clean copy")
                                context.startActivity(chooser)
                            },
                            onDone = onFinish
                        )
                    }
                    is ShareUiState.Error -> {
                        ErrorContent(
                            message = state.message,
                            onRetry = { viewModel.startProcessing() },
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
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = if (results.size == 1) "Clean copy saved" else "$successCount clean copies saved",
                    style = MaterialTheme.typography.titleMedium
                )
                if (failedCount > 0) {
                    Text(
                        text = "$failedCount failed",
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
            contentDescription = null,
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
