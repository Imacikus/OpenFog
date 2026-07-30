# Open Fog Online – Android App

Android-Wrapper für die Web-App **Fog of World (OpenFog Online)**.

## Inhalt

| Datei | Beschreibung |
|-------|-------------|
| `OpenFogOnline.apk` (3.9 MB) | **Fertige Debug-APK** – direkt installierbar auf Android |
| `android/` | Vollständiges Android-Studio-Projekt (Capacitor) zum Selberbauen |

## APK installieren (Sideload)

1. `OpenFogOnline.apk` aufs Android-Gerät übertragen (USB / Cloud / Download)
2. Auf dem Gerät: **Einstellungen → Sicherheit → Unbekannte Apps installieren** erlauben
3. APK öffnen → installieren

## Selber bauen (Android Studio)

### Voraussetzungen

- Android Studio (latest)
- JDK 17+
- Android SDK (wird von Android Studio mitgeliefert)

### Schritte

```bash
# 1. Web-App bauen (im fog-of-world-web Ordner)
cd ../fog-of-world-web
npm install
npm run build

# 2. Web-Assets in Android-Projekt kopieren
cd ../openfog-online
npx cap sync

# 3. In Android Studio öffnen und APK bauen
npx cap open android
# → Android Studio: Build → Build Bundle(s) / APK(s) → Build APK
```

Die APK liegt dann in `android/app/build/outputs/apk/debug/app-debug.apk`.

## Technik

- **Capacitor 5** (Ionic) – WebView-Wrapper
- Die Web-App (`fog-of-world-web/dist/`) wird als eingebettetes Asset ausgeliefert
- GPS, Kamera, etc. könnten per Capacitor-Plugins angebunden werden (aktuell nicht genutzt)
- Service Worker + Offline-Support durch PWA-Konfiguration

## Hinweise

- Debug-APK ist **nicht signiert** – für Play Store müsste ein Release-Build mit Keystore erstellt werden
- Bei Änderungen an der Web-App: `npm run build` + `npx cap sync` ausführen, dann APK neu bauen
