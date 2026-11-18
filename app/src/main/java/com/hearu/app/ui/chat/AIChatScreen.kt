package com.hearu.app.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hearu.app.model.Message
import com.hearu.app.ui.dialogs.CrisisSupportDialog
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val AI_CHAT_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

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

    val keyedMessages = remember(messages) {
        val seen = HashSet<String>(messages.size)
        messages.mapIndexed { idx, msg ->
            val base = msg.id.ifBlank { "blank_${idx}_${msg.senderId}_${msg.timestamp}" }
            var candidate = base
            var suffix = idx
            while (!seen.add(candidate)) {
                candidate = "${base}_${suffix++}"
            }
            msg to candidate
        }
    }

    val listState = rememberLazyListState()
    val expectedItemCount = 1 + keyedMessages.size + if (isLoading) 1 else 0
    LaunchedEffect(keyedMessages.size, isLoading) {
        if (keyedMessages.isNotEmpty()) {
            val settledCount = snapshotFlow { listState.layoutInfo.totalItemsCount }
                .first { it >= expectedItemCount }
            val targetIndex = (expectedItemCount - 1).coerceIn(0, settledCount - 1)
            listState.animateScrollToItem(targetIndex)
        }
    }

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
                    IconButton(onClick = onNavigateToCrisis) {
                        Icon(Icons.Default.Emergency, contentDescription = "Crisis Support", tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding()
            ) {
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
            state = listState,
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

            items(keyedMessages, key = { it.second }) { (message, _) ->
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
            TextButton(
                onClick = onCrisisClick,
                colors = ButtonDefaults.textButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Text("Get Help", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MessageBubble(message: Message) {
    val isUser = message.senderId != Message.SENDER_AI
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
                val timeString = remember(message.timestamp) {
                    Instant.ofEpochMilli(message.timestamp)
                        .atZone(ZoneId.systemDefault())
                        .format(AI_CHAT_TIME_FORMATTER)
                }
                Text(
                    text = timeString,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.90f),
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
        modifier = Modifier.widthIn(max = 140.dp)
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
