package com.openfog.online.model

/** A single recorded GPS fix. Mirrors the legacy web `{lat,lng,timestamp,accuracy}`. */
data class GpsPoint(
    val lat: Double,
    val lng: Double,
    val timestamp: Long,
    val accuracy: Float? = null
)

/** Track metadata (distance in METERS, source identifies how it was created). */
data class TrackMetadata(
    val startTime: Long,
    val endTime: Long,
    val distanceMeters: Double,
    val source: String // "live" | "gpx" | "kml"
)

/** A recorded or imported track. */
data class Track(
    val id: String,
    val name: String,
    val points: List<GpsPoint>,
    val color: String,
    val width: Int = 3,
    val metadata: TrackMetadata
)

/** GeoJSON geometry string (Polygon or MultiPolygon) as stored in fog_polygons. */
data class FogPolygon(
    val id: String,
    val geometryJson: String,
    val areaKm2: Double,
    val source: String = "track",
    val createdAt: Long
)

/** Condition driving an achievement unlock. */
data class AchievementCondition(
    val type: String, // "distance" | "area" | "percent" | "level" | "trackCount"
    val target: Double,
    val operator: String = ">="
)

/** Achievement definition + state. */
data class Achievement(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val condition: AchievementCondition,
    val unlocked: Boolean = false,
    val unlockedAt: Long? = null
)
