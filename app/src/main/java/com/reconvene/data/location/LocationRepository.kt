package com.reconvene.data.location

import android.Manifest
import android.content.Context
import android.location.LocationManager
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
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
    private suspend fun locate(): GeoPoint? {
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
