package com.hearu.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.vertexai.type.content
import com.hearu.app.ai.AiResult
import com.hearu.app.ai.GeminiAiService
import com.hearu.app.data.RolePreferences
import com.hearu.app.model.Message
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
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

    private val _quotaUsed = MutableStateFlow(0)
    val quotaUsed: StateFlow<Int> = _quotaUsed.asStateFlow()

    val MESSAGE_LIMIT = 50

    private val messageIdCounter = AtomicLong(0L)
    private var refreshQuotaJob: Job? = null

    val currentUserId: String
        get() = auth.currentUser?.uid ?: "user_anonymous"

    init {
        _messages.value = listOf(
            Message(
                id = "ai_welcome",
                senderId = Message.SENDER_AI,
                text = "Hello, I'm your HearU AI Companion. I'm here 24/7 to listen without judgment. How is your heart feeling today?",
                timestamp = System.currentTimeMillis()
            )
        )
        refreshQuota()
    }

    private fun refreshQuota() {
        refreshQuotaJob?.cancel()
        refreshQuotaJob = viewModelScope.launch {
            val used = preferences.getAiMessagesUsedToday()
            _quotaUsed.value = used
            _quotaExhausted.value = used >= MESSAGE_LIMIT
        }
    }

    fun dismissCrisisDialog() {
        _crisisEvent.value = false
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || _isLoading.value || _quotaExhausted.value) return
        refreshQuotaJob?.cancel()
        refreshQuotaJob = null
        val currentUid = currentUserId
        _isLoading.value = true

        viewModelScope.launch {
            try {
                val currentUsed = preferences.getAiMessagesUsedToday()
                if (currentUsed >= MESSAGE_LIMIT) {
                    _quotaUsed.value = currentUsed
                    _quotaExhausted.value = true
                    return@launch
                }

                val nonWelcome = _messages.value.filter { it.id != "ai_welcome" }
                val pairedHistory = buildList {
                    var i = 0
                    while (i + 1 < nonWelcome.size) {
                        val first = nonWelcome[i]
                        val second = nonWelcome[i + 1]
                        if (first.senderId != Message.SENDER_AI &&
                            second.senderId == Message.SENDER_AI &&
                            !second.id.startsWith("ai_err_")
                        ) {
                            add(first)
                            add(second)
                            i += 2
                        } else {
                            i += 1
                        }
                    }
                }.takeLast(20)
                val history = pairedHistory.map { msg ->
                    content(role = if (msg.senderId == Message.SENDER_AI) "model" else "user") {
                        text(msg.text)
                    }
                }

                val now = System.currentTimeMillis()
                val userMsg = Message(
                    id = "user_${messageIdCounter.incrementAndGet()}_$now",
                    senderId = currentUid,
                    text = text,
                    timestamp = now
                )
                _messages.value = _messages.value + userMsg

                val result = if (history.isEmpty()) {
                    geminiService.generateEmpatheticResponse(text)
                } else {
                    geminiService.generateEmpatheticResponse(text, history)
                }

                when (result) {
                    is AiResult.Success -> {
                        val consumed = preferences.tryConsumeAiQuota(MESSAGE_LIMIT)
                        val updatedUsed = preferences.getAiMessagesUsedToday()
                        _quotaUsed.value = updatedUsed
                        _quotaExhausted.value = !consumed || updatedUsed >= MESSAGE_LIMIT
                        if (result.isCrisisDetected) _crisisEvent.value = true

                        val replyTime = System.currentTimeMillis()
                        val aiMsg = Message(
                            id = "ai_${messageIdCounter.incrementAndGet()}_$replyTime",
                            senderId = Message.SENDER_AI,
                            text = result.message,
                            timestamp = replyTime
                        )
                        _messages.value = _messages.value + aiMsg
                    }
                    is AiResult.Error -> {
                        if (result.isCrisisDetected) _crisisEvent.value = true
                        val errTime = System.currentTimeMillis()
                        val errorMsg = Message(
                            id = "ai_err_${messageIdCounter.incrementAndGet()}_$errTime",
                            senderId = Message.SENDER_AI,
                            text = "I'm having a brief connection issue, but please know you're not alone. Feel free to try again or reach out to our human listeners.",
                            timestamp = errTime
                        )
                        _messages.value = _messages.value + errorMsg
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                _isLoading.value = false
            }
        }
    }
}
