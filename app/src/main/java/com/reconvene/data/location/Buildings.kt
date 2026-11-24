package com.reconvene.data.location

import com.reconvene.data.model.Pod
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import java.net.URLEncoder

data class GeoPoint(val latitude: Double, val longitude: Double)

// Approximate entrances of the buildings the create form suggests. A building typed in by hand
// has no entry, so its pods show no distance.
val buildingLocations: Map<String, GeoPoint> = mapOf(
    "Mugar Library" to GeoPoint(42.3510, -71.1079),
    "CDS" to GeoPoint(42.3500, -71.1037),
    "GSU" to GeoPoint(42.3509, -71.1089),
    "Questrom" to GeoPoint(42.3497, -71.0995),
    "Photonics Center" to GeoPoint(42.3492, -71.1066),
    "Science & Engineering Library" to GeoPoint(42.3487, -71.1031),
    "CAS" to GeoPoint(42.3503, -71.1050),
    "Kilachand Center" to GeoPoint(42.3491, -71.1008),
)

private const val EARTH_RADIUS_METERS = 6_371_000.0
private const val WALK_METERS_PER_MINUTE = 80.0

// Streets aren't straight lines, so a walk is longer than the distance between two points.
private const val ROUTE_FACTOR = 1.3

// Straight-line distance over the earth's surface (the haversine formula).
fun GeoPoint.metersTo(other: GeoPoint): Double {
    val lat1 = Math.toRadians(latitude)
    val lat2 = Math.toRadians(other.latitude)
    val dLat = lat2 - lat1
    val dLon = Math.toRadians(other.longitude - longitude)
    val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
    return 2 * EARTH_RADIUS_METERS * asin(sqrt(a))
}

fun walkingMinutes(meters: Double): Int = ceil(meters * ROUTE_FACTOR / WALK_METERS_PER_MINUTE).toInt().coerceAtLeast(1)

// Null when the pod's building isn't one we know the location of.
fun Pod.walkingMinutesFrom(here: GeoPoint): Int? =
    buildingLocations[building]?.let { walkingMinutes(here.metersTo(it)) }

// A geo: link any maps app can open. Known buildings get a pin at their coordinates; others are a
// search for the name on campus.
fun Pod.mapLink(): String {
    val label = encode(building)
    return buildingLocations[building]
        ?.let { "geo:${it.latitude},${it.longitude}?q=${it.latitude},${it.longitude}($label)" }
        ?: "geo:0,0?q=${encode("$building, Boston University")}"
}

// The charset-name overload, since the Charset one only exists from Android 13.
private fun encode(text: String): String = URLEncoder.encode(text, "UTF-8").replace("+", "%20")
