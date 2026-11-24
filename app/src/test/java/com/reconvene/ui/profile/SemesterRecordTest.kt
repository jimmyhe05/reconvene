package com.reconvene.ui.profile

import com.reconvene.data.model.Group
import com.reconvene.data.model.PodMember
import com.reconvene.testing.testPod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class SemesterRecordTest {

    private val now = Instant.parse("2026-11-24T20:00:00Z")
    private val me = PodMember("me", "Me")
    private val hour = 3600L

    @Test
    fun countsEndedPodsTheUserWasInThisSemester() {
        val hostedByMe = testPod(id = "a", startsAt = now.minusSeconds(48 * hour)).copy(hostUid = me.uid, members = listOf(me))
        val joined = testPod(id = "b", startsAt = now.minusSeconds(24 * hour)).withMember(me)
        val canceled = testPod(id = "c", startsAt = now.minusSeconds(24 * hour)).withMember(me).copy(canceled = true)
        val notMine = testPod(id = "d", startsAt = now.minusSeconds(24 * hour))
        val upcoming = testPod(id = "e", startsAt = now.plusSeconds(hour)).withMember(me)
        val lastTerm = testPod(id = "f", startsAt = Instant.parse("2026-05-01T16:00:00Z")).withMember(me)

        val record = semesterRecord(listOf(hostedByMe, joined, canceled, notMine, upcoming, lastTerm), emptyList(), me.uid, now)

        assertEquals(2, record.pods)
        assertEquals(1, record.hosted)
        assertEquals(
            Duration.between(hostedByMe.startsAt, hostedByMe.endsAt) + Duration.between(joined.startsAt, joined.endsAt),
            record.studied,
        )
    }

    @Test
    fun countsGroupsTheUserIsInButNotInvitesOrDisbanded() {
        val host = PodMember("host", "Host")
        val mine = Group(id = "g1", name = "Mine", course = null, fromPodId = "g1", hostUid = host.uid, members = listOf(host, me))
        val invite = mine.copy(id = "g2", members = listOf(host), invited = listOf(me))
        val gone = mine.copy(id = "g3", disbanded = true)

        assertEquals(1, semesterRecord(emptyList(), listOf(mine, invite, gone), me.uid, now).groups)
    }

    @Test
    fun nextPodIsTheSoonestOneNotOverOrCanceled() {
        val running = testPod(id = "now", startsAt = now.minusSeconds(600)).withMember(me)
        val later = testPod(id = "later", startsAt = now.plusSeconds(hour)).withMember(me)
        val canceled = testPod(id = "x", startsAt = now.plusSeconds(60)).withMember(me).copy(canceled = true)

        assertEquals("now", nextPod(listOf(later, canceled, running), me.uid, now)?.id)
        assertNull(nextPod(listOf(canceled), me.uid, now))
    }

    @Test
    fun hoursShowOneDecimalAndDropZero() {
        assertEquals("9.5", Duration.ofMinutes(570).hoursText())
        assertEquals("2", Duration.ofMinutes(120).hoursText())
        assertEquals("0", Duration.ZERO.hoursText())
        assertEquals("1.3", Duration.ofMinutes(80).hoursText())
    }
}
