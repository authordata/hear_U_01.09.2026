package com.hearu.app.ui.tools

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hearu.app.ui.theme.HearUTheme
import kotlinx.coroutines.delay

enum class CalmingToolTab {
    BREATHING_478,
    GROUNDING_54321
}

enum class BreathingPhase(val label: String, val seconds: Int, val targetScale: Float) {
    INHALE("Breathe In Deeply...", 4, 1.35f),
    HOLD("Hold Gently...", 7, 1.35f),
    EXHALE("Exhale Completely...", 8, 0.85f),
    REST("Rest & Settle...", 2, 1.0f)
}

data class GroundingStep(
    val count: Int,
    val senseName: String,
    val icon: ImageVector,
    val prompt: String,
    val examples: List<String>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreathingExerciseScreen(
    onNavigateBack: () -> Unit,
    onEmergencyClick: () -> Unit = {}
) {
    var selectedTab by rememberSaveable { mutableStateOf(CalmingToolTab.BREATHING_478) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calming & Grounding Tools", fontWeight = FontWeight.Bold) },
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
        ) {
            // Tool Selection Tabs
            TabRow(
                selectedTabIndex = if (selectedTab == CalmingToolTab.BREATHING_478) 0 else 1,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == CalmingToolTab.BREATHING_478,
                    onClick = { selectedTab = CalmingToolTab.BREATHING_478 },
                    text = { Text("4-7-8 Breathing", fontWeight = if (selectedTab == CalmingToolTab.BREATHING_478) FontWeight.Bold else FontWeight.Normal) },
                    icon = { Icon(Icons.Default.Air, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == CalmingToolTab.GROUNDING_54321,
                    onClick = { selectedTab = CalmingToolTab.GROUNDING_54321 },
                    text = { Text("5-4-3-2-1 Grounding", fontWeight = if (selectedTab == CalmingToolTab.GROUNDING_54321) FontWeight.Bold else FontWeight.Normal) },
                    icon = { Icon(Icons.Default.Spa, contentDescription = null) }
                )
            }

            if (selectedTab == CalmingToolTab.BREATHING_478) {
                BreathingPacerSection()
            } else {
                SensoryGroundingSection()
            }
        }
    }
}

@Composable
private fun BreathingPacerSection() {
    var isRunning by remember { mutableStateOf(false) }
    var currentPhase by remember { mutableStateOf(BreathingPhase.INHALE) }
    var secondsRemaining by remember { mutableStateOf(currentPhase.seconds) }
    var completedCycles by remember { mutableStateOf(0) }

    val animatedScale by animateFloatAsState(
        targetValue = if (isRunning) currentPhase.targetScale else 1.0f,
        animationSpec = tween(
            durationMillis = if (isRunning) currentPhase.seconds * 1000 else 600,
            easing = FastOutSlowInEasing
        ),
        label = "BreathingScale"
    )

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
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

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(280.dp)
                .padding(16.dp)
        ) {
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

@Composable
private fun SensoryGroundingSection() {
    val steps = listOf(
        GroundingStep(
            count = 5,
            senseName = "Things You Can SEE",
            icon = Icons.Default.Visibility,
            prompt = "Look around you and notice 5 distinct visual details.",
            examples = listOf("Patterns on a wall or fabric", "Light or shadow reflections", "A clock or object on your desk", "Color of your shoes", "Small details you normally miss")
        ),
        GroundingStep(
            count = 4,
            senseName = "Things You Can TOUCH",
            icon = Icons.Default.TouchApp,
            prompt = "Focus on physical sensations and touch 4 distinct textures.",
            examples = listOf("The texture of your shirt or jeans", "The coolness of your phone surface", "The firmness of the chair beneath you", "Your feet firmly flat on the floor")
        ),
        GroundingStep(
            count = 3,
            senseName = "Things You Can HEAR",
            icon = Icons.Default.Hearing,
            prompt = "Close your eyes slightly and listen for 3 sounds around you.",
            examples = listOf("Distant traffic or street noise", "The ambient hum of a fan or computer", "The sound of your own quiet breath")
        ),
        GroundingStep(
            count = 2,
            senseName = "Things You Can SMELL",
            icon = Icons.Default.FilterVintage,
            prompt = "Breathe in gently and try to detect 2 scents.",
            examples = listOf("The fresh air in the room", "Scent of your clothes, soap, or coffee")
        ),
        GroundingStep(
            count = 1,
            senseName = "Thing You Can TASTE",
            icon = Icons.Default.WaterDrop,
            prompt = "Notice the current taste in your mouth, or take a refreshing sip of water.",
            examples = listOf("A sip of cool water", "Lingering taste of mint or tea", "Focus on a clean, resting palate")
        )
    )

    var currentStepIndex by rememberSaveable { mutableStateOf(0) }
    var isCompleted by rememberSaveable { mutableStateOf(false) }

    val currentStep = steps[currentStepIndex]
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "5-4-3-2-1 Sensory Grounding",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Anchor your mind to the present moment during panic or acute anxiety.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Progress Indicators (5 to 1)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            steps.forEachIndexed { index, step ->
                val isCurrent = index == currentStepIndex
                val isDone = index < currentStepIndex || isCompleted
                Surface(
                    shape = CircleShape,
                    color = when {
                        isCurrent -> MaterialTheme.colorScheme.primary
                        isDone -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "${step.count}",
                            color = when {
                                isCurrent -> MaterialTheme.colorScheme.onPrimary
                                isDone -> MaterialTheme.colorScheme.onSecondary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (isCompleted) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Complete",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "You Are Here. You Are Safe.",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Take a moment to notice how your breathing has settled. You have brought your body and mind back to the present.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = {
                            currentStepIndex = 0
                            isCompleted = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Start Over")
                    }
                }
            }
        } else {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = currentStep.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "${currentStep.count} ${currentStep.senseName}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = currentStep.prompt,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "Suggestions to notice:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        currentStep.examples.forEach { example ->
                            Row(
                                modifier = Modifier.padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.FiberManualRecord,
                                    contentDescription = null,
                                    modifier = Modifier.size(8.dp),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = example,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            if (currentStepIndex < steps.size - 1) {
                                currentStepIndex++
                            } else {
                                isCompleted = true
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = if (currentStepIndex < steps.size - 1) "I've Found All ${currentStep.count} → Next Sense" else "Complete Grounding Session",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BreathingExerciseScreenPreview() {
    HearUTheme {
        BreathingExerciseScreen(onNavigateBack = {})
    }
}

