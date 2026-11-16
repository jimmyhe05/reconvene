package com.reconvene.data.repository

import com.reconvene.data.auth.LOCAL_USER
import com.reconvene.data.local.GroupDao
import com.reconvene.data.local.GroupEntity
import com.reconvene.data.local.MessageDao
import com.reconvene.data.local.MessageEntity
import com.reconvene.data.local.PodDao
import com.reconvene.data.local.PodEntity
import com.reconvene.data.local.toEntity
import com.reconvene.data.local.toPod
import com.reconvene.data.model.GroupActionException
import com.reconvene.data.model.GroupMessage
import com.reconvene.data.model.GroupProblem
import com.reconvene.data.model.MessageStatus
import com.reconvene.data.model.PodMember
import com.reconvene.data.model.PodMode
import com.reconvene.data.model.SessionProposal
import com.reconvene.data.remote.FakeGroupRemoteDataSource
import com.reconvene.data.remote.GroupRemoteDataSource
import com.reconvene.testing.testPod
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineFirstGroupRepositoryTest {

    private val zone = ZoneId.of("America/New_York")
    private val clock = Clock.fixed(ZonedDateTime.of(2026, 11, 16, 10, 0, 0, 0, zone).toInstant(), zone)
    private val you = PodMember(LOCAL_USER.uid, "You")
    private val noah = PodMember("sample-noah", "Noah")

    private val remote = FlakyRemote(FakeGroupRemoteDataSource(clock, delayMillis = 0))
    private val messageDao = FakeMessageDao()
    private val groupDao = FakeGroupDao(messageDao)
    private val podDao = GroupTestPodDao()
    private var ids = 0
    private val repository = OfflineFirstGroupRepository(groupDao, messageDao, podDao, remote, clock) { "id-${++ids}" }

    // Keeps the cache synced for the rest of the test, the way an open Groups screen would.
    private fun TestScope.syncGroups() {
        backgroundScope.launch { repository.syncGroups(you.uid) }
        runCurrent()
    }

    private suspend fun group(id: String) = repository.observeGroup(id).first()

    private suspend fun refusal(block: suspend () -> Unit): GroupProblem? =
        (runCatching { block() }.exceptionOrNull() as? GroupActionException)?.problem

    @Test
    fun syncCachesGroupsTheUserIsInOrInvitedTo() = runTest {
        syncGroups()

        val names = repository.observeGroups().first().map { it.name }
        assertEquals(listOf("Algorithms study group", "Deep work crew", "Signals study group"), names)
    }

    @Test
    fun onlyThePodsHostKeepsItAndTheRestAreInvited() = runTest {
        val mine = testPod(id = "mine", startsAt = clock.instant(), members = 3)
            .let { it.copy(hostUid = you.uid, hostName = you.name, members = listOf(you) + it.members.drop(1)) }
        val theirs = testPod(id = "theirs", startsAt = clock.instant(), members = 3)
        podDao.upsertAll(listOf(mine.toEntity(), theirs.toEntity()))

        assertEquals(GroupProblem.NOT_HOST, refusal { repository.keepGroup("theirs", you.uid, "Study group") })

        val id = repository.keepGroup("mine", you.uid, "Study group")
        val kept = group(id)!!
        assertEquals(listOf(you), kept.members)
        assertEquals(listOf("member-1", "member-2"), kept.invited.map { it.uid })
    }

    @Test
    fun decliningAnInviteDropsTheGroup() = runTest {
        syncGroups()

        repository.answerInvite("g2", you.uid, accept = false)

        assertNull(group("g2"))
    }

    @Test
    fun theHostCantLeaveButCanHandOverAndIsGoneOnceItsAccepted() = runTest {
        syncGroups()

        assertEquals(GroupProblem.HOST_MUST_HAND_OVER, refusal { repository.leaveGroup("g3", you.uid) })

        repository.offerHandover("g3", you.uid, noah.uid)
        assertEquals(noah, group("g3")!!.hostHandover)

        // Noah accepts on his phone; the sync brings the change here.
        remote.acceptHandover(group("g3")!!, noah.uid)
        runCurrent()
        assertNull(group("g3"))
    }

    @Test
    fun disbandingDropsTheGroupAndItsChat() = runTest {
        syncGroups()
        backgroundScope.launch { repository.syncMessages("g3") }
        runCurrent()
        assertTrue(repository.observeMessages("g3").first().isNotEmpty())

        assertEquals(GroupProblem.NOT_HOST, refusal { repository.disbandGroup("g1", you.uid) })
        repository.disbandGroup("g3", you.uid)

        assertNull(group("g3"))
        assertTrue(repository.observeMessages("g3").first().isEmpty())
    }

    @Test
    fun aMessageIsSavedAtOnceAndMarkedSent() = runTest {
        syncGroups()

        repository.sendMessage("g1", you, "  See you at Mugar  ")

        val message = repository.observeMessages("g1").first().single()
        assertEquals("See you at Mugar", message.text)
        assertEquals(MessageStatus.SENT, message.status)
        assertEquals(GroupProblem.BAD_MESSAGE, refusal { repository.sendMessage("g1", you, "   ") })
        assertEquals(GroupProblem.NOT_MEMBER, refusal { repository.sendMessage("g2", you, "Hi") })
    }

    @Test
    fun aFailedMessageStaysAndCanBeRetried() = runTest {
        syncGroups()
        remote.failSends = true

        repository.sendMessage("g1", you, "Running late")
        val failed = repository.observeMessages("g1").first().single()
        assertEquals(MessageStatus.FAILED, failed.status)

        remote.failSends = false
        repository.retryMessage(failed.id)
        val retried = repository.observeMessages("g1").first().single()
        assertEquals(failed.id, retried.id)
        assertEquals(MessageStatus.SENT, retried.status)
    }

    @Test
    fun theHostSchedulesWhenSomeoneElseCanComeAndThePodIsSaved() = runTest {
        syncGroups()
        val start = clock.instant().plus(Duration.ofDays(1))
        val proposal = SessionProposal(
            id = "next",
            startsAt = start,
            endsAt = start.plus(Duration.ofMinutes(90)),
            building = "GSU",
            meetingPoint = "Back tables",
            mode = PodMode.QUIET,
            byUid = you.uid,
        )

        repository.propose("g3", proposal)
        assertEquals(true, group("g3")!!.proposal?.voteOf(you.uid))
        assertEquals(GroupProblem.NOT_ENOUGH_YES, refusal { repository.schedule("g3", you.uid) })

        remote.vote(group("g3")!!, noah, canCome = true)
        runCurrent()
        val pod = repository.schedule("g3", you.uid)

        assertEquals(listOf(you, noah), pod.members)
        assertEquals("g3", podDao.getById("next")?.toPod()?.groupId)
        assertNull(group("g3")!!.proposal)
    }
}

