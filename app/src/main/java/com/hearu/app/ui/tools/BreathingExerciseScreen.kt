package com.hearu.app.ui.tools

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    onNavigateBack: () -> Unit
) {
    var isRunning by remember { mutableStateOf(false) }
    var currentPhase by remember { mutableStateOf(BreathingPhase.INHALE) }
    var secondsRemaining by remember { mutableStateOf(4) }
    var cyclesCompleted by remember { mutableStateOf(0) }
    var selectedTab by remember { mutableStateOf(0) } // 0: 4-7-8, 1: 5-4-3-2-1 Grounding

    // Countdown and cycle loop
    LaunchedEffect(isRunning, currentPhase) {
        if (!isRunning) return@LaunchedEffect
        secondsRemaining = currentPhase.seconds
        while (secondsRemaining > 0) {
            delay(1000L)
            secondsRemaining--
        }
        if (isRunning) {
            currentPhase = when (currentPhase) {
                BreathingPhase.INHALE -> BreathingPhase.HOLD
                BreathingPhase.HOLD -> BreathingPhase.EXHALE
                BreathingPhase.EXHALE -> {
                    cyclesCompleted++
                    BreathingPhase.REST
                }
                BreathingPhase.REST -> BreathingPhase.INHALE
            }
        }
    }

    val animatedScale by animateFloatAsState(
        targetValue = if (isRunning) currentPhase.targetScale else 1.0f,
        animationSpec = tween(
            durationMillis = if (isRunning) currentPhase.seconds * 1000 else 600,
            easing = FastOutSlowInEasing
        ),
        label = "OrbScale"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calm & Grounding Hub") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("4-7-8 Breathing") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("5-4-3-2-1 Grounding") }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (selectedTab == 0) {
                // 4-7-8 Breathing Pacer UI
                Text(
                    text = "4-7-8 Relaxing Breath Technique",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "A scientifically proven breath cycle that activates the parasympathetic nervous system to rapidly ease anxiety.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Pulsating Orb
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(240.dp)
                        .scale(animatedScale)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                                    Color.Transparent
                                )
                            )
                        )
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Air,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isRunning) currentPhase.label else "Ready",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        if (isRunning) {
                            Text(
                                text = "${secondsRemaining}s",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            isRunning = !isRunning
                            if (isRunning) {
                                currentPhase = BreathingPhase.INHALE
                                secondsRemaining = 4
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.height(50.dp)
                    ) {
                        Icon(
                            imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isRunning) "Pause Exercise" else "Start 4-7-8 Breath")
                    }

                    if (cyclesCompleted > 0) {
                        FilledTonalButton(
                            onClick = {
                                isRunning = false
                                cyclesCompleted = 0
                                currentPhase = BreathingPhase.INHALE
                            },
                            modifier = Modifier.height(50.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (cyclesCompleted > 0) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        modifier = Modifier.fillMaxWidth().padding(8.dp)
                    ) {
                        Text(
                            text = "✨ You have completed $cyclesCompleted relaxing cycle(s). Notice the calm in your chest.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // 5-4-3-2-1 Sensory Grounding Tool
                Text(
                    text = "5-4-3-2-1 Panic & Overwhelm Grounding",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Re-anchor your mind to the present moment by engaging your 5 senses.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                val groundingSteps = listOf(
                    "👁️ 5 things you can SEE" to "Look around: a color, shadow, object, or pattern.",
                    "✋ 4 things you can TOUCH" to "Feel your feet on the ground, clothing texture, or cool water.",
                    "👂 3 things you can HEAR" to "Listen for distant traffic, clock ticking, or your own breath.",
                    "👃 2 things you can SMELL" to "Notice fresh air, coffee, rain, or a familiar scent.",
                    "👅 1 thing you can TASTE" to "Savor a sip of water or express gratitude for yourself."
                )

                groundingSteps.forEach { (title, description) ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}
