package com.reconvene.ui.group

import com.reconvene.data.model.PodMode
import com.reconvene.ui.create.CreatePodForm
import com.reconvene.ui.create.FormError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ProposalFormTest {

    private val zone = ZoneId.of("America/New_York")
    private val now = ZonedDateTime.of(2026, 11, 17, 10, 0, 0, 0, zone).toInstant()
    private val form = CreatePodForm(
        building = "  CDS ",
        meetingPoint = "3rd floor\ncommons ",
        day = LocalDate.of(2026, 11, 18),
        startTime = LocalTime.of(16, 0),
        endTime = LocalTime.of(17, 30),
        mode = PodMode.TALK,
    )

    @Test
    fun becomesAValidProposalWithCleanedUpText() {
        val proposal = form.toProposal(id = "p1", byUid = "u1", zone = zone)

        assertEquals("CDS", proposal.building)
        assertEquals("3rd floor commons", proposal.meetingPoint)
        assertEquals(ZonedDateTime.of(2026, 11, 18, 16, 0, 0, 0, zone).toInstant(), proposal.startsAt)
        assertEquals(ZonedDateTime.of(2026, 11, 18, 17, 30, 0, 0, zone).toInstant(), proposal.endsAt)
        assertEquals(PodMode.TALK, proposal.mode)
        assertTrue(proposal.isValid(now))
    }

    @Test
    fun aProposalNeedsNoTopic() {
        val errors = form.proposalErrors(now, zone)

        assertNull(errors.title)
        assertTrue(errors.isEmpty)
    }

    @Test
    fun aProposalStillNeedsAPlace() {
        val errors = form.copy(building = null, meetingPoint = "").proposalErrors(now, zone)

        assertEquals(FormError.BUILDING_MISSING, errors.building)
        assertEquals(FormError.MEETING_POINT_TOO_SHORT, errors.meetingPoint)
    }
}
