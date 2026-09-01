package com.hearu.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hearu.app.ai.AiResult
import com.hearu.app.ai.GeminiAiService
import com.hearu.app.model.Message
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AIChatViewModel @Inject constructor(
    private val geminiService: GeminiAiService
) : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _crisisEvent = MutableStateFlow(false)
    val crisisEvent: StateFlow<Boolean> = _crisisEvent.asStateFlow()

    private var messagesUsedToday = 0
    private val MESSAGE_LIMIT = 50

    init {
        _messages.value = listOf(
            Message(
                id = "ai_welcome",
                senderId = "ai_companion",
                text = "Hello. I'm your HearU AI Companion, powered by Gemini. I'm here to listen without judgment. How are you feeling right now?",
                timestamp = System.currentTimeMillis()
            )
        )
    }

    fun dismissCrisisDialog() {
        _crisisEvent.value = false
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || messagesUsedToday >= MESSAGE_LIMIT) return

        val userMsg = Message(
            id = "user_${System.currentTimeMillis()}",
            senderId = "user123",
            text = text,
            timestamp = System.currentTimeMillis()
        )
        _messages.value = _messages.value + userMsg
        messagesUsedToday++

        viewModelScope.launch {
            _isLoading.value = true
            when (val result = geminiService.generateEmpatheticResponse(text)) {
                is AiResult.Success -> {
                    if (result.isCrisisDetected) {
                        _crisisEvent.value = true
                    }
                    val aiMsg = Message(
                        id = "ai_${System.currentTimeMillis()}",
                        senderId = "ai_companion",
                        text = result.message,
                        timestamp = System.currentTimeMillis()
                    )
                    _messages.value = _messages.value + aiMsg
                }
                is AiResult.Error -> {
                    val errorMsg = Message(
                        id = "ai_err_${System.currentTimeMillis()}",
                        senderId = "ai_companion",
                        text = "I'm having a little trouble connecting right now, but please know you're not alone. Feel free to reach out to a human listener or try again in a moment.",
                        timestamp = System.currentTimeMillis()
                    )
                    _messages.value = _messages.value + errorMsg
                }
            }
            _isLoading.value = false
        }
    }
}
