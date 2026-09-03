package com.hearu.app

import com.hearu.app.service.HearUFirebaseMessagingService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class HearUFirebaseMessagingServiceTest {

    @Test
    fun `verify notification channel ids and message types`() {
        assertEquals("hearu_chat_messages", HearUFirebaseMessagingService.CHANNEL_CHAT_MESSAGES)
        assertEquals("hearu_session_requests", HearUFirebaseMessagingService.CHANNEL_SESSION_REQUESTS)
        assertEquals("hearu_safety_alerts", HearUFirebaseMessagingService.CHANNEL_SAFETY_ALERTS)

        assertEquals("chat_message", HearUFirebaseMessagingService.TYPE_CHAT_MESSAGE)
        assertEquals("session_request", HearUFirebaseMessagingService.TYPE_SESSION_REQUEST)
        assertEquals("crisis_alert", HearUFirebaseMessagingService.TYPE_CRISIS_ALERT)
        assertEquals("session_ended", HearUFirebaseMessagingService.TYPE_SESSION_ENDED)

        assertNotNull(HearUFirebaseMessagingService.EXTRA_NOTIFICATION_TYPE)
        assertNotNull(HearUFirebaseMessagingService.EXTRA_SESSION_ID)
        assertNotNull(HearUFirebaseMessagingService.EXTRA_USER_ID)
    }
}
