package com.reconvene.data.remote

import com.reconvene.data.auth.LOCAL_USER
import com.reconvene.data.model.Group
import com.reconvene.data.model.GroupActionException
import com.reconvene.data.model.GroupMessage
import com.reconvene.data.model.GroupProblem
import com.reconvene.data.model.MessageReport
import com.reconvene.data.model.MessageStatus
import com.reconvene.data.model.Pod
import com.reconvene.data.model.PodMember
import com.reconvene.data.model.PodMode
import com.reconvene.data.model.SessionProposal
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

// Sample groups kept in memory, for builds without Firebase. It checks every change with the
// same rules as the server would, so the screens can show refusals too. Nothing survives a restart.
class FakeGroupRemoteDataSource(
    private val clock: Clock,
    private val delayMillis: Long = 300,
) : GroupRemoteDataSource {

    private val groups = MutableStateFlow(sampleGroups(clock).associateBy { it.id })
    private val messages = MutableStateFlow(sampleMessages(clock))
    private val blocked = MutableStateFlow<Map<String, Set<String>>>(emptyMap())

    // Reports go nowhere without a server; kept so tests can see them.
    val reports = mutableListOf<MessageReport>()

    override fun observeGroups(uid: String): Flow<List<Group>> =
        groups.map { all -> all.values.filter { it.isMember(uid) || it.isInvited(uid) } }

    override fun observeMessages(groupId: String): Flow<List<GroupMessage>> =
        messages.map { all -> all.filter { it.groupId == groupId }.sortedBy { it.sentAt } }

    override fun observeBlocked(uid: String): Flow<Set<String>> = blocked.map { it[uid].orEmpty() }

    override suspend fun createGroup(group: Group) {
        delay(delayMillis)
        groups.update { it + (group.id to group) }
    }

    override suspend fun answerInvite(group: Group, uid: String, accept: Boolean): Group =
        change(group.id, { it.respondProblem(uid, now()) }) { it.withInviteAnswered(uid, accept) }

    override suspend fun leaveGroup(group: Group, uid: String): Group =
        change(group.id, { it.leaveProblem(uid, now()) }) { it.withoutMember(uid) }

    override suspend fun offerHandover(group: Group, uid: String, to: PodMember): Group =
        change(group.id, { it.handoverProblem(uid, to.uid, now()) }) { it.withHandoverOffered(to) }

    override suspend fun withdrawHandover(group: Group, uid: String): Group =
        change(group.id, { it.withdrawHandoverProblem(uid, now()) }) { it.withoutHandover() }

    override suspend fun acceptHandover(group: Group, uid: String): Group =
        change(group.id, { it.acceptHandoverProblem(uid, now()) }) { it.withHandoverAccepted() }

    override suspend fun disbandGroup(group: Group, uid: String): Group =
        change(group.id, { it.disbandProblem(uid, now()) }) { it.copy(disbanded = true) }

    override suspend fun propose(group: Group, proposal: SessionProposal): Group =
        change(group.id, { it.proposeProblem(proposal.byUid, proposal, now()) }) { it.withProposal(proposal) }

    override suspend fun vote(group: Group, member: PodMember, canCome: Boolean): Group =
        change(group.id, { it.voteProblem(member.uid, now()) }) { it.withVote(member, canCome) }

    override suspend fun schedule(group: Group, uid: String): Pair<Group, Pod> {
        var pod: Pod? = null
        val saved = change(group.id, { it.scheduleProblem(uid, now()) }) {
            pod = it.scheduledPod()
            it.withoutProposal()
        }
        return saved to requireNotNull(pod)
    }

    override suspend fun sendMessage(message: GroupMessage) {
        delay(delayMillis)
        val group = groups.value[message.groupId] ?: throw GroupActionException(GroupProblem.NOT_FOUND)
        group.messageProblem(message.authorUid, now())?.let { throw GroupActionException(it, group) }
        val sent = message.copy(status = MessageStatus.SENT)
        messages.update { all -> all.filterNot { it.id == sent.id } + sent }
    }

    override suspend fun report(report: MessageReport) {
        delay(delayMillis)
        reports += report
    }

    override suspend fun block(uid: String, blockedUid: String) {
        blocked.update { it + (uid to it[uid].orEmpty() + blockedUid) }
    }

    override suspend fun unblock(uid: String, blockedUid: String) {
        blocked.update { it + (uid to it[uid].orEmpty() - blockedUid) }
    }

    private fun now(): Instant = clock.instant()

    private suspend fun change(
        groupId: String,
        problem: (Group) -> GroupProblem?,
        apply: (Group) -> Group,
    ): Group {
        delay(delayMillis)
        val latest = groups.value[groupId] ?: throw GroupActionException(GroupProblem.NOT_FOUND)
        problem(latest)?.let { throw GroupActionException(it, latest) }
        val saved = apply(latest)
        groups.update { it + (saved.id to saved) }
        return saved
    }
}

private fun sampleMember(name: String) = PodMember(uid = "sample-${name.lowercase()}", name = name)

private val YOU = PodMember(LOCAL_USER.uid, LOCAL_USER.displayName.orEmpty())

fun sampleGroups(clock: Clock): List<Group> {
    val tomorrow = LocalDate.now(clock).plusDays(1)
    fun tomorrowAt(hour: Int, minute: Int = 0): Instant = tomorrow.atTime(hour, minute).atZone(clock.zone).toInstant()
    val maya = sampleMember("Maya")
    val jordan = sampleMember("Jordan")
    return listOf(
        Group(
            id = "g1",
            name = "Algorithms study group",
            course = "CS 501",
            fromPodId = "a1",
            hostUid = maya.uid,
            members = listOf(maya, YOU, jordan, sampleMember("Sam")),
            proposal = SessionProposal(
                id = "g1-next",
                startsAt = tomorrowAt(16),
                endsAt = tomorrowAt(17, 30),
                building = "Mugar Library",
                meetingPoint = "2nd floor, north study area",
                mode = PodMode.QUIET,
                byUid = maya.uid,
                yes = listOf(maya, jordan),
            ),
        ),
        Group(
            id = "g2",
            name = "Signals study group",
            course = "EC 502",
            fromPodId = "a2",
            hostUid = sampleMember("Theo").uid,
            members = listOf(sampleMember("Theo"), sampleMember("Chen")),
            invited = listOf(YOU),
        ),
        Group(
            id = "g3",
            name = "Deep work crew",
            course = null,
            fromPodId = "a4",
            hostUid = YOU.uid,
            members = listOf(YOU, sampleMember("Noah"), sampleMember("Priya")),
        ),
    )
}

private fun sampleMessages(clock: Clock): List<GroupMessage> {
    val now = clock.instant()
    fun hoursAgo(hours: Double): Instant = now.minus(Duration.ofMinutes((hours * 60).toLong()))
    fun message(id: String, group: String, author: PodMember, text: String, hours: Double) =
        GroupMessage(id, group, author.uid, author.name, text, hoursAgo(hours))
    return listOf(
        message("m1", "g1", sampleMember("Maya"), "Thanks for coming today. Same time next week?", 26.0),
        message("m2", "g1", sampleMember("Jordan"), "Works for me. Can someone share the practice set?", 25.0),
        message("m3", "g1", sampleMember("Sam"), "Start with problems 3 and 4.", 24.5),
        message("m4", "g1", sampleMember("Maya"), "I proposed a time for the next session. Vote in the plan.", 2.0),
        message("m5", "g3", sampleMember("Noah"), "Same tables at GSU on Thursday?", 5.0),
        message("m6", "g3", YOU, "Yes, I'll be there by 9.", 4.5),
    )
}
