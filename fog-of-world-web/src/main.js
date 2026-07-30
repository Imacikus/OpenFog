// SPDX-License-Identifier: MIT
// OpenFog - Enthülle die Weltkarte durch Reisen
import 'leaflet/dist/leaflet.css';
import '@fortawesome/fontawesome-free/css/all.min.css';
import L from 'leaflet';
import { openDB } from 'idb';
import * as turf from '@turf/turf';
import { Geolocation } from '@capacitor/geolocation';
import JSZip from 'jszip';

// ==================== KONSTANTEN ====================
const WORLD_TOTAL_AREA = 510072000; // km² (Gesamtfläche der Erde)
const REVEAL_RADIUS = 0.015; // 15 Meter in Kilometern
const XP_PER_KM2 = 10; // 10 XP pro enthülltem km²
const XP_PER_LEVEL = 500; // 500 XP pro Level

// ==================== INDEXEDDB SETUP ====================
let db;

async function initDB() {
  db = await openDB('FogOfWorldDB', 1, {
    upgrade(db) {
      // Fog Polygone
      db.createObjectStore('fogPolygons', { keyPath: 'id' });
      
      // Tracks
      db.createObjectStore('tracks', { keyPath: 'id' });
      
      // Achievements
      db.createObjectStore('achievements', { keyPath: 'id' });
      
      // User Level
      db.createObjectStore('userLevel', { keyPath: 'id' });
      
      // Statistiken
      db.createObjectStore('stats', { keyPath: 'id' });
    }
  });
  
  // Initialisiere Standard-Achievements
  await initAchievements();
  
  // Initialisiere User Level
  await initUserLevel();
  
  // Initialisiere Statistiken
  await initStats();
}

// Standard-Achievements mit Font Awesome Icons
const DEFAULT_ACHIEVEMENTS = [
  {
    id: 'first_steps',
    name: 'Erste Schritte',
    description: 'Du hast deine ersten 1.000 Meter enthüllt!',
    icon: '<i class="fas fa-walking"></i>',
    condition: { type: 'distance', target: 1, operator: '>=' },
    unlocked: false,
    unlockedAt: null
  },
  {
    id: 'explorer',
    name: 'Entdecker',
    description: 'Du hast 10 km² der Welt enthüllt!',
    icon: '<i class="fas fa-compass"></i>',
    condition: { type: 'area', target: 10, operator: '>=' },
    unlocked: false,
    unlockedAt: null
  },
  {
    id: 'globetrotter',
    name: 'Weltenbummler',
    description: 'Du hast 0,0001% der Welt erkundet!',
    icon: '<i class="fas fa-globe-americas"></i>',
    condition: { type: 'percent', target: 0.0001, operator: '>=' },
    unlocked: false,
    unlockedAt: null
  },
  {
    id: 'marathon',
    name: 'Marathon',
    description: 'Du hast eine Marathon-Distanz (42,195 km) enthüllt!',
    icon: '<i class="fas fa-running"></i>',
    condition: { type: 'distance', target: 42.195, operator: '>=' },
    unlocked: false,
    unlockedAt: null
  },
  {
    id: 'level_5',
    name: 'Aufsteiger',
    description: 'Du hast Level 5 erreicht!',
    icon: '<i class="fas fa-arrow-up"></i>',
    condition: { type: 'level', target: 5, operator: '>=' },
    unlocked: false,
    unlockedAt: null
  },
  {
    id: 'level_10',
    name: 'Meister',
    description: 'Du hast Level 10 erreicht!',
    icon: '<i class="fas fa-crown"></i>',
    condition: { type: 'level', target: 10, operator: '>=' },
    unlocked: false,
    unlockedAt: null
  },
  {
    id: 'first_track',
    name: 'Erster Track',
    description: 'Du hast deinen ersten Track importiert!',
    icon: '<i class="fas fa-file-import"></i>',
    condition: { type: 'trackCount', target: 1, operator: '>=' },
    unlocked: false,
    unlockedAt: null
  },
  {
    id: 'ten_tracks',
    name: 'Sammler',
    description: 'Du hast 10 Tracks importiert!',
    icon: '<i class="fas fa-folder-open"></i>',
    condition: { type: 'trackCount', target: 10, operator: '>=' },
    unlocked: false,
    unlockedAt: null
  }
];

async function initAchievements() {
  const tx = db.transaction('achievements', 'readwrite');
  const store = tx.objectStore('achievements');
  
  for (const achievement of DEFAULT_ACHIEVEMENTS) {
    const existing = await store.get(achievement.id);
    if (!existing) {
      await store.add(achievement);
    }
  }
  
  await tx.done;
}

async function initUserLevel() {
  const tx = db.transaction('userLevel', 'readwrite');
  const store = tx.objectStore('userLevel');
  
  const existing = await store.get('main');
  if (!existing) {
    await store.add({
      id: 'main',
      currentLevel: 1,
      xp: 0,
      xpForNextLevel: XP_PER_LEVEL
    });
  }
  
  await tx.done;
}

