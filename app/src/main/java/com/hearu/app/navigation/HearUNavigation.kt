package com.hearu.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hearu.app.ui.auth.AuthState
import com.hearu.app.ui.auth.AuthViewModel
import com.hearu.app.ui.auth.LoginScreen
import com.hearu.app.ui.auth.RoleSelectionScreen
import com.hearu.app.ui.chat.AIChatScreen
import com.hearu.app.ui.chat.ChatScreen
import com.hearu.app.ui.emergency.EmergencyScreen
import com.hearu.app.ui.home.GiverDashboard
import com.hearu.app.ui.home.ProfileScreen
import com.hearu.app.ui.home.SeekerDashboard
import com.hearu.app.ui.onboarding.OnboardingScreen
import com.hearu.app.ui.tools.BreathingExerciseScreen

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Login : Screen("login")
    data object RoleSelection : Screen("role_selection")
    data object SeekerDashboard : Screen("seeker_dashboard")
    data object GiverDashboard : Screen("giver_dashboard")
    data object Chat : Screen("chat/{sessionId}/{userId}") {
        fun createRoute(sessionId: String, userId: String) = "chat/$sessionId/$userId"
    }
    data object AIChat : Screen("ai_chat")
    data object Profile : Screen("profile")
    data object EmergencyHub : Screen("emergency_hub")
    data object BreathingExercise : Screen("breathing_exercise")
}

@Composable
fun HearUNavigation(
    navController: NavHostController = rememberNavController(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val activeRole by authViewModel.activeRole.collectAsStateWithLifecycle(initialValue = null)
    val isOnboardingCompleted by authViewModel.isOnboardingCompleted.collectAsStateWithLifecycle(initialValue = false)

    val startDestination = when {
        !isOnboardingCompleted -> Screen.Onboarding.route
        authState !is AuthState.Authenticated -> Screen.Login.route
        activeRole == "SEEKER" -> Screen.SeekerDashboard.route
        activeRole == "GIVER" -> Screen.GiverDashboard.route
        else -> Screen.RoleSelection.route
    }

    NavHost(navController = navController, startDestination = startDestination) {

        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onFinishOnboarding = {
                    authViewModel.setOnboardingCompleted(true)
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSubmit = { email, pass ->
                    authViewModel.login(email, pass)
                },
                viewModel = authViewModel
            )
        }

        composable(Screen.RoleSelection.route) {
            RoleSelectionScreen(
                onRoleSelected = { role ->
                    authViewModel.saveRole(role)
                    if (role == "SEEKER") {
                        navController.navigate(Screen.SeekerDashboard.route) {
                            popUpTo(Screen.RoleSelection.route) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Screen.GiverDashboard.route) {
                            popUpTo(Screen.RoleSelection.route) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(Screen.SeekerDashboard.route) {
            SeekerDashboard(
                onNavigateToAIChat = { navController.navigate(Screen.AIChat.route) },
                onNavigateToHumanChat = { sessionId, userId ->
                    navController.navigate(Screen.Chat.createRoute(sessionId, userId))
                },
                onNavigateToCrisis = { navController.navigate(Screen.EmergencyHub.route) },
                onNavigateToBreathing = { navController.navigate(Screen.BreathingExercise.route) },
                onNavigateToProfile = { navController.navigate(Screen.Profile.route) }
            )
        }

        composable(Screen.GiverDashboard.route) {
            GiverDashboard(
                onNavigateToChat = { sessionId, userId ->
                    navController.navigate(Screen.Chat.createRoute(sessionId, userId))
                },
                onNavigateToProfile = { navController.navigate(Screen.Profile.route) }
            )
        }

        composable(
            route = Screen.Chat.route,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType },
                navArgument("userId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            ChatScreen(
                sessionId = sessionId,
                userId = userId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCrisis = { navController.navigate(Screen.EmergencyHub.route) }
            )
        }

        composable(Screen.Profile.route) {
            ProfileScreen(
                onNavigateBack = { navController.popBackStack() },
                authViewModel = authViewModel
            )
        }

        composable(Screen.AIChat.route) {
            AIChatScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToBreathing = { navController.navigate(Screen.BreathingExercise.route) },
                onNavigateToCrisis = { navController.navigate(Screen.EmergencyHub.route) }
            )
        }

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
