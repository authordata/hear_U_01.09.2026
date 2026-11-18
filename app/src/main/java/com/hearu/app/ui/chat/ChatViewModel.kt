package com.hearu.app.ui.chat

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import com.hearu.app.audio.VoiceNoteManager
import com.hearu.app.data.RolePreferences
import com.hearu.app.model.ChatSession
import com.hearu.app.model.Message
import com.hearu.app.repository.ChatRepository
import com.hearu.app.repository.ModerationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

@HiltViewModel
class ChatViewModel : ViewModel {
    private val chatRepository: ChatRepository
    private val auth: FirebaseAuth
    val voiceNoteManager: VoiceNoteManager
    private val moderationRepository: ModerationRepository?
    private val rolePreferences: RolePreferences?

    @Inject
    constructor(
        chatRepository: ChatRepository,
        auth: FirebaseAuth,
        voiceNoteManager: VoiceNoteManager,
        moderationRepository: ModerationRepository,
        rolePreferences: RolePreferences
    ) : super() {
        this.chatRepository = chatRepository
        this.auth = auth
        this.voiceNoteManager = voiceNoteManager
        this.moderationRepository = moderationRepository
        this.rolePreferences = rolePreferences
    }

    @JvmOverloads
    constructor(
        chatRepository: ChatRepository,
        auth: FirebaseAuth,
        voiceNoteManager: VoiceNoteManager,
        moderationRepository: ModerationRepository? = null
    ) : super() {
        this.chatRepository = chatRepository
        this.auth = auth
        this.voiceNoteManager = voiceNoteManager
        this.moderationRepository = moderationRepository
        this.rolePreferences = null
    }

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _errorState = MutableStateFlow<String?>(null)
    val errorState: StateFlow<String?> = _errorState.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _isPhotoRevealed = MutableStateFlow(false)
    val isPhotoRevealed: StateFlow<Boolean> = _isPhotoRevealed.asStateFlow()

    private val _myPhotoConsent = MutableStateFlow(false)
    val myPhotoConsent: StateFlow<Boolean> = _myPhotoConsent.asStateFlow()

    val currentUserId: String?
        get() = auth.currentUser?.uid

    private var currentSessionId: String = ""
    private var peerIdFallback: String = ""
    private var observedSession: ChatSession? = null
    private var messageJob: Job? = null
    private var sessionJob: Job? = null
    private var consentJob: Job? = null
    private val inFlightSendCount = AtomicInteger(0)

    val isRecording: StateFlow<Boolean>
        get() = voiceNoteManager.isRecording
    val recordingDuration: StateFlow<Int>
        get() = voiceNoteManager.recordingDuration
    val liveAmplitudes: StateFlow<List<Int>>
        get() = voiceNoteManager.liveAmplitudes
    val playingMessageId: StateFlow<String?>
        get() = voiceNoteManager.playingMessageId
    val playbackProgress: StateFlow<Float>
        get() = voiceNoteManager.playbackProgress
    val isPlaying: StateFlow<Boolean>
        get() = voiceNoteManager.isPlaying

    fun joinSession(sessionId: String, userId: String) {
        if (userId.isNotBlank() && userId != currentUserId) {
            peerIdFallback = userId
        }
        if (sessionId != currentSessionId) {
            consentJob?.cancel()
            consentJob = null
            inFlightSendCount.set(0)
            _isSending.value = false
            _errorState.value = null
            if (currentSessionId.isNotBlank()) {
                voiceNoteManager.cancelRecording()
                voiceNoteManager.stopPlayback()
                chatRepository.stopFirestoreSync(currentSessionId)
            }
            observedSession = null
            _messages.value = emptyList()
            _myPhotoConsent.value = false
            _isPhotoRevealed.value = false
        }
        currentSessionId = sessionId

        messageJob?.cancel()
        val msgFlow: Flow<List<Message>>? = chatRepository.getMessages(sessionId)
        if (msgFlow != null) {
            messageJob = viewModelScope.launch {
                msgFlow
                    .catch { e -> _errorState.value = "Failed to load messages: ${e.message}" }
                    .collect { msgs -> _messages.value = msgs }
            }
        }

        sessionJob?.cancel()
        val sessionFlow: Flow<ChatSession?>? = chatRepository.observeSession(sessionId)
        if (sessionFlow != null) {
            sessionJob = viewModelScope.launch {
                sessionFlow
                    .catch { Log.w(TAG, "Session observer unavailable for active session") }
                    .collect { session ->
                        if (session != null) {
                            observedSession = session
                            _isPhotoRevealed.value = session.identityRevealed
                            val uid = currentUserId
                            if (uid != null) {
                                _myPhotoConsent.value = when (uid) {
                                    session.seekerId -> session.seekerConsent
                                    session.giverId -> session.giverConsent
                                    else -> _myPhotoConsent.value
                                }
                            }
                        }
                    }
            }
        }
    }

