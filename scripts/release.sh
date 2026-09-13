#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

current_name=$(grep -m1 "versionName" app/build.gradle | sed -E "s/.*'([^']+)'.*/\1/")
current_code=$(grep -m1 "versionCode" app/build.gradle | sed -E 's/.*= *([0-9]+).*/\1/')
next_name=$(python3 - "$current_name" <<'PY'
import sys
parts = sys.argv[1].split('.')
parts[-1] = str(int(parts[-1]) + 1)
print('.'.join(parts))
PY
)
next_code=$((current_code + 1))

ask() {
    local prompt=$1 default=$2 value
    read -r -p "$prompt [$default]: " value
    printf '%s' "${value:-$default}"
}

yesno() {
    local prompt=$1 default=$2 value
    read -r -p "$prompt [$default]: " value
    value=${value:-$default}
    [[ $value =~ ^[YyJj] ]]
}

verify_signing_key() {
    local apk=app/build/outputs/apk/release/zaelio.apk
    local sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-android-sdk}}
    local apksigner expected actual

    [[ -f $apk ]] || { echo "Release APK missing: $apk" >&2; exit 1; }
    apksigner=$(command -v apksigner || find "$sdk/build-tools" -name apksigner 2>/dev/null | sort -V | tail -n1)
    [[ -n $apksigner ]] || { echo "apksigner not found" >&2; exit 1; }

    expected=$(awk '/AllowedAPKSigningKeys:/ { sub(/.*AllowedAPKSigningKeys:[[:space:]]*/, ""); if ($0 != "") { print; exit } getline; gsub(/^[[:space:]-]*/, ""); print; exit }' docs/fdroiddata/com.zaelio.app.yml)
    actual=$("$apksigner" verify --print-certs "$apk" | sed -n 's/.*SHA-256 digest: //p')
    [[ $actual == "$expected" ]] || { echo "Signing key mismatch: $actual != $expected" >&2; exit 1; }
    echo "APK signing certificate SHA-256: $actual"
}

version_name=$(ask "Version name" "$next_name")
version_code=$(ask "Version code (integer)" "$next_code")

if ! [[ $version_code =~ ^[0-9]+$ ]]; then
    echo "versionCode must be an integer" >&2
    exit 1
fi

if git rev-parse "v$version_name" >/dev/null 2>&1; then
    echo "Tag v$version_name already exists" >&2
    exit 1
fi

echo "Changelog entries, one per line. Empty line ends."
entries=()
while IFS= read -r line; do
    [[ -z $line ]] && break
    entries+=("$line")
done
if (( ${#entries[@]} == 0 )); then
    entries=("Release $version_name.")
fi

python3 - "$version_name" "$version_code" "${entries[@]}" <<'PY'
from pathlib import Path
import re, sys
version, code, *entries = sys.argv[1:]

p = Path('app/build.gradle')
s = p.read_text()
s = re.sub(r"versionCode = \d+", f"versionCode = {code}", s, count=1)
s = re.sub(r"versionName = '[^']+'", f"versionName = '{version}'", s, count=1)
p.write_text(s)

p = Path('CHANGELOG.md')
s = p.read_text()
entry = f"## {version}\n\n" + ''.join(f"- {e}\n" for e in entries) + "\n"
s = s.replace('# Changelog\n\n', '# Changelog\n\n' + entry, 1)
p.write_text(s)

fastlane = Path(f'fastlane/metadata/android/en-US/changelogs/{code}.txt')
fastlane.parent.mkdir(parents=True, exist_ok=True)
text = '\n'.join(entries) + '\n'
if len(text) > 500:
    raise SystemExit('Fastlane changelog must stay under 500 characters')
fastlane.write_text(text)
PY

if yesno "Run unit tests" "y"; then
    ./gradlew testDebugUnitTest
fi

if yesno "Build release APK" "y"; then
    ./gradlew assembleRelease
fi

if yesno "Commit and tag v$version_name" "y"; then
    git add app/build.gradle CHANGELOG.md fastlane/metadata/android/en-US/changelogs
    git commit -m "Release $version_name"
    release_commit=$(git rev-parse HEAD)
    git tag "v$version_name"

    python3 - "$version_name" "$version_code" "$release_commit" <<'PY'
from pathlib import Path
import re, sys
version, code, commit = sys.argv[1:]

p = Path('docs/fdroiddata/com.zaelio.app.yml')
s = p.read_text()
s = re.sub(r"versionName: [^\n]+", f"versionName: {version}", s, count=1)
s = re.sub(r"versionCode: \d+", f"versionCode: {code}", s, count=1)
s = re.sub(r"commit: [^\n]+", f"commit: {commit}", s, count=1)
s = re.sub(r"CurrentVersion: [^\n]+", f"CurrentVersion: {version}", s, count=1)
s = re.sub(r"CurrentVersionCode: \d+", f"CurrentVersionCode: {code}", s, count=1)
p.write_text(s)
PY
    git add docs/fdroiddata/com.zaelio.app.yml
    git commit -m "Update F-Droid metadata for $version_name"
fi

if yesno "Push branch and tag now" "n"; then
    verify_signing_key
    branch=$(git branch --show-current)
    git push origin "$branch"
    git push origin "v$version_name"
else
    echo "Push later with:"
    echo "  git push origin $(git branch --show-current)"
    echo "  git push origin v$version_name"
fi
