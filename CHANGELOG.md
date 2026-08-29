# Changelog

## 2.0.0 (2026-08-29)

- Complete native rewrite: Kotlin + Jetpack Compose + osmdroid (replaces the legacy JS/Leaflet web app).
- Room database (5 tables) with offline storage of fog polygons, tracks, achievements, level and stats.
- Fog-reveal engine with union cache + viewport diff and zoom-scaled simplification (JTS geometry).
- GPS tracking via the platform LocationManager (no Google Play Services).
- Import GPX / KML / KMZ and JSON backup export/import.
- Level/XP system and 8 achievements.
- Material 3 dynamic dark theme.
- First F-Droid release of the native build.