    fun togglePhotoConsent() {
        val next = !_myPhotoConsent.value
        _myPhotoConsent.value = next
        if (!next) {
            _isPhotoRevealed.value = false
        }
        val sessionId = currentSessionId
        if (sessionId.isBlank()) return

        consentJob?.cancel()
        consentJob = viewModelScope.launch {
            val isSeeker = resolveIsSeeker()
            try {
                val revealed = chatRepository.updatePhotoConsent(sessionId, isSeeker, next)
                if (currentSessionId == sessionId) {
                    _isPhotoRevealed.value = revealed
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: FirebaseFirestoreException) {
                if (currentSessionId == sessionId) {
                    _myPhotoConsent.value = !next
                    _errorState.value = "Failed to update photo consent: ${e.message}"
                }
            } catch (e: Exception) {
                if (currentSessionId == sessionId) {
                    _myPhotoConsent.value = !next
                    _errorState.value = "Failed to update photo consent: ${e.message}"
                }
            }
        }
    }

    private suspend fun resolveIsSeeker(): Boolean {
        val uid = currentUserId
        val session = observedSession
        if (session != null && uid != null) {
            return when (uid) {
                session.giverId -> false
                session.seekerId -> true
                else -> true
            }
        }
        if (rolePreferences != null) {
            return try {
                rolePreferences.activeRoleFlow.first() != "giver"
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                true
            }
        }
        return true
    }

    fun sendMessage(text: String) {
        val sessionId = currentSessionId
        if (text.isBlank() || sessionId.isBlank()) return
        val sender = auth.currentUser?.uid ?: run {
            _errorState.value = "Session expired. Please log in again."
            return
        }

        inFlightSendCount.incrementAndGet()
        _isSending.value = true
        viewModelScope.launch {
            try {
                val msg = Message(
                    senderId = sender,
                    text = text.trim(),
                    timestamp = System.currentTimeMillis(),
                    messageType = Message.TYPE_TEXT
                )
                chatRepository.sendMessage(sessionId, msg)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorState.value = "Failed to send message: ${e.message}"
            } finally {
                if (inFlightSendCount.decrementAndGet() <= 0) {
                    inFlightSendCount.set(0)
                    _isSending.value = false
                }
            }
        }
    }

    fun startVoiceRecording(context: Context, cacheDir: File): Boolean {
        val started = voiceNoteManager.startRecording(context, cacheDir)
        if (!started) {
            _errorState.value = "Unable to start voice recording. Please check microphone permissions."
        }
        return started
    }

    fun stopAndSendVoiceRecording() {
        val result = voiceNoteManager.stopRecording() ?: return
        val sessionId = currentSessionId
        if (sessionId.isBlank()) {
            result.file?.delete()
            return
        }
        val sender = auth.currentUser?.uid ?: run {
            result.file?.delete()
            _errorState.value = "Session expired. Please log in again."
            return
        }

        inFlightSendCount.incrementAndGet()
        _isSending.value = true
        viewModelScope.launch {
            try {
                val voiceMsg = Message(
                    senderId = sender,
                    text = "🎤 Voice Note (${String.format(Locale.US, "%d:%02d", result.durationSec / 60, result.durationSec % 60)})",
                    timestamp = System.currentTimeMillis(),
                    isVoiceNote = true,
                    voiceDurationSeconds = result.durationSec,
                    messageType = Message.TYPE_VOICE,
                    audioUrl = result.file?.absolutePath,
                    audioDurationSec = result.durationSec,
                    waveformAmplitudes = result.amplitudes
                )
                chatRepository.sendMessage(sessionId, voiceMsg)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorState.value = "Failed to send voice note: ${e.message}"
            } finally {
                if (inFlightSendCount.decrementAndGet() <= 0) {
                    inFlightSendCount.set(0)
                    _isSending.value = false
                }
            }
        }
    }

    fun cancelVoiceRecording() {
        voiceNoteManager.cancelRecording()
    }

    fun toggleVoicePlayback(message: Message) {
        voiceNoteManager.togglePlayback(
            messageId = message.id,
            audioPathOrUrl = message.audioUrl,
            durationSec = message.audioDurationSec.takeIf { it > 0 } ?: message.voiceDurationSeconds
        )
    }

    fun sendVoiceNote(duration: Int = 0) {
        stopAndSendVoiceRecording()
    }

    fun seekVoicePlayback(message: Message, fraction: Float) {
        voiceNoteManager.seekTo(
            messageId = message.id,
            progress = fraction,
            durationSec = message.audioDurationSec.takeIf { it > 0 } ?: message.voiceDurationSeconds
        )
    }

    fun submitReport(
        reportedUserId: String,
        reason: String,
        details: String
    ) {
        val reporterId = auth.currentUser?.uid ?: run {
            _errorState.value = "Session expired. Please log in again."
            return
        }
        val sessionId = currentSessionId
        if (sessionId.isBlank()) {
            _errorState.value = "No active session to report."
            return
        }
        val targetId = resolvePeerId(reporterId, reportedUserId)

        viewModelScope.launch {
            try {
                if (targetId.isBlank() || targetId == reporterId) {
                    if (moderationRepository != null) {
                        _errorState.value = "Unable to identify peer to report."
                    }
                    return@launch
                }
                moderationRepository?.reportUser(
                    reporterId = reporterId,
                    reportedUserId = targetId,
                    sessionId = sessionId,
                    reason = reason,
                    description = details
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorState.value = "Failed to submit report: ${e.message}"
            }
        }
    }

    fun blockAndLeave(
        reportedUserId: String,
        onSuccess: () -> Unit = {}
    ) {
        val uid = auth.currentUser?.uid ?: run {
            _errorState.value = "Session expired. Please log in again."
            return
        }
        val targetId = resolvePeerId(uid, reportedUserId)
        val sessionId = currentSessionId

        viewModelScope.launch {
            try {
                if (targetId.isNotBlank() && targetId != uid) {
                    moderationRepository?.blockUser(uid, targetId)
                }
                if (sessionId.isNotBlank()) {
                    try {
                        chatRepository.updateSessionStatus(sessionId, "ended")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        Log.w(TAG, "Best-effort session status end failed during blockAndLeave")
                    }
                    chatRepository.stopFirestoreSync(sessionId)
                }
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorState.value = "Failed to block user: ${e.message}"
            }
        }
    }

    private fun resolvePeerId(myUid: String, suppliedPeerId: String): String {
        val session = observedSession
        if (session != null) {
            when (myUid) {
                session.seekerId -> if (session.giverId.isNotBlank()) return session.giverId
                session.giverId -> if (session.seekerId.isNotBlank()) return session.seekerId
            }
        }
        if (suppliedPeerId.isNotBlank() && suppliedPeerId != myUid) return suppliedPeerId
        if (peerIdFallback.isNotBlank() && peerIdFallback != myUid) return peerIdFallback
        val inferredPeer = _messages.value.firstOrNull {
            it.senderId.isNotBlank() &&
                it.senderId != myUid &&
                it.senderId != Message.SENDER_AI &&
                !it.isSystemMessage
        }?.senderId
        if (!inferredPeer.isNullOrBlank()) return inferredPeer
        return ""
    }

    fun clearError() {
        _errorState.value = null
    }

    override fun onCleared() {
        super.onCleared()
        consentJob?.cancel()
        sessionJob?.cancel()
        messageJob?.cancel()
        if (currentSessionId.isNotBlank()) {
            chatRepository.stopFirestoreSync(currentSessionId)
        }
        voiceNoteManager.cancelRecording()
        voiceNoteManager.stopPlayback()
        voiceNoteManager.cleanup()
    }

    companion object {
        private const val TAG = "ChatViewModel"
    }
}
