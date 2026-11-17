package com.reconvene.data.model

import com.reconvene.testing.TEST_HOST
import com.reconvene.testing.testPod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class GroupTest {

    private val now = Instant.parse("2026-11-16T15:00:00Z")
    private val ana = PodMember("ana", "Ana")
    private val ben = PodMember("ben", "Ben")
    private val cal = PodMember("cal", "Cal")

    private val group = Group(
        id = "g",
        name = "Algorithms study group",
        course = "CS 501",
        fromPodId = "pod",
        hostUid = TEST_HOST.uid,
        members = listOf(TEST_HOST, ana, ben),
        invited = listOf(cal),
    )

    private fun proposal(by: String = ana.uid) = SessionProposal(
        id = "next",
        startsAt = now.plus(Duration.ofDays(1)),
        endsAt = now.plus(Duration.ofDays(1)).plus(Duration.ofMinutes(90)),
        building = "Mugar Library",
        meetingPoint = "2nd floor",
        mode = PodMode.QUIET,
        byUid = by,
    )

    @Test
    fun onlyThePodsHostCanKeepItAndEveryoneElseIsInvited() {
        val pod = testPod(startsAt = now, members = 3)

        assertEquals(GroupProblem.NOT_HOST, pod.keepProblem("member-1", "Study group", now))
        assertEquals(GroupProblem.BAD_NAME, pod.keepProblem(TEST_HOST.uid, "  A ", now))
        assertNull(pod.keepProblem(TEST_HOST.uid, "Study group", now))

        val kept = pod.toNewGroup(name = "  Study group ")
        assertEquals("Study group", kept.name)
        assertEquals(pod.id, kept.id)
        assertEquals(pod.id, kept.fromPodId)
        assertEquals(listOf(TEST_HOST), kept.members)
        assertEquals(listOf("member-1", "member-2"), kept.invited.map { it.uid })
        assertEquals(3, kept.size)
    }

    @Test
    fun aPodIsKeptOnlyWhileItsRunningAndWithSomeoneToInvite() {
        val running = testPod(startsAt = now.minus(Duration.ofMinutes(10)), members = 2)

        assertNull(running.keepProblem(TEST_HOST.uid, "Study group", now))
        assertEquals(GroupProblem.NOT_RUNNING, running.keepProblem(TEST_HOST.uid, "Study group", now.minus(Duration.ofMinutes(11))))
        assertEquals(GroupProblem.NOT_RUNNING, running.keepProblem(TEST_HOST.uid, "Study group", running.endsAt))
        assertEquals(
            GroupProblem.NOT_RUNNING,
            running.copy(canceled = true).keepProblem(TEST_HOST.uid, "Study group", now),
        )
        assertEquals(
            GroupProblem.NOBODY_TO_INVITE,
            testPod(startsAt = now, members = 1).keepProblem(TEST_HOST.uid, "Study group", now),
        )
        val afterSemester = testPod(startsAt = SEMESTER_END, members = 2)
        assertEquals(GroupProblem.ARCHIVED, afterSemester.keepProblem(TEST_HOST.uid, "Study group", SEMESTER_END))
    }

    @Test
    fun aFullFiftyPersonPodCanBeKept() {
        val pod = testPod(startsAt = now, capacity = GROUP_SIZE_MAX, members = GROUP_SIZE_MAX)

        assertNull(pod.keepProblem(TEST_HOST.uid, "Big review", now))
        assertEquals(GROUP_SIZE_MAX, pod.toNewGroup("Big review").size)
    }

    @Test
    fun anInviteIsAcceptedOrDeclinedOnlyByThePersonInvited() {
        assertEquals(GroupProblem.NOT_INVITED, group.respondProblem(ana.uid, now))
        assertNull(group.respondProblem(cal.uid, now))

        val accepted = group.withInviteAnswered(cal.uid, accept = true)
        assertTrue(accepted.isMember(cal.uid))
        assertFalse(accepted.isInvited(cal.uid))

        val declined = group.withInviteAnswered(cal.uid, accept = false)
        assertFalse(declined.isMember(cal.uid))
        assertEquals(3, declined.size)
    }

    @Test
    fun theHostCantLeaveWithoutHandingOver() {
        assertEquals(GroupProblem.HOST_MUST_HAND_OVER, group.leaveProblem(TEST_HOST.uid, now))
        assertEquals(GroupProblem.NOT_MEMBER, group.leaveProblem(cal.uid, now))
        assertNull(group.leaveProblem(ana.uid, now))
    }

    @Test
    fun handoverGoesToAMemberAndTheOldHostLeavesWhenItsAccepted() {
        assertEquals(GroupProblem.NOT_HOST, group.handoverProblem(ana.uid, ben.uid, now))
        assertEquals(GroupProblem.NOT_MEMBER, group.handoverProblem(TEST_HOST.uid, cal.uid, now))
        assertEquals(GroupProblem.NOT_MEMBER, group.handoverProblem(TEST_HOST.uid, TEST_HOST.uid, now))
        assertNull(group.handoverProblem(TEST_HOST.uid, ben.uid, now))

        val offered = group.withHandoverOffered(ben)
        assertEquals(GroupProblem.NO_HANDOVER, offered.acceptHandoverProblem(ana.uid, now))
        assertNull(offered.acceptHandoverProblem(ben.uid, now))

        val handedOver = offered.withHandoverAccepted()
        assertEquals(ben.uid, handedOver.hostUid)
        assertEquals(listOf(ben, ana), handedOver.members)
        assertNull(handedOver.hostHandover)
    }

    @Test
    fun theHostOrTheChosenMemberCanCallOffAHandover() {
        val offered = group.withHandoverOffered(ben)

        assertEquals(GroupProblem.NO_HANDOVER, group.withdrawHandoverProblem(TEST_HOST.uid, now))
        assertEquals(GroupProblem.NOT_HOST, offered.withdrawHandoverProblem(ana.uid, now))
        assertNull(offered.withdrawHandoverProblem(TEST_HOST.uid, now))
        assertNull(offered.withdrawHandoverProblem(ben.uid, now))
    }

    @Test
    fun onlyTheHostCanDisbandAndThenNothingElseChanges() {
        assertEquals(GroupProblem.NOT_HOST, group.disbandProblem(ana.uid, now))
        assertNull(group.disbandProblem(TEST_HOST.uid, now))

        val disbanded = group.copy(disbanded = true)
        assertNull(disbanded.disbandProblem(TEST_HOST.uid, now))
        assertEquals(GroupProblem.DISBANDED, disbanded.messageProblem(ana.uid, now))
        assertEquals(GroupProblem.DISBANDED, disbanded.leaveProblem(ana.uid, now))
        assertTrue(disbanded.isReadOnly(now))
    }

    @Test
    fun afterTheSemesterTheGroupIsReadOnly() {
        assertFalse(group.isReadOnly(now))
        assertEquals(GroupProblem.ARCHIVED, group.messageProblem(ana.uid, SEMESTER_END))
        assertEquals(GroupProblem.ARCHIVED, group.respondProblem(cal.uid, SEMESTER_END))
    }

    @Test
    fun aVoteCanChangeAndLeavingTakesItBack() {
        val voted = group.withProposal(proposal()).withVote(ana, canCome = true)
        assertEquals(true, voted.proposal?.voteOf(ana.uid))

        val changed = voted.withVote(ana, canCome = false)
        assertEquals(false, changed.proposal?.voteOf(ana.uid))
        assertTrue(changed.proposal!!.yes.isEmpty())

        val left = voted.withoutMember(ana.uid)
        assertNull(left.proposal?.voteOf(ana.uid))
    }

    @Test
    fun whenTheChosenMemberLeavesTheHandoverIsCalledOff() {
        val offered = group.withHandoverOffered(ben)

        assertNull(offered.withoutMember(ben.uid).hostHandover)
        assertEquals(ben, offered.withoutMember(ana.uid).hostHandover)
    }

    @Test
    fun proposalsFollowTheSameTimeLimitsAsPods() {
        val tooLong = proposal().copy(endsAt = proposal().startsAt.plus(Duration.ofHours(5)))
        val tooFar = proposal().copy(
            startsAt = now.plus(Duration.ofDays(8)),
            endsAt = now.plus(Duration.ofDays(8)).plus(Duration.ofHours(1)),
        )

        assertNull(group.proposeProblem(ana.uid, proposal(), now))
        assertEquals(GroupProblem.BAD_PROPOSAL, group.proposeProblem(ana.uid, tooLong, now))
        assertEquals(GroupProblem.BAD_PROPOSAL, group.proposeProblem(ana.uid, tooFar, now))
        assertEquals(GroupProblem.NOT_MEMBER, group.proposeProblem(cal.uid, proposal(), now))
    }

    @Test
    fun theHostSchedulesOnceTwoPeopleCanComeCountingTheHost() {
        val proposed = group.withProposal(proposal())
        assertEquals(GroupProblem.NO_PROPOSAL, group.scheduleProblem(TEST_HOST.uid, now))
        assertEquals(GroupProblem.NOT_ENOUGH_YES, proposed.scheduleProblem(TEST_HOST.uid, now))

        val oneYes = proposed.withVote(ana, canCome = true)
        assertEquals(GroupProblem.NOT_HOST, oneYes.scheduleProblem(ana.uid, now))
        assertNull(oneYes.scheduleProblem(TEST_HOST.uid, now))

        val pod = oneYes.scheduledPod()
        assertEquals("next", pod.id)
        assertEquals("g", pod.groupId)
        assertEquals(TEST_HOST.uid, pod.hostUid)
        assertEquals(listOf(TEST_HOST, ana), pod.members)
        assertEquals(group.members.size, pod.capacity)
        assertEquals(group.name, pod.title)
    }
}
