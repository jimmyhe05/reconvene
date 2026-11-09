package com.reconvene.data.remote

import android.util.Log
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.Transaction
import com.reconvene.data.model.Pod
import com.reconvene.data.model.PodActionException
import com.reconvene.data.model.PodActionProblem
import com.reconvene.data.model.PodMember
import kotlinx.coroutines.tasks.await
import java.time.Clock
import java.util.Date

class FirestorePodRemoteDataSource(
    private val firestore: FirebaseFirestore,
    private val clock: Clock,
) : PodRemoteDataSource {

    private val pods get() = firestore.collection(PODS_COLLECTION)

    // Source.SERVER fails when offline instead of quietly answering from Firestore's own cache,
    // so the repository keeps what Room already has and the screen can say the refresh failed.
    override suspend fun fetchPods(): List<Pod> {
        val snapshot = pods
            .whereGreaterThan(PodFields.ENDS_AT, Date.from(clock.instant()))
            .get(Source.SERVER)
            .await()
        return snapshot.documents.mapNotNull { document ->
            document.data?.let { podFromFirestore(document.id, it) }
                ?: null.also { Log.w(TAG, "Skipped malformed pod ${document.id}") }
        }
    }

    // Not awaited: Firestore saves the write on the device and sends it when the network is back,
    // even after a restart. Waiting for the server would leave the create button spinning offline.
    override suspend fun createPod(pod: Pod) {
        pods.document(pod.id)
            .set(pod.toFirestore())
            .addOnFailureListener { e -> Log.w(TAG, "Firestore rejected pod ${pod.id}", e) }
    }

    // A transaction reads the server's copy first, so a pod that filled up or closed since the last
    // refresh is refused here with the latest copy. The security rules check the same limits.
    override suspend fun joinPod(pod: Pod, member: PodMember): Pod = changePod(pod.id) { transaction, document, latest ->
        if (latest.hasMember(member.uid)) return@changePod latest
        latest.joinProblem(clock.instant())?.let { throw PodActionException(it, latest) }
        transaction.update(document, FieldPath.of(PodFields.MEMBERS, member.uid), member.name)
        latest.withMember(member)
    }

    override suspend fun leavePod(pod: Pod, uid: String): Pod = changePod(pod.id) { transaction, document, latest ->
        if (latest.hostUid == uid) throw PodActionException(PodActionProblem.HOST_CANNOT_LEAVE, latest)
        if (!latest.hasMember(uid)) return@changePod latest
        transaction.update(document, FieldPath.of(PodFields.MEMBERS, uid), FieldValue.delete())
        latest.withoutMember(uid)
    }

    // A transaction too, so canceling fails offline like joining does, instead of the button
    // spinning until the network is back.
    override suspend fun cancelPod(pod: Pod, uid: String): Pod = changePod(pod.id) { transaction, document, latest ->
        latest.cancelProblem(uid, clock.instant())?.let { throw PodActionException(it, latest) }
        if (latest.canceled) return@changePod latest
        transaction.update(document, PodFields.CANCELED, true)
        latest.copy(canceled = true)
    }

    // Transactions need the network; offline this fails instead of queueing.
    private suspend fun changePod(
        podId: String,
        change: (Transaction, DocumentReference, Pod) -> Pod,
    ): Pod {
        val document = pods.document(podId)
        try {
            return firestore.runTransaction { transaction ->
                val latest = transaction.get(document).data?.let { podFromFirestore(podId, it) }
                    ?: throw PodActionException(PodActionProblem.NOT_FOUND)
                change(transaction, document, latest)
            }.await()
        } catch (e: Exception) {
            // Firestore may wrap an exception thrown inside the transaction.
            throw generateSequence<Throwable>(e) { it.cause }.filterIsInstance<PodActionException>().firstOrNull() ?: e
        }
    }

    private companion object {
        const val TAG = "FirestorePods"
    }
}
