package com.reconvene.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.reconvene.data.model.Pod
import com.reconvene.data.model.PodMode
import java.time.Instant

@Entity(tableName = "pods")
data class PodEntity(
    @PrimaryKey val id: String,
    val title: String,
    val course: String?,
    val building: String,
    val meetingPoint: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val capacity: Int,
    val hostName: String,
    val memberNames: List<String>,
    val mode: PodMode,
    val canceled: Boolean,
)

fun PodEntity.toPod() = Pod(
    id = id,
    title = title,
    course = course,
    building = building,
    meetingPoint = meetingPoint,
    startsAt = startsAt,
    endsAt = endsAt,
    capacity = capacity,
    hostName = hostName,
    memberNames = memberNames,
    mode = mode,
    canceled = canceled,
)

fun Pod.toEntity() = PodEntity(
    id = id,
    title = title,
    course = course,
    building = building,
    meetingPoint = meetingPoint,
    startsAt = startsAt,
    endsAt = endsAt,
    capacity = capacity,
    hostName = hostName,
    memberNames = memberNames,
    mode = mode,
    canceled = canceled,
)