async function initStats() {
  const tx = db.transaction('stats', 'readwrite');
  const store = tx.objectStore('stats');
  
  const existing = await store.get('main');
  if (!existing) {
    await store.add({
      id: 'main',
      totalRevealedArea: 0,
      totalRevealedPercent: 0,
      totalDistance: 0,
      trackCount: 0,
      lastUpdated: Date.now()
    });
  }
  
  await tx.done;
}

async function initDefaultData() {
  await initAchievements();
  await initUserLevel();
  await initStats();
}

// ==================== KARTEN-INITIALISIERUNG ====================
let map;
let fogLayer;
let tracksLayer;
let gpsTrackLayer;
let currentTrack = { points: [] };
let isTracking = false;
let showTracks = false;

async function initMap() {
  window.L = L;
  // Karte initialisieren
  map = L.map('map').setView([51.1657, 10.4515], 6);

  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
  }).addTo(map);

  fogLayer = L.layerGroup().addTo(map);
  tracksLayer = L.layerGroup(); // erstmal unsichtbar
  gpsTrackLayer = L.layerGroup().addTo(map);

  // Fog bei Bewegung neu berechnen (debounced, damit schnelles Schwenken nicht blockiert)
  let fogTimer;
  map.on('moveend', () => {
    clearTimeout(fogTimer);
    fogTimer = setTimeout(() => updateFogOverlay(), 300);
  });
}

// ==================== FOG OVERLAY ====================
let fogOverlayLayer = null;
let fogUpdateGuard = false;
let cachedCells = null; // Array von { polygon, cellX, cellY } – vereinfachte Grid-Zellen

function invalidateFogCache() { cachedCells = null; }

function makeFogStyle() {
  return { color: '#1a1a2e', weight: 0, fillColor: '#1a1a2e', fillOpacity: 0.85, interactive: false };
}

function buildFogBounds() {
  const b = map.getBounds().pad(1.5);
  return turf.bboxPolygon([b.getWest(), b.getSouth(), b.getEast(), b.getNorth()]);
}

function geoToTurf(p) {
  const g = p.geometry || { type: 'Polygon', coordinates: p.coordinates };
  return g.type === 'MultiPolygon' ? turf.multiPolygon(g.coordinates) : turf.polygon(g.coordinates);
}

async function buildCellCache() {
  const polygons = await db.getAll('fogPolygons');
  if (polygons.length === 0) { cachedCells = []; return; }

  const grid = new Map();
  for (const p of polygons) {
    const poly = geoToTurf(p);
    if (!poly) continue;
    const c = poly.geometry.coordinates[0][0];
    const key = `${Math.round(c[0] * 1000)},${Math.round(c[1] * 1000)}`;
    if (!grid.has(key)) grid.set(key, []);
    grid.get(key).push(poly);
  }

  cachedCells = [];
  for (const [, group] of grid) {
    let merged = null;
    for (const poly of group) {
      if (!merged) { merged = poly; continue; }
      try { const m = turf.union(merged, poly); if (m) merged = m; } catch (_) {}
    }
    if (!merged) continue;
    try {
      const s = turf.simplify(merged, { tolerance: 0.00001, highQuality: true });
      if (s) merged = s;
    } catch (_) {}

    const c = merged.geometry.coordinates[0][0];
    cachedCells.push({ polygon: merged, cellX: c[0], cellY: c[1] });
  }

  console.log(`[CACHE] ${polygons.length} → ${cachedCells.length} Zellen`);
}

async function updateFogOverlay() {
  if (!map || fogUpdateGuard) return;
  fogUpdateGuard = true;
  const t0 = performance.now();
  try {
    if (fogOverlayLayer) map.removeLayer(fogOverlayLayer);
    fogOverlayLayer = null;

    if (!cachedCells) await buildCellCache();

    const fogRect = buildFogBounds();
    const fc = fogRect.geometry.coordinates[0];
    const fWest = fc[0][0], fSouth = fc[0][1], fEast = fc[2][0], fNorth = fc[2][1];

    if (cachedCells.length === 0) {
      fogOverlayLayer = L.geoJSON(fogRect, makeFogStyle()).addTo(map);
      return;
    }

    let currentFog = fogRect;
    let cellCount = 0;
    for (const cell of cachedCells) {
      if (cell.cellX > fEast || cell.cellX < fWest || cell.cellY > fNorth || cell.cellY < fSouth) continue;
      if (!turf.booleanIntersects(currentFog, cell.polygon)) continue;
      try {
        const diff = turf.difference(currentFog, cell.polygon);
        if (diff) { currentFog = diff; cellCount++; }
      } catch (_) {}
    }

    const elapsed = (performance.now() - t0).toFixed(1);
    console.log(`[FOG] ${cachedCells.length} Zellen, ${cellCount} diffs (${elapsed}ms)`);

    fogOverlayLayer = L.geoJSON(currentFog, makeFogStyle()).addTo(map);
  } catch (e) {
    console.warn('[FOG] Overlay-Fehler:', e);
    fogOverlayLayer = L.geoJSON(buildFogBounds(), makeFogStyle()).addTo(map);
  } finally {
    fogUpdateGuard = false;
  }
}

