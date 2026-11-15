package com.reconvene.ui.create

import com.reconvene.data.model.PodMember
import com.reconvene.data.model.PodMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
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
    fun startSlotsTodayAreTheNextHalfHours() {
        assertEquals(
            listOf(LocalTime.of(14, 30), LocalTime.of(15, 0), LocalTime.of(15, 30), LocalTime.of(16, 0)),
            startSlots(today, at(14, 0)),
        )
        assertEquals(LocalTime.of(15, 0), startSlots(today, at(14, 20)).first())
    }

    @Test
    fun startSlotsStopAtMidnight() {
        assertEquals(listOf(LocalTime.of(23, 0), LocalTime.of(23, 30)), startSlots(today, at(22, 40)))
        assertTrue(startSlots(today, at(23, 50)).isEmpty())
    }

    @Test
    fun startSlotsOnALaterDayAreRoundTimes() {
        assertEquals(
            listOf(LocalTime.of(10, 0), LocalTime.of(13, 0), LocalTime.of(16, 0), LocalTime.of(19, 0)),
            startSlots(today.plusDays(1), at(14, 0)),
        )
    }

    @Test
    fun quickDaysAreTodayAndTomorrow() {
        assertEquals(listOf(today, today.plusDays(1)), quickDays(today))
    }

    @Test
    fun aPodCanStartFromTodayToTheEndOfTheWindow() {
        assertFalse(canStartOn(today.minusDays(1), today))
        assertTrue(canStartOn(today, today))
        assertTrue(canStartOn(lastStartDay(today), today))
        assertFalse(canStartOn(lastStartDay(today).plusDays(1), today))
    }

    @Test
    fun theLastDayFitsTheServersWindowAtAnyTimeOfDay() {
        val justAfterMidnight = at(0, 0)
        val latestStart = ZonedDateTime.of(lastStartDay(today), LocalTime.of(23, 59), zone)

        assertTrue(latestStart <= justAfterMidnight.plusDays(START_WINDOW_DAYS.toLong()))
    }

    @Test
    fun pickerMillisAreMidnightUtcAndConvertBackToTheSameDay() {
        val day = LocalDate.of(2026, 11, 13)

        assertEquals(Instant.parse("2026-11-13T00:00:00Z").toEpochMilli(), day.toPickerMillis())
        assertEquals(day, pickerMillisToDate(day.toPickerMillis()))
    }

    @Test
    fun lengthSetsTheEndAndMustEndBeforeMidnight() {
        val twoHours = validForm.withLength(Duration.ofHours(2))
        assertEquals(LocalTime.of(17, 0), twoHours.endTime)
        assertEquals(Duration.ofHours(2), twoHours.length)

        val late = validForm.withStartTime(LocalTime.of(21, 0))
        assertTrue(late.canLast(Duration.ofHours(2)))
        assertFalse(late.canLast(Duration.ofHours(3)))
    }

    @Test
    fun everyLengthChoiceIsAllowed() {
        val now = at(9, 0).toInstant()
        LENGTH_CHOICES.forEach { length ->
            assertNull(length.toString(), validForm.withLength(length).validate(now, zone).time)
        }
    }

    @Test
    fun movingTheStartMovesTheEndAndKeepsTheLength() {
        val later = validForm.withStartTime(LocalTime.of(17, 0))
        assertEquals(LocalTime.of(17, 0) to LocalTime.of(18, 30), later.startTime to later.endTime)

        val earlier = validForm.withStartTime(LocalTime.of(9, 15))
        assertEquals(LocalTime.of(9, 15) to LocalTime.of(10, 45), earlier.startTime to earlier.endTime)
    }

    @Test
    fun movingTheStartNearMidnightStopsTheEndAt2359() {
        val late = validForm.withStartTime(LocalTime.of(23, 0))
        assertEquals(LocalTime.of(23, 59), late.endTime)
        assertNull(late.validate(at(14, 0).toInstant(), zone).time)

        val tooLate = validForm.withStartTime(LocalTime.of(23, 45))
        assertEquals(LocalTime.of(23, 59), tooLate.endTime)
        assertEquals(FormError.TOO_SHORT, tooLate.validate(at(14, 0).toInstant(), zone).time)
    }

    @Test
    fun movingTheStartOfOutOfOrderTimesUsesTheDefaultLength() {
        val outOfOrder = validForm.copy(startTime = LocalTime.of(16, 0), endTime = LocalTime.of(15, 0))

        assertEquals(LocalTime.of(14, 30), outOfOrder.withStartTime(LocalTime.of(13, 0)).endTime)
    }

    @Test
    fun validFormBecomesATrimmedPodHostedByTheCreator() {
        assertTrue(validForm.validate(at(14, 0).toInstant(), zone).isEmpty)

        val host = PodMember(uid = "terrier", name = "Terrier")
        val pod = validForm.toPod(id = "new", host = host, zone = zone)

        assertEquals("Algorithms exam review", pod.title)
        assertNull(pod.course)
        assertEquals("2nd floor", pod.meetingPoint)
        assertEquals(at(15, 0).toInstant(), pod.startsAt)
        assertEquals(at(16, 30).toInstant(), pod.endsAt)
        assertEquals("terrier", pod.hostUid)
        assertEquals(listOf(host), pod.members)
    }

    @Test
    fun customBuildingNeedsAName() {
        val now = at(14, 0).toInstant()
        val other = validForm.copy(building = "", customBuilding = true)

        assertEquals(FormError.BUILDING_MISSING, other.validate(now, zone).building)
        assertEquals(FormError.BUILDING_MISSING, other.copy(building = "   ").validate(now, zone).building)
        assertNull(other.copy(building = "College of Fine Arts").validate(now, zone).building)
    }

    @Test
    fun customBuildingIsTrimmedIntoThePod() {
        val form = validForm.copy(building = "  College of\nFine Arts ", customBuilding = true)

        val pod = form.toPod(id = "new", host = PodMember(uid = "terrier", name = "Terrier"), zone = zone)

        assertEquals("College of Fine Arts", pod.building)
    }

    @Test
    fun suggestedMeetingPointsBelongToSuggestedBuildingsAndFitTheLimits() {
        assertEquals(suggestedBuildings.toSet(), suggestedMeetingPoints.keys)
        suggestedMeetingPoints.values.flatten().forEach { spot ->
            assertTrue(spot, spot.length in MEETING_POINT_MIN..MEETING_POINT_MAX)
        }
    }

    @Test
    fun onlySuggestedBuildingsOfferMeetingPoints() {
        assertEquals(suggestedMeetingPoints["CDS"], validForm.withBuilding("CDS").meetingPointSuggestions)
        assertTrue(validForm.withBuilding("CDS", custom = true).meetingPointSuggestions.isEmpty())
        assertTrue(validForm.copy(building = null).meetingPointSuggestions.isEmpty())
    }

    @Test
    fun changingBuildingDropsASuggestedMeetingPointButKeepsTypedText() {
        val suggested = validForm.withBuilding("GSU").copy(meetingPoint = "Food court tables")
        assertEquals("", suggested.withBuilding("CDS").meetingPoint)
        assertEquals("", suggested.withBuilding("", custom = true).meetingPoint)
        assertEquals("Food court tables", suggested.withBuilding("GSU").meetingPoint)

        val typed = validForm.withBuilding("GSU").copy(meetingPoint = "Back corner booth")
        assertEquals("Back corner booth", typed.withBuilding("CDS").meetingPoint)
    }

    @Test
    fun pastedLineBreaksBecomeSpaces() {
        val pasted = validForm.copy(
            title = "Android Dev\n\n// rules comment",
            course = "CS501\r\nE1",
            meetingPoint = "GSU\nFloor 1\n",
        )

        val pod = pasted.toPod(id = "new", host = PodMember(uid = "terrier", name = "Terrier"), zone = zone)

        assertEquals("Android Dev // rules comment", pod.title)
        assertEquals("CS501 E1", pod.course)
        assertEquals("GSU Floor 1", pod.meetingPoint)
        assertEquals("Mugar Library", "Mugar\nLibrary".toSingleLine())
    }
}
