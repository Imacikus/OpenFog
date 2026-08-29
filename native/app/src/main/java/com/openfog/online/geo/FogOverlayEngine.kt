package com.openfog.online.geo

import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LinearRing
import org.locationtech.jts.geom.MultiPolygon
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.geom.TopologyException
import org.locationtech.jts.operation.overlayng.OverlayNGRobust
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier

/**
 * Performance-critical fog overlay computation, mirroring the legacy web app's
 * union-cache design (proven fast on-device):
 *
 *  - A running union of ALL revealed polygons ("revealed union") that only
 *    changes when NEW reveal shapes are added (not on pan/zoom).
 *  - On pan/zoom: viewport(bounds ⊕ buffer) MINUS revealed union, then
 *    simplified at a tolerance that scales with zoom.
 */
class FogOverlayEngine {

    private val factory = GeometryFactory()

    /**
     * Build the union of many revealed polygons efficiently using a spatial
     * index + unary union (equivalent to the legacy grid-cell union).
     *
     * Degenerate shapes (near-coincident edges of consecutive GPS capsules)
     * can make even the robust union throw a TopologyException; never let that
     * crash the app — fall back to an unmerged [MultiPolygon], which stays
     * correct for the viewport diff.
     */
    fun buildRevealedUnion(revealed: List<Geometry>): Geometry? {
        val cleaned = cleanShapes(revealed)
        if (cleaned.isEmpty()) return null
        return unionOrMerge(cleaned)
    }

    /**
     * Incrementally extend a cached revealed union with newly revealed shapes.
     * Fast path when only a few new segments arrive between viewport refreshes.
     *
     * Uses the robust OverlayNG union; falls back to an unmerged representation
     * if a TopologyException still escapes (see [unionOrMerge]).
     */
    fun extendRevealedUnion(current: Geometry?, newShapes: List<Geometry>): Geometry? {
        if (newShapes.isEmpty()) return current
        val cleaned = cleanShapes(newShapes)
        if (current == null) return unionOrMerge(cleaned)
        val all = ArrayList<Geometry>()
        all.add(current)
        all.addAll(cleaned)
        return unionOrMerge(all)
    }

    /** Drop empty/invalid/zero-area shapes and snap-clean the rest with buffer(0). */
    private fun cleanShapes(shapes: List<Geometry>): List<Geometry> {
        val out = ArrayList<Geometry>()
        for (g in shapes) {
            if (g.isEmpty) continue
            val c = try { g.buffer(0.0) } catch (_: TopologyException) { continue }
            if (c.isEmpty || c.area <= 0.0) continue
            out.add(c)
        }
        return out
    }

    /**
     * Robust union with a correctness-preserving fallback: if the union throws,
     * keep the input as an unmerged [MultiPolygon] so revealed areas stay
     * revealed instead of crashing.
     */
    private fun unionOrMerge(shapes: List<Geometry>): Geometry? {
        if (shapes.isEmpty()) return null
        try {
            val union = OverlayNGRobust.union(shapes)
            if (union != null && !union.isEmpty) return union.buffer(0.0)
        } catch (_: TopologyException) {
            // fall through to unmerged representation
        } catch (_: RuntimeException) {
            // fall through to unmerged representation
        }
        val polys = shapes.filterIsInstance<Polygon>()
        if (polys.size == shapes.size) {
            return factory.createMultiPolygon(polys.toTypedArray())
        }
        return factory.createGeometryCollection(shapes.toTypedArray())
    }

    /**
     * Compute the fog visible for the given viewport bounds:
     *   fog = viewportRect − revealedUnion
     * @param west/south/east/north viewport bounds (already padded by caller)
     * @return the fog polygon(s), or [boundsRect] if nothing is revealed.
     */
    fun computeFog(boundsRect: Polygon, revealedUnion: Geometry?): Geometry {
        if (revealedUnion == null || revealedUnion.isEmpty) return boundsRect
        val diff = boundsRect.difference(revealedUnion)
        return if (diff == null || diff.isEmpty) boundsRect else diff
    }

    /** Viewport rectangle (lng/lat) for the given padded bounds. */
    fun boundsRect(west: Double, south: Double, east: Double, north: Double): Polygon {
        val ring = factory.createLinearRing(
            arrayOf(
                Coordinate(west, south),
                Coordinate(east, south),
                Coordinate(east, north),
                Coordinate(west, north),
                Coordinate(west, south)
            )
        )
        return factory.createPolygon(ring)
    }

    /** Douglas-Peucker simplification with a tolerance appropriate for zoom. */
    fun simplify(geometry: Geometry, tolerance: Double): Geometry {
        if (tolerance <= 0.0) return geometry
        return DouglasPeuckerSimplifier.simplify(geometry, tolerance)
    }

    /**
     * Planar area in km2 of a geometry (degrees^2 -> km^2, approximate).
     * Sufficient for reveal-area accounting.
     */
    fun areaKm2(geometry: Geometry): Double {
        // 1 deg ~ 111.32 km; we track reveal zones that are small so a single
        // scale factor is acceptable.
        return geometry.area * (111.32 * 111.32)
    }

    /** Zoom-scaled tolerance for fog simplification (matches legacy design). */
    fun toleranceForZoom(zoom: Double): Double {
        // 360 / (256 * 2^zoom) * 0.5 (legacy web formula)
        return 360.0 / (256.0 * Math.pow(2.0, zoom)) * 0.5
    }

    /** Pads viewport bounds by a factor (multiplicative geo padding to avoid edge gaps). */
    fun padBounds(west: Double, south: Double, east: Double, north: Double, factor: Double): DoubleArray {
        val dLat = (north - south) * (factor - 1.0) / 2.0
        val dLng = (east - west) * (factor - 1.0) / 2.0
        return doubleArrayOf(
            maxOf(-180.0, west - dLng),
            maxOf(-85.0, south - dLat),
            minOf(180.0, east + dLng),
            minOf(85.0, north + dLat)
        )
    }
}
