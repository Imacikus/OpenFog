# OpenFog – Android App

Android-Wrapper für die Web-App **OpenFog**.

## Inhalt

| Datei | Beschreibung |
|-------|-------------|
| `OpenFog.apk` | **Fertige Debug-APK** – direkt installierbar auf Android |
| `android/` | Vollständiges Android-Studio-Projekt (Capacitor) zum Selberbauen |

## APK installieren (Sideload)

1. `OpenFog.apk` aufs Android-Gerät übertragen (USB / Cloud / Download)
2. Auf dem Gerät: **Einstellungen → Sicherheit → Unbekannte Apps installieren** erlauben
3. APK öffnen → installieren

## Selber bauen

### Voraussetzungen

- Node.js 18+
- Android SDK (API 35)
- JDK 17+

### Schnell (Full Pipeline)

```bash
# Aus dem fog-of-world-web/ Ordner:
cd ../fog-of-world-web
npm install
npm run build
npx cap sync
cd android && ./gradlew assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk ../../openfog/OpenFog.apk
```

### Schritt für Schritt

```bash
# 1. Web-App bauen
cd ../fog-of-world-web
npm install
npm run build

# 2. Web-Assets ins Android-Projekt kopieren
npx cap sync

# 3. APK bauen
cd android
./gradlew assembleDebug

# 4. APK kopieren
cp app/build/outputs/apk/debug/app-debug.apk ../../openfog/OpenFog.apk
```

Die APK liegt in `fog-of-world-web/android/app/build/outputs/apk/debug/app-debug.apk` und wird nach `openfog/OpenFog.apk` kopiert.

## Technik

- **Capacitor** – WebView-Wrapper
- Die Web-App (`fog-of-world-web/dist/`) wird als eingebettetes Asset ausgeliefert
- GPS per `@capacitor/geolocation`
- Service Worker + Offline-Support durch PWA-Konfiguration
- Keine CDN-Abhängigkeiten, alle Assets gebundled

## F-Droid

- `applicationId`: `com.openfog.online`
- Lizenz: MIT
- Alle Abhängigkeiten sind FLOSS (MIT / Apache 2.0)
- Kein Google Play Services, Firebase, Crashlytics oder Tracking

## Hinweise

- Debug-APK ist **nicht signiert** – für Play Store müsste ein Release-Build mit Keystore erstellt werden
- Bei Änderungen an der Web-App: `npm run build` + `npx cap sync` + Gradlew ausführen
