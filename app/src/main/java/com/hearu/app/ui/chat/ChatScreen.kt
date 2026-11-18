package com.hearu.app.ui.chat

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hearu.app.R
import com.hearu.app.model.Message
import com.hearu.app.ui.chat.components.VoiceNoteBubble
import com.hearu.app.ui.chat.components.VoiceRecordBar
import com.hearu.app.ui.dialogs.CrisisSupportDialog
import com.hearu.app.ui.dialogs.ReportUserDialog
import kotlinx.coroutines.launch
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
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isSending by viewModel.isSending.collectAsStateWithLifecycle()
    val errorState by viewModel.errorState.collectAsStateWithLifecycle()
    val isPhotoRevealed by viewModel.isPhotoRevealed.collectAsStateWithLifecycle()
    val myPhotoConsent by viewModel.myPhotoConsent.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val recordingDuration by viewModel.recordingDuration.collectAsStateWithLifecycle()
    val liveAmplitudes by viewModel.liveAmplitudes.collectAsStateWithLifecycle()
    val playingMessageId by viewModel.playingMessageId.collectAsStateWithLifecycle()
    val playbackProgress by viewModel.playbackProgress.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

    val effectiveUserId = viewModel.currentUserId.orEmpty()
    val resolvedPeerId = peerId.ifBlank { userId }.takeIf { it != effectiveUserId }.orEmpty()

    var inputText by rememberSaveable { mutableStateOf("") }
    var showMenu by rememberSaveable { mutableStateOf(false) }
    var showReportDialog by rememberSaveable { mutableStateOf(false) }
    var showBlockDialog by rememberSaveable { mutableStateOf(false) }
    var showCrisisDialog by rememberSaveable { mutableStateOf(false) }
    var showPhotoConsentDialog by rememberSaveable { mutableStateOf(false) }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startVoiceRecording(context, context.cacheDir)
        } else {
            coroutineScope.launch {
                val action = snackbarHostState.showSnackbar(
                    message = "Microphone permission is required to record voice notes.",
                    actionLabel = "Settings",
                    duration = SnackbarDuration.Long
                )
                if (action == SnackbarResult.ActionPerformed) {
                    val settingsIntent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    )
                    try {
                        context.startActivity(settingsIntent)
                    } catch (_: ActivityNotFoundException) {
                    }
                }
            }
        }
    }

    LaunchedEffect(sessionId) {
        viewModel.joinSession(sessionId, userId)
    }

    LaunchedEffect(errorState) {
        val currentError = errorState
        if (!currentError.isNullOrBlank()) {
            snackbarHostState.showSnackbar(currentError)
            viewModel.clearError()
        }
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
                viewModel.submitReport(resolvedPeerId, reason, details)
                showReportDialog = false
            }
        )
    }

    if (showBlockDialog) {
        AlertDialog(
            onDismissRequest = { showBlockDialog = false },
            icon = {
                Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            },
            title = { Text("Block & Leave Session") },
            text = {
                Text(
                    "Blocking this user will end your current session and prevent future matches with them."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBlockDialog = false
                        viewModel.blockAndLeave(resolvedPeerId) {
                            onNavigateBack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.block_user))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCrisisDialog) {
        CrisisSupportDialog(
            onDismiss = { showCrisisDialog = false }
        )
    }

    if (showPhotoConsentDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoConsentDialog = false },
            icon = {
                Icon(
                    imageVector = if (myPhotoConsent) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("Mutual Photo Reveal Consent") },
            text = {
                Text(
                    "HearU safeguards your privacy by visually blurring profile photos by default.\n\n" +
                        "To unblur avatars, both you and your peer must independently grant mutual consent. " +
                        "Either person can revoke consent at any time to instantly re-blur."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.togglePhotoConsent()
                        showPhotoConsentDialog = false
                    }
                ) {
                    Text(if (myPhotoConsent) "Revoke Consent (Re-blur)" else "Grant Mutual Consent")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPhotoConsentDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .then(if (!isPhotoRevealed) Modifier.blur(10.dp) else Modifier),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Peer Avatar",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondary)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Empathetic Listener",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isPhotoRevealed) "Photos unblurred (mutual consent)" else "End-to-end ephemeral peer chat",
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
                    IconButton(onClick = { showPhotoConsentDialog = true }) {
                        Icon(
                            imageVector = if (isPhotoRevealed) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Photo Reveal Consent",
                            tint = if (isPhotoRevealed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onNavigateToCrisis) {
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
                            text = { Text(if (myPhotoConsent) "Revoke Photo Consent" else "Mutual Photo Reveal Consent") },
                            onClick = {
                                showMenu = false
                                showPhotoConsentDialog = true
                            },
                            leadingIcon = {
                                Icon(
                                    if (myPhotoConsent) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Crisis Resources (988)") },
                            onClick = {
                                showMenu = false
                                showCrisisDialog = true
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
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.block_user), color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                showBlockDialog = true
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error)
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
                    items(reactions) { emoji ->
                        AssistChip(
                            onClick = { viewModel.sendMessage(emoji) },
                            label = { Text(emoji) }
                        )
                    }
                }

                // Voice Recording Live Bar
                if (isRecording) {
                    VoiceRecordBar(
                        isRecording = isRecording,
                        recordingDurationSec = recordingDuration,
                        liveAmplitudes = liveAmplitudes,
                        onStartRecording = {
                            viewModel.startVoiceRecording(context, context.cacheDir)
                        },
                        onStopRecording = {
                            viewModel.stopAndSendVoiceRecording()
                        },
                        onCancelRecording = {
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
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        viewModel.startVoiceRecording(context, context.cacheDir)
                                    } else {
                                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                            ) {
                                Icon(
                                    Icons.Default.Mic,
                                    contentDescription = "Record Voice Note",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
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
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Icon(
                        Icons.Default.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Your Safe Space is Connected", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Messages are ephemeral and automatically wiped every 30 days. Speak freely with empathy and respect.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
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
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(keyedMessages, key = { it.second }) { (message, _) ->
                    ChatMessageItem(
                        message = message,
                        isMine = effectiveUserId.isNotBlank() && message.senderId == effectiveUserId,
                        isPlaying = isPlaying && playingMessageId == message.id,
                        playbackProgress = if (playingMessageId == message.id) playbackProgress else 0f,
                        onTogglePlay = { viewModel.toggleVoicePlayback(message) },
                        onSeek = { fraction -> viewModel.seekVoicePlayback(message, fraction) }
                    )
                }
            }
        }
    }
}

@Composable
fun ChatMessageItem(
    message: Message,
    isMine: Boolean,
    isPlaying: Boolean = false,
    playbackProgress: Float = 0f,
    onTogglePlay: () -> Unit = {},
    onSeek: (Float) -> Unit = {}
) {
    if (message.isSystemMessage || message.messageType == Message.TYPE_SYSTEM) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
        return
    }

    val formattedTime = remember(message.timestamp) {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        sdf.format(Date(message.timestamp))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
        ) {
            if (message.isVoiceNote || message.messageType == Message.TYPE_VOICE) {
                val duration = (message.audioDurationSec.takeIf { it > 0 } ?: message.voiceDurationSeconds)
                    .coerceAtLeast(1)
                VoiceNoteBubble(
                    durationSeconds = duration,
                    isMine = isMine,
                    isPlaying = isPlaying,
                    playbackProgress = playbackProgress,
                    waveformAmplitudes = message.waveformAmplitudes,
                    onTogglePlay = onTogglePlay,
                    onSeek = onSeek
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 1.dp,
                    modifier = Modifier.widthIn(max = 300.dp)
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
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.90f),
                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
            )
        }
    }
}
