# PlexCloud für Android ☁️

Android-App für [PlexCloud](https://www.plexora.eu/plexcloud), den Cloud-Speicher von Plexora in Frankfurt (AWS eu-central-1).
Nicht im Play Store: Installation als APK aus den Releases dieses Repos, Updates meldet die App selbst.

## Was die App kann

- **Dateien und Ordner** durchsuchen, mit Pfad, Filtern (Fotos, PDF, verknüpft) und Speicheranzeige
- **Foto aufnehmen und hochladen** mit einem Tipp (Kamera-App des Handys, kein Kamera-Recht nötig)
- **Teilen → PlexCloud** aus jeder App, auch mehrere Dateien auf einmal
- **Uploads im Hintergrund** direkt nach S3, große Dateien ab 64 MB in Teilen; mit Tempo, Restzeit und SHA-256-Prüfsumme. Standardmäßig nur im WLAN
- **Ansehen, herunterladen, teilen**; Verknüpfungen mit Projekten, Kontakten, Firmen und Deals sehen und Dateien für Kontakte im **Kundenportal** frei- oder wegschalten
- **System**: Speicher, Download-Kontingent, Latenz zur API, Akzentfarbe frei wählbar
- **Updates automatisch** über die Releases dieses Repos (wie MHD Safety)

## Anmeldung und Sicherheit

- Anmeldung mit dem Plexora-Konto über die Cognito-Anmeldeseite (Google oder E-Mail + Passwort), OAuth 2.0 Authorization Code mit PKCE. Das Passwort wird nur bei Plexora eingegeben, nie in der App
- Tokens liegen AES-256-GCM-verschlüsselt auf dem Handy; der Schlüssel steckt im Android-Keystore und verlässt das Gerät nicht. Kein Backup der App-Daten
- Im Code stehen nur öffentliche Kennungen (API-Adresse, Cognito-Domain, App-Client ohne Secret). Kein Geheimnis im Repo; der Signier-Schlüssel liegt außerhalb

## Installieren

1. Unter [Releases](../../releases/latest) die Datei `PlexCloud-x.y.z.apk` am Handy herunterladen
2. Antippen und installieren (einmalig „Installation aus unbekannten Quellen“ erlauben)

## Entwickeln

- Android Studio, Kotlin, Jetpack Compose (Material 3), WorkManager, DataStore, Custom Tabs; keine weiteren Bibliotheken
- Tests: `./gradlew testDebugUnitTest`
- Die Debug-Version (`com.paeffgen.plexcloud.debug`) lässt sich neben der echten App installieren
- Neue Version: Abschnitt `## [x.y.z]` in `CHANGELOG.md`, committen, dann `scripts/release.sh x.y.z`

## Schriften

Inter, Space Grotesk und JetBrains Mono, alle unter der [SIL Open Font License 1.1](https://openfontlicense.org) (aus [google/fonts](https://github.com/google/fonts)).
