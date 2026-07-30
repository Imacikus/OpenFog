# AGENTS.md

Single-page browser app (vanilla JS) + **Capacitor Android App**. Leaflet map, Turf.js geospatial, IndexedDB via `idb`. All source in one file + inline CSS.

## Commands (run from `fog-of-world-web/`)

| Command | Action |
|---------|--------|
| `npm install` | Install dependencies |
| `npm run dev` | Dev server at http://localhost:3000 |
| `npm run build` | Production build to `dist/` |
| `npx cap sync` | Copy web build to Android project |
| `cd android && ./gradlew assembleDebug` | Build debug APK |
| `adb install -r android/app/build/outputs/apk/debug/app-debug.apk` | Install on device |

## Android Commands (run from `openfog-online/`)

| Command | Action |
|---------|--------|
| `cd android && ./gradlew assembleDebug` | Build debug APK |
| `adb install OpenFogOnline.apk` | Install on connected device |

## APK build (full pipeline)

```bash
npm run build && npx cap sync && cd android && ./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then copy: `cp android/app/build/outputs/apk/debug/app-debug.apk ../../openfog-online/OpenFogOnline.apk`

## Key files

- `fog-of-world-web/index.html` — entry point + inline CSS + HTML
- `fog-of-world-web/src/main.js` — all app logic (~1245 lines)
- `fog-of-world-web/vite.config.js` — Vite + PWA plugin
- `fog-of-world-web/capacitor.config.json` — Capacitor config (webDir: dist, appId `com.openfog.online`)
- `fog-of-world-web/android/` — Capacitor Android project (generated, minSdk 23)
- `fog-of-world-web/android/variables.gradle` — minSdkVersion, compileSdkVersion, etc.
- `fog-of-world-web/android/app/src/main/AndroidManifest.xml` — location permissions added
- `fog-of-world-web/public/icons/icon-{192,512}x{192,512}.png` — PWA/Android icons
- `openfog-online/` — standalone Android app folder (copy of android/ + APK)
- `openfog-online/OpenFogOnline.apk` — prebuilt debug APK

## Dependencies

| Package | Version | Usage |
|---------|---------|-------|
| `leaflet` | ^1.9.4 | Map |
| `@turf/turf` | ^7.1.0 | Geospatial (buffer, union, difference, area) |
| `idb` | ^8.0.0 | IndexedDB wrapper |
| `jszip` | ^3.10.1 | KMZ parsing (static import) |
| `@capacitor/geolocation` | ^8.2.0 | GPS on Android (native) |
| `vite` | ^4.5.0 | Build |
| `vite-plugin-pwa` | ^0.17.5 | Service worker + manifest |
| `@capacitor/core` / `@capacitor/cli` / `@capacitor/android` | ^6.0.0 | Capacitor |

**Unused (in package.json but never imported):** `gpx-parse`, `@mapbox/togeojson`, `leaflet-draw`.

## UI Architecture

### Design System (Dark Theme, Map-First)

- **Theme**: Dark (`#1a1a2e` bg, `#6c63ff` accent). No light mode toggle.
- **Glassmorphism**: Panels use `backdrop-filter: blur(16px)` + `rgba(22,33,62,0.78)` background via `.glass` class.
- **Map**: Fixed full-viewport (`position: fixed; inset: 0`), z-index 0. All UI floats above.
- **Responsive**: Single breakpoint at 768px.
- **Mobile (< 768px)**: Glass header (48px), Bottom Sheet for stats, FAB for tracking.
- **Desktop (≥ 768px)**: Glass header, collapsed sidebar (52px → 260px on hover/click), FAB.

### Layout Components

- **Header** (`#header`): Fixed top, 48px. App name + Locate + Import + DB backup buttons (Import/DB visible on all breakpoints now).
- **FAB** (`#fabTrack`): Bottom-right. Toggles GPS tracking. Has 3 visual states: default (blue pin icon), searching (orange spinner, `fab.searching`), tracking (red pulse, `fab.tracking`).
- **Bottom Sheet** (`#bottomSheet`): Fixed bottom mobile. Preview 68px, tap to expand 70vh. Stats + level + achievements.
- **Desktop Sidebar** (`#sidebarDesktop`): Fixed left-center, collapsed 52px, click to expand 260px.
- **Modals** (`#importModal`, `#dbModal`, `#settingsModal`): Fullscreen overlay, glass content.

## Architecture

