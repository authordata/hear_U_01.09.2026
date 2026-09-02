package com.hearu.app.ui.chat

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hearu.app.model.Message
import com.hearu.app.ui.dialogs.CrisisSupportDialog
import com.hearu.app.ui.dialogs.ReportUserDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    sessionId: String,
    userId: String,
    onNavigateBack: () -> Unit,
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
    var showPhotoRevealDialog by rememberSaveable { mutableStateOf(false) }
    var isPhotoUnblurred by rememberSaveable { mutableStateOf(false) }
    var showEndSessionSheet by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(sessionId) {
        viewModel.joinSession(sessionId, userId)
    }

    if (showReportDialog) {
        ReportUserDialog(
            onDismiss = { showReportDialog = false },
            onSubmitReport = { _, _ ->
                showReportDialog = false
                Toast.makeText(context, "Report submitted for moderation. Thank you for keeping HearU safe.", Toast.LENGTH_LONG).show()
            }
        )
    }

    if (showCrisisDialog) {
        CrisisSupportDialog(onDismiss = { showCrisisDialog = false })
    }

    if (showPhotoRevealDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoRevealDialog = false },
            title = { Text("Mutual Identity Reveal") },
            text = {
                Text("Both you and your peer must agree to reveal profile photos. Your name and private info remain strictly confidential. Do you wish to request mutual reveal?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        isPhotoUnblurred = true
                        showPhotoRevealDialog = false
                        Toast.makeText(context, "Mutual reveal enabled for this session.", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Reveal Photo")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPhotoRevealDialog = false }) {
                    Text("Keep Blurred")
                }
            }
        )
    }

    if (showEndSessionSheet) {
        ModalBottomSheet(onDismissRequest = { showEndSessionSheet = false }) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Session Completed", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("How was your conversation? Your rating helps maintain our empathetic community.", textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(5) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Star",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        showEndSessionSheet = false
                        onNavigateBack()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Submit & Return to Dashboard")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Blurred / Unblurred avatar
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .then(if (!isPhotoUnblurred) Modifier.blur(8.dp) else Modifier),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Peer Listener", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                if (isPhotoUnblurred) "Mutual Reveal Active" else "Encrypted & Blurred",
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
                    IconButton(onClick = { showPhotoRevealDialog = true }) {
                        Icon(
                            if (isPhotoUnblurred) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle photo reveal"
                        )
                    }
                    IconButton(
                        onClick = { showCrisisDialog = true },
                        modifier = Modifier.semantics { contentDescription = "Emergency SOS crisis support" }
                    ) {
                        Text("SOS", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("End Session & Rate") },
                            onClick = {
                                showMenu = false
                                showEndSessionSheet = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Report User") },
                            onClick = {
                                showMenu = false
                                showReportDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Block & Leave", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                Toast.makeText(context, "User blocked. Leaving session.", Toast.LENGTH_SHORT).show()
                                onNavigateBack()
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        bottomBar = {
            Column(modifier = Modifier.padding(8.dp).fillMaxWidth()) {
                // Quick Empathy Reaction Row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val reactions = listOf("❤️", "🫂", "🙏", "🌸", "✨")
                    reactions.forEach { emoji ->
                        AssistChip(
                            onClick = { viewModel.sendMessage(emoji) },
                            label = { Text(emoji) }
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Share with compassion...") },
                        maxLines = 4
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                viewModel.sendMessage(inputText)
                                inputText = ""
                            }
                        },
                        enabled = !isSending && inputText.isNotBlank(),
                        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.onPrimary)
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
                    Text(
                        "Say hello. Take your time, there is no rush.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                reverseLayout = true
            ) {
                items(messages.reversed(), key = { it.id }) { msg ->
                    val isMine = msg.senderId == userId
                    MessageBubble(message = msg, isMine = isMine)
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: Message, isMine: Boolean) {
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val formattedTime = remember(message.timestamp) { timeFormat.format(Date(message.timestamp)) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
        ) {
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
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
            )
        }
    }
}
