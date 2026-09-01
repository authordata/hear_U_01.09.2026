package com.hearu.app.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hearu.app.model.Message
import com.hearu.app.ui.dialogs.CrisisSupportDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIChatScreen(
    onNavigateBack: () -> Unit,
    viewModel: AIChatViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val showCrisisDialog by viewModel.crisisEvent.collectAsStateWithLifecycle()
    val isQuotaExhausted by viewModel.quotaExhausted.collectAsStateWithLifecycle()
    var inputText by remember { mutableStateOf("") }

    if (showCrisisDialog) {
        CrisisSupportDialog(onDismiss = { viewModel.dismissCrisisDialog() })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("AI Companion", style = MaterialTheme.typography.titleMedium)
                        Text("Powered by Gemini 3.7 Flash", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        bottomBar = {
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
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            reverseLayout = true
        ) {
            items(messages.reversed()) { msg ->
                MessageBubble(message = msg, isMine = msg.senderId == "user123")
            }
        }
    }
}

@Composable
private fun MessageBubble(message: Message, isMine: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
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
    Column(modifier = Modifier.padding(8.dp).fillMaxWidth()) {
        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
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
                Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White)
            }
        }
    }
}
