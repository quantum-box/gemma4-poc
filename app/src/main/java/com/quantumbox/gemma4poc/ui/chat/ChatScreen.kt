package com.quantumbox.gemma4poc.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quantumbox.gemma4poc.ui.audio.AudioRecorderDialog
import com.quantumbox.gemma4poc.ui.camera.CameraCaptureDialog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val listState = rememberLazyListState()
    var showCamera by remember { mutableStateOf(false) }
    var showAudioRecorder by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Auto-scroll to bottom
    LaunchedEffect(uiState.messages.size, uiState.messages.lastOrNull()?.text) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SessionDrawerContent(
                sessions = sessions,
                currentSessionId = uiState.currentSessionId,
                onNewSession = {
                    viewModel.newSession()
                    scope.launch { drawerState.close() }
                },
                onSelectSession = { sessionId ->
                    viewModel.switchSession(sessionId)
                    scope.launch { drawerState.close() }
                },
                onDeleteSession = { sessionId ->
                    viewModel.deleteSession(sessionId)
                },
            )
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Gemma 4 Chat") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Sessions")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.resetConversation() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset")
                        }
                    },
                )
            },
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                // Token usage bar
                if (uiState.tokenStats.totalTokensUsed > 0) {
                    TokenUsageBar(uiState.tokenStats)
                }

                // Messages
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    state = listState,
                ) {
                    items(uiState.messages, key = { it.id }) { message ->
                        MessageBubble(message)
                    }
                }

                HorizontalDivider()

                // Input bar
                InputBar(
                    pendingImages = uiState.pendingImages,
                    isGenerating = uiState.isGenerating,
                    onSend = { viewModel.sendMessage(it) },
                    onAddImage = { viewModel.addPendingImage(it) },
                    onRemoveImage = { viewModel.removePendingImage(it) },
                    onStopGeneration = { viewModel.stopGeneration() },
                    onCameraRequest = { showCamera = true },
                    onAudioRecord = { showAudioRecorder = true },
                )
            }
        }
    }

    // Camera dialog
    if (showCamera) {
        CameraCaptureDialog(
            onImageCaptured = { bitmap ->
                viewModel.addPendingImage(bitmap)
                showCamera = false
            },
            onDismiss = { showCamera = false },
        )
    }

    // Audio recorder dialog
    if (showAudioRecorder) {
        AudioRecorderDialog(
            onAudioRecorded = { audioData ->
                viewModel.addPendingAudioClip(audioData)
                showAudioRecorder = false
            },
            onDismiss = { showAudioRecorder = false },
        )
    }
}

@Composable
private fun SessionDrawerContent(
    sessions: List<com.quantumbox.gemma4poc.data.db.SessionEntity>,
    currentSessionId: String?,
    onNewSession: () -> Unit,
    onSelectSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }

    ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
        Column(modifier = Modifier.fillMaxHeight()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Sessions",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onNewSession) {
                    Icon(Icons.Default.Add, contentDescription = "New session")
                }
            }

            HorizontalDivider()

            // Session list
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(sessions, key = { it.id }) { session ->
                    val isSelected = session.id == currentSessionId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectSession(session.id) }
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                else MaterialTheme.colorScheme.surface
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = session.title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = dateFormat.format(Date(session.updatedAt)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onDeleteSession(session.id) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TokenUsageBar(stats: com.quantumbox.gemma4poc.engine.TokenStats) {
    val animatedProgress by animateFloatAsState(
        targetValue = stats.usageRatio.coerceIn(0f, 1f),
        label = "tokenProgress",
    )
    val color = when {
        stats.usageRatio > 0.9f -> MaterialTheme.colorScheme.error
        stats.usageRatio > 0.7f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp),
                color = color,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "${stats.totalTokensUsed} / ${stats.maxTokens}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (stats.decodeSpeed > 0) {
            Text(
                text = "%.1f tok/s".format(stats.decodeSpeed),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}
