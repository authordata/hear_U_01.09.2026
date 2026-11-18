package com.hearu.app.e2e.tier1_2

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.hearu.app.e2e.harness.E2ETestHarness
import com.hearu.app.e2e.harness.MainDispatcherRule
import com.hearu.app.model.User
import com.hearu.app.repository.UserRepository
import com.hearu.app.ui.home.GiverDashboard
import com.hearu.app.ui.theme.HearUTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Opaque-box E2E tests for Feature F6: Giver Dashboard, Availability & Queue.
 * Covers Tier 1 (6 happy-path tests) and Tier 2 (6 boundary/corner/error tests).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GiverDashboardE2ETest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var mockFirestore: FirebaseFirestore
    private lateinit var mockUsersCollection: CollectionReference
    private lateinit var mockUserDoc: DocumentReference
    private lateinit var userRepository: UserRepository

    @Before
    fun setUp() {
        mockFirestore = mock()
        mockUsersCollection = mock()
        mockUserDoc = mock()

        whenever(mockFirestore.collection("users")).thenReturn(mockUsersCollection)
        whenever(mockUsersCollection.document(any())).thenReturn(mockUserDoc)

        userRepository = UserRepository(mockFirestore)
    }

    // =========================================================================
    // Tier 1 — Happy Path (6 Tests)
    // =========================================================================

    @Test
    fun tier1_giverDashboard_initialOnlineState_displaysStatusStatsAndIncomingCard() {
        composeTestRule.setContent {
            HearUTheme(darkTheme = false) {
                GiverDashboard()
            }
        }

        composeTestRule.onNodeWithText("HearU - Listener Hub").assertIsDisplayed()
        composeTestRule.onNodeWithText("Active & Ready to Listen").assertIsDisplayed()
        composeTestRule.onNodeWithText("You are Available").assertIsDisplayed()
        composeTestRule.onNodeWithText("Your Empathy Impact").assertIsDisplayed()
        composeTestRule.onNodeWithText("Peers Heard").assertIsDisplayed()
        composeTestRule.onNodeWithText("Helpfulness").assertIsDisplayed()
        composeTestRule.onNodeWithText("Empathy Badge").assertIsDisplayed()
        composeTestRule.onNodeWithText("Incoming Listening Requests").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Accept & Connect Anonymously").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Empathy & Safety Code of Conduct").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun tier1_availabilitySwitch_togglesOfflineAndOnline_controllingQueueVisibility() {
        composeTestRule.setContent {
            HearUTheme(darkTheme = false) {
                GiverDashboard()
            }
        }

        composeTestRule.onNode(isToggleable()).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Status: Offline").assertIsDisplayed()
        composeTestRule.onNodeWithText("You are Offline").assertIsDisplayed()
        composeTestRule.onNodeWithText("Toggle on when you are in a safe headspace to listen.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Incoming Listening Requests").assertDoesNotExist()

        composeTestRule.onNode(isToggleable()).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Active & Ready to Listen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Incoming Listening Requests").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun tier1_acceptIncomingRequest_invokesOnNavigateToChat() {
        var navigatedSessionId: String? = null
        var navigatedUserId: String? = null

        composeTestRule.setContent {
            HearUTheme(darkTheme = false) {
                GiverDashboard(
                    onNavigateToChat = { sid, uid ->
                        navigatedSessionId = sid
                        navigatedUserId = uid
                    }
                )
            }
        }

        composeTestRule.onNodeWithText("Accept & Connect Anonymously").performScrollTo().performClick()

        assertNotNull(navigatedSessionId)
        assertTrue(navigatedSessionId!!.startsWith("session_"))
        assertFalse(navigatedUserId.isNullOrBlank())
    }

    @Test
    fun tier1_safetyCodeOfConduct_expandsAndCollapsesAllGuidelines() {
        composeTestRule.setContent {
            HearUTheme(darkTheme = false) {
                GiverDashboard()
            }
        }

        composeTestRule.onNodeWithText("1. Listen without giving unrequested advice.")
            .assertDoesNotExist()

        composeTestRule.onNodeWithText("Empathy & Safety Code of Conduct")
            .performScrollTo()
            .performClick()

        composeTestRule.onNodeWithText("1. Listen without giving unrequested advice.")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("2. Never ask for personally identifying information.")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("3. If crisis is detected, guide the user to the Crisis Hub button.")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("4. You may exit any conversation whenever you feel overwhelmed.")
            .performScrollTo()
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("Empathy & Safety Code of Conduct")
            .performScrollTo()
            .performClick()

        composeTestRule.onNodeWithText("1. Listen without giving unrequested advice.")
            .assertDoesNotExist()
    }

    @Test
    fun tier1_profileIcon_invokesOnNavigateToProfile() {
        var profileClicks = 0

        composeTestRule.setContent {
            HearUTheme(darkTheme = false) {
                GiverDashboard(onNavigateToProfile = { profileClicks++ })
            }
        }

        composeTestRule.onNodeWithContentDescription("Profile").performClick()

        assertEquals(1, profileClicks)
    }

    @Test
    fun tier1_userRepositoryStatusSync_writesOnlineAndOfflineToUsersCollection() =
        runTest(mainDispatcherRule.testDispatcher) {
            whenever(mockUserDoc.update("status", "online")).thenReturn(Tasks.forResult(null))
            whenever(mockUserDoc.update("status", "offline")).thenReturn(Tasks.forResult(null))

            userRepository.updateUserStatus("giver_1", "online")
            userRepository.updateUserStatus("giver_1", "offline")

            verify(mockUsersCollection, times(2)).document("giver_1")
            verify(mockUserDoc).update("status", "online")
            verify(mockUserDoc).update("status", "offline")
        }

    // =========================================================================
    // Tier 2 — Boundary / Corner / Error (6 Tests)
    // =========================================================================

    @Test
    fun tier2_rapidAvailabilityToggles_fiveClicksLeaveConsistentOfflineState() {
        composeTestRule.setContent {
            HearUTheme(darkTheme = false) {
                GiverDashboard()
            }
        }

        repeat(5) {
            composeTestRule.onNode(isToggleable()).performClick()
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Status: Offline").assertIsDisplayed()
        composeTestRule.onNodeWithText("You are Offline").assertIsDisplayed()
        composeTestRule.onNodeWithText("Incoming Listening Requests").assertDoesNotExist()
    }

    @Test
    fun tier2_stateRestorationAfterConfigChange_preservesOfflineAndExpandedGuidelines() {
        val restorationTester = StateRestorationTester(composeTestRule)
        restorationTester.setContent {
            HearUTheme(darkTheme = false) {
                GiverDashboard()
            }
        }

        composeTestRule.onNode(isToggleable()).performClick()
        composeTestRule.onNodeWithText("Empathy & Safety Code of Conduct")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("You are Offline").assertIsDisplayed()
        composeTestRule.onNodeWithText("1. Listen without giving unrequested advice.")
            .performScrollTo()
            .assertIsDisplayed()

        restorationTester.emulateSavedInstanceStateRestore()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("You are Offline").assertIsDisplayed()
        composeTestRule.onNodeWithText("1. Listen without giving unrequested advice.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun tier2_offlineGiver_cannotAcceptIncomingRequests() {
        var chatNavigated = 0

        composeTestRule.setContent {
            HearUTheme(darkTheme = false) {
                GiverDashboard(onNavigateToChat = { _, _ -> chatNavigated++ })
            }
        }

        composeTestRule.onNode(isToggleable()).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Accept & Connect Anonymously").assertDoesNotExist()
        assertEquals(0, chatNavigated)
    }

    @Test
    @Config(qualifiers = "w320dp-h480dp", fontScale = 1.4f)
    fun tier2_compactViewportWithExpandedGuidelines_scrollsToLastRule() {
        composeTestRule.setContent {
            HearUTheme(darkTheme = false) {
                GiverDashboard()
            }
        }

        composeTestRule.onNodeWithText("Empathy & Safety Code of Conduct")
            .performScrollTo()
            .performClick()

        composeTestRule.onNodeWithText("4. You may exit any conversation whenever you feel overwhelmed.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun tier2_userRepositoryStatusSyncFailure_andMissingUser_handledCleanly() =
        runTest(mainDispatcherRule.testDispatcher) {
            whenever(mockUserDoc.update("status", "online"))
                .thenReturn(Tasks.forException(RuntimeException("Firestore unavailable")))

            var caughtError: Throwable? = null
            try {
                userRepository.updateUserStatus("giver_1", "online")
            } catch (e: RuntimeException) {
                caughtError = e
            }
            assertNotNull("Expected RuntimeException when Firestore status update fails", caughtError)
            assertTrue(caughtError?.message?.contains("Firestore unavailable") == true)

            val mockSnapshot: DocumentSnapshot = mock()
            whenever(mockSnapshot.toObject(User::class.java)).thenReturn(null)
            whenever(mockUserDoc.get()).thenReturn(Tasks.forResult(mockSnapshot))

            val missingUser = userRepository.getUser("missing_uid")
            assertNull(missingUser)
        }

    @Test
    fun tier2_giverFirestoreRulesAndDiscoveryContract_protectRatingAndMatchActiveGivers() {
        val root: File = E2ETestHarness.resolveProjectRoot()
        val rulesContent = File(root, "firestore.rules").readText()
        val functionsContent = File(root, "functions/index.js").readText()

        assertTrue(
            "firestore.rules /users/{userId} update must allow 'status' and 'emotionTags'",
            rulesContent.contains("'status'") && rulesContent.contains("'emotionTags'")
        )
        assertTrue(
            "firestore.rules /users/{userId} create must forbid client-assigned rating",
            rulesContent.contains("!request.resource.data.keys().hasAny(['rating'])")
        )

        val publicListenersBlock = rulesContent.substringAfter("match /publicListeners/{userId}")
            .substringBefore("match /sessions/{sessionId}")
        assertFalse(
            "firestore.rules /publicListeners/{userId} write must not allow client rating overwrites",
            publicListenersBlock.contains("'rating'") &&
                !publicListenersBlock.contains("!request.resource.data.keys().hasAny(['rating'])")
        )

        assertTrue(
            "functions/index.js must query givers with role in ['giver', 'both']",
            functionsContent.contains("""where("role", "in", ["giver", "both"])""")
        )
        assertTrue(
            "functions/index.js must query givers with status == 'online'",
            functionsContent.contains("""where("status", "==", "online")""")
        )
    }
}
