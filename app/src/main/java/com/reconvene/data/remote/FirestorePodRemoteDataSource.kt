package com.reconvene.data.remote

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.reconvene.data.model.Pod
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

    private companion object {
        const val TAG = "FirestorePods"
    }
}
