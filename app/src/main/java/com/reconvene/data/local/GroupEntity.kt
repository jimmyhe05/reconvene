package com.reconvene.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.reconvene.data.model.Group
import com.reconvene.data.model.PodMember
import com.reconvene.data.model.PodMode
import com.reconvene.data.model.SessionProposal
import java.time.Instant

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val course: String?,
    val fromPodId: String,
    val hostUid: String,
    val members: List<PodMember>,
    val invited: List<PodMember>,
    // Room leaves an embedded object null when all of its columns are null.
    @Embedded(prefix = "handover_") val hostHandover: HandoverColumns?,
    @Embedded(prefix = "proposal_") val proposal: ProposalColumns?,
    val archivesAt: Instant,
)

data class HandoverColumns(val uid: String, val name: String)

data class ProposalColumns(
    val id: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val building: String,
    val meetingPoint: String,
    val mode: PodMode,
    val byUid: String,
    val yes: List<PodMember>,
    val no: List<String>,
)

// Disbanded groups are never stored: the repository deletes them and their chat.
fun GroupEntity.toGroup() = Group(
    id = id,
    name = name,
    course = course,
    fromPodId = fromPodId,
    hostUid = hostUid,
    members = members,
    invited = invited,
    hostHandover = hostHandover?.let { PodMember(it.uid, it.name) },
    proposal = proposal?.let {
        SessionProposal(
            id = it.id,
            startsAt = it.startsAt,
            endsAt = it.endsAt,
            building = it.building,
            meetingPoint = it.meetingPoint,
            mode = it.mode,
            byUid = it.byUid,
            yes = it.yes,
            no = it.no,
        )
    },
    archivesAt = archivesAt,
)

fun Group.toEntity() = GroupEntity(
    id = id,
    name = name,
    course = course,
    fromPodId = fromPodId,
    hostUid = hostUid,
    members = members,
    invited = invited,
    hostHandover = hostHandover?.let { HandoverColumns(it.uid, it.name) },
    proposal = proposal?.let {
        ProposalColumns(
            id = it.id,
            startsAt = it.startsAt,
            endsAt = it.endsAt,
            building = it.building,
            meetingPoint = it.meetingPoint,
            mode = it.mode,
            byUid = it.byUid,
            yes = it.yes,
            no = it.no,
        )
    },
    archivesAt = archivesAt,
)
