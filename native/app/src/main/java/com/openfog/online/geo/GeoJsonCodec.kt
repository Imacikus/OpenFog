package com.openfog.online.geo

import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.LinearRing
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.geom.MultiPolygon

/**
 * Minimal GeoJSON (RFC 7946) encoder/decoder for the JTS geometries the app
 * stores. Only Polygons/MultiPolygons are persisted (the fog reveal shapes).
 *
 * Coordinates are [lng, lat] per GeoJSON.
 */
object GeoJsonCodec {

    private val factory = GeometryFactory()

    fun toGeometry(json: String): Geometry {
        val obj = parseObject(json)
        val type = obj["type"] as? String ?: error("missing type")
        return when (type) {
            "Polygon" -> polygon(obj["coordinates"] as List<*>)
            "MultiPolygon" -> multiPolygon(obj["coordinates"] as List<*>)
            else -> error("unsupported geometry type: $type")
        }
    }

    fun toJson(geometry: Geometry): String {
        val sb = StringBuilder()
        writeGeometry(sb, geometry)
        return sb.toString()
    }

    private fun parseObject(json: String): Map<String, Any> {
        // Lightweight tokenizer for the limited shape of geometries we store.
        return parseJson(json) as Map<String, Any>
    }

    private fun polygon(coords: List<*>): Polygon {
        val rings = coords.map { ring(it as List<*>) }
        val shell = rings.first()
        val holes = rings.drop(1).toTypedArray()
        return factory.createPolygon(shell, holes)
    }

    private fun multiPolygon(coords: List<*>): MultiPolygon {
        val polys = coords.map { polygon(it as List<*> as List<*>) }
        return factory.createMultiPolygon(polys.toTypedArray())
    }

    private fun ring(coords: List<*>): LinearRing {
        val pts = coords.map { it as List<*> }.map { c ->
            Coordinate((c[0] as Number).toDouble(), (c[1] as Number).toDouble())
        }.toTypedArray()
        if (!pts[0].equals2D(pts[pts.size - 1])) {
            (pts as java.util.ArrayList<Coordinate>).add(Coordinate(pts[0].x, pts[0].y))
        }
        return factory.createLinearRing(pts)
    }

    private fun writeGeometry(sb: StringBuilder, g: Geometry) {
        when (g) {
            is Polygon -> {
                sb.append("{\"type\":\"Polygon\",\"coordinates\":[")
                writeRing(sb, g.exteriorRing)
                for (i in 0 until g.numInteriorRing) {
                    sb.append(',')
                    writeRing(sb, g.getInteriorRingN(i))
                }
                sb.append("]}")
            }
            is MultiPolygon -> {
                sb.append("{\"type\":\"MultiPolygon\",\"coordinates\":[")
                for (i in 0 until g.numGeometries) {
                    if (i > 0) sb.append(',')
                    val p = g.getGeometryN(i) as Polygon
                    sb.append('[')
                    writeRing(sb, p.exteriorRing)
                    for (j in 0 until p.numInteriorRing) {
                        sb.append(',')
                        writeRing(sb, p.getInteriorRingN(j))
                    }
                    sb.append(']')
                }
                sb.append("]}")
            }
            else -> error("unsupported output geometry: $g")
        }
    }

    private fun writeRing(sb: StringBuilder, ring: LineString) {
        sb.append('[')
        for (i in 0 until ring.numPoints) {
            if (i > 0) sb.append(',')
            val c = ring.getCoordinateN(i)
            sb.append('[').append(c.x).append(',').append(c.y).append(']')
        }
        sb.append(']')
    }

    // ---- Minimal JSON parser (enough for geometry objects) ----

    private fun parseJson(text: String): Any {
        val p = Parser(text)
        val v = p.parseValue()
        p.skipWs()
        return v
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun parseValue(): Any {
            skipWs()
            return when (s[i]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> { i += 4; true }
                'f' -> { i += 5; false }
                'n' -> { i += 4; null as Any }
                else -> parseNumber()
            }
        }

        fun skipWs() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        private fun parseObject(): Map<String, Any> {
            val map = LinkedHashMap<String, Any>()
            i++ // {
            skipWs()
            if (s[i] == '}') { i++; return map }
            while (true) {
                skipWs()
                val key = parseString()
                skipWs()
                i++ // :
                map[key] = parseValue()
                skipWs()
                when (s[i]) {
                    ',' -> i++
                    '}' -> { i++; return map }
                }
            }
        }

        private fun parseArray(): List<Any> {
            val list = ArrayList<Any>()
            i++ // [
            skipWs()
            if (s[i] == ']') { i++; return list }
            while (true) {
                list.add(parseValue())
                skipWs()
                when (s[i]) {
                    ',' -> i++
                    ']' -> { i++; return list }
                }
            }
        }

        private fun parseString(): String {
            i++ // opening quote
            val sb = StringBuilder()
            while (s[i] != '"') {
                if (s[i] == '\\') {
                    i++
                    sb.append(
                        when (s[i]) {
                            'n' -> '\n'; 't' -> '\t'; 'r' -> '\r'; '\\' -> '\\'; '/' -> '/'; '"' -> '"'
                            'u' -> {
                                val hex = s.substring(i + 1, i + 5)
                                i += 4
                                hex.toInt(16).toChar()
                            }
                            else -> s[i]
                        }
                    )
                } else {
                    sb.append(s[i])
                }
                i++
            }
            i++ // closing quote
            return sb.toString()
        }

        private fun parseNumber(): Double {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            return s.substring(start, i).toDouble()
        }
    }
}
