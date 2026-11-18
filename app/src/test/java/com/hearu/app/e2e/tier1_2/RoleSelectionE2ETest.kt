package com.hearu.app.e2e.tier1_2

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.auth.FirebaseUser
import com.hearu.app.data.RolePreferences
import com.hearu.app.e2e.harness.E2ETestHarness
import com.hearu.app.e2e.harness.MainDispatcherRule
import com.hearu.app.navigation.HearUNavigation
import com.hearu.app.navigation.Screen
import com.hearu.app.repository.AuthRepository
import com.hearu.app.repository.AuthResult
import com.hearu.app.ui.auth.AuthViewModel
import com.hearu.app.ui.auth.RoleSelectionScreen
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
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class RoleSelectionE2ETest {

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
    fun tier1_roleSelection_rendersBothSeekerAndGiverCardsWithDescriptions() {
        composeTestRule.setContent {
            HearUTheme {
                RoleSelectionScreen(onRoleSelected = {})
            }
        }

        composeTestRule.onNodeWithText("Welcome to HearU").assertIsDisplayed()
        composeTestRule.onNodeWithText("You can always switch roles in your profile.", substring = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("I need support (Seeker)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Connect with a compassionate peer or chat with our 24/7 AI companion.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("I want to listen (Giver)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Offer an empathetic, non-judgmental ear to someone walking through a tough moment.")
            .assertIsDisplayed()
    }

    @Test
    fun tier1_selectSeekerRole_invokesCallbackAndPersistsActiveRole() {
        var selectedRole: String? = null
        composeTestRule.setContent {
            HearUTheme {
                RoleSelectionScreen(onRoleSelected = { role ->
                    selectedRole = role
                    authViewModel.saveRole(role)
                })
            }
        }

        composeTestRule.onNodeWithText("I need support (Seeker)").performClick()

        assertEquals("seeker", selectedRole)
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            runBlocking { rolePreferences.activeRoleFlow.first() == "seeker" }
        }
        assertEquals("seeker", runBlocking { rolePreferences.activeRoleFlow.first() })
    }

    @Test
    fun tier1_selectGiverRole_invokesCallbackAndPersistsActiveRole() {
        var selectedRole: String? = null
        composeTestRule.setContent {
            HearUTheme {
                RoleSelectionScreen(onRoleSelected = { role ->
                    selectedRole = role
                    authViewModel.saveRole(role)
                })
            }
        }

        composeTestRule.onNodeWithText("I want to listen (Giver)").performClick()

        assertEquals("giver", selectedRole)
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            runBlocking { rolePreferences.activeRoleFlow.first() == "giver" }
        }
        assertEquals("giver", runBlocking { rolePreferences.activeRoleFlow.first() })
    }

    @Test
    fun tier1_profileRoleSwitcher_isDisplayedAndAllowsSwitchingRoles() {
        runBlocking {
            rolePreferences.setActiveRole("seeker")
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

        val switcherMatcher =
            hasText("Switch Role", substring = true, ignoreCase = true) or
                hasText("Switch to", substring = true, ignoreCase = true) or
                hasText("Change Role", substring = true, ignoreCase = true) or
                (hasText("Giver", substring = true, ignoreCase = true) and hasClickAction()) or
                (hasText("Role", substring = true, ignoreCase = true) and hasClickAction())

        val roleSwitcherNodes = composeTestRule.onAllNodes(switcherMatcher).fetchSemanticsNodes()
        assertTrue(
            "ProfileScreen should display an interactive role switcher control",
            roleSwitcherNodes.isNotEmpty()
        )

        val switcherNode = composeTestRule.onAllNodes(switcherMatcher).onFirst()
        runCatching { switcherNode.performScrollTo() }
        switcherNode.performClick()
        composeTestRule.waitForIdle()

        // If clicking opened a dialog/menu with a "Giver" choice, select it
        if (runBlocking { rolePreferences.activeRoleFlow.first() } != "giver") {
            val giverChoice = composeTestRule.onAllNodes(
                hasText("Giver", substring = true, ignoreCase = true) and hasClickAction()
            ).fetchSemanticsNodes()
            if (giverChoice.isNotEmpty()) {
                val giverNode = composeTestRule.onAllNodes(
                    hasText("Giver", substring = true, ignoreCase = true) and hasClickAction()
                ).onFirst()
                runCatching { giverNode.performScrollTo() }
                giverNode.performClick()
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            runBlocking { rolePreferences.activeRoleFlow.first() == "giver" }
        }
        assertEquals("giver", runBlocking { rolePreferences.activeRoleFlow.first() })
    }

    @Test
    fun tier1_roleSelection_navigationRoutesSeekerAndGiverCorrectly() {
        val navController = E2ETestHarness.createTestNavController(context)

        composeTestRule.setContent {
            HearUTheme {
                NavHost(
                    navController = navController,
                    startDestination = Screen.RoleSelection.route
                ) {
                    composable(Screen.RoleSelection.route) {
                        RoleSelectionScreen(onRoleSelected = { role ->
                            authViewModel.saveRole(role)
                            val target = if (role == "seeker") Screen.SeekerHome.route else Screen.GiverHome.route
                            navController.navigate(target)
                        })
                    }
                    composable(Screen.SeekerHome.route) {
                        Text("Seeker Home Destination")
                    }
                    composable(Screen.GiverHome.route) {
                        Text("Giver Home Destination")
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("I need support (Seeker)").performClick()
        assertEquals(Screen.SeekerHome.route, navController.currentDestination?.route)

        composeTestRule.runOnUiThread {
            navController.popBackStack()
        }
        assertEquals(Screen.RoleSelection.route, navController.currentDestination?.route)

        composeTestRule.onNodeWithText("I want to listen (Giver)").performClick()
        assertEquals(Screen.GiverHome.route, navController.currentDestination?.route)
    }

    // ==================== TIER 2: BOUNDARY / CORNER / ERROR ====================

    @Test
    fun tier2_authenticatedUserWithSavedRole_skipsRoleSelectionOnLogin() {
        runBlocking {
            rolePreferences.setOnboardingCompleted(true)
            rolePreferences.setActiveRole("giver")
            whenever(mockAuthRepository.signInWithEmail("giver@hearu.app", "StrongPass1!"))
                .thenReturn(AuthResult.Success(mockUser))
        }
        val vm = AuthViewModel(mockAuthRepository, rolePreferences)

        composeTestRule.setContent {
            HearUTheme {
                HearUNavigation(authViewModel = vm)
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            runCatching {
                composeTestRule.onNodeWithTag("email_field").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeTestRule.onNodeWithTag("email_field").performScrollTo().performTextInput("giver@hearu.app")
        composeTestRule.onNodeWithTag("password_field").performScrollTo().performTextInput("StrongPass1!")
        composeTestRule.onNodeWithTag("login_button").performScrollTo().performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            runCatching {
                composeTestRule.onNodeWithText("HearU - Listener Hub").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeTestRule.onNodeWithText("I want to listen (Giver)").assertDoesNotExist()
    }

    @Test
    fun tier2_roleSelectionScreen_providesSignOutOrBackExitOption() {
        composeTestRule.setContent {
            HearUTheme {
                RoleSelectionScreen(onRoleSelected = {})
            }
        }

        val exitNodes = composeTestRule.onAllNodes(
            hasText("Sign Out", substring = true, ignoreCase = true) or
                hasText("Log Out", substring = true, ignoreCase = true) or
                hasContentDescription("Back", substring = true, ignoreCase = true) or
                hasContentDescription("Sign Out", substring = true, ignoreCase = true)
        ).fetchSemanticsNodes()

        assertTrue(
            "RoleSelectionScreen should provide a Sign Out or Back option so users are not trapped",
            exitNodes.isNotEmpty()
        )
    }

    @Test
    fun tier2_roleSelectionScreen_smallViewportScrollable_allCardsReachable() {
        var chosenRole: String? = null
        composeTestRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = baseDensity.density, fontScale = 1.5f)
            ) {
                HearUTheme {
                    Box(modifier = Modifier.size(width = 320.dp, height = 360.dp)) {
                        RoleSelectionScreen(onRoleSelected = { chosenRole = it })
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("I want to listen (Giver)")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        assertEquals("giver", chosenRole)
    }

    @Test
    fun tier2_rapidRoleSwitch_seekerThenGiver_persistsLatestRoleOnly() {
        authViewModel.saveRole("seeker")
        authViewModel.saveRole("giver")

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            runBlocking { rolePreferences.activeRoleFlow.first() == "giver" }
        }
        assertEquals("giver", runBlocking { rolePreferences.activeRoleFlow.first() })
    }

    @Test
    fun tier2_signOutAfterRoleSelection_clearsPersistedActiveRole() {
        runBlocking {
            rolePreferences.setActiveRole("seeker")
            assertEquals("seeker", rolePreferences.activeRoleFlow.first())
        }

        authViewModel.signOut()

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            runBlocking { rolePreferences.activeRoleFlow.first() == null }
        }
        assertNull(runBlocking { rolePreferences.activeRoleFlow.first() })
    }
}
