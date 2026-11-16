package com.reconvene.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class GroupDao {
    @Query("SELECT * FROM groups ORDER BY name COLLATE NOCASE")
    abstract fun observeAll(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups WHERE id = :id")
    abstract fun observeById(id: String): Flow<GroupEntity?>

    @Query("SELECT * FROM groups WHERE id = :id")
    abstract suspend fun getById(id: String): GroupEntity?

    @Upsert
    abstract suspend fun upsertAll(groups: List<GroupEntity>)

    @Query("DELETE FROM groups WHERE id = :id")
    protected abstract suspend fun deleteById(id: String)

    @Query("DELETE FROM messages WHERE groupId = :id")
    protected abstract suspend fun deleteMessagesOf(id: String)

    // For a group the user is no longer in, or one that was disbanded.
    @Transaction
    open suspend fun deleteWithMessages(id: String) {
        deleteMessagesOf(id)
        deleteById(id)
    }

    @Query("DELETE FROM groups WHERE id NOT IN (:keep)")
    protected abstract suspend fun deleteAllExcept(keep: List<String>)

    @Query("DELETE FROM messages WHERE groupId NOT IN (:keep)")
    protected abstract suspend fun deleteMessagesOutside(keep: List<String>)

    @Query("DELETE FROM groups")
    protected abstract suspend fun deleteAllGroups()

    @Query("DELETE FROM messages")
    protected abstract suspend fun deleteAllMessages()

    // Makes the cache match the server's list of the user's groups. A group the user left, was
    // removed from or that was disbanded goes, and its chat goes with it.
    @Transaction
    open suspend fun replaceAll(groups: List<GroupEntity>) {
        val keep = groups.map { it.id }
        deleteAllExcept(keep)
        deleteMessagesOutside(keep)
        upsertAll(groups)
    }

    // On sign-out, so the next person on the device can't read this user's chats.
    @Transaction
    open suspend fun clear() {
        deleteAllMessages()
        deleteAllGroups()
    }
}
