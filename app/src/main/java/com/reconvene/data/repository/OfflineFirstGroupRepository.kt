package com.reconvene.data.repository

import com.reconvene.data.local.GroupDao
import com.reconvene.data.local.MessageDao
import com.reconvene.data.local.PodDao
import com.reconvene.data.local.toEntity
import com.reconvene.data.local.toGroup
import com.reconvene.data.local.toMessage
import com.reconvene.data.local.toPod
import com.reconvene.data.model.Group
import com.reconvene.data.model.GroupActionException
import com.reconvene.data.model.GroupMessage
import com.reconvene.data.model.GroupProblem
import com.reconvene.data.model.MESSAGE_MAX
import com.reconvene.data.model.MessageReport
import com.reconvene.data.model.MessageStatus
import com.reconvene.data.model.Pod
import com.reconvene.data.model.PodMember
import com.reconvene.data.model.ReportReason
import com.reconvene.data.model.SessionProposal
import com.reconvene.data.model.keepProblem
import com.reconvene.data.model.toNewGroup
import com.reconvene.data.remote.GroupRemoteDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.util.UUID

class OfflineFirstGroupRepository(
    private val groupDao: GroupDao,
    private val messageDao: MessageDao,
    private val podDao: PodDao,
    private val remote: GroupRemoteDataSource,
    private val clock: Clock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : GroupRepository {

    override fun observeGroups(): Flow<List<Group>> =
        groupDao.observeAll().map { groups -> groups.map { it.toGroup() } }

    override fun observeGroup(groupId: String): Flow<Group?> =
        groupDao.observeById(groupId).map { it?.toGroup() }

    override fun observeMessages(groupId: String): Flow<List<GroupMessage>> =
        messageDao.observeForGroup(groupId).map { messages -> messages.map { it.toMessage() } }

    override fun observeBlocked(uid: String): Flow<Set<String>> = remote.observeBlocked(uid)

    // Disbanded groups come from the server so the cache can drop them.
    override suspend fun syncGroups(uid: String, onSynced: () -> Unit) {
        remote.observeGroups(uid).collect { groups ->
            groupDao.replaceAll(groups.filterNot { it.disbanded }.map { it.toEntity() })
            onSynced()
        }
    }

    // Upserts only, so the user's failed messages, which the server never got, stay until retried.
    override suspend fun syncMessages(groupId: String) {
        remote.observeMessages(groupId).collect { messages ->
            messageDao.upsertAll(messages.map { it.toEntity() })
        }
    }

    override suspend fun keepGroup(podId: String, uid: String, name: String): String {
        val pod = podDao.getById(podId)?.toPod() ?: throw GroupActionException(GroupProblem.NOT_FOUND)
        pod.keepProblem(uid, name)?.let { throw GroupActionException(it) }
        val group = pod.toNewGroup(id = newId(), name = name)
        remote.createGroup(group)
        groupDao.upsertAll(listOf(group.toEntity()))
        return group.id
    }

    override suspend fun answerInvite(groupId: String, uid: String, accept: Boolean) =
        change(groupId, uid, { it.respondProblem(uid, now()) }) { remote.answerInvite(it, uid, accept) }

    override suspend fun leaveGroup(groupId: String, uid: String) =
        change(groupId, uid, { it.leaveProblem(uid, now()) }) { remote.leaveGroup(it, uid) }

    override suspend fun offerHandover(groupId: String, uid: String, toUid: String) =
        change(groupId, uid, { it.handoverProblem(uid, toUid, now()) }) { group ->
            remote.offerHandover(group, uid, group.members.first { it.uid == toUid })
        }

    override suspend fun withdrawHandover(groupId: String, uid: String) =
        change(groupId, uid, { it.withdrawHandoverProblem(uid, now()) }) { remote.withdrawHandover(it, uid) }

    override suspend fun acceptHandover(groupId: String, uid: String) =
        change(groupId, uid, { it.acceptHandoverProblem(uid, now()) }) { remote.acceptHandover(it, uid) }

    override suspend fun disbandGroup(groupId: String, uid: String) =
        change(groupId, uid, { it.disbandProblem(uid, now()) }) { remote.disbandGroup(it, uid) }

    override suspend fun propose(groupId: String, proposal: SessionProposal) {
        val uid = proposal.byUid
        change(groupId, uid, { it.proposeProblem(uid, proposal, now()) }) { group ->
            val proposer = group.members.first { it.uid == uid }
            remote.propose(group, proposal.copy(yes = emptyList(), no = emptyList()).withVote(proposer, canCome = true))
        }
    }

    override suspend fun vote(groupId: String, member: PodMember, canCome: Boolean) =
        change(groupId, member.uid, { it.voteProblem(member.uid, now()) }) { remote.vote(it, member, canCome) }

    override suspend fun schedule(groupId: String, uid: String): Pod {
        var pod: Pod? = null
        change(groupId, uid, { it.scheduleProblem(uid, now()) }) { group ->
            val (saved, scheduled) = remote.schedule(group, uid)
            pod = scheduled
            saved
        }
        val scheduled = requireNotNull(pod)
        podDao.upsertAll(listOf(scheduled.toEntity()))
        return scheduled
    }

    override suspend fun sendMessage(groupId: String, author: PodMember, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > MESSAGE_MAX) throw GroupActionException(GroupProblem.BAD_MESSAGE)
        cachedGroup(groupId).messageProblem(author.uid, now())?.let { throw GroupActionException(it) }
        val message = GroupMessage(
            id = newId(),
            groupId = groupId,
            authorUid = author.uid,
            authorName = author.name,
            text = trimmed,
            sentAt = now(),
            status = MessageStatus.SENDING,
        )
        messageDao.upsertAll(listOf(message.toEntity()))
        deliver(message)
    }

    // Sends the same id again, so a message the server got after all isn't doubled.
    override suspend fun retryMessage(messageId: String) {
        val message = messageDao.getById(messageId)?.toMessage() ?: return
        if (message.status != MessageStatus.FAILED) return
        messageDao.setStatus(messageId, MessageStatus.SENDING)
        deliver(message.copy(status = MessageStatus.SENDING))
    }

    // A refused or failed message is marked FAILED so the chat can offer a retry. Cancellation
    // leaves it SENDING: Firestore still has it queued, and the sync marks it sent when it lands.
    private suspend fun deliver(message: GroupMessage) {
        try {
            remote.sendMessage(message)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            messageDao.setStatus(message.id, MessageStatus.FAILED)
            return
        }
        messageDao.setStatus(message.id, MessageStatus.SENT)
    }

    override suspend fun report(groupId: String, messageId: String, reporterUid: String, reason: ReportReason) {
        val message = messageDao.getById(messageId) ?: throw GroupActionException(GroupProblem.NOT_FOUND)
        remote.report(MessageReport(groupId, messageId, reporterUid, message.authorUid, reason))
    }

    override suspend fun block(uid: String, blockedUid: String) = remote.block(uid, blockedUid)

    override suspend fun unblock(uid: String, blockedUid: String) = remote.unblock(uid, blockedUid)

    override suspend fun clear() = groupDao.clear()

    private fun now() = clock.instant()

    private suspend fun cachedGroup(groupId: String): Group =
        groupDao.getById(groupId)?.toGroup() ?: throw GroupActionException(GroupProblem.NOT_FOUND)

    // Checks the cached copy first, so a change that can't work doesn't need the network. Then saves
    // what the server answered, including its copy when it refused, so the screen shows why.
    private suspend fun change(
        groupId: String,
        uid: String,
        problem: (Group) -> GroupProblem?,
        send: suspend (Group) -> Group,
    ) {
        val group = cachedGroup(groupId)
        problem(group)?.let { throw GroupActionException(it) }
        val saved = try {
            send(group)
        } catch (e: GroupActionException) {
            e.latest?.let { store(it, uid) }
            throw e
        }
        store(saved, uid)
    }

    // A group the user isn't in or invited to any more, or one that was disbanded, leaves the
    // cache with its chat.
    private suspend fun store(group: Group, uid: String) {
        if (group.disbanded || (!group.isMember(uid) && !group.isInvited(uid))) {
            groupDao.deleteWithMessages(group.id)
        } else {
            groupDao.upsertAll(listOf(group.toEntity()))
        }
    }
}
