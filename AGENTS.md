# AGENTS.md

Single-page browser app (vanilla JS, no framework). Leaflet map, Turf.js geospatial, IndexedDB via `idb`. All source in one file + inline CSS. No tests, no linter, no formatter.

## Commands (run from `fog-of-world-web/`)

| Command | Action |
|---------|--------|
| `npm install` | Install dependencies |
| `npm run dev` | Dev server at http://localhost:3000 |
| `npm run build` | Production build to `dist/` |

No other scripts exist. Verify changes manually via `npm run dev`.

## Key files

- `fog-of-world-web/index.html` — entry point + inline CSS + HTML (385 lines)
- `fog-of-world-web/src/main.js` — all app logic (1016 lines)
- `fog-of-world-web/vite.config.js` — Vite + PWA plugin

## UI Architecture

### Design System (Dark Theme, Map-First)

- **Theme**: Dark (`#1a1a2e` bg, `#6c63ff` accent). No light mode toggle.
- **Glassmorphism**: Panels use `backdrop-filter: blur(16px)` + `rgba(22,33,62,0.78)` background via `.glass` class.
- **Map**: Fixed full-viewport (`position: fixed; inset: 0`), z-index 0. All UI floats above.
- **Responsive**: Single breakpoint at 768px.
  - **Mobile (< 768px)**: Glass header (48px), Bottom Sheet for stats, FAB for tracking.
  - **Desktop (≥ 768px)**: Glass header with more buttons, collapsed sidebar (52px → 260px on hover/click), FAB.

### Layout Components

| Component | Mobile | Desktop |
|-----------|--------|---------|
| **Header** (`#header`) | Fixed top, 48px. App name + Locate + Stats toggle | Same + Import + DB buttons |
| **FAB** (`#fabTrack`) | Bottom-right, 88px from bottom (above bottom sheet). Toggles GPS tracking | Bottom-right, 24px |
| **Bottom Sheet** (`#bottomSheet`) | Fixed bottom, 68px preview always visible. Tap to expand (70vh max). Contains all stats + achievements | Hidden (`display: none`) |
| **Desktop Sidebar** (`#sidebarDesktop`) | Hidden | Fixed left-center, collapsed (52px, icons only). Click to expand (260px) with full stats |
| **Modals** (`#importModal`, `#dbModal`) | Fullscreen overlay with glass content, `backdrop-filter: blur(4px)` | Centered dialog, max 380px |

### Animations (all CSS, ~90 lines)

| Animation | Trigger | Effect |
|-----------|---------|--------|
| `fadeSlideDown` | Page load (0.1s delay) | Header slides in from top |
| `popIn` | Page load (0.2s delay) | FAB scales in from 0.5 → 1 |
| `sheetSlideUp` | Page load (0.3s delay) | Bottom sheet slides up from lower position |
| `pulse-ring` | Tracking active | FAB gets pulsing red glow (`@keyframes pulse-ring`) |
| Bottom sheet expand | Tap preview | `transform: translateY(0)` with `0.4s cubic-bezier(0.32, 0.72, 0, 1)` |
| Bottom sheet collapse | Tap preview | Reverse animation |
| Achievement cards | Sheet expands | Staggered fade-in (8 items, 0.05s delay each) |
| Sidebar expand | Click (desktop) | Width 52px → 260px, content fades in |
| Modals | Open/close | Overlay fades in, content scales from 0.92 + translateY(16px) |
| Header buttons | Hover/active | Background tint + translateY(-1px) / scale(0.92) |

### CSS Structure (~200 lines inline in `<style>`)

- Custom properties for theming (`--fog-bg`, `--glass-bg`, `--accent`, `--ease-out`, etc.)
- `.glass` utility class for glassmorphism
- Responsive via `@media (min-width: 768px)`
- Helper classes `.mobile-only` / `.desktop-only` for element visibility per breakpoint
- Leaflet overrides for dark theme (zoom controls, attribution)
- Custom scrollbar styling

