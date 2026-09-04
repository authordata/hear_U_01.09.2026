package com.hearu.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hearu.app.model.Message
import com.hearu.app.ui.chat.components.VoiceNoteBubble
import com.hearu.app.ui.chat.components.VoiceRecordBar
import com.hearu.app.ui.dialogs.CrisisSupportDialog
import com.hearu.app.ui.dialogs.ReportUserDialog
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    sessionId: String,
    userId: String = "",
    peerId: String = userId,
    onNavigateBack: () -> Unit,
    onNavigateToCrisis: () -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isSending by viewModel.isSending.collectAsStateWithLifecycle()
    val errorState by viewModel.errorState.collectAsStateWithLifecycle()

    var inputText by rememberSaveable { mutableStateOf("") }
    var showMenu by rememberSaveable { mutableStateOf(false) }
    var showReportDialog by rememberSaveable { mutableStateOf(false) }
    var showCrisisDialog by rememberSaveable { mutableStateOf(false) }
    var showQuickSupportBanner by rememberSaveable { mutableStateOf(true) }

    // Audio Voice Note Recording State
    var isRecording by remember { mutableStateOf(false) }
    var recordingDuration by remember { mutableStateOf(0) }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingDuration = 0
            while (isRecording) {
                delay(1000L)
                recordingDuration++
            }
        }
    }

    LaunchedEffect(sessionId) {
        viewModel.joinSession(sessionId, userId)
    }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    if (showReportDialog) {
        ReportUserDialog(
            onDismiss = { showReportDialog = false },
            onSubmitReport = { reason, details ->
                showReportDialog = false
            }
        )
    }

    if (showCrisisDialog) {
        CrisisSupportDialog(
            onDismiss = { showCrisisDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4CAF50))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Empathetic Listener",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "End-to-end ephemeral peer chat",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        showCrisisDialog = true
                        onNavigateToCrisis()
                    }) {
                        Icon(
                            Icons.Default.Emergency,
                            contentDescription = "Crisis SOS",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Crisis Resources (988)") },
                            onClick = {
                                showMenu = false
                                showCrisisDialog = true
                                onNavigateToCrisis()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Emergency, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Report User / Safety") },
                            onClick = {
                                showMenu = false
                                showReportDialog = true
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Flag, contentDescription = null)
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(8.dp)
            ) {
                // Quick Empathy Suggestion Reactions
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val reactions = listOf("❤️", "🫂", "🙏", "🌸", "✨")
                    reactions.forEach { emoji ->
                        item {
                            AssistChip(
                                onClick = { viewModel.sendMessage(emoji) },
                                label = { Text(emoji) }
                            )
                        }
                    }
                }

                // Voice Recording Live Bar
                if (isRecording) {
                    VoiceRecordBar(
                        isRecording = isRecording,
                        recordingDurationSec = recordingDuration,
                        onStartRecording = {
                            isRecording = true
                            viewModel.startVoiceRecording(context, context.cacheDir)
                        },
                        onStopRecording = {
                            isRecording = false
                            viewModel.stopAndSendVoiceRecording()
                        },
                        onCancelRecording = {
                            isRecording = false
                            viewModel.cancelVoiceRecording()
                        }
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Share with compassion...") },
                            maxLines = 4
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        if (inputText.isBlank()) {
                            IconButton(
                                onClick = {
                                    isRecording = true
                                    viewModel.startVoiceRecording(context, context.cacheDir)
                                },
                                colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = "Record Voice Note", tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    if (inputText.isNotBlank()) {
                                        viewModel.sendMessage(inputText)
                                        inputText = ""
                                    }
                                },
                                enabled = !isSending,
                                colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Your Safe Space is Connected", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Messages are ephemeral and automatically wiped every 30 days. Speak freely with empathy and respect.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    ChatMessageItem(
                        message = message,
                        isMine = message.senderId == userId
                    )
                }
            }
        }
    }
}

@Composable
fun ChatMessageItem(
    message: Message,
    isMine: Boolean
) {
    val formattedTime = remember(message.timestamp) {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        sdf.format(Date(message.timestamp))
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
        ) {
            if (message.isVoiceNote) {
                VoiceNoteBubble(
                    durationSeconds = message.voiceDurationSeconds.coerceAtLeast(1),
                    isMine = isMine
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 1.dp
                ) {
                    Text(
                        text = message.text,
                        modifier = Modifier.padding(12.dp),
                        color = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
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
