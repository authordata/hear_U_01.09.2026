package com.hearu.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.hearu.app.ai.AiResult
import com.hearu.app.ai.GeminiAiService
import com.hearu.app.data.RolePreferences
import com.hearu.app.model.Message
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AIChatViewModel @Inject constructor(
    private val geminiService: GeminiAiService,
    private val preferences: RolePreferences,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _crisisEvent = MutableStateFlow(false)
    val crisisEvent: StateFlow<Boolean> = _crisisEvent.asStateFlow()

    private val _quotaExhausted = MutableStateFlow(false)
    val quotaExhausted: StateFlow<Boolean> = _quotaExhausted.asStateFlow()

    private val MESSAGE_LIMIT = 50

    init {
        _messages.value = listOf(
            Message(
                id = "ai_welcome",
                senderId = "ai_companion",
                text = "Hello. I'm your HearU AI Companion. I'm here to listen without judgment. How are you feeling right now?",
                timestamp = System.currentTimeMillis()
            )
        )
        // Check quota on init so UI disables input immediately if exhausted
        viewModelScope.launch {
            val used = preferences.getAiMessagesUsedToday()
            if (used >= MESSAGE_LIMIT) _quotaExhausted.value = true
        }
    }

    fun dismissCrisisDialog() { _crisisEvent.value = false }

    fun sendMessage(text: String) {
        if (text.isBlank() || _quotaExhausted.value) return
        val currentUid = auth.currentUser?.uid ?: "user"

        viewModelScope.launch {
            val userMsg = Message(
                id = "user_${System.currentTimeMillis()}",
                senderId = currentUid,
                text = text,
                timestamp = System.currentTimeMillis()
            )
            _messages.value = _messages.value + userMsg
            _isLoading.value = true

            when (val result = geminiService.generateEmpatheticResponse(text)) {
                is AiResult.Success -> {
                    // Only consume quota on successful AI response
                    val consumed = preferences.tryConsumeAiQuota(MESSAGE_LIMIT)
                    if (!consumed) {
                        _quotaExhausted.value = true
                    }
                    if (result.isCrisisDetected) _crisisEvent.value = true
                    val aiMsg = Message(
                        id = "ai_${System.currentTimeMillis()}",
                        senderId = "ai_companion",
                        text = result.message,
                        timestamp = System.currentTimeMillis()
                    )
                    _messages.value = _messages.value + aiMsg
                }
                is AiResult.Error -> {
                    // Quota NOT consumed on error — user gets a retry
                    val errorMsg = Message(
                        id = "ai_err_${System.currentTimeMillis()}",
                        senderId = "ai_companion",
                        text = "I'm having a little trouble connecting. Please try again in a moment.",
                        timestamp = System.currentTimeMillis()
                    )
                    _messages.value = _messages.value + errorMsg
                }
            }
            _isLoading.value = false
        }
    }
}
