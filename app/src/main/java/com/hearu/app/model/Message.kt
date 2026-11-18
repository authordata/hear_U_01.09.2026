package com.hearu.app.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName

data class Message(
    @DocumentId val id: String = "",
    val senderId: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    @get:PropertyName("isSystemMessage")
    @set:PropertyName("isSystemMessage")
    var isSystemMessage: Boolean = false,
    @get:PropertyName("isVoiceNote")
    @set:PropertyName("isVoiceNote")
    var isVoiceNote: Boolean = false,
    val voiceDurationSeconds: Int = 0,
    val messageType: String = TYPE_TEXT,
    val audioUrl: String? = null,
    val audioDurationSec: Int = 0,
    val waveformAmplitudes: List<Int> = emptyList()
) {
    companion object {
        const val SENDER_AI = "ai_companion"
        const val TYPE_TEXT = "text"
        const val TYPE_VOICE = "voice"
        const val TYPE_SYSTEM = "system"
    }
}
