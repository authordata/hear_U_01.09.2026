package com.hearu.app.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hearu.app.repository.UserRepository
import com.hearu.app.ui.auth.AppStartupState
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
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NavigationEntryPoint {
    fun userRepository(): UserRepository
}

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
        fun createRoute(sessionId: String, userId: String) =
            "chat/${Uri.encode(sessionId)}/${Uri.encode(userId)}"
    }
}

@Composable
fun HearUNavigation(
    authViewModel: AuthViewModel = hiltViewModel(),
    initialSessionId: String? = null,
    onDeepLinkConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val startupState by authViewModel.startupState.collectAsStateWithLifecycle()

    val ready = startupState as? AppStartupState.Ready
    if (ready == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val startDestination = remember {
        when {
            ready.isAuthenticated && ready.activeRole == "seeker" -> Screen.SeekerHome.route
            ready.isAuthenticated && ready.activeRole == "giver" -> Screen.GiverHome.route
            ready.isAuthenticated -> Screen.RoleSelection.route
            ready.isOnboardingCompleted -> Screen.Login.route
            else -> Screen.Onboarding.route
        }
    }

    var wasAuthenticated by rememberSaveable { mutableStateOf(authState is AuthState.Authenticated) }
    LaunchedEffect(authState) {
        if (authState is AuthState.Authenticated) {
            wasAuthenticated = true
        } else if (wasAuthenticated && authState is AuthState.Idle) {
            wasAuthenticated = false
            navController.navigate(Screen.Login.route) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
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
            LaunchedEffect(authState, ready.activeRole) {
                if (authState is AuthState.Authenticated) {
                    val target = when (ready.activeRole) {
                        "seeker" -> Screen.SeekerHome.route
                        "giver" -> Screen.GiverHome.route
                        else -> Screen.RoleSelection.route
                    }
                    navController.navigate(target) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }

        composable(Screen.RoleSelection.route) {
            RoleSelectionScreen(
                onRoleSelected = { role ->
                    authViewModel.saveRole(role)
                    val target = if (role == "seeker") Screen.SeekerHome.route else Screen.GiverHome.route
                    navController.navigate(target) {
                        popUpTo(Screen.RoleSelection.route) { inclusive = true }
                    }
                },
                onSignOutClick = {
                    authViewModel.signOut()
                }
            )
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
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val userRepository = remember(context) {
                runCatching {
                    EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        NavigationEntryPoint::class.java
                    ).userRepository()
                }.getOrNull()
            }

            GiverDashboard(
                onNavigateToProfile = { navController.navigate(Screen.Profile.route) },
                onNavigateToChat = { sessionId, userId ->
                    navController.navigate(Screen.Chat.createRoute(sessionId, userId))
                },
                onAvailabilityChanged = { isOnline ->
                    val uid = authViewModel.currentUserId
                    if (!uid.isNullOrBlank()) {
                        scope.launch {
                            try {
                                userRepository?.updateUserStatus(uid, if (isOnline) "online" else "offline")
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                            }
                        }
                    }
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
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCrisis = { navController.navigate(Screen.EmergencyHub.route) }
            )
        }

        composable(Screen.Profile.route) {
            ProfileScreen(
                onNavigateBack = { navController.popBackStack() },
                authViewModel = authViewModel,
                onRoleChanged = { role ->
                    val target = if (role == "seeker") Screen.SeekerHome.route else Screen.GiverHome.route
                    navController.navigate(target) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
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

    var consumedSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(initialSessionId) {
        if (initialSessionId == null) {
            consumedSessionId = null
        }
    }
    LaunchedEffect(initialSessionId, authState) {
        if (!initialSessionId.isNullOrBlank() &&
            authState is AuthState.Authenticated &&
            initialSessionId != consumedSessionId
        ) {
            consumedSessionId = initialSessionId
            val uid = authViewModel.currentUserId.orEmpty().ifBlank { "self" }
            navController.navigate(Screen.Chat.createRoute(initialSessionId, uid)) {
                launchSingleTop = true
            }
            onDeepLinkConsumed()
        }
    }
}
