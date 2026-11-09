package com.reconvene.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PodDao {
    @Query("SELECT * FROM pods ORDER BY startsAt")
    fun observeAll(): Flow<List<PodEntity>>

    @Query("SELECT * FROM pods WHERE id = :id")
    suspend fun getById(id: String): PodEntity?

    @Upsert
    suspend fun upsertAll(pods: List<PodEntity>)
}
