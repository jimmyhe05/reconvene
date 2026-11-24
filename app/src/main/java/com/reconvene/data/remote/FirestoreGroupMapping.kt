package com.reconvene.data.remote

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.reconvene.data.model.Group
import com.reconvene.data.model.GroupMessage
import com.reconvene.data.model.MessageReport
import com.reconvene.data.model.MessageStatus
import com.reconvene.data.model.PodMember
import com.reconvene.data.model.PodMode
import com.reconvene.data.model.SessionProposal
import java.time.Instant
import java.util.Date

const val GROUPS_COLLECTION = "groups"

// Field names in a group document. firebase/firestore.rules checks the same names.
object GroupFields {
    const val NAME = "name"
    const val COURSE = "course"
    const val FROM_POD_ID = "fromPodId"
    const val HOST_UID = "hostUid"

    // Uid to display name, like a pod's members, so each change touches only the keys the rules expect.
    const val MEMBERS = "members"
    const val INVITED = "invited"

    // Everyone in members or invited, sorted. The Groups tab finds a user's groups with array-contains,
    // and sorting keeps the list the same when someone only moves from invited to members.
    const val ACCESS = "access"

    // Who started the group. Never changes, and lets them read it to learn it already exists after
    // they've handed it over and left.
    const val KEPT_BY_UID = "keptByUid"

    // { uid, name } of the member the host offered to hand over to, or null.
    const val HANDOVER = "handover"
    const val HANDOVER_UID = "uid"
    const val HANDOVER_NAME = "name"

    // The next session someone suggested, or null. Groups kept before proposals reached Firestore
    // don't have the field.
    const val PROPOSAL = "proposal"

    const val ARCHIVES_AT = "archivesAt"
    const val DISBANDED = "disbanded"

    // Set by the server when the group is disbanded.
    const val DISBANDED_AT = "disbandedAt"
}

// Field names inside a group's proposal map.
object ProposalFields {
    // Also the id of the pod it becomes.
    const val ID = "id"
    const val STARTS_AT = "startsAt"
    const val ENDS_AT = "endsAt"
    const val BUILDING = "building"
    const val MEETING_POINT = "meetingPoint"
    const val MODE = "mode"
    const val BY_UID = "byUid"

    // Uid to name of everyone who can come, and uid to true of everyone who can't, so a vote changes
    // one key that the rules can check.
    const val YES = "yes"
    const val NO = "no"
}

// Every field a change can touch. A new group adds KEPT_BY_UID; disbanding adds DISBANDED_AT.
// The document id is the group id, which is also the pod's.
fun Group.toFirestore(): Map<String, Any?> = mapOf(
    GroupFields.NAME to name,
    GroupFields.COURSE to course,
    GroupFields.FROM_POD_ID to fromPodId,
    GroupFields.HOST_UID to hostUid,
    GroupFields.MEMBERS to members.associate { it.uid to it.name },
    GroupFields.INVITED to invited.associate { it.uid to it.name },
    GroupFields.ACCESS to (members + invited).map { it.uid }.distinct().sorted(),
    GroupFields.HANDOVER to hostHandover?.let {
        mapOf(GroupFields.HANDOVER_UID to it.uid, GroupFields.HANDOVER_NAME to it.name)
    },
    GroupFields.PROPOSAL to proposal?.toFirestore(),
    GroupFields.ARCHIVES_AT to Date.from(archivesAt),
    GroupFields.DISBANDED to disbanded,
)

private fun SessionProposal.toFirestore(): Map<String, Any> = mapOf(
    ProposalFields.ID to id,
    ProposalFields.STARTS_AT to Date.from(startsAt),
    ProposalFields.ENDS_AT to Date.from(endsAt),
    ProposalFields.BUILDING to building,
    ProposalFields.MEETING_POINT to meetingPoint,
    ProposalFields.MODE to mode.name,
    ProposalFields.BY_UID to byUid,
    ProposalFields.YES to yes.associate { it.uid to it.name },
    ProposalFields.NO to no.associateWith { true },
)

fun Group.toNewFirestoreGroup(): Map<String, Any?> = toFirestore() + (GroupFields.KEPT_BY_UID to hostUid)

// Returns null for a document that is missing a field or has the wrong type, so one bad group is
// skipped instead of breaking the list.
fun groupFromFirestore(id: String, data: Map<String, Any?>): Group? {
    val hostUid = data[GroupFields.HOST_UID] as? String ?: return null
    val members = membersOf(data[GroupFields.MEMBERS])
        // Firestore doesn't keep map order, so put the host first and everyone else by name.
        ?.sortedWith(compareBy({ it.uid != hostUid }, { it.name }))
        ?: return null
    val handover = when (val value = data[GroupFields.HANDOVER]) {
        null -> null
        is Map<*, *> -> {
            val uid = value[GroupFields.HANDOVER_UID] as? String ?: return null
            val name = value[GroupFields.HANDOVER_NAME] as? String ?: return null
            PodMember(uid, name)
        }
        else -> return null
    }
    val proposal = when (val value = data[GroupFields.PROPOSAL]) {
        null -> null
        is Map<*, *> -> proposalFromFirestore(value) ?: return null
        else -> return null
    }
    return Group(
        id = id,
        name = data[GroupFields.NAME] as? String ?: return null,
        course = data[GroupFields.COURSE] as? String,
        fromPodId = data[GroupFields.FROM_POD_ID] as? String ?: return null,
        hostUid = hostUid,
        members = members,
        invited = membersOf(data[GroupFields.INVITED])?.sortedBy { it.name } ?: return null,
        hostHandover = handover,
        proposal = proposal,
        archivesAt = instantOf(data[GroupFields.ARCHIVES_AT]) ?: return null,
        disbanded = data[GroupFields.DISBANDED] as? Boolean ?: return null,
    )
}

