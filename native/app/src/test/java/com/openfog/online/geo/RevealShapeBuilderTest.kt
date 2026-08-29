package com.openfog.online.geo

import com.openfog.online.geo.RevealShapeBuilder.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RevealShapeBuilderTest {

    private val a = LatLng(52.0, 13.5)
    private val b = LatLng(52.001, 13.5) // ~111 m north (short segment)

    @Test
    fun firstFix_isPointCircle() {
        val p = RevealShapeBuilder.buildRevealPolygon(null, a)
        assertNotNull(p)
        assertTrue(p.isValid)
        assertTrue(p.numPoints > 40) // sampled circle, not a 4-pt rect
    }

    @Test
    fun shortSegment_isCapsuleBand() {
        val p = RevealShapeBuilder.buildRevealPolygon(a, b)
        assertTrue(p.isValid)
        // Band ~ 111 m long x 15 m wide, capsule ~ (length*w) + pi r^2
        val areaKm2 = RevealShapeBuilder.revealKm2(a, b)
        assertTrue("expected ~0.002 km2, got $areaKm2", areaKm2 > 0.001 && areaKm2 < 0.004)
    }

    @Test
    fun longGap_isPointOnly() {
        // ~1 km jump (> MAX_SEGMENT_M=100) => point circle, not a long band
        val far = LatLng(52.01, 13.5)
        val p = RevealShapeBuilder.buildRevealPolygon(a, far)
        val areaKm2 = RevealShapeBuilder.revealKm2(a, far)
        // point circle only: ~0.0007 km2
        assertTrue("got $areaKm2", areaKm2 < 0.0012)
    }

    @Test
    fun revealArea_isPositive() {
        val area = RevealShapeBuilder.revealKm2(a, b)
        assertTrue(area > 0.0)
    }
}
