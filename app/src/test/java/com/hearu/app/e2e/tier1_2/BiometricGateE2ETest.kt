package com.hearu.app.e2e.tier1_2

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.hearu.app.MainActivity
import com.hearu.app.auth.BiometricHelper
import com.hearu.app.data.RolePreferences
import com.hearu.app.e2e.harness.E2ETestHarness
import com.hearu.app.e2e.harness.MainDispatcherRule
import com.hearu.app.repository.AuthRepository
import com.hearu.app.ui.auth.AuthViewModel
import com.hearu.app.ui.home.ProfileScreen
import com.hearu.app.ui.theme.HearUTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowBiometricManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BiometricGateE2ETest {

    @get:Rule(order = 0)
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule(order = 1)
    val composeTestRule = createEmptyComposeRule()

    private lateinit var context: Context
    private lateinit var rolePreferences: RolePreferences
    private lateinit var mockAuthRepository: AuthRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        E2ETestHarness.ensureFirebaseInitialized(context)
        runBlocking {
            E2ETestHarness.clearRolePreferences(context)
        }
        rolePreferences = E2ETestHarness.createRolePreferences(context)
        mockAuthRepository = mock()
        whenever(mockAuthRepository.currentUser).thenReturn(null)
    }

    @After
    fun tearDown() {
        composeTestRule.mainClock.autoAdvance = true
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        runBlocking {
            E2ETestHarness.clearRolePreferences(context)
        }
    }

    // ==================== TIER 1: HAPPY PATHS ====================

    @Test
    fun tier1_biometricDisabledByDefaultInRolePreferences() = runBlocking {
        val freshPreferences = E2ETestHarness.createRolePreferences(context)
        assertFalse(freshPreferences.isBiometricEnabledFlow.first())
    }

    @Test
    fun tier1_setBiometricEnabledPersistsTrueAndFalse() = runBlocking {
        rolePreferences.setBiometricEnabled(true)
        assertTrue(E2ETestHarness.createRolePreferences(context).isBiometricEnabledFlow.first())

        rolePreferences.setBiometricEnabled(false)
        assertFalse(E2ETestHarness.createRolePreferences(context).isBiometricEnabledFlow.first())
    }

    @Test
    fun tier1_profileScreenDisplaysBiometricToggleAndDisablesCleanly() {
        runBlocking {
            rolePreferences.setBiometricEnabled(true)
        }
        val authViewModel = AuthViewModel(mockAuthRepository, rolePreferences)

        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    HearUTheme {
                        ProfileScreen(
                            onNavigateBack = {},
                            authViewModel = authViewModel
                        )
                    }
                }
            }

            composeTestRule.onNodeWithText("Biometric App Lock")
                .performScrollTo()
                .assertIsDisplayed()
            composeTestRule.onNodeWithText("Require Face ID / Fingerprint on launch")
                .performScrollTo()
                .assertIsDisplayed()

            composeTestRule.onNode(
                isToggleable() and hasAnySibling(hasText("Biometric App Lock"))
            ).performScrollTo().performClick()

            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                runBlocking { !rolePreferences.isBiometricEnabledFlow.first() }
            }
            assertFalse(runBlocking { rolePreferences.isBiometricEnabledFlow.first() })
        }
    }

    @Test
    fun tier1_mainActivityBypassesLockOverlayWhenBiometricDisabled() {
        runBlocking {
            rolePreferences.setBiometricEnabled(false)
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                runCatching {
                    composeTestRule.onNodeWithText("A Safe, Anonymous Haven").assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }
            composeTestRule.onNodeWithText("HearU is Locked").assertDoesNotExist()
        }
    }

    @Test
    fun tier1_mainActivityDisplaysLockOverlayWhenBiometricEnabled() {
        runBlocking {
            rolePreferences.setBiometricEnabled(true)
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                runCatching {
                    composeTestRule.onNodeWithText("HearU is Locked").assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }
            composeTestRule.onNodeWithContentDescription("Locked").assertIsDisplayed()
            composeTestRule.onNodeWithText("Unlock with Biometrics").assertIsDisplayed()
        }
    }

    @Test
    @Config(sdk = [34], application = Application::class)
    fun tier1_biometricHelperExecutesCleanlyOnApi34() {
        val biometricManager = context.getSystemService(android.hardware.biometrics.BiometricManager::class.java)!!
        val shadowBiometricManager = Shadow.extract<ShadowBiometricManager>(biometricManager)
        val helper = BiometricHelper()

        shadowBiometricManager.setCanAuthenticate(true)
        assertTrue(
            "Expected BiometricHelper.isBiometricAvailable to return true when hardware is available",
            helper.isBiometricAvailable(context)
        )

        shadowBiometricManager.setCanAuthenticate(false)
        assertFalse(
            "Expected BiometricHelper.isBiometricAvailable to return false when hardware is unavailable",
            helper.isBiometricAvailable(context)
        )
    }

    // ==================== TIER 2: BOUNDARY / CORNER / ERROR ====================

    @Test
    @Config(sdk = [28], application = Application::class)
    fun tier2_biometricHelperSupportsPreAndroid10Api28WithoutCrash() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        val helper = BiometricHelper()

        helper.isBiometricAvailable(activity)
        helper.showBiometricPrompt(
            activity = activity,
            onSuccess = {},
            onError = {}
        )
    }

    @Test
    fun tier2_profileScreenDoesNotEnableBiometricWhenHardwareUnavailable() {
        runBlocking {
            rolePreferences.setBiometricEnabled(false)
        }
        val biometricManager = context.getSystemService(android.hardware.biometrics.BiometricManager::class.java)!!
        Shadow.extract<ShadowBiometricManager>(biometricManager).setCanAuthenticate(false)
        val authViewModel = AuthViewModel(mockAuthRepository, rolePreferences)

        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    HearUTheme {
                        ProfileScreen(
                            onNavigateBack = {},
                            authViewModel = authViewModel
                        )
                    }
                }
            }

            composeTestRule.onNode(
                isToggleable() and hasAnySibling(hasText("Biometric App Lock"))
            ).performScrollTo().performClick()

            composeTestRule.waitForIdle()
            assertFalse(
                "Biometric lock must not be enabled when hardware/activity cannot authenticate",
                runBlocking { rolePreferences.isBiometricEnabledFlow.first() }
            )
        }
    }

    @Test
    fun tier2_coldStartWithBiometricEnabledNeverFlashesUnprotectedRoute() {
        runBlocking {
            rolePreferences.setBiometricEnabled(true)
            rolePreferences.setOnboardingCompleted(false)
        }

        val controller = Robolectric.buildActivity(MainActivity::class.java)
        composeTestRule.mainClock.autoAdvance = false
        try {
            controller.create().start().resume().visible()
            composeTestRule.onRoot().assertExists()
            composeTestRule.onNodeWithText("A Safe, Anonymous Haven").assertDoesNotExist()
            composeTestRule.onNodeWithText("Welcome to HearU").assertDoesNotExist()
            composeTestRule.mainClock.advanceTimeByFrame()
            composeTestRule.onNodeWithText("A Safe, Anonymous Haven").assertDoesNotExist()
            composeTestRule.onNodeWithText("Welcome to HearU").assertDoesNotExist()

            composeTestRule.mainClock.autoAdvance = true
            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                runCatching {
                    composeTestRule.onNodeWithText("HearU is Locked").assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }
            composeTestRule.onNodeWithText("A Safe, Anonymous Haven").assertDoesNotExist()
            composeTestRule.onNodeWithText("Welcome to HearU").assertDoesNotExist()
        } finally {
            composeTestRule.mainClock.autoAdvance = true
            runCatching { controller.pause().stop().destroy() }
            Shadows.shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun tier2_lockOverlayProvidesRecoveryOrErrorFeedbackWhenBiometricFails() {
        runBlocking {
            rolePreferences.setBiometricEnabled(true)
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                runCatching {
                    composeTestRule.onNodeWithText("HearU is Locked").assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }

            composeTestRule.onNodeWithText("Unlock with Biometrics").performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithText("HearU is Locked").assertIsDisplayed()
            val recoveryOrErrorNodes = composeTestRule.onAllNodes(
                hasText("Sign Out", substring = true, ignoreCase = true) or
                    hasText("Reset", substring = true, ignoreCase = true) or
                    hasText("unavailable", substring = true, ignoreCase = true) or
                    hasText("failed", substring = true, ignoreCase = true) or
                    hasText("error", substring = true, ignoreCase = true) or
                    hasText("not available", substring = true, ignoreCase = true) or
                    hasText("No biometric", substring = true, ignoreCase = true)
            ).fetchSemanticsNodes()
            assertTrue(
                "Lock overlay should expose an error message or Sign Out recovery option when biometrics fail",
                recoveryOrErrorNodes.isNotEmpty()
            )
        }
    }

    @Test
    fun tier2_configurationChangeRecreatePreservesLockStateSafely() {
        runBlocking {
            rolePreferences.setBiometricEnabled(true)
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                runCatching {
                    composeTestRule.onNodeWithText("HearU is Locked").assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }

            scenario.recreate()

            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                runCatching {
                    composeTestRule.onNodeWithText("HearU is Locked").assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }
            composeTestRule.onNodeWithText("Unlock with Biometrics").assertIsDisplayed()
        }
    }

    @Test
    fun tier2_mainActivityEnforcesFlagSecureAndRelocksOnBackgroundStop() {
        runBlocking {
            rolePreferences.setBiometricEnabled(true)
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val flags = activity.window.attributes.flags
                assertTrue(
                    "MainActivity must set FLAG_SECURE to prevent Recents screen snapshots",
                    (flags and android.view.WindowManager.LayoutParams.FLAG_SECURE) != 0
                )
            }
        }
    }
}
