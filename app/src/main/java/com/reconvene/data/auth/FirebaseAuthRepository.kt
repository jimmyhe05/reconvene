package com.reconvene.data.auth

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

class FirebaseAuthRepository(private val auth: FirebaseAuth) : AuthRepository {

    override val isAvailable = true

    override val currentUser: Flow<SignedInUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.toSignedInUser()) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    override suspend fun signInWithGoogleIdToken(idToken: String): SignedInUser {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val user = try {
            auth.signInWithCredential(credential).await().user
        } catch (e: Exception) {
            Log.w(TAG, "Firebase sign-in failed", e)
            throw e
        }
        val signedIn = user?.toSignedInUser()
        if (signedIn == null) {
            Log.w(TAG, "Rejected a non-BU account")
            auth.signOut()
            throw NotBuAccountException()
        }
        return signedIn
    }

    override suspend fun signOut() {
        auth.signOut()
    }

    // Null for anyone who isn't a verified BU user, so a stray session never counts as signed in.
    private fun FirebaseUser.toSignedInUser(): SignedInUser? {
        val email = email
        if (!isAllowedBuEmail(email, isEmailVerified) || email == null) return null
        return SignedInUser(uid = uid, email = email, displayName = displayName)
    }

    private companion object {
        const val TAG = "FirebaseAuth"
    }
}
