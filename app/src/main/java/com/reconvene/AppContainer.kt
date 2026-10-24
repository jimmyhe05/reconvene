package com.reconvene

import com.reconvene.data.repository.FakePodRepository
import com.reconvene.data.repository.PodRepository
import java.time.Clock

class AppContainer {
    val clock: Clock = Clock.systemDefaultZone()
    val podRepository: PodRepository = FakePodRepository(clock)
}
