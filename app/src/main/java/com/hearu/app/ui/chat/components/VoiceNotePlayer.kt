package com.hearu.app.ui.chat.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun VoiceNoteBubble(
    durationSeconds: Int,
    isMine: Boolean,
    modifier: Modifier = Modifier
) {
    var isPlaying by remember { mutableStateOf(false) }
    var currentSeconds by remember { mutableStateOf(0) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            currentSeconds = 0
            while (currentSeconds < durationSeconds && isPlaying) {
                delay(1000L)
                currentSeconds++
            }
            isPlaying = false
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "WaveformAnimation")
    val waveScale by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "WaveHeight"
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { isPlaying = !isPlaying },
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isMine) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Waveform Bars
            Row(
                modifier = Modifier.height(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                val heights = listOf(8, 16, 22, 12, 18, 24, 14, 20, 10, 16, 22, 14, 8)
                heights.forEachIndexed { index, baseHeight ->
                    val dynamicHeight = if (isPlaying) {
                        (baseHeight * (0.5f + (waveScale * (if (index % 2 == 0) 0.5f else 0.8f)))).dp
                    } else {
                        baseHeight.dp
                    }
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(dynamicHeight)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (isMine) {
                                    if (index < (heights.size * (currentSeconds.toFloat() / durationSeconds.coerceAtLeast(1)))) {
                                        Color.White
                                    } else {
                                        Color.White.copy(alpha = 0.4f)
                                    }
                                } else {
                                    if (index < (heights.size * (currentSeconds.toFloat() / durationSeconds.coerceAtLeast(1)))) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    }
                                }
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = String.format("%d:%02d", (if (isPlaying) currentSeconds else durationSeconds) / 60, (if (isPlaying) currentSeconds else durationSeconds) % 60),
                style = MaterialTheme.typography.labelSmall,
                color = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun VoiceRecordBar(
    isRecording: Boolean,
    recordingDurationSec: Int,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isRecording) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = String.format("Recording: %d:%02d", recordingDurationSec / 60, recordingDurationSec % 60),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onCancelRecording) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(
                onClick = onStopRecording,
                colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Stop, contentDescription = "Send Voice Note", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}
