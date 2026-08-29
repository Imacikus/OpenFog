package com.openfog.online.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SphericalMathTest {

    @Test
    fun distanceMeters_isSymmetric() {
        val a = SphericalMath.distanceMeters(52.5, 13.4, 48.1, 11.6)
        val b = SphericalMath.distanceMeters(48.1, 11.6, 52.5, 13.4)
        assertEquals(a, b, 1.0)
        assertTrue(a > 400_000) // Berlin -> Munich ballpark
    }

    @Test
    fun distanceMeters_zeroForSamePoint() {
        assertEquals(0.0, SphericalMath.distanceMeters(10.0, 20.0, 10.0, 20.0), 1e-3)
    }

    @Test
    fun destination_returnsStartWhenDistanceZero() {
        val p = SphericalMath.destination(51.0, 7.0, 0.0, 90.0)
        assertEquals(51.0, p[0], 1e-6)
        assertEquals(7.0, p[1], 1e-6)
    }

    @Test
    fun destination_roundTripDistance() {
        val p = SphericalMath.destination(52.0, 13.0, 1000.0, 45.0)
        val back = SphericalMath.distanceMeters(52.0, 13.0, p[0], p[1])
        assertEquals(1000.0, back, 5.0)
    }

    @Test
    fun bearing_isNormalizedTo360() {
        val b = SphericalMath.bearing(52.0, 13.0, 52.0, 14.0)
        assertTrue(b in 0.0..360.0)
        assertTrue(b < 180.0) // moving east => bearing ~90
    }

    @Test
    fun perpendicular_isNinetyDegreesOff() {
        val (left, right) = SphericalMath.perpendicular(45.0, 45.0)
        assertEquals(135.0, left, 1e-9)
        assertEquals(315.0, right, 1e-9)
    }
}
