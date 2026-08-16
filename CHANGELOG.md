# Changelog

## 1.0.7

- Improved session input performance with debounced, batched field saves.
- Added animated collapse/expand controls for live session fields and a setting for their default start state.
- Aligned live session field cards with the app's shared Material-style card and expand icon design.
- Reworked settings selection controls to keep scroll position on global refreshes and avoid unnecessary local flicker.
- Restored accent color options as colored controls.
- Smoothed settings refreshes with a crossfade after global setting changes.
- Added tests for session batch-save replacement, numeric formatting/parsing, and session field collapse settings.
- Made live session text fields start compact and grow with multiline content, including prefilled values.
- Colored app bar titles and back buttons with the selected accent color.
- Fixed tracker-editor delete gestures so dropdown/input interactions do not trigger delayed or stacked delete dialogs.

## 1.0.6

- Added language selection and multilingual UI support.
- Added F-Droid Fastlane icon metadata.

## 1.0.5

- Removed Android Gradle dependency metadata from release APKs so F-Droid accepts reproducible-build binaries.

## 1.0.4

- Release builds now use JDK 21 to match the F-Droid build server. 
- Added signing-key verification to the GitHub release workflow and release script.
- Added a local helper script to check F-Droid reproducible builds.   

## 1.0.3

- - F-Droid-Metadaten für reproduzierbare Builds ergänzt.
- - Release-Script aktualisiert, damit F-Droid volle Commit-Hashes statt Tags verwendet.
- - F-Droid- und Release-Dokumentation präzisiert.

## 1.0.2

- Gradle-Konfiguration auf die neue Property-Assignment-Syntax mit `=` aktualisiert.

## 1.0.1

- F-Droid/Fastlane-Metadaten und Screenshot für die Paketierung ergänzt.
- Quellcode-Links in README und About-Screen auf `github.com/braunbearded/zaelio` aktualisiert.
- Gradle-Wrapper-Prüfsumme ergänzt, damit der Build reproduzierbarer und prüfbarer ist.
- Release- und Tagging-Dokumentation präzisiert.

## 1.0.0

- Erste öffentliche Version von Zaelio.
- Eigene Tracker, Sessions und lokale SQLite-Speicherung.
- JSON Import/Export für Tracker, Sessions und Backups.
- Helles/dunkles Design, Akzentfarbe, Schriftgröße und Feldgröße.
