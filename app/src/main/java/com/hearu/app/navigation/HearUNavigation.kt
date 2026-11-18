package com.hearu.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import kotlinx.coroutines.launch

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Login : Screen("login")
    data object RoleSelection : Screen("role_selection")
    data object SeekerHome : Screen("seeker_home")
    data object GiverHome : Screen("giver_home")
    data object AIChat : Screen("ai_chat")
    data object Profile : Screen("profile")
    data object BreathingExercise : Screen("breathing_exercise")
    data object EmergencyHub : Screen("emergency_hub")
    data object Chat : Screen("chat/{sessionId}/{userId}") {
        fun createRoute(sessionId: String, userId: String) = "chat/$sessionId/$userId"
    }
}

@Composable
fun HearUNavigation(
    authViewModel: AuthViewModel = hiltViewModel(),
    initialSessionId: String? = null
) {
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val activeRole by authViewModel.activeRole.collectAsStateWithLifecycle(initialValue = null)
    val isOnboardingCompleted by authViewModel.isOnboardingCompleted.collectAsStateWithLifecycle(initialValue = false)

    val startDestination = when {
        authState is AuthState.Authenticated && activeRole == "seeker" -> Screen.SeekerHome.route
        authState is AuthState.Authenticated && activeRole == "giver" -> Screen.GiverHome.route
        authState is AuthState.Authenticated -> Screen.RoleSelection.route
        isOnboardingCompleted -> Screen.Login.route
        else -> Screen.Onboarding.route
    }

    LaunchedEffect(initialSessionId, authState) {
        if (!initialSessionId.isNullOrBlank() && authState is AuthState.Authenticated) {
            navController.navigate(Screen.Chat.createRoute(initialSessionId, "peer"))
        }
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
                viewModel = authViewModel,
                onLoginSubmit = { email, pass ->
                    authViewModel.login(email, pass)
                },
                onSignUpSubmit = { email, pass ->
                    authViewModel.register(email, pass)
                },
                onAnonymousSubmit = {
                    authViewModel.signInAnonymously()
                }
            )
            LaunchedEffect(authState) {
                if (authState is AuthState.Authenticated) {
                    navController.navigate(Screen.RoleSelection.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            }
        }

        composable(Screen.RoleSelection.route) {
            RoleSelectionScreen(onRoleSelected = { role ->
                authViewModel.saveRole(role)
                val target = if (role == "seeker") Screen.SeekerHome.route else Screen.GiverHome.route
                navController.navigate(target) {
                    popUpTo(Screen.RoleSelection.route) { inclusive = true }
                }
            })
        }

        composable(Screen.SeekerHome.route) {
            SeekerDashboard(
                onNavigateToProfile = { navController.navigate(Screen.Profile.route) },
                onNavigateToAIChat = { navController.navigate(Screen.AIChat.route) },
                onNavigateToBreathing = { navController.navigate(Screen.BreathingExercise.route) },
                onNavigateToEmergency = { navController.navigate(Screen.EmergencyHub.route) },
                onNavigateToMatch = { sessionId, userId ->
                    navController.navigate(Screen.Chat.createRoute(sessionId, userId))
                }
            )
        }

        composable(Screen.GiverHome.route) {
            GiverDashboard(
                onNavigateToProfile = { navController.navigate(Screen.Profile.route) },
                onNavigateToChat = { sessionId, userId ->
                    navController.navigate(Screen.Chat.createRoute(sessionId, userId))
                }
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
                onNavigateBack = { navController.popBackStack() }
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
                onNavigateToBreathing = { navController.navigate(Screen.BreathingExercise.route) }
            )
        }

        composable(Screen.BreathingExercise.route) {
            BreathingExerciseScreen(
                onNavigateBack = { navController.popBackStack() }
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
