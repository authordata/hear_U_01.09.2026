package com.hearu.app

import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import com.hearu.app.ui.home.MatchState
import com.hearu.app.ui.home.MatchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class MatchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val auth: FirebaseAuth = mock()
    private val mockUser: FirebaseUser = mock()
    private val functions: FirebaseFunctions = mock()
    private val callable: HttpsCallableReference = mock()
    private lateinit var viewModel: MatchViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        whenever(auth.currentUser).thenReturn(mockUser)
        whenever(mockUser.uid).thenReturn("seeker_uid_1")
        whenever(functions.getHttpsCallable("matchSeekerWithGiver")).thenReturn(callable)
        viewModel = MatchViewModel(auth, functions)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Idle`() {
        assertEquals(MatchState.Idle, viewModel.matchState.value)
    }

    @Test
    fun `startMatch ticks elapsedSeconds and emits MatchFound when matchFound is true`() = runTest(testDispatcher) {
        val tcs = TaskCompletionSource<HttpsCallableResult>()
        whenever(callable.call(any())).thenReturn(tcs.task)

        viewModel.startMatch(listOf("Anxiety"))
        testDispatcher.scheduler.runCurrent()

        val initial = viewModel.matchState.value
        assertTrue(initial is MatchState.Searching)
        assertEquals(0, (initial as MatchState.Searching).elapsedSeconds)

        testDispatcher.scheduler.advanceTimeBy(2100L)
        testDispatcher.scheduler.runCurrent()
        val ticked = viewModel.matchState.value as MatchState.Searching
        assertTrue("Expected elapsedSeconds >= 2, was ${ticked.elapsedSeconds}", ticked.elapsedSeconds >= 2)

        val result: HttpsCallableResult = mock()
        whenever(result.data).thenReturn(
            mapOf(
                "matchFound" to true,
                "sessionId" to "sess_42",
                "giverId" to "giver_7",
                "giverDisplayName" to "WarmListener"
            )
        )
        tcs.setResult(result)
        testDispatcher.scheduler.advanceUntilIdle()

        val finalState = viewModel.matchState.value
        assertTrue(finalState is MatchState.MatchFound)
        finalState as MatchState.MatchFound
        assertEquals("sess_42", finalState.sessionId)
        assertEquals("giver_7", finalState.giverId)
        assertEquals("WarmListener", finalState.giverDisplayName)
    }

    @Test
    fun `startMatch emits NoMatchFound when no listener is available`() = runTest(testDispatcher) {
        val result: HttpsCallableResult = mock()
        whenever(result.data).thenReturn(mapOf("matchFound" to false))
        whenever(callable.call(any())).thenReturn(Tasks.forResult(result))

        viewModel.startMatch(listOf("Stress"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.matchState.value is MatchState.NoMatchFound)
    }

    @Test
    fun `startMatch emits MatchState Error on failure without fake demo match`() = runTest(testDispatcher) {
        whenever(callable.call(any())).thenReturn(Tasks.forException(RuntimeException("Network error")))

        viewModel.startMatch(listOf("Loneliness"))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.matchState.value
        assertTrue("Expected MatchState.Error, was $state", state is MatchState.Error)
        assertTrue((state as MatchState.Error).message.contains("Network error"))
    }

    @Test
    fun `cancelMatch cancels in-flight matchJob and ignores late function completion`() = runTest(testDispatcher) {
        val tcs = TaskCompletionSource<HttpsCallableResult>()
        whenever(callable.call(any())).thenReturn(tcs.task)

        viewModel.startMatch(listOf("Anxiety"))
        testDispatcher.scheduler.runCurrent()
        assertTrue(viewModel.matchState.value is MatchState.Searching)

        viewModel.cancelMatch()
        assertEquals(MatchState.Idle, viewModel.matchState.value)

        val lateResult: HttpsCallableResult = mock()
        whenever(lateResult.data).thenReturn(
            mapOf(
                "matchFound" to true,
                "sessionId" to "late_session",
                "giverId" to "late_giver"
            )
        )
        tcs.setResult(lateResult)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(MatchState.Idle, viewModel.matchState.value)
    }
}
