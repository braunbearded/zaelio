# 📊 Zaelio

Eine kleine Android-App zum Erstellen eigener Tracker, Starten von Sessions und Speichern von Messwerten lokal in SQLite.

Lizenz: MIT

Kontakt/Bugs: https://github.com/braunbearded/zaelio/issues

## ✨ Features

- Eigene Tracker mit global sortierten Feldern erstellen; neue Elemente scrollen im Editor automatisch in den sichtbaren Bereich
- Sessions erfassen und fortsetzen, mit großen Plus/Minus-Buttons und Material-Feldern für Text, Zahlen und Timer
- Session-Felder minimal animiert ein-/ausklappen; Startzustand in den Einstellungen wählen
- Listen-Einträge per Long-Press, Links-Swipe oder `...`-Menü löschen
- Sessions und Tracker per Drag-Handle in der Übersicht sortieren
- Android-Zurück navigiert sinnvoll; auf Home beendet erst ein schneller Doppel-Zurück-Druck die App
- Werte lokal in SQLite speichern
- Tracker, Sessions oder komplette Backups als JSON importieren/exportieren
- Helles/dunkles Design, Schriftgröße, Akzentfarbe, globale Feldgröße und Session-Feld-Startzustand einstellbar
- Kein Google Play Services, kein Firebase, keine Cloud

## 📱 Screenshots

![Session screen](docs/screenshots/session.png)

## 🧰 Benötigte Abhängigkeiten

Zum Bauen brauchst du lokal:

- JDK 21 für Release-/F-Droid-Builds; JDK 17+ reicht für lokale Entwicklung
- Android SDK mit Platform `36`
- Android Build Tools passend zum SDK
- Gradle Wrapper aus diesem Repository (`./gradlew`)

Projektabhängigkeiten:

- Android Gradle Plugin `9.3.1`
- Gradle `9.5.1`
- Material Components `com.google.android.material:material:1.14.0`

Test-Abhängigkeiten:

- JUnit `4.13.2`
- AndroidX Test Core `1.7.0`
- Robolectric `4.16.1`
- ASM `9.10.1` für Robolectric auf modernen JDKs
- org.json `20260719`

Das Projekt nutzt absichtlich keine Google Play Services und kein Firebase.

## ⚙️ Einrichtung

Wenn dein Android SDK nicht automatisch gefunden wird, erstelle im Projektordner eine `local.properties`:

```properties
sdk.dir=/pfad/zu/deinem/Android/Sdk
```

