# OpenFog

> **Hinweis — im Umbau:** OpenFog wird derzeit von der alten Web-App (JS + Leaflet)
> zu einer **nativen Android-App komplett neu geschrieben** (Kotlin + Jetpack Compose
> + osmdroid). Fokus liegt aktuell auf dem aktiven `native/` Projekt; der alte Code
> liegt eingefroren in `archive/`.

**Fog of World** — eine Open-Source-Android-App, die die von dir bereiste Karte
zurückhält, bis du sie tatsächlich erkundet hast. Je mehr Gebiet du abläufst oder
abfährst, desto mehr von der Weltkarte wird aufgedeckt.

- 🔒 **Privat / lokal** — alle Daten bleiben auf dem Gerät (kein Server, kein Konto).
- 🗺️ **Freie Karten** — OSM (OpenStreetMap), tiled, offline-fähig.
- 🐧 **100 % FLOSS** — kein Google Play Services, kein Firebase, kein Tracking.

## Repository-Struktur

```
OpenFog-Online/
├── native/   # AKTIVE App: Kotlin + Jetpack Compose + osmdroid (Wird gerade entwickelt)
├── archive/  # ALTES Projekt, EINGEFROREN (Web-App + F-Droid-Metadaten). Nur Referenz.
└── LICENSE
```

## native/ — die aktive App

Eine vollständig neue native Android-Implementation (Kotlin, Jetpack Compose,
Material 3, Room, osmdroid). Das ist der aktuelle Entwicklungsstand:

| Bereich | Status |
|---------|--------|
| Architektur / Daten (Room, 5 Tabellen) | ✅ geschrieben |
| Geometrie (JTS + `SphericalMath`) | ✅ geschrieben |
| Fog-Overlay-Engine (Union + Viewport-Diff) | ✅ geschrieben |
| GPS (LocationManager) | ✅ geschrieben |
| Erfolge / Level / Statistik | ✅ geschrieben |
| Import/Export (GPX/KML/KMZ, Backup) | ✅ geschrieben |
| Compose-UI (Karte / Profil / Einstellungen) | ✅ geschrieben |
| Build | ✅ `./gradlew assembleDebug` (BUILD SUCCESSFUL) |
| On-Device-Smoke-Test (MI9) | ⏳ offen |
| Unit-Tests (JTS, Parser) | ⏳ offen |

Version: `2.0.0` (`versionCode 3`). Package / applicationId: `com.openfog.online`.

### Build

Aus `native/` (JDK 21 erforderlich):

```bash
export JAVA_HOME=~/.jdks/jdk-21.0.12.1+1
export PATH=$JAVA_HOME/bin:$PATH
export ANDROID_HOME=~/Android/Sdk
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Ausführlichere Hinweise (Tech-Stack, Architektur, offene Punkte) stehen in der
lokalen `AGENTS.md` (gitignored, nicht im Repo).

## archive/ — das alte Projekt (eingefroren)

Der frühere Stand war eine Single-Page Web-App (Vite + vanilla JS + Leaflet +
Turf.js), verpackt als Capacitor-Android-App, plus F-Droid-Metadaten. Er wurde
komplett nach `archive/` verschoben und wird **nicht mehr gepflegt** — nur noch
zur historischen Referenz aufbewahrt.

## Lizenz

Alle Abhängigkeiten sind FLOSS (MIT / Apache-2.0). Siehe `LICENSE`.
