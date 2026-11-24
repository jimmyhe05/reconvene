package com.reconvene.ui.group

import com.reconvene.data.auth.LOCAL_USER
import com.reconvene.data.model.GroupMessage
import com.reconvene.data.model.MessageStatus
import com.reconvene.data.model.PodMember
import com.reconvene.data.model.ReportReason
import com.reconvene.data.remote.FakeGroupRemoteDataSource
import com.reconvene.data.remote.GroupRemoteDataSource
import com.reconvene.data.repository.OfflineFirstGroupRepository
import com.reconvene.testing.FakeGroupDao
import com.reconvene.testing.FakeMessageDao
import com.reconvene.testing.GroupTestPodDao
import com.reconvene.testing.MainDispatcherRule
import com.reconvene.testing.TestAuthRepository
import com.reconvene.ui.create.CreatePodForm
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class GroupViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val zone = ZoneId.of("America/New_York")
    private val clock = Clock.fixed(ZonedDateTime.of(2026, 11, 17, 10, 0, 0, 0, zone).toInstant(), zone)
    private val fake = FakeGroupRemoteDataSource(clock, delayMillis = 0)
    private val remote = ControlledRemote(fake)
    private val messageDao = FakeMessageDao()
    private val podDao = GroupTestPodDao()
    private var nextId = 0
    private val repository =
        OfflineFirstGroupRepository(FakeGroupDao(messageDao), messageDao, podDao, remote, clock) { "new-${nextId++}" }

    // The sample groups are built around LOCAL_USER: a member of g1, invited to g2, host of g3.
    // The Groups tab keeps them in Room while the app runs; here the test does.
    private fun TestScope.viewModel(groupId: String): GroupViewModel {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.syncGroups(LOCAL_USER.uid) }
        val viewModel = GroupViewModel(groupId, repository, TestAuthRepository(LOCAL_USER), clock) { "proposal" }
        // uiState only updates while something collects it, as the screen does.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    private fun GroupViewModel.content() = uiState.value as GroupUiState.Content

    private fun texts(messages: List<GroupMessage>) = messages.map { it.text }

    private val tomorrowAt4 = CreatePodForm(
        building = "CDS",
        meetingPoint = "3rd floor commons",
        day = LocalDate.of(2026, 11, 18),
        startTime = LocalTime.of(16, 0),
        endTime = LocalTime.of(17, 30),
    )

    // As after Android restores the app straight onto this screen: nothing else is syncing groups.
    @Test
    fun theGroupScreenLoadsTheGroupAndItsVotesOnItsOwn() = runTest {
        val viewModel = GroupViewModel("g1", repository, TestAuthRepository(LOCAL_USER), clock) { "proposal" }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        assertEquals("Algorithms study group", viewModel.content().group.name)

        val sam = PodMember("sample-sam", "Sam")
        fake.vote(viewModel.content().group, sam, canCome = true)

        assertEquals(true, viewModel.content().group.proposal?.voteOf(sam.uid))
    }

    @Test
    fun messagesShowOldestFirst() = runTest {
        val viewModel = viewModel("g1")

        val content = viewModel.content()
        assertEquals("Algorithms study group", content.group.name)
        assertEquals(
            listOf(
                "Thanks for coming today. Same time next week?",
                "Works for me. Can someone share the practice set?",
                "Start with problems 3 and 4.",
                "I proposed a time for the next session. Vote in the plan.",
            ),
            texts(content.messages),
        )
    }

    @Test
    fun aGroupYouAreOnlyInvitedToIsGone() = runTest {
        val viewModel = viewModel("g2")

        assertEquals(GroupUiState.Gone, viewModel.uiState.value)
    }

    @Test
    fun sendAddsTheMessageAtTheEnd() = runTest {
        val viewModel = viewModel("g1")

        viewModel.send("  See you there  ")

        val last = viewModel.content().messages.last()
        assertEquals("See you there", last.text)
        assertEquals(LOCAL_USER.uid, last.authorUid)
        assertEquals(MessageStatus.SENT, last.status)
    }

    @Test
    fun aFailedSendCanBeRetried() = runTest {
        val viewModel = viewModel("g1")
        remote.failSends = true

        viewModel.send("Anyone have notes?")
        val failed = viewModel.content().messages.last()
        assertEquals(MessageStatus.FAILED, failed.status)

        remote.failSends = false
        viewModel.retry(failed.id)

        val retried = viewModel.content().messages.last()
        assertEquals(failed.id, retried.id)
        assertEquals(MessageStatus.SENT, retried.status)
    }

    @Test
    fun blockingHidesTheirMessages() = runTest {
        val viewModel = viewModel("g1")

        viewModel.block("sample-maya")

        val content = viewModel.content()
        assertEquals(listOf("Jordan", "Sam"), content.messages.map { it.authorName })
        assertEquals(GroupNotice.BLOCKED, viewModel.notice.value)
    }

    @Test
    fun reportSendsTheReasonAndConfirms() = runTest {
        val viewModel = viewModel("g1")

        viewModel.report("m3", ReportReason.SPAM)

        val report = fake.reports.single()
        assertEquals("sample-sam", report.targetUid)
        assertEquals(ReportReason.SPAM, report.reason)
        assertEquals(GroupNotice.REPORTED, viewModel.notice.value)
    }

    @Test
    fun votingYesCountsYou() = runTest {
        val viewModel = viewModel("g1")
        assertNull(viewModel.content().myVote)
        assertEquals(2, viewModel.content().yesCount)

        viewModel.vote(canCome = true)

        val content = viewModel.content()
        assertEquals(true, content.myVote)
        assertEquals(3, content.yesCount)
        assertFalse(content.isBusy)
    }

    @Test
    fun hostSchedulesOnceTwoCanCome() = runTest {
        val viewModel = viewModel("g3")
        viewModel.propose(tomorrowAt4)
        assertEquals(GroupNotice.PROPOSED, viewModel.notice.value)
        // Proposing counts the host as coming, which is one.
        assertEquals(1, viewModel.content().yesCount)
        assertFalse(viewModel.content().canSchedule)

        repository.vote("g3", PodMember("sample-noah", "Noah"), canCome = true)
        assertTrue(viewModel.content().canSchedule)
        viewModel.schedule()

        assertEquals(GroupNotice.SCHEDULED, viewModel.notice.value)
        assertNull(viewModel.content().proposal)
        val pod = requireNotNull(podDao.getById("proposal"))
        assertEquals("Deep work crew", pod.title)
    }

    @Test
    fun aRefusalShowsWhy() = runTest {
        // Maya hosts g1, so LOCAL_USER can't schedule it.
        val viewModel = viewModel("g1")

        viewModel.schedule()

        assertEquals(GroupNotice.NOT_HOST, viewModel.notice.value)
        assertNotNull(viewModel.content().proposal)
    }

    @Test
    fun chatThatCantSyncStillShowsTheGroup() = runTest {
        remote.failMessageSync = true
        val viewModel = viewModel("g1")

        val content = viewModel.content()
        assertTrue(content.chatSyncFailed)
        assertTrue(content.messages.isEmpty())

        remote.failMessageSync = false
        viewModel.retrySync()

        assertFalse(viewModel.content().chatSyncFailed)
        assertEquals(4, viewModel.content().messages.size)
    }
}

private class ControlledRemote(private val inner: GroupRemoteDataSource) : GroupRemoteDataSource by inner {
    var failSends = false
    var failMessageSync = false

    override fun observeMessages(groupId: String): Flow<List<GroupMessage>> = flow {
        if (failMessageSync) throw IOException("No connection")
        inner.observeMessages(groupId).collect { emit(it) }
    }

    override suspend fun sendMessage(message: GroupMessage) {
        if (failSends) throw IOException("No connection")
        inner.sendMessage(message)
    }
}
