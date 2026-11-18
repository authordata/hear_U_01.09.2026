package com.hearu.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

sealed class MatchState {
    data object Idle : MatchState()
    data class Searching(val selectedTags: List<String>, val elapsedSeconds: Int = 0) : MatchState()
    data class MatchFound(
        val sessionId: String,
        val giverId: String,
        val giverDisplayName: String,
        val giverRating: Double = 5.0
    ) : MatchState()
    data class NoMatchFound(val message: String) : MatchState()
    data class Error(val message: String) : MatchState()
}

@HiltViewModel
class MatchViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val functions: FirebaseFunctions
) : ViewModel() {

    constructor(auth: FirebaseAuth) : this(auth, FirebaseFunctions.getInstance())

    private val _matchState = MutableStateFlow<MatchState>(MatchState.Idle)
    val matchState: StateFlow<MatchState> = _matchState.asStateFlow()

    private var matchJob: Job? = null

    fun startMatch(tags: List<String>) {
        matchJob?.cancel()
        matchJob = viewModelScope.launch {
            _matchState.value = MatchState.Searching(selectedTags = tags, elapsedSeconds = 0)
            val timerJob = launch {
                var seconds = 0
                while (isActive && seconds < 300) {
                    delay(1000L)
                    seconds++
                    val current = _matchState.value
                    if (current is MatchState.Searching) {
                        _matchState.value = current.copy(elapsedSeconds = seconds)
                    } else {
                        break
                    }
                }
            }

            try {
                if (auth.currentUser == null) {
                    timerJob.cancel()
                    _matchState.value = MatchState.Error(
                        "Please sign in before searching for a listener."
                    )
                    return@launch
                }

                val data = hashMapOf("tags" to tags)
                val result = functions
                    .getHttpsCallable("matchSeekerWithGiver")
                    .call(data)
                    .await()

                timerJob.cancel()
                if (!isActive) return@launch

                @Suppress("UNCHECKED_CAST")
                val responseMap = result.data as? Map<String, Any>
                val matchFound = responseMap?.get("matchFound") as? Boolean ?: false

                if (matchFound) {
                    val sessionId = responseMap?.get("sessionId") as? String
                        ?: "session_${System.currentTimeMillis()}"
                    val giverName = responseMap?.get("giverDisplayName") as? String
                        ?: "Empathetic Listener"
                    val giverId = responseMap?.get("giverId") as? String
                        ?: "listener_${System.currentTimeMillis()}"
                    _matchState.value = MatchState.MatchFound(
                        sessionId = sessionId,
                        giverId = giverId,
                        giverDisplayName = giverName,
                        giverRating = 5.0
                    )
                } else {
                    _matchState.value = MatchState.NoMatchFound(
                        responseMap?.get("message") as? String
                            ?: "All listeners are currently occupied. Feel free to talk with our 24/7 AI Companion or try again soon."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!isActive) return@launch
                _matchState.value = MatchState.Error(
                    e.localizedMessage?.takeIf { it.isNotBlank() }
                        ?: "Unable to reach matching service. Please check your connection and try again."
                )
            } finally {
                timerJob.cancel()
            }
        }
    }

    fun cancelMatch() {
        matchJob?.cancel()
        matchJob = null
        _matchState.value = MatchState.Idle
    }

    fun reset() {
        matchJob?.cancel()
        matchJob = null
        _matchState.value = MatchState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        matchJob?.cancel()
        matchJob = null
    }
}
