package com.hearu.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.hearu.app.model.Message
import com.hearu.app.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _errorState = MutableStateFlow<String?>(null)
    val errorState: StateFlow<String?> = _errorState.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private var currentSessionId: String = ""
    private var messageJob: Job? = null

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
                    text = text,
                    timestamp = System.currentTimeMillis()
                )
                chatRepository.sendMessage(currentSessionId, msg)
            } catch (e: Exception) {
                _errorState.value = "Failed to send message: ${e.message}"
            } finally {
                _isSending.value = false
            }
        }
    }

    fun clearError() { _errorState.value = null }

    override fun onCleared() {
        super.onCleared()
        messageJob?.cancel()
    }
}
