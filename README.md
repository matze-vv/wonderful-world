# Wonderful World

Ein „Anti-Rot-Journal“ für Android: Rituale, Monats-Missionen, Tagebuch mit Fotos und ein Wecker, der direkt ins Dashboard führt.
Kotlin · Jetpack Compose · Room. Alle Daten bleiben lokal auf dem Gerät.

## APK holen
- **GitHub Actions:** Jeder Push auf `main` baut die APK → Tab *Actions* → letzter Lauf → Artefakt `wonderful-world-<nr>`.
- **Release:** Ein Tag wie `v1.0` erzeugt zusätzlich ein GitHub-Release mit der APK (bequem direkt auf dem Handy herunterladen).

Alle APKs sind mit demselben Schlüssel (`app/debug.keystore`) signiert. Updates lassen sich deshalb einfach drüberinstallieren, ohne dass Daten verloren gehen.

## Lokal bauen
```
./gradlew assembleRelease
```

## Bedienung (ab Version 2)
- **Dashboard „Woche“** mit Kacheln: Rituale heute (rötlich), eine Kachel pro Missions-Tag (gelblich, umrandet in der Tag-Farbe), eine Kachel pro Tagebucheintrag der Woche (bläulich, Foto als Hintergrund).
- Kreis bzw. **+1** in der Kachel hakt direkt ab, der Rest der Kachel öffnet die Detailansicht.
- **Mehr:** gespeicherte Rituale (Details, Standard-Tage, aktiv/pausiert) und Tags (Name, Farbe) verwalten, dazu Einstellungen und Backup.
- **Neuer Eintrag:** Foto-Kachel oben (+ zum Hinzufügen, Tippen = Vollbild, Halten = ändern), Text-Kachel darunter.
- Schrift: [Figtree](https://github.com/erikdkennedy/figtree) (SIL Open Font License, siehe `licenses/`).

## Wichtige Logik
- Die Woche beginnt am **Sonntag**. Ohne Sonntagsplanung gelten die Rituale der letzten geplanten Woche weiter.
- Die **Woche, die den Monatsersten enthält**, gehört zum neuen Monat. An ihrem Sonntag werden die Monats-Missionen geplant und offene Missionen des Vormonats übernommen oder archiviert.
- Die Sonntagsplanung startet mit den **Standard-Tagen** aller aktiven Rituale; pausierte Rituale werden nicht geplant.
- **Serien** zählen nur fällige Tage. Ein heute fälliges, noch offenes Ritual unterbricht die Serie nicht.
- **Wecker:** `AlarmManager.setAlarmClock` + Vollbild-Activity, Standard-Weckton, 3 Min. Schlummern (max. 3×), nach 10 Min. ohne Reaktion automatisch Schlummern. Nach dem Ausschalten erscheint der Morgen-Impuls, und das Dashboard öffnet sich.
- **Morgen-Impuls** an Tagen ohne Wecker zur in der Sonntagsplanung gewählten Zeit.
- **Backup:** Einstellungen → Exportieren/Importieren (ZIP mit `data.json` und `photos/`). Beim Import werden alle Daten ersetzt.
