package com.reconvene.data.model

import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant

// Joining closes this long before a pod ends.
val JOIN_CUTOFF: Duration = Duration.ofMinutes(10)

// Longest display name stored on a pod. firebase/firestore.rules checks the same limit.
const val MEMBER_NAME_MAX = 40

@Serializable
data class PodMember(val uid: String, val name: String)

data class Pod(
    val id: String,
    val title: String,
    val course: String?,
    val building: String,
    val meetingPoint: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val capacity: Int,
    val hostUid: String,
    val hostName: String,
    // Everyone going, host first.
    val members: List<PodMember>,
    val mode: PodMode,
    val canceled: Boolean = false,
) {
    val memberNames: List<String>
        get() = members.map { it.name }

    val spotsLeft: Int
        get() = (capacity - members.size).coerceAtLeast(0)

    val isFull: Boolean
        get() = spotsLeft == 0

    fun timing(now: Instant): PodTiming = when {
        canceled -> PodTiming.CANCELED
        now >= endsAt -> PodTiming.ENDED
        now >= startsAt -> PodTiming.ONGOING
        else -> PodTiming.UPCOMING
    }

    fun hasMember(uid: String): Boolean = members.any { it.uid == uid }

    // Why someone who isn't in the pod can't join it now, or null if they can.
    fun joinProblem(now: Instant): PodActionProblem? = when {
        canceled || now >= endsAt.minus(JOIN_CUTOFF) -> PodActionProblem.CLOSED
        isFull -> PodActionProblem.FULL
        else -> null
    }

    fun withMember(member: PodMember): Pod =
        if (hasMember(member.uid)) this else copy(members = members + member)

    fun withoutMember(uid: String): Pod = copy(members = members.filterNot { it.uid == uid })
}

enum class PodMode { QUIET, COORDINATE, TALK }

enum class PodTiming { UPCOMING, ONGOING, ENDED, CANCELED }

enum class PodActionProblem { FULL, CLOSED, NOT_FOUND, HOST_CANNOT_LEAVE }

// latest is the pod as the server has it, when the server is what said no.
class PodActionException(val problem: PodActionProblem, val latest: Pod? = null) :
    Exception("Pod action refused: $problem")
