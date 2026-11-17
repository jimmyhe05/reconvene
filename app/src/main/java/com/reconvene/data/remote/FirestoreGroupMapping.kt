package com.reconvene.data.remote

import com.google.firebase.Timestamp
import com.reconvene.data.model.Group
import com.reconvene.data.model.PodMember
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

    const val ARCHIVES_AT = "archivesAt"
    const val DISBANDED = "disbanded"

    // Set by the server when the group is disbanded.
    const val DISBANDED_AT = "disbandedAt"
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
    GroupFields.ARCHIVES_AT to Date.from(archivesAt),
    GroupFields.DISBANDED to disbanded,
)

fun Group.toNewFirestoreGroup(): Map<String, Any?> = toFirestore() + (GroupFields.KEPT_BY_UID to hostUid)

// Returns null for a document that is missing a field or has the wrong type, so one bad group is
// skipped instead of breaking the list. Proposals come in a later change; until then none is read.
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
    return Group(
        id = id,
        name = data[GroupFields.NAME] as? String ?: return null,
        course = data[GroupFields.COURSE] as? String,
        fromPodId = data[GroupFields.FROM_POD_ID] as? String ?: return null,
        hostUid = hostUid,
        members = members,
        invited = membersOf(data[GroupFields.INVITED])?.sortedBy { it.name } ?: return null,
        hostHandover = handover,
        archivesAt = instantOf(data[GroupFields.ARCHIVES_AT]) ?: return null,
        disbanded = data[GroupFields.DISBANDED] as? Boolean ?: return null,
    )
}

// Only the fields whose value differs, so an update touches exactly what the change touched.
fun changedFields(before: Group, after: Group): Map<String, Any?> {
    val old = before.toFirestore()
    return after.toFirestore().filter { (field, value) -> old[field] != value }
}

private fun membersOf(value: Any?): List<PodMember>? = (value as? Map<*, *>)
    ?.mapNotNull { (uid, name) -> if (uid is String && name is String) PodMember(uid, name) else null }

// Firestore returns timestamps as Timestamp; a Date is accepted too so the mapping can be tested on the JVM.
private fun instantOf(value: Any?): Instant? = when (value) {
    is Timestamp -> value.toDate().toInstant()
    is Date -> value.toInstant()
    else -> null
}
