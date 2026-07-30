# AGENTS.md

**OpenFog** – Single-page vanilla JS app + **Capacitor Android App**. Leaflet map, Turf.js, IndexedDB via `idb`. Source in `src/main.js` + `index.html` (inline CSS). Vite `@` alias maps to `src/`.

## Commands

Run from `fog-of-world-web/`:

| Command | Action |
|---------|--------|
| `npm run dev` | Dev server at http://localhost:3000 |
| `npm run build` | Production build to `dist/` |
| `npm run preview` | Preview production build |
| `npx cap sync` | Copy web build to Android project |
| `cd android && ./gradlew assembleDebug` | Build debug APK |

APK full pipeline (from `fog-of-world-web/`):
```bash
npm run build && npx cap sync && cd android && ./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
cp android/app/build/outputs/apk/debug/app-debug.apk ../../openfog/OpenFog.apk
```
F-Droid builds from `openfog/android/` — gradle only, no `npx cap sync` during CI.

## Architecture

- **Entrypoint**: `index.html` → `<script type="module" src="/src/main.js">`
- **Fog reveal**: `turf.buffer()` each point at `REVEAL_RADIUS=0.015`km (15m) → save each circle to `fogPolygons` store. `turf.union()` only for area calculation (stop/import), never for storage.
- **Fog overlay**: viewport-bounds minus revealed via `turf.difference()`. Re-renders on `moveend`. Guarded by `fogUpdateGuard`. Grid cell cache (`buildCellCache`) groups + pre-merges nearby polygons.
- **5 IndexedDB stores**: `fogPolygons`, `tracks`, `achievements`, `userLevel`, `stats`. Version `1` — no migration.
- **Level system**: `XP = area_km2 * 10`, `level = floor(XP / 500) + 1`. 8 achievements checked on stats update.
- **Init** has try-catch: errors shown via visible message inside `#map`. `window.db` and `window.map` exposed as debug globals.

## UI Structure (Bottom Tab Navigation)

- **3 fixed tabs** at bottom (`#tabBar`): Karte / Profil / Einstellungen. Tab switching via `switchTab('map'|'profile'|'settings')`.
- **Tab 1 (Karte)**: transparent panel – map + FAB visible. FAB hidden on other tabs.
- **Tab 2 (Profil)**: scrollable panel with stats (area, %, distance, tracks), level + XP bar, achievements list. Updated by `updateUI()`.
- **Tab 3 (Einstellungen)**: scrollable panel with tracks toggle, GPX/KML/KMZ import, DB export/import, reset buttons. All inline – no modals.

## GPS System

- **One-shot** `gpsGetPosition`: tries `@capacitor/geolocation.getCurrentPosition()` first, falls back to `navigator.geolocation.getCurrentPosition()`.
- **Continuous** `gpsStartWatch`: tries `Geolocation.watchPosition()` with `enableLocationFallback: true` (critical for devices without Play Services), falls back to `navigator.geolocation.watchPosition()`.
- **Blue dot** (`locateMe()`): one-shot → marker + center map, then starts a watch. Second click centers map on marker. Blue dot added via `map.add()` directly, so `stopTracking()` does not clear it.
- **Tracking** (`startTracking/stopTracking`): separate GPS watch. `onTrackingLocation` saves fog circles via `revealFogAtPoint()` (no live stats update). On stop: union area calculated, stats updated once, `gpsTrackLayer.clearLayers()`.
- **FAB states**: `searching` (orange spinner) → `tracking` (red pulse). Error recovery resets to default.

## Key Conventions

- **All `onclick="fn()"` in HTML must be exposed on `window`**: every handler needs `window.fnName = fnName` in `main.js`. Exposed: `exportDatabase`, `handleFileImport`, `importDatabase`, `locateMe`, `resetAllData`, `resetFogOnly`, `resetTracksAndStats`, `switchTab`, `startTracking`, `stopTracking`, `toggleTracking`, `toggleTracks`.
- **`updateStats` uses promise-chain mutex** to serialize concurrent writes.

## Android / Capacitor

- **Two Android projects**:
  - `fog-of-world-web/android/` – **dev build target** (Capacitor 8.x). `npx cap sync` writes here, `./gradlew assembleDebug` builds from here.
  - `openfog/android/` – **F-Droid build target** (Capacitor 5.x, `@capacitor/android: ^5.7.8`). Gradle-only build, no `npx cap sync` during build. Web assets must be pre-synced and committed.
- **Version mismatch**: `fog-of-world-web/package.json` has `@capacitor/cli: ^6.2.1` but `@capacitor/core` + `@capacitor/android: ^8.4.2`. The CLI pin to 6.x is intentional.
- `openfog/capacitor.config.json` `webDir` points to `../fog-of-world-web/dist`.
- `minSdkVersion = 23` (required by `@capacitor/geolocation`), in both `android/variables.gradle` files.

## F-Droid

- F-Droid metadata at `fog-of-world-web/metadata/com.openfog.online.yml`:
  - Build: `commit: ac37c2b`, `subdir: openfog/android`, gradle build.
- Fastlane store metadata at `fog-of-world-web/fastlane/metadata/android/en-US/` (title, short_description, full_description).
- No CDN dependencies, no Google Play Services, no Firebase (`openfog/android/build.gradle` has no `google-services` classpath).
- All deps FLOSS (MIT / Apache 2.0).
- `applicationId = com.openfog.online` (all projects).

## Gotchas

- `REVEAL_RADIUS = 0.015` is in **kilometers** (15m), passed to `turf.buffer()` `{units: 'kilometers'}`.
- `saveFogPolygon` stores `geometry` object + legacy `coordinates`. Both `updateFogOverlay` and `updateRevealedTrail` handle `Polygon` and `MultiPolygon`.
- SW cache cleanup on startup deletes all caches except `osm-tiles` after APK update.
