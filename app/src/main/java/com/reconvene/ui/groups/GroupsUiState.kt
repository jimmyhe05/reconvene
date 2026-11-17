package com.reconvene.ui.groups

import androidx.annotation.StringRes
import com.reconvene.R
import com.reconvene.data.model.Group

sealed interface GroupsUiState {
    data object Loading : GroupsUiState

    data object SignedOut : GroupsUiState

    data object Empty : GroupsUiState

    // Nothing stored and the server couldn't be reached.
    data object Error : GroupsUiState

    // Invites to answer, then the groups the user is in, each sorted by name.
    data class Content(
        val invites: List<Group>,
        val groups: List<Group>,
        val viewerUid: String,
        val busyGroupIds: Set<String> = emptySet(),
    ) : GroupsUiState
}

enum class GroupNotice(@StringRes val textRes: Int) {
    JOINED(R.string.groups_joined),
    DECLINED(R.string.groups_declined),
    INVITE_GONE(R.string.groups_invite_gone),
    ARCHIVED(R.string.groups_archived),
    FAILED(R.string.pod_error_failed),
}