function updateRevealedTrail() {
  fogLayer.clearLayers();
  db.getAll('fogPolygons').then(polygons => {
    for (const p of polygons) {
      const g = p.geometry || { type: 'Polygon', coordinates: p.coordinates };
      L.geoJSON({ type: 'Feature', geometry: g, properties: {} }, {
        style: { fillColor: 'transparent', color: 'rgba(255,255,255,0.25)', weight: 1, fillOpacity: 0 }
      }).addTo(fogLayer);
    }
  });
}

async function refreshDisplay() {
  await updateFogOverlay();
}

// ==================== TRACK-FUNKTIONEN ====================
function calculateTrackDistance(points) {
  if (points.length < 2) return 0;
  
  let distance = 0;
  for (let i = 1; i < points.length; i++) {
    const p1 = turf.point([points[i-1].lng, points[i-1].lat]);
    const p2 = turf.point([points[i].lng, points[i].lat]);
    distance += turf.distance(p1, p2, { units: 'kilometers' });
  }
  
  return distance * 1000; // in Meter
}

async function saveTrack(track) {
  const tx = db.transaction('tracks', 'readwrite');
  const store = tx.objectStore('tracks');
  await store.add(track);
  await tx.done;
  
  // Aktualisiere Track-Count in Stats
  await updateTrackCount();
}

async function getAllTracks() {
  return db.getAll('tracks');
}

// ==================== NEBEL-ENTHÜLLUNG ====================
async function revealFogAlongTrack(track) {
  let unionPolygon = null;
  let saved = 0;
  
  for (const point of track.points) {
    const circle = turf.buffer(
      turf.point([point.lng, point.lat]),
      REVEAL_RADIUS,
      { units: 'kilometers' }
    );
    if (!circle) continue;

    await saveFogPolygon(circle);
    saved++;

    if (!unionPolygon) {
      unionPolygon = circle;
    } else {
      try {
        const merged = turf.union(unionPolygon, circle);
        if (merged) unionPolygon = merged;
      } catch (_) {}
    }
  }
  
  invalidateFogCache();
  console.log(`[REVEAL] ${saved} Kreise gespeichert für "${track.name}" (${track.points.length} Punkte)`);
  
  if (unionPolygon) {
    const area = turf.area(unionPolygon) / 1000000;
    console.log(`[REVEAL] Union-Fläche: ${area.toFixed(6)} km²`);
    await updateStats(area, track.metadata.distance / 1000);
  } else {
    console.warn('[REVEAL] unionPolygon ist null – keine Fläche berechnet');
  }
  
  await refreshDisplay();
}

async function saveFogPolygon(polygon) {
  if (!polygon || !polygon.geometry) return;
  const tx = db.transaction('fogPolygons', 'readwrite');
  const store = tx.objectStore('fogPolygons');
  
  const area = turf.area(polygon) / 1000000; // in km²
  
  await store.add({
    id: crypto.randomUUID(),
    geometry: polygon.geometry,
    coordinates: polygon.geometry.coordinates, // backward compat
    area,
    source: 'track',
    createdAt: Date.now()
  });
  
  await tx.done;
}

// ==================== STATISTIKEN ====================
let statsLock = Promise.resolve();

async function updateStats(newArea, newDistance) {
  let release;
  const wait = new Promise(r => release = r);
  const prev = statsLock;
  statsLock = wait;
  await prev;

  try {
    const tx = db.transaction('stats', 'readwrite');
    const store = tx.objectStore('stats');

    const stats = await store.get('main');

    const totalWorldArea = WORLD_TOTAL_AREA;
    const newTotalArea = (stats.totalRevealedArea || 0) + newArea;
    const newTotalDistance = (stats.totalDistance || 0) + newDistance;
    const newPercent = (newTotalArea / totalWorldArea) * 100;

    await store.put({
      ...stats,
      totalRevealedArea: newTotalArea,
      totalRevealedPercent: newPercent,
      totalDistance: newTotalDistance,
      lastUpdated: Date.now()
    });

    await tx.done;

    await updateLevel(newArea);
    await checkAchievements();
    await updateUI();
  } finally {
    release();
  }
}

async function updateTrackCount() {
  const tracks = await db.getAll('tracks');
  const tx = db.transaction('stats', 'readwrite');
  const store = tx.objectStore('stats');
  
  const stats = await store.get('main');
  await store.put({
    ...stats,
    trackCount: tracks.length
  });
  
  await tx.done;
}

