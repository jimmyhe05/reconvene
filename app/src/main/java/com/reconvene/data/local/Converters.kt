package com.reconvene.data.local

import androidx.room.TypeConverter
import com.reconvene.data.model.PodMember
import kotlinx.serialization.json.Json
import java.time.Instant

class Converters {
    @TypeConverter
    fun instantToMillis(value: Instant): Long = value.toEpochMilli()

    @TypeConverter
    fun millisToInstant(value: Long): Instant = Instant.ofEpochMilli(value)

    @TypeConverter
    fun membersToJson(value: List<PodMember>): String = Json.encodeToString(value)

    @TypeConverter
    fun jsonToMembers(value: String): List<PodMember> = Json.decodeFromString(value)
}