- **Entrypoint**: `index.html` → `<script type="module" src="/src/main.js">`
- **Fog reveal**: buffer each track point at 15m → save each circle individually to `fogPolygons` store. `turf.union()` used for area calculation on stop/import.
- **Fog overlay**: dark (`#1a1a2e`, 85%) viewport-bounds polygon minus revealed areas via `turf.difference`. Re-renders on `moveend`. Guarded by `fogUpdateGuard`.
- **5 IndexedDB stores**: `fogPolygons`, `tracks`, `achievements`, `userLevel`, `stats`
- **8 default achievements** (`main.js:48-121`). Checks: area, percent, distance, level, trackCount.
- **Level system**: `XP = area_km2 * 10`, `level = floor(XP / 500) + 1`
- **World area**: `WORLD_TOTAL_AREA = 510072000` km²

## GPS System

- **GPS helper** `gpsStartWatch()` (`main.js:902-937`): Tries `@capacitor/geolocation` first (with `enableLocationFallback: true` for devices without Play Services), falls back to `navigator.geolocation.watchPosition()`.
- **GPS one-shot** `gpsGetPosition()` (`main.js:878-900`): `@capacitor/geolocation.getCurrentPosition()` with fallback to `navigator.geolocation.getCurrentPosition()`. Used by `locateMe()` for immediate first fix.
- **Blue dot** (`locateMe()`): first does a one-shot `gpsGetPosition()` (immediate marker + map center), then starts a continuous watch via `gpsStartWatch()`. Second click centers map on marker.
- **Tracking** (`startTracking()` / `stopTracking()`): Separate GPS watch. Fog circles saved in real-time via `revealFogAtPoint()` (no stats update). On stop, union area calculated and stats updated once.
- **FAB states**: `searching` (spinner, `.fab.searching`) → `tracking` (red pulse, `.fab.tracking`). Error recovery resets FAB to default.
- **Permissions**: `@capacitor/geolocation` handles runtime permission requests. Android manifest has `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION`.

## Data Safety

- **`updateStats` mutex**: serializes concurrent stats writes to prevent race conditions.
- **`onTrackingLocation`** is now `async` with `await` + try-catch around `revealFogAtPoint`.
- **No stats during GPS**: `revealFogAtPoint()` only saves the fog circle. Stats update happens once on stop via `turf.union` area calculation.
- **`onunhandledrejection` handler** logs all uncaught promise rejections.
- **SW cache cleanup** on startup deletes old caches after APK update.

## Key conventions

- **All HTML onclick handlers must be on `window`**: every `onclick="fn()"` in `index.html` needs `window.fnName = fnName` in `main.js`. Current exposed: `toggleSidebar`, `toggleBottomSheet`, `toggleDesktopSidebar`, `toggleTracking`, `showImportModal`, `closeImportModal`, `handleFileImport`, `locateMe`, `startTracking`, `stopTracking`, `showDBModal`, `closeDBModal`, `exportDatabase`, `importDatabase`, `showSettingsModal`, `closeSettingsModal`, `resetAllData`, `resetTracksAndStats`, `resetFogOnly`, `toggleTracks`.
- **GPS markers in layer group**: tracking adds circle markers to `gpsTrackLayer` (`L.layerGroup`). Cleared on `stopTracking()`.
- **Blue dot is NOT in a layer group**: added directly to map via `map.add()`, not cleared by stopTracking.

## Gotchas

- **`REVEAL_RADIUS = 0.015` is in kilometers** (15m). Passed to Turf `buffer()` with `{units: 'kilometers'}`.
- **No DB migration**: IndexedDB version is hardcoded `1` in `openDB('FogOfWorldDB', 1)`.
- **Debug globals**: `window.db` and `window.map` are exposed after init.
- **Map center**: Germany `[51.1657, 10.4515]` zoom 6.
- **`saveFogPolygon` stores full `geometry` object** with `type`. Both `updateFogOverlay` and `updateRevealedTrail` handle `Polygon` and `MultiPolygon`. Old entries with only `coordinates` (no `geometry.type`) still supported via fallback.
- **Union only for area**: Each 15m circle stored individually. `turf.union()` used only for exact area calculation, not for storage.
- **Init has try-catch**: errors show visible message inside `#map` div.
- **`minSdkVersion = 23`** (required by `@capacitor/geolocation`).
- **Import/Export buttons** in header are visible on all screen sizes (not desktop-only).
- **Leaflet zoom controls** remain (top-left). Drawing toolbar (`leaflet-draw`) was removed.
- **JSZip is a static import** (not dynamic), bundled into main chunk to avoid SW cache hash mismatches.
