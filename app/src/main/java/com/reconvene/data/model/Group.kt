package com.reconvene.data.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

// Limits for groups. firebase/firestore.rules checks the same numbers.
const val GROUP_SIZE_MAX = 50
const val GROUP_NAME_MIN = 3
const val GROUP_NAME_MAX = 40
const val MESSAGE_MAX = 500

// The host can schedule a proposed session once this many people say they can come.
const val SCHEDULE_YES_MIN = 2

// Groups last the semester. After the end of Dec 19, 2026 in Boston they're read-only.
val SEMESTER_END: Instant = LocalDateTime.of(2026, 12, 20, 0, 0).atZone(ZoneId.of("America/New_York")).toInstant()

// A semester-long group kept from a pod. Only the pod's host can keep it, and everyone else in the
// pod is invited; nobody is added without accepting.
data class Group(
    val id: String,
    val name: String,
    val course: String?,
    val fromPodId: String,
    val hostUid: String,
    // Everyone who accepted, host first.
    val members: List<PodMember>,
    // Invited and hasn't answered yet.
    val invited: List<PodMember> = emptyList(),
    // The member the host asked to take over. The host can't leave until someone accepts.
    val hostHandover: PodMember? = null,
    val proposal: SessionProposal? = null,
    val archivesAt: Instant = SEMESTER_END,
    // A disbanded group stays on the server but nobody can read its chat or change it.
    val disbanded: Boolean = false,
) {
    val hostName: String
        get() = members.firstOrNull { it.uid == hostUid }?.name.orEmpty()

    // Everyone who has a seat, invited or not. Invites count toward the limit.
    val size: Int
        get() = members.size + invited.size

    fun isMember(uid: String): Boolean = members.any { it.uid == uid }

    fun isInvited(uid: String): Boolean = invited.any { it.uid == uid }

    fun isReadOnly(now: Instant): Boolean = disbanded || now >= archivesAt

    // Why this user can't do something now, or null if they can. Each check starts with the
    // things every change needs.
    private fun changeProblem(now: Instant): GroupProblem? = when {
        disbanded -> GroupProblem.DISBANDED
        now >= archivesAt -> GroupProblem.ARCHIVED
        else -> null
    }

    fun respondProblem(uid: String, now: Instant): GroupProblem? =
        changeProblem(now) ?: GroupProblem.NOT_INVITED.takeIf { !isInvited(uid) }

    fun leaveProblem(uid: String, now: Instant): GroupProblem? = changeProblem(now) ?: when {
        !isMember(uid) -> GroupProblem.NOT_MEMBER
        uid == hostUid -> GroupProblem.HOST_MUST_HAND_OVER
        else -> null
    }

    fun handoverProblem(uid: String, toUid: String, now: Instant): GroupProblem? = changeProblem(now) ?: when {
        uid != hostUid -> GroupProblem.NOT_HOST
        toUid == hostUid || !isMember(toUid) -> GroupProblem.NOT_MEMBER
        else -> null
    }

    // The host takes the offer back, or the member it was made to says no.
    fun withdrawHandoverProblem(uid: String, now: Instant): GroupProblem? = changeProblem(now) ?: when {
        hostHandover == null -> GroupProblem.NO_HANDOVER
        uid != hostUid && uid != hostHandover.uid -> GroupProblem.NOT_HOST
        else -> null
    }

    fun acceptHandoverProblem(uid: String, now: Instant): GroupProblem? =
        changeProblem(now) ?: GroupProblem.NO_HANDOVER.takeIf { hostHandover?.uid != uid }

    // Disbanding twice is allowed and does nothing.
    fun disbandProblem(uid: String, now: Instant): GroupProblem? = when {
        uid != hostUid -> GroupProblem.NOT_HOST
        disbanded -> null
        now >= archivesAt -> GroupProblem.ARCHIVED
        else -> null
    }

    fun messageProblem(uid: String, now: Instant): GroupProblem? =
        changeProblem(now) ?: GroupProblem.NOT_MEMBER.takeIf { !isMember(uid) }

    fun proposeProblem(uid: String, proposal: SessionProposal, now: Instant): GroupProblem? =
        messageProblem(uid, now) ?: GroupProblem.BAD_PROPOSAL.takeIf { !proposal.isValid(now) }

    fun voteProblem(uid: String, now: Instant): GroupProblem? = messageProblem(uid, now) ?: when {
        proposal == null -> GroupProblem.NO_PROPOSAL
        !proposal.isValid(now) -> GroupProblem.BAD_PROPOSAL
        else -> null
    }

    fun scheduleProblem(uid: String, now: Instant): GroupProblem? = changeProblem(now) ?: when {
        uid != hostUid -> GroupProblem.NOT_HOST
        proposal == null -> GroupProblem.NO_PROPOSAL
        !proposal.isValid(now) -> GroupProblem.BAD_PROPOSAL
        proposal.goingWith(members.first { it.uid == hostUid }).size < SCHEDULE_YES_MIN -> GroupProblem.NOT_ENOUGH_YES
        else -> null
    }

    fun withInviteAnswered(uid: String, accept: Boolean): Group {
        val member = invited.firstOrNull { it.uid == uid } ?: return this
        return copy(
            members = if (accept) members + member else members,
            invited = invited - member,
        )
    }

    // Leaving also takes back the member's vote, so it can't count toward scheduling.
    fun withoutMember(uid: String): Group = copy(
        members = members.filterNot { it.uid == uid },
        proposal = proposal?.withoutVote(uid),
    )

    fun withHandoverOffered(to: PodMember): Group = copy(hostHandover = to)

    fun withoutHandover(): Group = copy(hostHandover = null)

    // The new host takes over and the old host leaves, in one change.
    fun withHandoverAccepted(): Group {
        val to = hostHandover ?: return this
        val oldHostUid = hostUid
        return copy(hostUid = to.uid, hostHandover = null).withoutMember(oldHostUid).hostFirst()
    }

    fun withProposal(proposal: SessionProposal): Group = copy(proposal = proposal)

    fun withVote(member: PodMember, canCome: Boolean): Group = copy(proposal = proposal?.withVote(member, canCome))

    fun withoutProposal(): Group = copy(proposal = null)

    private fun hostFirst(): Group = copy(members = members.sortedBy { it.uid != hostUid })

    // The pod the host schedules from the proposal: everyone who said yes is going, the host too.
    // Room for the whole group, so members who didn't vote can still join it.
    fun scheduledPod(): Pod {
        val proposal = requireNotNull(proposal)
        val host = members.first { it.uid == hostUid }
        val going = proposal.goingWith(host)
        return Pod(
            id = proposal.id,
            title = name,
            course = course,
            building = proposal.building,
            meetingPoint = proposal.meetingPoint,
            startsAt = proposal.startsAt,
            endsAt = proposal.endsAt,
            capacity = members.size.coerceIn(going.size.coerceAtLeast(2), GROUP_SIZE_MAX),
            hostUid = hostUid,
            hostName = host.name,
            members = going,
            mode = proposal.mode,
            groupId = id,
        )
    }
}

