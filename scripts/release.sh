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

version_name=$(ask "Version name" "$next_name")
version_code=$(ask "Version code (integer)" "$next_code")

if ! [[ $version_name =~ ^[0-9]+\.[0-9]+\.[0-9]+$ && $version_code =~ ^[0-9]+$ ]]; then
    echo "Use a version like 1.2.3 and an integer versionCode" >&2
    exit 1
fi

if git rev-parse --verify "refs/tags/v$version_name" >/dev/null 2>&1; then
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

branch="v$version_name"
if [[ $(git branch --show-current) != "$branch" ]]; then
    git switch -c "$branch"
fi

python3 - "$version_name" "$version_code" "${entries[@]}" <<'PY'
from pathlib import Path
import re, sys
version, code, *entries = sys.argv[1:]
text = '\n'.join(entries) + '\n'
if len(text) > 500:
    raise SystemExit('Fastlane changelog must stay under 500 characters')
if int(code) <= 0:
    raise SystemExit('versionCode must be positive')
code = str(int(code))

p = Path('app/build.gradle')
s = p.read_text()
s = re.sub(r"versionCode = \d+", f"versionCode = {code}", s, count=1)
s = re.sub(r"versionName = '[^']+'", f"versionName = '{version}'", s, count=1)
p.write_text(s)

p = Path('CHANGELOG.md')
s = p.read_text()
summary = ''.join(f"- {entry}\n" for entry in entries)
unreleased = re.search(r'(?ms)^## Unreleased\n(.*?)(?=^## |\Z)', s)
if unreleased:
    details = unreleased[1].strip() or summary.strip()
    section = f"## Unreleased\n\n## {version}\n\n{details}\n\n"
    s = s[:unreleased.start()] + section + s[unreleased.end():]
else:
    s = s.replace('# Changelog\n\n', f'# Changelog\n\n## {version}\n\n{summary}\n', 1)
p.write_text(s)

fastlane = Path(f'fastlane/metadata/android/en-US/changelogs/{code}.txt')
fastlane.parent.mkdir(parents=True, exist_ok=True)
fastlane.write_text(text)
PY

python3 scripts/release_ci.py "$branch"

if yesno "Run unit tests" "y"; then
    python3 -m unittest discover -s scripts -p 'test_*.py'
    ./gradlew testDebugUnitTest
fi

if yesno "Build debug APK" "y"; then
    ./gradlew assembleDebug
fi

if yesno "Commit release preparation for v$version_name" "y"; then
    git add app/build.gradle CHANGELOG.md fastlane/metadata/android/en-US/changelogs
    git commit -m "Prepare release $version_name"
fi

if yesno "Push version branch now" "n"; then
    git push --set-upstream origin "refs/heads/$branch"
else
    echo "Push later with:"
    echo "  git push --set-upstream origin refs/heads/$branch"
fi

echo "Open a PR into main. Its merge creates the tag, signed release and F-Droid metadata."
