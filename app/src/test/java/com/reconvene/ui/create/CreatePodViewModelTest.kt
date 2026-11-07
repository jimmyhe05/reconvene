package com.reconvene.ui.create

import androidx.lifecycle.SavedStateHandle
import com.reconvene.data.model.Pod
import com.reconvene.data.repository.PodRepository
import com.reconvene.testing.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class CreatePodViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val zone = ZoneId.of("America/New_York")
    private val clock = Clock.fixed(ZonedDateTime.of(2026, 11, 10, 14, 0, 0, 0, zone).toInstant(), zone)
    private val repository = RecordingPodRepository()
    private val savedState = SavedStateHandle()
    private val viewModel = CreatePodViewModel(savedState, repository, clock, hostName = "You")

    private fun fillIn() = viewModel.updateForm(
        viewModel.uiState.value.form.copy(
            title = "Algorithms exam review",
            building = "Mugar Library",
            meetingPoint = "2nd floor",
        )
    )

    @Test
    fun startsWithTheDefaultTimeAndNoErrors() {
        val state = viewModel.uiState.value

        assertEquals(LocalTime.of(14, 30), state.form.startTime)
        assertTrue(state.errors.isEmpty)
    }

    @Test
    fun errorsAppearOnlyAfterCreateAndClearAsFieldsAreFixed() {
        viewModel.updateForm(viewModel.uiState.value.form.copy(title = "A"))
        assertTrue(viewModel.uiState.value.errors.isEmpty)

        viewModel.create()
        assertEquals(FormError.TOPIC_TOO_SHORT, viewModel.uiState.value.errors.title)
        assertTrue(repository.created.isEmpty())

        viewModel.updateForm(viewModel.uiState.value.form.copy(title = "Algorithms"))
        assertNull(viewModel.uiState.value.errors.title)
        assertEquals(FormError.BUILDING_MISSING, viewModel.uiState.value.errors.building)
    }

    @Test
    fun validFormSavesAPodHostedByYou() {
        fillIn()

        viewModel.create()

        assertTrue(viewModel.uiState.value.isCreated)
        val pod = repository.created.single()
        assertEquals("Algorithms exam review", pod.title)
        assertEquals("You", pod.hostName)
    }

    @Test
    fun formComesBackFromSavedStateAfterProcessDeath() {
        fillIn()

        val restored = CreatePodViewModel(savedState, repository, clock, hostName = "You")

        assertEquals(viewModel.uiState.value.form, restored.uiState.value.form)
    }

    @Test
    fun failedSaveLetsTheUserTryAgain() {
        repository.shouldFail = true
        fillIn()

        viewModel.create()
        assertTrue(viewModel.uiState.value.saveFailed)
        assertFalse(viewModel.uiState.value.isSaving)

        repository.shouldFail = false
        viewModel.create()
        assertTrue(viewModel.uiState.value.isCreated)
    }
}

private class RecordingPodRepository : PodRepository {
    val created = mutableListOf<Pod>()
    var shouldFail = false

    override fun observePods(): Flow<List<Pod>> = flowOf(created)

    override suspend fun refresh() = Unit

    override suspend fun createPod(pod: Pod) {
        if (shouldFail) throw IOException("Disk full")
        created += pod
    }
}
