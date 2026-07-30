# AGENTS.md

**OpenFog** – Single-page vanilla JS app + **Capacitor Android App**. Leaflet map, Turf.js, IndexedDB via `idb`. Source in `src/main.js` (~1182 lines) + `index.html` (~750 lines, inline CSS). Vite `@` alias maps to `src/`.

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
Detailed Android prerequisites in `openfog/README.md`.
Detailed Android prerequisites in `openfog/README.md`.

## Architecture

- **Entrypoint**: `index.html:770` → `<script type="module" src="/src/main.js">`
- **Fog reveal**: `turf.buffer()` each point at `REVEAL_RADIUS=0.015`km (15m) → save each circle to `fogPolygons` store. `turf.union()` only for area calculation (stop/import), never for storage.
- **Fog overlay**: viewport-bounds minus revealed via `turf.difference()`. Re-renders on `moveend`. Guarded by `fogUpdateGuard`. Grid cell cache (`buildCellCache`) groups + pre-merges nearby polygons.
- **5 IndexedDB stores**: `fogPolygons`, `tracks`, `achievements`, `userLevel`, `stats`. Version `1` — no migration.
- **Level system**: `XP = area_km2 * 10`, `level = floor(XP / 500) + 1`. 8 achievements checked on stats update.
- **Init** has try-catch: errors shown via visible message inside `#map`. `window.db` and `window.map` exposed as debug globals.
- `leaflet-draw` JS removed but its CSS still loaded from CDN (`index.html:8`).

## UI Structure (Bottom Tab Navigation)

- **3 fixed tabs** at bottom (`#tabBar`): Karte / Profil / Einstellungen. Tab switching via `switchTab('map'|'profile'|'settings')`.
- **Tab 1 (Karte)**: transparent panel – map + FAB visible. FAB hidden on other tabs.
- **Tab 2 (Profil)**: scrollable panel with stats (area, %, distance, tracks), level + XP bar, achievements list. Updated by `updateUI()`.
- **Tab 3 (Einstellungen)**: scrollable panel with tracks toggle, GPX/KML/KMZ import, DB export/import, reset buttons. All inline – no modals.
- **Header** (`#header`): only app title + locate button. Height 48px, sits above tabs.
- **Map** (`#map`): fullscreen fixed, always behind tab panels.
- **FAB** (`#fabTrack`): tracking start/stop, floats above map on Tab 1 only.

## GPS System

- **One-shot** `gpsGetPosition` (`main.js:878-900`): tries `@capacitor/geolocation.getCurrentPosition()` first, falls back to `navigator.geolocation.getCurrentPosition()`.
- **Continuous** `gpsStartWatch` (`main.js:903-937`): tries `Geolocation.watchPosition()` with `enableLocationFallback: true` (critical for devices without Play Services), falls back to `navigator.geolocation.watchPosition()`.
- **Blue dot** (`locateMe()`): one-shot → marker + center map, then starts a watch. Second click centers map on marker. Blue dot added via `map.add()` directly, so `stopTracking()` does not clear it.
- **Tracking** (`startTracking/stopTracking`): separate GPS watch. `onTrackingLocation` saves fog circles via `revealFogAtPoint()` (no live stats update). On stop: union area calculated, stats updated once, `gpsTrackLayer.clearLayers()`.
- **FAB states**: `searching` (orange spinner) → `tracking` (red pulse). Error recovery resets to default.

## Key Conventions

- **All `onclick="fn()"` in HTML must be exposed on `window`**: every handler needs `window.fnName = fnName` in `main.js`. Exposed: `exportDatabase`, `handleFileImport`, `importDatabase`, `locateMe`, `resetAllData`, `resetFogOnly`, `resetTracksAndStats`, `switchTab`, `startTracking`, `stopTracking`, `toggleTracking`, `toggleTracks`.
- **`updateStats` uses promise-chain mutex** to serialize concurrent writes (`main.js:410-446`).

## F-Droid Publishing

### Änderungen für F-Droid-Konformität (bereits gemacht)

1. **LICENSE** (MIT) im Projekt-Root
2. **Keine CDN-Links mehr** – Leaflet CSS + Font Awesome werden via npm importiert und von Vite gebundled
3. **Kein google-services-Fallback** in `build.gradle`
4. **SPDX-Header** in `main.js`
5. **`applicationId` = `com.openfog.online`** (matching namespace)
6. **Unused deps entfernt** (`leaflet-draw`, `@mapbox/togeojson`, `gpx-parse`)

### Fürs Haupt-F-Droid-Repository einreichen

```bash
# 1. Source auf GitHub/GitLab pushen
git remote add origin https://github.com/Imacikus/OpenFog-Online.git
git push -u origin main

# 2. Metadata-PR auf gitlab.com/fdroid/fdroiddata erstellen
#    Eine Datei anlegen unter: metadata/com.openfog.online.yml
#    Inhalt:
#    Categories: - Navigation
#    License: MIT
#    WebSite: https://github.com/Imacikus/OpenFog-Online
#    SourceCode: https://github.com/Imacikus/OpenFog-Online
#    IssueTracker: https://github.com/Imacikus/OpenFog-Online/issues
#    AutoUpdateMode: Version
#    UpdateCheckMode: Tags
#    CurrentVersion: "1.0"
#    CurrentVersionCode: 1

# 3. F-Droid baut selbst – du musst nur taggen:
git tag v1.0
git push origin v1.0
```

### Eigenes F-Droid-Repo (einfacher)

```bash
# 1. fdroidserver installieren (Linux/macOS)
sudo apt install fdroidserver

# 2. Repo initialisieren
mkdir ~/fdroid-repo && cd ~/fdroid-repo
fdroid init

# 3. APK ins Repo aufnehmen
cp /pfad/zu/OpenFog.apk repo/
fdroid update --create-metadata

# 4. Signieren (einmalig)
fdroid sign

# 5. Repo index neu bauen
fdroid update

# 6. Hochladen (z.B. auf GitHub Pages)
#    Den gesamten Ordner auf z.B. username.github.io/fdroid-repo/ hosten
#    Auf dem Phone: Repo-URL hinzufügen in F-Droid → Einstellungen → Repos
```

## Gotchas

- `REVEAL_RADIUS = 0.015` is in **kilometers** (15m), passed to `turf.buffer()` `{units: 'kilometers'}`.
- `saveFogPolygon` stores `geometry` object + legacy `coordinates`. Both `updateFogOverlay` and `updateRevealedTrail` handle `Polygon` and `MultiPolygon`.
- `minSdkVersion = 23` (required by `@capacitor/geolocation`). Confirmed in `android/variables.gradle`.
- SW cache cleanup on startup (`main.js:1169-1176`) deletes all caches except `osm-tiles` after APK update.
- Unused deps: `gpx-parse`, `@mapbox/togeojson`, `leaflet-draw` (but its CSS is loaded from CDN).
- `openfog/` is a standalone Android project copy with its own `package.json` / `node_modules` (Capacitor 5.x). `fog-of-world-web/` has Capacitor 8.x. APK is copied into `openfog/OpenFog.apk` after build.
- `applicationId` = `com.openfog.online` (matching namespace `com.openfog.online`). This is F-Droid compatible.
