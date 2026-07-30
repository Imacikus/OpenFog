# OpenFog

Reveal the world map by traveling. GPS-basierte Erkundungs-App, die die Weltkarte unter einem Nebel verbirgt und nach und nach freischaltet, während du dich bewegst.

## Features

- **Nebel-Mechanik**: Die Karte ist anfangs komplett verdeckt – jeder besuchte Ort lichtet den Nebel in einem Radius von 15m
- **GPS-Tracking**: Zeichne Routen auf und schalte beim Gehen/Fahren Nebel frei
- **GPX/KML/KMZ-Import**: Bestehende Tracks importieren, um zurückgelegte Gebiete nachträglich zu enthüllen
- **Level-System**: XP = enthüllte Fläche × 10, Level = XP / 500 + 1
- **8 Erfolge**: Vom ersten Schritt bis zum Weltentdecker
- **Statistiken**: Zurückgelegte Fläche, Strecken, Distanz, Level
- **Offline-fähig**: Läuft komplett ohne Internet über Service Worker Cache
- **Kein Tracking, keine Werbung, keine Analytics**

## Aufbau

```
OpenFog/
├── fog-of-world-web/   # Web-App (Vite + Vanilla JS + Leaflet)
│   ├── src/main.js     # Hauptlogik (~1180 Zeilen)
│   ├── index.html      # Einstieg, inline CSS (~750 Zeilen)
│   └── android/        # Capacitor Android Projekt
├── openfog/            # Eigenständiges Android-Projekt
├── LICENSE             # MIT
├── AGENTS.md           # Entwickler-Dokumentation
└── README.md
```

## Entwicklung

```bash
cd fog-of-world-web
npm install
npm run dev        # Dev-Server :3000
npm run build      # Produktions-Build → dist/
```

### APK bauen

```bash
cd fog-of-world-web
npm run build
npx cap sync
cd android
./gradlew assembleDebug
```

## Lizenz

MIT
