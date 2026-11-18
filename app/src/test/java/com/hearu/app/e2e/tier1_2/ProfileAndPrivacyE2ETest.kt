package com.hearu.app.e2e.tier1_2

import android.app.Application
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.auth.FirebaseUser
import com.hearu.app.data.RolePreferences
import com.hearu.app.data.local.entity.MessageEntity
import com.hearu.app.e2e.harness.E2ETestHarness
import com.hearu.app.e2e.harness.MainDispatcherRule
import com.hearu.app.navigation.HearUNavigation
import com.hearu.app.repository.AuthRepository
import com.hearu.app.ui.auth.AuthState
import com.hearu.app.ui.auth.AuthViewModel
import com.hearu.app.ui.home.ProfileScreen
import com.hearu.app.ui.theme.HearUTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class ProfileAndPrivacyE2ETest {

    @get:Rule(order = 0)
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule(order = 1)
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var rolePreferences: RolePreferences
    private lateinit var mockAuthRepository: AuthRepository
    private lateinit var mockUser: FirebaseUser
    private lateinit var authViewModel: AuthViewModel

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        runBlocking {
            E2ETestHarness.clearRolePreferences(context)
        }
        rolePreferences = E2ETestHarness.createRolePreferences(context)
        mockAuthRepository = mock()
        mockUser = mock()
        whenever(mockAuthRepository.currentUser).thenReturn(null)
        authViewModel = AuthViewModel(mockAuthRepository, rolePreferences)
    }

    @After
    fun tearDown() {
        runBlocking {
            E2ETestHarness.clearRolePreferences(context)
        }
    }

    // ==================== TIER 1: HAPPY PATHS ====================

    @Test
    fun tier1_profileScreen_displaysAliasTrustAndAutoPurgeGuarantee() {
        composeTestRule.setContent {
            HearUTheme {
                ProfileScreen(
                    onNavigateBack = {},
                    authViewModel = authViewModel
                )
            }
        }

        composeTestRule.onNodeWithText("My Profile & Security").assertIsDisplayed()
        composeTestRule.onNodeWithText("KindSoul").assertIsDisplayed()
        composeTestRule.onNodeWithText("Anonymous Peer Identity").assertIsDisplayed()
        composeTestRule.onNodeWithText("Trust & Safety Standing").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("30-Day Auto Purge Guarantee").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Return to Dashboard").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun tier1_changeAlias_trimsWhitespaceAndPersistsNewDisplayName() {
        composeTestRule.setContent {
            HearUTheme {
                ProfileScreen(
                    onNavigateBack = {},
                    authViewModel = authViewModel
                )
            }
        }

        composeTestRule.onNodeWithText("Change Alias Name").performClick()
        composeTestRule.onNodeWithText("Change Anonymous Alias").assertIsDisplayed()
        composeTestRule.onNodeWithText("Alias Name").performTextReplacement("  CalmOwl  ")
        composeTestRule.onNodeWithText("Save").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            runBlocking { rolePreferences.displayNameFlow.first() == "CalmOwl" }
        }
        composeTestRule.onNodeWithText("CalmOwl").assertIsDisplayed()
        assertEquals("CalmOwl", runBlocking { rolePreferences.displayNameFlow.first() })
    }

    @Test
    fun tier1_darkThemeToggle_persistsPreferenceInDataStore() {
        composeTestRule.setContent {
            HearUTheme {
                ProfileScreen(
                    onNavigateBack = {},
                    authViewModel = authViewModel
                )
            }
        }

        composeTestRule.onNode(
            isToggleable() and hasAnySibling(hasText("Dark Theme"))
        ).performScrollTo().performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            runBlocking { rolePreferences.isDarkThemeFlow.first() == true }
        }
        assertEquals(true, runBlocking { rolePreferences.isDarkThemeFlow.first() })
    }

    @Test
    fun tier1_inAppPrivacyPolicy_isAccessibleAndDisplaysPolicyDetails() {
        composeTestRule.setContent {
            HearUTheme {
                ProfileScreen(
                    onNavigateBack = {},
                    authViewModel = authViewModel
                )
            }
        }

        val privacyPolicyControls = composeTestRule.onAllNodes(
            hasText("Privacy Policy", substring = true, ignoreCase = true) and hasClickAction()
        )
        assertTrue(
            "ProfileScreen should expose a clickable Privacy Policy control",
            privacyPolicyControls.fetchSemanticsNodes().isNotEmpty()
        )
        privacyPolicyControls.onFirst()
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        val policyDetailNodes = composeTestRule.onAllNodes(
            hasText("privacy@hearu.app", substring = true, ignoreCase = true) or
                hasText("18 and older", substring = true, ignoreCase = true) or
                hasText("Account & Data Deletion", substring = true, ignoreCase = true) or
                hasText("Data Collection", substring = true, ignoreCase = true) or
                hasText("BiometricPrompt", substring = true, ignoreCase = true) or
                hasText("Ephemeral Chat Retention", substring = true, ignoreCase = true)
        ).fetchSemanticsNodes()

        assertTrue(
            "In-app Privacy Policy viewer should display retention/age/contact/deletion policy details",
            policyDetailNodes.isNotEmpty()
        )

        val dismissActions = composeTestRule.onAllNodes(
            (hasText("Close", substring = true, ignoreCase = true) or
                hasText("Back", ignoreCase = true) or
                hasText("Done", substring = true, ignoreCase = true) or
                hasText("OK", ignoreCase = true) or
                hasText("Dismiss", substring = true, ignoreCase = true) or
                hasText("Got It", ignoreCase = true) or
                hasContentDescription("Close", substring = true, ignoreCase = true)) and
                hasClickAction()
        )
        assertTrue(
            "In-app Privacy Policy viewer should provide a Close/Back/Done/OK dismiss action",
            dismissActions.fetchSemanticsNodes().isNotEmpty()
        )
        dismissActions.onFirst().apply {
            runCatching { performScrollTo() }
            assertIsDisplayed()
        }
    }

    @Test
    fun tier1_deleteAccount_buttonVisibleAndOpensConfirmationDialog() {
        composeTestRule.setContent {
            HearUTheme {
                ProfileScreen(
                    onNavigateBack = {},
                    authViewModel = authViewModel
                )
            }
        }

        val deleteControls = composeTestRule.onAllNodes(
            hasText("Delete Account", substring = true, ignoreCase = true) and hasClickAction()
        )
        assertTrue(
            "ProfileScreen should expose a clickable Delete Account control",
            deleteControls.fetchSemanticsNodes().isNotEmpty()
        )
        deleteControls.onFirst()
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        val warningNodes = composeTestRule.onAllNodes(
            hasText("permanently delete", substring = true, ignoreCase = true) or
                hasText("permanently erase", substring = true, ignoreCase = true) or
                hasText("erase your", substring = true, ignoreCase = true) or
                hasText("cannot be undone", substring = true, ignoreCase = true) or
                hasText("irreversible", substring = true, ignoreCase = true)
        ).fetchSemanticsNodes()
        assertTrue(
            "Delete Account dialog should warn user about permanent data erasure",
            warningNodes.isNotEmpty()
        )
        composeTestRule.onNodeWithText("Cancel", substring = true, ignoreCase = true).assertIsDisplayed()
    }

    @Test
    fun tier1_returnToDashboardAndTopBarBack_invokeOnNavigateBack() {
        var backCount = 0
        composeTestRule.setContent {
            HearUTheme {
                ProfileScreen(
                    onNavigateBack = { backCount++ },
                    authViewModel = authViewModel
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backCount)

        composeTestRule.onNodeWithText("Return to Dashboard").performScrollTo().performClick()
        assertEquals(2, backCount)
    }

    // ==================== TIER 2: BOUNDARY / CORNER / ERROR ====================

    @Test
    fun tier2_changeAlias_blankOrWhitespaceOnly_preservesExistingAlias() {
        runBlocking {
            rolePreferences.setDisplayName("BravePanda")
        }
        val vm = AuthViewModel(mockAuthRepository, rolePreferences)

        composeTestRule.setContent {
            HearUTheme {
                ProfileScreen(
                    onNavigateBack = {},
                    authViewModel = vm
                )
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            runCatching {
                composeTestRule.onNodeWithText("BravePanda").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeTestRule.onNodeWithText("Change Alias Name").performClick()
        composeTestRule.onNodeWithText("Alias Name").performTextReplacement("   ")
        composeTestRule.onNodeWithText("Save").performClick()

        composeTestRule.waitForIdle()
        assertEquals("BravePanda", runBlocking { rolePreferences.displayNameFlow.first() })
        composeTestRule.onNodeWithText("BravePanda").assertIsDisplayed()
    }

    @Test
    fun tier2_signOut_fromProfile_resetsBackstackToLoginAndPreventsBackToDashboard() {
        runBlocking {
            rolePreferences.setOnboardingCompleted(true)
            rolePreferences.setActiveRole("giver")
        }
        whenever(mockAuthRepository.currentUser).thenReturn(mockUser)
        val vm = AuthViewModel(mockAuthRepository, rolePreferences)

        var backPressedDispatcher: androidx.activity.OnBackPressedDispatcher? = null
        var hostActivity: android.app.Activity? = null
        composeTestRule.setContent {
            backPressedDispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
            hostActivity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
            HearUTheme {
                HearUNavigation(authViewModel = vm)
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            runCatching {
                composeTestRule.onNodeWithText("HearU - Listener Hub").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeTestRule.onNodeWithContentDescription("Profile").performClick()
        composeTestRule.onNodeWithText("My Profile & Security").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Sign Out").performClick()
        composeTestRule.onNodeWithText("Sign Out of HearU").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sign Out").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            runCatching {
                composeTestRule.onNodeWithTag("email_field").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeTestRule.onNodeWithText("Continue as Anonymous Guest").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("HearU - Listener Hub").assertDoesNotExist()
        assertEquals(AuthState.Idle, vm.authState.value)
        verify(mockAuthRepository).signOut()

        var activityFinishing = false
        composeTestRule.runOnUiThread {
            backPressedDispatcher?.onBackPressed()
            activityFinishing = hostActivity?.isFinishing == true
        }
        if (!activityFinishing) {
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("HearU - Listener Hub").assertDoesNotExist()
            composeTestRule.onNodeWithText("Continue as Anonymous Guest").performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    @Config(qualifiers = "+night")
    fun tier2_darkThemeSwitch_whenPreferenceNullAndSystemIsDark_reflectsCheckedState() {
        assertNull(runBlocking { rolePreferences.isDarkThemeFlow.first() })

        composeTestRule.setContent {
            HearUTheme {
                ProfileScreen(
                    onNavigateBack = {},
                    authViewModel = authViewModel
                )
            }
        }

        composeTestRule.onNode(
            isToggleable() and hasAnySibling(hasText("Dark Theme"))
        ).performScrollTo().assertIsOn()
    }

    @Test
    fun tier2_darkThemeColorScheme_meetsWcagAccessibleContrast() {
        var primaryOnPrimaryContrast = 0f
        var containerContrast = 0f
        var surfaceContrast = 0f
        var surfaceVariantContrast = 0f
        var backgroundContrast = 0f

        composeTestRule.setContent {
            HearUTheme(darkTheme = true) {
                val scheme = MaterialTheme.colorScheme
                primaryOnPrimaryContrast = contrastRatio(scheme.onPrimary, scheme.primary)
                containerContrast = contrastRatio(scheme.onPrimaryContainer, scheme.primaryContainer)
                surfaceContrast = contrastRatio(scheme.onSurface, scheme.surface)
                surfaceVariantContrast = contrastRatio(scheme.onSurfaceVariant, scheme.surfaceVariant)
                backgroundContrast = contrastRatio(scheme.onBackground, scheme.background)
            }
        }

        assertTrue("Dark primary/onPrimary contrast $primaryOnPrimaryContrast < 4.5", primaryOnPrimaryContrast >= 4.5f)
        assertTrue("Dark primaryContainer contrast $containerContrast < 4.5", containerContrast >= 4.5f)
        assertTrue("Dark surface contrast $surfaceContrast < 4.5", surfaceContrast >= 4.5f)
        assertTrue("Dark surfaceVariant contrast $surfaceVariantContrast < 4.5", surfaceVariantContrast >= 4.5f)
        assertTrue("Dark background contrast $backgroundContrast < 4.5", backgroundContrast >= 4.5f)
    }

    @Test
    fun tier2_deleteAccount_cancelDismissesWithoutWipingData_andLocalRoomPurgeWorks() {
        runBlocking {
            rolePreferences.setDisplayName("SafeListener")
        }
        val db = E2ETestHarness.createInMemoryDb(context)
        try {
            val messageDao = db.messageDao()
            runBlocking {
                messageDao.insertMessage(
                    MessageEntity(
                        id = "msg_1",
                        sessionId = "session_purge",
                        senderId = "uid_1",
                        text = "Hello",
                        timestamp = 1_700_000_000_000L
                    )
                )
                assertEquals(1, messageDao.getMessagesForSession("session_purge").first().size)
                messageDao.clearSession("session_purge")
                assertEquals(0, messageDao.getMessagesForSession("session_purge").first().size)
            }
        } finally {
            db.close()
        }

        val vm = AuthViewModel(mockAuthRepository, rolePreferences)
        composeTestRule.setContent {
            HearUTheme {
                ProfileScreen(
                    onNavigateBack = {},
                    authViewModel = vm
                )
            }
        }

        val deleteControls = composeTestRule.onAllNodes(
            hasText("Delete Account", substring = true, ignoreCase = true) and hasClickAction()
        )
        assertTrue(
            "ProfileScreen should expose a clickable Delete Account control",
            deleteControls.fetchSemanticsNodes().isNotEmpty()
        )
        deleteControls.onFirst()
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeTestRule.onNodeWithText("Cancel", substring = true, ignoreCase = true).performClick()

        composeTestRule.onNodeWithText("My Profile & Security").assertIsDisplayed()
        assertEquals("SafeListener", runBlocking { rolePreferences.displayNameFlow.first() })
    }

    @Test
    fun tier2_signOut_cancelDialog_keepsSessionAuthenticated() {
        whenever(mockAuthRepository.currentUser).thenReturn(mockUser)
        val vm = AuthViewModel(mockAuthRepository, rolePreferences)

        composeTestRule.setContent {
            HearUTheme {
                ProfileScreen(
                    onNavigateBack = {},
                    authViewModel = vm
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Sign Out").performClick()
        composeTestRule.onNodeWithText("Sign Out of HearU").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performClick()

        composeTestRule.onNodeWithText("Sign Out of HearU").assertDoesNotExist()
        assertEquals(AuthState.Authenticated, vm.authState.value)
        verify(mockAuthRepository, never()).signOut()
    }

    private fun contrastRatio(foreground: Color, background: Color): Float {
        val l1 = foreground.luminance()
        val l2 = background.luminance()
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05f) / (darker + 0.05f)
    }
}
