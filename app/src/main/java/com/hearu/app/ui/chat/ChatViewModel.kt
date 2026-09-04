package com.hearu.app.ui.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.hearu.app.audio.VoiceNoteManager
import com.hearu.app.model.Message
import com.hearu.app.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val auth: FirebaseAuth,
    val voiceNoteManager: VoiceNoteManager
) : ViewModel() {

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

    fun togglePhotoConsent() {
        val next = !_myPhotoConsent.value
        _myPhotoConsent.value = next
        _isPhotoRevealed.value = next
    }

    private var currentSessionId: String = ""
    private var messageJob: Job? = null

    val isRecording = voiceNoteManager.isRecording
    val recordingDuration = voiceNoteManager.recordingDuration
    val liveAmplitudes = voiceNoteManager.liveAmplitudes
    val playingMessageId = voiceNoteManager.playingMessageId
    val playbackProgress = voiceNoteManager.playbackProgress
    val isPlaying = voiceNoteManager.isPlaying

    fun joinSession(sessionId: String, userId: String) {
        currentSessionId = sessionId
        messageJob?.cancel()
        messageJob = viewModelScope.launch {
            chatRepository.getMessages(sessionId)
                .catch { e -> _errorState.value = "Failed to load messages: ${e.message}" }
                .collect { msgs -> _messages.value = msgs }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || currentSessionId.isBlank()) return
        val sender = auth.currentUser?.uid ?: run {
            _errorState.value = "Session expired. Please log in again."
            return
        }

        viewModelScope.launch {
            _isSending.value = true
            try {
                val msg = Message(
                    senderId = sender,
                    text = text.trim(),
                    timestamp = System.currentTimeMillis(),
                    messageType = Message.TYPE_TEXT
                )
                chatRepository.sendMessage(currentSessionId, msg)
            } catch (e: Exception) {
                _errorState.value = "Failed to send message: ${e.message}"
            } finally {
                _isSending.value = false
            }
        }
    }

    fun startVoiceRecording(context: Context, cacheDir: File): Boolean {
        return voiceNoteManager.startRecording(context, cacheDir)
    }

    fun stopAndSendVoiceRecording() {
        val result = voiceNoteManager.stopRecording() ?: return
        val sender = auth.currentUser?.uid ?: run {
            _errorState.value = "Session expired. Please log in again."
            return
        }

        viewModelScope.launch {
            _isSending.value = true
            try {
                val voiceMsg = Message(
                    senderId = sender,
                    text = "🎤 Voice Note (${String.format("%d:%02d", result.durationSec / 60, result.durationSec % 60)})",
                    timestamp = System.currentTimeMillis(),
                    isVoiceNote = true,
                    voiceDurationSeconds = result.durationSec,
                    messageType = Message.TYPE_VOICE,
                    audioUrl = result.file?.absolutePath,
                    audioDurationSec = result.durationSec,
                    waveformAmplitudes = result.amplitudes
                )
                chatRepository.sendMessage(currentSessionId, voiceMsg)
            } catch (e: Exception) {
                _errorState.value = "Failed to send voice note: ${e.message}"
            } finally {
                _isSending.value = false
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

    fun clearError() { _errorState.value = null }

    override fun onCleared() {
        super.onCleared()
        messageJob?.cancel()
        voiceNoteManager.cancelRecording()
    }
}
