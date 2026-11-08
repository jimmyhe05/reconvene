package com.reconvene.data.remote

import com.google.firebase.Timestamp
import com.reconvene.data.model.Pod
import com.reconvene.data.model.PodMode
import java.time.Instant
import java.util.Date

const val PODS_COLLECTION = "pods"

// Field names in a pod document. firebase/firestore.rules checks the same names.
object PodFields {
    const val TITLE = "title"
    const val COURSE = "course"
    const val BUILDING = "building"
    const val MEETING_POINT = "meetingPoint"
    const val STARTS_AT = "startsAt"
    const val ENDS_AT = "endsAt"
    const val CAPACITY = "capacity"
    const val HOST_NAME = "hostName"
    const val MEMBER_NAMES = "memberNames"
    const val MODE = "mode"
    const val CANCELED = "canceled"
}

// The document id is the pod id, so it is not repeated as a field. Firestore stores a Date as a timestamp.
fun Pod.toFirestore(): Map<String, Any?> = mapOf(
    PodFields.TITLE to title,
    PodFields.COURSE to course,
    PodFields.BUILDING to building,
    PodFields.MEETING_POINT to meetingPoint,
    PodFields.STARTS_AT to Date.from(startsAt),
    PodFields.ENDS_AT to Date.from(endsAt),
    PodFields.CAPACITY to capacity.toLong(),
    PodFields.HOST_NAME to hostName,
    PodFields.MEMBER_NAMES to memberNames,
    PodFields.MODE to mode.name,
    PodFields.CANCELED to canceled,
)

// Returns null for a document that is missing a field or has the wrong type, so one bad
// document is skipped instead of failing the whole refresh.
fun podFromFirestore(id: String, data: Map<String, Any?>): Pod? {
    val mode = (data[PodFields.MODE] as? String)
        ?.let { name -> PodMode.entries.firstOrNull { it.name == name } }
        ?: return null
    return Pod(
        id = id,
        title = data[PodFields.TITLE] as? String ?: return null,
        course = data[PodFields.COURSE] as? String,
        building = data[PodFields.BUILDING] as? String ?: return null,
        meetingPoint = data[PodFields.MEETING_POINT] as? String ?: return null,
        startsAt = instantOf(data[PodFields.STARTS_AT]) ?: return null,
        endsAt = instantOf(data[PodFields.ENDS_AT]) ?: return null,
        capacity = (data[PodFields.CAPACITY] as? Number)?.toInt() ?: return null,
        hostName = data[PodFields.HOST_NAME] as? String ?: return null,
        memberNames = (data[PodFields.MEMBER_NAMES] as? List<*>)?.filterIsInstance<String>() ?: return null,
        mode = mode,
        canceled = data[PodFields.CANCELED] as? Boolean ?: false,
    )
}

// Firestore returns timestamps as Timestamp; a Date is accepted too so the mapping can be tested on the JVM.
private fun instantOf(value: Any?): Instant? = when (value) {
    is Timestamp -> value.toDate().toInstant()
    is Date -> value.toInstant()
    else -> null
}
