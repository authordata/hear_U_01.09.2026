package com.hearu.app.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.hearu.app.model.ChatSession
import com.hearu.app.model.UserStatus
import com.hearu.app.repository.ChatRepository
import com.hearu.app.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GiverViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val chatRepository: ChatRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _activeSession = MutableStateFlow<ChatSession?>(null)
    val activeSession: StateFlow<ChatSession?> = _activeSession.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    private var sessionJob: Job? = null
    private var statusJob: Job? = null

    fun setAvailability(isOnline: Boolean) {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) return

        statusJob?.cancel()
        statusJob = viewModelScope.launch {
            try {
                val status = if (isOnline) UserStatus.ONLINE.wireValue else UserStatus.OFFLINE.wireValue
                userRepository.updateUserStatus(uid, status)
                _syncError.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Log.w(TAG, "Failed to update giver availability status")
                _syncError.value = "Unable to sync availability status. Check your connection."
            }
        }

        if (isOnline) {
            observeIncomingSession(uid)
        } else {
            sessionJob?.cancel()
            sessionJob = null
            _activeSession.value = null
        }
    }

    private fun observeIncomingSession(uid: String) {
        sessionJob?.cancel()
        sessionJob = viewModelScope.launch {
            chatRepository.observeActiveGiverSession(uid)
                .catch {
                    Log.w(TAG, "Active giver session observer unavailable")
                    _activeSession.value = null
                }
                .collect { session ->
                    _activeSession.value = session
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        sessionJob?.cancel()
        statusJob?.cancel()
    }

    companion object {
        private const val TAG = "GiverViewModel"
    }
}