// Lets a test make sending fail, the way a refused or broken write would.
private class FlakyRemote(private val inner: GroupRemoteDataSource) : GroupRemoteDataSource by inner {
    var failSends = false

    override suspend fun sendMessage(message: GroupMessage) {
        if (failSends) throw IOException("No connection")
        inner.sendMessage(message)
    }
}

private class FakeMessageDao : MessageDao {
    val rows = MutableStateFlow<Map<String, MessageEntity>>(emptyMap())

    override fun observeForGroup(groupId: String): Flow<List<MessageEntity>> =
        rows.map { all -> all.values.filter { it.groupId == groupId }.sortedWith(compareBy({ it.sentAt }, { it.id })) }

    override suspend fun getById(id: String): MessageEntity? = rows.value[id]

    override suspend fun upsertAll(messages: List<MessageEntity>) {
        rows.update { it + messages.associateBy { message -> message.id } }
    }

    override suspend fun setStatus(id: String, status: MessageStatus) {
        rows.update { all -> all[id]?.let { all + (id to it.copy(status = status)) } ?: all }
    }

    override suspend fun deleteForGroup(groupId: String) {
        rows.update { all -> all.filterValues { it.groupId != groupId } }
    }
}

private class FakeGroupDao(private val messages: FakeMessageDao) : GroupDao() {
    private val rows = MutableStateFlow<Map<String, GroupEntity>>(emptyMap())

    override fun observeAll(): Flow<List<GroupEntity>> =
        rows.map { all -> all.values.sortedBy { it.name.lowercase() } }

    override fun observeById(id: String): Flow<GroupEntity?> = rows.map { it[id] }

    override suspend fun getById(id: String): GroupEntity? = rows.value[id]

    override suspend fun upsertAll(groups: List<GroupEntity>) {
        rows.update { it + groups.associateBy { group -> group.id } }
    }

    override suspend fun deleteById(id: String) {
        rows.update { it - id }
    }

    override suspend fun deleteMessagesOf(id: String) = messages.deleteForGroup(id)

    override suspend fun deleteAllExcept(keep: List<String>) {
        rows.update { all -> all.filterKeys { it in keep } }
    }

    override suspend fun deleteMessagesOutside(keep: List<String>) {
        messages.rows.update { all -> all.filterValues { it.groupId in keep } }
    }

    override suspend fun deleteAllGroups() {
        rows.value = emptyMap()
    }

    override suspend fun deleteAllMessages() {
        messages.rows.value = emptyMap()
    }
}

private class GroupTestPodDao : PodDao {
    private val rows = MutableStateFlow<Map<String, PodEntity>>(emptyMap())

    override fun observeAll(): Flow<List<PodEntity>> = rows.map { pods -> pods.values.sortedBy { it.startsAt } }

    override suspend fun getById(id: String): PodEntity? = rows.value[id]

    override fun observeById(id: String): Flow<PodEntity?> = rows.map { it[id] }

    override suspend fun upsertAll(pods: List<PodEntity>) {
        rows.update { current -> current + pods.associateBy { it.id } }
    }
}
