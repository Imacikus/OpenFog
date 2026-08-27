package com.openfog.online.importexport

import android.util.Xml
import com.openfog.online.model.GpsPoint
import com.openfog.online.model.Track
import com.openfog.online.model.TrackMetadata
import org.xmlpull.v1.XmlPullParser
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Parses GPX, KML, and KMZ track files into [Track] models.
 * Mirrors the dual-variant KML parsing of the legacy web app.
 */
object TrackParser {

    fun parse(bytes: ByteArray, fileName: String): Track {
        return when {
            fileName.endsWith(".kmz", ignoreCase = true) -> parseKmz(bytes)
            fileName.endsWith(".gpx", ignoreCase = true) -> parseGpx(bytes)
            fileName.endsWith(".kml", ignoreCase = true) -> parseKml(bytes)
            else -> error("Nicht unterstützter Dateityp: $fileName")
        }
    }

    private fun parseKmz(bytes: ByteArray): Track {
        val zip = ZipInputStream(bytes.inputStream())
        var kmlBytes: ByteArray? = null
        while (true) {
            val entry = zip.nextEntry ?: break
            if (entry.name.endsWith(".kml", ignoreCase = true)) {
                kmlBytes = zip.readBytes()
                break
            }
            zip.closeEntry()
        }
        zip.close()
        val kml = kmlBytes ?: error("KMZ enthält keine .kml-Datei")
        return parseKml(kml)
    }

    fun parseGpx(bytes: ByteArray): Track {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(bytes.inputStream(), null)

        var name: String? = null
        val points = ArrayList<GpsPoint>()

        var event = parser.eventType
        var inTrkpt = false
        var curLat = 0.0
        var curLng = 0.0
        var curTime: String? = null

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name
                    if (tag == "trkpt") {
                        inTrkpt = true
                        curLat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: 0.0
                        curLng = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: 0.0
                        curTime = null
                    } else if (tag == "name" && name == null) {
                        name = parser.nextText()
                    } else if (tag == "time" && inTrkpt) {
                        curTime = parser.nextText()
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "trkpt") {
                        points.add(
                            GpsPoint(
                                curLat, curLng,
                                curTime?.let { runCatching { parseIsoDate(it) }.getOrNull() } ?: System.currentTimeMillis()
                            )
                        )
                        inTrkpt = false
                    }
                }
            }
            event = parser.next()
        }

        return buildTrack(name ?: "Track ${System.currentTimeMillis()}", points, "gpx")
    }

    private fun parseKml(bytes: ByteArray): Track {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(bytes.inputStream(), null)

        var name: String? = null
        val points = ArrayList<GpsPoint>()
        var inCoord = false
        var coordBuffer = StringBuilder()
        var inWhen = false
        var whenTime: String? = null
        var pendingTime: String? = null

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "name" -> { if (name == null) name = parser.nextText() }
                        "coordinates" -> { inCoord = true; coordBuffer = StringBuilder() }
                        "when" -> { inWhen = true; pendingTime = null }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inCoord) coordBuffer.append(parser.text)
                    else if (inWhen) pendingTime = parser.text?.trim()
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "coordinates" -> {
                            inCoord = false
                            points.addAll(parseCoordinates(coordBuffer.toString(), whenTime))
                            whenTime = null
                        }
                        "when" -> { inWhen = false; whenTime = pendingTime }
                        "gx:coord", "coord" -> { /* handled via coordinates; some KML nests coords */ }
                    }
                }
            }
            event = parser.next()
        }

        return buildTrack(name ?: "Track ${System.currentTimeMillis()}", points, "kml")
    }

    private fun parseCoordinates(text: String, ts: String?): List<GpsPoint> {
        val result = ArrayList<GpsPoint>()
        val now = ts?.let { runCatching { parseIsoDate(it) }.getOrNull() } ?: System.currentTimeMillis()
        val tokens = text.trim().split(Regex("\\s+"))
        for (token in tokens) {
            if (token.isBlank()) continue
            val parts = token.split(",")
            if (parts.size >= 2) {
                val lng = parts[0].toDoubleOrNull() ?: continue
                val lat = parts[1].toDoubleOrNull() ?: continue
                result.add(GpsPoint(lat, lng, now))
            }
        }
        return result
    }

    private fun buildTrack(name: String, points: List<GpsPoint>, source: String): Track {
        val sorted = points.sortedBy { it.timestamp }
        val meta = TrackMetadata(
            startTime = sorted.firstOrNull()?.timestamp ?: 0L,
            endTime = sorted.lastOrNull()?.timestamp ?: 0L,
            distanceMeters = calculateTrackDistanceKm(sorted) * 1000.0,
            source = source
        )
        val color = listOf("#3498db", "#e74c3c", "#2ecc71", "#f39c12", "#9b59b6", "#1abc9c").random()
        return Track(UUID.randomUUID().toString(), name, sorted, color, 3, meta)
    }

    /** Great-circle distance in km over all consecutive points. */
    fun calculateTrackDistanceKm(points: List<GpsPoint>): Double {
        if (points.size < 2) return 0.0
        var sum = 0.0
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            sum += haversineKm(a.lat, a.lng, b.lat, b.lng)
        }
        return sum
    }

    private fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371.0088
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val s1 = sin(dLat / 2.0); val s2 = sin(dLng / 2.0)
        val a = s1 * s1 + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * s2 * s2
        return 2.0 * r * atan2(sqrt(a), sqrt(1.0 - a))
    }

    private fun parseIsoDate(s: String): Long {
        // "2024-01-01T12:00:00Z" and variants
        return java.time.Instant.parse(s.replace(" ", "T")).toEpochMilli()
    }
}