## Architecture

- **Entrypoint**: `index.html` → `<script type="module" src="/src/main.js">`
- **Fog reveal**: buffer each track point at 15m → save each circle individually to `fogPolygons` store (same approach for GPS and imports). `turf.union()` is used only for area calculation, not for storage.
- **Fog overlay**: dark (`#1a1a2e`, 85%) viewport-bounds polygon minus revealed areas via `turf.difference`. Re-renders on `map.on('moveend')`. Guarded by `fogUpdateGuard` to prevent concurrent execution.
- **5 IndexedDB stores**: `fogPolygons`, `tracks`, `achievements`, `userLevel`, `stats`
- **8 default achievements** in `DEFAULT_ACHIEVEMENTS` array (`main.js:47-119`). Checks: area, percent, distance, level, trackCount.
- **Level system**: `XP = area_km2 * 10`, `level = floor(XP / 500) + 1`
- **World area constant**: `WORLD_TOTAL_AREA = 510072000` km²

## Key conventions

- **All HTML-called functions must be on `window`**: every `onclick` handler in `index.html` needs a `window.fnName = fnName` assignment in `main.js`. Current exposed fns: `toggleSidebar`, `toggleBottomSheet`, `toggleDesktopSidebar`, `toggleTracking`, `showImportModal`, `closeImportModal`, `handleFileImport`, `locateMe`, `startTracking`, `stopTracking`, `showDBModal`, `closeDBModal`, `exportDatabase`, `importDatabase`.
- **`leaflet-draw` is loaded dynamically**: `await import('leaflet-draw')` inside `initMap()`, not as static import. Static import fails in ESM + CommonJS boundary. `window.L = L` is set beforehand for plugin compatibility.
- **No track layer group**: `drawTrackOnMap()` adds polylines to `tracksLayer` (a `L.layerGroup`). Clear it before redrawing on import/refresh.
- **GPS markers in layer group**: GPS tracking adds circle markers to `gpsTrackLayer` (a `L.layerGroup`). Cleared on `stopTracking()` to prevent memory leaks.

## Gotchas

- **Unused deps**: `gpx-parse` and `@mapbox/togeojson` in package.json are **not imported**. GPX/KML parsing uses raw `DOMParser`. `jszip` is imported dynamically for `.kmz` files.
- **`REVEAL_RADIUS = 0.015` is in kilometers** (15m). Passed to Turf `buffer()` with `{units: 'kilometers'}`.
- **GPS tracking doesn't accumulate distance**: `revealFogAtPoint()` passes `0` for distance to `updateStats()`.
- **KMZ File-Read**: `handleFileImport` liest nur GPX/KML via `file.text()`. KMZ bekommt das `file`-Objekt direkt für `arrayBuffer()` (kein doppelter Read).
- **OSM tile caching broken**: Workbox regex `{s}` is a Leaflet template variable, not a regex pattern. Tiles are not cached offline.
- **Missing icon files**: `public/icons/icon-{192,512}x{192,512}.png` don't exist but are referenced in `manifest.json`.
- **No DB migration**: IndexedDB version is hardcoded `1` in `openDB('FogOfWorldDB', 1)`.
- **Debug globals**: `window.db` and `window.map` are exposed after init.
- **Map center**: Germany `[51.1657, 10.4515]` zoom 6.
- **`saveFogPolygon` stores full `geometry` object** (with `type`). Both `updateFogOverlay` and `updateRevealedTrail` handle `Polygon` and `MultiPolygon`. Old entries with only `coordinates` (no `geometry.type`) are still supported via fallback.
- **Union only for area**: `revealFogAlongTrack` und `revealFogAtPoint` speichern jeden 15m-Kreis einzeln in `fogPolygons`. `turf.union()` dient nur der exakten Flächenberechnung, nicht der Speicherung.
- **Init has try-catch**: errors show a visible message inside the `#map` div instead of crashing silently.
