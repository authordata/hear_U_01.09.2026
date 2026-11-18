package com.hearu.app.e2e.tier1_2

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.hearu.app.data.RolePreferences
import com.hearu.app.e2e.harness.E2ETestHarness
import com.hearu.app.e2e.harness.MainDispatcherRule
import com.hearu.app.navigation.HearUNavigation
import com.hearu.app.repository.AuthRepository
import com.hearu.app.ui.auth.AuthViewModel
import com.hearu.app.ui.onboarding.OnboardingScreen
import com.hearu.app.ui.theme.HearUTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class OnboardingE2ETest {

    @get:Rule(order = 0)
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule(order = 1)
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var rolePreferences: RolePreferences
    private lateinit var mockAuthRepository: AuthRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        runBlocking {
            E2ETestHarness.clearRolePreferences(context)
        }
        rolePreferences = E2ETestHarness.createRolePreferences(context)
        mockAuthRepository = mock()
        whenever(mockAuthRepository.currentUser).thenReturn(null)
    }

    @After
    fun tearDown() {
        runBlocking {
            E2ETestHarness.clearRolePreferences(context)
        }
    }

    // ==================== TIER 1: HAPPY PATHS ====================

    @Test
    fun tier1_initialPageDisplaysTitleSubtitleSkipAndContinue() {
        composeTestRule.setContent {
            HearUTheme {
                OnboardingScreen(onFinishOnboarding = {})
            }
        }

        composeTestRule.onNodeWithText("A Safe, Anonymous Haven").assertIsDisplayed()
        composeTestRule.onNodeWithText("100% Confidential Peer Support").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("A Safe, Anonymous Haven").assertIsDisplayed()
        composeTestRule.onNodeWithText("Skip").assertIsDisplayed().assertIsEnabled()
        composeTestRule.onNodeWithText("Continue").assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun tier1_continueButtonAdvancesThroughAllThreePages() {
        composeTestRule.setContent {
            HearUTheme {
                OnboardingScreen(onFinishOnboarding = {})
            }
        }

        composeTestRule.onNodeWithText("Continue").performClick()
        composeTestRule.onNodeWithText("24/7 AI Companion & SOS").assertIsDisplayed()
        composeTestRule.onNodeWithText("Skip").assertIsDisplayed()

        composeTestRule.onNodeWithText("Continue").performClick()
        composeTestRule.onNodeWithText("30-Day Auto Purge Guarantee").assertIsDisplayed()
        composeTestRule.onNodeWithText("Your Privacy is Absolute").assertIsDisplayed()
        composeTestRule.onNodeWithText("Get Started").assertIsDisplayed().assertIsEnabled()
        composeTestRule.onNodeWithText("Skip").assertDoesNotExist()
    }

    @Test
    fun tier1_getStartedOnFinalPageInvokesCallback() {
        var finishedCount = 0
        composeTestRule.setContent {
            HearUTheme {
                OnboardingScreen(onFinishOnboarding = { finishedCount++ })
            }
        }

        composeTestRule.onNodeWithText("Continue").performClick()
        composeTestRule.onNodeWithText("Continue").performClick()
        composeTestRule.onNodeWithText("Get Started").performClick()

        assertEquals(1, finishedCount)
    }

    @Test
    fun tier1_skipOnFirstPageInvokesCallbackImmediately() {
        var finishedCount = 0
        composeTestRule.setContent {
            HearUTheme {
                OnboardingScreen(onFinishOnboarding = { finishedCount++ })
            }
        }

        composeTestRule.onNodeWithText("Skip").performClick()
        assertEquals(1, finishedCount)
    }

    @Test
    fun tier1_skipOnSecondPageInvokesCallbackImmediately() {
        var finishedCount = 0
        composeTestRule.setContent {
            HearUTheme {
                OnboardingScreen(onFinishOnboarding = { finishedCount++ })
            }
        }

        composeTestRule.onNodeWithText("Continue").performClick()
        composeTestRule.onNodeWithText("24/7 AI Companion & SOS").assertIsDisplayed()
        composeTestRule.onNodeWithText("Skip").performClick()

        assertEquals(1, finishedCount)
    }

    @Test
    fun tier1_navigationOnboardingFlowPersistsFlagAndRoutesToLogin() {
        val authViewModel = AuthViewModel(mockAuthRepository, rolePreferences)

        composeTestRule.setContent {
            HearUTheme {
                HearUNavigation(authViewModel = authViewModel)
            }
        }

        composeTestRule.onNodeWithText("A Safe, Anonymous Haven").assertIsDisplayed()
        composeTestRule.onNodeWithText("Skip").performClick()

        composeTestRule.onNodeWithText("Welcome to HearU").assertIsDisplayed()
        composeTestRule.onNodeWithTag("email_field").assertIsDisplayed()

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            runBlocking { rolePreferences.onboardingCompletedFlow.first() }
        }
        assertTrue(runBlocking { rolePreferences.onboardingCompletedFlow.first() })
    }

    // ==================== TIER 2: BOUNDARY / CORNER / ERROR ====================

    @Test
    fun tier2_onboardingCompletedPersistsAcrossFreshRolePreferencesInstance() = runBlocking {
        assertFalse(rolePreferences.onboardingCompletedFlow.first())

        rolePreferences.setOnboardingCompleted(true)
        val freshPreferences = E2ETestHarness.createRolePreferences(context)

        assertTrue(freshPreferences.onboardingCompletedFlow.first())
    }

    @Test
    fun tier2_swipeLeftAndRightNavigatesBetweenPages() {
        composeTestRule.setContent {
            HearUTheme {
                OnboardingScreen(onFinishOnboarding = {})
            }
        }

        composeTestRule.onNodeWithText("A Safe, Anonymous Haven").assertIsDisplayed()
        composeTestRule.onRoot().performTouchInput {
            swipe(
                start = Offset(right * 0.85f, centerY),
                end = Offset(right * 0.15f, centerY)
            )
        }
        composeTestRule.onNodeWithText("24/7 AI Companion & SOS").assertIsDisplayed()

        composeTestRule.onRoot().performTouchInput {
            swipe(
                start = Offset(right * 0.15f, centerY),
                end = Offset(right * 0.85f, centerY)
            )
        }
        composeTestRule.onNodeWithText("A Safe, Anonymous Haven").assertIsDisplayed()
    }

    @Test
    fun tier2_skipButtonDoesNotExistOnFinalPage() {
        composeTestRule.setContent {
            HearUTheme {
                OnboardingScreen(onFinishOnboarding = {})
            }
        }

        composeTestRule.onNodeWithText("Continue").performClick()
        composeTestRule.onNodeWithText("Continue").performClick()
        composeTestRule.onNodeWithText("Get Started").assertIsDisplayed()
        composeTestRule.onNodeWithText("Skip").assertDoesNotExist()
    }

    @Test
    fun tier2_continueOnFirstPageDoesNotPrematurelyFinish() {
        var finishedCount = 0
        composeTestRule.setContent {
            HearUTheme {
                OnboardingScreen(onFinishOnboarding = { finishedCount++ })
            }
        }

        composeTestRule.onNodeWithText("Continue").performClick()
        assertEquals(0, finishedCount)

        composeTestRule.onNodeWithText("Continue").performClick()
        assertEquals(0, finishedCount)

        composeTestRule.onNodeWithText("Get Started").performClick()
        assertEquals(1, finishedCount)
    }

    @Test
    fun tier2_returningUserWithCompletedOnboardingStartsAtLoginWithoutOnboardingFlash() {
        runBlocking {
            rolePreferences.setOnboardingCompleted(true)
        }
        val authViewModel = AuthViewModel(mockAuthRepository, rolePreferences)

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            HearUTheme {
                HearUNavigation(authViewModel = authViewModel)
            }
        }
        composeTestRule.mainClock.advanceTimeByFrame()

        composeTestRule.onNodeWithText("A Safe, Anonymous Haven").assertDoesNotExist()
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            runCatching {
                composeTestRule.onNodeWithText("Welcome to HearU").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeTestRule.onNodeWithTag("email_field").assertIsDisplayed()
    }

    @Test
    fun tier2_compactViewportWithLargeFontScaleKeepsContentAndButtonAccessible() {
        composeTestRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = baseDensity.density, fontScale = 1.6f)
            ) {
                HearUTheme {
                    Box(modifier = Modifier.size(width = 320.dp, height = 380.dp)) {
                        OnboardingScreen(onFinishOnboarding = {})
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("Connect with empathetic listeners", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        val continueButton = composeTestRule.onNodeWithText("Continue")
        runCatching { continueButton.performScrollTo() }
        continueButton.assertIsDisplayed().assertIsEnabled()
    }
}
