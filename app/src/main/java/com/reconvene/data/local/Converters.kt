package com.reconvene.data.local

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json
import java.time.Instant

class Converters {
    @TypeConverter
    fun instantToMillis(value: Instant): Long = value.toEpochMilli()

    @TypeConverter
    fun millisToInstant(value: Long): Instant = Instant.ofEpochMilli(value)

    @TypeConverter
    fun namesToJson(value: List<String>): String = Json.encodeToString(value)

    @TypeConverter
    fun jsonToNames(value: String): List<String> = Json.decodeFromString(value)
}
