package com.reconvene.data.repository

import com.reconvene.data.model.Pod
import com.reconvene.data.model.PodMember
import kotlinx.coroutines.flow.Flow

interface PodRepository {
    fun observePods(): Flow<List<Pod>>

    suspend fun refresh()

    suspend fun createPod(pod: Pod)

    // Both throw PodActionException when the pod can't be joined or left, and other exceptions
    // (for example offline) when the server couldn't be reached.
    suspend fun joinPod(podId: String, member: PodMember)

    suspend fun leavePod(podId: String, uid: String)
}
