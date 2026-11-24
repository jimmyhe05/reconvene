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
import com.reconvene.data.location.DeviceLocationRepository
import com.reconvene.data.location.LocationRepository
import com.reconvene.data.remote.FakeGroupRemoteDataSource
import com.reconvene.data.remote.FakePodRemoteDataSource
import com.reconvene.data.remote.FirestoreGroupRemoteDataSource
import com.reconvene.data.remote.FirestorePodRemoteDataSource
import com.reconvene.data.remote.GroupRemoteDataSource
import com.reconvene.data.remote.PodRemoteDataSource
import com.reconvene.data.repository.GroupRepository
import com.reconvene.data.repository.OfflineFirstGroupRepository
import com.reconvene.data.repository.OfflineFirstPodRepository
import com.reconvene.data.repository.PodRepository
import com.reconvene.data.repository.clearWhenUserChanges
import com.reconvene.data.settings.DataStoreSettingsRepository
import com.reconvene.data.settings.SettingsRepository
import com.reconvene.data.settings.settingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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

    val groupRepository: GroupRepository = OfflineFirstGroupRepository(
        groupDao = database.groupDao(),
        messageDao = database.messageDao(),
        podDao = database.podDao(),
        remote = groupRemoteDataSource(),
        clock = clock,
    )

    val locationRepository: LocationRepository = DeviceLocationRepository(context)

    val settingsRepository: SettingsRepository = DataStoreSettingsRepository(context.applicationContext.settingsDataStore)

    val authRepository: AuthRepository =
        firebaseApp?.let { FirebaseAuthRepository(FirebaseAuth.getInstance(it)) } ?: UnavailableAuthRepository

    // Work that lasts as long as the app, not a screen.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        // Groups and chat are private, so they leave the device on sign-out. Pods are open to every BU
        // student and stay cached.
        appScope.launch {
            clearWhenUserChanges(authRepository.currentUser.map { it?.uid }) { groupRepository.clear() }
        }
    }

    // Firestore when Firebase is configured, sample pods otherwise,
    // so a clean checkout and CI build and run without a Firebase project.
    private fun podRemoteDataSource(): PodRemoteDataSource {
        val app = firebaseApp ?: return FakePodRemoteDataSource(clock)
        return FirestorePodRemoteDataSource(FirebaseFirestore.getInstance(app), clock)
    }

    // Firestore when Firebase is configured, sample groups otherwise.
    private fun groupRemoteDataSource(): GroupRemoteDataSource {
        val app = firebaseApp ?: return FakeGroupRemoteDataSource(clock)
        return FirestoreGroupRemoteDataSource(FirebaseFirestore.getInstance(app), clock)
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