// A time and place someone in the group suggested for the next session.
data class SessionProposal(
    // Also the id of the pod it becomes, so scheduling twice can't make two pods.
    val id: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val building: String,
    val meetingPoint: String,
    val mode: PodMode,
    val byUid: String,
    // Who said they can come, and who said they can't.
    val yes: List<PodMember> = emptyList(),
    val no: List<String> = emptyList(),
) {
    // Same limits as a new pod, since it becomes one.
    fun isValid(now: Instant): Boolean {
        val length = Duration.between(startsAt, endsAt)
        return endsAt > now &&
            length >= MIN_SESSION && length <= MAX_SESSION &&
            startsAt <= now.plus(START_WINDOW) &&
            building.isNotBlank() &&
            meetingPoint.trim().length >= 3
    }

    // true, false, or null for no answer yet.
    fun voteOf(uid: String): Boolean? = when {
        yes.any { it.uid == uid } -> true
        uid in no -> false
        else -> null
    }

    fun withVote(member: PodMember, canCome: Boolean): SessionProposal {
        val cleared = withoutVote(member.uid)
        return if (canCome) cleared.copy(yes = cleared.yes + member) else cleared.copy(no = cleared.no + member.uid)
    }

    fun withoutVote(uid: String): SessionProposal = copy(yes = yes.filterNot { it.uid == uid }, no = no - uid)

    // Scheduling counts the host as going, host first.
    fun goingWith(host: PodMember): List<PodMember> = listOf(host) + yes.filterNot { it.uid == host.uid }

    companion object {
        val MIN_SESSION: Duration = Duration.ofMinutes(30)
        val MAX_SESSION: Duration = Duration.ofHours(4)
        val START_WINDOW: Duration = Duration.ofDays(7)
    }
}

data class GroupMessage(
    val id: String,
    val groupId: String,
    val authorUid: String,
    val authorName: String,
    val text: String,
    val sentAt: Instant,
    val status: MessageStatus = MessageStatus.SENT,
)

// SENDING until the server has it; Firestore keeps trying while offline. FAILED when it was refused.
enum class MessageStatus { SENDING, SENT, FAILED }

// From the design: the closest reason, and the person isn't told who reported them.
enum class ReportReason { HARASSMENT, SPAM, ACADEMIC_DISHONESTY, UNSAFE_MEETUP, OTHER }

data class MessageReport(
    val groupId: String,
    val messageId: String,
    val reporterUid: String,
    val targetUid: String,
    val reason: ReportReason,
)

enum class GroupProblem {
    NOT_FOUND,
    DISBANDED,
    ARCHIVED,
    FULL,
    NOT_HOST,
    NOT_MEMBER,
    NOT_INVITED,
    HOST_MUST_HAND_OVER,
    NO_HANDOVER,
    NO_PROPOSAL,
    BAD_PROPOSAL,
    NOT_ENOUGH_YES,
    BAD_NAME,
    BAD_MESSAGE,
}

// latest is the group as the server has it, when the server is what said no.
class GroupActionException(val problem: GroupProblem, val latest: Group? = null) :
    Exception("Group action refused: $problem")

// Only the pod's host can keep it as a group. Everyone else in the pod gets an invite.
fun Pod.keepProblem(uid: String, name: String): GroupProblem? = when {
    uid != hostUid -> GroupProblem.NOT_HOST
    name.trim().length !in GROUP_NAME_MIN..GROUP_NAME_MAX -> GroupProblem.BAD_NAME
    members.size > GROUP_SIZE_MAX -> GroupProblem.FULL
    else -> null
}

fun Pod.toNewGroup(id: String, name: String): Group {
    val host = members.first { it.uid == hostUid }
    return Group(
        id = id,
        name = name.trim(),
        course = course,
        fromPodId = this.id,
        hostUid = hostUid,
        members = listOf(host),
        invited = members.filterNot { it.uid == hostUid },
    )
}

// The pod's title, cut to fit, as the starting name for a kept group.
fun Pod.suggestedGroupName(): String = title.take(GROUP_NAME_MAX).trim()
