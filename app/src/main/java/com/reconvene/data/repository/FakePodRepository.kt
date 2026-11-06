package com.reconvene.data.repository

import com.reconvene.data.model.Pod
import com.reconvene.data.remote.samplePods
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Clock

class FakePodRepository(private val clock: Clock) : PodRepository {

    override fun observePods(): Flow<List<Pod>> = flow {
        delay(700)
        emit(samplePods(clock))
    }
}
