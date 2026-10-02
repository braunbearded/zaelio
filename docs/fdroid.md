# F-Droid Integration

## Ziel

Zaelio soll über `fdroiddata` in das offizielle F-Droid-Repository aufgenommen werden. Der RFP ist bereits angelegt:

- https://gitlab.com/fdroid/rfp/-/work_items/4205

## Voraussetzungen

- GitLab-Account
- Fork von https://gitlab.com/fdroid/fdroiddata
- Python-Umgebung mit `fdroidserver`
- Android SDK und JDK 21 passend zum F-Droid-Buildserver

In einem Arch-Container minimal. Nimm im Container am einfachsten ein SDK im Home-Verzeichnis; `/opt/android-sdk` braucht sonst Root-/Gruppenrechte:

```bash
pacman -Syu --needed git jdk21-openjdk python python-pip unzip wget
```

Android SDK Command Line Tools installieren und die benötigte Plattform/Build-Tools nachziehen:

```bash
mkdir -p "$HOME/Android/Sdk/cmdline-tools"
wget -O /tmp/cmdline-tools.zip https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
unzip -q /tmp/cmdline-tools.zip -d /tmp/android-cmdline-tools
mv /tmp/android-cmdline-tools/cmdline-tools "$HOME/Android/Sdk/cmdline-tools/latest"
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
yes | sdkmanager --licenses
sdkmanager "platforms;android-36" "build-tools;36.0.0" "platform-tools"
```

`fdroidserver` am besten in einem bestehenden oder externen Virtualenv installieren, nicht global und nicht im Zaelio-Repo:

```bash
python3 -m venv venv
. venv/bin/activate
pip install git+https://gitlab.com/fdroid/fdroidserver.git
fdroid --version
```

## Metadata-Datei

Die aktuelle Metadata-Vorlage liegt hier im Repo:

```text
docs/fdroiddata/com.zaelio.app.yml
```

Im `fdroiddata`-Fork nach folgendem Ziel kopieren:

```text
metadata/com.zaelio.app.yml
```

Bei jedem Release die Version in der Metadata-Datei mit aktualisieren:

- `Builds[].versionName`
- `Builds[].versionCode`
- `Builds[].commit` als vollen Commit-Hash, nicht als Tag/Branch
- `CurrentVersion`
- `CurrentVersionCode`
- `Binaries`, falls sich der Release-APK-Name oder die URL ändert
- `AllowedAPKSigningKeys`, falls ein neuer Release-Key verwendet wird

Den Signing-Key-Fingerprint aus der veröffentlichten APK ermittelt die Release-Action automatisch und prüft ihn gegen `AllowedAPKSigningKeys`. Lokal geht es so:

```bash
apksigner verify --print-certs zaelio.apk | grep SHA-256
```

## Lokal validieren

Nach dem GitHub-Release kann der F-Droid-Build inklusive Reproducible-Build-Vergleich lokal geprüft werden:

```bash
FDROIDDATA_DIR=/path/to/fdroiddata ./scripts/check-fdroid-reproducible.sh
```

Vor der Veröffentlichung durch den Versions-PR-Merge geht dieser Vergleich nicht vollständig, weil die `Binaries`-Referenz-APK auf GitHub noch nicht existiert.

