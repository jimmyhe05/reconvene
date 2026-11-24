package com.reconvene.data.reminders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ReminderChangesTest {

    private val start = Instant.parse("2026-11-24T22:00:00Z")

    private fun reminder(id: String, startsAt: Instant = start) =
        PodReminder(id, "Pod $id", "Mugar", "2nd floor", startsAt, startsAt.minusSeconds(900))

    @Test
    fun firstTimeClearsEverythingAndSchedulesAll() {
        val changes = reminderChanges(null, listOf(reminder("a"), reminder("b")))

        assertTrue(changes.cancelEverything)
        assertEquals(listOf("a", "b"), changes.schedule.map { it.podId })
    }

    @Test
    fun laterOnlyTouchesWhatChanged() {
        val previous = listOf(reminder("a"), reminder("b"), reminder("c")).associateBy { it.podId }
        val moved = reminder("b", start.plusSeconds(3600))

        val changes = reminderChanges(previous, listOf(reminder("a"), moved, reminder("d")))

        assertEquals(false, changes.cancelEverything)
        assertEquals(setOf("c"), changes.cancel)
        assertEquals(listOf("b", "d"), changes.schedule.map { it.podId })
    }
}
