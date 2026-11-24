package com.reconvene.data.location

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration

class ChooseLocationTest {

    private val mugar = buildingLocations.getValue("Mugar Library")
    private val gsu = buildingLocations.getValue("GSU")

    private fun lastKnown(minutesAgo: Long) = LastKnown(mugar, Duration.ofMinutes(minutesAgo))

    @Test
    fun aRecentLastKnownLocationAnswersWithoutWaiting() = runTest {
        var asked = false

        val point = chooseLocation(lastKnown(minutesAgo = 1)) { asked = true; gsu }

        assertEquals(mugar, point)
        assertFalse(asked)
    }

    @Test
    fun anOlderLastKnownLocationWaitsForAFreshFix() = runTest {
        assertEquals(gsu, chooseLocation(lastKnown(minutesAgo = 10)) { gsu })
    }

    @Test
    fun withNoFreshFixTheLastHalfHourIsCloseEnough() = runTest {
        assertEquals(mugar, chooseLocation(lastKnown(minutesAgo = 20)) { null })
        assertNull(chooseLocation(lastKnown(minutesAgo = 45)) { null })
    }

    @Test
    fun withNothingKnownItWaitsForAFreshFix() = runTest {
        assertEquals(gsu, chooseLocation(null) { gsu })
        assertNull(chooseLocation(null) { null })
    }
}
