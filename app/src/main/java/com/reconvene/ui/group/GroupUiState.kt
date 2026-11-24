package com.reconvene.ui.group

import androidx.annotation.StringRes
import com.reconvene.R
import com.reconvene.data.model.Group
import com.reconvene.data.model.GroupMessage
import com.reconvene.data.model.GroupProblem
import com.reconvene.data.model.SCHEDULE_YES_MIN
import com.reconvene.data.model.SessionProposal
import java.time.Instant

sealed interface GroupUiState {
    data object Loading : GroupUiState

    // Not stored any more, or the viewer isn't a member: they left, it was disbanded, or they signed out.
    data object Gone : GroupUiState

    data object Error : GroupUiState

    // messages are oldest first, without anyone the viewer blocked. chatSyncFailed means the stored
    // chat shows but new messages couldn't be loaded.
    data class Content(
        val group: Group,
        val messages: List<GroupMessage>,
        val viewerUid: String,
        val now: Instant,
        val chatSyncFailed: Boolean = false,
        val isBusy: Boolean = false,
    ) : GroupUiState {
        val isHost: Boolean
            get() = group.hostUid == viewerUid

        val isReadOnly: Boolean
            get() = group.isReadOnly(now)

        // A proposal whose time has passed can't be voted on or scheduled, so it isn't shown as current.
        val proposal: SessionProposal?
            get() = group.proposal?.takeIf { it.isValid(now) }

        val myVote: Boolean?
            get() = proposal?.voteOf(viewerUid)

        // Counted the way scheduling counts: the host is always going.
        val yesCount: Int
            get() = proposal?.let { p -> p.goingWith(group.members.first { it.uid == group.hostUid }).size } ?: 0

        val canSchedule: Boolean
            get() = isHost && !isReadOnly && yesCount >= SCHEDULE_YES_MIN
    }
}

// A one-off message for the snackbar: the first four confirm, the rest say why something didn't happen.
enum class GroupNotice(@StringRes val textRes: Int) {
    REPORTED(R.string.group_reported),
    BLOCKED(R.string.group_blocked),
    PROPOSED(R.string.group_proposed),
    SCHEDULED(R.string.group_scheduled),
    GONE(R.string.group_error_gone),
    READ_ONLY(R.string.group_error_read_only),
    NOT_HOST(R.string.group_error_not_host),
    NOT_MEMBER(R.string.group_error_not_member),
    NO_PROPOSAL(R.string.group_error_no_proposal),
    BAD_PROPOSAL(R.string.group_error_bad_proposal),
    NOT_ENOUGH_YES(R.string.group_error_not_enough_yes),
    BAD_MESSAGE(R.string.group_error_bad_message),
    FAILED(R.string.pod_error_failed),
}

fun GroupProblem.toNotice(): GroupNotice = when (this) {
    GroupProblem.NOT_FOUND, GroupProblem.DISBANDED -> GroupNotice.GONE
    GroupProblem.ARCHIVED -> GroupNotice.READ_ONLY
    GroupProblem.NOT_HOST -> GroupNotice.NOT_HOST
    GroupProblem.NOT_MEMBER -> GroupNotice.NOT_MEMBER
    GroupProblem.NO_PROPOSAL -> GroupNotice.NO_PROPOSAL
    GroupProblem.BAD_PROPOSAL -> GroupNotice.BAD_PROPOSAL
    GroupProblem.NOT_ENOUGH_YES -> GroupNotice.NOT_ENOUGH_YES
    GroupProblem.BAD_MESSAGE -> GroupNotice.BAD_MESSAGE
    // Invites, handovers and keeping a pod don't happen on this screen.
    else -> GroupNotice.FAILED
}
