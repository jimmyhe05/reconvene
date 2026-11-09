package com.reconvene.data.auth

import com.reconvene.data.model.MEMBER_NAME_MAX
import com.reconvene.data.model.PodMember
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

data class SignedInUser(
    val uid: String,
    val email: String,
    val displayName: String?,
)

class NotBuAccountException : Exception("Only verified @bu.edu Google accounts can sign in")

interface AuthRepository {
    // False when the build has no Firebase settings, so there is nothing to sign in to.
    val isAvailable: Boolean

    // The signed-in BU user, or null. Emits again on sign-in and sign-out.
    val currentUser: Flow<SignedInUser?>

    // Signs in with a Google ID token. Throws NotBuAccountException, after signing back out,
    // if the account isn't a verified @bu.edu address.
    suspend fun signInWithGoogleIdToken(idToken: String): SignedInUser

    suspend fun signOut()
}

const val BU_EMAIL_DOMAIN = "bu.edu"

// The account picker only offers bu.edu accounts, but that is a convenience, not a check.
// This runs on the token Firebase verified. Firestore rules repeat it on the server.
fun isAllowedBuEmail(email: String?, emailVerified: Boolean): Boolean =
    emailVerified && email != null && email.lowercase().endsWith("@$BU_EMAIL_DOMAIN")

// Used when the build has no Firebase settings (CI, a clean checkout).
object UnavailableAuthRepository : AuthRepository {
    override val isAvailable = false
    override val currentUser: Flow<SignedInUser?> = flowOf(null)
    override suspend fun signInWithGoogleIdToken(idToken: String): SignedInUser =
        throw UnsupportedOperationException("Sign-in needs the Firebase settings in local.properties")
    override suspend fun signOut() = Unit
}

// Stands in for a signed-in user when the build has no Firebase settings. Pods then never leave
// the device, so creating and joining sample pods still works for CI and a clean checkout.
val LOCAL_USER = SignedInUser(uid = "local-user", email = "", displayName = "You")

// Who creates and joins pods: the signed-in BU user, LOCAL_USER without Firebase, or null when
// signed out.
fun AuthRepository.actingUser(): Flow<SignedInUser?> = if (isAvailable) currentUser else flowOf(LOCAL_USER)

// How the user appears on a pod: their Google name, or the start of their email without one.
fun SignedInUser.asMember(): PodMember {
    val name = displayName?.trim()?.takeIf { it.isNotEmpty() } ?: email.substringBefore('@')
    return PodMember(uid = uid, name = name.take(MEMBER_NAME_MAX))
}