// ==================== LEVEL-SYSTEM ====================
async function updateLevel(newArea) {
  const tx = db.transaction('userLevel', 'readwrite');
  const store = tx.objectStore('userLevel');
  
  const levelData = await store.get('main');
  const newXP = levelData.xp + (newArea * XP_PER_KM2);
  const newLevel = Math.floor(newXP / XP_PER_LEVEL) + 1;
  const xpForNextLevel = newLevel * XP_PER_LEVEL;
  
  await store.put({
    ...levelData,
    xp: newXP,
    currentLevel: newLevel,
    xpForNextLevel
  });
  
  await tx.done;
}

// ==================== ACHIEVEMENT-SYSTEM ====================
async function checkAchievements() {
  const stats = await db.get('stats', 'main');
  const level = await db.get('userLevel', 'main');
  const tracks = await db.getAll('tracks');
  const achievements = await db.getAll('achievements');
  
  for (const achievement of achievements) {
    if (!achievement.unlocked) {
      let isUnlocked = false;
      
      switch (achievement.condition.type) {
        case 'area':
          isUnlocked = stats.totalRevealedArea >= achievement.condition.target;
          break;
        case 'percent':
          isUnlocked = stats.totalRevealedPercent >= achievement.condition.target;
          break;
        case 'distance':
          isUnlocked = stats.totalDistance >= achievement.condition.target;
          break;
        case 'level':
          isUnlocked = level.currentLevel >= achievement.condition.target;
          break;
        case 'trackCount':
          isUnlocked = tracks.length >= achievement.condition.target;
          break;
      }
      
      if (isUnlocked) {
        const tx = db.transaction('achievements', 'readwrite');
        const store = tx.objectStore('achievements');
        achievement.unlocked = true;
        achievement.unlockedAt = Date.now();
        await store.put(achievement);
        await tx.done;
        
        // Zeige Benachrichtigung
        showNotification(`🎉 Erfolg freigeschaltet: ${achievement.name}!`);
      }
    }
  }
  
  // Aktualisiere UI
  await updateUI();
}

function showNotification(message) {
  // Einfache Benachrichtigung (kann später durch Toast ersetzt werden)
  alert(message);
}

// ==================== UI-FUNKTIONEN ====================
async function updateUI() {
  const stats = await db.get('stats', 'main');
  const level = await db.get('userLevel', 'main');
  const achievements = await db.getAll('achievements');
  
  // Gemeinsame Formatierung
  const fmtArea = v => `${v.toFixed(2)} km²`;
  const fmtPct = v => `${v.toFixed(6)} %`;
  const fmtDist = v => `${v.toFixed(2)} km`;
  
  if (stats) {
    const a = fmtArea(stats.totalRevealedArea);
    const p = fmtPct(stats.totalRevealedPercent);
    const d = fmtDist(stats.totalDistance);
    const tc = stats.trackCount || 0;
    
    document.getElementById('profileArea').textContent = a;
    document.getElementById('profilePercent').textContent = p;
    document.getElementById('profileDistance').textContent = d;
    document.getElementById('profileTrackCount').textContent = tc;
  }
  
  if (level) {
    const xpInLevel = level.xp % XP_PER_LEVEL;
    const pct = (xpInLevel / XP_PER_LEVEL) * 100;
    
    document.getElementById('profileLevel').textContent = level.currentLevel;
    document.getElementById('profileXpBarFill').style.width = `${pct}%`;
    document.getElementById('profileXP').textContent = Math.floor(level.xp);
    document.getElementById('profileNextXP').textContent = level.xpForNextLevel;
  }
  
  // Achievements
  const html = achievements
    .sort((a, b) => a.unlocked === b.unlocked ? 0 : a.unlocked ? -1 : 1)
    .map(a => `
      <div class="achievement-card ${a.unlocked ? 'unlocked' : 'locked'}">
        <div class="achievement-icon">${a.icon}</div>
        <div class="achievement-info">
          <h4>${a.name}</h4>
          <p>${a.description}</p>
        </div>
      </div>
    `)
    .join('');
  
  document.getElementById('profileAchievementsList').innerHTML = html;
}

// ==================== EXPORT FÜR HTML-ONCLICK ====================
// Module-Script-Funktionen sind nicht global sichtbar — daher explizit auf window heben

function switchTab(tab) {
  document.querySelectorAll('.tab-panel').forEach(p => p.classList.remove('active'));
  document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
  document.getElementById('panel' + tab.charAt(0).toUpperCase() + tab.slice(1)).classList.add('active');
  document.querySelector(`.tab-btn[onclick*="'${tab}'"]`).classList.add('active');
  const fab = document.getElementById('fabTrack');
  if (tab === 'map') { fab.style.display = 'flex'; } else { fab.style.display = 'none'; }
}
window.switchTab = switchTab;

function toggleTracking() {
  if (isTracking) { stopTracking(); } else { startTracking(); }
}
window.toggleTracking = toggleTracking;

