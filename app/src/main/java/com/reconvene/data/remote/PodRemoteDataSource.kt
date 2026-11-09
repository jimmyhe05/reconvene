package com.reconvene.data.remote

import com.reconvene.data.model.Pod
import com.reconvene.data.model.PodMember

interface PodRemoteDataSource {
    suspend fun fetchPods(): List<Pod>

    // Hands a new pod to the remote. Returns once the write is queued; delivery can happen later.
    suspend fun createPod(pod: Pod)

    // Joining and leaving wait for the server, which checks them against its own copy of the pod
    // (someone else may have taken the last spot). They return the pod as saved, or throw
    // PodActionException when the server's copy doesn't allow the change.
    suspend fun joinPod(pod: Pod, member: PodMember): Pod

    suspend fun leavePod(pod: Pod, uid: String): Pod
}
