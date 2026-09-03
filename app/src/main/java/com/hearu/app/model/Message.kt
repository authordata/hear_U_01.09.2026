package com.hearu.app.model

import com.google.firebase.firestore.DocumentId

data class Message(
    @DocumentId val id: String = "",
    val senderId: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isSystemMessage: Boolean = false,
    val isVoiceNote: Boolean = false,
    val voiceDurationSeconds: Int = 0,
    val messageType: String = TYPE_TEXT,
    val audioUrl: String? = null,
    val audioDurationSec: Int = 0,
    val waveformAmplitudes: List<Int> = emptyList()
) {
    companion object {
        const val TYPE_TEXT = "text"
        const val TYPE_VOICE = "voice"
        const val TYPE_SYSTEM = "system"
    }
}
