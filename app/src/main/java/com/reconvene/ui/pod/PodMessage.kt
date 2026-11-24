package com.reconvene.ui.pod

import androidx.annotation.StringRes
import com.reconvene.R

// A one-off message after a create, join, leave, cancel or keep. CREATED, CANCELED and GROUP_KEPT
// confirm, the rest say why something didn't go through. The LOCATION ones explain why Nearest
// first stayed off. actionRes labels a snackbar button for messages the user can act on.
enum class PodMessage(@StringRes val textRes: Int, @StringRes val actionRes: Int? = null) {
    CREATED(R.string.pod_created),
    CANCELED(R.string.pod_canceled),
    GROUP_KEPT(R.string.pod_group_kept),
    FULL(R.string.pod_error_full),
    CLOSED(R.string.pod_error_closed),
    GONE(R.string.pod_error_gone),
    CANT_KEEP(R.string.pod_error_cant_keep),
    FAILED(R.string.pod_error_failed),
    LOCATION_DENIED(R.string.nearby_denied, R.string.nearby_open_settings),
    LOCATION_UNAVAILABLE(R.string.nearby_unavailable),
    NO_MAPS_APP(R.string.pod_detail_no_maps),
}
