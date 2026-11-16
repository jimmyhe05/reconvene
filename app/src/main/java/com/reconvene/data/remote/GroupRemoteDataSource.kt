package com.reconvene.data.remote

import com.reconvene.data.model.Group
import com.reconvene.data.model.GroupMessage
import com.reconvene.data.model.MessageReport
import com.reconvene.data.model.Pod
import com.reconvene.data.model.PodMember
import com.reconvene.data.model.SessionProposal
import kotlinx.coroutines.flow.Flow

interface GroupRemoteDataSource {
    // Live lists, emitted again on every change until the collector stops. observeGroups covers
    // groups the user is in or invited to, disbanded ones included so the cache can drop them.
    fun observeGroups(uid: String): Flow<List<Group>>

    // A message the server doesn't have yet comes back as SENDING.
    fun observeMessages(groupId: String): Flow<List<GroupMessage>>

    fun observeBlocked(uid: String): Flow<Set<String>>

    // The changes below wait for the server, which checks them against its own copy of the group.
    // They return the group as saved, or throw GroupActionException when the server's copy
    // doesn't allow the change.
    suspend fun createGroup(group: Group)

    suspend fun answerInvite(group: Group, uid: String, accept: Boolean): Group

    suspend fun leaveGroup(group: Group, uid: String): Group

    suspend fun offerHandover(group: Group, uid: String, to: PodMember): Group

    suspend fun withdrawHandover(group: Group, uid: String): Group

    suspend fun acceptHandover(group: Group, uid: String): Group

    suspend fun disbandGroup(group: Group, uid: String): Group

    suspend fun propose(group: Group, proposal: SessionProposal): Group

    suspend fun vote(group: Group, member: PodMember, canCome: Boolean): Group

    // Creates the pod and clears the proposal together. Returns both as saved.
    suspend fun schedule(group: Group, uid: String): Pair<Group, Pod>

    // Returns once the server has the message. While offline it waits, and the write stays queued
    // even if the caller stops waiting.
    suspend fun sendMessage(message: GroupMessage)

    suspend fun report(report: MessageReport)

    suspend fun block(uid: String, blockedUid: String)

    suspend fun unblock(uid: String, blockedUid: String)
}
