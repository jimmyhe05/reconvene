package com.reconvene.data.model

import com.reconvene.testing.testPod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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

    @Test
    fun joiningClosesWhenFullCanceledOrTenMinutesBeforeTheEnd() {
        assertNull(pod.joinProblem(start.minusSeconds(60)))
        assertNull(pod.joinProblem(start.plus(Duration.ofMinutes(49))))
        assertEquals(PodActionProblem.CLOSED, pod.joinProblem(start.plus(Duration.ofMinutes(50))))
        assertEquals(PodActionProblem.CLOSED, pod.copy(canceled = true).joinProblem(start))
        assertEquals(PodActionProblem.FULL, testPod(startsAt = start, capacity = 2, members = 2).joinProblem(start))
    }

    @Test
    fun onlyTheHostCanCancelAndOnlyBeforeTheEnd() {
        assertNull(pod.cancelProblem("host", start.plus(Duration.ofMinutes(59))))
        assertEquals(PodActionProblem.CLOSED, pod.cancelProblem("host", start.plus(Duration.ofMinutes(60))))
        assertEquals(PodActionProblem.NOT_HOST, pod.cancelProblem("member-1", start))
        assertNull(pod.copy(canceled = true).cancelProblem("host", start))
    }

    @Test
    fun addingAMemberTwiceKeepsOneCopy() {
        val alex = PodMember(uid = "alex", name = "Alex")
        val joined = pod.withMember(alex)

        assertEquals(listOf("Host", "Alex"), joined.memberNames)
        assertSame(joined, joined.withMember(alex))
        assertEquals(pod, joined.withoutMember("alex"))
    }
}
