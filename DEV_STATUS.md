# OpenFog — Native Kotlin Rewrite: Development Status

This file records the state of the **native Kotlin rewrite** so work can resume
cleanly. The app was completely rewritten from the legacy web (JS/Leaflet) stack
into a native Android app.

## Layout

```
OpenFog-Online/
├── archive/   # FROZEN legacy (web app + old Android projects + release metadata). Do NOT modify.
├── native/    # THE ACTIVE APP (Kotlin + Jetpack Compose + osmdroid)
├── DEV_STATUS.md   # this file
├── LICENSE
└── .gitignore
```

- Package / applicationId: `com.openfog.online` (kept → upgrade path).
- Version: `2.0.0`, versionCode `3` (was 1.0.1 / 2). Range Picks: minSdk 26, target/compile 35.
- No Google Play Services, no Firebase — all FLOSS (preserves F-Droid posture).
- `AGENTS.md` is intentionally **gitignored / NOT in the repo** (project decision).

## Tech stack

| Concern        | Choice |
|----------------|--------|
| UI             | Jetpack Compose + Material 3 (`com.materialkolor` `DynamicMaterialTheme`, dark, seed `#6C63FF`) |
| Map            | osmdroid (`org.osmdroid:osmdroid-android:6.1.20`), OSM raster, tiled |
| Geometry       | JTS (`org.locationtech.jts:jts-core:1.19.0`) + custom spherical math (`geo/SphericalMath`) |
| Database       | Room 2.6.1 (KSP), 5 tables mirroring the old IndexedDB stores |
| JSON           | kotlinx.serialization |
| GPS            | platform `LocationManager` (no Play Services) |

## Build / run

From `native/` (JDK 21 is REQUIRED — `~/.jdks/jdk-21.0.12.1+1`):

```bash
export JAVA_HOME=~/.jdks/jdk-21.0.12.1+1
export PATH=$JAVA_HOME/bin:$PATH
export ANDROID_HOME=~/Android/Sdk
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Node/npm (only needed if you touch the archived web app): VS Code nvm path
`/home/linux/.var/app/com.visualstudio.code/config/nvm/versions/node/v18.20.8/bin`.

## Package map (`native/app/src/main/java/com/openfog/online/`)

- `MainActivity.kt` — Compose entry, ViewModel factory, toast for messages.
- `OpenFogApp.kt` — Application, builds `AppContainer`.
- `AppContainer.kt` — manual DI (db, engines, repos).
- `model/` — `OpenFogConstants`, `Models.kt` (GpsPoint, Track, FogPolygon, Achievement, TrackMetadata).
- `data/` —
  - `db/Entities.kt`, `db/Daos.kt`, `db/AppDatabase.kt` (5 tables: fog_polygons, tracks, achievements, user_level, stats).
  - `repo/Repositories.kt` — FogRepository (append reveal shapes), TrackRepository.
  - `AppJson.kt` — track point/metadata JSON round-trip.
- `geo/` —
  - `SphericalMath.kt`, `RevealShapeBuilder.kt` (point circle + rounded band capsule).
  - `GeoJsonCodec.kt` (custom minimal GeoJSON <-> JTS).
  - `FogOverlayEngine.kt` (union cache, viewport−revealed diff, simplify).
  - `FogState.kt` (running revealed-union cache; fog only changes when new shapes added).
- `gps/LocationProvider.kt` — one-shot + `watch(interval): Flow<GpsPoint>` via LocationManager.
- `achievements/` — `DefaultAchievements.kt` (8), `ProgressService.kt` (stats/level/achievements, mutex-serialized).
- `importexport/` — `TrackParser.kt` (GPX/KML/KMZ), `BackupManager.kt` (JSON backup, legacy-shaped `_version=1`).
- `ui/` —
  - `OpenFogApp.kt` (Scaffold + NavigationBar 3 tabs), `OpenFogViewModel.kt` (all state/actions).
  - `theme/Theme.kt`, `map/OsmMapController.kt` + `MapScreen.kt`, `profile/ProfileScreen.kt`, `settings/SettingsScreen.kt`.

## Status: what WORKS (compiles, logic ported)

- Project builds an installable debug APK (`BUILD SUCCESSFUL`, ~18 MB).
- Fog reveal geometry (point circle + 15 m rounded band), fog union-cache + viewport difference.
- Room persistence for all 5 stores; seed of achievements/userLevel/stats.
- Stats / level / 8 achievements engine.
- GPS LocationManager one-shot + watch flows.
- Track/backup import-export JSON.
- Compose UI: 3-tab nav, map screen (osmdroid + fog/blue-dot/live-polyline), profile (stats/level/achievements), settings (import/export/reset).

## Status: NOT yet verified / remaining work

- **NOT yet run on the MI9** — has not been `adb install`-ed or smoke-tested on-device.
- Voltage checkpoints not validated live: tracking cadence, fog refresh on pan/zoom, blue dot, live track polyline.
- `consumeMessage`/toast loop uses `LaunchedEffect(message)` — confirm no toast loop.
- osmdroid fog overlay draws hardcoded dark colors; wire to theme colors later.
- No unit tests yet (planned: JTS geometry engine + TrackParser tests).
- F-Droid `metadata/` for the new native app NOT yet recreated (archived old one only).

## Historical note

The previous web implementation was moved to `archive/` and is frozen. The F-Droid
release pipeline, fastlane metadata, splash/icon assets, etc. live there for
reference only and are superseded by this native app.

## Next steps (tomorrow)

1. `adb install -r` on the MI9 and smoke-test: reveal-on-travel, pan/zoom fog, live tracking + stop, locateMe blue dot, GPX/KML/KMZ import, backup export/import, resets, leveling.
2. Fix any on-device issues found.
3. Add JTS + parser unit tests.
4. (Optional) Recreate F-Droid metadata if redistribution is desired.
