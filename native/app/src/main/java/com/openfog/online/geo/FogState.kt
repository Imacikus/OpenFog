package com.openfog.online.geo

import org.locationtech.jts.geom.Geometry

/**
 * Holds the running union of ALL revealed polygons and computes the fog for a
 * viewport. Mirrors the legacy union-cache: the union only changes when NEW
 * shapes are added (not on pan/zoom), so a plain pan costs just diff+simplify.
 */
class FogState(private val engine: FogOverlayEngine) {

    /** Running union of all revealed shapes (null until first shape). */
    var revealedUnion: Geometry? = null
        private set

    /**
     * Initialize from all persisted geometries (full union). Called once at
     * startup and after data resets.
     */
    fun loadFrom(revealed: List<Geometry>) {
        revealedUnion = engine.buildRevealedUnion(revealed)
    }

    /** Incrementally extend the cached union with newly revealed shapes. */
    fun addShapes(shapes: List<Geometry>) {
        if (shapes.isEmpty()) return
        revealedUnion = engine.extendRevealedUnion(revealedUnion, shapes)
    }

    /** Compute the fog geometry (viewport minus revealed) for the given bounds. */
    fun computeFog(west: Double, south: Double, east: Double, north: Double, zoom: Double): Geometry {
        val padded = engine.padBounds(west, south, east, north, 1.5)
        val rect = engine.boundsRect(padded[0], padded[1], padded[2], padded[3])
        val fog = engine.computeFog(rect, revealedUnion)
        val tol = engine.toleranceForZoom(zoom)
        return engine.simplify(fog, tol)
    }

    fun clear() {
        revealedUnion = null
    }
}
