package com.reconvene.data.reminders

import com.reconvene.data.model.PodMember
import com.reconvene.data.settings.ReminderLead
import com.reconvene.testing.testPod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PodRemindersTest {

    private val now = Instant.parse("2026-11-24T20:00:00Z")
    private val me = PodMember("me", "Me")

    @Test
    fun remindsBeforePodsTheUserIsGoingTo() {
        val soon = testPod(id = "soon", startsAt = now.plusSeconds(3600)).withMember(me)
        val later = testPod(id = "later", startsAt = now.plusSeconds(5 * 3600)).withMember(me)

        val reminders = remindersFor(listOf(later, soon), me.uid, ReminderLead.MINUTES_15, now)

        assertEquals(listOf("soon", "later"), reminders.map { it.podId })
        assertEquals(soon.startsAt.minusSeconds(15 * 60), reminders.first().remindAt)
    }

    @Test
    fun skipsPodsNotTheirsCanceledOrTooCloseToRemind() {
        val notMine = testPod(id = "a", startsAt = now.plusSeconds(3600))
        val canceled = testPod(id = "b", startsAt = now.plusSeconds(3600)).withMember(me).copy(canceled = true)
        val inTenMinutes = testPod(id = "c", startsAt = now.plusSeconds(600)).withMember(me)
        val running = testPod(id = "d", startsAt = now.minusSeconds(600)).withMember(me)

        assertTrue(remindersFor(listOf(notMine, canceled, inTenMinutes, running), me.uid, ReminderLead.MINUTES_15, now).isEmpty())
    }

    @Test
    fun noneWhenOffOrSignedOut() {
        val pod = testPod(id = "a", startsAt = now.plusSeconds(3600)).withMember(me)

        assertTrue(remindersFor(listOf(pod), me.uid, ReminderLead.OFF, now).isEmpty())
        assertTrue(remindersFor(listOf(pod), null, ReminderLead.HOUR_1, now).isEmpty())
    }
}
