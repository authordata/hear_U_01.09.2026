package com.hearu.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hearu.app.model.Message
import com.hearu.app.ui.dialogs.CrisisSupportDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIChatScreen(
    onNavigateBack: () -> Unit,
    onNavigateToBreathing: () -> Unit = {},
    onNavigateToCrisis: () -> Unit = {},
    viewModel: AIChatViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val showCrisisDialog by viewModel.crisisEvent.collectAsStateWithLifecycle()
    val isQuotaExhausted by viewModel.quotaExhausted.collectAsStateWithLifecycle()
    val quotaUsed by viewModel.quotaUsed.collectAsStateWithLifecycle()
    var inputText by rememberSaveable { mutableStateOf("") }
    var showLocalCrisisDialog by rememberSaveable { mutableStateOf(false) }

    if (showCrisisDialog || showLocalCrisisDialog) {
        CrisisSupportDialog(
            onDismiss = {
                viewModel.dismissCrisisDialog()
                showLocalCrisisDialog = false
            }
        )
    }

    val suggestionPrompts = listOf(
        "🌬️ Can we do a breathing exercise?",
        "😰 I'm having acute anxiety right now",
        "💭 Just need to vent about my day",
        "🌱 Give me an encouraging thought",
        "🌙 Having trouble falling asleep"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("HearU AI Companion", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Empathetic, non-judgmental listening", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToBreathing) {
                        Icon(Icons.Default.Spa, contentDescription = "Calm Breath", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { showLocalCrisisDialog = true }) {
                        Icon(Icons.Default.Emergency, contentDescription = "Crisis Support", tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
                // Quota Warning if high
                if (quotaUsed >= 40) {
                    Surface(
                        color = if (isQuotaExhausted) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isQuotaExhausted) "Daily AI limit reached (50/50). Resets tomorrow." else "Daily quota: $quotaUsed/50 messages used",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            color = if (isQuotaExhausted) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }

                // Suggestion chips
                if (messages.size <= 2) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(suggestionPrompts) { prompt ->
                            SuggestionChip(
                                onClick = {
                                    if (prompt.contains("breathing")) {
                                        onNavigateToBreathing()
                                    } else {
                                        inputText = prompt.substring(prompt.indexOf(' ') + 1)
                                    }
                                },
                                label = { Text(prompt, style = MaterialTheme.typography.bodySmall) }
                            )
                        }
                    }
                }

                // Chat Input Bar
                ChatInputBar(
                    inputText = inputText,
                    onTextChanged = { inputText = it },
                    onSend = {
                        if (inputText.isNotBlank() && !isLoading && !isQuotaExhausted) {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                        }
                    },
                    isLoading = isLoading,
                    isDisabled = isQuotaExhausted
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            reverseLayout = false
        ) {
            item {
                DisclaimerCard(onCrisisClick = { showLocalCrisisDialog = true })
                Spacer(modifier = Modifier.height(12.dp))
            }

            items(messages, key = { it.id }) { message ->
                MessageBubble(message = message)
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (isLoading) {
                item {
                    TypingIndicatorBubble()
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun DisclaimerCard(onCrisisClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "HearU AI Companion is an emotional sounding board, not a therapist.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "If you are in immediate distress, please connect with human crisis professionals.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = onCrisisClick) {
                Text("Get Help", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MessageBubble(message: Message) {
    val isUser = message.senderId != "gemini_ai"
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val containerColor = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
    val contentColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            color = containerColor,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            tonalElevation = 1.dp,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
                val timeString = remember(message.timestamp) { timeFormat.format(Date(message.timestamp)) }
                Text(
                    text = timeString,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
private fun TypingIndicatorBubble() {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp),
        modifier = Modifier.widthIn(max = 120.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Thinking...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
private fun ChatInputBar(
    inputText: String,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    isLoading: Boolean,
    isDisabled: Boolean
) {
    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onTextChanged,
                placeholder = {
                    Text(if (isDisabled) "Daily quota reached" else "Share how you feel...")
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                enabled = !isDisabled
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onSend,
                enabled = !isLoading && !isDisabled && inputText.isNotBlank(),
                colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}
