package com.openfog.online.data

import com.openfog.online.model.GpsPoint
import com.openfog.online.model.Track
import com.openfog.online.model.TrackMetadata
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

/** Shared JSON configured for the app's serialization needs. */
object AppJson {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encodeTrack(track: Track): Pair<String, String> {
        val points = track.points.map { SerializedPoint(it.lat, it.lng, it.timestamp, it.accuracy) }
        val meta = SerializedMetadata(
            track.metadata.startTime,
            track.metadata.endTime,
            track.metadata.distanceMeters,
            track.metadata.source
        )
        return json.encodeToString(SerializedPoints(points)) to json.encodeToString(meta)
    }

    fun decodeTrack(id: String, name: String, color: String, width: Int, pointsJson: String, metadataJson: String): Track {
        val pointsRaw = json.decodeFromString<SerializedPoints>(pointsJson).points
        val meta = json.decodeFromString<SerializedMetadata>(metadataJson)
        val points = pointsRaw.map { GpsPoint(it.lat, it.lng, it.timestamp, it.accuracy) }
        val metadata = TrackMetadata(meta.startTime, meta.endTime, meta.distanceMeters, meta.source)
        return Track(id, name, points, color, width, metadata)
    }
}

@Serializable
private data class SerializedPoint(
    val lat: Double,
    val lng: Double,
    val timestamp: Long,
    val accuracy: Float? = null
)

@Serializable
private data class SerializedPoints(val points: List<SerializedPoint>)

@Serializable
private data class SerializedMetadata(
    val startTime: Long,
    val endTime: Long,
    val distanceMeters: Double,
    val source: String
)
