package com.openfog.online.data.repo

import com.openfog.online.data.AppJson
import com.openfog.online.data.db.AppDatabase
import com.openfog.online.data.db.FogPolygonEntity
import com.openfog.online.data.db.TrackEntity
import com.openfog.online.geo.GeoJsonCodec
import com.openfog.online.model.FogPolygon
import com.openfog.online.model.GpsPoint
import com.openfog.online.model.Track
import org.locationtech.jts.geom.Geometry

/** Appends reveal shapes and reads them back as JTS geometries. */
class FogRepository(private val db: AppDatabase) {

    private val dao = db.fogPolygonDao()

    /** Persist a reveal shape (append-only, never overwrite). */
    suspend fun saveRevealShape(geometry: Geometry, areaKm2: Double) {
        val entity = FogPolygonEntity(
            id = java.util.UUID.randomUUID().toString(),
            geometryJson = GeoJsonCodec.toJson(geometry),
            areaKm2 = areaKm2,
            source = "track",
            createdAt = System.currentTimeMillis()
        )
        dao.insertAll(listOf(entity))
    }

    /** All persisted geometries, parsed into JTS. */
    suspend fun getAllGeometries(): List<Geometry> {
        return dao.getAll().map { runCatching { GeoJsonCodec.toGeometry(it.geometryJson) }.getOrNull() }
            .filterNotNull()
    }

    suspend fun count(): Int = dao.count()

    suspend fun clear() = dao.clear()

    fun observe() = dao.observeAll()
}

/** Persist and read back tracks. */
class TrackRepository(private val db: AppDatabase) {

    private val dao = db.trackDao()

    suspend fun insert(track: Track) {
        val (pointsJson, metaJson) = AppJson.encodeTrack(track)
        dao.insert(TrackEntity(track.id, track.name, track.color, track.width, pointsJson, metaJson))
    }

    suspend fun getAll(): List<Track> {
        return dao.getAll().map { e ->
            AppJson.decodeTrack(e.id, e.name, e.color, e.width, e.pointsJson, e.metadataJson)
        }
    }

    /** Count of imported (non-live) tracks. */
    suspend fun importedCount(): Int {
        return dao.getAll().count { AppJson.decodeTrack(it.id, it.name, it.color, it.width, it.pointsJson, it.metadataJson).metadata.source != "live" }
    }

    suspend fun clear() = dao.clear()

    fun observe() = dao.observeAll()
}
