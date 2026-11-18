package com.hearu.app.e2e.tier1_2

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ActivityScenario
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.messaging.RemoteMessage
import com.hearu.app.HearUApplication
import com.hearu.app.MainActivity
import com.hearu.app.R
import com.hearu.app.audio.VoiceNoteManager
import com.hearu.app.data.RolePreferences
import com.hearu.app.e2e.harness.E2ETestHarness
import com.hearu.app.e2e.harness.MainDispatcherRule
import com.hearu.app.model.Message
import com.hearu.app.navigation.HearUNavigation
import com.hearu.app.repository.AuthRepository
import com.hearu.app.repository.ChatRepository
import com.hearu.app.repository.UserRepository
import com.hearu.app.service.HearUFirebaseMessagingService
import com.hearu.app.service.HearUNotificationConfig
import com.hearu.app.ui.auth.AuthViewModel
import com.hearu.app.ui.chat.ChatScreen
import com.hearu.app.ui.chat.ChatViewModel
import com.hearu.app.ui.theme.HearUTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HearUApplication::class, sdk = [34], qualifiers = "w411dp-h915dp")
class NotificationsDeepLinkE2ETest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private lateinit var appContext: HearUApplication
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setUp() {
        appContext = RuntimeEnvironment.getApplication() as HearUApplication
        E2ETestHarness.ensureFirebaseInitialized(appContext)
        runBlocking { E2ETestHarness.clearRolePreferences(appContext) }

        Shadows.shadowOf(appContext).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notificationManager =
            appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()
    }

    @After
    fun tearDown() {
        runBlocking { E2ETestHarness.clearRolePreferences(appContext) }
        notificationManager.cancelAll()
    }

    private fun stripKotlinComments(source: String): String =
        source
            .replace(Regex("""/\*[\s\S]*?\*/"""), "")
            .replace(Regex("""//[^\n]*"""), "")

    private fun buildService(): HearUFirebaseMessagingService {
        return Robolectric.buildService(HearUFirebaseMessagingService::class.java).create().get()
    }

    // ==================== TIER 1: HAPPY PATH ====================

    @Test
    fun tier1_createNotificationChannels_registersAllThreeHighImportanceChannels() {
        HearUFirebaseMessagingService.createNotificationChannels(appContext)

        val chatChannel =
            notificationManager.getNotificationChannel(HearUNotificationConfig.CHANNEL_CHAT_MESSAGES)
        val sessionChannel =
            notificationManager.getNotificationChannel(HearUNotificationConfig.CHANNEL_SESSION_REQUESTS)
        val safetyChannel =
            notificationManager.getNotificationChannel(HearUNotificationConfig.CHANNEL_SAFETY_ALERTS)

        assertNotNull(chatChannel)
        assertNotNull(sessionChannel)
        assertNotNull(safetyChannel)

        assertEquals(NotificationManager.IMPORTANCE_HIGH, chatChannel!!.importance)
        assertEquals("Chat Messages", chatChannel.name.toString())

        assertEquals(NotificationManager.IMPORTANCE_HIGH, sessionChannel!!.importance)
        assertEquals("Session Requests", sessionChannel.name.toString())

        assertEquals(NotificationManager.IMPORTANCE_HIGH, safetyChannel!!.importance)
        assertEquals("Safety & Crisis Alerts", safetyChannel.name.toString())
    }

    @Test
    fun tier1_onMessageReceived_routesAllThreeTypesToExpectedChannels() {
        val service = buildService()
        val shadowNm = Shadows.shadowOf(notificationManager)

        val cases = listOf(
            HearUNotificationConfig.TYPE_CHAT_MESSAGE to HearUNotificationConfig.CHANNEL_CHAT_MESSAGES,
            HearUNotificationConfig.TYPE_SESSION_REQUEST to HearUNotificationConfig.CHANNEL_SESSION_REQUESTS,
            HearUNotificationConfig.TYPE_CRISIS_ALERT to HearUNotificationConfig.CHANNEL_SAFETY_ALERTS
        )

        cases.forEachIndexed { index, (type, expectedChannel) ->
            notificationManager.cancelAll()
            val msg = RemoteMessage.Builder("sender@fcm.googleapis.com")
                .setData(
                    mapOf(
                        HearUNotificationConfig.EXTRA_NOTIFICATION_TYPE to type,
                        HearUNotificationConfig.EXTRA_SESSION_ID to "sess_$index",
                        HearUNotificationConfig.EXTRA_USER_ID to "user_$index"
                    )
                )
                .build()

            service.onMessageReceived(msg)

            val posted = shadowNm.allNotifications
            assertEquals("Expected 1 notification for type=$type", 1, posted.size)
            assertEquals(expectedChannel, posted.first().channelId)
        }
    }

    @Test
    fun tier1_onMessageReceived_usesMonochromeDrawableSmallIconAndPrivateVisibility() {
        val service = buildService()
        val shadowNm = Shadows.shadowOf(notificationManager)

        val chatMsg = RemoteMessage.Builder("sender@fcm.googleapis.com")
            .setData(
                mapOf(
                    HearUNotificationConfig.EXTRA_NOTIFICATION_TYPE to HearUNotificationConfig.TYPE_CHAT_MESSAGE,
                    HearUNotificationConfig.EXTRA_SESSION_ID to "sess_vis_1"
                )
            )
            .build()
        service.onMessageReceived(chatMsg)

        val chatNotification = shadowNm.allNotifications.single()
        assertEquals(NotificationCompat.VISIBILITY_PRIVATE, chatNotification.visibility)
        val smallIcon = chatNotification.smallIcon
        assertNotNull(smallIcon)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            assertEquals(Icon.TYPE_RESOURCE, smallIcon.type)
            val resId = smallIcon.resId
            assertNotEquals(R.mipmap.ic_launcher, resId)
            assertEquals("drawable", appContext.resources.getResourceTypeName(resId))
            assertTrue(
                "Expected monochrome notification icon drawable, got: ${appContext.resources.getResourceEntryName(resId)}",
                appContext.resources.getResourceEntryName(resId)
                    .contains("notification", ignoreCase = true)
            )
        }

        notificationManager.cancelAll()
        val crisisMsg = RemoteMessage.Builder("sender@fcm.googleapis.com")
            .setData(
                mapOf(
                    HearUNotificationConfig.EXTRA_NOTIFICATION_TYPE to HearUNotificationConfig.TYPE_CRISIS_ALERT,
                    HearUNotificationConfig.EXTRA_SESSION_ID to "sess_vis_2"
                )
            )
            .build()
        service.onMessageReceived(crisisMsg)

        assertEquals(
            NotificationCompat.VISIBILITY_PRIVATE,
            shadowNm.allNotifications.single().visibility
        )
    }

    @Test
    fun tier1_onMessageReceived_prefersRemoteNotificationTitleAndBodyWhenPresent() {
        val service = buildService()
        val shadowNm = Shadows.shadowOf(notificationManager)

        val bundle = Bundle().apply {
            putString("gcm.n.e", "1")
            putString("gcm.n.title", "Listener Matched")
            putString("gcm.n.body", "Your peer is ready to listen")
            putString(
                HearUNotificationConfig.EXTRA_NOTIFICATION_TYPE,
                HearUNotificationConfig.TYPE_SESSION_REQUEST
            )
            putString(HearUNotificationConfig.EXTRA_SESSION_ID, "sess_101")
        }
        service.onMessageReceived(RemoteMessage(bundle))

        val posted = shadowNm.allNotifications.single()
        assertEquals("Listener Matched", posted.extras.getString(NotificationCompat.EXTRA_TITLE))
        assertEquals(
            "Your peer is ready to listen",
            posted.extras.getString(NotificationCompat.EXTRA_TEXT)
        )
    }

    @Test
    fun tier1_onMessageReceived_attachesImmutableMainActivityPendingIntentWithExtras() {
        val service = buildService()
        val shadowNm = Shadows.shadowOf(notificationManager)

        val msg = RemoteMessage.Builder("sender@fcm.googleapis.com")
            .setData(
                mapOf(
                    HearUNotificationConfig.EXTRA_NOTIFICATION_TYPE to HearUNotificationConfig.TYPE_SESSION_REQUEST,
                    HearUNotificationConfig.EXTRA_SESSION_ID to "sess_pi_1",
                    HearUNotificationConfig.EXTRA_USER_ID to "giver_88"
                )
            )
            .build()
        service.onMessageReceived(msg)

        val notification = shadowNm.allNotifications.single()
        assertNotNull(notification.contentIntent)
        assertTrue(notification.flags and Notification.FLAG_AUTO_CANCEL != 0)

        val shadowPi = Shadows.shadowOf(notification.contentIntent)
        assertTrue(shadowPi.isActivityIntent)
        assertTrue(shadowPi.flags and PendingIntent.FLAG_IMMUTABLE != 0)

        val savedIntent = shadowPi.savedIntent
        assertEquals(MainActivity::class.java.name, savedIntent.component?.className)
        assertTrue(savedIntent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        assertTrue(savedIntent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        assertEquals(
            HearUNotificationConfig.TYPE_SESSION_REQUEST,
            savedIntent.getStringExtra(HearUNotificationConfig.EXTRA_NOTIFICATION_TYPE)
        )
        assertEquals(
            "sess_pi_1",
            savedIntent.getStringExtra(HearUNotificationConfig.EXTRA_SESSION_ID)
        )
        assertEquals(
            "giver_88",
            savedIntent.getStringExtra(HearUNotificationConfig.EXTRA_USER_ID)
        )
    }

    @Test
    fun tier1_onNewToken_syncsTokenToUserRepositoryForAuthenticatedUser() {
        runBlocking {
            val serviceController =
                Robolectric.buildService(HearUFirebaseMessagingService::class.java).create()
            try {
                val service = serviceController.get()
                val mockAuth = mock<FirebaseAuth>()
                val mockUser = mock<FirebaseUser>()
                val mockUserRepo = mock<UserRepository>()
                val synced = CompletableDeferred<Pair<String, String>>()

                whenever(mockAuth.currentUser).thenReturn(mockUser)
                whenever(mockUser.uid).thenReturn("uid_fcm_1")
                whenever(mockUserRepo.updateFcmToken(any(), any())).doSuspendableAnswer { invocation ->
                    val uid = invocation.getArgument<String>(0)
                    val tok = invocation.getArgument<String>(1)
                    synced.complete(uid to tok)
                    Unit
                }

                service.auth = mockAuth
                service.userRepository = mockUserRepo

                ShadowLog.clear()
                service.onNewToken("token_abc_999")

                val result = withTimeout(3_000L) { synced.await() }
                assertEquals("uid_fcm_1" to "token_abc_999", result)
                verify(mockUserRepo).updateFcmToken("uid_fcm_1", "token_abc_999")
                assertFalse(
                    "Raw FCM token must NEVER be logged to Logcat (PROJECT.md #10)",
                    ShadowLog.getLogs().any { it.msg?.contains("token_abc_999") == true }
                )
            } finally {
                serviceController.destroy()
            }
        }
    }

    @Test
    fun tier1_authenticatedDeepLink_navigatesToChatAndBackPopsToDashboard() {
        val rolePreferences = RolePreferences(appContext)
        runBlocking {
            rolePreferences.setOnboardingCompleted(true)
            rolePreferences.setActiveRole("seeker")
        }

        val mockAuthRepo = mock<AuthRepository>()
        val mockUser = mock<FirebaseUser>()
        whenever(mockUser.uid).thenReturn("real_uid_77")
        whenever(mockAuthRepo.currentUser).thenReturn(mockUser)

        val authVm = AuthViewModel(mockAuthRepo, rolePreferences)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    HearUTheme {
                        HearUNavigation(authViewModel = authVm, initialSessionId = "dl_sess_77")
                    }
                }
            }

            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                composeTestRule.onAllNodesWithText("Empathetic Listener")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            composeTestRule.onNodeWithText("Empathetic Listener").assertIsDisplayed()

            composeTestRule.onNodeWithContentDescription("Back").performClick()

            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                composeTestRule.onAllNodesWithText("Connect with a Human Listener")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            composeTestRule.onNodeWithText("Connect with a Human Listener").assertIsDisplayed()
            composeTestRule.onNodeWithText("Empathetic Listener").assertDoesNotExist()

            // Trigger recomposition on SeekerDashboard and verify deep link does not re-open
            composeTestRule.onNodeWithText("Anxiety 😰").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("Empathetic Listener").assertDoesNotExist()
        }
    }

    // ==================== TIER 2: BOUNDARY / CORNER / ERROR ====================

    @Test
    fun tier2_postNotificationsPermission_declaredInManifestAndGatedOnApi33Plus() {
        @Suppress("DEPRECATION")
        val packageInfo = appContext.packageManager.getPackageInfo(
            appContext.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val requested = packageInfo.requestedPermissions?.toList().orEmpty()
        assertTrue(requested.contains(Manifest.permission.POST_NOTIFICATIONS))

        Shadows.shadowOf(appContext).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val service = buildService()
        val msg = RemoteMessage.Builder("sender@fcm.googleapis.com")
            .setData(
                mapOf(
                    HearUNotificationConfig.EXTRA_NOTIFICATION_TYPE to HearUNotificationConfig.TYPE_CHAT_MESSAGE,
                    HearUNotificationConfig.EXTRA_SESSION_ID to "sess_denied"
                )
            )
            .build()

        service.onMessageReceived(msg)

        val shadowNm = Shadows.shadowOf(notificationManager)
        assertTrue(
            "Expected 0 notifications posted when POST_NOTIFICATIONS is denied",
            shadowNm.allNotifications.isEmpty()
        )
    }

    @Test
    fun tier2_rapidConsecutiveMessages_produceDistinctNotificationIdsAndRequestCodes() {
        val service = buildService()
        val shadowNm = Shadows.shadowOf(notificationManager)

        val msg1 = RemoteMessage.Builder("sender@fcm.googleapis.com")
            .setData(
                mapOf(
                    HearUNotificationConfig.EXTRA_NOTIFICATION_TYPE to HearUNotificationConfig.TYPE_CHAT_MESSAGE,
                    HearUNotificationConfig.EXTRA_SESSION_ID to "sess_rapid_1"
                )
            )
            .build()
        val msg2 = RemoteMessage.Builder("sender@fcm.googleapis.com")
            .setData(
                mapOf(
                    HearUNotificationConfig.EXTRA_NOTIFICATION_TYPE to HearUNotificationConfig.TYPE_CHAT_MESSAGE,
                    HearUNotificationConfig.EXTRA_SESSION_ID to "sess_rapid_2"
                )
            )
            .build()

        service.onMessageReceived(msg1)
        service.onMessageReceived(msg2)

        val posted = shadowNm.allNotifications
        assertEquals(
            "Rapid consecutive notifications must not overwrite each other",
            2,
            posted.size
        )

        val rc1 = Shadows.shadowOf(posted[0].contentIntent).requestCode
        val rc2 = Shadows.shadowOf(posted[1].contentIntent).requestCode
        assertNotEquals("PendingIntent requestCodes must be distinct", rc1, rc2)
    }

    @Test
    fun tier2_dataOnlyPayloadWithMissingOptionalFields_fallsBackSafely() {
        val service = buildService()
        val shadowNm = Shadows.shadowOf(notificationManager)

        service.onMessageReceived(RemoteMessage.Builder("sender@fcm.googleapis.com").build())

        val fallback = shadowNm.allNotifications.single()
        assertEquals(HearUNotificationConfig.CHANNEL_CHAT_MESSAGES, fallback.channelId)
        assertEquals("HearU Message", fallback.extras.getString(NotificationCompat.EXTRA_TITLE))
        assertEquals(
            "You have a new update in HearU.",
            fallback.extras.getString(NotificationCompat.EXTRA_TEXT)
        )
        assertEquals(
            "",
            Shadows.shadowOf(fallback.contentIntent).savedIntent
                .getStringExtra(HearUNotificationConfig.EXTRA_SESSION_ID)
        )

        notificationManager.cancelAll()
        val unknownTypeMsg = RemoteMessage.Builder("sender@fcm.googleapis.com")
            .setData(mapOf(HearUNotificationConfig.EXTRA_NOTIFICATION_TYPE to "unknown_type"))
            .build()
        service.onMessageReceived(unknownTypeMsg)
        assertEquals(
            HearUNotificationConfig.CHANNEL_CHAT_MESSAGES,
            shadowNm.allNotifications.single().channelId
        )
    }

    @Test
    fun tier2_blankOrNullSessionId_doesNotDeepLinkIntoChat() {
        val rolePreferences = RolePreferences(appContext)
        runBlocking {
            rolePreferences.setOnboardingCompleted(true)
            rolePreferences.setActiveRole("seeker")
        }

        val mockAuthRepo = mock<AuthRepository>()
        val mockUser = mock<FirebaseUser>()
        whenever(mockUser.uid).thenReturn("real_uid_blank")
        whenever(mockAuthRepo.currentUser).thenReturn(mockUser)

        val authVm = AuthViewModel(mockAuthRepo, rolePreferences)
        listOf<String?>(null, "   ").forEach { candidateSessionId ->
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.setContent {
                        HearUTheme {
                            HearUNavigation(
                                authViewModel = authVm,
                                initialSessionId = candidateSessionId
                            )
                        }
                    }
                }

                composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                    composeTestRule.onAllNodesWithText("Connect with a Human Listener")
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
                composeTestRule.onNodeWithText("Connect with a Human Listener").assertIsDisplayed()
                composeTestRule.onNodeWithText("Empathetic Listener").assertDoesNotExist()
            }
        }

        val service = buildService()
        service.onMessageReceived(
            RemoteMessage.Builder("sender@fcm.googleapis.com")
                .setData(mapOf(HearUNotificationConfig.EXTRA_SESSION_ID to "  "))
                .build()
        )
        assertEquals(1, Shadows.shadowOf(notificationManager).allNotifications.size)
    }

    @Test
    fun tier2_unauthenticatedMainActivityLaunchWithSessionExtra_blocksChatAccess() {
        FirebaseAuth.getInstance().signOut()
        val rolePreferences = RolePreferences(appContext)
        runBlocking { rolePreferences.setOnboardingCompleted(true) }

        val launchIntent = Intent(appContext, MainActivity::class.java).apply {
            putExtra(HearUNotificationConfig.EXTRA_SESSION_ID, "secret_sess_9")
        }
        ActivityScenario.launch<MainActivity>(launchIntent).use {
            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                composeTestRule.onAllNodesWithText("Welcome to HearU")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            composeTestRule.onNodeWithText("Welcome to HearU").assertIsDisplayed()
            composeTestRule.onNodeWithText("Your Safe Space is Connected").assertDoesNotExist()
            composeTestRule.onNodeWithText("Empathetic Listener").assertDoesNotExist()
        }
    }

    @Test
    fun tier2_deepLinkChatAndNavContract_usesRealUidInsteadOfPeerAndConsumesSessionId() {
        val mockChatRepo = mock<ChatRepository>()
        whenever(mockChatRepo.getMessages("dl_1")).thenReturn(
            flowOf(
                listOf(
                    Message(id = "1", senderId = "peer_99", text = "Peer hello", timestamp = 1000L),
                    Message(id = "2", senderId = "real_uid_123", text = "Hi", timestamp = 2000L)
                )
            )
        )
        val mockAuth = mock<FirebaseAuth>()
        val mockUser = mock<FirebaseUser>()
        whenever(mockAuth.currentUser).thenReturn(mockUser)
        whenever(mockUser.uid).thenReturn("real_uid_123")

        val mockVoiceNoteManager = mock<VoiceNoteManager>()
        whenever(mockVoiceNoteManager.isRecording).thenReturn(MutableStateFlow(false))
        whenever(mockVoiceNoteManager.recordingDuration).thenReturn(MutableStateFlow(0))
        whenever(mockVoiceNoteManager.liveAmplitudes).thenReturn(MutableStateFlow(emptyList()))
        whenever(mockVoiceNoteManager.playingMessageId).thenReturn(MutableStateFlow(null))
        whenever(mockVoiceNoteManager.playbackProgress).thenReturn(MutableStateFlow(0f))
        whenever(mockVoiceNoteManager.isPlaying).thenReturn(MutableStateFlow(false))

        val realChatVm = ChatViewModel(mockChatRepo, mockAuth, mockVoiceNoteManager)

        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    HearUTheme {
                        ChatScreen(
                            sessionId = "dl_1",
                            userId = "real_uid_123",
                            onNavigateBack = {},
                            viewModel = realChatVm
                        )
                    }
                }
            }

            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                composeTestRule.onAllNodesWithText("Peer hello").fetchSemanticsNodes().isNotEmpty() &&
                    composeTestRule.onAllNodesWithText("Hi").fetchSemanticsNodes().isNotEmpty()
            }
            val peerBounds =
                composeTestRule.onNodeWithText("Peer hello").fetchSemanticsNode().boundsInRoot
            val myBounds = composeTestRule.onNodeWithText("Hi").fetchSemanticsNode().boundsInRoot
            assertTrue(
                "Expected own deep-link chat message aligned right of peer message",
                myBounds.left > peerBounds.left && myBounds.right > peerBounds.right
            )
        }

        val root = E2ETestHarness.resolveProjectRoot()
        val navSource =
            File(root, "app/src/main/java/com/hearu/app/navigation/HearUNavigation.kt").readText()
        val mainSource =
            File(root, "app/src/main/java/com/hearu/app/MainActivity.kt").readText()
        val navCode = stripKotlinComments(navSource)
        val mainCode = stripKotlinComments(mainSource)

        assertFalse(
            "HearUNavigation must pass authenticated user UID instead of hardcoded \"peer\"",
            Regex("""createRoute\([^)]*"peer"[^)]*\)""").containsMatchIn(navCode)
        )
        assertTrue(
            "Deep-link sessionId must be consumed once (cleared or tracked as consumed)",
            Regex("""\b\w*[Ss]essionId\w*\s*=\s*null\b""").containsMatchIn(mainCode) ||
                mainCode.contains("removeExtra(") ||
                Regex("""consumed.*session|onDeepLinkConsumed|onSessionConsumed""", RegexOption.IGNORE_CASE)
                    .containsMatchIn("$mainCode\n$navCode")
        )
    }

    @Test
    fun tier2_mainActivityOnNewIntent_handlesValidBlankAndDestroySafely() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            controller.newIntent(
                Intent(appContext, MainActivity::class.java).apply {
                    putExtra(HearUNotificationConfig.EXTRA_SESSION_ID, "sess_new_5")
                }
            )
            assertEquals(
                "sess_new_5",
                controller.get().intent.getStringExtra(HearUNotificationConfig.EXTRA_SESSION_ID)
            )

            controller.newIntent(
                Intent(appContext, MainActivity::class.java).apply {
                    putExtra(HearUNotificationConfig.EXTRA_SESSION_ID, "  ")
                }
            )
        } finally {
            controller.pause().stop().destroy()
        }

        val serviceController =
            Robolectric.buildService(HearUFirebaseMessagingService::class.java).create()
        serviceController.destroy()
    }

    @Test
    fun tier2_onNewToken_skipsUnauthenticatedAndSwallowsRepositoryFailure() {
        runBlocking {
            val serviceController =
                Robolectric.buildService(HearUFirebaseMessagingService::class.java).create()
            try {
                val service = serviceController.get()

                val mockAuth = mock<FirebaseAuth>()
                val mockUserRepo = mock<UserRepository>()
                service.auth = mockAuth
                service.userRepository = mockUserRepo

                whenever(mockAuth.currentUser).thenReturn(null)
                ShadowLog.clear()
                service.onNewToken("tok_unauth")
                verifyNoInteractions(mockUserRepo)
                assertFalse(
                    "Raw FCM token must NEVER be logged to Logcat (PROJECT.md #10)",
                    ShadowLog.getLogs().any { it.msg?.contains("tok_unauth") == true }
                )

                val mockUser = mock<FirebaseUser>()
                val failedAttempt = CompletableDeferred<Unit>()
                whenever(mockAuth.currentUser).thenReturn(mockUser)
                whenever(mockUser.uid).thenReturn("uid_err")
                whenever(mockUserRepo.updateFcmToken(any(), any())).doSuspendableAnswer {
                    failedAttempt.complete(Unit)
                    throw RuntimeException("Firestore offline")
                }

                ShadowLog.clear()
                service.onNewToken("tok_err")
                withTimeout(3_000L) { failedAttempt.await() }
                assertFalse(
                    "Raw FCM token must NEVER be logged to Logcat (PROJECT.md #10)",
                    ShadowLog.getLogs().any { it.msg?.contains("tok_err") == true }
                )
            } finally {
                serviceController.destroy()
            }
        }
    }
}
