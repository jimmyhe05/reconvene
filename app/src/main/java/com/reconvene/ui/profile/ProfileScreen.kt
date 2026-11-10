package com.reconvene.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reconvene.R
import com.reconvene.data.auth.SignedInUser
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    ProfileContent(
        uiState = uiState,
        onSignIn = {
            scope.launch {
                viewModel.signIn { requestBuGoogleIdToken(context, viewModel.googleWebClientId) }
            }
        },
        onSignOut = viewModel::signOut,
        modifier = modifier,
    )
}

@Composable
fun ProfileContent(
    uiState: ProfileUiState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.nav_profile),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier
                .padding(vertical = 16.dp)
                .semantics { heading() },
        )
        val user = uiState.user
        if (user != null) {
            SignedIn(user, onSignOut)
        } else {
            SignedOut(uiState, onSignIn)
        }
    }
}

@Composable
private fun SignedIn(user: SignedInUser, onSignOut: () -> Unit) {
    user.displayName?.let { Text(it, style = MaterialTheme.typography.titleLarge) }
    Text(user.email, style = MaterialTheme.typography.bodyLarge)
    // Shown only for a Google account Firebase reports as verified and @bu.edu.
    Text(stringResource(R.string.profile_bu_verified), style = MaterialTheme.typography.labelLarge)
    OutlinedButton(onClick = onSignOut, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(stringResource(R.string.profile_sign_out))
    }
}

@Composable
private fun SignedOut(uiState: ProfileUiState, onSignIn: () -> Unit) {
    Text(stringResource(R.string.profile_sign_in_intro), style = MaterialTheme.typography.bodyLarge)
    if (!uiState.isAvailable) {
        Text(stringResource(R.string.profile_sign_in_unavailable), style = MaterialTheme.typography.bodyMedium)
        return
    }
    Button(
        onClick = onSignIn,
        enabled = !uiState.isSigningIn,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        if (uiState.isSigningIn) {
            val signingIn = stringResource(R.string.profile_signing_in)
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp).semantics { contentDescription = signingIn },
                strokeWidth = 2.dp,
            )
        } else {
            Text(stringResource(R.string.profile_sign_in))
        }
    }
    // Google's own "add account" screen, which the app can't change, takes "name" as name@gmail.com.
    Text(
        text = stringResource(R.string.profile_sign_in_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val error = when (uiState.error) {
        SignInError.NOT_BU_ACCOUNT -> stringResource(R.string.profile_error_not_bu)
        SignInError.NO_BU_ACCOUNT -> stringResource(R.string.profile_error_no_bu_account)
        SignInError.FAILED -> stringResource(R.string.profile_error_failed)
        null -> null
    }
    if (error != null) {
        Text(
            text = error,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}
