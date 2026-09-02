package com.hearu.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Send
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
                        Text("AI Companion", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Gemini 3.7 Flash • Empathetic & Safe", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToBreathing) {
                        Icon(Icons.Default.Spa, contentDescription = "4-7-8 Breathing Guide", tint = MaterialTheme.colorScheme.secondary)
                    }
                    IconButton(onClick = { showLocalCrisisDialog = true }) {
                        Icon(Icons.Default.Emergency, contentDescription = "SOS Crisis Hotline", tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        bottomBar = {
            Column(modifier = Modifier.padding(8.dp).fillMaxWidth()) {
                // Quota bar indicator
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Daily Quota: $quotaUsed/${viewModel.MESSAGE_LIMIT} used",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isQuotaExhausted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Resets at midnight",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                // Suggestion chips
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(suggestionPrompts) { prompt ->
                        SuggestionChip(
                            onClick = {
                                if (prompt.contains("breathing exercise", ignoreCase = true)) {
                                    onNavigateToBreathing()
                                } else {
                                    viewModel.sendMessage(prompt)
                                }
                            },
                            label = { Text(prompt, style = MaterialTheme.typography.bodySmall) },
                            enabled = !isLoading && !isQuotaExhausted
                        )
                    }
                }

                // Input bar
                ChatInputBar(
                    inputText = inputText,
                    onInputChanged = { inputText = it },
                    onSend = {
                        val toSend = inputText
                        inputText = ""
                        viewModel.sendMessage(toSend)
                    },
                    isLoading = isLoading,
                    isDisabled = isQuotaExhausted
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            reverseLayout = true
        ) {
            if (isLoading) {
                item {
                    TypingBubble()
                }
            }
            items(messages.reversed(), key = { it.id }) { msg ->
                val isMine = msg.senderId != "ai_companion"
                MessageBubble(message = msg, isMine = isMine)
            }
        }
    }
}

@Composable
private fun TypingBubble() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AI Companion is listening & reflecting...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(message: Message, isMine: Boolean) {
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val formattedTime = remember(message.timestamp) { timeFormat.format(Date(message.timestamp)) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Column(horizontalAlignment = if (isMine) Alignment.End else Alignment.Start) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 2.dp
            ) {
                Text(
                    text = message.text,
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
            )
        }
    }
}

@Composable
private fun ChatInputBar(
    inputText: String,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
    isLoading: Boolean,
    isDisabled: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        AnimatedVisibility(visible = isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChanged,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(if (isDisabled) "Daily 50-message quota reached" else "Share what's on your mind...")
                },
                enabled = !isDisabled,
                maxLines = 4
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onSend,
                enabled = !isLoading && !isDisabled && inputText.isNotBlank(),
                colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}
