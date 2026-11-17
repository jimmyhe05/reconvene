package com.reconvene.data.remote

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Transaction
import com.reconvene.data.model.Group
import com.reconvene.data.model.GroupActionException
import com.reconvene.data.model.GroupMessage
import com.reconvene.data.model.GroupProblem
import com.reconvene.data.model.MessageReport
import com.reconvene.data.model.Pod
import com.reconvene.data.model.PodMember
import com.reconvene.data.model.SessionProposal
import com.reconvene.data.model.keepProblem
import com.reconvene.data.model.toNewGroup
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import java.time.Clock

// Groups in Firestore. Every change is a transaction that reads the server's copy, checks it with the
// same rules as the app (Group.kt), and writes only the fields that change. firebase/firestore.rules
// checks the same things again. Transactions need the network, so changes fail offline instead of
// queueing; the cached groups stay readable.
class FirestoreGroupRemoteDataSource(
    private val firestore: FirebaseFirestore,
    private val clock: Clock,
) : GroupRemoteDataSource {

    private val groups get() = firestore.collection(GROUPS_COLLECTION)
    private val pods get() = firestore.collection(PODS_COLLECTION)

    // Live while collected. Offline it answers from Firestore's own cache and catches up later. A
    // group the user left or declined drops out of the results, so the repository drops it too.
    override fun observeGroups(uid: String): Flow<List<Group>> = callbackFlow {
        val registration = groups.whereArrayContains(GroupFields.ACCESS, uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val found = snapshot?.documents.orEmpty().mapNotNull { document ->
                document.data?.let { groupFromFirestore(document.id, it) }
                    ?: null.also { Log.w(TAG, "Skipped malformed group ${document.id}") }
            }
            trySend(found)
        }
        awaitClose { registration.remove() }
    }

    // Reads the pod on the server too, so the group invites whoever is in it now, and fails if the
    // pod already has a group (the group's id is the pod's).
    override suspend fun createGroup(group: Group): Group = transaction {
        val podDocument = pods.document(group.fromPodId)
        val groupDocument = groups.document(group.fromPodId)
        val pod = get(podDocument).data?.let { podFromFirestore(podDocument.id, it) }
            ?: throw GroupActionException(GroupProblem.NOT_FOUND)
        if (get(groupDocument).exists()) throw GroupActionException(GroupProblem.ALREADY_KEPT)
        pod.keepProblem(group.hostUid, group.name, clock.instant())?.let { throw GroupActionException(it) }
        val saved = pod.toNewGroup(group.name)
        set(groupDocument, saved.toNewFirestoreGroup())
        saved
    }

    override suspend fun answerInvite(group: Group, uid: String, accept: Boolean): Group =
        change(group.id, { it.respondProblem(uid, clock.instant()) }) { it.withInviteAnswered(uid, accept) }

    override suspend fun leaveGroup(group: Group, uid: String): Group =
        change(group.id, { it.leaveProblem(uid, clock.instant()) }) { it.withoutMember(uid) }

    override suspend fun offerHandover(group: Group, uid: String, to: PodMember): Group =
        change(group.id, { it.handoverProblem(uid, to.uid, clock.instant()) }) { latest ->
            // The name as the group has it, which is what the rules compare against.
            latest.withHandoverOffered(latest.members.first { it.uid == to.uid })
        }

    override suspend fun withdrawHandover(group: Group, uid: String): Group =
        change(group.id, { it.withdrawHandoverProblem(uid, clock.instant()) }) { it.withoutHandover() }

    override suspend fun acceptHandover(group: Group, uid: String): Group =
        change(group.id, { it.acceptHandoverProblem(uid, clock.instant()) }) { it.withHandoverAccepted() }

    // Soft: the document stays, marked disbanded with the server's time. Disbanding twice does nothing.
    override suspend fun disbandGroup(group: Group, uid: String): Group = transaction {
        val document = groups.document(group.id)
        val latest = readGroup(this, group.id)
        latest.disbandProblem(uid, clock.instant())?.let { throw GroupActionException(it, latest) }
        if (latest.disbanded) return@transaction latest
        update(document, mapOf(GroupFields.DISBANDED to true, GroupFields.DISBANDED_AT to FieldValue.serverTimestamp()))
        latest.copy(disbanded = true)
    }

    // Chat, proposals, reports and blocking reach Firestore in later changes. No screen uses them yet.
    override fun observeMessages(groupId: String): Flow<List<GroupMessage>> = flow { notYet("Group chat") }

    override fun observeBlocked(uid: String): Flow<Set<String>> = flowOf(emptySet())

    override suspend fun propose(group: Group, proposal: SessionProposal): Group = notYet("Proposals")

    override suspend fun vote(group: Group, member: PodMember, canCome: Boolean): Group = notYet("Voting")

    override suspend fun schedule(group: Group, uid: String): Pair<Group, Pod> = notYet("Scheduling")

    override suspend fun sendMessage(message: GroupMessage) = notYet("Group chat")

    override suspend fun report(report: MessageReport) = notYet("Reporting")

    override suspend fun block(uid: String, blockedUid: String) = notYet("Blocking")

    override suspend fun unblock(uid: String, blockedUid: String) = notYet("Blocking")

    private fun notYet(what: String): Nothing = throw UnsupportedOperationException("$what isn't on Firestore yet")

    private suspend fun change(
        groupId: String,
        problem: (Group) -> GroupProblem?,
        apply: (Group) -> Group,
    ): Group = transaction {
        val latest = readGroup(this, groupId)
        problem(latest)?.let { throw GroupActionException(it, latest) }
        val saved = apply(latest)
        val changed = changedFields(latest, saved)
        if (changed.isNotEmpty()) update(groups.document(groupId), changed)
        saved
    }

    private fun readGroup(transaction: Transaction, groupId: String): Group {
        val snapshot = transaction.get(groups.document(groupId))
        return snapshot.data?.let { groupFromFirestore(groupId, it) }
            ?: throw GroupActionException(GroupProblem.NOT_FOUND)
    }

    // Firestore may wrap an exception thrown inside the transaction, so find ours again.
    private suspend fun <T> transaction(block: Transaction.() -> T): T {
        try {
            return firestore.runTransaction { it.block() }.await()
        } catch (e: Exception) {
            throw generateSequence<Throwable>(e) { it.cause }.filterIsInstance<GroupActionException>().firstOrNull() ?: e
        }
    }

    private companion object {
        const val TAG = "FirestoreGroups"
    }
}
