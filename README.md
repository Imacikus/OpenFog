# OpenFog

**Fog of World** — eine Open-Source-Android-App, die die von dir bereiste Karte
zurückhält, bis du sie tatsächlich erkundet hast. Je mehr Gebiet du abläufst oder
abfährst, desto mehr von der Weltkarte wird aufgedeckt.

- 🔒 **Privat / lokal** — alle Daten bleiben auf dem Gerät (kein Server, kein Konto).
- 🗺️ **Freie Karten** — OSM (OpenStreetMap), tiled, offline-fähig.
- 🐧 **100 % FLOSS** — kein Google Play Services, kein Firebase, kein Tracking.

## Repository-Struktur

```
OpenFog-Online/
├── native/      # Die aktive Android-App: Kotlin + Jetpack Compose + osmdroid
├── metadata/    # F-Droid-Metadaten (com.openfog.online.yml)
├── fastlane/    # F-Droid-/Store-Metadaten (Titel, Beschreibungen)
├── CHANGELOG.md
└── LICENSE
```

## native/ — die App

Eine vollständig native Android-Implementierung (Kotlin, Jetpack Compose,
Material 3, Room, osmdroid, JTS):

| Bereich | Status |
|---------|--------|
| Architektur / Daten (Room, 5 Tabellen) | ✅ |
| Geometrie (JTS + `SphericalMath`) | ✅ |
| Fog-Overlay-Engine (Union + Viewport-Diff) | ✅ |
| GPS (LocationManager) | ✅ |
| Erfolge / Level / Statistik | ✅ |
| Import/Export (GPX/KML/KMZ, Backup) | ✅ |
| Compose-UI (Karte / Profil / Einstellungen) | ✅ |
| Build | ✅ `./gradlew assembleDebug` |
| Unit-Tests (JTS, Geo) | ✅ `./gradlew testDebugUnitTest` |
| On-Device-Smoke-Test (MI9) | ✅ |

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

Release-Signing (für F-Droid / Verteilung) liest `native/keystore.properties`
(gitignored) und signiert mit dem keystore unter `~/.android/openfog-release.keystore`:

```bash
./gradlew assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```

### F-Droid

- Metadaten: `metadata/com.openfog.online.yml` (pinnt einen vollen Commit-Hash,
  `subdir: native/app`, `gradle: yes`). `Binaries` + `AllowedAPKSigningKeys`
  verifizieren die signierte Release-APK.
- Bei jedem Release: Version in `build.gradle.kts` erhöhen, `CurrentVersion`/
  `CurrentVersionCode` in der yml aktualisieren und den Commit-Hash des Builds pinnen.

## Lizenz

Alle Abhängigkeiten sind FLOSS (MIT / Apache-2.0). Siehe `LICENSE`.
