package com.hearu.app.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class ModelSerializationTest {

    @Test
    fun `ChatSession createdAtMillis supports Timestamp Long Number Date and null`() {
        val tsSession = ChatSession(createdAt = Timestamp(1_700_000_000L, 500_000_000))
        assertEquals(1_700_000_000_500L, tsSession.createdAtMillis())

        val longSession = ChatSession(createdAt = 9_876_543L)
        assertEquals(9_876_543L, longSession.createdAtMillis())

        val intSession = ChatSession(createdAt = 42)
        assertEquals(42L, intSession.createdAtMillis())

        val doubleSession = ChatSession(createdAt = 1234.0)
        assertEquals(1234L, doubleSession.createdAtMillis())

        val dateSession = ChatSession(createdAt = Date(555_000L))
        assertEquals(555_000L, dateSession.createdAtMillis())

        val nullSession = ChatSession(createdAt = null)
        assertEquals(0L, nullSession.createdAtMillis())
    }

    @Test
    fun `ChatSession has DocumentId and false consent defaults`() {
        val session = ChatSession(sessionId = "s1", seekerId = "u1", giverId = "u2")
        assertFalse(session.identityRevealed)
        assertFalse(session.seekerConsent)
        assertFalse(session.giverConsent)

        val sessionIdField = ChatSession::class.java.getDeclaredField("sessionId")
        assertNotNull(sessionIdField.getAnnotation(DocumentId::class.java))
    }

    @Test
    fun `Message exposes SENDER_AI and PropertyName annotations on boolean flags`() {
        assertEquals("ai_companion", Message.SENDER_AI)
        assertEquals("text", Message.TYPE_TEXT)
        assertEquals("voice", Message.TYPE_VOICE)
        assertEquals("system", Message.TYPE_SYSTEM)

        val getSystem = Message::class.java.getMethod("isSystemMessage")
        val setSystem = Message::class.java.getMethod("setSystemMessage", Boolean::class.javaPrimitiveType)
        assertEquals("isSystemMessage", getSystem.getAnnotation(PropertyName::class.java)?.value)
        assertEquals("isSystemMessage", setSystem.getAnnotation(PropertyName::class.java)?.value)

        val getVoice = Message::class.java.getMethod("isVoiceNote")
        val setVoice = Message::class.java.getMethod("setVoiceNote", Boolean::class.javaPrimitiveType)
        assertEquals("isVoiceNote", getVoice.getAnnotation(PropertyName::class.java)?.value)
        assertEquals("isVoiceNote", setVoice.getAnnotation(PropertyName::class.java)?.value)
    }

    @Test
    fun `User omits rating and includes expected profile fields with safe defaults`() {
        val fieldNames = User::class.java.declaredFields.map { it.name }.toSet()
        assertFalse("User must not define rating field", fieldNames.contains("rating"))
        assertTrue(fieldNames.contains("photoUrl"))
        assertTrue(fieldNames.contains("fcmToken"))
        assertTrue(fieldNames.contains("tokenUpdatedAt"))
        assertTrue(fieldNames.contains("blockedUsers"))

        val defaultUser = User()
        assertNull(defaultUser.photoUrl)
        assertNull(defaultUser.fcmToken)
        assertEquals(0L, defaultUser.tokenUpdatedAt)
        assertTrue(defaultUser.blockedUsers.isEmpty())
    }
}
