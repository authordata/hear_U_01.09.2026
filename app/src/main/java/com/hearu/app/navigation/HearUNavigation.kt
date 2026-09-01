package com.hearu.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hearu.app.ui.auth.AuthViewModel
import com.hearu.app.ui.auth.LoginScreen
import com.hearu.app.ui.auth.RoleSelectionScreen
import com.hearu.app.ui.chat.AIChatScreen
import com.hearu.app.ui.chat.ChatScreen
import com.hearu.app.ui.home.GiverDashboard
import com.hearu.app.ui.home.ProfileScreen
import com.hearu.app.ui.home.SeekerDashboard

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object RoleSelection : Screen("role_selection")
    data object SeekerHome : Screen("seeker_home")
    data object GiverHome : Screen("giver_home")
    data object AIChat : Screen("ai_chat")
    data object Profile : Screen("profile")
    data object Chat : Screen("chat/{sessionId}/{userId}") {
        fun createRoute(sessionId: String, userId: String) = "chat/$sessionId/$userId"
    }
}

@Composable
fun HearUNavigation(
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val activeRole by authViewModel.activeRole.collectAsStateWithLifecycle(initialValue = null)

    NavHost(navController = navController, startDestination = Screen.Login.route) {
        composable(Screen.Login.route) {
            LoginScreen(onLoginSubmit = { email, pass ->
                authViewModel.login(email, pass)
                navController.navigate(Screen.RoleSelection.route) {
                    popUpTo(Screen.Login.route) { inclusive = true }
                }
            })
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
                onNavigateToMatch = { navController.navigate(Screen.Chat.createRoute("temp_session_123", "user123")) }
            ) 
        }
        composable(Screen.GiverHome.route) { 
            GiverDashboard(onNavigateToProfile = { navController.navigate(Screen.Profile.route) }) 
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
            ProfileScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable(Screen.AIChat.route) {
            AIChatScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
