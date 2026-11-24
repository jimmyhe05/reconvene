package com.reconvene.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reconvene.R
import com.reconvene.data.auth.SignedInUser
import com.reconvene.data.settings.Appearance
import com.reconvene.ui.discover.PodCard
import com.reconvene.ui.theme.ReconveneTheme
import com.reconvene.ui.theme.TimeStyles
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    onOpenPod: (podId: String) -> Unit,
    onOpenBlocked: () -> Unit,
    onOpenSafety: () -> Unit,
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
        actions = ProfileActions(
            onOpenPod = onOpenPod,
            onOpenBlocked = onOpenBlocked,
            onOpenSafety = onOpenSafety,
            onAppearanceChange = viewModel::setAppearance,
        ),
    )
}

class ProfileActions(
    val onOpenPod: (podId: String) -> Unit = {},
    val onOpenBlocked: () -> Unit = {},
    val onOpenSafety: () -> Unit = {},
    val onAppearanceChange: (Appearance) -> Unit = {},
)

@Composable
fun ProfileContent(
    uiState: ProfileUiState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    actions: ProfileActions = ProfileActions(),
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.nav_profile),
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier
                .padding(bottom = 18.dp)
                .semantics { heading() },
        )
        val user = uiState.user
        if (user != null) {
            SignedIn(user, uiState, onSignOut, actions)
        } else {
            SignedOut(uiState, onSignIn)
        }
    }
}

@Composable
private fun SignedIn(user: SignedInUser, uiState: ProfileUiState, onSignOut: () -> Unit, actions: ProfileActions) {
    var choosingAppearance by rememberSaveable { mutableStateOf(false) }
    Identity(user)
    Record(uiState.record)
    uiState.nextPod?.let { pod ->
        SectionLabel(R.string.profile_next_up)
        PodCard(pod = pod, now = uiState.now, onOpen = { actions.onOpenPod(pod.id) })
    }
    SectionLabel(R.string.profile_preferences)
    SettingRow(
        label = stringResource(R.string.profile_appearance),
        value = stringResource(uiState.appearance.labelRes),
        onClick = { choosingAppearance = true },
    )
    SectionLabel(R.string.profile_safety)
    SettingRow(
        label = stringResource(R.string.blocked_title),
        value = uiState.blockedCount.takeIf { it > 0 }?.toString(),
        onClick = actions.onOpenBlocked,
    )
    SettingRow(label = stringResource(R.string.safety_title), value = null, onClick = actions.onOpenSafety)
    OutlinedButton(
        onClick = onSignOut,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, ReconveneTheme.colors.ink),
        modifier = Modifier
            .padding(top = 24.dp)
            .heightIn(min = 48.dp),
    ) {
        Text(stringResource(R.string.profile_sign_out))
    }
    if (choosingAppearance) {
        AppearanceDialog(
            current = uiState.appearance,
            onChoose = {
                actions.onAppearanceChange(it)
                choosingAppearance = false
            },
            onDismiss = { choosingAppearance = false },
        )
    }
}

// Initials in an ink square, then name, email and the BU check.
@Composable
private fun Identity(user: SignedInUser) {
    val colors = ReconveneTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(colors.ink, MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initials(user),
                style = MaterialTheme.typography.titleLarge,
                color = colors.canvas,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            user.displayName?.let { Text(it, style = MaterialTheme.typography.titleLarge) }
            Text(user.email, style = MaterialTheme.typography.bodyMedium, color = colors.inkMuted)
            // Shown only for a Google account Firebase reports as verified and @bu.edu.
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = colors.inkMuted,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = stringResource(R.string.profile_bu_verified).uppercase(LocalConfiguration.current.locales[0]),
                    style = TimeStyles.meta,
                    color = colors.inkMuted,
                )
            }
        }
    }
}

// Three numbers between hairlines: pods, hours, groups this semester.
@Composable
private fun Record(record: SemesterRecord) {
    val colors = ReconveneTheme.colors
    val hours = record.studied.hoursText()
    val summary = stringResource(R.string.profile_record_summary, record.pods, hours, record.groups)
    Column(Modifier.padding(top = 20.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clearAndSetSemantics { contentDescription = summary },
        ) {
            Stat(record.pods.toString(), R.string.profile_stat_pods, Modifier.weight(1f))
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Stat(hours, R.string.profile_stat_hours, Modifier.weight(1f).padding(start = 14.dp))
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Stat(record.groups.toString(), R.string.profile_stat_groups, Modifier.weight(1f).padding(start = 14.dp))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (record.hosted > 0) {
            Text(
                text = pluralStringResource(R.plurals.profile_hosted, record.hosted, record.hosted),
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkMuted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun Stat(value: String, label: Int, modifier: Modifier) {
    Column(modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = TimeStyles.start.copy(fontSize = MaterialTheme.typography.headlineSmall.fontSize))
        Text(
            text = stringResource(label).uppercase(LocalConfiguration.current.locales[0]),
            style = TimeStyles.meta,
            color = ReconveneTheme.colors.inkMuted,
        )
    }
}

@Composable
private fun SectionLabel(text: Int) {
    Text(
        text = stringResource(text).uppercase(LocalConfiguration.current.locales[0]),
        style = MaterialTheme.typography.labelSmall,
        color = ReconveneTheme.colors.inkMuted,
        modifier = Modifier
            .padding(top = 22.dp, bottom = 2.dp)
            .semantics { heading() },
    )
}

// A label, its current value and a chevron; the whole row is the button.
@Composable
private fun SettingRow(label: String, value: String?, onClick: () -> Unit) {
    val colors = ReconveneTheme.colors
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            value?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.inkMuted) }
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = colors.inkMuted,
                modifier = Modifier.size(18.dp),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun AppearanceDialog(current: Appearance, onChoose: (Appearance) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_appearance)) },
        text = {
            Column(Modifier.selectableGroup()) {
                Appearance.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = option == current, role = Role.RadioButton) { onChoose(option) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == current, onClick = null)
                        Text(
                            text = stringResource(option.labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private val Appearance.labelRes: Int
    get() = when (this) {
        Appearance.SYSTEM -> R.string.appearance_system
        Appearance.LIGHT -> R.string.appearance_light
        Appearance.DARK -> R.string.appearance_dark
    }

// First letters of the first two words of the name, or of the email when there's no name.
private fun initials(user: SignedInUser): String {
    val words = user.displayName?.split(" ")?.filter { it.isNotBlank() }.orEmpty()
    val letters = if (words.isNotEmpty()) words.take(2).map { it.first() } else listOf(user.email.first())
    return letters.joinToString("").uppercase()
}

@Composable
private fun SignedOut(uiState: ProfileUiState, onSignIn: () -> Unit) {
    Text(stringResource(R.string.profile_sign_in_intro), style = MaterialTheme.typography.titleMedium)
    if (!uiState.isAvailable) {
        Text(
            text = stringResource(R.string.profile_sign_in_unavailable),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
        return
    }
    Button(
        onClick = onSignIn,
        enabled = !uiState.isSigningIn,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .padding(top = 20.dp)
            .fillMaxWidth()
            .heightIn(min = 48.dp),
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
        modifier = Modifier.padding(top = 10.dp),
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
            modifier = Modifier
                .padding(top = 10.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    SectionLabel(R.string.profile_why_bu)
    NumberedLine(1, R.string.profile_why_verified)
    NumberedLine(2, R.string.safety_public)
    NumberedLine(3, R.string.safety_name_only)
}
