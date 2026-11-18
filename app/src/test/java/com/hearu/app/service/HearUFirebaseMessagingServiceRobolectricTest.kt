package com.hearu.app.service

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.messaging.RemoteMessage
import com.hearu.app.R
import com.hearu.app.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HearUFirebaseMessagingServiceRobolectricTest {

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        if (FirebaseApp.getApps(app).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setApplicationId("1:100000000000:android:abcdef0123456789")
                .setApiKey("AIzaSyTestKeyForRobolectricUnitTests")
                .setProjectId("hearu-production")
                .build()
            FirebaseApp.initializeApp(app, options)
        }
    }

    @Test
    fun `onCreate creates all 3 notification channels with HIGH importance`() {
        val controller = Robolectric.buildService(HearUFirebaseMessagingService::class.java).create()
        val service = controller.get()
        val nm = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channels = listOf(
            HearUNotificationConfig.CHANNEL_CHAT_MESSAGES,
            HearUNotificationConfig.CHANNEL_SESSION_REQUESTS,
            HearUNotificationConfig.CHANNEL_SAFETY_ALERTS
        ).map { id -> nm.getNotificationChannel(id) }

        channels.forEach { channel ->
            assertNotNull(channel)
            assertEquals(NotificationManager.IMPORTANCE_HIGH, channel!!.importance)
        }
        controller.destroy()
    }

    @Test
    fun `onMessageReceived skips notification when POST_NOTIFICATIONS is denied on API 33`() {
        Shadows.shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        val controller = Robolectric.buildService(HearUFirebaseMessagingService::class.java).create()
        val service = controller.get()
        val nm = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancelAll()

        val msg = RemoteMessage.Builder("sender@fcm.googleapis.com")
            .setData(
                mapOf(
                    HearUNotificationConfig.EXTRA_NOTIFICATION_TYPE to HearUNotificationConfig.TYPE_CHAT_MESSAGE,
                    HearUNotificationConfig.EXTRA_SESSION_ID to "s1",
                    HearUNotificationConfig.EXTRA_USER_ID to "u2",
                    "message" to "Hello"
                )
            )
            .build()

        service.onMessageReceived(msg)

        assertTrue(Shadows.shadowOf(nm).allNotifications.isEmpty())
        controller.destroy()
    }

    @Test
    fun `onMessageReceived posts private notification with ic_notification and distinct IDs`() {
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        val controller = Robolectric.buildService(HearUFirebaseMessagingService::class.java).create()
        val service = controller.get()
        val nm = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancelAll()

        val chatMsg = RemoteMessage.Builder("sender@fcm.googleapis.com")
            .setData(
                mapOf(
                    HearUNotificationConfig.EXTRA_NOTIFICATION_TYPE to HearUNotificationConfig.TYPE_CHAT_MESSAGE,
                    HearUNotificationConfig.EXTRA_SESSION_ID to "s1",
                    "message" to "How are you?"
                )
            )
            .build()
        val crisisMsg = RemoteMessage.Builder("sender@fcm.googleapis.com")
            .setData(
                mapOf(
                    HearUNotificationConfig.EXTRA_NOTIFICATION_TYPE to HearUNotificationConfig.TYPE_CRISIS_ALERT,
                    HearUNotificationConfig.EXTRA_SESSION_ID to "s2",
                    "message" to "Crisis check"
                )
            )
            .build()

        service.onMessageReceived(chatMsg)
        service.onMessageReceived(crisisMsg)

        val notifications = Shadows.shadowOf(nm).allNotifications
        assertEquals(2, notifications.size)
        notifications.forEach { notification ->
            assertEquals(R.drawable.ic_notification, notification.smallIcon.resId)
            assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
        }
        val requestCodes = notifications.map {
            Shadows.shadowOf(it.contentIntent).requestCode
        }
        assertNotEquals(requestCodes[0], requestCodes[1])
        controller.destroy()
    }

    @Test
    fun `onNewToken updates repository when authenticated and onDestroy cancels scope`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val controller = Robolectric.buildService(HearUFirebaseMessagingService::class.java).create()
        val service = controller.get()

        val mockAuth: FirebaseAuth = mock()
        val mockUser: FirebaseUser = mock()
        val mockUserRepository: UserRepository = mock()
        service.auth = mockAuth
        service.userRepository = mockUserRepository
        service.serviceScope = TestScope(dispatcher)

        whenever(mockAuth.currentUser).thenReturn(null)
        service.onNewToken("tok_anon")
        testScheduler.advanceUntilIdle()
        verify(mockUserRepository, never()).updateFcmToken(any(), any())

        whenever(mockAuth.currentUser).thenReturn(mockUser)
        whenever(mockUser.uid).thenReturn("uid_1")
        service.onNewToken("tok_auth")
        testScheduler.advanceUntilIdle()
        verify(mockUserRepository).updateFcmToken("uid_1", "tok_auth")

        val scopeRef = service.serviceScope
        controller.destroy()
        assertFalse(scopeRef.isActive)
    }
}
