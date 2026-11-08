package com.reconvene.data.remote

import com.reconvene.data.model.Pod

interface PodRemoteDataSource {
    suspend fun fetchPods(): List<Pod>

    // Hands a new pod to the remote. Returns once the write is queued; delivery can happen later.
    suspend fun createPod(pod: Pod)
}
