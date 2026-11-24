package com.reconvene.data.remote

import com.reconvene.data.model.Pod
import com.reconvene.data.model.PodMember
import com.reconvene.data.model.PodMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

// Keeps the sample pods in memory like a tiny server, so a refresh returns what was joined, left,
// canceled or created. They reset when the app restarts.
class FakePodRemoteDataSource(private val clock: Clock) : PodRemoteDataSource {

    private val pods = MutableStateFlow<Map<String, Pod>?>(null)

    override suspend fun fetchPods(): List<Pod> {
        delay(700)
        return pods.updateAndGet { it ?: samples() }!!.values.toList()
    }

    override suspend fun fetchMyPods(uid: String): List<Pod> = fetchPods().filter { it.hasMember(uid) }

    override suspend fun createPod(pod: Pod) {
        store(pod)
    }

    override suspend fun joinPod(pod: Pod, member: PodMember): Pod = store(pod.withMember(member))

    override suspend fun leavePod(pod: Pod, uid: String): Pod = store(pod.withoutMember(uid))

    override suspend fun cancelPod(pod: Pod, uid: String): Pod = store(pod.copy(canceled = true))

    private fun store(pod: Pod): Pod {
        pods.update { (it ?: samples()) + (pod.id to pod) }
        return pod
    }

    private fun samples(): Map<String, Pod> = samplePods(clock).associateBy { it.id }
}

private fun sampleMember(name: String) = PodMember(uid = "sample-${name.lowercase()}", name = name)

fun samplePods(clock: Clock): List<Pod> {
    val now = clock.instant()
    val tomorrow = LocalDate.now(clock).plusDays(1)
    fun minutesFromNow(minutes: Long): Instant = now.plus(Duration.ofMinutes(minutes))
    fun tomorrowAt(hour: Int, minute: Int = 0): Instant =
        tomorrow.atTime(hour, minute).atZone(clock.zone).toInstant()

    return listOf(
        Pod(
            id = "a5",
            title = "Machine learning problem set 3",
            course = "CS 542",
            building = "CDS",
            meetingPoint = "Level 3 commons, near the windows",
            startsAt = minutesFromNow(-50),
            endsAt = minutesFromNow(20),
            capacity = 4,
            hostUid = sampleMember("Priya").uid,
            hostName = "Priya",
            members = listOf("Priya", "Alex", "Jordan").map(::sampleMember),
            mode = PodMode.TALK,
        ),
        Pod(
            id = "a6",
            title = "Operating systems midterm prep",
            course = "CS 552",
            building = "Science & Engineering Library",
            meetingPoint = "Lobby, by the group study rooms",
            startsAt = minutesFromNow(10),
            endsAt = minutesFromNow(110),
            capacity = 6,
            hostUid = sampleMember("Chen").uid,
            hostName = "Chen",
            members = listOf("Chen", "Sam").map(::sampleMember),
            mode = PodMode.TALK,
        ),
        Pod(
            id = "a1",
            title = "Algorithms exam review",
            course = "CS 501",
            building = "Mugar Library",
            meetingPoint = "2nd floor, north study area",
            startsAt = minutesFromNow(25),
            endsAt = minutesFromNow(115),
            capacity = 5,
            hostUid = sampleMember("Maya").uid,
            hostName = "Maya",
            members = listOf("Maya", "Jordan", "Sam").map(::sampleMember),
            mode = PodMode.QUIET,
        ),
        Pod(
            id = "a2",
            title = "Signals and systems problem set",
            course = "EC 502",
            building = "Photonics Center",
            meetingPoint = "Lobby tables by the east entrance",
            startsAt = minutesFromNow(180),
            endsAt = minutesFromNow(270),
            capacity = 4,
            hostUid = sampleMember("Theo").uid,
            hostName = "Theo",
            members = listOf("Theo", "Chen", "Priya").map(::sampleMember),
            mode = PodMode.TALK,
        ),
        Pod(
            id = "a4",
            title = "Quiet deep work morning",
            course = null,
            building = "GSU",
            meetingPoint = "Main floor, back tables",
            startsAt = tomorrowAt(9),
            endsAt = tomorrowAt(11),
            capacity = 4,
            hostUid = sampleMember("Noah").uid,
            hostName = "Noah",
            members = listOf("Noah", "Chen", "Priya", "Sam").map(::sampleMember),
            mode = PodMode.QUIET,
        ),
        Pod(
            id = "a3",
            title = "Calculus practice sprint",
            course = "MA 123",
            building = "CDS",
            meetingPoint = "Level 5 open collaboration area",
            startsAt = tomorrowAt(10),
            endsAt = tomorrowAt(11, 15),
            capacity = 6,
            hostUid = sampleMember("Lina").uid,
            hostName = "Lina",
            members = listOf("Lina", "Noah").map(::sampleMember),
            mode = PodMode.TALK,
        ),
        Pod(
            id = "a7",
            title = "Linear algebra review",
            course = "MA 242",
            building = "Mugar Library",
            meetingPoint = "3rd floor, east wing tables",
            startsAt = tomorrowAt(14),
            endsAt = tomorrowAt(15, 30),
            capacity = 5,
            hostUid = sampleMember("Lina").uid,
            hostName = "Lina",
            members = listOf("Lina", "Alex", "Noah").map(::sampleMember),
            mode = PodMode.TALK,
            canceled = true,
        ),
    )
}
