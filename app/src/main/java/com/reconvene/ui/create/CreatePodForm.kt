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
const val BUILDING_MAX = 40
const val MEETING_POINT_MIN = 3
const val MEETING_POINT_MAX = 80
const val CAPACITY_MIN = 2
const val CAPACITY_MAX = 8
const val START_WINDOW_DAYS = 7
val MIN_DURATION: Duration = Duration.ofMinutes(30)
val MAX_DURATION: Duration = Duration.ofHours(4)

// Most likely picks first. The form also takes any other building, typed in.
val suggestedBuildings = listOf(
    "Mugar Library",
    "CDS",
    "GSU",
    "Questrom",
    "Photonics Center",
    "Science & Engineering Library",
    "CAS",
    "Kilachand Center",
)

// Common public spots in each suggested building, offered as one-tap meeting points.
val suggestedMeetingPoints: Map<String, List<String>> = mapOf(
    "Mugar Library" to listOf("1st floor, near the entrance", "2nd floor study tables", "3rd floor quiet study"),
    "CDS" to listOf("Ground floor, by the café", "2nd floor, by the big stairs", "3rd floor commons, near the windows"),
    "GSU" to listOf("Food court tables", "2nd floor lounge", "By the Starbucks"),
    "Questrom" to listOf("1st floor atrium", "By the Starbucks", "Pardee Library, 2nd floor"),
    "Photonics Center" to listOf("1st floor lobby", "2nd floor atrium tables"),
    "Science & Engineering Library" to listOf("1st floor group tables", "2nd floor quiet area"),
    "CAS" to listOf("Main lobby, Comm Ave entrance", "Basement study tables"),
    "Kilachand Center" to listOf("Ground floor lobby", "Lower level study area"),
)

data class CreatePodForm(
    val title: String = "",
    val course: String = "",
    val building: String? = null,
    // True while the user is typing a building that isn't in the suggestions. A flag, not a lookup,
    // so a typed name that happens to match a suggestion doesn't close the text field.
    val customBuilding: Boolean = false,
    val meetingPoint: String = "",
    val day: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val capacity: Int = 4,
    val mode: PodMode = PodMode.QUIET,
) : Serializable {
    val meetingPointSuggestions: List<String>
        get() = if (customBuilding) emptyList() else suggestedMeetingPoints[building].orEmpty()

    // Switching building drops a meeting point that was one of the old building's suggestions,
    // since it names a spot somewhere else. Text the user typed is kept.
    fun withBuilding(building: String, custom: Boolean = false): CreatePodForm {
        if (building == this.building && custom == customBuilding) return this
        val wasSuggested = meetingPoint in meetingPointSuggestions
        return copy(
            building = building,
            customBuilding = custom,
            meetingPoint = if (wasSuggested) "" else meetingPoint,
        )
    }

    val length: Duration
        get() = Duration.between(startTime, endTime)

    // A pod can't run past midnight, so a length only fits if it ends later on the same day.
    fun canLast(length: Duration): Boolean = startTime.plus(length) > startTime

    fun withLength(length: Duration): CreatePodForm = copy(endTime = startTime.plus(length))

    fun startsAt(zone: ZoneId): Instant = day.atTime(startTime).atZone(zone).toInstant()

    fun endsAt(zone: ZoneId): Instant = day.atTime(endTime).atZone(zone).toInstant()

    fun hasSameTimeAs(other: CreatePodForm): Boolean =
        day == other.day && startTime == other.startTime && endTime == other.endTime

    // Moving the start moves the end with it, keeping the length, so the end can't fall behind the
    // start. A pod can't run past midnight, so the end stops at 23:59 and validation flags it if
    // that's too short. If the current times are out of order, the length resets to the default.
    fun withStartTime(start: LocalTime): CreatePodForm {
        val kept = length.takeIf { it > Duration.ZERO } ?: DEFAULT_DURATION
        val end = start.plus(kept)
        return copy(startTime = start, endTime = if (end > start) end else LAST_END_TIME)
    }
}

val DEFAULT_DURATION: Duration = Duration.ofMinutes(90)
private val LAST_END_TIME: LocalTime = LocalTime.of(23, 59)

// The lengths offered as chips, covering MIN_DURATION to MAX_DURATION.
val LENGTH_CHOICES: List<Duration> = listOf(30L, 60L, 90L, 120L, 180L, 240L).map { Duration.ofMinutes(it) }

private const val START_SLOT_COUNT = 4
private val LATER_DAY_SLOTS = listOf(LocalTime.of(10, 0), LocalTime.of(13, 0), LocalTime.of(16, 0), LocalTime.of(19, 0))

// The next half hour that is at least 15 minutes away.
private fun nextHalfHour(now: ZonedDateTime): ZonedDateTime {
    val earliest = now.plusMinutes(15)
    val hour = earliest.truncatedTo(ChronoUnit.HOURS)
    return listOf(0L, 30L, 60L).map { hour.plusMinutes(it) }.first { it >= earliest }
}

// Quick start times for a day: the next few half hours today, or round times across a later day.
// Late at night today there may be fewer than four, or none.
fun startSlots(day: LocalDate, now: ZonedDateTime): List<LocalTime> {
    if (day != now.toLocalDate()) return LATER_DAY_SLOTS
    val first = nextHalfHour(now)
    return List(START_SLOT_COUNT) { first.plusMinutes(30L * it) }
        .filter { it.toLocalDate() == day }
        .map { it.toLocalTime() }
}

// Next half hour at least 15 minutes away, for 90 minutes; tomorrow morning if that runs past midnight.
fun newPodForm(now: ZonedDateTime): CreatePodForm {
    val start = nextHalfHour(now)
    val end = start.plus(DEFAULT_DURATION)
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
        building = FormError.BUILDING_MISSING.takeIf { building.isNullOrBlank() },
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

// Pasted text can bring line breaks into a single-line field.
fun String.toSingleLine(): String = replace(Regex("\\s*[\\r\\n]+\\s*"), " ")

fun CreatePodForm.toPod(id: String, host: PodMember, zone: ZoneId) = Pod(
    id = id,
    title = title.toSingleLine().trim(),
    course = course.toSingleLine().trim().ifEmpty { null },
    building = requireNotNull(building).toSingleLine().trim(),
    meetingPoint = meetingPoint.toSingleLine().trim(),
    startsAt = startsAt(zone),
    endsAt = endsAt(zone),
    capacity = capacity,
    hostUid = host.uid,
    hostName = host.name,
    members = listOf(host),
    mode = mode,
)
