package com.openfog.online.geo

import com.openfog.online.model.OpenFogConstants
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LinearRing
import org.locationtech.jts.geom.Polygon

/**
 * Builds the geodesic "reveal" shape for one GPS segment.
 *
 * - First fix (prev == null) or GPS jump > MAX_SEGMENT_KM => point circle.
 * - Otherwise => a rounded band (capsule) along the segment, both at
 *   REVEAL_RADIUS_KM (15 m).
 *
 * Radius is in KM but shapes are constructed in meters via spherical math.
 */
object RevealShapeBuilder {

    private const val CIRCLE_SAMPLES = 48
    private const val HALF_CIRCLE_SAMPLES = 24

    private val factory = GeometryFactory()

    fun revealKm2(prev: LatLng?, current: LatLng, radiusKm: Double = OpenFogConstants.REVEAL_RADIUS_KM): Double {
        return buildRevealPolygon(prev, current, radiusKm).area * planarKm2PerDeg()
    }

    /**
     * Builds the reveal polygon for one segment.
     * @param prev previous fix, or null for the first fix / treat as point.
     */
    fun buildRevealPolygon(prev: LatLng?, current: LatLng, radiusKm: Double = OpenFogConstants.REVEAL_RADIUS_KM): Polygon {
        val radiusM = radiusKm * 1000.0
        val pointOnly = prev == null ||
            SphericalMath.distanceMeters(prev.lat, prev.lng, current.lat, current.lng) >
            OpenFogConstants.MAX_SEGMENT_KM * 1000.0

        return if (pointOnly) {
            pointCircle(current.lat, current.lng, radiusM)
        } else {
            bandCapsule(prev!!.lat, prev.lng, current.lat, current.lng, radiusM)
        }
    }

    private fun pointCircle(lat: Double, lng: Double, radiusM: Double): Polygon {
        val coords = ArrayList<Coordinate>(CIRCLE_SAMPLES + 1)
        for (i in 0 until CIRCLE_SAMPLES) {
            val bearing = i * 360.0 / CIRCLE_SAMPLES
            val p = SphericalMath.destination(lat, lng, radiusM, bearing)
            coords.add(Coordinate(p[1], p[0]))
        }
        coords.add(Coordinate(coords[0].x, coords[0].y))
        val ring = factory.createLinearRing(coords.toTypedArray())
        return factory.createPolygon(ring)
    }

    /**
     * Rounded-capsule band between A and B of half-width radiusM (matches turf
     * buffer of a lineString with round caps).
     */
    private fun bandCapsule(lat1: Double, lng1: Double, lat2: Double, lng2: Double, radiusM: Double): Polygon {
        val base = SphericalMath.bearing(lat1, lng1, lat2, lng2)
        val (leftBearing, rightBearing) = SphericalMath.perpendicular(base, base)

        // Offset endpoints at both sides.
        val aLeft = SphericalMath.destination(lat1, lng1, radiusM, leftBearing)
        val aRight = SphericalMath.destination(lat1, lng1, radiusM, rightBearing)
        val bLeft = SphericalMath.destination(lat2, lng2, radiusM, leftBearing)
        val bRight = SphericalMath.destination(lat2, lng2, radiusM, rightBearing)

        val coords = ArrayList<Coordinate>()
        // Start at A-left, sweep around B (from B-left to B-right the far way),
        // then back along A (A-right to A-left the far way) to close.
        coords.add(Coordinate(aLeft[1], aLeft[0]))

        // Arc around B from B-left to B-right (far side).
        val startB = (base - 90.0 + 360.0) % 360.0 // B-left bearing from B toward arc start
        for (i in 0..HALF_CIRCLE_SAMPLES) {
            val bearing = (startB + 180.0 - i * 180.0 / HALF_CIRCLE_SAMPLES + 720.0) % 360.0
            val p = SphericalMath.destination(lat2, lng2, radiusM, bearing)
            coords.add(Coordinate(p[1], p[0]))
        }
        coords.add(Coordinate(bRight[1], bRight[0]))

        // Back along A side: A-right -> A-left straight.
        coords.add(Coordinate(aRight[1], aRight[0]))

        // Arc around A from A-right to A-left (far side) to close shape.
        val startA = (base + 90.0 + 360.0) % 360.0 // A-right bearing
        for (i in 1 until HALF_CIRCLE_SAMPLES) {
            val bearing = (startA + i * 180.0 / HALF_CIRCLE_SAMPLES + 360.0) % 360.0
            val p = SphericalMath.destination(lat1, lng1, radiusM, bearing)
            coords.add(Coordinate(p[1], p[0]))
        }
        coords.add(Coordinate(coords[0].x, coords[0].y))

        val ring = factory.createLinearRing(coords.toTypedArray())
        return factory.createPolygon(ring)
    }

    /** Rough planar convert: degrees^2 -> km^2 (scales from equator; adequate for zone area display). */
    fun planarKm2PerDeg(): Double {
        // 1 deg lat ~ 111.32 km; at mid-ish latitudes use cos(lat)~1 for small reveal zones.
        return 111.32 * 111.32
    }

    data class LatLng(val lat: Double, val lng: Double)
}
