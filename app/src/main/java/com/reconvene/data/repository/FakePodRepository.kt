package com.reconvene.data.repository

import com.reconvene.data.model.Pod
import com.reconvene.data.model.PodMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class FakePodRepository(private val clock: Clock) : PodRepository {

    override fun observePods(): Flow<List<Pod>> = flow {
        delay(700)
        emit(samplePods(clock))
    }
}

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
            hostName = "Priya",
            memberNames = listOf("Priya", "Alex", "Jordan"),
            mode = PodMode.COORDINATE,
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
            hostName = "Chen",
            memberNames = listOf("Chen", "Sam"),
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
            hostName = "Maya",
            memberNames = listOf("Maya", "Jordan", "Sam"),
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
            hostName = "Theo",
            memberNames = listOf("Theo", "Chen", "Priya"),
            mode = PodMode.COORDINATE,
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
            hostName = "Noah",
            memberNames = listOf("Noah", "Chen", "Priya", "Sam"),
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
            hostName = "Lina",
            memberNames = listOf("Lina", "Noah"),
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
            hostName = "Lina",
            memberNames = listOf("Lina", "Alex", "Noah"),
            mode = PodMode.TALK,
            canceled = true,
        ),
    )
}
