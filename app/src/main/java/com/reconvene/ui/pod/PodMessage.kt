package com.reconvene.ui.pod

import androidx.annotation.StringRes
import com.reconvene.R

// A one-off message after a join, leave, cancel or keep: CANCELED and GROUP_KEPT confirm, the rest
// say why something didn't go through.
enum class PodMessage(@StringRes val textRes: Int) {
    CANCELED(R.string.pod_canceled),
    GROUP_KEPT(R.string.pod_group_kept),
    FULL(R.string.pod_error_full),
    CLOSED(R.string.pod_error_closed),
    GONE(R.string.pod_error_gone),
    CANT_KEEP(R.string.pod_error_cant_keep),
    FAILED(R.string.pod_error_failed),
}
