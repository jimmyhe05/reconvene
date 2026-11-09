package com.reconvene.ui.profile

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.reconvene.data.auth.BU_EMAIL_DOMAIN

// Shows Google's account picker, limited to bu.edu accounts, and returns the ID token.
// Needs an Activity context because the picker is a UI. Lives in the UI layer for that reason.
suspend fun requestBuGoogleIdToken(context: Context, webClientId: String): String {
    val option = GetSignInWithGoogleOption.Builder(webClientId)
        .setHostedDomainFilter(BU_EMAIL_DOMAIN)
        .build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(option)
        .build()
    val credential = try {
        CredentialManager.create(context).getCredential(context, request).credential
    } catch (e: GetCredentialCancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Google account picker failed: ${e::class.simpleName}: ${e.message}", e)
        throw e
    }
    if (credential is CustomCredential &&
        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
    ) {
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }
    throw IllegalStateException("Unexpected credential type ${credential.type}")
}

private const val TAG = "GoogleSignIn"
