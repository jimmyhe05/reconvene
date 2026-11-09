package com.reconvene.testing

import com.reconvene.data.auth.AuthRepository
import com.reconvene.data.auth.SignedInUser
import kotlinx.coroutines.flow.MutableStateFlow

val TERRIER = SignedInUser(uid = "terrier", email = "terrier@bu.edu", displayName = "Terrier")

class TestAuthRepository(user: SignedInUser? = TERRIER) : AuthRepository {
    override val isAvailable = true
    override val currentUser = MutableStateFlow(user)

    override suspend fun signInWithGoogleIdToken(idToken: String): SignedInUser {
        currentUser.value = TERRIER
        return TERRIER
    }

    override suspend fun signOut() {
        currentUser.value = null
    }
}
