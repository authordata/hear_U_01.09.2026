package com.hearu.app

import com.hearu.app.service.HearUNotificationConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class HearUFirebaseMessagingServiceTest {

    @Test
    fun `verify notification channel ids and message types`() {
        assertEquals("hearu_chat_messages", HearUNotificationConfig.CHANNEL_CHAT_MESSAGES)
        assertEquals("hearu_session_requests", HearUNotificationConfig.CHANNEL_SESSION_REQUESTS)
        assertEquals("hearu_safety_alerts", HearUNotificationConfig.CHANNEL_SAFETY_ALERTS)

        assertEquals("chat_message", HearUNotificationConfig.TYPE_CHAT_MESSAGE)
        assertEquals("session_request", HearUNotificationConfig.TYPE_SESSION_REQUEST)
        assertEquals("crisis_alert", HearUNotificationConfig.TYPE_CRISIS_ALERT)
        assertEquals("session_ended", HearUNotificationConfig.TYPE_SESSION_ENDED)

        assertNotNull(HearUNotificationConfig.EXTRA_NOTIFICATION_TYPE)
        assertNotNull(HearUNotificationConfig.EXTRA_SESSION_ID)
        assertNotNull(HearUNotificationConfig.EXTRA_USER_ID)
    }
}
