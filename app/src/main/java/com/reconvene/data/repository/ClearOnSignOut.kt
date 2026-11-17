package com.reconvene.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

// Calls clear when the signed-in user signs out or another account takes their place, so one
// person's groups and chat never show for the next. Runs until the caller is cancelled.
suspend fun clearWhenUserChanges(uids: Flow<String?>, clear: suspend () -> Unit) {
    var previous: String? = null
    uids.distinctUntilChanged().collect { uid ->
        if (previous != null && uid != previous) clear()
        previous = uid
    }
}
