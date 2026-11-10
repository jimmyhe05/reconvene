package com.reconvene.ui.create

import androidx.lifecycle.SavedStateHandle
import com.reconvene.data.auth.UnavailableAuthRepository
import com.reconvene.data.model.Pod
import com.reconvene.data.model.PodMember
import com.reconvene.data.repository.PodRepository
import com.reconvene.testing.MainDispatcherRule
import com.reconvene.testing.TERRIER
import com.reconvene.testing.TestAuthRepository
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
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
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
    private val auth = TestAuthRepository()
    private val viewModel = CreatePodViewModel(savedState, repository, clock, auth)

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
    fun badTimeShowsRightAwayWithoutOtherErrors() {
        val form = viewModel.uiState.value.form

        viewModel.updateForm(form.copy(endTime = form.startTime.minusHours(1)))

        val errors = viewModel.uiState.value.errors
        assertEquals(FormError.END_NOT_AFTER_START, errors.time)
        assertNull(errors.title)
        assertNull(errors.building)

        viewModel.updateForm(viewModel.uiState.value.form.withStartTime(LocalTime.of(15, 0)))
        assertNull(viewModel.uiState.value.errors.time)
    }

    @Test
    fun validFormSavesAPodHostedByTheSignedInUser() {
        fillIn()

        viewModel.create()

        assertTrue(viewModel.uiState.value.isCreated)
        val pod = repository.created.single()
        assertEquals("Algorithms exam review", pod.title)
        assertEquals(TERRIER.uid, pod.hostUid)
        assertEquals(listOf(PodMember(TERRIER.uid, "Terrier")), pod.members)
    }

    @Test
    fun signedOutUserIsAskedToSignInInsteadOfSaving() {
        auth.currentUser.value = null
        fillIn()

        viewModel.create()

        assertTrue(viewModel.uiState.value.signInRequired)
        assertFalse(viewModel.uiState.value.isSaving)
        assertTrue(repository.created.isEmpty())
    }

    @Test
    fun withoutFirebaseThePodIsHostedByYou() {
        val local = CreatePodViewModel(SavedStateHandle(), repository, clock, UnavailableAuthRepository)
        local.updateForm(viewModel.uiState.value.form.copy(title = "Quiet hour", building = "GSU", meetingPoint = "Back tables"))

        local.create()

        assertEquals("You", repository.created.single().hostName)
    }

    @Test
    fun formComesBackFromSavedStateAfterProcessDeath() {
        fillIn()

        val restored = CreatePodViewModel(savedState, repository, clock, auth)

        assertEquals(viewModel.uiState.value.form, restored.uiState.value.form)
    }

    @Test
    fun todayMovesOnAfterMidnightOnResumeAndOnEdits() {
        val movingClock = MovingClock(ZonedDateTime.of(2026, 11, 10, 23, 50, 0, 0, zone).toInstant(), zone)
        val lateNight = CreatePodViewModel(SavedStateHandle(), repository, movingClock, auth)
        assertEquals(LocalDate.of(2026, 11, 10), lateNight.uiState.value.today)

        movingClock.now = movingClock.now.plus(Duration.ofMinutes(20))
        lateNight.refreshToday()
        assertEquals(LocalDate.of(2026, 11, 11), lateNight.uiState.value.today)

        movingClock.now = movingClock.now.plus(Duration.ofDays(1))
        lateNight.updateForm(lateNight.uiState.value.form.copy(title = "Algorithms"))
        assertEquals(LocalDate.of(2026, 11, 12), lateNight.uiState.value.today)
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

private class MovingClock(var now: Instant, private val zone: ZoneId) : Clock() {
    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = MovingClock(now, zone)

    override fun instant(): Instant = now
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

    override suspend fun joinPod(podId: String, member: PodMember) = Unit

    override suspend fun leavePod(podId: String, uid: String) = Unit

    override suspend fun cancelPod(podId: String, uid: String) = Unit
}
