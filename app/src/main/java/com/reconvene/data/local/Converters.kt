package com.reconvene.data.local

import androidx.room.TypeConverter
import com.reconvene.data.model.PodMember
import com.reconvene.data.model.PodMode
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

    @TypeConverter
    fun stringsToJson(value: List<String>): String = Json.encodeToString(value)

    @TypeConverter
    fun jsonToStrings(value: String): List<String> = Json.decodeFromString(value)

    @TypeConverter
    fun modeToName(value: PodMode): String = value.name

    // Room's own enum converter throws on a name it doesn't know, and rows saved before the vibes
    // were merged can say COORDINATE.
    @TypeConverter
    fun nameToMode(value: String): PodMode = PodMode.fromStored(value) ?: PodMode.QUIET
}
