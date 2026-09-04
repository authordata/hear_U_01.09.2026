package com.hearu.app.ui.auth

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hearu.app.data.RolePreferences
import com.hearu.app.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val rolePreferences: RolePreferences
) : ViewModel() {

    val activeRole = rolePreferences.activeRoleFlow
    val isOnboardingCompleted = rolePreferences.isOnboardingCompletedFlow

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        checkCurrentUser()
    }

    private fun checkCurrentUser() {
        if (authRepository.currentUser != null) {
            _authState.value = AuthState.Authenticated
        }
    }

    fun login(email: String, pass: String) {
        when {
            email.isBlank() || pass.isBlank() -> {
                _authState.value = AuthState.Error("Email and password are required.")
                return
            }
            !isValidEmail(email) -> {
                _authState.value = AuthState.Error("Please enter a valid email address.")
                return
            }
            pass.length < 8 -> {
                _authState.value = AuthState.Error("Password must be at least 8 characters.")
                return
            }
        }
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            when (val result = authRepository.signInWithEmail(email, pass)) {
                is com.hearu.app.repository.AuthResult.Success -> _authState.value = AuthState.Authenticated
                is com.hearu.app.repository.AuthResult.Failure -> _authState.value = AuthState.Error(result.exception.localizedMessage ?: "Login failed.")
            }
        }
    }

    fun register(email: String, pass: String) {
        when {
            email.isBlank() || pass.isBlank() -> {
                _authState.value = AuthState.Error("Email and password are required.")
                return
            }
            !isValidEmail(email) -> {
                _authState.value = AuthState.Error("Please enter a valid email address.")
                return
            }
            pass.length < 8 -> {
                _authState.value = AuthState.Error("Password must be at least 8 characters.")
                return
            }
        }
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            when (val result = authRepository.signUpWithEmail(email, pass)) {
                is com.hearu.app.repository.AuthResult.Success -> _authState.value = AuthState.Authenticated
                is com.hearu.app.repository.AuthResult.Failure -> _authState.value = AuthState.Error(result.exception.localizedMessage ?: "Registration failed.")
            }
        }
    }

    private fun isValidEmail(email: String): Boolean {
        val regex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
        return email.isNotBlank() && regex.matches(email)
    }

    fun saveRole(role: String) {
        viewModelScope.launch {
            rolePreferences.setActiveRole(role)
        }
    }

    fun setOnboardingCompleted(completed: Boolean) {
        viewModelScope.launch {
            rolePreferences.setOnboardingCompleted(completed)
        }
    }

    fun signOut() {
        authRepository.signOut()
        _authState.value = AuthState.Idle
    }

    fun clearError() {
        if (_authState.value is AuthState.Error) _authState.value = AuthState.Idle
    }
}

sealed class AuthState {
    data object Idle : AuthState()
    data object Loading : AuthState()
    data object Authenticated : AuthState()
    data class Error(val message: String) : AuthState()
}
