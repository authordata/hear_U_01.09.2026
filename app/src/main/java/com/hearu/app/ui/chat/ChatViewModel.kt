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
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private var currentSessionId: String = ""
    private var currentUserId: String = auth.currentUser?.uid ?: ""
    private var messageJob: Job? = null

    fun joinSession(sessionId: String, userId: String) {
        currentSessionId = sessionId
        currentUserId = userId.ifBlank { auth.currentUser?.uid ?: "" }
        
        messageJob?.cancel()
        messageJob = viewModelScope.launch {
            chatRepository.getMessages(sessionId).collect { msgs ->
                _messages.value = msgs
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || currentSessionId.isBlank()) return
        val sender = currentUserId.ifBlank { auth.currentUser?.uid ?: "anonymous" }
        viewModelScope.launch {
            val msg = Message(
                senderId = sender,
                text = text,
                timestamp = System.currentTimeMillis()
            )
            chatRepository.sendMessage(currentSessionId, msg)
        }
    }

    override fun onCleared() {
        super.onCleared()
        messageJob?.cancel()
    }
}