// ==================== GPX/KML-IMPORT ====================
async function handleFileImport(event) {
  const file = event.target.files[0];
  if (!file) return;
  
  try {
    let trackData;
    
    if (file.name.endsWith('.kmz')) {
      trackData = await parseKMZFile(file);
    } else if (file.name.endsWith('.gpx') || file.name.endsWith('.kml')) {
      const content = await file.text();
      trackData = file.name.endsWith('.gpx')
        ? await parseGPXFile(content)
        : await parseKMLFile(content);
    } else {
      alert('Ununterstütztes Dateiformat. Bitte wähle eine GPX-, KML- oder KMZ-Datei.');
      return;
    }
    
    console.log(`[IMPORT] "${file.name}" gelesen: ${trackData.points.length} Punkte, ${(trackData.metadata.distance / 1000).toFixed(1)} km`);
    
    // Speichere Track
    await saveTrack(trackData);
    
    // Enthülle Nebel entlang des Tracks
    await revealFogAlongTrack(trackData);
    
    // Zeichne Track auf der Karte
    drawTrackOnMap(trackData);
    
    // Aktualisiere UI
    await updateUI();
    
    alert(`Track "${trackData.name}" erfolgreich importiert!`);
  } catch (error) {
    console.error('Fehler beim Import:', error);
    alert('Fehler beim Import: ' + error.message);
  } finally {
    document.getElementById('fileInput').value = '';
  }
}
window.handleFileImport = handleFileImport;

async function parseGPXFile(content) {
  // Parsen der GPX-Datei
  const parser = new DOMParser();
  const xmlDoc = parser.parseFromString(content, 'text/xml');
  
  // Extrahiere Track-Punkte
  const points = [];
  const trackName = xmlDoc.querySelector('name')?.textContent || `Track ${Date.now()}`;
  
  const trkpts = xmlDoc.querySelectorAll('trkpt');
  trkpts.forEach(pt => {
    const lat = parseFloat(pt.getAttribute('lat'));
    const lon = parseFloat(pt.getAttribute('lon'));
    const time = pt.querySelector('time')?.textContent;
    
    points.push({
      lat,
      lng: lon,
      timestamp: time ? new Date(time).getTime() : Date.now()
    });
  });
  
  // Berechne Distanz
  const distance = calculateTrackDistance(points);
  
  return {
    id: crypto.randomUUID(),
    name: trackName,
    points,
    color: getRandomColor(),
    width: 3,
    metadata: {
      startTime: points[0]?.timestamp || Date.now(),
      endTime: points[points.length - 1]?.timestamp || Date.now(),
      distance,
      source: 'gpx'
    }
  };
}

async function parseKMLFile(content) {
  const parser = new DOMParser();
  const xmlDoc = parser.parseFromString(content, 'text/xml');
  
  const points = [];
  const trackName = xmlDoc.querySelector('name')?.textContent || `Track ${Date.now()}`;
  
  // Variante A: <coordinates> (LineString/LinearRing)
  const coordEls = xmlDoc.querySelectorAll('coordinates');
  if (coordEls.length) {
    coordEls.forEach(el => {
      el.textContent.trim().split(/\s+/).forEach(block => {
        const parts = block.trim().split(',');
        const lng = parseFloat(parts[0]);
        const lat = parseFloat(parts[1]);
        if (!isNaN(lng) && !isNaN(lat)) {
          points.push({ lat, lng, timestamp: Date.now() });
        }
      });
    });
  }
  
  // Variante B: <gx:Track> / <gx:coord>
  const gxCoords = xmlDoc.querySelectorAll('gx\\:coord, coord');
  if (gxCoords.length) {
    gxCoords.forEach(el => {
      const parts = el.textContent.trim().split(/\s+/);
      const lng = parseFloat(parts[0]);
      const lat = parseFloat(parts[1]);
      if (!isNaN(lng) && !isNaN(lat)) {
        const when = el.previousElementSibling?.textContent?.trim?.();
        points.push({ lat, lng, timestamp: when ? new Date(when).getTime() : Date.now() });
      }
    });
  }
  
  console.log(`[KML] "${trackName}": ${coordEls.length} <coordinates>, ${gxCoords.length} <gx:coord> → ${points.length} Punkte`);
  
  const distance = calculateTrackDistance(points);
  
  return {
    id: crypto.randomUUID(),
    name: trackName,
    points,
    color: getRandomColor(),
    width: 3,
    metadata: {
      startTime: points[0]?.timestamp || Date.now(),
      endTime: points[points.length - 1]?.timestamp || Date.now(),
      distance,
      source: 'kml'
    }
  };
}

async function parseKMZFile(file) {
  const zip = await JSZip.loadAsync(await file.arrayBuffer());
  const fileList = Object.keys(zip.files);
  console.log('[KMZ] Dateien im Archiv:', fileList);
  const kmlFile = fileList.find(f => f.endsWith('.kml'));
  if (!kmlFile) throw new Error('Keine KML-Datei im KMZ-Archiv gefunden.');
  console.log(`[KMZ] Gefundene KML: "${kmlFile}"`);
  const content = await zip.files[kmlFile].async('text');
  return parseKMLFile(content);
}

