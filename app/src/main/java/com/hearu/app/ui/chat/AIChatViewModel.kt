package com.hearu.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hearu.app.model.Message
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AIChatViewModel @Inject constructor() : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private var messagesUsedToday = 0
    private val MESSAGE_LIMIT = 50

    init {
        // Initial greeting
        _messages.value = listOf(
            Message(
                id = "ai_welcome",
                senderId = "ai_companion",
                text = "Hello. I'm your HearU AI Companion. I'm here to listen. How are you feeling today?",
                timestamp = System.currentTimeMillis()
            )
        )
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || messagesUsedToday >= MESSAGE_LIMIT) return

        // Add user message
        val userMsg = Message(
            id = "user_${System.currentTimeMillis()}",
            senderId = "user123",
            text = text,
            timestamp = System.currentTimeMillis()
        )
        _messages.value = _messages.value + userMsg
        messagesUsedToday++

        // Simulate AI Response (In production, this calls Firebase AI Logic / Gemini)
        viewModelScope.launch {
            delay(1500) // Simulate thinking
            val aiResponse = Message(
                id = "ai_${System.currentTimeMillis()}",
                senderId = "ai_companion",
                text = "I hear you. Thank you for sharing that with me. (Simulated Gemini 3.7 Flash Response)",
                timestamp = System.currentTimeMillis()
            )
            _messages.value = _messages.value + aiResponse
        }
    }
}
