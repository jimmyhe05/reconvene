package com.reconvene

import android.content.Context
import androidx.room.Room
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.reconvene.data.auth.AuthRepository
import com.reconvene.data.auth.FirebaseAuthRepository
import com.reconvene.data.auth.UnavailableAuthRepository
import com.reconvene.data.local.ReconveneDatabase
import com.reconvene.data.remote.FakePodRemoteDataSource
import com.reconvene.data.remote.FirestorePodRemoteDataSource
import com.reconvene.data.remote.PodRemoteDataSource
import com.reconvene.data.repository.OfflineFirstPodRepository
import com.reconvene.data.repository.PodRepository
import java.time.Clock

class AppContainer(context: Context) {
    val clock: Clock = Clock.systemDefaultZone()
    val googleWebClientId: String = BuildConfig.FIREBASE_WEB_CLIENT_ID

    // Null when local.properties has no Firebase settings (see firebase/README.md).
    private val firebaseApp: FirebaseApp? = firebaseApp(context)

    private val database = Room.databaseBuilder(
        context,
        ReconveneDatabase::class.java,
        "reconvene.db",
    ).fallbackToDestructiveMigration(dropAllTables = true).build()

    val podRepository: PodRepository = OfflineFirstPodRepository(
        podDao = database.podDao(),
        remote = podRemoteDataSource(),
        clock = clock,
    )

    val authRepository: AuthRepository =
        firebaseApp?.let { FirebaseAuthRepository(FirebaseAuth.getInstance(it)) } ?: UnavailableAuthRepository

    // Firestore when Firebase is configured, sample pods otherwise,
    // so a clean checkout and CI build and run without a Firebase project.
    private fun podRemoteDataSource(): PodRemoteDataSource {
        val app = firebaseApp ?: return FakePodRemoteDataSource(clock)
        return FirestorePodRemoteDataSource(FirebaseFirestore.getInstance(app), clock)
    }

    private fun firebaseApp(context: Context): FirebaseApp? {
        if (BuildConfig.FIREBASE_PROJECT_ID.isBlank()) return null
        FirebaseApp.getApps(context).firstOrNull()?.let { return it }
        val options = FirebaseOptions.Builder()
            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
            .setApplicationId(BuildConfig.FIREBASE_APP_ID)
            .setApiKey(BuildConfig.FIREBASE_API_KEY)
            .build()
        return FirebaseApp.initializeApp(context, options)
    }
}
