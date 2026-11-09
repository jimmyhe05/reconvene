package com.reconvene.data.repository

import com.reconvene.data.local.PodDao
import com.reconvene.data.local.PodEntity
import com.reconvene.data.local.toEntity
import com.reconvene.data.local.toPod
import com.reconvene.data.model.Pod
import com.reconvene.data.model.PodActionException
import com.reconvene.data.model.PodActionProblem
import com.reconvene.data.model.PodMember
import com.reconvene.data.remote.PodRemoteDataSource
import com.reconvene.testing.TEST_HOST
import com.reconvene.testing.testPod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class OfflineFirstPodRepositoryTest {

    private val noon = Instant.parse("2026-11-10T17:00:00Z")
    private val podDao = FakePodDao()
    private val clock = Clock.fixed(noon.minusSeconds(3600), ZoneOffset.UTC)
    private val terrier = PodMember(uid = "terrier", name = "Terrier")

    private fun repository(remote: PodRemoteDataSource) = OfflineFirstPodRepository(podDao, remote, clock)

    private suspend fun cachedPod(id: String) = podDao.getById(id)!!.toPod()

    @Test
    fun refreshSavesRemotePodsForObservers() = runTest {
        val remotePods = listOf(testPod(id = "a1", startsAt = noon))
        val repository = repository(TestPodRemoteDataSource { remotePods })

        repository.refresh()

        assertEquals(remotePods, repository.observePods().first())
    }

    @Test
    fun failedRefreshKeepsCachedPods() = runTest {
        podDao.upsertAll(listOf(testPod(id = "cached", startsAt = noon).toEntity()))
        val repository = repository(TestPodRemoteDataSource { throw IOException("Network unavailable") })

        val result = runCatching { repository.refresh() }

        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals(listOf("cached"), repository.observePods().first().map { it.id })
    }

    @Test
    fun createdPodShowsUpForObserversAndSurvivesRefresh() = runTest {
        val remotePods = listOf(testPod(id = "remote", startsAt = noon))
        val repository = repository(TestPodRemoteDataSource { remotePods })

        repository.createPod(testPod(id = "mine", startsAt = noon.plusSeconds(3600)))
        repository.refresh()

        assertEquals(listOf("remote", "mine"), repository.observePods().first().map { it.id })
    }

    @Test
    fun createdPodIsSavedLocallyAndSentToRemote() = runTest {
        val remote = TestPodRemoteDataSource { emptyList() }
        val repository = repository(remote)
        val pod = testPod(id = "mine", startsAt = noon)

        repository.createPod(pod)

        assertEquals(listOf(pod), repository.observePods().first())
        assertEquals(listOf(pod), remote.created)
    }

    @Test
    fun joiningSavesThePodTheServerReturns() = runTest {
        val remote = TestPodRemoteDataSource { emptyList() }
        podDao.upsertAll(listOf(testPod(id = "a1", startsAt = noon).toEntity()))

        repository(remote).joinPod("a1", terrier)

        assertEquals(listOf(TEST_HOST, terrier), cachedPod("a1").members)
        assertEquals(1, remote.joins)
    }

    @Test
    fun joiningAFullPodFailsWithoutAskingTheServer() = runTest {
        val remote = TestPodRemoteDataSource { emptyList() }
        podDao.upsertAll(listOf(testPod(id = "a1", startsAt = noon, capacity = 2, members = 2).toEntity()))

        val error = runCatching { repository(remote).joinPod("a1", terrier) }.exceptionOrNull()

        assertEquals(PodActionProblem.FULL, (error as PodActionException).problem)
        assertEquals(0, remote.joins)
    }

    @Test
    fun serverRefusalSavesTheServersCopy() = runTest {
        val filledUp = testPod(id = "a1", startsAt = noon, capacity = 2, members = 2)
        val remote = TestPodRemoteDataSource { emptyList() }
        remote.refuseJoin = PodActionException(PodActionProblem.FULL, latest = filledUp)
        podDao.upsertAll(listOf(testPod(id = "a1", startsAt = noon, capacity = 2).toEntity()))

        val error = runCatching { repository(remote).joinPod("a1", terrier) }.exceptionOrNull()

        assertEquals(PodActionProblem.FULL, (error as PodActionException).problem)
        assertEquals(filledUp, cachedPod("a1"))
    }

    @Test
    fun offlineJoinLeavesThePodAsItWas() = runTest {
        val pod = testPod(id = "a1", startsAt = noon)
        val remote = TestPodRemoteDataSource { emptyList() }
        remote.refuseJoin = IOException("Network unavailable")
        podDao.upsertAll(listOf(pod.toEntity()))

        val error = runCatching { repository(remote).joinPod("a1", terrier) }.exceptionOrNull()

        assertTrue(error is IOException)
        assertEquals(pod, cachedPod("a1"))
    }

    @Test
    fun leavingRemovesTheMemberButTheHostCannotLeave() = runTest {
        val remote = TestPodRemoteDataSource { emptyList() }
        podDao.upsertAll(listOf(testPod(id = "a1", startsAt = noon).withMember(terrier).toEntity()))
        val repository = repository(remote)

        repository.leavePod("a1", terrier.uid)
        assertEquals(listOf(TEST_HOST), cachedPod("a1").members)

        val error = runCatching { repository.leavePod("a1", TEST_HOST.uid) }.exceptionOrNull()
        assertEquals(PodActionProblem.HOST_CANNOT_LEAVE, (error as PodActionException).problem)
    }
}

private class TestPodRemoteDataSource(private val fetch: () -> List<Pod>) : PodRemoteDataSource {
    val created = mutableListOf<Pod>()
    var joins = 0
    var refuseJoin: Exception? = null

    override suspend fun fetchPods(): List<Pod> = fetch()

    override suspend fun createPod(pod: Pod) {
        created += pod
    }

    override suspend fun joinPod(pod: Pod, member: PodMember): Pod {
        joins++
        refuseJoin?.let { throw it }
        return pod.withMember(member)
    }

    override suspend fun leavePod(pod: Pod, uid: String): Pod = pod.withoutMember(uid)
}

private class FakePodDao : PodDao {
    private val rows = MutableStateFlow<Map<String, PodEntity>>(emptyMap())

    override fun observeAll(): Flow<List<PodEntity>> =
        rows.map { pods -> pods.values.sortedBy { it.startsAt } }

    override suspend fun getById(id: String): PodEntity? = rows.value[id]

    override suspend fun upsertAll(pods: List<PodEntity>) {
        rows.update { current -> current + pods.associateBy { it.id } }
    }
}
