# Changelog

Alle Versionen von PlexCloud für Android. Der Abschnitt einer Version erscheint in der App als „Was ist neu“.

## [0.2.6] - 2026-11-16

- Neu: Fotos landen in **Bilder/<Monat>**, Videos in **Videos/<Monat>**, gesicherte Ordner in **Dokumente**. Keine Ordner „Handy-Sicherung“ mehr
- Neu: Umzug beim ersten Lauf. Was schon in „Handy-Sicherung“ liegt, wandert nach Bilder bzw. Dokumente; doppelte Kopien kommen dabei in den Papierkorb, danach der leere alte Ordner
- Kamera und Scanner: im Stamm geht ein Foto nach Bilder und ein Scan nach Dokumente; in einem geöffneten Ordner bleibt es dort

## [0.2.5] - 2026-11-16

- Behoben: Fotos wurden doppelt hochgeladen, wenn zwei Sicherungsläufe gleichzeitig starteten. Jetzt läuft immer nur einer
- Besser: Bei Netzfehlern (WLAN-Wechsel, Funkloch, „Unable to resolve host“) versucht die App es bis zu zehnmal mit wachsendem Abstand statt dreimal
- Neu: Abgleich vor jedem Sicherungs-Upload. Liegt die Datei (gleicher Name, gleiche Größe) schon im Zielordner, wird sie übersprungen. „Auch vorhandene sichern“ lädt damit nur nach, was fehlt, und gibt es jetzt auch beim Ändern der Alben
- Neu: „Erneut senden“ unter Uploads schickt fehlgeschlagene Dateien noch einmal; Fehler stehen oben in der Liste

## [0.2.4] - 2026-11-16

- Neu: Sicherung abbrechen. Unter „Uploads“ und „System“ stoppt ein Tipp alle wartenden Sicherungs-Uploads; Hochgeladenes bleibt, ab dann nur neue Fotos
- Neu: Alben wählen. Beim Einschalten der Sicherung zeigt die App die Alben (Kamera, Screenshots, WhatsApp …) mit Anzahl; vorausgewählt ist nur die Kamera. Unter „System → Sicherung → Alben“ jederzeit änderbar
- „Auch vorhandene sichern“ ist jetzt ein Schalter im selben Dialog und zeigt, wie viele Dateien das wären

## [0.2.3] - 2026-11-16

- Upload-Liste: PDFs, Videos und Fotos mit eigenem Symbol; nach dem Hochladen zeigt die Übersicht, was übertragen wurde, statt „0 B“
- Speicheranzeige bei unbegrenztem Speicher nicht mehr als voll
- Knopf „Aufnehmen“ statt „Foto aufnehmen“ (öffnet die Wahl Dokument oder Foto)

## [0.2.2] - 2026-11-16

- Sicherer: Der Dokumentenscanner wird in der Release-Version nicht mehr wegoptimiert (Absturz-Schutz wie bei MHD Safety)

## [0.2.1] - 2026-11-16

- Besser: Der Sicherungsordner heißt wie das Handy in den Einstellungen („Galaxy A16 5G“), nicht nach der Typnummer („SM-A166B“)
- Neu: Beim Einschalten der Sicherung fragt die App, ob sie vom Energiesparen ausgenommen werden darf. Samsung beendet sonst Apps im Hintergrund, und die Sicherung liefe erst beim nächsten Öffnen. Steht noch „Energiesparen bremst die Sicherung“ unter „System“, genügt ein Tipp darauf

## [0.2.0] - 2026-11-16

- Neu: Beim Tipp auf die Kamera fragt die App „Dokument oder Foto?“. Dokumente laufen durch den Scanner (Kanten erkennen, gerade ziehen, mehrere Seiten) und werden als PDF hochgeladen, Fotos als JPG
- Neu: Automatische Sicherung. Fotos (auf Wunsch auch Videos) landen von selbst in „Handy-Sicherung/<Gerät>/Fotos/<Monat>“, beim Einschalten wahlweise nur neue oder alle vorhandenen
- Neu: Ordner sichern, z. B. Download oder Dokumente. Neue und geänderte Dateien werden stündlich hochgeladen
- Sicherung nur im WLAN und bei ausreichend Akku, wenn „Nur im WLAN hochladen“ an ist

## [0.1.0] - 2026-11-16

- Erste Version: Anmeldung mit dem Plexora-Konto (Google oder E-Mail), Dateien und Ordner durchsuchen, neue Ordner
- Foto aufnehmen und direkt hochladen, Dateien aus anderen Apps per „Teilen“ hochladen, Uploads im Hintergrund mit Tempo und SHA-256
- Dateien ansehen, herunterladen und teilen; Verknüpfungen sehen, Kundenportal je Kontakt an- und ausschalten
- System: Speicher, Downloads, Latenz, Akzentfarbe frei wählbar, automatische Updates
