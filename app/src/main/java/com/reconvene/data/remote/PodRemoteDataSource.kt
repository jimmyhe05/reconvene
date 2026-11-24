package com.reconvene.data.remote

import com.reconvene.data.model.Pod
import com.reconvene.data.model.PodMember

interface PodRemoteDataSource {
    // Pods that haven't ended, for Discover.
    suspend fun fetchPods(): List<Pod>

    // Every pod the user is in, ended ones too, for their record on Profile.
    suspend fun fetchMyPods(uid: String): List<Pod> = fetchPods().filter { it.hasMember(uid) }

    suspend fun createPod(pod: Pod)

    suspend fun joinPod(pod: Pod, member: PodMember): Pod

    suspend fun leavePod(pod: Pod, uid: String): Pod

    suspend fun cancelPod(pod: Pod, uid: String): Pod
}
