package com.reconvene.data.remote

import com.reconvene.data.model.Pod

interface PodRemoteDataSource {
    suspend fun fetchPods(): List<Pod>
}
