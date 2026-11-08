package com.reconvene.data.repository

import com.reconvene.data.local.PodDao
import com.reconvene.data.local.PodEntity
import com.reconvene.data.local.toEntity
import com.reconvene.data.model.Pod
import com.reconvene.data.remote.PodRemoteDataSource
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
import java.time.Instant

class OfflineFirstPodRepositoryTest {

    private val noon = Instant.parse("2026-11-10T17:00:00Z")
    private val podDao = FakePodDao()

    @Test
    fun refreshSavesRemotePodsForObservers() = runTest {
        val remotePods = listOf(testPod(id = "a1", startsAt = noon))
        val repository = OfflineFirstPodRepository(podDao, TestPodRemoteDataSource { remotePods })

        repository.refresh()

        assertEquals(remotePods, repository.observePods().first())
    }

    @Test
    fun failedRefreshKeepsCachedPods() = runTest {
        podDao.upsertAll(listOf(testPod(id = "cached", startsAt = noon).toEntity()))
        val repository = OfflineFirstPodRepository(
            podDao,
            TestPodRemoteDataSource { throw IOException("Network unavailable") },
        )

        val result = runCatching { repository.refresh() }

        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals(listOf("cached"), repository.observePods().first().map { it.id })
    }

    @Test
    fun createdPodShowsUpForObserversAndSurvivesRefresh() = runTest {
        val remotePods = listOf(testPod(id = "remote", startsAt = noon))
        val repository = OfflineFirstPodRepository(podDao, TestPodRemoteDataSource { remotePods })

        repository.createPod(testPod(id = "mine", startsAt = noon.plusSeconds(3600)))
        repository.refresh()

        assertEquals(listOf("remote", "mine"), repository.observePods().first().map { it.id })
    }

    @Test
    fun createdPodIsSavedLocallyAndSentToRemote() = runTest {
        val remote = TestPodRemoteDataSource { emptyList() }
        val repository = OfflineFirstPodRepository(podDao, remote)
        val pod = testPod(id = "mine", startsAt = noon)

        repository.createPod(pod)

        assertEquals(listOf(pod), repository.observePods().first())
        assertEquals(listOf(pod), remote.created)
    }
}

private class TestPodRemoteDataSource(private val fetch: () -> List<Pod>) : PodRemoteDataSource {
    val created = mutableListOf<Pod>()

    override suspend fun fetchPods(): List<Pod> = fetch()

    override suspend fun createPod(pod: Pod) {
        created += pod
    }
}

private class FakePodDao : PodDao {
    private val rows = MutableStateFlow<Map<String, PodEntity>>(emptyMap())

    override fun observeAll(): Flow<List<PodEntity>> =
        rows.map { pods -> pods.values.sortedBy { it.startsAt } }

    override suspend fun upsertAll(pods: List<PodEntity>) {
        rows.update { current -> current + pods.associateBy { it.id } }
    }
}
