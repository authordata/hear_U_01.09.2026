package com.hearu.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hearu.app.ui.auth.AuthViewModel
import com.hearu.app.ui.auth.LoginScreen
import com.hearu.app.ui.auth.RoleSelectionScreen
import com.hearu.app.ui.home.GiverDashboard
import com.hearu.app.ui.home.SeekerDashboard

@Composable
fun HearUNavigation(
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val authState by authViewModel.authState.collectAsState()
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
        composable("seeker_home") { SeekerDashboard() }
        composable("giver_home") { GiverDashboard() }
    }
}
