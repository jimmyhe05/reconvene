package com.reconvene

import android.content.Context
import androidx.room.Room
import com.reconvene.data.local.ReconveneDatabase
import com.reconvene.data.remote.FakePodRemoteDataSource
import com.reconvene.data.repository.OfflineFirstPodRepository
import com.reconvene.data.repository.PodRepository
import java.time.Clock

class AppContainer(context: Context) {
    val clock: Clock = Clock.systemDefaultZone()
    val currentUserName = "You"

    private val database = Room.databaseBuilder(
        context,
        ReconveneDatabase::class.java,
        "reconvene.db",
    ).build()

    val podRepository: PodRepository = OfflineFirstPodRepository(
        podDao = database.podDao(),
        remote = FakePodRemoteDataSource(clock),
    )
}
