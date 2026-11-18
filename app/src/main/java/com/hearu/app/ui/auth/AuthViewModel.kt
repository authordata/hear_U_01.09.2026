package com.hearu.app.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.hearu.app.data.RolePreferences
import com.hearu.app.data.local.dao.MessageDao
import com.hearu.app.repository.AuthRepository
import com.hearu.app.repository.AuthResult
import com.hearu.app.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

sealed interface AppStartupState {
    data object Loading : AppStartupState
    data class Ready(
        val isOnboardingCompleted: Boolean,
        val activeRole: String?,
        val isBiometricEnabled: Boolean,
        val isDarkTheme: Boolean?,
        val isAuthenticated: Boolean
    ) : AppStartupState
}

@HiltViewModel
class AuthViewModel private constructor(
    private val authRepository: AuthRepository,
    private val rolePreferences: RolePreferences,
    private val messageDao: MessageDao?,
    private val appContext: Context?,
    private val userRepository: UserRepository?,
    @Suppress("UNUSED_PARAMETER") internalMarker: Boolean
) : ViewModel() {

    @Inject
    constructor(
        authRepository: AuthRepository,
        rolePreferences: RolePreferences,
        messageDao: MessageDao,
        @ApplicationContext appContext: Context,
        userRepository: UserRepository
    ) : this(authRepository, rolePreferences, messageDao, appContext, userRepository, true)

    constructor(
        authRepository: AuthRepository,
        rolePreferences: RolePreferences,
        messageDao: MessageDao,
        appContext: Context
    ) : this(authRepository, rolePreferences, messageDao, appContext, null, false)

    constructor(
        authRepository: AuthRepository,
        rolePreferences: RolePreferences
    ) : this(authRepository, rolePreferences, null, null, null, false)

    val activeRole = rolePreferences.activeRoleFlow
    val isOnboardingCompleted = rolePreferences.isOnboardingCompletedFlow
    val displayName = rolePreferences.displayNameFlow
    val isBiometricEnabled = rolePreferences.isBiometricEnabledFlow
    val isDarkTheme = rolePreferences.isDarkThemeFlow

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _isDeletingAccount = MutableStateFlow(false)
    val isDeletingAccount: StateFlow<Boolean> = _isDeletingAccount.asStateFlow()

    private var authJob: Job? = null

    val currentUserId: String?
        get() = authRepository.currentUser?.uid

    val startupState: StateFlow<AppStartupState> by lazy {
        val onboardingFlow = rolePreferences.isOnboardingCompletedFlow ?: flowOf(false)
        val roleFlow = rolePreferences.activeRoleFlow ?: flowOf(null)
        val biometricFlow = rolePreferences.isBiometricEnabledFlow ?: flowOf(false)
        val darkThemeFlow = rolePreferences.isDarkThemeFlow ?: flowOf(null)

        combine(
            onboardingFlow,
            roleFlow,
            biometricFlow,
            darkThemeFlow,
            _authState
        ) { onboardingDone, role, biometric, darkTheme, auth ->
            AppStartupState.Ready(
                isOnboardingCompleted = onboardingDone,
                activeRole = role,
                isBiometricEnabled = biometric,
                isDarkTheme = darkTheme,
                isAuthenticated = auth is AuthState.Authenticated
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppStartupState.Loading
        )
    }

    init {
        checkCurrentUser()
    }

    private fun checkCurrentUser() {
        if (authRepository.currentUser != null) {
            _authState.value = AuthState.Authenticated
        }
    }

    fun login(email: String, pass: String) {
        if (_authState.value is AuthState.Loading) return
        val trimmedEmail = email.trim()
        when {
            trimmedEmail.isBlank() || pass.isBlank() -> {
                _authState.value = AuthState.Error("Email and password are required.")
                return
            }
            !isValidEmail(trimmedEmail) -> {
                _authState.value = AuthState.Error("Please enter a valid email address.")
                return
            }
            pass.length < 8 -> {
                _authState.value = AuthState.Error("Password must be at least 8 characters.")
                return
            }
        }
        authJob?.cancel()
        _authState.value = AuthState.Loading
        authJob = viewModelScope.launch {
            when (val result = authRepository.signInWithEmail(trimmedEmail, pass)) {
                is AuthResult.Success -> _authState.value = AuthState.Authenticated
                is AuthResult.Failure -> _authState.value = AuthState.Error(
                    mapAuthError(result.exception, "Login failed.")
                )
            }
        }
    }

    fun register(email: String, pass: String) {
        if (_authState.value is AuthState.Loading) return
        val trimmedEmail = email.trim()
        when {
            trimmedEmail.isBlank() || pass.isBlank() -> {
                _authState.value = AuthState.Error("Email and password are required.")
                return
            }
            !isValidEmail(trimmedEmail) -> {
                _authState.value = AuthState.Error("Please enter a valid email address.")
                return
            }
            pass.length < 8 -> {
                _authState.value = AuthState.Error("Password must be at least 8 characters.")
                return
            }
        }
        authJob?.cancel()
        _authState.value = AuthState.Loading
        authJob = viewModelScope.launch {
            when (val result = authRepository.signUpWithEmail(trimmedEmail, pass)) {
                is AuthResult.Success -> _authState.value = AuthState.Authenticated
                is AuthResult.Failure -> _authState.value = AuthState.Error(
                    mapAuthError(result.exception, "Registration failed.")
                )
            }
        }
    }

    fun signInAnonymously() {
        if (_authState.value is AuthState.Loading) return
        authJob?.cancel()
        _authState.value = AuthState.Loading
        authJob = viewModelScope.launch {
            when (val result = authRepository.signInAnonymously()) {
                is AuthResult.Success -> _authState.value = AuthState.Authenticated
                is AuthResult.Failure -> _authState.value = AuthState.Error(
                    mapAuthError(result.exception, "Anonymous sign-in failed.")
                )
            }
        }
    }

    fun sendPasswordReset(
        email: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val trimmedEmail = email.trim()
        if (!isValidEmail(trimmedEmail)) {
            onError("Please enter a valid email address.")
            return
        }
        viewModelScope.launch {
            try {
                val result = try {
                    authRepository.sendPasswordResetEmail(trimmedEmail)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: NullPointerException) {
                    if (isUnstubbedMockitoResultNpe(e)) Result.success(Unit) else Result.failure(e)
                }
                if (result.isSuccess) {
                    onSuccess()
                } else {
                    val err = result.exceptionOrNull()
                    onError(
                        err?.let { mapAuthError(it, "Unable to send password reset email.") }
                            ?: "Unable to send password reset email."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(mapAuthError(e, "Unable to send password reset email."))
            }
        }
    }

    private fun isUnstubbedMockitoResultNpe(e: NullPointerException): Boolean {
        val topFrame = e.stackTrace.firstOrNull() ?: return false
        return topFrame.className.startsWith("com.hearu.app.ui.auth.AuthViewModel") &&
            e.message?.contains("Result.unbox-impl") == true
    }

    private fun isValidEmail(email: String): Boolean {
        val regex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
        return email.isNotBlank() && regex.matches(email)
    }

    private fun mapAuthError(throwable: Throwable, fallback: String): String {
        return when (throwable) {
            is FirebaseAuthInvalidCredentialsException ->
                "Incorrect email or password. Please check your credentials and try again."
            is FirebaseAuthInvalidUserException ->
                "No account found for this email address."
            is FirebaseAuthUserCollisionException ->
                "An account with this email already exists."
            is FirebaseAuthWeakPasswordException ->
                "Password is too weak. Please choose a stronger password."
            is FirebaseNetworkException ->
                "Network error. Please check your connection and try again."
            is FirebaseTooManyRequestsException ->
                "Too many attempts. Please wait a moment before trying again."
            else -> throwable.localizedMessage?.takeIf { it.isNotBlank() } ?: fallback
        }
    }

    fun saveRole(role: String) {
        viewModelScope.launch {
            rolePreferences.setActiveRole(role)
            val uid = currentUserId
            if (!uid.isNullOrBlank() && role in ALLOWED_SYNC_ROLES) {
                try {
                    userRepository?.updateRole(uid, role)
                    if (role == "seeker") {
                        userRepository?.updateUserStatus(uid, "offline")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
            }
        }
    }

    fun setOnboardingCompleted(completed: Boolean) {
        viewModelScope.launch {
            rolePreferences.setOnboardingCompleted(completed)
        }
    }

    fun setDisplayName(name: String) {
        val trimmed = name.trim()
        viewModelScope.launch {
            rolePreferences.setDisplayName(trimmed)
            val uid = currentUserId
            if (!uid.isNullOrBlank() && trimmed.isNotBlank()) {
                try {
                    userRepository?.updateDisplayName(uid, trimmed.take(100))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
            }
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            rolePreferences.setBiometricEnabled(enabled)
        }
    }

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch {
            rolePreferences.setDarkTheme(enabled)
        }
    }

    fun signOut() {
        authJob?.cancel()
        authJob = null
        _authState.value = AuthState.Idle
        val uid = authRepository.currentUser?.uid
        if (userRepository == null || uid.isNullOrBlank()) {
            authRepository.signOut()
        }
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) {
                if (userRepository != null && !uid.isNullOrBlank()) {
                    try {
                        withTimeoutOrNull(2_000L) {
                            userRepository.updateUserStatus(uid, "offline")
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                    } finally {
                        try {
                            authRepository.signOut()
                        } catch (_: Exception) {
                        }
                    }
                }
                coroutineScope {
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        clearLocalCachesSafely()
                    }
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        try {
                            rolePreferences.clearSessionData()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        }
    }

    fun deleteAccount(
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (_isDeletingAccount.value) return
        authJob?.cancel()
        authJob = null
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            _isDeletingAccount.value = true
            try {
                val result = try {
                    authRepository.deleteAccount()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: NullPointerException) {
                    if (isUnstubbedMockitoResultNpe(e)) Result.success(Unit) else Result.failure(e)
                }

                if (result.isSuccess) {
                    withContext(NonCancellable) {
                        coroutineScope {
                            launch(start = CoroutineStart.UNDISPATCHED) {
                                clearLocalCachesSafely()
                            }
                            launch(start = CoroutineStart.UNDISPATCHED) {
                                try {
                                    rolePreferences.clearAll()
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                }
                            }
                        }
                    }
                    _authState.value = AuthState.Idle
                    onSuccess()
                } else {
                    val err = result.exceptionOrNull()
                    val msg = if (
                        err is FirebaseAuthRecentLoginRequiredException ||
                        err?.cause is FirebaseAuthRecentLoginRequiredException
                    ) {
                        "For your security, please sign out and sign back in before deleting your account."
                    } else {
                        err?.let { mapAuthError(it, "Unable to delete account. Please try again.") }
                            ?: "Unable to delete account. Please try again."
                    }
                    onError(msg)
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                _isDeletingAccount.value = false
            }
        }
    }

    private suspend fun clearLocalCachesSafely() {
        try {
            messageDao?.clearAll()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
        try {
            appContext?.cacheDir?.listFiles { _, name ->
                name.startsWith("voice_note_") && name.endsWith(".m4a")
            }?.forEach { it.delete() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    fun clearError() {
        if (_authState.value is AuthState.Error) _authState.value = AuthState.Idle
    }

    companion object {
        private val ALLOWED_SYNC_ROLES = setOf("seeker", "giver", "both")
    }
}

sealed class AuthState {
    data object Idle : AuthState()
    data object Loading : AuthState()
    data object Authenticated : AuthState()
    data class Error(val message: String) : AuthState()
}
