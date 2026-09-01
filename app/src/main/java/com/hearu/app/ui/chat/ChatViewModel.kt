package com.hearu.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hearu.app.model.Message
import com.hearu.app.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private var currentSessionId: String = ""
    private var currentUserId: String = "user123"

    fun joinSession(sessionId: String, userId: String) {
        currentSessionId = sessionId
        currentUserId = userId
        viewModelScope.launch {
            chatRepository.getMessages(sessionId).collect { msgs ->
                _messages.value = msgs
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val msg = Message(
                senderId = currentUserId,
                text = text,
                timestamp = System.currentTimeMillis()
            )
            chatRepository.sendMessage(currentSessionId, msg)
        }
    }
}
