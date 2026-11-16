package com.reconvene.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.reconvene.data.model.GroupMessage
import com.reconvene.data.model.MessageStatus
import java.time.Instant

// Indexed by group, since every read is one group's chat.
@Entity(tableName = "messages", indices = [Index("groupId")])
data class MessageEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val authorUid: String,
    val authorName: String,
    val text: String,
    val sentAt: Instant,
    val status: MessageStatus,
)

fun MessageEntity.toMessage() = GroupMessage(
    id = id,
    groupId = groupId,
    authorUid = authorUid,
    authorName = authorName,
    text = text,
    sentAt = sentAt,
    status = status,
)

fun GroupMessage.toEntity() = MessageEntity(
    id = id,
    groupId = groupId,
    authorUid = authorUid,
    authorName = authorName,
    text = text,
    sentAt = sentAt,
    status = status,
)
