package com.reconvene.data.repository

import com.reconvene.data.local.PodDao
import com.reconvene.data.local.toEntity
import com.reconvene.data.local.toPod
import com.reconvene.data.model.Pod
import com.reconvene.data.model.PodActionException
import com.reconvene.data.model.PodActionProblem
import com.reconvene.data.model.PodMember
import com.reconvene.data.remote.PodRemoteDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock

class OfflineFirstPodRepository(
    private val podDao: PodDao,
    private val remote: PodRemoteDataSource,
    private val clock: Clock,
) : PodRepository {

    override fun observePods(): Flow<List<Pod>> =
        podDao.observeAll().map { pods -> pods.map { it.toPod() } }

    override suspend fun refresh() {
        podDao.upsertAll(remote.fetchPods().map { it.toEntity() })
    }

    // Room first, so the pod shows up at once and survives a restart even if the remote is unreachable.
    override suspend fun createPod(pod: Pod) {
        podDao.upsertAll(listOf(pod.toEntity()))
        remote.createPod(pod)
    }

    // Unlike create, joining waits for the server before Room changes, because only the server
    // knows whether the last spot is still free.
    override suspend fun joinPod(podId: String, member: PodMember) {
        val pod = cachedPod(podId)
        if (pod.hasMember(member.uid)) return
        pod.joinProblem(clock.instant())?.let { throw PodActionException(it) }
        save { remote.joinPod(pod, member) }
    }

    override suspend fun leavePod(podId: String, uid: String) {
        val pod = cachedPod(podId)
        if (pod.hostUid == uid) throw PodActionException(PodActionProblem.HOST_CANNOT_LEAVE)
        if (!pod.hasMember(uid)) return
        save { remote.leavePod(pod, uid) }
    }

    override suspend fun cancelPod(podId: String, uid: String) {
        val pod = cachedPod(podId)
        pod.cancelProblem(uid, clock.instant())?.let { throw PodActionException(it) }
        if (pod.canceled) return
        save { remote.cancelPod(pod, uid) }
    }

    private suspend fun cachedPod(podId: String): Pod =
        podDao.getById(podId)?.toPod() ?: throw PodActionException(PodActionProblem.NOT_FOUND)

    // Saves what the server answered, including its copy when it refused, so the card shows why.
    private suspend fun save(change: suspend () -> Pod) {
        val saved = try {
            change()
        } catch (e: PodActionException) {
            e.latest?.let { podDao.upsertAll(listOf(it.toEntity())) }
            throw e
        }
        podDao.upsertAll(listOf(saved.toEntity()))
    }
}
