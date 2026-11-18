package com.hearu.app.ui.auth

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.hearu.app.auth.BiometricHelper
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
class AuthViewModel : ViewModel {
    private val authRepository: AuthRepository
    private val rolePreferences: RolePreferences
    private val messageDao: MessageDao?
    private val appContext: Context?
    private val userRepository: UserRepository?
    internal val biometricHelper: BiometricHelper?

    @Inject
    constructor(
        authRepository: AuthRepository,
        rolePreferences: RolePreferences,
        messageDao: MessageDao,
        @ApplicationContext appContext: Context,
        userRepository: UserRepository,
        biometricHelper: BiometricHelper
    ) : super() {
        this.authRepository = authRepository
        this.rolePreferences = rolePreferences
        this.messageDao = messageDao
        this.appContext = appContext
        this.userRepository = userRepository
        this.biometricHelper = biometricHelper
        checkCurrentUser()
    }

    constructor(
        authRepository: AuthRepository,
        rolePreferences: RolePreferences,
        messageDao: MessageDao,
        appContext: Context,
        userRepository: UserRepository
    ) : super() {
        this.authRepository = authRepository
        this.rolePreferences = rolePreferences
        this.messageDao = messageDao
        this.appContext = appContext
        this.userRepository = userRepository
        this.biometricHelper = null
        checkCurrentUser()
    }

    constructor(
        authRepository: AuthRepository,
        rolePreferences: RolePreferences,
        messageDao: MessageDao,
        appContext: Context
    ) : super() {
        this.authRepository = authRepository
        this.rolePreferences = rolePreferences
        this.messageDao = messageDao
        this.appContext = appContext
        this.userRepository = null
        this.biometricHelper = null
        checkCurrentUser()
    }

    constructor(
        authRepository: AuthRepository,
        rolePreferences: RolePreferences
    ) : super() {
        this.authRepository = authRepository
        this.rolePreferences = rolePreferences
        this.messageDao = null
        this.appContext = null
        this.userRepository = null
        this.biometricHelper = null
        checkCurrentUser()
    }

    val activeRole: kotlinx.coroutines.flow.Flow<String?>
        get() = rolePreferences.activeRoleFlow
    val isOnboardingCompleted: kotlinx.coroutines.flow.Flow<Boolean>
        get() = rolePreferences.isOnboardingCompletedFlow
    val displayName: kotlinx.coroutines.flow.Flow<String>
        get() = rolePreferences.displayNameFlow
    val isBiometricEnabled: kotlinx.coroutines.flow.Flow<Boolean>
        get() = rolePreferences.isBiometricEnabledFlow
    val isDarkTheme: kotlinx.coroutines.flow.Flow<Boolean?>
        get() = rolePreferences.isDarkThemeFlow

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _isDeletingAccount = MutableStateFlow(false)
    val isDeletingAccount: StateFlow<Boolean> = _isDeletingAccount.asStateFlow()

    private var authJob: Job? = null

    val currentUserId: String?
        get() = authRepository.currentUser?.uid

    val startupState: StateFlow<AppStartupState> by lazy {
        combine(
            rolePreferences.isOnboardingCompletedFlow,
            rolePreferences.activeRoleFlow,
            rolePreferences.isBiometricEnabledFlow,
            rolePreferences.isDarkThemeFlow,
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
                val result = authRepository.sendPasswordResetEmail(trimmedEmail)
                if (result.isSuccess) {
                    onSuccess()
                } else {
                    val err = result.exceptionOrNull()
                    if (err is FirebaseAuthInvalidUserException) {
                        // Prevent email enumeration on password reset
                        onSuccess()
                    } else {
                        onError(
                            err?.let { mapAuthError(it, "Unable to send password reset email.") }
                                ?: "Unable to send password reset email."
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(mapAuthError(e, "Unable to send password reset email."))
            }
        }
    }

    private fun isValidEmail(email: String): Boolean {
        return email.isNotBlank() && EMAIL_REGEX.matches(email)
    }

    private fun mapAuthError(throwable: Throwable, fallback: String): String {
        return when (throwable) {
            is FirebaseAuthInvalidCredentialsException,
            is FirebaseAuthInvalidUserException ->
                "Incorrect email or password. Please check your credentials and try again."
            is FirebaseAuthUserCollisionException ->
                "Unable to complete registration with these credentials. Try signing in instead."
            is FirebaseAuthWeakPasswordException ->
                "Password is too weak. Please choose a stronger password."
            is FirebaseNetworkException ->
                "Network error. Please check your connection and try again."
            is FirebaseTooManyRequestsException ->
                "Too many attempts. Please wait a moment before trying again."
            else -> throwable.localizedMessage?.takeIf { it.isNotBlank() } ?: fallback
        }
    }

    fun confirmAgeVerified() {
        viewModelScope.launch {
            try {
                rolePreferences.setAgeVerified(true)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Log.w(TAG, "Failed to persist age verification preference")
            }
            val uid = currentUserId
            if (!uid.isNullOrBlank()) {
                try {
                    userRepository?.updateAgeVerified(uid, true)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    Log.w(TAG, "Failed to sync age verification to Firestore")
                }
            }
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
                    Log.w(TAG, "Failed to sync role update to Firestore")
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
                    Log.w(TAG, "Failed to sync display name to Firestore")
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
                        Log.w(TAG, "Failed to set user status offline before sign-out")
                    } finally {
                        try {
                            authRepository.signOut()
                        } catch (_: Exception) {
                            Log.w(TAG, "Firebase signOut threw during cleanup")
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
                            Log.w(TAG, "Failed to clear session preferences on sign-out")
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
                } catch (e: Exception) {
                    Result.failure(e)
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
                                    Log.w(TAG, "Failed to clear preferences on account deletion")
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
            Log.w(TAG, "Failed to clear Room message cache")
        }
        try {
            appContext?.cacheDir?.listFiles { _, name ->
                name.startsWith("voice_note_") && name.endsWith(".m4a")
            }?.forEach { it.delete() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Log.w(TAG, "Failed to clear voice note cache files")
        }
    }

    fun clearError() {
        if (_authState.value is AuthState.Error) _authState.value = AuthState.Idle
    }

    companion object {
        private const val TAG = "AuthViewModel"
        private val ALLOWED_SYNC_ROLES = setOf("seeker", "giver", "both")
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    }
}

sealed class AuthState {
    data object Idle : AuthState()
    data object Loading : AuthState()
    data object Authenticated : AuthState()
    data class Error(val message: String) : AuthState()
}
