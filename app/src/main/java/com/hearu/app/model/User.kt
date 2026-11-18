package com.hearu.app.model

import androidx.compose.runtime.Immutable

enum class UserRole(val wireValue: String) {
    NONE(""),
    SEEKER("seeker"),
    GIVER("giver"),
    BOTH("both");

    companion object {
        fun fromWire(value: String?): UserRole =
            entries.firstOrNull { it.wireValue == value } ?: NONE
    }
}

enum class UserStatus(val wireValue: String) {
    ONLINE("online"),
    OFFLINE("offline");

    companion object {
        fun fromWire(value: String?): UserStatus =
            entries.firstOrNull { it.wireValue == value } ?: OFFLINE
    }
}

@Immutable
data class User(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val role: String = "",
    val status: String = "offline",
    val emotionTags: List<String> = emptyList(),
    val photoUrl: String? = null,
    val fcmToken: String? = null,
    val tokenUpdatedAt: Long = 0L,
    val blockedUsers: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)
