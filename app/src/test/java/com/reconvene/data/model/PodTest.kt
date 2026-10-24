package com.reconvene.data.model

import com.reconvene.testing.testPod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class PodTest {

    private val start = Instant.parse("2026-10-23T18:00:00Z")
    private val pod = testPod(startsAt = start, durationMinutes = 60)

    @Test
    fun timingFollowsStartAndEnd() {
        assertEquals(PodTiming.UPCOMING, pod.timing(start.minusSeconds(1)))
        assertEquals(PodTiming.ONGOING, pod.timing(start))
        assertEquals(PodTiming.ENDED, pod.timing(start.plus(Duration.ofMinutes(60))))
    }

    @Test
    fun canceledOverridesTiming() {
        assertEquals(PodTiming.CANCELED, pod.copy(canceled = true).timing(start))
    }

    @Test
    fun spotsLeftNeverGoesNegative() {
        assertEquals(3, testPod(startsAt = start, capacity = 4, members = 1).spotsLeft)

        val overbooked = testPod(startsAt = start, capacity = 2, members = 3)
        assertEquals(0, overbooked.spotsLeft)
        assertTrue(overbooked.isFull)
    }
}
