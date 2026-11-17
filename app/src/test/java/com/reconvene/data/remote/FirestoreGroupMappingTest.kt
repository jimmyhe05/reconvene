package com.reconvene.data.remote

import com.reconvene.data.model.Group
import com.reconvene.data.model.PodMember
import com.reconvene.data.model.SEMESTER_END
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
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
}