function getRandomColor() {
  const colors = ['#3498db', '#e74c3c', '#2ecc71', '#f39c12', '#9b59b6', '#1abc9c'];
  return colors[Math.floor(Math.random() * colors.length)];
}

function drawTrackOnMap(track) {
  const latlngs = track.points.map(p => [p.lat, p.lng]);
  L.polyline(latlngs, {
    color: track.color,
    weight: track.width,
    opacity: 0.8
  }).addTo(tracksLayer);
}

// ==================== DATENBANK-BACKUP ====================
const DB_STORES = ['fogPolygons', 'tracks', 'achievements', 'userLevel', 'stats'];

async function exportDatabase() {
  if (!db) { alert('Datenbank noch nicht bereit.'); return; }
  try {
    const backup = {};
    for (const store of DB_STORES) {
      backup[store] = await db.getAll(store);
    }
    backup._exportedAt = new Date().toISOString();
    backup._version = 1;

    const blob = new Blob([JSON.stringify(backup, null, 2)], { type: 'application/json' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `fog-of-world-backup-${Date.now()}.json`;
    a.click();
    URL.revokeObjectURL(a.href);
    alert('Daten exportiert!');
  } catch (e) {
    alert('Export fehlgeschlagen: ' + e.message);
  }
}
window.exportDatabase = exportDatabase;

async function importDatabase(event) {
  const file = event.target.files[0];
  if (!file) return;
  try {
    const text = await file.text();
    const backup = JSON.parse(text);
    if (!backup._version) { alert('Keine gültige Backup-Datei.'); return; }

    if (!confirm('Existierende Daten werden überschrieben. Fortfahren?')) { document.getElementById('dbFileInput').value = ''; return; }

    const tx = db.transaction(DB_STORES, 'readwrite');
    for (const store of DB_STORES) {
      const os = tx.objectStore(store);
      await os.clear();
      const items = backup[store] || [];
      for (const item of items) {
        await os.add(item);
      }
    }
    await tx.done;

    document.getElementById('dbFileInput').value = '';
    invalidateFogCache();
    await loadAndRefresh();
    alert('Daten erfolgreich wiederhergestellt!');
  } catch (e) {
    alert('Import fehlgeschlagen: ' + e.message);
  }
}
window.importDatabase = importDatabase;

async function loadAndRefresh() {
  tracksLayer.clearLayers();
  const tracks = await getAllTracks();
  tracks.forEach(t => drawTrackOnMap(t));
  await refreshDisplay();
  await updateUI();
}

// ==================== GPS-HELPER ====================
async function gpsGetPosition(timeout) {
  let cap = false;
  try { cap = !!(window.Capacitor?.isNativePlatform?.()); } catch (_) {}

  if (cap) {
    try {
      const p = await Geolocation.checkPermissions();
      if (p.location === 'denied' || p.location === 'prompt') {
        await Geolocation.requestPermissions();
      }
    } catch (_) {}

    try {
      return await Geolocation.getCurrentPosition({ enableHighAccuracy: true, timeout });
    } catch (e) {
      console.warn('Capacitor getCurrentPosition fehlgeschlagen:', e);
    }
  }
  if (!navigator.geolocation) throw new Error('navigator.geolocation nicht verfügbar');
  return new Promise((resolve, reject) =>
    navigator.geolocation.getCurrentPosition(resolve, reject, { enableHighAccuracy: true, timeout, maximumAge: 0 })
  );
}

// Versuche Capacitor Geolocation, fallback auf navigator.geolocation
async function gpsStartWatch(opts, onPosition, onError) {
  let cap = false;
  try { cap = !!(window.Capacitor?.isNativePlatform?.()); } catch (_) {}

  if (cap) {
    try {
      const p = await Geolocation.checkPermissions();
      if (p.location === 'denied' || p.location === 'prompt') {
        await Geolocation.requestPermissions();
      }
    } catch (_) {}

    try {
      const id = await Geolocation.watchPosition(
        { enableHighAccuracy: opts.highAccuracy || false, timeout: opts.timeout || 30000, enableLocationFallback: true },
        (pos, err) => {
          if (err) { onError(err); return; }
          onPosition(pos);
        }
      );
      return { clear: () => { try { Geolocation.clearWatch({ id }); } catch (_) {} } };
    } catch (e) {
      console.warn('Capacitor Geolocation fehlgeschlagen, Fallback auf navigator.geolocation:', e);
    }
  }

  if (!navigator.geolocation) throw new Error('navigator.geolocation nicht verfügbar');

  const watchId = navigator.geolocation.watchPosition(
    (pos) => onPosition(pos),
    (err) => onError(err),
    { enableHighAccuracy: opts.highAccuracy || false, timeout: opts.timeout || 30000, maximumAge: 30000 }
  );
  return { clear: () => navigator.geolocation.clearWatch(watchId) };
}

// ==================== STANDORT (Blauer Punkt) ====================
let myLocationMarker = null;
let myLocationWatcher = null;

async function locateMe() {
  if (!map) return;

  if (myLocationWatcher && myLocationMarker) {
    map.setView(myLocationMarker.getLatLng(), 16);
    return;
  }

  // 1. Sofort eine Position holen (Marker anzeigen + Karte zentrieren)
  try {
    const pos = await gpsGetPosition(15000);
    const { latitude: lat, longitude: lng } = pos.coords;
    if (!myLocationMarker) {
      myLocationMarker = L.circleMarker([lat, lng], {
        radius: 8,
        fillColor: '#3498db',
        color: '#ffffff',
        weight: 2,
        fillOpacity: 0.9
      }).addTo(map);
      map.setView([lat, lng], 16);
    }
  } catch (e) {
    console.warn('Initiale Position fehlgeschlagen, starte Watch:', e);
  }

  // 2. Watch für kontinuierliche Aktualisierung
  try {
    myLocationWatcher = await gpsStartWatch(
      { highAccuracy: true, timeout: 30000 },
      (pos) => {
        const { latitude: lat, longitude: lng } = pos.coords;
        if (!myLocationMarker) {
          myLocationMarker = L.circleMarker([lat, lng], {
            radius: 8,
            fillColor: '#3498db',
            color: '#ffffff',
            weight: 2,
            fillOpacity: 0.9
          }).addTo(map);
          map.setView([lat, lng], 16);
        } else {
          myLocationMarker.setLatLng([lat, lng]);
        }
      },
      (err) => console.warn('Standort-Fehler:', err.message || err.code || err)
    );
  } catch (e) {
    if (!myLocationMarker) {
      alert('GPS nicht verfügbar: ' + (e.message || e));
    }
  }
}
window.locateMe = locateMe;

// ==================== LIVE-GPS-TRACKING ====================
let trackingWatcher = null;
let trackingFirstFix = true;

async function startTracking() {
  if (!map || isTracking) return;

  isTracking = true;
  currentTrack = { points: [] };
  trackingFirstFix = true;

  const fab = document.getElementById('fabTrack');
  fab.classList.add('searching');
  fab.innerHTML = '<i class="fas fa-spinner fa-pulse"></i>';
  fab.title = 'Suche GPS-Signal...';

  try {
    trackingWatcher = await gpsStartWatch(
      { highAccuracy: true, timeout: 60000 },
      onTrackingLocation,
      onTrackingError
    );
  } catch (e) {
    console.error('Tracking-Start fehlgeschlagen:', e);
    isTracking = false;
    fab.classList.remove('searching');
    fab.innerHTML = '<i class="fas fa-map-marker-alt"></i>';
    fab.title = 'Tracking starten';
    alert('GPS starten fehlgeschlagen: ' + (e.message || e));
  }
}

async function onTrackingLocation(pos) {
  const lat = pos.coords.latitude;
  const lng = pos.coords.longitude;
  const accuracy = pos.coords.accuracy || 0;

  currentTrack.points.push({ lat, lng, timestamp: Date.now(), accuracy });

  const marker = L.circleMarker([lat, lng], {
    radius: 8,
    fillColor: '#ef4444',
    color: '#ffffff',
    weight: 2,
    opacity: 1,
    fillOpacity: 0.85
  }).addTo(gpsTrackLayer);

  if (trackingFirstFix) {
    trackingFirstFix = false;
    const fab = document.getElementById('fabTrack');
    fab.classList.remove('searching');
    fab.classList.add('tracking');
    fab.innerHTML = '<i class="fas fa-stop"></i>';
    fab.title = 'Tracking stoppen';
    map.setView([lat, lng], 16);
    marker.bindPopup('Tracking aktiv').openPopup();
  }

  try {
    await revealFogAtPoint(lat, lng);
  } catch (e) {
    console.error('Fehler beim Fog-Reveal:', e);
  }
}

function onTrackingError(err) {
  const msg = err.message || err.error?.message || String(err);
  if (msg.includes('denied') || msg.includes('permission')) {
    alert('GPS-Zugriff verweigert. Bitte Standortzugriff in den App-Einstellungen erlauben.');
    stopTracking();
  } else if (msg.includes('timeout') || msg.includes('TIMEOUT')) {
    console.warn('GPS sucht noch nach Signal…');
  } else {
    console.error('GPS-Fehler:', msg);
  }
}

async function revealFogAtPoint(lat, lng) {
  const circle = turf.buffer(turf.point([lng, lat]), REVEAL_RADIUS, { units: 'kilometers' });
  if (!circle) return;

  await saveFogPolygon(circle);
  invalidateFogCache();
  await refreshDisplay();
}

async function stopTracking() {
  if (!isTracking) return;
  isTracking = false;

  document.getElementById('fabTrack').classList.remove('tracking', 'searching');
  document.getElementById('fabTrack').innerHTML = '<i class="fas fa-map-marker-alt"></i>';
  document.getElementById('fabTrack').title = 'Tracking starten';

  gpsTrackLayer.clearLayers();

  if (trackingWatcher) {
    try { trackingWatcher.clear(); } catch (_) {}
    trackingWatcher = null;
  }
  trackingFirstFix = true;

  if (currentTrack.points.length > 0) {
    const track = {
      id: crypto.randomUUID(),
      name: `Track ${Date.now()}`,
      points: currentTrack.points,
      color: getRandomColor(),
      width: 3,
      metadata: {
        startTime: currentTrack.points[0].timestamp,
        endTime: currentTrack.points[currentTrack.points.length - 1].timestamp,
        distance: calculateTrackDistance(currentTrack.points),
        source: 'live'
      }
    };
    await saveTrack(track);

    // Union-Fläche berechnen (korrekt, keine Doppelzählung)
    let unionPolygon = null;
    for (const pt of track.points) {
      const circle = turf.buffer(turf.point([pt.lng, pt.lat]), REVEAL_RADIUS, { units: 'kilometers' });
      if (!circle) continue;
      if (!unionPolygon) { unionPolygon = circle; continue; }
      try { const m = turf.union(unionPolygon, circle); if (m) unionPolygon = m; } catch (_) {}
    }
    if (unionPolygon) {
      const area = turf.area(unionPolygon) / 1000000;
      await updateStats(area, track.metadata.distance / 1000);
    }

    drawTrackOnMap(track);
    await updateUI();
  }

  currentTrack = { points: [] };
}
window.startTracking = startTracking;
window.stopTracking = stopTracking;

// ==================== DATEN ZURÜCKSETZEN ====================
async function resetAllData() {
  if (!confirm('WIRKLICH alles zurücksetzen? Fog, Tracks, Achievements, Level und Statistiken werden gelöscht.')) return;
  await clearStore('fogPolygons');
  await clearStore('tracks');
  await clearStore('achievements');
  await clearStore('userLevel');
  await clearStore('stats');
  await initDefaultData();
  invalidateFogCache();
  tracksLayer.clearLayers();
  await loadAndRefresh();
  alert('Alle Daten wurden zurückgesetzt.');
}
window.resetAllData = resetAllData;

async function resetTracksAndStats() {
  if (!confirm('Tracks, Achievements, Level und Statistiken zurücksetzen? Der Nebel bleibt erhalten.')) return;
  await clearStore('tracks');
  await clearStore('achievements');
  await clearStore('userLevel');
  await clearStore('stats');
  await initDefaultData();
  tracksLayer.clearLayers();
  await loadAndRefresh();
  alert('Tracks und Statistik zurückgesetzt.');
}
window.resetTracksAndStats = resetTracksAndStats;

async function resetFogOnly() {
  if (!confirm('Gesamten Nebel zurücksetzen? Alle enthüllten Flächen werden gelöscht.')) return;
  await clearStore('fogPolygons');
  invalidateFogCache();
  await loadAndRefresh();
  alert('Nebel zurückgesetzt.');
}
window.resetFogOnly = resetFogOnly;

async function clearStore(storeName) {
  const tx = db.transaction(storeName, 'readwrite');
  await tx.objectStore(storeName).clear();
  await tx.done;
}

function toggleTracks() {
  showTracks = !showTracks;
  const checkbox = document.getElementById('toggleTracks');
  if (checkbox) checkbox.checked = showTracks;
  if (showTracks) {
    map.addLayer(tracksLayer);
  } else {
    map.removeLayer(tracksLayer);
  }
}
window.toggleTracks = toggleTracks;

// ==================== INITIALISIERUNG ====================
document.addEventListener('DOMContentLoaded', async () => {
  // Unbehandelte Promise-Fehler abfangen
  window.addEventListener('unhandledrejection', (e) => {
    console.error('Unbehandelter Promise-Fehler:', e.reason?.message || e.reason);
  });

  // Alte Service-Worker-Caches bereinigen (nach APK-Update)
  try {
    const reg = await navigator.serviceWorker?.getRegistration();
    if (reg?.active) {
      const keys = await caches.keys();
      await Promise.all(keys.filter(k => k !== 'osm-tiles').map(k => caches.delete(k)));
    }
  } catch (_) {}

  try {
    await initDB();
    await initMap();

    const tracks = await getAllTracks();
    tracks.forEach(track => drawTrackOnMap(track));

    await refreshDisplay();
    await updateUI();

    window.db = db;
    window.map = map;
  } catch (e) {
    console.error('Fehler beim Initialisieren der App:', e);
    document.getElementById('map').innerHTML = `<div class="map-error">
      <h2><i class="fas fa-exclamation-triangle"></i> Fehler beim Laden</h2>
      <p>${e.message}</p>
      <p><small>Öffne die Browser-Konsole (F12) für Details.</small></p>
    </div>`;
  }
});
