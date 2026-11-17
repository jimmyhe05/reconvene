package com.reconvene.data.remote

import com.reconvene.data.model.Pod
import com.reconvene.data.model.PodMember

interface PodRemoteDataSource {
    suspend fun fetchPods(): List<Pod>

    suspend fun createPod(pod: Pod)

    suspend fun joinPod(pod: Pod, member: PodMember): Pod

    suspend fun leavePod(pod: Pod, uid: String): Pod

    suspend fun cancelPod(pod: Pod, uid: String): Pod
}
