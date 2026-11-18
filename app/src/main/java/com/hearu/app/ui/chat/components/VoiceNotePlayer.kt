package com.hearu.app.ui.chat.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun VoiceNoteBubble(
    durationSeconds: Int,
    isMine: Boolean,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    playbackProgress: Float = 0f,
    waveformAmplitudes: List<Int> = emptyList(),
    onTogglePlay: () -> Unit = {},
    onSeek: (Float) -> Unit = {}
) {
    val waveScale = if (isPlaying) {
        val infiniteTransition = rememberInfiniteTransition(label = "WaveformAnimation")
        val animated by infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(400),
                repeatMode = RepeatMode.Reverse
            ),
            label = "WaveHeight"
        )
        animated
    } else {
        0f
    }

    val safeDuration = durationSeconds.coerceAtLeast(1)
    val clampedProgress = playbackProgress.coerceIn(0f, 1f)
    val displaySeconds = if (isPlaying || clampedProgress > 0f) {
        (safeDuration * clampedProgress).toInt().coerceIn(0, safeDuration)
    } else {
        durationSeconds.coerceAtLeast(0)
    }

    val bars = if (waveformAmplitudes.isNotEmpty()) {
        waveformAmplitudes.take(16).map { amp ->
            (6 + (amp.coerceIn(0, 100) * 18 / 100))
        }
    } else {
        listOf(8, 16, 22, 12, 18, 24, 14, 20, 10, 16, 22, 14, 8)
    }

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
                onClick = onTogglePlay,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (isMine) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    )
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Waveform Bars
            val progressPercent = (clampedProgress * 100).toInt()
            val formattedTime = String.format(Locale.US, "%d:%02d", displaySeconds / 60, displaySeconds % 60)
            Row(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .pointerInput(bars.size) {
                        detectTapGestures { offset ->
                            if (size.width > 0) {
                                val targetFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                onSeek(targetFraction)
                            }
                        }
                    }
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Voice note playback progress"
                        stateDescription = "$progressPercent percent, $formattedTime"
                        setProgress { target ->
                            onSeek(target.coerceIn(0f, 1f))
                            true
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                bars.forEachIndexed { index, baseHeight ->
                    val dynamicHeight = if (isPlaying) {
                        (baseHeight * (0.5f + (waveScale * (if (index % 2 == 0) 0.5f else 0.8f)))).dp
                    } else {
                        baseHeight.dp
                    }
                    val isFilled = index < (bars.size * clampedProgress)
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(dynamicHeight.coerceIn(6.dp, 24.dp))
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (isMine) {
                                    if (isFilled) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.62f)
                                } else {
                                    if (isFilled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                                }
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = formattedTime,
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
    liveAmplitudes: List<Int> = emptyList(),
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
                text = String.format(Locale.US, "Recording: %d:%02d", recordingDurationSec / 60, recordingDurationSec % 60),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            if (liveAmplitudes.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    liveAmplitudes.takeLast(10).forEach { amp ->
                        val h = (4 + (amp.coerceIn(0, 100) * 14 / 100)).dp
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(h)
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onCancelRecording) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(
                onClick = onStopRecording,
                colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    Icons.Default.Stop,
                    contentDescription = "Send Voice Note",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}
