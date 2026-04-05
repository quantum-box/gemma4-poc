package com.quantumbox.gemma4poc.ui.download

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quantumbox.gemma4poc.data.DownloadState

@Composable
fun DownloadScreen(
    onModelReady: () -> Unit,
    viewModel: DownloadViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isModelReady) {
        if (uiState.isModelReady) onModelReady()
    }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Default.SmartToy,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Gemma 4 On-Device",
                style = MaterialTheme.typography.headlineMedium,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "LiteRT LM でオンデバイス推論",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(48.dp))

            when {
                uiState.isInitializing -> {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("モデルを初期化中...")
                }

                uiState.initError != null -> {
                    Text(
                        text = "初期化エラー: ${uiState.initError}",
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }

                uiState.progress.state == DownloadState.DOWNLOADING -> {
                    DownloadProgressSection(uiState.progress)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(onClick = { viewModel.cancelDownload() }) {
                        Text("キャンセル")
                    }
                }

                uiState.progress.state == DownloadState.FAILED -> {
                    Text(
                        text = "ダウンロード失敗",
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.startDownload() }) {
                        Text("再試行")
                    }
                }

                else -> {
                    Text(
                        text = "Gemma 4 E2B モデル (約2.6GB) をダウンロードします",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { viewModel.startDownload() }) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("ダウンロード開始")
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadProgressSection(progress: com.quantumbox.gemma4poc.data.DownloadProgress) {
    val fraction = if (progress.totalBytes > 0) {
        progress.receivedBytes.toFloat() / progress.totalBytes.toFloat()
    } else 0f

    val animatedProgress by animateFloatAsState(targetValue = fraction, label = "progress")

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "%.1f / %.1f MB".format(
                progress.receivedBytes / 1_000_000.0,
                progress.totalBytes / 1_000_000.0,
            ),
            style = MaterialTheme.typography.bodyMedium,
        )

        if (progress.downloadRateBytes > 0) {
            val rateMbps = progress.downloadRateBytes / 1_000_000.0
            val remainingSec = progress.remainingMs / 1000
            Text(
                text = "%.1f MB/s · 残り %d:%02d".format(
                    rateMbps,
                    remainingSec / 60,
                    remainingSec % 60,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
