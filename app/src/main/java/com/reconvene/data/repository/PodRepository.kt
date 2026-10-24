package com.reconvene.data.repository

import com.reconvene.data.model.Pod
import kotlinx.coroutines.flow.Flow

interface PodRepository {
    fun observePods(): Flow<List<Pod>>
}
