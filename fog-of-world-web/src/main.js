// Fog of World WebApp - Hauptdatei
import L from 'leaflet';
import 'leaflet-draw';
import { openDB } from 'idb';
import * as turf from '@turf/turf';

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

// ==================== KARTEN-INITIALISIERUNG ====================
let map;
let fogLayer;
let drawnItems;
let currentTrack = { points: [] };
let isTracking = false;
let watchId = null;

function initMap() {
  // Karte initialisieren
  map = L.map('map').setView([51.1657, 10.4515], 6); // Deutschland als Startpunkt
  
  // OpenStreetMap Layer
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
  }).addTo(map);
  
  // Fog Layer (wird später hinzugefügt)
  fogLayer = L.layerGroup().addTo(map);
  
  // Leaflet.Draw für Track-Editor
  drawnItems = new L.FeatureGroup();
  map.addLayer(drawnItems);
  
  const drawControl = new L.Control.Draw({
    edit: { featureGroup: drawnItems },
    draw: {
      polyline: true,
      polygon: false,
      rectangle: false,
      circle: false,
      marker: false
    }
  });
  map.addControl(drawControl);
  
  // Event-Handler für gezeichnete Tracks
  map.on(L.Draw.Event.CREATED, async (e) => {
    const layer = e.layer;
    drawnItems.addLayer(layer);
    
    // Konvertiere zu Track
    const track = convertLeafletLayerToTrack(layer);
    await saveTrack(track);
    
    // Enthülle Nebel entlang des Tracks
    await revealFogAlongTrack(track);
    
    // Aktualisiere UI
    await updateUI();
  });
}

// ==================== FOG LAYER ====================
function updateFogLayer() {
  fogLayer.clearLayers();
  
  // Lade alle Fog-Polygone
  db.getAll('fogPolygons').then(polygons => {
    polygons.forEach(polygon => {
      const geoJSON = {
        type: 'Feature',
        geometry: {
          type: 'Polygon',
          coordinates: polygon.coordinates
        },
        properties: {}
      };
      
      L.geoJSON(geoJSON, {
        style: {
          fillColor: 'rgba(200, 200, 200, 0.7)',
          fillOpacity: 0.7,
          color: 'rgba(200, 200, 200, 0.7)',
          weight: 0
        }
      }).addTo(fogLayer);
    });
  });
}

// ==================== TRACK-FUNKTIONEN ====================
function convertLeafletLayerToTrack(layer) {
  const points = [];
  
  if (layer instanceof L.Polyline) {
    const latlngs = layer.getLatLngs();
    latlngs.forEach(latlng => {
      points.push({
        lat: latlng.lat,
        lng: latlng.lng,
        timestamp: Date.now()
      });
    });
  }
  
  return {
    id: crypto.randomUUID(),
    name: `Track ${Date.now()}`,
    points,
    color: '#3498db',
    width: 3,
    metadata: {
      startTime: Date.now(),
      endTime: Date.now(),
      distance: calculateTrackDistance(points),
      source: 'draw'
    }
  };
}

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
  
  for (const point of track.points) {
    const circle = turf.buffer(
      turf.point([point.lng, point.lat]),
      REVEAL_RADIUS,
      { units: 'kilometers' }
    );
    
    if (!unionPolygon) {
      unionPolygon = circle;
    } else {
      try {
        unionPolygon = turf.union(unionPolygon, circle);
      } catch (e) {
        // Falls Union fehlschlägt (z. B. bei nicht überlappenden Polygonen)
        console.warn('Union fehlgeschlagen, füge Polygon separat hinzu');
        await saveFogPolygon(circle);
        continue;
      }
    }
  }
  
  if (unionPolygon) {
    await saveFogPolygon(unionPolygon);
    
    // Aktualisiere Statistiken
    const area = turf.area(unionPolygon) / 1000000; // in km²
    await updateStats(area, track.metadata.distance / 1000); // distance in km
  }
  
  // Aktualisiere Fog Layer
  updateFogLayer();
}

async function saveFogPolygon(polygon) {
  const tx = db.transaction('fogPolygons', 'readwrite');
  const store = tx.objectStore('fogPolygons');
  
  const area = turf.area(polygon) / 1000000; // in km²
  
  await store.add({
    id: crypto.randomUUID(),
    coordinates: polygon.geometry.coordinates,
    area,
    source: 'track',
    createdAt: Date.now()
  });
  
  await tx.done;
}

// ==================== STATISTIKEN ====================
async function updateStats(newArea, newDistance) {
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
  
  // Aktualisiere Level
  await updateLevel(newArea);
  
  // Prüfe Achievements
  await checkAchievements();
  
  // Aktualisiere UI
  await updateUI();
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
  
  // Statistiken
  if (stats) {
    document.getElementById('revealedArea').textContent = `${stats.totalRevealedArea.toFixed(2)} km²`;
    document.getElementById('revealedPercent').textContent = `${stats.totalRevealedPercent.toFixed(6)}%`;
  }
  
  // Level
  if (level) {
    document.getElementById('currentLevel').textContent = level.currentLevel;
    document.getElementById('currentXP').textContent = `${Math.floor(level.xp)} XP`;
    document.getElementById('nextLevelXP').textContent = `${level.xpForNextLevel} XP`;
    
    const xpProgress = document.getElementById('xpProgress');
    xpProgress.value = level.xp % XP_PER_LEVEL;
    xpProgress.max = XP_PER_LEVEL;
  }
  
  // Achievements
  const achievementsList = document.getElementById('achievementsList');
  achievementsList.innerHTML = achievements
    .sort((a, b) => a.unlocked === b.unlocked ? 0 : a.unlocked ? -1 : 1)
    .map(achievement => `
      <div class="achievement ${achievement.unlocked ? 'unlocked' : 'locked'}">
        <span class="achievement-icon">${achievement.icon}</span>
        <div class="achievement-content">
          <h3>${achievement.name}</h3>
          <p>${achievement.description}</p>
        </div>
      </div>
    `)
    .join('');
}

