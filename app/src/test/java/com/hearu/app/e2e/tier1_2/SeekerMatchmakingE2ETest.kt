package com.hearu.app.e2e.tier1_2

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import com.hearu.app.e2e.harness.E2ETestHarness
import com.hearu.app.e2e.harness.MainDispatcherRule
import com.hearu.app.ui.home.MatchState
import com.hearu.app.ui.home.MatchViewModel
import com.hearu.app.ui.home.SeekerDashboard
import com.hearu.app.ui.theme.HearUTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * Opaque-box E2E tests for Feature F5: Seeker Dashboard & Matchmaking.
 * Covers Tier 1 (6 happy-path tests) and Tier 2 (6 boundary/corner/error tests).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SeekerMatchmakingE2ETest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var mockAuth: FirebaseAuth
    private lateinit var mockUser: FirebaseUser
    private lateinit var mockFunctions: FirebaseFunctions
    private lateinit var mockCallable: HttpsCallableReference
    private var mockFunctionsStatic: MockedStatic<FirebaseFunctions>? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockAuth = mock()
        mockUser = mock()
        mockFunctions = mock()
        mockCallable = mock()

        whenever(mockAuth.currentUser).thenReturn(mockUser)
        whenever(mockUser.uid).thenReturn("seeker_e2e_1")

        mockFunctionsStatic = Mockito.mockStatic(FirebaseFunctions::class.java)
        mockFunctionsStatic?.`when`<FirebaseFunctions> { FirebaseFunctions.getInstance() }
            ?.thenReturn(mockFunctions)
        whenever(mockFunctions.getHttpsCallable("matchSeekerWithGiver")).thenReturn(mockCallable)
    }

    @After
    fun tearDown() {
        mockFunctionsStatic?.close()
        mockFunctionsStatic = null
    }

    private fun buildMatchViewModel(
        auth: FirebaseAuth = mockAuth,
        functions: FirebaseFunctions = mockFunctions
    ): MatchViewModel {
        val ctors = MatchViewModel::class.java.constructors
        val twoArgCtor = ctors.firstOrNull { ctor ->
            val params = ctor.parameterTypes
            params.size == 2 &&
                FirebaseAuth::class.java.isAssignableFrom(params[0]) &&
                FirebaseFunctions::class.java.isAssignableFrom(params[1])
        }
        if (twoArgCtor != null) {
            return twoArgCtor.newInstance(auth, functions) as MatchViewModel
        }
        val oneArgCtor = ctors.first { ctor ->
            ctor.parameterTypes.size == 1 &&
                FirebaseAuth::class.java.isAssignableFrom(ctor.parameterTypes[0])
        }
        return oneArgCtor.newInstance(auth) as MatchViewModel
    }

    private fun drainAsync() {
        ShadowLooper.idleMainLooper()
        mainDispatcherRule.testDispatcher.scheduler.runCurrent()
        ShadowLooper.idleMainLooper()
        composeTestRule.waitForIdle()
    }

    private fun contrastRatio(foreground: Color, background: Color): Float {
        val l1 = foreground.luminance()
        val l2 = background.luminance()
        return (max(l1, l2) + 0.05f) / (min(l1, l2) + 0.05f)
    }

    // =========================================================================
    // Tier 1 — Happy Path (6 Tests)
    // =========================================================================

    @Test
    fun tier1_seekerDashboard_rendersHeaderToolsAndAnonymityBadge() {
        val vm = buildMatchViewModel()

        composeTestRule.setContent {
            HearUTheme(darkTheme = false) {
                SeekerDashboard(
                    onNavigateToAIChat = {},
                    onNavigateToMatch = { _, _ -> },
                    matchViewModel = vm
                )
            }
        }

        composeTestRule.onNodeWithText("HearU").assertIsDisplayed()
        composeTestRule.onNodeWithText("Safe & Anonymous Space").assertIsDisplayed()
        composeTestRule.onNodeWithText("How are you feeling right now?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Connect with a Human Listener").assertIsDisplayed()
        composeTestRule.onNodeWithText("Find a Listener Now").assertIsDisplayed()
        composeTestRule.onNodeWithText("Instant Support & Tools").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("AI Companion", substring = true).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("4-7-8 Breathing & Grounding").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("SOS Emergency & 988 Lifeline").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("100% Anonymous & Ephemeral", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun tier1_selectMultipleEmotionTags_andStartMatch_passesTagsAndShowsSearchingPulse() {
        val pendingTask = TaskCompletionSource<HttpsCallableResult>().task
        whenever(mockCallable.call(any())).thenReturn(pendingTask)
        val vm = buildMatchViewModel()

        composeTestRule.setContent {
            HearUTheme(darkTheme = false) {
                SeekerDashboard(
                    onNavigateToAIChat = {},
                    onNavigateToMatch = { _, _ -> },
                    matchViewModel = vm
                )
            }
        }

        composeTestRule.onNodeWithText("Anxiety 😰").performClick()
        composeTestRule.onNodeWithText("Loneliness 🌧️").performClick()
        composeTestRule.onNodeWithText("Anxiety 😰").assertIsSelected()
        composeTestRule.onNodeWithText("Loneliness 🌧️").assertIsSelected()

        composeTestRule.onNodeWithText("Find a Listener Now").performClick()
        drainAsync()

        verify(mockFunctions).getHttpsCallable("matchSeekerWithGiver")
        verify(mockCallable).call(argThat<Any> { payload ->
            (payload as? Map<*, *>)?.get("tags") == listOf("Anxiety 😰", "Loneliness 🌧️")
        })
        composeTestRule.onNodeWithText("Finding your empathetic listener...").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel Search").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun tier1_matchFound_navigatesToChatWithSessionIdAndResetsMatchState() {
        val mockResult: HttpsCallableResult = mock()
        whenever(mockResult.data).thenReturn(
            mapOf(
                "matchFound" to true,
                "sessionId" to "session_live_101",
                "giverId" to "giver_uid_42",
                "giverDisplayName" to "CalmListener"
            )
        )
        whenever(mockCallable.call(any())).thenReturn(Tasks.forResult(mockResult))
        val vm = buildMatchViewModel()

        var navigatedSessionId: String? = null
        var navigatedUserId: String? = null

        composeTestRule.setContent {
            HearUTheme(darkTheme = false) {
                SeekerDashboard(
                    onNavigateToAIChat = {},
                    onNavigateToMatch = { sid, uid ->
                        navigatedSessionId = sid
                        navigatedUserId = uid
                    },
                    matchViewModel = vm
                )
            }
        }

        composeTestRule.onNodeWithText("Find a Listener Now").performClick()
        drainAsync()

        assertEquals("session_live_101", navigatedSessionId)
        assertFalse(navigatedUserId.isNullOrBlank())
        assertEquals(MatchState.Idle, vm.matchState.value)
    }

    @Test
    fun tier1_noMatchFound_displaysHighDemandFallback_andRoutesToAiCompanion() {
        val mockResult: HttpsCallableResult = mock()
        whenever(mockResult.data).thenReturn(
            mapOf(
                "matchFound" to false,
                "message" to "All listeners are currently occupied."
            )
        )
        whenever(mockCallable.call(any())).thenReturn(Tasks.forResult(mockResult))
        val vm = buildMatchViewModel()
        var aiClicks = 0

        composeTestRule.setContent {
            HearUTheme(darkTheme = false) {
                SeekerDashboard(
                    onNavigateToAIChat = { aiClicks++ },
                    onNavigateToMatch = { _, _ -> },
                    matchViewModel = vm
                )
            }
        }

        composeTestRule.onNodeWithText("Find a Listener Now").performClick()
        drainAsync()

        composeTestRule.onNodeWithText("Listeners are currently in high demand")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("All listeners are currently occupied.")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Chat with 24/7 AI Companion Instead")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        assertEquals(1, aiClicks)
    }

    @Test
    fun tier1_quickToolsAndTopBarActions_invokeAllNavigationCallbacks() {
        val vm = buildMatchViewModel()
        var aiCount = 0
        var breathingCount = 0
        var emergencyCount = 0
        var profileCount = 0

        composeTestRule.setContent {
            HearUTheme(darkTheme = false) {
                SeekerDashboard(
                    onNavigateToAIChat = { aiCount++ },
                    onNavigateToMatch = { _, _ -> },
                    onNavigateToBreathing = { breathingCount++ },
                    onNavigateToEmergency = { emergencyCount++ },
                    onNavigateToProfile = { profileCount++ },
                    matchViewModel = vm
                )
            }
        }

        composeTestRule.onNodeWithText("AI Companion", substring = true).performScrollTo().performClick()
        composeTestRule.onNodeWithText("4-7-8 Breathing & Grounding").performScrollTo().performClick()
        composeTestRule.onNodeWithText("SOS Emergency & 988 Lifeline").performScrollTo().performClick()
        composeTestRule.onNodeWithContentDescription("Crisis 24/7").performClick()
        composeTestRule.onNodeWithContentDescription("Profile").performClick()

        assertEquals(1, aiCount)
        assertEquals(1, breathingCount)
        assertEquals(2, emergencyCount)
        assertEquals(1, profileCount)
    }

    @Test
    fun tier1_emotionChipToggleAndHorizontalScroll_updatesSelectionState() {
        val vm = buildMatchViewModel()

        composeTestRule.setContent {
            HearUTheme(darkTheme = false) {
                SeekerDashboard(
                    onNavigateToAIChat = {},
                    onNavigateToMatch = { _, _ -> },
                    matchViewModel = vm
                )
            }
        }

        composeTestRule.onNode(hasScrollToIndexAction())
            .performScrollToNode(hasText("Work Stress 💼"))
        composeTestRule.onNodeWithText("Work Stress 💼").performClick()
        composeTestRule.onNodeWithText("Work Stress 💼").assertIsSelected()

        composeTestRule.onNodeWithText("Work Stress 💼").performClick()
        composeTestRule.onNodeWithText("Work Stress 💼").assertIsNotSelected()

        composeTestRule.onNode(hasScrollToIndexAction())
            .performScrollToNode(hasText("Just Venting 🗣️"))
        composeTestRule.onNodeWithText("Just Venting 🗣️").performClick()
        composeTestRule.onNodeWithText("Just Venting 🗣️").assertIsSelected()
    }

    // =========================================================================
    // Tier 2 — Boundary / Corner / Error (6 Tests)
    // =========================================================================

    @Test
    fun tier2_startMatchWithZeroTags_sendsEmptyListAndMatches() {
        val mockResult: HttpsCallableResult = mock()
        whenever(mockResult.data).thenReturn(
            mapOf(
                "matchFound" to true,
                "sessionId" to "session_zero_tags",
                "giverId" to "giver_9",
                "giverDisplayName" to "KindEar"
            )
        )
        whenever(mockCallable.call(any())).thenReturn(Tasks.forResult(mockResult))
        val vm = buildMatchViewModel()
        var navigatedSessionId: String? = null

        composeTestRule.setContent {
            HearUTheme(darkTheme = false) {
                SeekerDashboard(
                    onNavigateToAIChat = {},
                    onNavigateToMatch = { sid, _ -> navigatedSessionId = sid },
                    matchViewModel = vm
                )
            }
        }

        composeTestRule.onNodeWithText("Find a Listener Now").performClick()
        drainAsync()

        verify(mockCallable).call(argThat<Any> { payload ->
            (payload as? Map<*, *>)?.get("tags") == emptyList<String>()
        })
        assertEquals("session_zero_tags", navigatedSessionId)
    }

    @Test
    fun tier2_selectAllSevenEmotionTags_forwardsFullListWithoutTruncation() {
        val pendingTask = TaskCompletionSource<HttpsCallableResult>().task
        whenever(mockCallable.call(any())).thenReturn(pendingTask)
        val vm = buildMatchViewModel()

        val allTags = listOf(
            "Anxiety 😰",
            "Loneliness 🌧️",
            "Work Stress 💼",
            "Relationships 💔",
            "Grief & Loss 🕯️",
            "Sleep & Insomnia 🌙",
            "Just Venting 🗣️"
        )

        composeTestRule.setContent {
            HearUTheme(darkTheme = false) {
                SeekerDashboard(
                    onNavigateToAIChat = {},
                    onNavigateToMatch = { _, _ -> },
                    matchViewModel = vm
                )
            }
        }

        allTags.forEachIndexed { index, tag ->
            composeTestRule.onNode(hasScrollToIndexAction()).performScrollToIndex(index)
            composeTestRule.onNodeWithText(tag).performClick()
            composeTestRule.onNodeWithText(tag).assertIsSelected()
        }

        composeTestRule.onNodeWithText("Find a Listener Now").performScrollTo().performClick()
        drainAsync()

        verify(mockCallable).call(argThat<Any> { payload ->
            val tags = (payload as? Map<*, *>)?.get("tags") as? List<*>
            tags?.size == 7 && tags == allTags
        })
    }

    @Test
    fun tier2_cancelSearchMidFlight_cancelsJobAndIgnoresLateResponse() {
        val tcs = TaskCompletionSource<HttpsCallableResult>()
        whenever(mockCallable.call(any())).thenReturn(tcs.task)
        val vm = buildMatchViewModel()
        var navigatedSessionId: String? = null

        composeTestRule.setContent {
            HearUTheme(darkTheme = false) {
                SeekerDashboard(
                    onNavigateToAIChat = {},
                    onNavigateToMatch = { sid, _ -> navigatedSessionId = sid },
                    matchViewModel = vm
                )
            }
        }

        composeTestRule.onNodeWithText("Find a Listener Now").performClick()
        drainAsync()
        assertTrue(vm.matchState.value is MatchState.Searching)

        composeTestRule.onNodeWithText("Cancel Search")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        drainAsync()
        assertEquals(MatchState.Idle, vm.matchState.value)
        composeTestRule.onNodeWithText("Find a Listener Now").performScrollTo().assertIsDisplayed()

        val lateResult: HttpsCallableResult = mock()
        whenever(lateResult.data).thenReturn(
            mapOf(
                "matchFound" to true,
                "sessionId" to "session_late_ghost",
                "giverId" to "giver_late",
                "giverDisplayName" to "LateListener"
            )
        )
        tcs.setResult(lateResult)
        drainAsync()

        assertEquals(
            "Cancelled search must ignore late Cloud Function completions",
            MatchState.Idle,
            vm.matchState.value
        )
        assertNull(navigatedSessionId)
    }

    @Test
    fun tier2_matchmakingNetworkError_surfacesErrorStateWithoutSilentFakeMatch() {
        whenever(mockCallable.call(any())).thenReturn(
            Tasks.forException(RuntimeException("Network unreachable"))
        )
        val vm = buildMatchViewModel()
        var navigatedSessionId: String? = null

        composeTestRule.setContent {
            HearUTheme(darkTheme = false) {
                SeekerDashboard(
                    onNavigateToAIChat = {},
                    onNavigateToMatch = { sid, _ -> navigatedSessionId = sid },
                    matchViewModel = vm
                )
            }
        }

        composeTestRule.onNodeWithText("Find a Listener Now").performClick()
        mainDispatcherRule.testDispatcher.scheduler.advanceTimeBy(3000L)
        drainAsync()

        assertNull(
            "Matchmaking error must NOT silently navigate to a fake demo listener",
            navigatedSessionId
        )
        assertTrue(
            "Expected MatchState.Error on network failure, was ${vm.matchState.value}",
            vm.matchState.value is MatchState.Error
        )
        val hasErrorFeedback = listOf("unable", "error", "try again", "failed").any { keyword ->
            composeTestRule.onAllNodesWithText(keyword, substring = true, ignoreCase = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        assertTrue("SeekerDashboard must render visible error/retry UI on MatchState.Error", hasErrorFeedback)
    }

    @Test
    @Config(qualifiers = "w320dp-h560dp")
    fun tier2_darkThemeContrastAndCompactViewport_renderCleanly() {
        val vm = buildMatchViewModel()
        var darkPrimary: Color? = null
        var darkOnPrimary: Color? = null

        composeTestRule.setContent {
            HearUTheme(darkTheme = true) {
                darkPrimary = MaterialTheme.colorScheme.primary
                darkOnPrimary = MaterialTheme.colorScheme.onPrimary
                SeekerDashboard(
                    onNavigateToAIChat = {},
                    onNavigateToMatch = { _, _ -> },
                    matchViewModel = vm
                )
            }
        }

        composeTestRule.onNodeWithText("100% Anonymous & Ephemeral", substring = true)
            .performScrollTo()
            .assertIsDisplayed()

        assertNotNull(darkPrimary)
        assertNotNull(darkOnPrimary)
        val onPrimaryContrast = contrastRatio(darkOnPrimary!!, darkPrimary!!)
        assertTrue(
            "Dark theme primary/onPrimary WCAG AA contrast must be >= 4.5 (was $onPrimaryContrast)",
            onPrimaryContrast >= 4.5f
        )

        val seekerFile = File(
            E2ETestHarness.resolveProjectRoot(),
            "app/src/main/java/com/hearu/app/ui/home/SeekerDashboard.kt"
        )
        val source = seekerFile.readText()
        assertFalse(
            "SeekerDashboard must use MaterialTheme.colorScheme.onPrimary instead of hardcoded Color.White on primary card",
            source.contains("color = Color.White") || source.contains("tint = Color.White")
        )
    }

    @Test
    fun tier2_backendMatchmakingContract_returnsGiverIdAndExcludesBlockedUsers() {
        val functionsFile = File(E2ETestHarness.resolveProjectRoot(), "functions/index.js")
        assertTrue("functions/index.js must exist", functionsFile.exists())
        val content = functionsFile.readText()

        assertTrue(
            "matchSeekerWithGiver must guard null/non-array data?.tags",
            content.contains("Array.isArray(data?.tags)")
        )
        assertTrue(
            "matchSeekerWithGiver must return giverId in response",
            content.contains("giverId:") || content.contains("giverId,")
        )
        assertTrue(
            "matchSeekerWithGiver must return giverDisplayName in response",
            content.contains("giverDisplayName:") || content.contains("giverDisplayName,")
        )
        assertTrue(
            "matchSeekerWithGiver must exclude blockedUsers",
            content.contains("blockedUsers")
        )
    }
}
