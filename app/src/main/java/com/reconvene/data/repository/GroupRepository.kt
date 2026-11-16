package com.reconvene.data.repository

import com.reconvene.data.model.Group
import com.reconvene.data.model.GroupMessage
import com.reconvene.data.model.Pod
import com.reconvene.data.model.PodMember
import com.reconvene.data.model.ReportReason
import com.reconvene.data.model.SessionProposal
import kotlinx.coroutines.flow.Flow

interface GroupRepository {
    // From Room. Groups the user is in or invited to, by name.
    fun observeGroups(): Flow<List<Group>>

    fun observeGroup(groupId: String): Flow<Group?>

    // From Room, oldest first, including the user's own messages still sending or failed.
    fun observeMessages(groupId: String): Flow<List<GroupMessage>>

    // Who this user blocked. Their messages should be hidden.
    fun observeBlocked(uid: String): Flow<Set<String>>

    // These keep Room matching the server until the caller is cancelled, for example by running
    // them in a ViewModel's scope while a screen is open. They throw if the server can't be read.
    suspend fun syncGroups(uid: String)

    suspend fun syncMessages(groupId: String)

    // The changes below wait for the server. They throw GroupActionException when the change isn't
    // allowed, and other exceptions (for example offline) when the server couldn't be reached.

    // Keeps a pod the user hosts as a group and invites everyone else in it. Returns the group's id.
    suspend fun keepGroup(podId: String, uid: String, name: String): String

    suspend fun answerInvite(groupId: String, uid: String, accept: Boolean)

    // The host can't leave; they hand over first, and leave when the new host accepts.
    suspend fun leaveGroup(groupId: String, uid: String)

    suspend fun offerHandover(groupId: String, uid: String, toUid: String)

    // The host takes the offer back, or the member it was made to declines.
    suspend fun withdrawHandover(groupId: String, uid: String)

    suspend fun acceptHandover(groupId: String, uid: String)

    suspend fun disbandGroup(groupId: String, uid: String)

    // Replaces any earlier proposal. The person proposing counts as coming.
    suspend fun propose(groupId: String, proposal: SessionProposal)

    suspend fun vote(groupId: String, member: PodMember, canCome: Boolean)

    // Host only, with enough yes votes. Returns the new pod, which is also saved for Discover.
    suspend fun schedule(groupId: String, uid: String): Pod

    // Saved at once and shown as sending; Firestore keeps trying while offline. Throws only for a
    // message the user can't send (for example empty, or not a member any more).
    suspend fun sendMessage(groupId: String, author: PodMember, text: String)

    suspend fun retryMessage(messageId: String)

    suspend fun report(groupId: String, messageId: String, reporterUid: String, reason: ReportReason)

    suspend fun block(uid: String, blockedUid: String)

    suspend fun unblock(uid: String, blockedUid: String)

    // Drops every cached group and message, for sign-out.
    suspend fun clear()
}
