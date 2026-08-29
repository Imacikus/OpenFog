package com.openfog.online.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory

class FogOverlayEngineTest {

    private val engine = FogOverlayEngine()
    private val f = GeometryFactory()

    private fun rect(x: Double, y: Double, x2: Double, y2: Double) =
        f.createPolygon(
            f.createLinearRing(
                arrayOf(
                    Coordinate(x, y), Coordinate(x2, y), Coordinate(x2, y2),
                    Coordinate(x, y2), Coordinate(x, y)
                )
            )
        )

    @Test
    fun buildRevealedUnion_returnsNullForEmpty() {
        assertNull(engine.buildRevealedUnion(emptyList()))
    }

    @Test
    fun buildRevealedUnion_mergesOverlappingShapes() {
        val union = engine.buildRevealedUnion(listOf(rect(0.0, 0.0, 1.0, 1.0), rect(0.5, 0.0, 1.5, 1.0)))
        assertTrue(union != null && union.isValid)
        // Merged width ~1.5, area ~1.5 deg^2
        val area = union!!.area
        assertTrue("expected ~1.5, got $area", area > 1.4 && area < 1.6)
    }

    @Test
    fun extendRevealedUnion_toleratesNonNodedEdges() {
        // Two overlapping rectangles that share a nearly coincident boundary
        // (as consecutive GPS reveal capsules do) must not throw a TopologyException.
        val a = rect(0.0, 0.0, 1.0, 1.0)
        val b = rect(1.0, 0.0, 2.0, 1.0)
        val c = rect(1.000001, 0.0, 2.0, 1.0) // near-coincident shared edge
        var u = engine.extendRevealedUnion(null, listOf(a))
        u = engine.extendRevealedUnion(u, listOf(b))
        // This is the operation that crashed on-device with a non-noded intersection.
        u = engine.extendRevealedUnion(u, listOf(c))
        assertTrue(u != null && u.isValid)
        assertTrue(u!!.area > 1.9)
    }

    @Test
    fun extendRevealedUnion_returnsCurrentForEmpty() {
        val a = rect(0.0, 0.0, 1.0, 1.0)
        val u = engine.extendRevealedUnion(a, emptyList())
        assertEquals(a, u)
    }

    @Test
    fun buildRevealedUnion_doesNotCrashOnDegenerateDenseShapes() {
        // Dense near-coincident rectangles (as a dense GPS track produces) can
        // throw a TopologyException from even the robust union; it must fall
        // back to an unmerged representation instead of crashing.
        var shapes = mutableListOf<Geometry>()
        for (i in 0 until 50) {
            val x = i * 1.0
            shapes.add(rect(x, 0.0, x + 1.0 + 0.000001, 1.0))
        }
        val union = engine.buildRevealedUnion(shapes)
        assertTrue(union != null && union.isValid && union.area > 0.0)
    }

    @Test
    fun computeFog_returnsBoundsWhenNothingRevealed() {
        val bounds = rect(0.0, 0.0, 10.0, 10.0)
        val fog = engine.computeFog(bounds, null)
        assertEquals(100.0, fog.area, 1e-6)
    }

    @Test
    fun computeFog_subtractsRevealed() {
        val bounds = rect(0.0, 0.0, 4.0, 4.0)
        val revealed = rect(1.0, 1.0, 3.0, 3.0)
        val fog = engine.computeFog(bounds, revealed)
        assertTrue(fog.area < 16.0)
    }

    @Test
    fun boundsRect_hasFourCornersAndCloses() {
        val r = engine.boundsRect(1.0, 2.0, 3.0, 4.0)
        assertEquals(5, r.numPoints) // 4 corners + closure
        assertEquals(4.0, r.area, 1e-9)
    }

    @Test
    fun toleranceForZoom_ScalesDownWithZoom() {
        assertTrue(engine.toleranceForZoom(10.0) < engine.toleranceForZoom(5.0))
    }

    @Test
    fun padBounds_expandsWithinLimits() {
        val p = engine.padBounds(-170.0, -80.0, 170.0, 80.0, 1.1)
        assertTrue(p[0] >= -180.0)
        assertTrue(p[3] <= 85.0)
    }

    @Test
    fun simplify_reducesPointCountWithTolerance() {
        val rect = rect(0.0, 0.0, 10.0, 10.0)
        val simplified = engine.simplify(rect, 0.5)
        assertTrue(simplified.numPoints <= rect.numPoints)
    }
}
