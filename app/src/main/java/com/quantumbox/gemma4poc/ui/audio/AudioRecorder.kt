package com.quantumbox.gemma4poc.ui.audio

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private const val TAG = "AudioRecorder"
private const val SAMPLE_RATE = 16000
private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
private const val MAX_DURATION_SEC = 30

@Composable
fun AudioRecorderDialog(
    onAudioRecorded: (ByteArray) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    var amplitude by remember { mutableFloatStateOf(0f) }
    var audioData by remember { mutableStateOf<ByteArray?>(null) }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        if (!granted) onDismiss()
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) hasPermission = true
        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // Recording logic
    LaunchedEffect(isRecording) {
        if (!isRecording) return@LaunchedEffect

        withContext(Dispatchers.IO) {
            val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize,
            )

            val outputStream = ByteArrayOutputStream()
            val buffer = ShortArray(bufferSize / 2)
            val startTime = System.currentTimeMillis()

            try {
                audioRecord.startRecording()

                while (isActive && isRecording) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        // Convert short array to byte array
                        val byteBuffer = ByteArray(read * 2)
                        for (i in 0 until read) {
                            byteBuffer[i * 2] = (buffer[i].toInt() and 0xFF).toByte()
                            byteBuffer[i * 2 + 1] = (buffer[i].toInt() shr 8 and 0xFF).toByte()
                        }
                        outputStream.write(byteBuffer)

                        // Calculate amplitude
                        var sum = 0L
                        for (i in 0 until read) {
                            sum += buffer[i] * buffer[i]
                        }
                        val rms = Math.sqrt(sum.toDouble() / read).toFloat()
                        amplitude = (rms / Short.MAX_VALUE).coerceIn(0f, 1f)
                    }

                    val elapsed = (System.currentTimeMillis() - startTime) / 1000
                    elapsedSeconds = elapsed

                    if (elapsed >= MAX_DURATION_SEC) {
                        isRecording = false
                    }
                }
            } finally {
                audioRecord.stop()
                audioRecord.release()
                audioData = outputStream.toByteArray()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { isRecording = false }
    }

    if (hasPermission) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("音声録音") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Amplitude visualization
                    val animatedScale by animateFloatAsState(
                        targetValue = if (isRecording) 1f + amplitude * 0.5f else 1f,
                        label = "amplitude",
                    )

                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .scale(animatedScale)
                            .clip(CircleShape)
                            .background(
                                if (isRecording) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primaryContainer
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = if (isRecording) MaterialTheme.colorScheme.onError
                            else MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "%d:%02d / %d:%02d".format(
                            elapsedSeconds / 60, elapsedSeconds % 60,
                            MAX_DURATION_SEC / 60, MAX_DURATION_SEC % 60,
                        ),
                        style = MaterialTheme.typography.headlineSmall,
                    )

                    if (isRecording) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("録音中", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {
                if (isRecording) {
                    Button(onClick = { isRecording = false }) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("停止")
                    }
                } else if (audioData != null) {
                    Button(onClick = { audioData?.let { onAudioRecorded(it) } }) {
                        Text("送信")
                    }
                } else {
                    Button(onClick = { isRecording = true }) {
                        Icon(Icons.Default.Mic, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("録音開始")
                    }
                }
            },
            dismissButton = {
                OutlinedButton(onClick = onDismiss) {
                    Text("キャンセル")
                }
            },
        )
    }
}
