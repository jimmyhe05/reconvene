package com.reconvene.ui.pod

import com.reconvene.data.model.Pod
import java.time.Instant

sealed interface PodDetailUiState {
    data object Loading : PodDetailUiState

    // The pod isn't stored and the refresh couldn't reach the server.
    data object Error : PodDetailUiState

    // The pod was canceled, or the server no longer has it. canceledByYou is true when the viewer
    // is the host, which is how they get here right after canceling.
    data class Gone(val canceledByYou: Boolean = false) : PodDetailUiState

    // viewerUid is null when nobody is signed in, which hides the Join, Leave and Cancel buttons.
    data class Content(
        val pod: Pod,
        val now: Instant,
        val viewerUid: String?,
        val isBusy: Boolean = false,
        val keep: KeepOption = KeepOption.HIDDEN,
    ) : PodDetailUiState
}

// Whether the host can keep this pod as a group, and whether that's being sent.
enum class KeepOption { HIDDEN, AVAILABLE, KEEPING }