private fun proposalFromFirestore(data: Map<*, *>): SessionProposal? = SessionProposal(
    id = data[ProposalFields.ID] as? String ?: return null,
    startsAt = instantOf(data[ProposalFields.STARTS_AT]) ?: return null,
    endsAt = instantOf(data[ProposalFields.ENDS_AT]) ?: return null,
    building = data[ProposalFields.BUILDING] as? String ?: return null,
    meetingPoint = data[ProposalFields.MEETING_POINT] as? String ?: return null,
    mode = (data[ProposalFields.MODE] as? String)?.let(PodMode::fromStored) ?: return null,
    byUid = data[ProposalFields.BY_UID] as? String ?: return null,
    yes = membersOf(data[ProposalFields.YES])?.sortedBy { it.name } ?: return null,
    no = (data[ProposalFields.NO] as? Map<*, *>)?.keys?.filterIsInstance<String>()?.sorted() ?: return null,
)

// Only the fields whose value differs, so an update touches exactly what the change touched.
fun changedFields(before: Group, after: Group): Map<String, Any?> {
    val old = before.toFirestore()
    return after.toFirestore().filter { (field, value) -> old[field] != value }
}

// A group's chat is a subcollection of the group: groups/{groupId}/messages/{messageId}.
const val MESSAGES_COLLECTION = "messages"

// Field names in a message document. firebase/firestore.rules checks the same names.
object MessageFields {
    const val AUTHOR_UID = "authorUid"
    const val AUTHOR_NAME = "authorName"
    const val TEXT = "text"

    // Set by the server, so everyone sees the chat in the same order whatever their phone's clock says.
    const val SENT_AT = "sentAt"
}

// The new message as written. The phone's sentAt isn't sent; the server fills in its own time.
fun GroupMessage.toNewFirestoreMessage(): Map<String, Any> = mapOf(
    MessageFields.AUTHOR_UID to authorUid,
    MessageFields.AUTHOR_NAME to authorName,
    MessageFields.TEXT to text,
    MessageFields.SENT_AT to FieldValue.serverTimestamp(),
)

// pending is true while the server doesn't have the message yet, which shows it as sending.
// Returns null for a document missing a field or with the wrong type.
fun messageFromFirestore(id: String, groupId: String, data: Map<String, Any?>, pending: Boolean): GroupMessage? =
    GroupMessage(
        id = id,
        groupId = groupId,
        authorUid = data[MessageFields.AUTHOR_UID] as? String ?: return null,
        authorName = data[MessageFields.AUTHOR_NAME] as? String ?: return null,
        text = data[MessageFields.TEXT] as? String ?: return null,
        sentAt = instantOf(data[MessageFields.SENT_AT]) ?: return null,
        status = if (pending) MessageStatus.SENDING else MessageStatus.SENT,
    )

// Reports of chat messages. Only the reporter can read their own; the team reviews them in the
// Firebase console.
const val REPORTS_COLLECTION = "reports"

object ReportFields {
    const val GROUP_ID = "groupId"
    const val MESSAGE_ID = "messageId"
    const val REPORTER_UID = "reporterUid"
    const val TARGET_UID = "targetUid"
    const val REASON = "reason"

    // Set by the server.
    const val CREATED_AT = "createdAt"
}

// One report per person per message, so reporting the same message again changes nothing.
val MessageReport.documentId: String get() = "${messageId}_$reporterUid"

fun MessageReport.toNewFirestoreReport(): Map<String, Any> = mapOf(
    ReportFields.GROUP_ID to groupId,
    ReportFields.MESSAGE_ID to messageId,
    ReportFields.REPORTER_UID to reporterUid,
    ReportFields.TARGET_UID to targetUid,
    ReportFields.REASON to reason.name,
    ReportFields.CREATED_AT to FieldValue.serverTimestamp(),
)

// Whom a user blocked: users/{uid}/blocked/{blockedUid}. Only that user can read or change it.
const val USERS_COLLECTION = "users"
const val BLOCKED_COLLECTION = "blocked"

object BlockFields {
    // The blocked person's name when they were blocked, for the list on Profile.
    const val NAME = "name"

    // Set by the server.
    const val BLOCKED_AT = "blockedAt"
}

fun PodMember.toNewFirestoreBlock(): Map<String, Any> = mapOf(
    BlockFields.NAME to name,
    BlockFields.BLOCKED_AT to FieldValue.serverTimestamp(),
)

// The document id is the blocked person's uid. Null for a document without a name.
fun blockedFromFirestore(blockedUid: String, data: Map<String, Any?>): PodMember? =
    (data[BlockFields.NAME] as? String)?.let { PodMember(blockedUid, it) }

private fun membersOf(value: Any?): List<PodMember>? = (value as? Map<*, *>)
    ?.mapNotNull { (uid, name) -> if (uid is String && name is String) PodMember(uid, name) else null }

// Firestore returns timestamps as Timestamp; a Date is accepted too so the mapping can be tested on the JVM.
private fun instantOf(value: Any?): Instant? = when (value) {
    is Timestamp -> value.toDate().toInstant()
    is Date -> value.toInstant()
    else -> null
}
