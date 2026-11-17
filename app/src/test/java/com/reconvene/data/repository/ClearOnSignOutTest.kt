package com.reconvene.data.repository

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ClearOnSignOutTest {

    @Test
    fun clearsOnSignOutAndOnAnotherAccountButNotOnFirstSignIn() = runTest {
        var clears = 0

        clearWhenUserChanges(flowOf(null, "ana", "ana", null, "ben", "cal")) { clears++ }

        // ana signing out, then ben being replaced by cal.
        assertEquals(2, clears)
    }

    @Test
    fun theSameUserComingBackAfterARestartKeepsTheCache() = runTest {
        var clears = 0

        clearWhenUserChanges(flowOf("ana", "ana")) { clears++ }

        assertEquals(0, clears)
    }
}
