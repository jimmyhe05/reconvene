package com.reconvene.data.location

import android.Manifest
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import kotlin.coroutines.resume

interface LocationRepository {
    // One fix of where the phone is now, or null when location is off, not allowed, or takes too
    // long. The caller asks for the permission first. Nothing here keeps the result.
    suspend fun currentLocation(): GeoPoint?
}

// For builds and tests with no location, like a phone with location switched off.
object UnavailableLocationRepository : LocationRepository {
    override suspend fun currentLocation(): GeoPoint? = null
}

// Where the phone last knew it was, and how long ago.
data class LastKnown(val point: GeoPoint, val age: Duration)

private val RECENT: Duration = Duration.ofMinutes(2)
private val STILL_USABLE: Duration = Duration.ofMinutes(30)

// A recent last-known location answers at once. Otherwise a fresh fix is worth the wait, but if
// none comes, a location from the last half hour is still close enough for walking times on campus.
suspend fun chooseLocation(lastKnown: LastKnown?, requestFresh: suspend () -> GeoPoint?): GeoPoint? {
    if (lastKnown != null && lastKnown.age <= RECENT) return lastKnown.point
    return requestFresh() ?: lastKnown?.takeIf { it.age <= STILL_USABLE }?.point
}

class DeviceLocationRepository(context: Context) : LocationRepository {

    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(LocationManager::class.java)

    override suspend fun currentLocation(): GeoPoint? = try {
        locate()
    } catch (e: SecurityException) {
        // The permission was taken away between the screen's check and this call.
        null
    }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION])
    private suspend fun locate(): GeoPoint? = chooseLocation(newestLastKnown()) { requestFresh() }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION])
    private fun newestLastKnown(): LastKnown? =
        locationManager.getProviders(true)
            .mapNotNull { locationManager.getLastKnownLocation(it) }
            .maxByOrNull { it.elapsedRealtimeNanos }
            ?.let { LastKnown(GeoPoint(it.latitude, it.longitude), it.age()) }

    private fun Location.age(): Duration = Duration.ofNanos(SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos)

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION])
    private suspend fun requestFresh(): GeoPoint? {
        val provider = bestProvider() ?: return null
        return withTimeoutOrNull(TIMEOUT_MILLIS) {
            suspendCancellableCoroutine { continuation ->
                val cancel = CancellationSignal()
                continuation.invokeOnCancellation { cancel.cancel() }
                LocationManagerCompat.getCurrentLocation(
                    locationManager,
                    provider,
                    cancel,
                    ContextCompat.getMainExecutor(appContext),
                ) { location ->
                    continuation.resume(location?.let { GeoPoint(it.latitude, it.longitude) })
                }
            }
        }
    }

    // The fused provider when the phone has it, otherwise whichever is switched on.
    private fun bestProvider(): String? {
        val enabled = locationManager.getProviders(true)
        val preferred = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
        }
        return preferred.firstOrNull { it in enabled }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
