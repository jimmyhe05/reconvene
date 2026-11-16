package com.reconvene.testing

import com.reconvene.data.model.Pod
import com.reconvene.data.model.PodMember
import com.reconvene.data.repository.PodRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.IOException

// A repository whose refresh waits until the test calls finishRefresh or failRefresh, so tests
// can see the screen while it loads.
class TestPodRepository : PodRepository {
    private val cachedPods = MutableStateFlow<List<Pod>>(emptyList())
    private val refreshResults = Channel<Result<List<Pod>>>(Channel.UNLIMITED)

    fun cache(pods: List<Pod>) {
        cachedPods.value = pods
    }

    suspend fun finishRefresh(pods: List<Pod>) = refreshResults.send(Result.success(pods))

    suspend fun failRefresh() = refreshResults.send(Result.failure(IOException("Network unavailable")))

    override fun observePods(): Flow<List<Pod>> = cachedPods

    override suspend fun refresh() {
        cachedPods.value = refreshResults.receive().getOrThrow()
    }

    override suspend fun createPod(pod: Pod) {
        cachedPods.value += pod
    }

    val joins = mutableListOf<Pair<String, PodMember>>()
    var joinAnswer: CompletableDeferred<Unit>? = null
    var joinError: Exception? = null

    override suspend fun joinPod(podId: String, member: PodMember) {
        joinAnswer?.await()
        joinError?.let { throw it }
        joins += podId to member
    }

    override suspend fun leavePod(podId: String, uid: String) = Unit

    var cancelError: Exception? = null

    override suspend fun cancelPod(podId: String, uid: String) {
        cancelError?.let { throw it }
        cachedPods.value = cachedPods.value.map { if (it.id == podId) it.copy(canceled = true) else it }
    }
}
