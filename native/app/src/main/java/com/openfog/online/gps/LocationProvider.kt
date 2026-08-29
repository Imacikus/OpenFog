package com.openfog.online.gps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import com.openfog.online.model.GpsPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * FLOSS location provider using the platform LocationManager (no Google Play
 * Services). Matches the legacy app's "no Play Services — fallback" reality.
 */
class LocationProvider(private val context: Context) {

    private val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    fun hasPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    /** One-shot fix. Falls back to last known if nothing arrives in time. */
    suspend fun getCurrentLocation(timeoutMs: Long = 15000): GpsPoint? {
        if (!hasPermission()) return null
        return withTimeoutOrNull(timeoutMs) {
            watch(intervalMs = 0L).first()
        } ?: lastKnown() ?: null
    }

    /** Continuous watch emitting fixes. Requires permission already granted. */
    fun watch(intervalMs: Long = 1000L): Flow<GpsPoint> = callbackFlow {
        if (!hasPermission()) { close(); return@callbackFlow }
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySendBlocking(location.toGpsPoint())
            }
        }
        val providers = listOfNotNull(
            LocationManager.GPS_PROVIDER.takeIf { lm.isProviderEnabled(it) },
            LocationManager.NETWORK_PROVIDER.takeIf { lm.isProviderEnabled(it) }
        )
        if (providers.isEmpty()) {
            lastKnown()?.let { trySendBlocking(it) }
            close()
            return@callbackFlow
        }
        val minTime = if (intervalMs > 0L) intervalMs else 0L
        try {
            providers.forEach { provider ->
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        lm.requestLocationUpdates(provider, minTime, 0f, listener, Looper.getMainLooper())
                    } else {
                        @Suppress("DEPRECATION")
                        lm.requestLocationUpdates(provider, minTime, 0f, listener)
                    }
                } catch (_: Exception) {}
            }
        } catch (_: SecurityException) {
            close()
            return@callbackFlow
        }
        awaitClose { try { lm.removeUpdates(listener) } catch (_: Exception) {} }
    }

    fun lastKnown(): GpsPoint? {
        val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .firstOrNull { lm.isProviderEnabled(it) } ?: return null
        return try {
            lm.getLastKnownLocation(provider)?.toGpsPoint()
        } catch (_: Exception) {
            null
        }
    }

    private fun Location.toGpsPoint() = GpsPoint(
        lat = latitude,
        lng = longitude,
        timestamp = time,
        accuracy = accuracy
    )
}
