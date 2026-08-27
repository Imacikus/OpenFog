package com.openfog.online.ui.map

import android.content.Context
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.openfog.online.model.GpsPoint
import com.openfog.online.model.OpenFogConstants
import com.openfog.online.ui.CenterRequest
import com.openfog.online.ui.FogFrame
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.Polygon
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.Overlay

/**
 * Wraps an osmdroid MapView inside Compose, renders the fog overlay, track
 * polylines, the blue dot, and reports viewport changes to the ViewModel.
 */
class OsmMapController(context: Context) {

    val mapView: MapView = MapView(context).apply {
        val prefs = context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
        Configuration.getInstance().load(context, prefs)
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        controller.setZoom(OpenFogConstants.DEFAULT_ZOOM)
        controller.setCenter(GeoPoint(OpenFogConstants.DEFAULT_LAT, OpenFogConstants.DEFAULT_LNG))
        isTilesScaledToDpi = true
    }

    private val fogOverlays = mutableListOf<FogPolygonOverlay>()
    private var trackOverlays = mutableListOf<Polyline>()
    private var liveOverlay: Polyline? = null
    private var blueDot: CircleMarkerOverlay? = null

    fun setListener(onViewport: (west: Double, south: Double, east: Double, north: Double, zoom: Double) -> Unit) {
        mapView.overlays.removeAll { it is ViewportListenerOverlay }
        mapView.overlays.add(2, ViewportListenerOverlay(mapView, onViewport))
    }

    fun setFog(frame: FogFrame?) {
        mapView.overlays.removeAll(fogOverlays)
        fogOverlays.clear()
        if (frame != null) {
            addGeometry(frame.geometry, fogOverlays)
        }
        mapView.invalidate()
    }

    fun setTracks(polylines: List<List<GeoPoint>>, visible: Boolean) {
        mapView.overlays.removeAll(trackOverlays)
        trackOverlays.clear()
        if (visible) {
            polylines.forEach { pts ->
                val pl = Polyline(mapView).apply {
                    setPoints(pts)
                    outlinePaint.color = 0xCC3498DB.toInt()
                    outlinePaint.strokeWidth = 6f * mapView.context.resources.displayMetrics.density
                }
                trackOverlays.add(pl)
            }
        }
        mapView.overlays.addAll(trackOverlays)
        mapView.invalidate()
    }

    fun setLivePolyline(points: List<GpsPoint>) {
        val old = liveOverlay
        if (old != null) mapView.overlays.remove(old)
        if (points.size < 2) { liveOverlay = null; mapView.invalidate(); return }
        val pl = Polyline(mapView).apply {
            setPoints(points.map { GeoPoint(it.lat, it.lng) })
            outlinePaint.color = 0xCCE74C3C.toInt()
            outlinePaint.strokeWidth = 6f * mapView.context.resources.displayMetrics.density
        }
        liveOverlay = pl
        mapView.overlays.add(pl)
        mapView.invalidate()
    }

    fun setBlueDot(point: GpsPoint?) {
        val old = blueDot
        if (old != null) mapView.overlays.remove(old)
        if (point == null) { blueDot = null; mapView.invalidate(); return }
        val overlay = CircleMarkerOverlay(point.lat, point.lng)
        blueDot = overlay
        mapView.overlays.add(overlay)
        mapView.invalidate()
    }

    fun applyCenterRequest(request: CenterRequest) {
        val lat = request.lat ?: return
        val lng = request.lng ?: return
        mapView.controller.setZoom(request.zoom.toDouble())
        mapView.controller.animateTo(GeoPoint(lat, lng))
    }

    fun dispose() {
        mapView.overlays.clear()
        mapView.onDetach()
    }

    private fun addGeometry(g: Geometry, out: MutableList<FogPolygonOverlay>) {
        fun polygon(p: Polygon) {
            val overlay = FogPolygonOverlay()
            overlay.ring = p.exteriorRing.coordinates.map { GeoPoint(it.y, it.x) }.toList()
            overlay.fillPaint.color = 0xDD0F0F13.toInt()
            overlay.fillPaint.style = Paint.Style.FILL
            out.add(overlay)
        }
        when (g) {
            is Polygon -> polygon(g)
            is org.locationtech.jts.geom.MultiPolygon -> {
                for (i in 0 until g.numGeometries) polygon(g.getGeometryN(i) as Polygon)
            }
            else -> {}
        }
    }

    /** osmdroid overlay that draws a filled polygon ring. */
    private class FogPolygonOverlay : Overlay() {
        var ring: List<GeoPoint> = emptyList()
        val fillPaint = Paint().apply {
            color = 0xDD0F0F13.toInt()
            style = Paint.Style.FILL
        }

        override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
            if (shadow) return
            if (ring.size < 3) return
            val path = Path()
            ring.forEachIndexed { i, gp ->
                val p = mapView.projection.toPixels(gp, null)
                if (i == 0) path.moveTo(p.x.toFloat(), p.y.toFloat())
                else path.lineTo(p.x.toFloat(), p.y.toFloat())
            }
            path.close()
            canvas.drawPath(path, fillPaint)
        }
    }

    private class CircleMarkerOverlay(private val lat: Double, private val lng: Double) : Overlay() {
        private val paint = Paint().apply {
            color = 0xFF6C63FF.toInt()
            style = Paint.Style.FILL
        }
        private val border = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
            if (shadow) return
            val p = mapView.projection.toPixels(GeoPoint(lat, lng), null)
            val r = 8f * mapView.context.resources.displayMetrics.density
            canvas.drawCircle(p.x.toFloat(), p.y.toFloat(), r, paint)
            canvas.drawCircle(p.x.toFloat(), p.y.toFloat(), r, border)
        }
    }

    private class ViewportListenerOverlay(
        private val map: MapView,
        private val onViewport: (Double, Double, Double, Double, Double) -> Unit
    ) : Overlay() {
        private var lastKey = ""

        override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
            if (shadow) return
            val b = map.boundingBox
            val zoom = map.zoomLevelDouble
            val key = "${b.latNorth},${b.latSouth},${b.lonEast},${b.lonWest},$zoom"
            if (key != lastKey) {
                lastKey = key
                onViewport(b.lonWest, b.latSouth, b.lonEast, b.latNorth, zoom)
            }
        }
    }
}

@Composable
fun OsmMapHost(
    controller: OsmMapController,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { controller.mapView }
    )
}
