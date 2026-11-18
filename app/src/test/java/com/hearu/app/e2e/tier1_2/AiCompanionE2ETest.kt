package com.hearu.app.e2e.tier1_2

import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.hearu.app.ai.AiResult
import com.hearu.app.ai.GeminiAiService
import com.hearu.app.data.RolePreferences
import com.hearu.app.data.dataStore
import com.hearu.app.e2e.harness.E2ETestHarness
import com.hearu.app.e2e.harness.MainDispatcherRule
import com.hearu.app.ui.chat.AIChatScreen
import com.hearu.app.ui.chat.AIChatViewModel
import com.hearu.app.ui.theme.HearUTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h915dp")
class AiCompanionE2ETest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var appContext: Application
    private lateinit var geminiService: GeminiAiService
    private lateinit var mockPreferences: RolePreferences
    private lateinit var mockAuth: FirebaseAuth
    private lateinit var mockUser: FirebaseUser

    @Before
    fun setUp() {
        appContext = RuntimeEnvironment.getApplication()
        E2ETestHarness.ensureFirebaseInitialized(appContext)
        runBlocking { E2ETestHarness.clearRolePreferences(appContext) }

        geminiService = mock()
        mockPreferences = mock()
        mockAuth = mock()
        mockUser = mock()

        whenever(mockAuth.currentUser).thenReturn(mockUser)
        whenever(mockUser.uid).thenReturn("seeker_e2e_1")
        runBlocking {
            whenever(mockPreferences.getAiMessagesUsedToday()).thenReturn(0)
            whenever(mockPreferences.tryConsumeAiQuota(any())).thenReturn(true)
        }
    }

    @After
    fun tearDown() {
        runBlocking { E2ETestHarness.clearRolePreferences(appContext) }
    }

    private fun scrollAiChatListTo(matcher: SemanticsMatcher) {
        composeTestRule.onNode(
            hasScrollToNodeAction() and
                SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
        ).performScrollToNode(matcher)
    }

    private fun todayStartEpoch(): Long {
        return LocalDate.now(ZoneId.systemDefault())
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    private fun yesterdayStartEpoch(): Long {
        return LocalDate.now(ZoneId.systemDefault())
            .minusDays(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    // ==================== TIER 1: HAPPY PATH ====================

    @Test
    fun tier1_initialRender_showsWelcomeMessageDisclaimerAndSuggestionChips() {
        val viewModel = AIChatViewModel(geminiService, mockPreferences, mockAuth)

        composeTestRule.setContent {
            HearUTheme {
                AIChatScreen(onNavigateBack = {}, viewModel = viewModel)
            }
        }

        composeTestRule.onNodeWithText("HearU AI Companion").assertIsDisplayed()
        composeTestRule.onNodeWithText("Empathetic, non-judgmental listening").assertIsDisplayed()
        composeTestRule.onNodeWithText("HearU AI Companion is an emotional sounding board, not a therapist.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Get Help").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Hello, I'm your HearU AI Companion. I'm here 24/7 to listen without judgment. How is your heart feeling today?"
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText("🌬️ Can we do a breathing exercise?").assertIsDisplayed()
        composeTestRule.onNodeWithText("😰 I'm having acute anxiety right now").assertIsDisplayed()
    }

    @Test
    fun tier1_sendMessage_displaysUserAndAiReplyWithCorrectAlignment() {
        runBlocking {
            val reply = AiResult.Success("Okay.", isCrisisDetected = false)
            whenever(geminiService.generateEmpatheticResponse("Hello there")).thenReturn(reply)
            whenever(geminiService.generateEmpatheticResponse(eq("Hello there"), any())).thenReturn(reply)
        }
        val viewModel = AIChatViewModel(geminiService, mockPreferences, mockAuth)

        composeTestRule.setContent {
            HearUTheme {
                AIChatScreen(onNavigateBack = {}, viewModel = viewModel)
            }
        }

        composeTestRule.onNode(hasSetTextAction()).performTextInput("Hello there")
        composeTestRule.onNodeWithContentDescription("Send").performClick()

        composeTestRule.onNodeWithText("Hello there").assertIsDisplayed()
        scrollAiChatListTo(hasText("Okay."))
        composeTestRule.onNodeWithText("Okay.").assertIsDisplayed()

        val aiBounds = composeTestRule.onNodeWithText("Okay.").fetchSemanticsNode().boundsInRoot
        val userBounds = composeTestRule.onNodeWithText("Hello there").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Expected AI companion bubble aligned left and user bubble aligned right (ai=$aiBounds, user=$userBounds)",
            aiBounds.left < userBounds.left && aiBounds.right < userBounds.right
        )
    }

    @Test
    fun tier1_suggestionChips_populatePromptOrNavigateToBreathing_andHideAfterFirstTurn() {
        runBlocking {
            val reply = AiResult.Success("Breathe slowly.", isCrisisDetected = false)
            whenever(geminiService.generateEmpatheticResponse(any())).thenReturn(reply)
            whenever(geminiService.generateEmpatheticResponse(any(), any())).thenReturn(reply)
        }
        val viewModel = AIChatViewModel(geminiService, mockPreferences, mockAuth)
        var navigatedToBreathing = false

        composeTestRule.setContent {
            HearUTheme {
                AIChatScreen(
                    onNavigateBack = {},
                    onNavigateToBreathing = { navigatedToBreathing = true },
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.onNodeWithText("🌬️ Can we do a breathing exercise?").performClick()
        assertTrue(navigatedToBreathing)

        composeTestRule.onNodeWithText("😰 I'm having acute anxiety right now").performClick()
        composeTestRule.onNode(hasSetTextAction()).assertTextContains("I'm having acute anxiety right now")

        composeTestRule.onNodeWithContentDescription("Send").performClick()

        assertEquals(3, viewModel.messages.value.size)
        scrollAiChatListTo(hasText("Breathe slowly."))
        composeTestRule.onNodeWithText("Breathe slowly.").assertIsDisplayed()
        composeTestRule.onNodeWithText("🌙 Having trouble falling asleep").assertDoesNotExist()
    }

    @Test
    fun tier1_multiTurnConversation_preservesFullOrderedTranscriptAndAutoScrolls() {
        var turnCounter = 0
        runBlocking {
            whenever(geminiService.generateEmpatheticResponse(any())).thenAnswer {
                turnCounter++
                AiResult.Success("AI reply turn $turnCounter", isCrisisDetected = false)
            }
            whenever(geminiService.generateEmpatheticResponse(any(), any())).thenAnswer {
                turnCounter++
                AiResult.Success("AI reply turn $turnCounter", isCrisisDetected = false)
            }
        }
        val viewModel = AIChatViewModel(geminiService, mockPreferences, mockAuth)

        composeTestRule.setContent {
            HearUTheme {
                AIChatScreen(onNavigateBack = {}, viewModel = viewModel)
            }
        }

        repeat(10) { idx ->
            // TODO(M3/M4): Pre-M3 AIChatViewModel.kt:72,89 uses System.currentTimeMillis() as LazyColumn key;
            // keep Thread.sleep(2) to prevent key collisions until M3 switches to UUID/monotonic IDs.
            Thread.sleep(2)
            composeTestRule.onNode(hasSetTextAction()).performTextInput("User turn ${idx + 1}")
            composeTestRule.onNodeWithContentDescription("Send").performClick()
            composeTestRule.waitForIdle()
        }

        val transcript = viewModel.messages.value
        assertEquals(21, transcript.size)
        assertEquals("ai_welcome", transcript.first().id)
        assertEquals("User turn 10", transcript[19].text)
        assertEquals("AI reply turn 10", transcript[20].text)

        composeTestRule.onNodeWithText("AI reply turn 10").assertIsDisplayed()
    }

    @Test
    fun tier1_crisisResponseOrTag_opensCrisisSupportDialogAndAllowsDismiss() {
        runBlocking {
            val crisisReply = AiResult.Success("Please call 988 right away.", isCrisisDetected = true)
            whenever(geminiService.generateEmpatheticResponse(any())).thenReturn(crisisReply)
            whenever(geminiService.generateEmpatheticResponse(any(), any())).thenReturn(crisisReply)
        }
        val viewModel = AIChatViewModel(geminiService, mockPreferences, mockAuth)

        composeTestRule.setContent {
            HearUTheme {
                AIChatScreen(onNavigateBack = {}, viewModel = viewModel)
            }
        }

        composeTestRule.onNode(hasSetTextAction()).performTextInput("I feel hopeless")
        composeTestRule.onNodeWithContentDescription("Send").performClick()

        composeTestRule.onNodeWithText("We're Here For You").assertIsDisplayed()
        composeTestRule.onNodeWithText("Call 988 Crisis Lifeline").assertIsDisplayed()
        composeTestRule.onNodeWithText("Chat with Lifeline Online").assertIsDisplayed()

        composeTestRule.onNodeWithText("Return to App").performClick()
        assertFalse(viewModel.crisisEvent.value)
        composeTestRule.onNodeWithText("We're Here For You").assertDoesNotExist()

        composeTestRule.onNodeWithText("Get Help").performClick()
        composeTestRule.onNodeWithText("We're Here For You").assertIsDisplayed()
        composeTestRule.onNodeWithText("Return to App").performClick()
        composeTestRule.onNodeWithText("We're Here For You").assertDoesNotExist()
    }

    @Test
    fun tier1_realDataStoreQuota_49To50Allowed_thenShowsLimitBanner() = runTest {
        appContext.dataStore.edit { prefs ->
            prefs[intPreferencesKey("ai_msg_count")] = 49
            prefs[longPreferencesKey("ai_msg_date")] = todayStartEpoch()
        }
        val quotaReply = AiResult.Success("Message 50 reply", isCrisisDetected = false)
        whenever(geminiService.generateEmpatheticResponse(any())).thenReturn(quotaReply)
        whenever(geminiService.generateEmpatheticResponse(any(), any())).thenReturn(quotaReply)

        val realPrefs = RolePreferences(appContext)
        val viewModel = AIChatViewModel(geminiService, realPrefs, mockAuth)

        composeTestRule.setContent {
            HearUTheme {
                AIChatScreen(onNavigateBack = {}, viewModel = viewModel)
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 10_000L) { viewModel.quotaUsed.value == 49 }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Daily quota: 49/50 messages used").assertIsDisplayed()
        composeTestRule.onNode(hasSetTextAction()).performTextInput("50th message")
        composeTestRule.onNodeWithContentDescription("Send").performClick()
        composeTestRule.waitUntil(timeoutMillis = 10_000L) {
            !viewModel.isLoading.value && viewModel.quotaUsed.value == 50
        }
        composeTestRule.waitForIdle()

        assertEquals(50, viewModel.quotaUsed.value)
        assertTrue(viewModel.quotaExhausted.value)
        scrollAiChatListTo(hasText("Message 50 reply"))
        composeTestRule.onNodeWithText("Message 50 reply").assertIsDisplayed()
        composeTestRule.onNodeWithText("Daily AI limit reached (50/50). Resets tomorrow.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Daily quota reached").assertIsDisplayed()
    }

    // ==================== TIER 2: BOUNDARY / CORNER / ERROR ====================

    @Test
    fun tier2_quotaBoundary_51stMessageBlockedInViewModelAndUi() = runTest {
        appContext.dataStore.edit { prefs ->
            prefs[intPreferencesKey("ai_msg_count")] = 50
            prefs[longPreferencesKey("ai_msg_date")] = todayStartEpoch()
        }

        val realPrefs = RolePreferences(appContext)
        val viewModel = AIChatViewModel(geminiService, realPrefs, mockAuth)

        composeTestRule.setContent {
            HearUTheme {
                AIChatScreen(onNavigateBack = {}, viewModel = viewModel)
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 10_000L) { viewModel.quotaExhausted.value }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Daily quota reached").assertIsDisplayed()
        composeTestRule.onNode(hasSetTextAction()).assertIsNotEnabled()
        composeTestRule.onNodeWithContentDescription("Send").assertIsNotEnabled()

        viewModel.sendMessage("51st attempt")
        composeTestRule.waitForIdle()

        verify(geminiService, never()).generateEmpatheticResponse(any())
        verify(geminiService, never()).generateEmpatheticResponse(any(), any())
        assertEquals(1, viewModel.messages.value.size)
        assertTrue(viewModel.quotaExhausted.value)
    }

    @Test
    fun tier2_dailyQuotaReset_yesterdayExhaustedQuotaResetsToZeroToday() = runTest {
        appContext.dataStore.edit { prefs ->
            prefs[intPreferencesKey("ai_msg_count")] = 50
            prefs[longPreferencesKey("ai_msg_date")] = yesterdayStartEpoch()
        }
        val freshReply = AiResult.Success("Welcome to a new day.", isCrisisDetected = false)
        whenever(geminiService.generateEmpatheticResponse("Fresh day")).thenReturn(freshReply)
        whenever(geminiService.generateEmpatheticResponse(eq("Fresh day"), any())).thenReturn(freshReply)

        val realPrefs = RolePreferences(appContext)
        val viewModel = AIChatViewModel(geminiService, realPrefs, mockAuth)

        assertEquals(0, viewModel.quotaUsed.value)
        assertFalse(viewModel.quotaExhausted.value)

        composeTestRule.setContent {
            HearUTheme {
                AIChatScreen(onNavigateBack = {}, viewModel = viewModel)
            }
        }

        composeTestRule.onNode(hasSetTextAction()).performTextInput("Fresh day")
        composeTestRule.onNodeWithContentDescription("Send").performClick()
        composeTestRule.waitUntil(timeoutMillis = 10_000L) {
            !viewModel.isLoading.value && viewModel.messages.value.size >= 3
        }
        composeTestRule.waitForIdle()

        assertEquals(1, viewModel.quotaUsed.value)
        assertFalse(viewModel.quotaExhausted.value)
        scrollAiChatListTo(hasText("Welcome to a new day."))
        composeTestRule.onNodeWithText("Welcome to a new day.").assertIsDisplayed()
    }

    @Test
    fun tier2_loadingConcurrencyGuard_disablesSendAndIgnoresConcurrentCalls() = runTest {
        val deferred = CompletableDeferred<AiResult>()
        whenever(geminiService.generateEmpatheticResponse(any())).doSuspendableAnswer {
            deferred.await()
        }
        whenever(geminiService.generateEmpatheticResponse(any(), any())).doSuspendableAnswer {
            deferred.await()
        }
        val viewModel = AIChatViewModel(geminiService, mockPreferences, mockAuth)

        composeTestRule.setContent {
            HearUTheme {
                AIChatScreen(onNavigateBack = {}, viewModel = viewModel)
            }
        }

        composeTestRule.onNode(hasSetTextAction()).performTextInput("First message")
        composeTestRule.onNodeWithContentDescription("Send").performClick()

        scrollAiChatListTo(hasText("Thinking..."))
        composeTestRule.onNodeWithText("Thinking...").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Send").assertIsNotEnabled()

        viewModel.sendMessage("Second concurrent message")
        val aiCalls = Mockito.mockingDetails(geminiService).invocations
            .count { it.method.name == "generateEmpatheticResponse" }
        assertEquals(1, aiCalls)
        deferred.complete(AiResult.Success("Reply 1", isCrisisDetected = false))
        composeTestRule.waitForIdle()

        assertEquals(1, viewModel.messages.value.count { it.senderId != "ai_companion" })
        scrollAiChatListTo(hasText("Reply 1"))
        composeTestRule.onNodeWithText("Reply 1").assertIsDisplayed()
    }

    @Test
    fun tier2_crisisKeywordOnApiOrSafetyError_stillTriggersCrisisSupportDialog() {
        val realGeminiService = GeminiAiService()
        val viewModel = AIChatViewModel(realGeminiService, mockPreferences, mockAuth)

        composeTestRule.setContent {
            HearUTheme {
                AIChatScreen(onNavigateBack = {}, viewModel = viewModel)
            }
        }

        composeTestRule.onNode(hasSetTextAction()).performTextInput("I want to kill myself")
        composeTestRule.onNodeWithContentDescription("Send").performClick()

        composeTestRule.waitUntil(timeoutMillis = 10_000L) { !viewModel.isLoading.value }

        assertTrue(
            "Expected crisisEvent=true even when Gemini API fails/errors on crisis keyword",
            viewModel.crisisEvent.value
        )
        composeTestRule.onNodeWithText("We're Here For You").assertIsDisplayed()
    }

    @Test
    fun tier2_longPromptOver500Chars_truncatesSafelyAt500Boundary() {
        val longSafePrompt = "x".repeat(650)
        runBlocking {
            val safeReply = AiResult.Success("Received safely.", isCrisisDetected = false)
            whenever(geminiService.generateEmpatheticResponse(longSafePrompt)).thenReturn(safeReply)
            whenever(geminiService.generateEmpatheticResponse(eq(longSafePrompt), any())).thenReturn(safeReply)
        }

        val viewModel = AIChatViewModel(geminiService, mockPreferences, mockAuth)
        composeTestRule.setContent {
            HearUTheme {
                AIChatScreen(onNavigateBack = {}, viewModel = viewModel)
            }
        }

        viewModel.sendMessage(longSafePrompt)
        composeTestRule.waitForIdle()

        scrollAiChatListTo(hasText(longSafePrompt))
        composeTestRule.onNodeWithText(longSafePrompt).assertIsDisplayed()
        scrollAiChatListTo(hasText("Received safely."))
        composeTestRule.onNodeWithText("Received safely.").assertIsDisplayed()

        val realService = GeminiAiService()
        val afterBoundaryVm = AIChatViewModel(realService, mockPreferences, mockAuth)
        afterBoundaryVm.sendMessage("a".repeat(500) + " kill myself")
        composeTestRule.waitUntil(timeoutMillis = 10_000L) { !afterBoundaryVm.isLoading.value }
        assertFalse(
            "Keyword beyond 500 chars must not trigger crisis when truncated at 500",
            afterBoundaryVm.crisisEvent.value
        )

        val withinBoundaryVm = AIChatViewModel(realService, mockPreferences, mockAuth)
        withinBoundaryVm.sendMessage("kill myself " + "a".repeat(550))
        composeTestRule.waitUntil(timeoutMillis = 10_000L) { !withinBoundaryVm.isLoading.value }
        assertTrue(
            "Crisis keyword within first 500 chars of >500-char prompt must trigger crisis",
            withinBoundaryVm.crisisEvent.value
        )
    }

    @Test
    fun tier2_apiErrorFallback_displaysFriendlyMessageAndDoesNotConsumeQuota() = runTest {
        val realPrefs = RolePreferences(appContext)
        val apiErr = AiResult.Error("503 Unavailable")
        whenever(geminiService.generateEmpatheticResponse("Hello")).thenReturn(apiErr)
        whenever(geminiService.generateEmpatheticResponse(eq("Hello"), any())).thenReturn(apiErr)
        val viewModel = AIChatViewModel(geminiService, realPrefs, mockAuth)

        composeTestRule.setContent {
            HearUTheme {
                AIChatScreen(onNavigateBack = {}, viewModel = viewModel)
            }
        }

        composeTestRule.onNode(hasSetTextAction()).performTextInput("Hello")
        composeTestRule.onNodeWithContentDescription("Send").performClick()
        composeTestRule.waitUntil(timeoutMillis = 10_000L) {
            !viewModel.isLoading.value && viewModel.messages.value.size >= 3
        }
        composeTestRule.waitForIdle()

        val fallbackText =
            "I'm having a brief connection issue, but please know you're not alone. Feel free to try again or reach out to our human listeners."
        scrollAiChatListTo(hasText(fallbackText))
        composeTestRule.onNodeWithText(fallbackText).assertIsDisplayed()
        assertFalse(viewModel.isLoading.value)
        assertEquals(0, viewModel.quotaUsed.value)
        assertEquals(0, realPrefs.getAiMessagesUsedToday())
    }

    @Test
    fun tier2_blankAndWhitespacePrompts_ignoredWithoutCallingAiOrConsumingQuota() = runTest {
        val viewModel = AIChatViewModel(geminiService, mockPreferences, mockAuth)

        composeTestRule.setContent {
            HearUTheme {
                AIChatScreen(onNavigateBack = {}, viewModel = viewModel)
            }
        }

        composeTestRule.onNode(hasSetTextAction()).performTextInput("   \n\t  ")
        composeTestRule.onNodeWithContentDescription("Send").assertIsNotEnabled()

        viewModel.sendMessage("   ")
        composeTestRule.waitForIdle()

        verify(geminiService, never()).generateEmpatheticResponse(any())
        verify(geminiService, never()).generateEmpatheticResponse(any(), any())
        verify(mockPreferences, never()).tryConsumeAiQuota(any())
        assertEquals(1, viewModel.messages.value.size)
    }
}
