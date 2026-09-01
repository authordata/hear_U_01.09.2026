package com.hearu.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.hearu.app.data.RolePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val rolePreferences: RolePreferences
) : ViewModel() {

    val activeRole = rolePreferences.activeRoleFlow

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        checkCurrentUser()
    }

    private fun checkCurrentUser() {
        if (auth.currentUser != null) {
            _authState.value = AuthState.Authenticated
        }
    }

    fun login() {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            kotlinx.coroutines.delay(1000)
            _authState.value = AuthState.Authenticated
        }
    }

    fun saveRole(role: String) {
        viewModelScope.launch {
            rolePreferences.setActiveRole(role)
        }
    }
}

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object Authenticated : AuthState()
    data class Error(val message: String) : AuthState()
}
