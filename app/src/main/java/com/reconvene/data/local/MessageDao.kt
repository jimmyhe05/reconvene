package com.reconvene.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.reconvene.data.model.MessageStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    // Oldest first, the way a chat reads. The id breaks ties between messages sent the same millisecond.
    @Query("SELECT * FROM messages WHERE groupId = :groupId ORDER BY sentAt, id")
    fun observeForGroup(groupId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: String): MessageEntity?

    @Upsert
    suspend fun upsertAll(messages: List<MessageEntity>)

    @Query("UPDATE messages SET status = :status WHERE id = :id")
    suspend fun setStatus(id: String, status: MessageStatus)

    @Query("DELETE FROM messages WHERE groupId = :groupId")
    suspend fun deleteForGroup(groupId: String)
}
