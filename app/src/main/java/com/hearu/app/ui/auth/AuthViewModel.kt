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
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
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
            authRepository.signInWithEmail(email, pass)
                .onSuccess { _authState.value = AuthState.Authenticated }
                .onFailure { _authState.value = AuthState.Error(it.localizedMessage ?: "Login failed.") }
        }
    }

    fun register(email: String, pass: String) {
        when {
            email.isBlank() || pass.isBlank() -> {
                _authState.value = AuthState.Error("Email and password are required.")
                return
            }
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
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
            authRepository.signUpWithEmail(email, pass)
                .onSuccess { _authState.value = AuthState.Authenticated }
                .onFailure { _authState.value = AuthState.Error(it.localizedMessage ?: "Registration failed.") }
        }
    }

    fun saveRole(role: String) {
        viewModelScope.launch {
            rolePreferences.setActiveRole(role)
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
