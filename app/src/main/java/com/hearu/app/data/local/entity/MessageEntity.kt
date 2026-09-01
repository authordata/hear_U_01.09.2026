package com.hearu.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val senderId: String,
    val text: String,
    val timestamp: Long,
    val isSystemMessage: Boolean = false
)
