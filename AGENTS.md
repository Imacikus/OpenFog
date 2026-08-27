# AGENTS.md

**OpenFog** – Single-page vanilla JS app + **Capacitor Android App**. Leaflet map, Turf.js, IndexedDB via `idb`. Source in `src/main.js` + `index.html` (inline CSS). Vite `@` alias maps to `src/`.

## Theme (Material Design 3, Dark only)

- **MD3 via design tokens, not a UI framework**: `src/theme.js` generates the full MD3 color scheme from a seed color (`SEED_COLOR = '#6c63ff'`) using `@material/material-color-utilities` (Apache-2.0, ESM-only — Node's bare `require` fails on its extensionless imports, but Vite resolves them fine). `applyScheme()` writes `--md-sys-color-*` vars onto `:root` inline.
- **Dark only — no light mode, no theme switcher**. A light scheme was removed because on-device WebView rasterizes the `.glass` panels with a stale dark first-paint texture (`will-change: transform` layer caching), so light panels never repaint correctly. `theme.js` builds a single `SchemeTonalSpot(source, isDark=true)` scheme and sets `data-theme="dark"` once in `initTheme()`.
- **`index.html` `<style>` defines semantic aliases** (`--accent`, `--text`, `--glass-bg`, `--radius`, …) that reference the `--md-sys-color-*` tokens, plus static dark fallbacks for the first paint before JS runs.
- **Leaflet/canvas colors read via CSS vars at render time** (no hardcoded hex in JS): fog overlay `--fog-overlay` (dark navy), map bg `--map-bg`, track outline `--track-outline`, blue dot / tracking marker use `--md-sys-color-primary` / `--md-sys-color-error`. `cssVar(name, fallback)` helper reads them. `themechange` (fired once on init) re-applies `fogOverlayLayer.setStyle(makeFogStyle())` + `map.invalidateSize()`.
- **No CDN, no native Material You** (wallpaper colors would need native bridge in both Android projects). MD3 component *look* (Navigation Bar pill, FAB, filled/tonal buttons, MD3 switch, cards) is pure CSS in `index.html`.

## Commands

Run from `fog-of-world-web/`:

| Command | Action |
|---------|--------|
| `npm run dev` | Dev server at http://localhost:3000 |
| `npm run build` | Production build to `dist/` |
| `npm run preview` | Preview production build |
| `npx cap sync` | Copy web build to Android project |
| `cd android && ./gradlew assembleDebug` | Build debug APK |

**No tests, no lint, no typecheck** – plain JS, zero test tooling. The only automated verification is `npm run build`.

APK full pipeline (from `fog-of-world-web/`):
```bash
npm run build && npx cap sync && cd android && ./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
```
Release APK for distribution (from `openfog/`): build web app → sync → sign with release key:
```bash
cd ../fog-of-world-web && npm run build && cd ../openfog && npx cap sync && cd android && JAVA_HOME=<jdk> ./gradlew assembleRelease
cp app/build/outputs/apk/release/app-release.apk ../../openfog/OpenFog.apk
```
Release signing reads `openfog/android/keystore.properties` (gitignored); no keystore = unsigned build (F-Droid CI case). F-Droid builds from `openfog/android/` — gradle only, no `npx cap sync` during CI.

## Architecture

- **Entrypoint**: `index.html` → `<script type="module" src="/src/main.js">`
- **Fog reveal**: `buildRevealShape(prev, current)` buffers a `turf.lineString` between consecutive fixes at `REVEAL_RADIUS=0.015`km (15m) → **band shape** saved to `fogPolygons` store (gap-free reveal even at highway speed). Point-circle only for the first fix (`prev == null`) or GPS jumps > `MAX_SEGMENT_KM=0.1`km (100m — no line through unvisited areas). `turf.union()` only for area calculation (stop/import), never for storage.
- **Fog overlay**: viewport-bounds minus revealed via `turf.difference()`. Re-renders on `moveend` (debounced 300ms). Rendered via **canvas renderer** (`L.canvas()`), not SVG — much faster on zoom/pan. One persistent `fogOverlayLayer` updated in place via `setFogData()` (clearLayers + addData) — never removed, so no flicker. Update coalesced: if a compute is running, the latest request is re-run after completion (`fogUpdateQueued`), not dropped. Visible cells are unioned first, then a single `turf.difference` (vs. O(N) diffs on a growing polygon); result simplified at tolerance scaled to current zoom.
- **Union is cached** (`unionCache`): the revealed-cell union is zoom-independent — it only changes when new cells enter the viewport. On pan, only *new* cells are unioned in (Divide-and-Conquer `unionAll`), so a plain pan costs only `diff`+`simplify` (~10-60ms on device vs. 200-260ms recomputing). The simplified union (`unionCache.simplified`/`simplifiedZoom`) is cached per zoom level (tolerance scales with zoom, not pan). Both caches reset via `invalidateFogCache()`/`buildCellCache()`. Measured on MI9: far-zoom startup union=164ms (cache build, one-time), subsequent moves union=1-9ms, total 10-60ms.
- **Grid cell cache** (`buildCellCache`) groups + pre-merges nearby polygons (`turf.union` per grid key, then `turf.simplify`).
- **5 IndexedDB stores**: `fogPolygons`, `tracks`, `achievements`, `userLevel`, `stats`. Version `1` — no migration.
- **Level system**: `XP = area_km2 * 10`, `level = floor(XP / 500) + 1`. 8 achievements checked on stats update.
- **Init** has try-catch: errors shown via visible message inside `#map`. `window.db` and `window.map` exposed as debug globals.

## UI Structure (Bottom Tab Navigation)

- **3 fixed tabs** at bottom (`#tabBar`): Karte / Profil / Einstellungen. Tab switching via `switchTab('map'|'profile'|'settings')`.
- **Tab 1 (Karte)**: transparent panel – map + FAB visible. FAB hidden on other tabs.
- **Tab 2 (Profil)**: scrollable panel with stats (area, %, distance, tracks), level + XP bar, achievements list. Updated by `updateUI()`.
- **Tab 3 (Einstellungen)**: scrollable panel with tracks toggle, GPX/KML/KMZ import, DB export/import, reset buttons. All inline – no modals.
- **No borders on panels**: `#header`, `.stat-row`, `.achievement-card`, `.settings-toggle`, and `.tab-panel-content` all use `border: none` — the `.glass` border was deliberately removed so the dark theme shows no bright separators/lines on-device (WebView rasterizes the `.glass` texture with a light border otherwise).
- **Active tab pill** (`.tab-btn.active::before`): `width: 64px`, `height: 32px`, `top: 16.5px` — centered on the icon (icon center sits 32.5px below button top).

## GPS System

- **One-shot** `gpsGetPosition`: tries `@capacitor/geolocation.getCurrentPosition()` first, falls back to `navigator.geolocation.getCurrentPosition()`.
- **Continuous** `gpsStartWatch`: tries `Geolocation.watchPosition()` with `enableLocationFallback: true` (critical for devices without Play Services), falls back to `navigator.geolocation.watchPosition()`.
- **Watch cadence gotcha**: Capacitor 8's `watchPosition` defaults `interval` to `timeout` → without an explicit `interval`, Android delivers fixes only every ~60s (tracking) / 30s (locateMe). `gpsStartWatch` always sets `interval: opts.interval ?? opts.timeout ?? 5000` and `minimumUpdateInterval` the same way; the `navigator` fallback uses `maximumAge: 0`. Callers: `startTracking()` → `{highAccuracy: true, timeout: 60000, interval: 1000}`; `locateMe()` watch → `{highAccuracy: true, timeout: 30000, interval: 3000}`. Verified on-device: fixes at ~1s (`[TRACK] … dt=~990-1000ms`).
- **Stop latency**: after `clearWatch`, 1-2 queued fixes may still flush (`[TRACK]` logs keep appearing briefly); tracking state is already cleared. `[TRACK]` logs each fix with `dt=…ms`; a `dt=0ms` fix after a tap means a fresh track started (toggle was a stop, not a start).
- **Blue dot** (`locateMe()`): one-shot → marker + center map, then starts a watch. Second click centers map on marker. Blue dot added via `map.add()` directly, so `stopTracking()` does not clear it.
- **Tracking** (`startTracking/stopTracking`): separate GPS watch. `onTrackingLocation` saves segment shapes via `revealFogSegment(lastPt, {lat, lng})` (no live stats update). **Gotcha**: `lastPt` must be captured *before* the new fix is pushed to `currentTrack.points`, else the segment covers the same point twice. Fog cache rebuild is **throttled to ~1/sec** (`fogRebuildTimer` via `scheduleFogRebuild()`) so GPS fixes don't trigger a full DB read + recompute each time. On stop: pending rebuild flushed, union area calculated by iterating `buildRevealShape`, stats updated once, `gpsTrackLayer.clearLayers()`.
- **FAB states**: `searching` (orange spinner) → `tracking` (red pulse). Error recovery resets to default. **Gotcha**: the base `.fab` uses `animation: popIn … forwards` with `opacity: 0`; any state class that sets its own `animation` (e.g. `searching`'s `pulse-ring`) *replaces* `popIn` and drops the button back to `opacity: 0` (invisible while searching). Both `.fab.tracking` and `.fab.searching` must list `popIn 0.4s … forwards` after the pulse keyframe.

## Key Conventions

- **All `onclick="fn()"` in HTML must be exposed on `window`**: every handler needs `window.fnName = fnName` in `main.js`. Exposed: `exportDatabase`, `handleFileImport`, `importDatabase`, `locateMe`, `resetAllData`, `resetFogOnly`, `resetTracksAndStats`, `switchTab`, `startTracking`, `stopTracking`, `toggleTracking`, `toggleTracks`.
- **`updateStats` uses promise-chain mutex** to serialize concurrent writes.

## Android / Capacitor

- **Two Android projects**:
  - `fog-of-world-web/android/` – **dev build target** (Capacitor 8.x). `npx cap sync` writes here, `./gradlew assembleDebug` builds from here.
  - `openfog/android/` – **F-Droid build target** (Capacitor 5.x, `@capacitor/android: ^5.7.8`). Gradle-only build, no `npx cap sync` during build. Web assets are pre-synced **and committed** (gitignored, so re-sync needs `git add -f openfog/android/app/src/main/assets/`).
- `openfog/capacitor.config.json` `webDir` points to `../fog-of-world-web/dist`.
- `minSdkVersion = 23` (required by `@capacitor/geolocation`), in both `android/variables.gradle` files.
- **Splash screen**: dark splash (`#131318` = `--md-sys-color-surface`) with the lilac icon centered at 40% of the smaller dimension. Generated into all `drawable*`/`drawable-port-*`/`drawable-land-*` `splash.png` sizes in both projects. Shown via `AppTheme.NoActionBarLaunch` (`android:background @drawable/splash`) in `styles.xml`. No `@capacitor/splash-screen` plugin — regenerate with a Pillow script (dark canvas, icon scaled to `min(W,H)*0.4`, centered).

## F-Droid

- F-Droid metadata at `metadata/com.openfog.online.yml` (repo root):
  - Build: `commit: 63aec213cd4b9e3b0255e57de04b7943d534b94d`, `subdir: openfog/android`, gradle build. Must be a **full commit hash**, never a tag/branch/short hash.
  - `Binaries` + `AllowedAPKSigningKeys` verify the signed release APK against the GitHub release (`OpenFog.apk`).
  - **Gotcha**: for each new release a new `Builds:` entry (new versionCode + full commit hash) must be added and `CurrentVersion`/`CurrentVersionCode` bumped — else F-Droid won't build it. Releases are git tags (`UpdateCheckMode: Tags`). (Current metadata lists the 1.0.0 and 1.0.1 builds.)
- Fastlane store metadata at `fastlane/metadata/android/en-US/` (repo root) – title, short_description, full_description. Kept out of the yaml on purpose.
- **Versioning / changelog**: current version **1.0.1** (`versionCode 2`). `CHANGELOG.md` at repo root is the single source of release notes; keep it short and simple. Bump `versionName`/`versionCode` in both `android/app/build.gradle` files and both `package.json` files.
- **App icon**: Android launcher icon is a **custom lilac icon** (source JPEG `openfogiconneedtoedit.jpg`, background `#B6A1D1`) — generated into all `mipmap-*` densities as `ic_launcher.png`/`ic_launcher_round.png` plus adaptive-icon `ic_launcher_foreground.png` (image at 66% safe-zone, background color `@color/ic_launcher_background` set to the lilac). Regenerate with a Pillow script (pad source to square, resize per density, foreground at 66%). The web/PWA icon (`fog-of-world-web/public/icons/icon-*.png`) is a **custom purple globe** in seed color `#6C63FF` (same as `SEED_COLOR`).
- `openfog/node_modules/` is **committed** (~2900 files) — `openfog/android/capacitor.settings.gradle` references `../node_modules/@capacitor/android/capacitor`, so the gradle-only F-Droid CI build needs it. Never delete or ignore it.
- No CDN dependencies, no Google Play Services, no Firebase (`openfog/android/build.gradle` has no `google-services` classpath).
- All deps FLOSS (MIT / Apache 2.0).
- `applicationId = com.openfog.online` (all projects).

## Gotchas

- `REVEAL_RADIUS = 0.015` is in **kilometers** (15m), passed to `turf.buffer()` `{units: 'kilometers'}`. `MAX_SEGMENT_KM = 0.1` is also in km (100m) — GPS jumps beyond it reveal only a point, no band.
- `saveFogPolygon` stores `geometry` object + legacy `coordinates`. Both `updateFogOverlay` and `updateRevealedTrail` handle `Polygon` and `MultiPolygon`.
- SW cache cleanup on startup deletes all caches except `osm-tiles` after APK update. **Gotcha**: after installing an updated APK, the WebView may still serve the *stale* `index.html` from the workbox-precache. Fix on-device via CDP: `caches.keys()` → delete all except `osm-tiles`, unregister the service worker, then `Page.reload` with `ignoreCache: true`. Port forward: `adb forward tcp:9222 localabstract:webview_devtools_remote_$(adb shell pidof com.openfog.online | tr -d '\r')`.
- Release keystore lives at `~/.android/openfog-release.keystore`, config in `openfog/android/keystore.properties` (gitignored). Losing the keystore/passwords makes future updates unsignable — back it up. `AllowedAPKSigningKeys` in the metadata = base64 of SHA-256 of the signer public key (NOT the keytool cert fingerprint); recompute via openssl pipeline if the key rotates.
- `npx cap sync` regenerates `openfog/android/app/src/main/assets/{public,capacitor.config.json,capacitor.plugins.json}` + `res/xml/config.xml` — all gitignored; commit them with `git add -f` so the F-Droid build ships the current web build.
