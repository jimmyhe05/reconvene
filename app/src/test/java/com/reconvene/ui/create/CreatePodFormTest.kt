package com.reconvene.ui.create

import com.reconvene.data.model.PodMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class CreatePodFormTest {

    private val zone = ZoneId.of("America/New_York")
    private val today = LocalDate.of(2026, 11, 10)
    private fun at(hour: Int, minute: Int = 0) = ZonedDateTime.of(today, LocalTime.of(hour, minute), zone)

    private val validForm = CreatePodForm(
        title = "  Algorithms exam review ",
        course = " ",
        building = "Mugar Library",
        meetingPoint = "2nd floor ",
        day = today,
        startTime = LocalTime.of(15, 0),
        endTime = LocalTime.of(16, 30),
        capacity = 5,
        mode = PodMode.TALK,
    )

    @Test
    fun newFormStartsAtTheNextHalfHourAtLeastFifteenMinutesAway() {
        val onTheHour = newPodForm(at(14, 0))
        assertEquals(LocalTime.of(14, 30) to LocalTime.of(16, 0), onTheHour.startTime to onTheHour.endTime)

        val twenty = newPodForm(at(14, 20))
        assertEquals(LocalTime.of(15, 0) to LocalTime.of(16, 30), twenty.startTime to twenty.endTime)
        assertEquals(today, twenty.day)
    }

    @Test
    fun newFormLateAtNightMovesToTomorrowMorning() {
        val form = newPodForm(at(23, 0))

        assertEquals(today.plusDays(1), form.day)
        assertEquals(LocalTime.of(9, 0) to LocalTime.of(10, 30), form.startTime to form.endTime)
    }

    @Test
    fun blankFormFlagsEveryRequiredField() {
        val errors = newPodForm(at(14, 0)).validate(at(14, 0).toInstant(), zone)

        assertEquals(
            FormErrors(
                title = FormError.TOPIC_TOO_SHORT,
                building = FormError.BUILDING_MISSING,
                meetingPoint = FormError.MEETING_POINT_TOO_SHORT,
            ),
            errors,
        )
    }

    @Test
    fun timeMustBeAfterStartNotOverAndBetweenHalfAnHourAndFourHours() {
        val now = at(14, 0).toInstant()
        fun timeError(start: LocalTime, end: LocalTime) =
            validForm.copy(startTime = start, endTime = end).validate(now, zone).time

        assertEquals(FormError.END_NOT_AFTER_START, timeError(LocalTime.of(15, 0), LocalTime.of(15, 0)))
        assertEquals(FormError.ALREADY_OVER, timeError(LocalTime.of(12, 0), LocalTime.of(13, 0)))
        assertEquals(FormError.TOO_SHORT, timeError(LocalTime.of(15, 0), LocalTime.of(15, 20)))
        assertEquals(FormError.TOO_LONG, timeError(LocalTime.of(15, 0), LocalTime.of(19, 30)))
        assertNull(timeError(LocalTime.of(13, 30), LocalTime.of(15, 0)))
    }

    @Test
    fun validFormBecomesATrimmedPodHostedByTheCreator() {
        assertTrue(validForm.validate(at(14, 0).toInstant(), zone).isEmpty)

        val pod = validForm.toPod(id = "new", hostName = "You", zone = zone)

        assertEquals("Algorithms exam review", pod.title)
        assertNull(pod.course)
        assertEquals("2nd floor", pod.meetingPoint)
        assertEquals(at(15, 0).toInstant(), pod.startsAt)
        assertEquals(at(16, 30).toInstant(), pod.endsAt)
        assertEquals(listOf("You"), pod.memberNames)
    }
}
