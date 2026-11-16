package com.reconvene.ui.pod

import androidx.annotation.StringRes
import com.reconvene.R

// A one-off message after a join, leave or cancel: CANCELED confirms a cancel, the rest say why
// something didn't go through.
enum class PodMessage(@StringRes val textRes: Int) {
    CANCELED(R.string.pod_canceled),
    FULL(R.string.pod_error_full),
    CLOSED(R.string.pod_error_closed),
    GONE(R.string.pod_error_gone),
    FAILED(R.string.pod_error_failed),
}
