package com.hearu.app.e2e.tier5

import android.app.Application
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.hearu.app.ai.AiResult
import com.hearu.app.ai.GeminiAiService
import com.hearu.app.audio.VoiceNoteManager
import com.hearu.app.data.RolePreferences
import com.hearu.app.data.local.AppDatabase
import com.hearu.app.data.local.entity.MessageEntity
import com.hearu.app.e2e.harness.E2ETestHarness
import com.hearu.app.e2e.harness.MainDispatcherRule
import com.hearu.app.model.Message
import com.hearu.app.repository.AuthRepository
import com.hearu.app.repository.AuthResult
import com.hearu.app.repository.ChatRepository
import com.hearu.app.repository.ModerationRepository
import com.hearu.app.ui.auth.AuthState
import com.hearu.app.ui.auth.AuthViewModel
import com.hearu.app.ui.chat.AIChatViewModel
import com.hearu.app.ui.chat.ChatScreen
import com.hearu.app.ui.chat.ChatViewModel
import com.hearu.app.ui.chat.components.VoiceNoteBubble
import com.hearu.app.ui.dialogs.CrisisSupportDialog
import com.hearu.app.ui.theme.HearUTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h915dp")
class Tier5UiConcurrencyAndAccessibilityE2ETest {

    @get:Rule(order = 0)
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule(order = 1)
    val composeTestRule = createComposeRule()

    private fun contrastRatio(fg: Color, bg: Color): Float {
        val effectiveFg = if (fg.alpha < 1f) fg.compositeOver(bg) else fg
        val l1 = effectiveFg.luminance()
        val l2 = bg.luminance()
        return (max(l1, l2) + 0.05f) / (min(l1, l2) + 0.05f)
    }

    private fun stubVoiceNoteManager(): VoiceNoteManager {
        val vnm: VoiceNoteManager = mock()
        whenever(vnm.isRecording).thenReturn(MutableStateFlow(false))
        whenever(vnm.recordingDuration).thenReturn(MutableStateFlow(0))
        whenever(vnm.liveAmplitudes).thenReturn(MutableStateFlow(emptyList()))
        whenever(vnm.playingMessageId).thenReturn(MutableStateFlow(null))
        whenever(vnm.playbackProgress).thenReturn(MutableStateFlow(0f))
        whenever(vnm.isPlaying).thenReturn(MutableStateFlow(false))
        return vnm
    }

    // 1. AIChatViewModel: refreshQuota() vs sendMessage() stale quota clobber race
    @Test
    fun tier5_aiChatViewModel_slowInitRefreshQuota_doesNotClobberUpdatedQuota() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val geminiService: GeminiAiService = mock()
            val preferences: RolePreferences = mock()
            val auth: FirebaseAuth = mock()
            val user: FirebaseUser = mock()
            whenever(auth.currentUser).thenReturn(user)
            whenever(user.uid).thenReturn("uid_1")

            val initQuotaGate = CompletableDeferred<Int>()
            val calls = AtomicInteger(0)
            whenever(preferences.getAiMessagesUsedToday()).doSuspendableAnswer {
                when (calls.incrementAndGet()) {
                    1 -> initQuotaGate.await()
                    2 -> 49
                    else -> 50
                }
            }
            whenever(preferences.tryConsumeAiQuota(50)).thenReturn(true)
            whenever(geminiService.generateEmpatheticResponse(any<String>()))
                .thenReturn(AiResult.Success("Reply", isCrisisDetected = false))

            val vm = AIChatViewModel(geminiService, preferences, auth)
            testDispatcher.scheduler.runCurrent()

            vm.sendMessage("50th message")
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(50, vm.quotaUsed.value)
            assertTrue(vm.quotaExhausted.value)

