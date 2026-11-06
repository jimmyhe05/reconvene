package com.reconvene.data.repository

import com.reconvene.data.local.PodDao
import com.reconvene.data.local.toEntity
import com.reconvene.data.local.toPod
import com.reconvene.data.model.Pod
import com.reconvene.data.remote.PodRemoteDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class OfflineFirstPodRepository(
    private val podDao: PodDao,
    private val remote: PodRemoteDataSource,
) : PodRepository {

    override fun observePods(): Flow<List<Pod>> =
        podDao.observeAll().map { pods -> pods.map { it.toPod() } }

    override suspend fun refresh() {
        podDao.upsertAll(remote.fetchPods().map { it.toEntity() })
    }
}
