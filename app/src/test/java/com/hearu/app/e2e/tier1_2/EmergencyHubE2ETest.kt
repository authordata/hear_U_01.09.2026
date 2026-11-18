package com.hearu.app.e2e.tier1_2

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.hearu.app.ui.dialogs.CrisisSupportDialog
import com.hearu.app.ui.emergency.EmergencyScreen
import com.hearu.app.ui.theme.HearUTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h915dp")
class EmergencyHubE2ETest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun registerUriHandler(action: String, scheme: String) {
        val app = RuntimeEnvironment.getApplication()
        val shadowPm = Shadows.shadowOf(app.packageManager)
        val component = ComponentName("com.hearu.test", "com.hearu.test.FakeHandler_${scheme}")
        shadowPm.addActivityIfNotPresent(component)
        val filter = IntentFilter(action).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addDataScheme(scheme)
        }
        shadowPm.addIntentFilterForActivity(component, filter)
    }

    private fun noHandlersContext(base: Context): Context = object : ContextWrapper(base) {
        override fun startActivity(intent: Intent?) {
            throw ActivityNotFoundException("No activity found to handle $intent")
        }

        override fun startActivity(intent: Intent?, options: Bundle?) {
            throw ActivityNotFoundException("No activity found to handle $intent")
        }
    }

    // ==================== TIER 1: HAPPY PATH ====================

    @Test
    fun tier1_emergencyScreen_rendersWarningAndAllFiveResources() {
        composeTestRule.setContent {
            HearUTheme {
                EmergencyScreen(onNavigateBack = {}, onNavigateToBreathing = {})
            }
        }

        composeTestRule.onNodeWithText("24/7 Crisis & Emergency Hub").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Urgent").assertIsDisplayed()
        composeTestRule.onNodeWithText("You Are Not Alone").assertIsDisplayed()
        composeTestRule.onNodeWithText("988 Suicide & Crisis Lifeline").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Crisis Text Line").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithText("Veterans Crisis Line").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("The Trevor Project (LGBTQ+ Youth)")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("International Resources (Outside North America)")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Take a 2-Minute Calming Breath (4-7-8 Exercise)")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun tier1_988Lifeline_callAndTextActionsLaunchExpectedIntents() {
        registerUriHandler(Intent.ACTION_DIAL, "tel")
        registerUriHandler(Intent.ACTION_VIEW, "sms")
        val shadowApp = Shadows.shadowOf(RuntimeEnvironment.getApplication())

        composeTestRule.setContent {
            HearUTheme {
                EmergencyScreen(onNavigateBack = {}, onNavigateToBreathing = {})
            }
        }

        composeTestRule.onAllNodesWithText("Call Now")[0].performScrollTo().performClick()
        val callIntent = shadowApp.nextStartedActivity
        assertNotNull(callIntent)
        assertEquals(Intent.ACTION_DIAL, callIntent.action)
        assertEquals(Uri.parse("tel:988"), callIntent.data)

        composeTestRule.onNodeWithText("Text 988").performScrollTo().performClick()
        val smsIntent = shadowApp.nextStartedActivity
        assertNotNull(smsIntent)
        assertEquals(Intent.ACTION_VIEW, smsIntent.action)
        assertEquals(Uri.parse("sms:988"), smsIntent.data)
    }

    @Test
    fun tier1_crisisTextLineAndVeterans_launchExpectedIntents() {
        registerUriHandler(Intent.ACTION_DIAL, "tel")
        registerUriHandler(Intent.ACTION_VIEW, "sms")
        val shadowApp = Shadows.shadowOf(RuntimeEnvironment.getApplication())

        composeTestRule.setContent {
            HearUTheme {
                EmergencyScreen(onNavigateBack = {}, onNavigateToBreathing = {})
            }
        }

        composeTestRule.onNodeWithText("Text HOME to 741741").performScrollTo().performClick()
        val textLineIntent = shadowApp.nextStartedActivity
        assertNotNull(textLineIntent)
        assertEquals(Intent.ACTION_VIEW, textLineIntent.action)
        assertEquals(Uri.parse("sms:741741?body=HOME"), textLineIntent.data)

        composeTestRule.onNodeWithText("Call 988 (Press 1)").performScrollTo().performClick()
        val veteransCallIntent = shadowApp.nextStartedActivity
        assertNotNull(veteransCallIntent)
        assertEquals(Intent.ACTION_DIAL, veteransCallIntent.action)
        assertEquals(Uri.parse("tel:988"), veteransCallIntent.data)

        composeTestRule.onNodeWithText("Text 838255").performScrollTo().performClick()
        val veteransSmsIntent = shadowApp.nextStartedActivity
        assertNotNull(veteransSmsIntent)
        assertEquals(Intent.ACTION_VIEW, veteransSmsIntent.action)
        assertEquals(Uri.parse("sms:838255"), veteransSmsIntent.data)
    }

    @Test
    fun tier1_trevorProjectAndBefrienders_launchExpectedIntents() {
        registerUriHandler(Intent.ACTION_DIAL, "tel")
        registerUriHandler(Intent.ACTION_VIEW, "sms")
        registerUriHandler(Intent.ACTION_VIEW, "https")
        val shadowApp = Shadows.shadowOf(RuntimeEnvironment.getApplication())

        composeTestRule.setContent {
            HearUTheme {
                EmergencyScreen(onNavigateBack = {}, onNavigateToBreathing = {})
            }
        }

        composeTestRule.onAllNodesWithText("Call Now")[1].performScrollTo().performClick()
        val trevorCallIntent = shadowApp.nextStartedActivity
        assertNotNull(trevorCallIntent)
        assertEquals(Intent.ACTION_DIAL, trevorCallIntent.action)
        assertEquals(Uri.parse("tel:18664887386"), trevorCallIntent.data)

        composeTestRule.onNodeWithText("Text START").performScrollTo().performClick()
        val trevorSmsIntent = shadowApp.nextStartedActivity
        assertNotNull(trevorSmsIntent)
        assertEquals(Intent.ACTION_VIEW, trevorSmsIntent.action)
        assertEquals(Uri.parse("sms:678678?body=START"), trevorSmsIntent.data)

        composeTestRule.onNodeWithText("Visit Befrienders Worldwide Directory")
            .performScrollTo()
            .performClick()
        val webIntent = shadowApp.nextStartedActivity
        assertNotNull(webIntent)
        assertEquals(Intent.ACTION_VIEW, webIntent.action)
        assertEquals(Uri.parse("https://www.befrienders.org/find-support-now/"), webIntent.data)
    }

    @Test
    fun tier1_emergencyScreen_backAndBreathingNavigationCallbacksWork() {
        var backCalled = false
        var breathingCalled = false

        composeTestRule.setContent {
            HearUTheme {
                EmergencyScreen(
                    onNavigateBack = { backCalled = true },
                    onNavigateToBreathing = { breathingCalled = true }
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Back").performClick()
        assertTrue(backCalled)

        composeTestRule.onNodeWithText("Take a 2-Minute Calming Breath (4-7-8 Exercise)")
            .performScrollTo()
            .performClick()
        assertTrue(breathingCalled)
    }

    @Test
    fun tier1_crisisSupportDialog_displaysActionsFiresIntentsAndDismisses() {
        registerUriHandler(Intent.ACTION_DIAL, "tel")
        registerUriHandler(Intent.ACTION_VIEW, "https")
        val shadowApp = Shadows.shadowOf(RuntimeEnvironment.getApplication())
        var dismissed = false

        composeTestRule.setContent {
            HearUTheme {
                CrisisSupportDialog(onDismiss = { dismissed = true })
            }
        }

        composeTestRule.onNodeWithText("We're Here For You").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Crisis Warning").assertIsDisplayed()

        composeTestRule.onNodeWithText("Call 988 Crisis Lifeline").performClick()
        val callIntent = shadowApp.nextStartedActivity
        assertNotNull(callIntent)
        assertEquals(Intent.ACTION_DIAL, callIntent.action)
        assertEquals(Uri.parse("tel:988"), callIntent.data)

        composeTestRule.onNodeWithText("Chat with Lifeline Online").performClick()
        val webIntent = shadowApp.nextStartedActivity
        assertNotNull(webIntent)
        assertEquals(Intent.ACTION_VIEW, webIntent.action)
        assertEquals(Uri.parse("https://988lifeline.org/chat/"), webIntent.data)

        composeTestRule.onNodeWithText("Return to App").performClick()
        assertTrue(dismissed)
    }

    // ==================== TIER 2: BOUNDARY / CORNER / ERROR ====================

    @Test
    fun tier2_emergencyScreen_noDialerApp_doesNotCrashOnCallActions() {
        composeTestRule.setContent {
            val base = LocalContext.current
            CompositionLocalProvider(LocalContext provides noHandlersContext(base)) {
                HearUTheme {
                    EmergencyScreen(onNavigateBack = {}, onNavigateToBreathing = {})
                }
            }
        }

        composeTestRule.onAllNodesWithText("Call Now")[0].performScrollTo().performClick()
        composeTestRule.onNodeWithText("Call 988 (Press 1)").performScrollTo().performClick()
        composeTestRule.onAllNodesWithText("Call Now")[1].performScrollTo().performClick()

        composeTestRule.onNodeWithText("24/7 Crisis & Emergency Hub").assertIsDisplayed()
    }

    @Test
    fun tier2_emergencyScreen_noSmsApp_doesNotCrashOnTextActions() {
        composeTestRule.setContent {
            val base = LocalContext.current
            CompositionLocalProvider(LocalContext provides noHandlersContext(base)) {
                HearUTheme {
                    EmergencyScreen(onNavigateBack = {}, onNavigateToBreathing = {})
                }
            }
        }

        composeTestRule.onNodeWithText("Text 988").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Text HOME to 741741").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Text 838255").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Text START").performScrollTo().performClick()

        composeTestRule.onNodeWithText("24/7 Crisis & Emergency Hub").assertIsDisplayed()
    }

    @Test
    fun tier2_emergencyScreen_noBrowserApp_doesNotCrashOnBefriendersAction() {
        composeTestRule.setContent {
            val base = LocalContext.current
            CompositionLocalProvider(LocalContext provides noHandlersContext(base)) {
                HearUTheme {
                    EmergencyScreen(onNavigateBack = {}, onNavigateToBreathing = {})
                }
            }
        }

        composeTestRule.onNodeWithText("Visit Befrienders Worldwide Directory")
            .performScrollTo()
            .performClick()

        composeTestRule.onNodeWithText("International Resources (Outside North America)")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun tier2_crisisSupportDialog_noDialerOrBrowser_doesNotCrashAndStillDismisses() {
        var dismissed = false

        composeTestRule.setContent {
            val base = LocalContext.current
            CompositionLocalProvider(LocalContext provides noHandlersContext(base)) {
                HearUTheme {
                    CrisisSupportDialog(onDismiss = { dismissed = true })
                }
            }
        }

        composeTestRule.onNodeWithText("Call 988 Crisis Lifeline").performClick()
        composeTestRule.onNodeWithText("Chat with Lifeline Online").performClick()

        composeTestRule.onNodeWithText("We're Here For You").assertIsDisplayed()
        composeTestRule.onNodeWithText("Return to App").performClick()
        assertTrue(dismissed)
    }

    @Test
    @Config(sdk = [34], application = Application::class, qualifiers = "w480dp-h320dp-land")
    fun tier2_emergencyScreen_compactLandscapeViewport_bottomResourcesAndBreathingReachable() {
        var breathingCalled = false

        composeTestRule.setContent {
            HearUTheme {
                EmergencyScreen(
                    onNavigateBack = {},
                    onNavigateToBreathing = { breathingCalled = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Visit Befrienders Worldwide Directory")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Take a 2-Minute Calming Breath (4-7-8 Exercise)")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        assertTrue(breathingCalled)
    }

    @Test
    fun tier2_emergencyScreen_repeatedTapsWithoutHandlers_backNavigationStillWorks() {
        var backCalled = false

        composeTestRule.setContent {
            val base = LocalContext.current
            CompositionLocalProvider(LocalContext provides noHandlersContext(base)) {
                HearUTheme {
                    EmergencyScreen(
                        onNavigateBack = { backCalled = true },
                        onNavigateToBreathing = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Text HOME to 741741").performScrollTo().performClick()
        composeTestRule.onAllNodesWithText("Call Now")[0].performScrollTo().performClick()
        composeTestRule.onNodeWithContentDescription("Back").performClick()

        assertTrue(backCalled)
    }
}
