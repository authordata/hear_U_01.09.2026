package com.hearu.app.e2e.tier1_2

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.hearu.app.R
import com.hearu.app.audio.VoiceNoteManager
import com.hearu.app.e2e.harness.E2ETestHarness
import com.hearu.app.e2e.harness.MainDispatcherRule
import com.hearu.app.model.ChatSession
import com.hearu.app.repository.ChatRepository
import com.hearu.app.repository.ModerationRepository
import com.hearu.app.ui.chat.ChatScreen
import com.hearu.app.ui.chat.ChatViewModel
import com.hearu.app.ui.dialogs.ReportUserDialog
import com.hearu.app.ui.theme.HearUTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.io.File

/**
 * Opaque-box E2E tests for Features F9 (Photo Blur / Mutual Reveal Consent)
 * and F13 (Moderation: Report User & Block & Leave).
 * F9: 6 Tier 1 + 5 Tier 2 = 11 tests.
 * F13: 6 Tier 1 + 6 Tier 2 = 12 tests.
 * Total: 23 tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ModerationAndPhotoE2ETest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var mockAuth: FirebaseAuth
    private lateinit var mockUser: FirebaseUser
    private lateinit var mockChatRepository: ChatRepository
    private lateinit var mockVoiceNoteManager: VoiceNoteManager
    private lateinit var mockFirestore: FirebaseFirestore
    private lateinit var mockReportsCollection: CollectionReference
    private lateinit var mockUsersCollection: CollectionReference
    private lateinit var mockUserDoc: DocumentReference
    private lateinit var moderationRepository: ModerationRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockAuth = mock()
        mockUser = mock()
        mockChatRepository = mock()
        mockVoiceNoteManager = createStubbedVoiceNoteManager()
        mockFirestore = mock()
        mockReportsCollection = mock()
        mockUsersCollection = mock()
        mockUserDoc = mock()

        whenever(mockAuth.currentUser).thenReturn(mockUser)
        whenever(mockUser.uid).thenReturn("seeker_1")
        whenever(mockChatRepository.getMessages(any())).thenReturn(flowOf(emptyList()))
        runBlocking {
            whenever(mockChatRepository.sendMessage(any(), any())).thenReturn(Unit)
        }

        whenever(mockFirestore.collection("reports")).thenReturn(mockReportsCollection)
        whenever(mockFirestore.collection("users")).thenReturn(mockUsersCollection)
        whenever(mockUsersCollection.document(any())).thenReturn(mockUserDoc)

        moderationRepository = ModerationRepository(mockFirestore)
    }

    private fun createStubbedVoiceNoteManager(): VoiceNoteManager {
        val manager: VoiceNoteManager = mock()
        whenever(manager.isRecording).thenReturn(MutableStateFlow(false))
        whenever(manager.recordingDuration).thenReturn(MutableStateFlow(0))
        whenever(manager.liveAmplitudes).thenReturn(MutableStateFlow(emptyList()))
        whenever(manager.playingMessageId).thenReturn(MutableStateFlow(null))
        whenever(manager.playbackProgress).thenReturn(MutableStateFlow(0f))
        whenever(manager.isPlaying).thenReturn(MutableStateFlow(false))
        return manager
    }

    private fun createChatViewModel(): ChatViewModel =
        ChatViewModel(mockChatRepository, mockAuth, mockVoiceNoteManager)

    private fun drainUi() {
        ShadowLooper.idleMainLooper()
        mainDispatcherRule.testDispatcher.scheduler.runCurrent()
        ShadowLooper.idleMainLooper()
        composeTestRule.waitForIdle()
    }

    // =========================================================================
    // Feature F9 — Tier 1: Happy Path (6 Tests)
    // =========================================================================

    @Test
    fun f9_tier1_photoBlurredByDefaultOnChatStart() {
        val vm = createChatViewModel()

        composeTestRule.setContent {
            HearUTheme(darkTheme = false) {
                ChatScreen(
                    sessionId = "s1",
                    userId = "seeker_1",
                    onNavigateBack = {},
                    viewModel = vm
                )
            }
        }
        drainUi()

        assertFalse(vm.isPhotoRevealed.value)
        assertFalse(vm.myPhotoConsent.value)
        composeTestRule.onNodeWithText("End-to-end ephemeral peer chat").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Peer Avatar").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Photo Reveal Consent").assertIsDisplayed()
        composeTestRule.onNodeWithText("Photos unblurred (mutual consent)").assertDoesNotExist()
    }

    @Test
    fun f9_tier1_topBarButtonOpensMutualPhotoConsentDialog() {
        val vm = createChatViewModel()

        composeTestRule.setContent {
            HearUTheme(darkTheme = false) {
                ChatScreen(
                    sessionId = "s1",
                    userId = "seeker_1",
                    onNavigateBack = {},
                    viewModel = vm
                )
            }
        }
        drainUi()

        composeTestRule.onNodeWithContentDescription("Photo Reveal Consent").performClick()
        drainUi()

        composeTestRule.onNodeWithText("Mutual Photo Reveal Consent").assertIsDisplayed()
        composeTestRule.onNodeWithText("Grant Mutual Consent").assertIsDisplayed()
        composeTestRule.onNodeWithText("Close").assertIsDisplayed()
    }

    @Test
    fun f9_tier1_overflowMenuOpensMutualPhotoConsentDialog() {
        val vm = createChatViewModel()

        composeTestRule.setContent {
            HearUTheme(darkTheme = false) {
                ChatScreen(
                    sessionId = "s1",
                    userId = "seeker_1",
                    onNavigateBack = {},
                    viewModel = vm
                )
            }
        }
        drainUi()

        composeTestRule.onNodeWithContentDescription("Options").performClick()
        drainUi()
        composeTestRule.onNodeWithText("Mutual Photo Reveal Consent").performClick()
        drainUi()

        composeTestRule.onNodeWithText("Mutual Photo Reveal Consent").assertIsDisplayed()
        composeTestRule.onNodeWithText("Grant Mutual Consent").assertIsDisplayed()
    }

    @Test
    fun f9_tier1_grantingConsentUpdatesMyPhotoConsentAndMenuLabel() {
        val vm = createChatViewModel()

        composeTestRule.setContent {
            HearUTheme(darkTheme = false) {
                ChatScreen(
                    sessionId = "s1",
                    userId = "seeker_1",
                    onNavigateBack = {},
                    viewModel = vm
                )
            }
        }
        drainUi()

        composeTestRule.onNodeWithContentDescription("Photo Reveal Consent").performClick()
        drainUi()
        composeTestRule.onNodeWithText("Grant Mutual Consent").performClick()
        drainUi()

        assertTrue(vm.myPhotoConsent.value)

        composeTestRule.onNodeWithContentDescription("Options").performClick()
        drainUi()
        composeTestRule.onNodeWithText("Revoke Photo Consent").assertIsDisplayed()
    }

    @Test
    fun f9_tier1_revokingConsentResetsConsentAndRestoresBlurredSubtitle() {
        val vm = createChatViewModel()

        composeTestRule.setContent {
            HearUTheme(darkTheme = false) {
                ChatScreen(
                    sessionId = "s1",
                    userId = "seeker_1",
                    onNavigateBack = {},
                    viewModel = vm
                )
            }
        }
        drainUi()

        composeTestRule.onNodeWithContentDescription("Photo Reveal Consent").performClick()
        composeTestRule.onNodeWithText("Grant Mutual Consent").performClick()
        drainUi()
        assertTrue(vm.myPhotoConsent.value)

        composeTestRule.onNodeWithContentDescription("Photo Reveal Consent").performClick()
        drainUi()
        composeTestRule.onNodeWithText("Revoke Consent (Re-blur)").performClick()
        drainUi()

        assertFalse(vm.myPhotoConsent.value)
        assertFalse(vm.isPhotoRevealed.value)
        composeTestRule.onNodeWithText("End-to-end ephemeral peer chat").assertIsDisplayed()
    }

    @Test
    fun f9_tier1_chatSessionAndFirestoreRulesAllowParticipantIdentityRevealedUpdate() {
        val session = ChatSession(sessionId = "s1", seekerId = "u1", giverId = "u2")
        assertFalse(session.identityRevealed)

        val rulesContent = File(E2ETestHarness.resolveProjectRoot(), "firestore.rules").readText()
        val sessionsBlock = rulesContent.substringAfter("match /sessions/{sessionId}")
            .substringBefore("match /messages/{messageId}")

        assertTrue(
            "Session update must check participant ownership",
            sessionsBlock.contains("resource.data.seekerId == request.auth.uid") &&
                sessionsBlock.contains("resource.data.giverId == request.auth.uid")
        )
        assertTrue(
            "Session update must restrict affectedKeys via hasOnly including status and identityRevealed",
            sessionsBlock.contains(".affectedKeys()") &&
                sessionsBlock.contains("hasOnly") &&
                sessionsBlock.contains("'status'") &&
                sessionsBlock.contains("'identityRevealed'")
        )
    }

    // =========================================================================
    // Feature F9 — Tier 2: Boundary / Corner / Error (5 Tests)
    // =========================================================================

    @Test
    fun f9_tier2_dismissingConsentDialogViaCloseDoesNotChangeConsentState() {
        val vm = createChatViewModel()

        composeTestRule.setContent {
            HearUTheme(darkTheme = false) {
                ChatScreen(
                    sessionId = "s1",
                    userId = "seeker_1",
                    onNavigateBack = {},
                    viewModel = vm
                )
            }
        }
        drainUi()

        composeTestRule.onNodeWithContentDescription("Photo Reveal Consent").performClick()
        drainUi()
        composeTestRule.onNodeWithText("Close").performClick()
        drainUi()

        assertFalse(vm.myPhotoConsent.value)
        assertFalse(vm.isPhotoRevealed.value)
    }

    @Test
    fun f9_tier2_unilateralLocalConsentMustNotRevealPhotosWithoutPeerConsent() {
        val vm = createChatViewModel()
        vm.joinSession("s_mutual", "seeker_1")
        vm.togglePhotoConsent()

        assertTrue(vm.myPhotoConsent.value)
        assertFalse(
            "Photos must remain blurred until mutual peer consent",
            vm.isPhotoRevealed.value
        )
    }

    @Test
    fun f9_tier2_rapidRepeatedConsentTogglesMaintainConsistentParity() {
        val vm = createChatViewModel()

        repeat(5) {
            vm.togglePhotoConsent()
        }
        assertTrue(vm.myPhotoConsent.value)

        vm.togglePhotoConsent()
        assertFalse(vm.myPhotoConsent.value)
        assertFalse(vm.isPhotoRevealed.value)
    }

    @Test
    fun f9_tier2_joiningNewSessionResetsPreviousPhotoConsentState() {
        val vm = createChatViewModel()

        vm.joinSession("sess_A", "u1")
        vm.togglePhotoConsent()
        assertTrue(vm.myPhotoConsent.value)

        vm.joinSession("sess_B", "u1")
        assertFalse(
            "Joining a new session must reset myPhotoConsent",
            vm.myPhotoConsent.value
        )
        assertFalse(
            "Joining a new session must reset isPhotoRevealed",
            vm.isPhotoRevealed.value
        )
    }

    @Test
    fun f9_tier2_anonymityStringsGuaranteeBlurredDefault() {
        val guarantee = context.getString(R.string.anonymity_guarantee)
        assertTrue(guarantee.contains("Photos stay blurred until mutual consent"))

        val privacyPolicy = File(E2ETestHarness.resolveProjectRoot(), "PRIVACY_POLICY.md").readText()
        assertTrue(privacyPolicy.contains("blurred"))
        assertTrue(privacyPolicy.contains("mutual"))
    }

    // =========================================================================
    // Feature F13 — Tier 1: Happy Path (6 Tests)
    // =========================================================================

    @Test
    fun f13_tier1_reportUserDialogDisplaysAllFourReasonsAndSubmitsDefault() {
        var submittedReason: String? = null
        var submittedDetails: String? = null

        composeTestRule.setContent {
            HearUTheme(darkTheme = false) {
                ReportUserDialog(
                    onDismiss = {},
                    onSubmitReport = { reason, details ->
                        submittedReason = reason
                        submittedDetails = details
                    }
                )
            }
        }

        composeTestRule.onNodeWithText("Report User").assertIsDisplayed()
        composeTestRule.onNodeWithText("Harassment / Hate Speech").assertIsDisplayed()
        composeTestRule.onNodeWithText("Inappropriate Content").assertIsDisplayed()
        composeTestRule.onNodeWithText("Spam / Scams").assertIsDisplayed()
        composeTestRule.onNodeWithText("Safety Concern / Self Harm").assertIsDisplayed()
        composeTestRule.onNodeWithText("Additional details (optional)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeTestRule.onNodeWithText("Submit Report").assertIsDisplayed().performClick()

        assertEquals("Harassment / Hate Speech", submittedReason)
        assertEquals("", submittedDetails)
    }

    @Test
    fun f13_tier1_reportUserDialogCapturesCustomReasonAndDetails() {
        var submittedReason: String? = null
        var submittedDetails: String? = null

        composeTestRule.setContent {
            HearUTheme(darkTheme = false) {
                ReportUserDialog(
                    onDismiss = {},
                    onSubmitReport = { reason, details ->
                        submittedReason = reason
                        submittedDetails = details
                    }
                )
            }
        }

        composeTestRule.onNodeWithText("Safety Concern / Self Harm").performClick()
        composeTestRule.onNodeWithText("Additional details (optional)")
            .performTextInput("Distressing threats")
        composeTestRule.onNodeWithText("Submit Report").performClick()

        assertEquals("Safety Concern / Self Harm", submittedReason)
        assertEquals("Distressing threats", submittedDetails)
    }

    @Test
    fun f13_tier1_chatScreenOverflowMenuOpensReportUserDialog() {
        val vm = createChatViewModel()

        composeTestRule.setContent {
            HearUTheme(darkTheme = false) {
                ChatScreen(
                    sessionId = "s1",
                    userId = "seeker_1",
                    onNavigateBack = {},
                    viewModel = vm
                )
            }
        }
        drainUi()

        composeTestRule.onNodeWithContentDescription("Options").performClick()
        drainUi()
        composeTestRule.onNodeWithText("Report User / Safety").performClick()
        drainUi()

        composeTestRule.onNodeWithText("Report User").assertIsDisplayed()
        composeTestRule.onNodeWithText("Submit Report").assertIsDisplayed()
    }

    @Test
    fun f13_tier1_moderationRepositoryReportUserWritesExpectedFirestorePayload() =
        runTest(mainDispatcherRule.testDispatcher) {
            val mockDocRef: DocumentReference = mock()
            whenever(mockReportsCollection.add(any())).thenReturn(Tasks.forResult(mockDocRef))

            moderationRepository.reportUser(
                reporterId = "rep_1",
                reportedUserId = "bad_2",
                sessionId = "sess_3",
                reason = "Inappropriate Content",
                description = "Abuse"
            )

            val captor = argumentCaptor<Any>()
            verify(mockReportsCollection).add(captor.capture())
            val payload = captor.firstValue as Map<*, *>
            assertEquals("rep_1", payload["reporterId"])
            assertEquals("bad_2", payload["reportedUserId"])
            assertEquals("sess_3", payload["sessionId"])
            assertEquals("Inappropriate Content", payload["reason"])
            assertEquals("Abuse", payload["description"])
            assertEquals("PENDING_REVIEW", payload["status"])
            assertNotNull(payload["timestamp"])
        }

    @Test
    fun f13_tier1_moderationRepositoryBlockUserCallsArrayUnionOnBlockedUsers() =
        runTest(mainDispatcherRule.testDispatcher) {
            whenever(mockUserDoc.update(eq("blockedUsers"), any())).thenReturn(Tasks.forResult(null))

            moderationRepository.blockUser("rep_1", "bad_2")

            verify(mockUsersCollection).document("rep_1")
            verify(mockUserDoc).update(eq("blockedUsers"), any())
        }

    @Test
    fun f13_tier1_firestoreRulesPermitValidReportCreateAndUserBlockedUsersUpdate() {
        val rulesContent = File(E2ETestHarness.resolveProjectRoot(), "firestore.rules").readText()

        val reportsBlock = rulesContent.substringAfter("match /reports/{reportId}")
        assertTrue(reportsBlock.contains("allow create:"))
        assertTrue(reportsBlock.contains("reporterId == request.auth.uid"))
        assertTrue(reportsBlock.contains("reason.size() > 0") && reportsBlock.contains("reason.size() <= 500"))
        assertTrue(reportsBlock.contains("allow read, update, delete: if false;"))

        val usersBlock = rulesContent.substringAfter("match /users/{userId}")
            .substringBefore("match /publicListeners/{userId}")
        assertTrue(
            "firestore.rules /users/{userId} allow update must include 'blockedUsers'",
            usersBlock.contains("'blockedUsers'")
        )
    }

    // =========================================================================
    // Feature F13 — Tier 2: Boundary / Corner / Error (6 Tests)
    // =========================================================================

    @Test
    fun f13_tier2_reportUserDialogCancelDismissesWithoutSubmitting() {
        var dismissed = 0
        var submitted = 0

        composeTestRule.setContent {
            HearUTheme(darkTheme = false) {
                ReportUserDialog(
                    onDismiss = { dismissed++ },
                    onSubmitReport = { _, _ -> submitted++ }
                )
            }
        }

        composeTestRule.onNodeWithText("Spam / Scams").performClick()
        composeTestRule.onNodeWithText("Additional details (optional)").performTextInput("scam link")
        composeTestRule.onNodeWithText("Cancel").performClick()

        assertEquals(1, dismissed)
        assertEquals(0, submitted)
    }

    @Test
    fun f13_tier2_chatScreenExposesBlockAndLeaveActionInMenu() {
        val vm = createChatViewModel()

        composeTestRule.setContent {
            HearUTheme(darkTheme = false) {
                ChatScreen(
                    sessionId = "s1",
                    userId = "seeker_1",
                    onNavigateBack = {},
                    viewModel = vm
                )
            }
        }
        drainUi()

        composeTestRule.onNodeWithContentDescription("Options").performClick()
        drainUi()

        composeTestRule.onNodeWithText(context.getString(R.string.block_user)).assertIsDisplayed()
    }

    @Test
    fun f13_tier2_chatScreenReportSubmissionIsNotSilentNoOpWhenSessionUnauthenticated() {
        whenever(mockAuth.currentUser).thenReturn(null)
        val vm = createChatViewModel()

        composeTestRule.setContent {
            HearUTheme(darkTheme = false) {
                ChatScreen(
                    sessionId = "s1",
                    userId = "seeker_1",
                    onNavigateBack = {},
                    viewModel = vm
                )
            }
        }
        drainUi()

        composeTestRule.onNodeWithContentDescription("Options").performClick()
        drainUi()
        composeTestRule.onNodeWithText("Report User / Safety").performClick()
        drainUi()
        composeTestRule.onNodeWithText("Submit Report").performClick()
        drainUi()

        val hasUiError = listOf("error", "expired", "failed", "log in").any { keyword ->
            composeTestRule.onAllNodesWithText(keyword, substring = true, ignoreCase = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        assertTrue(
            "Submitting a report when unauthenticated must trigger an error state rather than silently succeeding",
            vm.errorState.value != null || hasUiError
        )
    }

    @Test
    fun f13_tier2_moderationRepositoryReportUserPropagatesFirestoreException() =
        runTest(mainDispatcherRule.testDispatcher) {
            whenever(mockReportsCollection.add(any()))
                .thenReturn(Tasks.forException(RuntimeException("PERMISSION_DENIED")))

            var caught: RuntimeException? = null
            try {
                moderationRepository.reportUser(
                    reporterId = "rep_1",
                    reportedUserId = "bad_2",
                    sessionId = "sess_3",
                    reason = "Harassment / Hate Speech",
                    description = "Details"
                )
            } catch (e: RuntimeException) {
                caught = e
            }

            assertNotNull(caught)
            assertTrue(caught!!.message?.contains("PERMISSION_DENIED") == true)
        }

    @Test
    fun f13_tier2_moderationRepositoryBlockUserPropagatesFirestoreException() =
        runTest(mainDispatcherRule.testDispatcher) {
            whenever(mockUserDoc.update(eq("blockedUsers"), any()))
                .thenReturn(Tasks.forException(RuntimeException("PERMISSION_DENIED")))

            var caught: RuntimeException? = null
            try {
                moderationRepository.blockUser("u1", "bad_2")
            } catch (e: RuntimeException) {
                caught = e
            }

            assertNotNull(caught)
            assertTrue(caught!!.message?.contains("PERMISSION_DENIED") == true)
        }

    @Test
    fun f13_tier2_reportBoundaryWhitespaceAnd500CharDetailsPlusBlockedMatchFilter() {
        val capturedSubmissions = mutableListOf<Pair<String, String>>()

        composeTestRule.setContent {
            HearUTheme(darkTheme = false) {
                ReportUserDialog(
                    onDismiss = {},
                    onSubmitReport = { reason, details ->
                        capturedSubmissions.add(reason to details)
                    }
                )
            }
        }

        composeTestRule.onNodeWithText("Additional details (optional)").performTextInput("   ")
        composeTestRule.onNodeWithText("Submit Report").performClick()

        val long500 = "a".repeat(500)
        composeTestRule.onNodeWithText("Additional details (optional)").performTextReplacement(long500)
        composeTestRule.onNodeWithText("Submit Report").performClick()

        assertEquals(2, capturedSubmissions.size)
        assertEquals("   ", capturedSubmissions[0].second)
        assertEquals(500, capturedSubmissions[1].second.length)
        assertEquals(long500, capturedSubmissions[1].second)

        val functionsContent = File(E2ETestHarness.resolveProjectRoot(), "functions/index.js").readText()
        assertTrue(
            "matchSeekerWithGiver must filter blockedUsers",
            functionsContent.contains("blockedUsers")
        )
    }
}
