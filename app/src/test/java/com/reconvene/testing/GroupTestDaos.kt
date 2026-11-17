package com.reconvene.testing

import com.reconvene.data.local.GroupDao
import com.reconvene.data.local.GroupEntity
import com.reconvene.data.local.MessageDao
import com.reconvene.data.local.MessageEntity
import com.reconvene.data.local.PodDao
import com.reconvene.data.local.PodEntity
import com.reconvene.data.model.MessageStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

// In-memory stand-ins for Room, so OfflineFirstGroupRepository runs in unit tests.

class FakeMessageDao : MessageDao {
    val rows = MutableStateFlow<Map<String, MessageEntity>>(emptyMap())

    override fun observeForGroup(groupId: String): Flow<List<MessageEntity>> =
        rows.map { all -> all.values.filter { it.groupId == groupId }.sortedWith(compareBy({ it.sentAt }, { it.id })) }

    override suspend fun getById(id: String): MessageEntity? = rows.value[id]

    override suspend fun upsertAll(messages: List<MessageEntity>) {
        rows.update { it + messages.associateBy { message -> message.id } }
    }

    override suspend fun setStatus(id: String, status: MessageStatus) {
        rows.update { all -> all[id]?.let { all + (id to it.copy(status = status)) } ?: all }
    }

    override suspend fun deleteForGroup(groupId: String) {
        rows.update { all -> all.filterValues { it.groupId != groupId } }
    }
}

class FakeGroupDao(private val messages: FakeMessageDao) : GroupDao() {
    private val rows = MutableStateFlow<Map<String, GroupEntity>>(emptyMap())

    override fun observeAll(): Flow<List<GroupEntity>> =
        rows.map { all -> all.values.sortedBy { it.name.lowercase() } }

    override fun observeById(id: String): Flow<GroupEntity?> = rows.map { it[id] }

    override suspend fun getById(id: String): GroupEntity? = rows.value[id]

    override suspend fun upsertAll(groups: List<GroupEntity>) {
        rows.update { it + groups.associateBy { group -> group.id } }
    }

    override suspend fun deleteById(id: String) {
        rows.update { it - id }
    }

    override suspend fun deleteMessagesOf(id: String) = messages.deleteForGroup(id)

    override suspend fun deleteAllExcept(keep: List<String>) {
        rows.update { all -> all.filterKeys { it in keep } }
    }

    override suspend fun deleteMessagesOutside(keep: List<String>) {
        messages.rows.update { all -> all.filterValues { it.groupId in keep } }
    }

    override suspend fun deleteAllGroups() {
        rows.value = emptyMap()
    }

    override suspend fun deleteAllMessages() {
        messages.rows.value = emptyMap()
    }
}

class GroupTestPodDao : PodDao {
    private val rows = MutableStateFlow<Map<String, PodEntity>>(emptyMap())

    override fun observeAll(): Flow<List<PodEntity>> = rows.map { pods -> pods.values.sortedBy { it.startsAt } }

    override suspend fun getById(id: String): PodEntity? = rows.value[id]

    override fun observeById(id: String): Flow<PodEntity?> = rows.map { it[id] }

    override suspend fun upsertAll(pods: List<PodEntity>) {
        rows.update { current -> current + pods.associateBy { it.id } }
    }
}
