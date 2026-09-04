package com.hearu.app.ui.tools

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

enum class BreathingPhase(val label: String, val seconds: Int, val targetScale: Float) {
    INHALE("Breathe In Deeply...", 4, 1.35f),
    HOLD("Hold Gently...", 7, 1.35f),
    EXHALE("Exhale Completely...", 8, 0.85f),
    REST("Rest & Settle...", 2, 1.0f)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreathingExerciseScreen(
    onNavigateBack: () -> Unit,
    onEmergencyClick: () -> Unit = {}
) {
    var isRunning by remember { mutableStateOf(false) }
    var currentPhase by remember { mutableStateOf(BreathingPhase.INHALE) }
    var secondsRemaining by remember { mutableStateOf(currentPhase.seconds) }
    var completedCycles by remember { mutableStateOf(0) }

    // Controlled smooth pulsing scale
    val animatedScale by animateFloatAsState(
        targetValue = if (isRunning) currentPhase.targetScale else 1.0f,
        animationSpec = tween(
            durationMillis = if (isRunning) currentPhase.seconds * 1000 else 600,
            easing = FastOutSlowInEasing
        ),
        label = "BreathingScale"
    )

    // Exercise timer loop
    LaunchedEffect(isRunning, currentPhase) {
        if (!isRunning) return@LaunchedEffect
        secondsRemaining = currentPhase.seconds
        while (secondsRemaining > 0 && isRunning) {
            delay(1000L)
            secondsRemaining--
        }
        if (isRunning && secondsRemaining == 0) {
            when (currentPhase) {
                BreathingPhase.INHALE -> currentPhase = BreathingPhase.HOLD
                BreathingPhase.HOLD -> currentPhase = BreathingPhase.EXHALE
                BreathingPhase.EXHALE -> currentPhase = BreathingPhase.REST
                BreathingPhase.REST -> {
                    completedCycles++
                    currentPhase = BreathingPhase.INHALE
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Guided 4-7-8 Breathing", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onEmergencyClick) {
                        Icon(Icons.Default.Emergency, contentDescription = "Crisis Hub", tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text(
                    text = "Regulate your nervous system and reduce acute anxiety in minutes.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "Cycles Completed: $completedCycles",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }

            // Visual Breathing Pulsing Orbs
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(280.dp)
                    .padding(16.dp)
            ) {
                // Outer subtle glow
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(animatedScale)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        )
                )

                // Mid breathing aura
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .scale(animatedScale * 0.9f)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                )
                            ),
                            shape = CircleShape
                        )
                )

                // Central Focus Orb
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(150.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            ),
                            shape = CircleShape
                        )
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isRunning) "$secondsRemaining" else "Ready",
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            text = if (isRunning) "seconds" else "Tap Start",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Phase Guide Label
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isRunning) currentPhase.label else "Find a comfortable posture and press start.",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when (currentPhase) {
                        BreathingPhase.INHALE -> "Inhale quietly through your nose (4s)"
                        BreathingPhase.HOLD -> "Hold your breath calmly without tension (7s)"
                        BreathingPhase.EXHALE -> "Exhale completely through your mouth with a whoosh (8s)"
                        BreathingPhase.REST -> "Rest and reset (2s)"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            // Control Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = {
                        isRunning = false
                        currentPhase = BreathingPhase.INHALE
                        secondsRemaining = 4
                        completedCycles = 0
                    },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset Exercise")
                }

                Spacer(modifier = Modifier.width(20.dp))

                Button(
                    onClick = { isRunning = !isRunning },
                    shape = RoundedCornerShape(28.dp),
                    contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isRunning) "Pause" else "Start"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isRunning) "Pause" else "Begin Session",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