            initQuotaGate.complete(49)
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals("Stale init refreshQuota must not overwrite quotaUsed=50", 50, vm.quotaUsed.value)
            assertTrue("Stale init refreshQuota must not clear quotaExhausted=true", vm.quotaExhausted.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    // 2. AuthViewModel: rapid double-submit & signOut while login in flight
    @Test
    fun tier5_authViewModel_rapidDoubleLogin_executesOnceAndSignOutCancelsInFlightAuth() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val authRepo: AuthRepository = mock()
            val rolePrefs: RolePreferences = mock()
            val user: FirebaseUser = mock()
            whenever(authRepo.currentUser).thenReturn(null)
            whenever(rolePrefs.activeRoleFlow).thenReturn(flowOf(null))
            whenever(rolePrefs.isOnboardingCompletedFlow).thenReturn(flowOf(true))
            whenever(rolePrefs.isBiometricEnabledFlow).thenReturn(flowOf(false))
            whenever(rolePrefs.isDarkThemeFlow).thenReturn(flowOf(null))
            whenever(rolePrefs.displayNameFlow).thenReturn(flowOf("KindSoul"))

            val gate = CompletableDeferred<AuthResult>()
            whenever(authRepo.signInWithEmail("a@b.com", "Password123!")).doSuspendableAnswer {
                gate.await()
            }

            val vm = AuthViewModel(authRepo, rolePrefs)
            vm.login("a@b.com", "Password123!")
            testDispatcher.scheduler.runCurrent()
            vm.login("a@b.com", "Password123!")
            testDispatcher.scheduler.runCurrent()

            assertEquals(AuthState.Loading, vm.authState.value)

            vm.signOut()
            assertEquals(AuthState.Idle, vm.authState.value)

            gate.complete(AuthResult.Success(user))
            testDispatcher.scheduler.advanceUntilIdle()

            verify(authRepo, times(1)).signInWithEmail("a@b.com", "Password123!")
            assertEquals(
                "Stale login completion after signOut must not restore Authenticated state",
                AuthState.Idle,
                vm.authState.value
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    // 3. ChatViewModel: session switch cancels stale consent & stops playback
    @Test
    fun tier5_chatViewModel_sessionSwitch_cancelsStaleConsentAndStopsPlayback() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val chatRepo: ChatRepository = mock()
            val auth: FirebaseAuth = mock()
            val user: FirebaseUser = mock()
            val vnm = stubVoiceNoteManager()
            whenever(auth.currentUser).thenReturn(user)
            whenever(user.uid).thenReturn("my_uid")
            whenever(chatRepo.getMessages(any())).thenReturn(flowOf(emptyList()))
            whenever(chatRepo.observeSession(any())).thenReturn(flowOf(null))

            val consentGate = CompletableDeferred<Boolean>()
            whenever(chatRepo.updatePhotoConsent("s1", true, true)).doSuspendableAnswer {
                consentGate.await()
            }

            val vm = ChatViewModel(chatRepo, auth, vnm)
            vm.joinSession("s1", "peer_1")
            testDispatcher.scheduler.runCurrent()

            vm.togglePhotoConsent()
            testDispatcher.scheduler.runCurrent()

            vm.joinSession("s2", "peer_2")
            verify(vnm).stopPlayback()

            consentGate.complete(true)
            testDispatcher.scheduler.advanceUntilIdle()

            assertFalse(
                "Stale consent response from s1 must not mutate s2 isPhotoRevealed",
                vm.isPhotoRevealed.value
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    // 4. ChatViewModel: concurrent sendMessage keeps isSending=true until all sends complete
    @Test
    fun tier5_chatViewModel_concurrentSendMessage_keepsIsSendingTrueUntilLastCompletes() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val chatRepo: ChatRepository = mock()
            val auth: FirebaseAuth = mock()
            val user: FirebaseUser = mock()
            val vnm = stubVoiceNoteManager()
            whenever(auth.currentUser).thenReturn(user)
            whenever(user.uid).thenReturn("u1")
            whenever(chatRepo.getMessages(any())).thenReturn(flowOf(emptyList()))
            whenever(chatRepo.observeSession(any())).thenReturn(flowOf(null))

            val gate1 = CompletableDeferred<Unit>()
            val gate2 = CompletableDeferred<Unit>()
            var count = 0
            whenever(chatRepo.sendMessage(any<String>(), any<Message>())).doSuspendableAnswer {
                count++
                if (count == 1) gate1.await() else gate2.await()
            }

            val vm = ChatViewModel(chatRepo, auth, vnm)
            vm.joinSession("s1", "p1")
            testDispatcher.scheduler.runCurrent()

            vm.sendMessage("msg1")
            testDispatcher.scheduler.runCurrent()
            vm.sendMessage("msg2")
            testDispatcher.scheduler.runCurrent()

            assertTrue(vm.isSending.value)
            gate1.complete(Unit)
            testDispatcher.scheduler.runCurrent()
            assertTrue("isSending must remain true while msg2 is still in flight", vm.isSending.value)

            gate2.complete(Unit)
            testDispatcher.scheduler.advanceUntilIdle()
            assertFalse(vm.isSending.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    // 5. Deep-link Chat moderation & null-auth bubble ownership safety
    @Test
    fun tier5_deepLinkChat_reportUserInfersPeerFromMessagesWhenSessionNotLoaded() {
        val chatRepo: ChatRepository = mock()
        val modRepo: ModerationRepository = mock()
        val auth: FirebaseAuth = mock()
        val user: FirebaseUser = mock()
        val vnm = stubVoiceNoteManager()
        whenever(auth.currentUser).thenReturn(user)
        whenever(user.uid).thenReturn("my_uid")
        whenever(chatRepo.getMessages("dl_sess")).thenReturn(
            flowOf(listOf(Message(id = "m1", senderId = "actual_peer_42", text = "Hi", timestamp = 1000L)))
        )
        whenever(chatRepo.observeSession("dl_sess")).thenReturn(flowOf(null))

        val vm = ChatViewModel(chatRepo, auth, vnm, modRepo)
        vm.joinSession("dl_sess", "my_uid")
        vm.submitReport("my_uid", "Harassment / Hate Speech", "Details")

        assertNull("Report should resolve peer from session messages when session snapshot is unavailable", vm.errorState.value)
    }

    @Test
    fun tier5_chatScreen_nullCurrentUser_doesNotMisalignPeerBubbleAsMine() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val db: AppDatabase = E2ETestHarness.createInMemoryDb(context)
        val dao = db.messageDao()
        val mockFirestore: FirebaseFirestore = mock()
        val mockSessions: CollectionReference = mock()
        val mockSessionDoc: DocumentReference = mock()
        val mockMsgsCol: CollectionReference = mock()
        val mockQuery: Query = mock()
        val mockReg: ListenerRegistration = mock()
        whenever(mockFirestore.collection("sessions")).thenReturn(mockSessions)
        whenever(mockSessions.document(any())).thenReturn(mockSessionDoc)
        whenever(mockSessionDoc.collection("messages")).thenReturn(mockMsgsCol)
        whenever(mockMsgsCol.orderBy("timestamp", Query.Direction.ASCENDING)).thenReturn(mockQuery)
        whenever(mockQuery.addSnapshotListener(any())).thenReturn(mockReg)
        whenever(mockSessionDoc.addSnapshotListener(any())).thenReturn(mockReg)

        val mockAuth: FirebaseAuth = mock()
        whenever(mockAuth.currentUser).thenReturn(null)
        val repo = ChatRepository(mockFirestore, dao)
        runBlocking(Dispatchers.IO) {
            dao.insertMessages(
                listOf(
                    MessageEntity(
                        id = "m1",
                        sessionId = "s_null_auth",
                        senderId = "peer_giver_1",
                        text = "Hello from peer",
                        timestamp = 1000L
                    )
                )
            )
        }
        val vm = ChatViewModel(repo, mockAuth, stubVoiceNoteManager())

        try {
            composeTestRule.setContent {
                HearUTheme {
                    ChatScreen(
                        sessionId = "s_null_auth",
                        userId = "peer_giver_1",
                        onNavigateBack = {},
                        viewModel = vm
                    )
                }
            }
            composeTestRule.waitUntil(timeoutMillis = 3_000L) { vm.messages.value.size == 1 }
            composeTestRule.waitForIdle()

            val peerBounds = composeTestRule.onNodeWithText("Hello from peer").fetchSemanticsNode().boundsInRoot
            assertTrue(
                "Peer bubble must stay left-aligned even when currentUser is null (bounds=$peerBounds)",
                peerBounds.left < 150f
            )
        } finally {
            repo.stopAllFirestoreSync()
            db.close()
        }
    }

    // 6. VoiceNoteBubble WCAG 1.4.11 contrast (>= 3.0:1) & >= 48dp touch targets
    @Test
    fun tier5_voiceNoteBubble_wcagNonTextContrastAndMinimumTouchTargets() {
        val voicePlayerSrc = java.io.File(
            E2ETestHarness.resolveProjectRoot(),
            "app/src/main/java/com/hearu/app/ui/chat/components/VoiceNotePlayer.kt"
        ).readText()
        val waveformBlock = voicePlayerSrc.substringAfter("// Waveform Bars")
        val mineAlpha = Regex("""onPrimary\.copy\(alpha\s*=\s*([0-9.]+)f\)""")
            .find(waveformBlock)?.groupValues?.get(1)?.toFloatOrNull() ?: 1.0f
        val peerAlpha = Regex("""onSurfaceVariant\.copy\(alpha\s*=\s*([0-9.]+)f\)""")
            .find(waveformBlock)?.groupValues?.get(1)?.toFloatOrNull() ?: 1.0f

        val ratios = mutableListOf<Pair<String, Float>>()
        composeTestRule.setContent {
            HearUTheme(darkTheme = false) {
                val c = MaterialTheme.colorScheme
                ratios += "light mine inactive wave (alpha=$mineAlpha)" to contrastRatio(c.onPrimary.copy(alpha = mineAlpha), c.primary)
                ratios += "light peer inactive wave (alpha=$peerAlpha)" to contrastRatio(c.onSurfaceVariant.copy(alpha = peerAlpha), c.surfaceVariant)
                VoiceNoteBubble(durationSeconds = 15, isMine = false)
            }
            HearUTheme(darkTheme = true) {
                val c = MaterialTheme.colorScheme
                ratios += "dark mine inactive wave (alpha=$mineAlpha)" to contrastRatio(c.onPrimary.copy(alpha = mineAlpha), c.primary)
                ratios += "dark peer inactive wave (alpha=$peerAlpha)" to contrastRatio(c.onSurfaceVariant.copy(alpha = peerAlpha), c.surfaceVariant)
            }
        }
        ratios.forEach { (label, ratio) ->
            assertTrue("$label contrast $ratio must be >= 3.0", ratio >= 3.0f)
        }

        val clickableNodes = composeTestRule.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)
        ).fetchSemanticsNodes()
        assertTrue(clickableNodes.isNotEmpty())
        clickableNodes.forEach { node ->
            val density = node.layoutInfo.density.density
            val widthDp = node.size.width / density
            val heightDp = node.size.height / density
            assertTrue(
                "Clickable node in VoiceNoteBubble has touch target ${widthDp}dp x ${heightDp}dp (< 48dp)",
                widthDp >= 48f && heightDp >= 48f
            )
        }
    }

    // 7. Compact landscape + fontScale = 2.0f scrollability on CrisisSupportDialog & ChatScreen empty state
    @Test
    @Config(qualifiers = "w480dp-h320dp-land")
    fun tier5_crisisSupportDialog_compactLandscapeLargeFont_isScrollable() {
        composeTestRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = baseDensity.density, fontScale = 2.0f)
            ) {
                HearUTheme {
                    CrisisSupportDialog(onDismiss = {})
                }
            }
        }
        composeTestRule.onNodeWithText("Chat with Lifeline Online")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Return to App").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w480dp-h320dp-land")
    fun tier5_chatScreen_emptyState_compactLandscapeLargeFont_isScrollable() {
        val chatRepo: ChatRepository = mock()
        whenever(chatRepo.getMessages(any())).thenReturn(flowOf(emptyList()))
        whenever(chatRepo.observeSession(any())).thenReturn(flowOf(null))
        val auth: FirebaseAuth = mock()
        val user: FirebaseUser = mock()
        whenever(auth.currentUser).thenReturn(user)
        whenever(user.uid).thenReturn("uid_self")
        val vm = ChatViewModel(chatRepo, auth, stubVoiceNoteManager())

        composeTestRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = baseDensity.density, fontScale = 2.0f)
            ) {
                HearUTheme {
                    ChatScreen(sessionId = "s_empty", userId = "peer_1", onNavigateBack = {}, viewModel = vm)
                }
            }
        }
        composeTestRule.onNodeWithText("Your Safe Space is Connected")
            .performScrollTo()
            .assertIsDisplayed()
    }
}
