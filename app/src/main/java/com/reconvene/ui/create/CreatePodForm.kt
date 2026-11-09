package com.reconvene.ui.create

import com.reconvene.data.model.Pod
import com.reconvene.data.model.PodMember
import com.reconvene.data.model.PodMode
import java.io.Serializable
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

const val TOPIC_MIN = 3
const val TOPIC_MAX = 60
const val COURSE_MAX = 20
const val MEETING_POINT_MIN = 3
const val MEETING_POINT_MAX = 80
const val CAPACITY_MIN = 2
const val CAPACITY_MAX = 8
const val START_WINDOW_DAYS = 7
val MIN_DURATION: Duration = Duration.ofMinutes(30)
val MAX_DURATION: Duration = Duration.ofHours(4)

val buildings = listOf("Mugar Library", "CDS", "Photonics Center", "GSU", "Science & Engineering Library")

data class CreatePodForm(
    val title: String = "",
    val course: String = "",
    val building: String? = null,
    val meetingPoint: String = "",
    val day: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val capacity: Int = 4,
    val mode: PodMode = PodMode.QUIET,
) : Serializable {
    fun startsAt(zone: ZoneId): Instant = day.atTime(startTime).atZone(zone).toInstant()

    fun endsAt(zone: ZoneId): Instant = day.atTime(endTime).atZone(zone).toInstant()
}

// Next half hour at least 15 minutes away, for 90 minutes; tomorrow morning if that runs past midnight.
fun newPodForm(now: ZonedDateTime): CreatePodForm {
    val earliest = now.plusMinutes(15)
    val hour = earliest.truncatedTo(ChronoUnit.HOURS)
    val start = listOf(0L, 30L, 60L).map { hour.plusMinutes(it) }.first { it >= earliest }
    val end = start.plusMinutes(90)
    return if (end.toLocalDate() == now.toLocalDate()) {
        CreatePodForm(day = now.toLocalDate(), startTime = start.toLocalTime(), endTime = end.toLocalTime())
    } else {
        CreatePodForm(day = now.toLocalDate().plusDays(1), startTime = LocalTime.of(9, 0), endTime = LocalTime.of(10, 30))
    }
}

fun dayChoices(today: LocalDate): List<LocalDate> = List(START_WINDOW_DAYS) { today.plusDays(it.toLong()) }

enum class FormError {
    TOPIC_TOO_SHORT,
    BUILDING_MISSING,
    MEETING_POINT_TOO_SHORT,
    END_NOT_AFTER_START,
    ALREADY_OVER,
    TOO_SHORT,
    TOO_LONG,
}

data class FormErrors(
    val title: FormError? = null,
    val building: FormError? = null,
    val meetingPoint: FormError? = null,
    val time: FormError? = null,
) {
    val isEmpty: Boolean
        get() = this == FormErrors()
}

fun CreatePodForm.validate(now: Instant, zone: ZoneId): FormErrors {
    val startsAt = startsAt(zone)
    val endsAt = endsAt(zone)
    val length = Duration.between(startsAt, endsAt)
    return FormErrors(
        title = FormError.TOPIC_TOO_SHORT.takeIf { title.trim().length < TOPIC_MIN },
        building = FormError.BUILDING_MISSING.takeIf { building == null },
        meetingPoint = FormError.MEETING_POINT_TOO_SHORT.takeIf { meetingPoint.trim().length < MEETING_POINT_MIN },
        time = when {
            !endsAt.isAfter(startsAt) -> FormError.END_NOT_AFTER_START
            !endsAt.isAfter(now) -> FormError.ALREADY_OVER
            length < MIN_DURATION -> FormError.TOO_SHORT
            length > MAX_DURATION -> FormError.TOO_LONG
            else -> null
        },
    )
}

fun CreatePodForm.toPod(id: String, host: PodMember, zone: ZoneId) = Pod(
    id = id,
    title = title.trim(),
    course = course.trim().ifEmpty { null },
    building = requireNotNull(building),
    meetingPoint = meetingPoint.trim(),
    startsAt = startsAt(zone),
    endsAt = endsAt(zone),
    capacity = capacity,
    hostUid = host.uid,
    hostName = host.name,
    members = listOf(host),
    mode = mode,
)
