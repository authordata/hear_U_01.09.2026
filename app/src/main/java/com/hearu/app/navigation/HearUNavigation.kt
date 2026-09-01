package com.hearu.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hearu.app.ui.auth.AuthViewModel
import com.hearu.app.ui.auth.LoginScreen
import com.hearu.app.ui.auth.RoleSelectionScreen
import com.hearu.app.ui.home.GiverDashboard
import com.hearu.app.ui.home.SeekerDashboard
import com.hearu.app.ui.chat.ChatScreen
import com.hearu.app.ui.chat.AIChatScreen

@Composable
fun HearUNavigation(
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val activeRole by authViewModel.activeRole.collectAsState(initial = null)

    NavHost(navController = navController, startDestination = "login") {
        composable("login") {
            LoginScreen(onLoginSuccess = { 
                authViewModel.login()
                navController.navigate("role_selection") {
                    popUpTo("login") { inclusive = true }
                }
            })
        }
        composable("role_selection") {
            RoleSelectionScreen(onRoleSelected = { role ->
                authViewModel.saveRole(role)
                val route = if (role == "seeker") "seeker_home" else "giver_home"
                navController.navigate(route) {
                    popUpTo("role_selection") { inclusive = true }
                }
            })
        }
        composable("seeker_home") { 
            SeekerDashboard(
                onNavigateToAIChat = { navController.navigate("ai_chat") },
                onNavigateToMatch = { navController.navigate("chat/temp_session_123/user123") }
            ) 
        }
        composable("giver_home") { GiverDashboard() }
        
        composable(
            route = "chat/{sessionId}/{userId}",
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
        
        composable("ai_chat") {
            AIChatScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
