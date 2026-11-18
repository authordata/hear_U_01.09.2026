package com.hearu.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cached_messages",
    indices = [
        Index(value = ["sessionId", "timestamp"])
    ]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val senderId: String,
    val text: String,
    val timestamp: Long,
    val isSystemMessage: Boolean = false,
    val isVoiceNote: Boolean = false,
    val voiceDurationSeconds: Int = 0,
    val messageType: String = "text",
    val audioUrl: String? = null,
    val audioDurationSec: Int = 0,
    val waveformAmplitudes: List<Int> = emptyList(),
    val syncStatus: String = SYNC_SENT
) {
    companion object {
        const val SYNC_PENDING = "PENDING"
        const val SYNC_SENT = "SENT"
        const val SYNC_FAILED = "FAILED"
    }
}
