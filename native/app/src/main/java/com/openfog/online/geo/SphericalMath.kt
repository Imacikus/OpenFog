package com.openfog.online.geo

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Spherical-earth helpers (meters + degrees). */
object SphericalMath {

    private const val EARTH_RADIUS_M = 6_371_008.8

    /** Great-circle distance in meters between two [lat,lng] points. */
    fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val a = haversineInternal(lat1, lng1, lat2, lng2)
        return 2.0 * EARTH_RADIUS_M * asin(sqrt(a))
    }

    /**
     * Destination point at [distanceMeters] and [bearingDeg] (degrees clockwise
     * from north) from a start [lat,lng].
     */
    fun destination(lat: Double, lng: Double, distanceMeters: Double, bearingDeg: Double): DoubleArray {
        val ang = distanceMeters / EARTH_RADIUS_M
        val brg = Math.toRadians(bearingDeg)
        val phi1 = Math.toRadians(lat)
        val lambda1 = Math.toRadians(lng)

        val sinPhi2 = sin(phi1) * cos(ang) + cos(phi1) * sin(ang) * cos(brg)
        val phi2 = asin(sinPhi2)
        val y = sin(brg) * sin(ang) * cos(phi1)
        val x = cos(ang) - sin(phi1) * sinPhi2
        val lambda2 = lambda1 + atan2(y, x)

        return doubleArrayOf(Math.toDegrees(phi2), normalizeLng(Math.toDegrees(lambda2)))
    }

    /** Bearing (degrees) from point1 to point2. */
    fun bearing(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dl = Math.toRadians(lng2 - lng1)
        val y = sin(dl) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dl)
        return Math.toDegrees(atan2(y, x)).let { (it + 360.0) % 360.0 }
    }

    /** Perpendicular bearings (±90°) for a band. */
    fun perpendicular(b1: Double, b2: Double): Pair<Double, Double> {
        val left = (b1 + 90.0) % 360.0
        val right = (b1 - 90.0 + 360.0) % 360.0
        return left to right
    }

    private fun haversineInternal(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val s1 = sin(dLat / 2.0)
        val s2 = sin(dLng / 2.0)
        return s1 * s1 + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * s2 * s2
    }

    private fun normalizeLng(lng: Double): Double {
        return ((lng + 540.0) % 360.0) - 180.0
    }
}