Manuell im `fdroiddata`-Checkout mit aktiviertem Virtualenv vorher den Android-SDK-Pfad setzen; `fdroid build` baut in einem temporären Checkout und sieht das Zaelio-`local.properties` nicht:

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
fdroid readmeta
fdroid rewritemeta com.zaelio.app
fdroid lint com.zaelio.app
fdroid build -v -l com.zaelio.app
```

Wenn der Build lokal scheitert, zuerst prüfen:

- unterstützt F-Droid-CI die verwendeten Versionen von Android Gradle Plugin, Gradle und `compileSdk`?
- muss die Build-Recipe um `srclibs`, `prebuild`, `gradleprops` oder `sudo` ergänzt werden?
- sind alle Dependencies aus erlaubten Maven-Repositories und FLOSS-lizenziert?

## Merge Request

```bash
git checkout -b com.zaelio.app
cp /path/to/zaelio/docs/fdroiddata/com.zaelio.app.yml metadata/com.zaelio.app.yml
git add metadata/com.zaelio.app.yml
git commit -m "Add Zaelio"
git push origin com.zaelio.app
```

`scripts/release.sh` bereitet den Versionsbranch und den passenden Fastlane-Changelog
unter `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` vor. Erst der
Merge des Versions-PRs nach `main` erzeugt Tag und signierte Referenz-APK.
`.github/workflows/release.yml` prüft das Signing-Zertifikat gegen
`AllowedAPKSigningKeys` und veröffentlicht direkt im Merge-Workflow.
Der Job verwendet das Environment `release`: vor dem Merge auf GitHub nur Branch
`main` für Deployments zulassen und die Signing-Secrets dorthin verschieben.
Repository-/Organization-Secrets für Branch-Workflows dürfen nicht als Kopie
bestehen bleiben. Details und optionale Freigaben stehen im README unter
„Release signieren“; die Environment-Schutzregeln werden nicht durch YAML angelegt.

Danach setzt `scripts/release_ci.py` die F-Droid-Version und den vollständigen
getaggten Merge-/Squash-Commit-Hash. Die Action lädt die aktualisierte Metadata-Datei
als Artefakt hoch und erstellt einen separaten Metadaten-Commit auf `main`.
Branchschutz muss diesen Bot-Push erlauben; andernfalls die Artefakt-Datei per
separatem PR übernehmen. Das Release bleibt bei einem blockierten Metadaten-Push
bereits veröffentlicht. Den externen `fdroiddata`-Fork anschließend mit dieser
Datei aktualisieren und die Reproduzierbarkeit prüfen.

Danach einen Merge Request gegen `fdroid/fdroiddata` öffnen und im RFP kommentieren:

```text
Metadata MR submitted: <MR-Link>
```

## F-Droid-MR-Checklist

- `make-summary-translatable.py`: kein `Summary:` in `metadata/com.zaelio.app.yml` eintragen; Summary/Description kommen aus dem upstream Fastlane-Verzeichnis `fastlane/metadata/android/en-US/`.
- Inclusion Criteria: keine proprietären Dienste, kein Tracking, MIT-Lizenz, Quellcode und Dependencies öffentlich.
- App-Autor: `braunbearded` ist im Metadata-File gesetzt; wenn du den MR selbst öffnest, bist du der Autor.
- Issues referenzieren: im MR den RFP `https://gitlab.com/fdroid/rfp/-/work_items/4205` verlinken.
- Build: lokal mit `fdroid build -v -l com.zaelio.app` prüfen.
- Issue Tracker/Kontakt: GitHub Issues sind in Metadata und README verlinkt.
- Upstream-Metadaten: `fastlane/metadata/android/en-US/` enthält Titel, Kurzbeschreibung, Beschreibung, versionCode-passende Changelogs und mehrere sinnvoll benannte Screenshots.
- Releases/Autoupdate: Releases sind als `vX.Y.Z` getaggt; `UpdateCheckMode: Tags` ist gesetzt.
- Externe Repos/Submodules: keine.
- Native Code/Multiple APKs: keine native Codebasis, daher nicht relevant.
- Reproducible Builds: `Binaries` und `AllowedAPKSigningKeys` sind gesetzt; Release-APK muss mit JDK 21 gebaut und mit demselben Signing-Key signiert bleiben. AGP-Dependency-Metadaten bleiben per `dependenciesInfo` aus der APK, weil F-Droid extra Signing Blocks ablehnt.

## Repomaker

Repomaker ist für die offizielle Aufnahme nicht nötig. Es ist nur sinnvoll, wenn zusätzlich ein eigenes F-Droid-Repository für Zaelio angeboten werden soll.