Optional Java setzen:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
```

## 🛠️ Debug-Build erstellen

```bash
./gradlew assembleDebug
```

Die APK liegt danach hier:

```text
app/build/outputs/apk/debug/zaelio-debug.apk
```

Release-Builds sollten mit JDK 21 laufen, damit GitHub-Release und F-Droid-Buildserver dieselbe Java-Version nutzen.

## 🚀 Release-Version erstellen

```bash
./scripts/release.sh
```

Das Script aktualisiert Version und Changelog, kann Tests/Release-Build ausführen und Commit/Tag erstellen. Danach schreibt es die F-Droid-Metadaten mit dem vollen Release-Commit-Hash in einen zweiten Commit. Vor einem direkten Push prüft es den lokalen APK-Signing-Zertifikat-Hash. Die GitHub Action baut aus dem Tag eine signierte Release-APK und hängt sie an den GitHub Release.

Tag prüfen oder bei Fehler löschen:

```bash
git tag
git show v1.1.0
git tag -d v1.1.0
git push origin :refs/tags/v1.1.0
```

## 🔐 Release signieren

Keystore erstellen:

```bash
keytool -genkeypair -v -keystore zaelio-release.jks -alias zaelio -keyalg RSA -keysize 4096 -validity 10000
```

Keystore als GitHub Secret ablegen:

```bash
base64 -w0 zaelio-release.jks
```

Benötigte GitHub Secrets:

```text
ANDROID_SIGNING_KEY_BASE64
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD
```

Lokal kann eine signierte APK mit denselben Umgebungsvariablen gebaut werden:

```bash
ANDROID_KEYSTORE_PATH=/pfad/zu/zaelio-release.jks \
ANDROID_KEYSTORE_PASSWORD=... \
ANDROID_KEY_ALIAS=zaelio \
ANDROID_KEY_PASSWORD=... \
./gradlew assembleRelease
```

Ohne diese Variablen erzeugt Gradle weiterhin nur eine unsigned Release-APK. Release-Builds heißen `app/build/outputs/apk/release/zaelio.apk`, Debug-Builds `app/build/outputs/apk/debug/zaelio-debug.apk`. Die Release-Action prüft den APK-Signing-Zertifikat-Hash gegen `AllowedAPKSigningKeys` und schreibt ihn in die GitHub-Release-Notes.

## 📦 F-Droid

Vor der Einreichung bei F-Droid:

- `LICENSE`, `CHANGELOG.md`, Fastlane-Metadaten und Screenshots aktuell halten.
- Pro Release `versionCode` erhöhen und einen Tag wie `v1.1.0` setzen.
- `docs/fdroiddata/com.zaelio.app.yml` für die neue Version aktualisieren: voller Commit-Hash, `Binaries`, `AllowedAPKSigningKeys`.
- Prüfen, ob F-Droid die verwendete Kombination aus Android Gradle Plugin und `compileSdk` bauen kann.
- Nach dem GitHub-Release lokal prüfen: `FDROIDDATA_DIR=/path/to/fdroiddata ./scripts/check-fdroid-reproducible.sh`

Details: `docs/fdroid.md`

## 📁 Projektstruktur

```text
app/src/main/java/com/zaelio/app/
├── MainActivity.java              # Routing, Lifecycle, Top Bar, Navigation
├── TrackingDatabase.java          # SQLite Schema v8, Migrationen, Datenzugriff
├── TrackerJsonRepository.java     # JSON Import/Export und Tracker-Speicherung
├── BackupJsonRepository.java      # JSON Backup für Tracker, Sessions und Werte
├── JsonUtil.java                  # JSON-Helfer
├── FormatUtil.java                # Gemeinsame Formatierung
├── Models.java                    # Datenmodelle
├── HomeUi.java                    # Session-/Tracker-Übersicht
├── ReorderHelper.java             # Gemeinsames Drag-Reorder-Verhalten
├── TrackerFlowUi.java             # Tracker-Editor und Session-Routing
├── FieldInputUi.java              # Eingabefelder, Timer, Zahlensteuerung und einklappbare Session-Felder
├── theme/ThemeStore.java          # Theme, Akzentfarbe, Schrift-/Feldgröße und Session-Feld-Startzustand
└── ui/
    ├── AppUi.java                 # Gemeinsame UI-Bausteine
    └── SettingsUi.java            # Einstellungen und Über-Screen
```

## 🧪 Tests

Lokale Unit-Tests laufen mit JUnit und Robolectric:

```bash
./gradlew testDebugUnitTest
```

Aktueller Fokus:

- `JsonUtilTest` prüft JSON-Roundtrips und Tracker-Export.
- `TrackingDatabaseTest` prüft Seed-Daten, Sessions, Records, Previous Values, Löschlogik, Batch-Speicherung, Übersichtssortierung und Migration auf Schema v8.
- `BackupJsonRepositoryTest` prüft alle Backup-Export/Import-Varianten gegen Beispiel-JSON unter `app/src/test/resources/backup-fixtures/`.

Zusätzlicher Build-Check:

```bash
./gradlew assembleDebug
```

## 📝 Hinweise

- App-Daten bleiben lokal auf dem Gerät.
- Die App nutzt `android.permission.VIBRATE` nur für kurzes Feedback beim Markieren eines Löschkandidaten.
- `local.properties`, Keystores und Passwörter nicht committen.
- Für F-Droid/OSS-Builds nur freie Abhängigkeiten verwenden.
