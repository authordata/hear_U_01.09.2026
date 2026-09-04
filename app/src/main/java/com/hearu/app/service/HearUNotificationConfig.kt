package com.hearu.app.service

/**
 * Pure Kotlin configuration constants for HearU notifications.
 * Decoupled from Android Service and Hilt lifecycle to ensure 100% testability on JVM.
 */
object HearUNotificationConfig {
    const val CHANNEL_CHAT_MESSAGES = "hearu_chat_messages"
    const val CHANNEL_SESSION_REQUESTS = "hearu_session_requests"
    const val CHANNEL_SAFETY_ALERTS = "hearu_safety_alerts"

    const val TYPE_CHAT_MESSAGE = "chat_message"
    const val TYPE_SESSION_REQUEST = "session_request"
    const val TYPE_CRISIS_ALERT = "crisis_alert"
    const val TYPE_SESSION_ENDED = "session_ended"

    const val EXTRA_NOTIFICATION_TYPE = "extra_notification_type"
    const val EXTRA_SESSION_ID = "extra_session_id"
    const val EXTRA_USER_ID = "extra_user_id"
}
