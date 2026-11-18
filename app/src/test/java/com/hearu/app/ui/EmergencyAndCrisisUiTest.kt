package com.hearu.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.hearu.app.ui.dialogs.CrisisSupportDialog
import com.hearu.app.ui.emergency.EmergencyScreen
import com.hearu.app.ui.theme.HearUTheme
import com.hearu.app.ui.tools.BreathingExerciseScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EmergencyAndCrisisUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun noActivityContext(base: Context): Context = object : ContextWrapper(base) {
        override fun startActivity(intent: Intent?) {
            throw ActivityNotFoundException("No activity found")
        }

        override fun startActivity(intent: Intent?, options: Bundle?) {
            throw ActivityNotFoundException("No activity found")
        }
    }

    @Test
    fun emergencyScreen_showsSnackbarFallbackWhenNoIntentHandlerExists() {
        composeTestRule.setContent {
            val base = LocalContext.current
            CompositionLocalProvider(LocalContext provides noActivityContext(base)) {
                HearUTheme {
                    EmergencyScreen(onNavigateBack = {})
                }
            }
        }

        composeTestRule.onAllNodesWithText("Call Now")[0].performClick()
        composeTestRule.onNodeWithText("No phone app found. Please dial 988 directly.").assertIsDisplayed()
    }

    @Test
    fun crisisSupportDialog_showsInlineFallbackWhenNoBrowserOrDialerExists() {
        composeTestRule.setContent {
            val base = LocalContext.current
            CompositionLocalProvider(LocalContext provides noActivityContext(base)) {
                HearUTheme {
                    CrisisSupportDialog(onDismiss = {})
                }
            }
        }

        composeTestRule.onNodeWithText("Call 988 Crisis Lifeline").performClick()
        composeTestRule.onNodeWithText("No phone app found. Please dial 988 directly.").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [34], application = android.app.Application::class, qualifiers = "w480dp-h320dp-land")
    fun breathingExerciseScreen_preservesRemainingSecondsOnPauseAndScrollsInLandscape() {
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
        composeTestRule.onNodeWithText("4").assertIsDisplayed()

        composeTestRule.mainClock.advanceTimeBy(2100L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("2").assertIsDisplayed()

        composeTestRule.onNodeWithText("Pause").performClick()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithText("Begin Session").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ready").assertIsDisplayed()

        // Resuming preserves the paused remaining seconds instead of resetting to 4
        composeTestRule.onNodeWithText("Begin Session").performClick()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.onNodeWithText("Pause").assertIsDisplayed()
        composeTestRule.onNodeWithText("2").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Reset Exercise")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    @Config(sdk = [34], application = android.app.Application::class, qualifiers = "w480dp-h320dp-land")
    fun breathingExerciseScreen_compactLandscape_genuinelyScrollsWhenOverflowing() {
        composeTestRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = baseDensity.density, fontScale = 1.6f)
            ) {
                HearUTheme {
                    Box(modifier = Modifier.size(width = 480.dp, height = 220.dp)) {
                        BreathingExerciseScreen(onNavigateBack = {})
                    }
                }
            }
        }

        val scrollMatcher = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
        val rangeBefore = composeTestRule.onNode(scrollMatcher)
            .fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue(
            "Expected compact landscape scroll maxValue > 0 when overflowing, was ${rangeBefore.maxValue()}",
            rangeBefore.maxValue() > 0f
        )
        assertEquals(0f, rangeBefore.value(), 0.01f)
        composeTestRule.onNodeWithText("Begin Session").assertIsNotDisplayed()

        composeTestRule.onNodeWithText("Begin Session")
            .performScrollTo()
            .assertIsDisplayed()

        val rangeAfter = composeTestRule.onNode(scrollMatcher)
            .fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue(
            "Expected scroll offset > 0 after performScrollTo, was ${rangeAfter.value()}",
            rangeAfter.value() > 0f
        )

        composeTestRule.onNodeWithText("Begin Session").performClick()
        composeTestRule.onNodeWithText("Pause").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Reset Exercise")
            .performScrollTo()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }
}
