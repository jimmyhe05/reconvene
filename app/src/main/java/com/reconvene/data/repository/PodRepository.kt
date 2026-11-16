package com.reconvene.data.repository

import com.reconvene.data.model.Pod
import com.reconvene.data.model.PodMember
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface PodRepository {
    fun observePods(): Flow<List<Pod>>

    // The pod with this id, or null when it isn't stored. Canceled pods still come through, so a
    // screen showing one can say what happened.
    fun observePod(podId: String): Flow<Pod?> =
        observePods().map { pods -> pods.find { it.id == podId } }

    suspend fun refresh()

    suspend fun createPod(pod: Pod)

    // These throw PodActionException when the pod can't be joined or left, and other exceptions
    // (for example offline) when the server couldn't be reached.
    suspend fun joinPod(podId: String, member: PodMember)

    suspend fun leavePod(podId: String, uid: String)

    // Only the host can cancel. A canceled pod disappears from Discover.
    suspend fun cancelPod(podId: String, uid: String)
}
