package com.hearu.app.e2e.tier1_2

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.testing.TestNavHostController
import com.hearu.app.navigation.Screen
import com.hearu.app.ui.emergency.EmergencyScreen
import com.hearu.app.ui.theme.HearUTheme
import com.hearu.app.ui.tools.BreathingExerciseScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h915dp")
class WellnessToolsE2ETest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun advanceBreathingSeconds(seconds: Int, extraTailMs: Long = 100L) {
        repeat(seconds) {
            composeTestRule.mainClock.advanceTimeBy(1000L)
            composeTestRule.mainClock.advanceTimeByFrame()
        }
        if (extraTailMs > 0L) {
            composeTestRule.mainClock.advanceTimeBy(extraTailMs)
            composeTestRule.mainClock.advanceTimeByFrame()
        }
    }

    // ==================== TIER 1: HAPPY PATH ====================

    @Test
    fun tier1_initialScreen_rendersTopBarTabsAndReadyBreathingState() {
        composeTestRule.setContent {
            HearUTheme {
                BreathingExerciseScreen(onNavigateBack = {}, onEmergencyClick = {})
            }
        }

        composeTestRule.onNodeWithText("Calming & Grounding Tools").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Back").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Crisis Hub").assertIsDisplayed()
        composeTestRule.onNodeWithText("4-7-8 Breathing").assertIsDisplayed()
        composeTestRule.onNodeWithText("5-4-3-2-1 Grounding").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cycles Completed: 0").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ready").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tap Start").assertIsDisplayed()
        composeTestRule.onNodeWithText("Begin Session").assertIsDisplayed()
        composeTestRule.onNodeWithText("Inhale quietly through your nose (4s)").assertIsDisplayed()
    }

    @Test
    fun tier1_breathing478_progressesThroughAllPhasesAndIncrementsCycleCount() {
        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setContent {
            HearUTheme {
                BreathingExerciseScreen(onNavigateBack = {}, onEmergencyClick = {})
            }
        }

        composeTestRule.onNodeWithText("Begin Session").performClick()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(200L)
        composeTestRule.mainClock.advanceTimeByFrame()

        composeTestRule.onNodeWithText("Breathe In Deeply...").assertIsDisplayed()
        composeTestRule.onNodeWithText("Inhale quietly through your nose (4s)").assertIsDisplayed()

        // Complete 4s INHALE -> HOLD (7s)
        advanceBreathingSeconds(4)
        composeTestRule.onNodeWithText("Hold Gently...").assertIsDisplayed()
        composeTestRule.onNodeWithText("Hold your breath calmly without tension (7s)").assertIsDisplayed()

        // Complete 7s HOLD -> EXHALE (8s)
        advanceBreathingSeconds(7)
        composeTestRule.onNodeWithText("Exhale Completely...").assertIsDisplayed()
        composeTestRule.onNodeWithText("Exhale completely through your mouth with a whoosh (8s)")
            .assertIsDisplayed()

        // Complete 8s EXHALE -> REST (2s)
        advanceBreathingSeconds(8)
        composeTestRule.onNodeWithText("Rest & Settle...").assertIsDisplayed()
        composeTestRule.onNodeWithText("Rest and reset (2s)").assertIsDisplayed()

        // Complete 2s REST -> Cycle 1 completed, back to INHALE
        advanceBreathingSeconds(2)
        composeTestRule.onNodeWithText("Cycles Completed: 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Breathe In Deeply...").assertIsDisplayed()
    }

    @Test
    fun tier1_breathing478_resetRestoresInitialStateAfterCompletedCycle() {
        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setContent {
            HearUTheme {
                BreathingExerciseScreen(onNavigateBack = {}, onEmergencyClick = {})
            }
        }

        composeTestRule.onNodeWithText("Begin Session").performClick()
        composeTestRule.mainClock.advanceTimeByFrame()
        advanceBreathingSeconds(21)

        composeTestRule.onNodeWithText("Cycles Completed: 1").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Reset Exercise").performClick()
        composeTestRule.mainClock.advanceTimeByFrame()

        composeTestRule.onNodeWithText("Ready").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tap Start").assertIsDisplayed()
        composeTestRule.onNodeWithText("Begin Session").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cycles Completed: 0").assertIsDisplayed()
        composeTestRule.onNodeWithText("Inhale quietly through your nose (4s)").assertIsDisplayed()
    }

    @Test
    fun tier1_grounding54321_progressesThroughAllFiveSensesAndRestarts() {
        composeTestRule.setContent {
            HearUTheme {
                BreathingExerciseScreen(onNavigateBack = {}, onEmergencyClick = {})
            }
        }

        composeTestRule.onNodeWithText("5-4-3-2-1 Grounding").performClick()

        composeTestRule.onNodeWithText("5 Things You Can SEE").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("I've Found All 5 → Next Sense").performScrollTo().performClick()

        composeTestRule.onNodeWithText("4 Things You Can TOUCH").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("I've Found All 4 → Next Sense").performScrollTo().performClick()

        composeTestRule.onNodeWithText("3 Things You Can HEAR").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("I've Found All 3 → Next Sense").performScrollTo().performClick()

        composeTestRule.onNodeWithText("2 Things You Can SMELL").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("I've Found All 2 → Next Sense").performScrollTo().performClick()

        composeTestRule.onNodeWithText("1 Thing You Can TASTE").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Complete Grounding Session").performScrollTo().performClick()

        composeTestRule.onNodeWithText("You Are Here. You Are Safe.").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Complete").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Start Over").performScrollTo().performClick()

        composeTestRule.onNodeWithText("5 Things You Can SEE").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun tier1_topAppBar_sosAndBackCallbacksInvokeHandlers() {
        var backClicked = false
        var sosClicked = false

        composeTestRule.setContent {
            HearUTheme {
                BreathingExerciseScreen(
                    onNavigateBack = { backClicked = true },
                    onEmergencyClick = { sosClicked = true }
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Crisis Hub").performClick()
        assertTrue(sosClicked)

        composeTestRule.onNodeWithContentDescription("Back").performClick()
        assertTrue(backClicked)
    }

    @Test
    fun tier1_navigationIntegration_sosRoutesFromBreathingToEmergencyHub() {
        val navController = TestNavHostController(RuntimeEnvironment.getApplication()).apply {
            navigatorProvider.addNavigator(ComposeNavigator())
        }

        composeTestRule.setContent {
            HearUTheme {
                NavHost(
                    navController = navController,
                    startDestination = Screen.BreathingExercise.route
                ) {
                    composable(Screen.BreathingExercise.route) {
                        BreathingExerciseScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onEmergencyClick = { navController.navigate(Screen.EmergencyHub.route) }
                        )
                    }
                    composable(Screen.EmergencyHub.route) {
                        EmergencyScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToBreathing = { navController.navigate(Screen.BreathingExercise.route) }
                        )
                    }
                }
            }
        }

        composeTestRule.onNodeWithContentDescription("Crisis Hub").performClick()

        assertEquals(Screen.EmergencyHub.route, navController.currentBackStackEntry?.destination?.route)
        composeTestRule.onNodeWithText("24/7 Crisis & Emergency Hub").assertIsDisplayed()
    }

    // ==================== TIER 2: BOUNDARY / CORNER / ERROR ====================

    @Test
    fun tier2_breathing478_pauseAndResume_preservesRemainingSeconds() {
        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setContent {
            HearUTheme {
                BreathingExerciseScreen(onNavigateBack = {})
            }
        }

        composeTestRule.onNodeWithText("Begin Session").performClick()
        composeTestRule.mainClock.advanceTimeByFrame()

        // Advance 2s into 4s INHALE -> 2 seconds remaining
        advanceBreathingSeconds(2, extraTailMs = 50L)
        composeTestRule.onNodeWithText("2").assertIsDisplayed()
        composeTestRule.onNodeWithText("Breathe In Deeply...").assertIsDisplayed()

        // Pause and wait 3s -> must remain paused without advancing phase
        composeTestRule.onNodeWithText("Pause").performClick()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(3000L)
        composeTestRule.mainClock.advanceTimeByFrame()

        composeTestRule.onNodeWithText("Begin Session").assertIsDisplayed()
        composeTestRule.onNodeWithText("Hold Gently...").assertDoesNotExist()

        // Resume -> remaining seconds must continue from 2 (not reset to 4)
        composeTestRule.onNodeWithText("Begin Session").performClick()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(100L)
        composeTestRule.mainClock.advanceTimeByFrame()

        composeTestRule.onNodeWithText("2").assertIsDisplayed()

        // Advance remaining 2s -> transitions into HOLD
        advanceBreathingSeconds(2, extraTailMs = 200L)
        composeTestRule.onNodeWithText("Hold Gently...").assertIsDisplayed()
    }

    @Test
    fun tier2_breathing478_stateSurvivesConfigurationChange() {
        composeTestRule.mainClock.autoAdvance = false
        val restorationTester = StateRestorationTester(composeTestRule)

        restorationTester.setContent {
            HearUTheme {
                BreathingExerciseScreen(onNavigateBack = {})
            }
        }

        composeTestRule.onNodeWithText("Begin Session").performClick()
        composeTestRule.mainClock.advanceTimeByFrame()
        // Complete Cycle 1 (21s) + 4s INHALE + ~2s into Cycle 2 HOLD (leaving ~4-5s of 7s HOLD)
        advanceBreathingSeconds(21)
        composeTestRule.onNodeWithText("Cycles Completed: 1").assertIsDisplayed()
        advanceBreathingSeconds(6)

        composeTestRule.onNodeWithText("Hold Gently...").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pause").performClick()
        composeTestRule.mainClock.advanceTimeByFrame()

        composeTestRule.mainClock.autoAdvance = true
        restorationTester.emulateSavedInstanceStateRestore()
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.onNodeWithText("Cycles Completed: 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Hold your breath calmly without tension (7s)")
            .assertIsDisplayed()

        // Resume and advance 5s (< 7s full HOLD) -> must finish remaining HOLD into EXHALE
        composeTestRule.onNodeWithText("Begin Session").performClick()
        composeTestRule.mainClock.advanceTimeByFrame()
        advanceBreathingSeconds(5)
        composeTestRule.onNodeWithText("Exhale Completely...").assertIsDisplayed()
    }

    @Test
    fun tier2_grounding54321_stateAndSelectedTabSurviveConfigurationChange() {
        val restorationTester = StateRestorationTester(composeTestRule)

        restorationTester.setContent {
            HearUTheme {
                BreathingExerciseScreen(onNavigateBack = {})
            }
        }

        composeTestRule.onNodeWithText("5-4-3-2-1 Grounding").performClick()
        composeTestRule.onNodeWithText("I've Found All 5 → Next Sense").performScrollTo().performClick()
        composeTestRule.onNodeWithText("I've Found All 4 → Next Sense").performScrollTo().performClick()

        composeTestRule.onNodeWithText("3 Things You Can HEAR").performScrollTo().assertIsDisplayed()

        restorationTester.emulateSavedInstanceStateRestore()

        composeTestRule.onNodeWithText("3 Things You Can HEAR").performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(sdk = [34], application = Application::class, qualifiers = "w480dp-h320dp-land")
    fun tier2_breathing478_compactLandscapeViewport_controlsScrollableAndClickable() {
        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setContent {
            HearUTheme {
                BreathingExerciseScreen(onNavigateBack = {})
            }
        }

        composeTestRule.onNodeWithText("Begin Session")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeTestRule.mainClock.advanceTimeByFrame()

        composeTestRule.onNodeWithText("Pause").assertIsDisplayed()
    }

    @Test
    fun tier2_breathing478_resetWhenIdleAndRepeatedReset_remainsValid() {
        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setContent {
            HearUTheme {
                BreathingExerciseScreen(onNavigateBack = {})
            }
        }

        composeTestRule.onNodeWithContentDescription("Reset Exercise").performClick()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithContentDescription("Reset Exercise").performClick()
        composeTestRule.mainClock.advanceTimeByFrame()

        composeTestRule.onNodeWithText("Ready").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cycles Completed: 0").assertIsDisplayed()
        composeTestRule.onNodeWithText("Begin Session").assertIsDisplayed()

        composeTestRule.onNodeWithText("Begin Session").performClick()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(500L)
        composeTestRule.mainClock.advanceTimeByFrame()

        composeTestRule.onNodeWithContentDescription("Reset Exercise").performClick()
        composeTestRule.mainClock.advanceTimeByFrame()

        composeTestRule.onNodeWithText("Ready").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cycles Completed: 0").assertIsDisplayed()
    }

    @Test
    fun tier2_breathing478_rapidStartPauseToggle_doesNotCorruptCountdown() {
        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setContent {
            HearUTheme {
                BreathingExerciseScreen(onNavigateBack = {})
            }
        }

        composeTestRule.onNodeWithText("Begin Session").performClick()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(100L)
        composeTestRule.mainClock.advanceTimeByFrame()

        composeTestRule.onNodeWithText("Pause").performClick()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithText("Begin Session").performClick()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithText("Pause").performClick()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithText("Begin Session").performClick()
        composeTestRule.mainClock.advanceTimeByFrame()

        advanceBreathingSeconds(5)

        composeTestRule.onNodeWithText("Cycles Completed: 0").assertIsDisplayed()
        composeTestRule.onNodeWithText("Hold Gently...").assertIsDisplayed()
    }
}
