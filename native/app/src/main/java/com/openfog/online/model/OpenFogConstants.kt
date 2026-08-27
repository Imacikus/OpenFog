package com.openfog.online.model

/** Global game constants (mirrored from the legacy web app). */
object OpenFogConstants {
    /** Total land+water area of Earth in km2 */
    const val WORLD_TOTAL_AREA = 510_072_000.0

    /** Reveal radius around a fix in KILOMETERS (15 m). */
    const val REVEAL_RADIUS_KM = 0.015

    /** Max segment distance (km) before a fix reveals only a point (100 m). */
    const val MAX_SEGMENT_KM = 0.1

    /** XP gained per km2 revealed. */
    const val XP_PER_KM2 = 10.0

    /** XP required per level. */
    const val XP_PER_LEVEL = 500.0

    /** Default map center (Germany). */
    const val DEFAULT_LAT = 51.1657
    const val DEFAULT_LNG = 10.4515
    const val DEFAULT_ZOOM = 6.0

    /** Lat clamp for fog/map bounds. */
    const val MAX_LAT = 85.0
    const val MIN_LAT = -85.0

    /** Live tracking fix cadence (ms). */
    const val TRACKING_INTERVAL_MS = 1000L

    /** locateMe watch cadence (ms). */
    const val LOCATE_ME_INTERVAL_MS = 3000L

    /** Fog rebuild throttle (ms) after live fixes. */
    const val FOG_REBUILD_THROTTLE_MS = 1000L
}
