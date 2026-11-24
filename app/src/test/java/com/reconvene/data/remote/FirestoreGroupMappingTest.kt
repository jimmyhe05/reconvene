package com.reconvene.data.remote

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.reconvene.data.model.Group
import com.reconvene.data.model.GroupMessage
import com.reconvene.data.model.MessageStatus
import com.reconvene.data.model.PodMember
import com.reconvene.data.model.PodMode
import com.reconvene.data.model.SEMESTER_END
import com.reconvene.data.model.SessionProposal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.Date

class FirestoreGroupMappingTest {

    private val maya = PodMember("maya", "Maya")
    private val ana = PodMember("ana", "Ana")
    private val ben = PodMember("ben", "Ben")

    private val group = Group(
        id = "pod-1",
        name = "Algorithms",
        course = "CS 330",
        fromPodId = "pod-1",
        hostUid = maya.uid,
        members = listOf(maya, ben),
        invited = listOf(ana),
        hostHandover = ben,
    )

    @Test
    fun groupSurvivesRoundTrip() {
        assertEquals(group, groupFromFirestore("pod-1", group.toFirestore()))
        assertEquals(group.copy(hostHandover = null, disbanded = true), groupFromFirestore("pod-1", group.copy(hostHandover = null, disbanded = true).toFirestore()))
    }

    @Test
    fun documentHasSortedAccessAndAKeeperOnlyWhenNew() {
        val data = group.toFirestore()

        assertEquals(listOf("ana", "ben", "maya"), data[GroupFields.ACCESS])
        assertEquals(mapOf("maya" to "Maya", "ben" to "Ben"), data[GroupFields.MEMBERS])
        assertEquals(mapOf("uid" to "ben", "name" to "Ben"), data[GroupFields.HANDOVER])
        assertEquals(Date.from(SEMESTER_END), data[GroupFields.ARCHIVES_AT])
        assertNull(data[GroupFields.KEPT_BY_UID])
        assertEquals("maya", group.toNewFirestoreGroup()[GroupFields.KEPT_BY_UID])
    }

    @Test
    fun acceptingAnInviteChangesOnlyMembersAndInvited() {
        val accepted = group.withInviteAnswered(ana.uid, accept = true)

        // access stays the same list, so the security rules see only these two keys change
        assertEquals(setOf(GroupFields.MEMBERS, GroupFields.INVITED), changedFields(group, accepted).keys)
    }

    @Test
    fun acceptingAHandoverChangesHostMembersAccessAndHandover() {
        val handedOver = group.withHandoverAccepted()

        assertEquals(
            setOf(GroupFields.HOST_UID, GroupFields.MEMBERS, GroupFields.ACCESS, GroupFields.HANDOVER),
            changedFields(group, handedOver).keys,
        )
        assertEquals(listOf("ana", "ben"), changedFields(group, handedOver)[GroupFields.ACCESS])
    }

    @Test
    fun missingOrMistypedFieldGivesNull() {
        val data = group.toFirestore()

        assertNull(groupFromFirestore("pod-1", data - GroupFields.NAME))
        assertNull(groupFromFirestore("pod-1", data + (GroupFields.MEMBERS to "maya")))
        assertNull(groupFromFirestore("pod-1", data + (GroupFields.HANDOVER to mapOf("uid" to "ben"))))
        assertNull(groupFromFirestore("pod-1", data - GroupFields.ARCHIVES_AT))
    }

    private val sentAt = Instant.parse("2026-11-24T15:00:00Z")

    private val message = GroupMessage("m1", "pod-1", "ben", "Ben", "Same time next week?", sentAt)

    @Test
    fun newMessageLeavesItsTimeToTheServer() {
        val data = message.toNewFirestoreMessage()

        assertEquals(setOf(MessageFields.AUTHOR_UID, MessageFields.AUTHOR_NAME, MessageFields.TEXT, MessageFields.SENT_AT), data.keys)
        assertEquals("ben", data[MessageFields.AUTHOR_UID])
        assertTrue(data[MessageFields.SENT_AT] is FieldValue)
    }

    @Test
    fun messageReadsAsSentOrStillSending() {
        val data = message.toNewFirestoreMessage() + (MessageFields.SENT_AT to Timestamp(Date.from(sentAt)))

        assertEquals(message, messageFromFirestore("m1", "pod-1", data, pending = false))
        assertEquals(message.copy(status = MessageStatus.SENDING), messageFromFirestore("m1", "pod-1", data, pending = true))
    }

    @Test
    fun messageMissingAFieldOrItsTimeGivesNull() {
        val data = message.toNewFirestoreMessage() + (MessageFields.SENT_AT to Date.from(sentAt))

        assertNull(messageFromFirestore("m1", "pod-1", data - MessageFields.TEXT, pending = false))
        assertNull(messageFromFirestore("m1", "pod-1", data + (MessageFields.AUTHOR_UID to 7), pending = false))
        assertNull(messageFromFirestore("m1", "pod-1", data + (MessageFields.SENT_AT to null), pending = false))
    }

    private val proposal = SessionProposal(
        id = "next-1",
        startsAt = sentAt,
        endsAt = sentAt.plusSeconds(3600),
        building = "Mugar Library",
        meetingPoint = "2nd floor",
        mode = PodMode.TALK,
        byUid = ben.uid,
        yes = listOf(ben, maya),
        no = listOf(ana.uid),
    )

    @Test
    fun proposalSurvivesRoundTripWithVotesSortedByName() {
        val proposed = group.copy(invited = emptyList(), members = listOf(maya, ana, ben), proposal = proposal)

        assertEquals(proposed, groupFromFirestore("pod-1", proposed.toFirestore()))
        assertEquals(
            mapOf("ben" to "Ben", "maya" to "Maya"),
            (proposed.toFirestore()[GroupFields.PROPOSAL] as Map<*, *>)[ProposalFields.YES],
        )
        assertEquals(mapOf("ana" to true), (proposed.toFirestore()[GroupFields.PROPOSAL] as Map<*, *>)[ProposalFields.NO])
    }

    @Test
    fun groupWithoutTheProposalFieldHasNoProposal() {
        assertNull(groupFromFirestore("pod-1", group.toFirestore() - GroupFields.PROPOSAL)!!.proposal)
    }

    @Test
    fun malformedProposalSkipsTheGroup() {
        val data = group.copy(proposal = proposal).toFirestore()
        val broken = (data[GroupFields.PROPOSAL] as Map<*, *>) - ProposalFields.ENDS_AT

        assertNull(groupFromFirestore("pod-1", data + (GroupFields.PROPOSAL to broken)))
        assertNull(groupFromFirestore("pod-1", data + (GroupFields.PROPOSAL to "soon")))
    }

    @Test
    fun votingAndSchedulingChangeOnlyTheProposal() {
        val proposed = group.copy(invited = emptyList(), members = listOf(maya, ana, ben), proposal = proposal)

        assertEquals(setOf(GroupFields.PROPOSAL), changedFields(proposed, proposed.withVote(ana, canCome = true)).keys)
        assertEquals(mapOf(GroupFields.PROPOSAL to null), changedFields(proposed, proposed.withoutProposal()))
    }

    @Test
    fun leavingWithAVoteTakesItBackInTheSameWrite() {
        val proposed = group.copy(invited = emptyList(), members = listOf(maya, ana, ben), hostHandover = null, proposal = proposal)

        assertEquals(
            setOf(GroupFields.MEMBERS, GroupFields.ACCESS, GroupFields.PROPOSAL),
            changedFields(proposed, proposed.withoutMember(ana.uid)).keys,
        )
    }
}