function toggleSidebar() {
  const sidebar = document.getElementById('sidebar');
  sidebar.classList.toggle('hidden');
}

function showImportModal() {
  document.getElementById('importModal').classList.add('show');
}

function closeImportModal() {
  document.getElementById('importModal').classList.remove('show');
  document.getElementById('fileInput').value = '';
}

// ==================== GPX/KML-IMPORT ====================
async function handleFileImport(event) {
  const file = event.target.files[0];
  if (!file) return;
  
  try {
    const content = await file.text();
    let trackData;
    
    if (file.name.endsWith('.gpx')) {
      trackData = await parseGPXFile(content);
    } else if (file.name.endsWith('.kml')) {
      trackData = await parseKMLFile(content);
    } else {
      alert('Ununterstütztes Dateiformat. Bitte wähle eine GPX- oder KML-Datei.');
      return;
    }
    
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
    closeImportModal();
  }
}

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
  // Parsen der KML-Datei
  const parser = new DOMParser();
  const xmlDoc = parser.parseFromString(content, 'text/xml');
  
  // Extrahiere Track-Punkte
  const points = [];
  const trackName = xmlDoc.querySelector('name')?.textContent || `Track ${Date.now()}`;
  
  const coordinates = xmlDoc.querySelector('coordinates');
  if (coordinates) {
    const coordsText = coordinates.textContent.trim();
    const coords = coordsText.split('\n').map(c => c.trim()).filter(c => c);
    
    coords.forEach(c => {
      const [lng, lat] = c.split(',').map(parseFloat);
      if (!isNaN(lng) && !isNaN(lat)) {
        points.push({
          lat,
          lng,
          timestamp: Date.now()
        });
      }
    });
  }
  
  // Berechne Distanz
  const distance = calculateTrackDistance(points);
  
  return {
    id: crypto.randomUUID(),
    name: trackName,
    points,
    color: getRandomColor(),
    width: 3,
    metadata: {
      startTime: Date.now(),
      endTime: Date.now(),
      distance,
      source: 'kml'
    }
  };
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
  }).addTo(map);
}

// ==================== LIVE-GPS-TRACKING ====================
function startTracking() {
  if (isTracking) return;
  
  if (!navigator.geolocation) {
    alert('Geolocation wird von deinem Browser nicht unterstützt.');
    return;
  }
  
  isTracking = true;
  currentTrack = { points: [] };
  
  document.getElementById('startTrackingBtn').classList.add('hidden');
  document.getElementById('stopTrackingBtn').classList.remove('hidden');
  
  watchId = navigator.geolocation.watchPosition(
    (position) => {
      const { latitude, longitude, accuracy } = position.coords;
      
      currentTrack.points.push({
        lat: latitude,
        lng: longitude,
        timestamp: Date.now(),
        accuracy
      });
      
      // Zeichne Punkt auf der Karte
      L.circleMarker([latitude, longitude], {
        radius: 5,
        fillColor: '#e74c3c',
        color: '#e74c3c',
        weight: 1,
        opacity: 1,
        fillOpacity: 0.8
      }).addTo(map);
      
      // Enthülle Nebel um den aktuellen Punkt
      revealFogAtPoint(latitude, longitude);
    },
    (error) => {
      console.error('GPS-Fehler:', error);
      alert('Fehler beim GPS-Tracking: ' + error.message);
    },
    {
      enableHighAccuracy: true,
      maximumAge: 0,
      timeout: 10000
    }
  );
}

async function revealFogAtPoint(lat, lng) {
  const circle = turf.buffer(
    turf.point([lng, lat]),
    REVEAL_RADIUS,
    { units: 'kilometers' }
  );
  
  await saveFogPolygon(circle);
  
  // Aktualisiere Statistiken (15m Radius = ~0.0007 km²)
  const area = Math.PI * REVEAL_RADIUS * REVEAL_RADIUS;
  await updateStats(area, 0); // Keine Distanz hinzufügen
  
  // Aktualisiere Fog Layer
  updateFogLayer();
}

function stopTracking() {
  if (!isTracking) return;
  
  isTracking = false;
  
  document.getElementById('startTrackingBtn').classList.remove('hidden');
  document.getElementById('stopTrackingBtn').classList.add('hidden');
  
  if (watchId) {
    navigator.geolocation.clearWatch(watchId);
    watchId = null;
  }
  
  // Speichere Track
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
    
    saveTrack(track).then(() => {
      // Enthülle Nebel entlang des gesamten Tracks (für den Fall, dass Punkte zu weit auseinander liegen)
      revealFogAlongTrack(track);
      
      // Zeichne Track auf der Karte
      drawTrackOnMap(track);
      
      // Aktualisiere UI
      updateUI();
    });
  }
  
  currentTrack = { points: [] };
}

// ==================== INITIALISIERUNG ====================
document.addEventListener('DOMContentLoaded', async () => {
  // Initialisiere Datenbank
  await initDB();
  
  // Initialisiere Karte
  initMap();
  
  // Lade bestehende Tracks und zeichne sie
  const tracks = await getAllTracks();
  tracks.forEach(track => drawTrackOnMap(track));
  
  // Lade Fog Layer
  updateFogLayer();
  
  // Aktualisiere UI
  await updateUI();
});

// Export für Debugging
window.db = db;
window.map = map;
