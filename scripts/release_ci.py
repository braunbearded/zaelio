#!/usr/bin/env python3
"""Validate a prepared version branch and update its post-merge F-Droid metadata."""

import argparse
from pathlib import Path
import re


def release_info(root, branch):
    config = (root / 'app/build.gradle').read_text()
    name = re.search(r"^\s*versionName = '(\d+\.\d+\.\d+)'\s*$", config, re.MULTILINE)
    code = re.search(r'^\s*versionCode = (\d+)\s*$', config, re.MULTILINE)
    version_branch = re.fullmatch(r'v(\d+\.\d+\.\d+)(?:[-/].*)?', branch)
    if not name or not code or not version_branch or version_branch[1] != name[1]:
        raise ValueError('Version branch must match versionName in app/build.gradle')
    version, version_code = name[1], int(code[1])
    if version_code <= 0:
        raise ValueError('versionCode must be positive')
    notes = (root / f'fastlane/metadata/android/en-US/changelogs/{version_code}.txt').read_text()
    if not notes.strip() or len(notes) > 500:
        raise ValueError('Fastlane changelog must contain 1–500 characters')
    return version, version_code


def update_metadata(path, version, code, commit):
    if not re.fullmatch(r'[0-9a-f]{40}', commit):
        raise ValueError('F-Droid requires a full release commit hash')
    text = path.read_text()
    for key, value in (('versionName', version), ('versionCode', code), ('commit', commit),
                       ('CurrentVersion', version), ('CurrentVersionCode', code)):
        text, count = re.subn(rf'(?m)^(\s*(?:- )?{key}: )[^\n]+$',
                             lambda match: match[1] + str(value), text, count=1)
        if count != 1:
            raise ValueError(f'Missing F-Droid metadata key: {key}')
    path.write_text(text)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('branch')
    parser.add_argument('--metadata-commit')
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    try:
        version, code = release_info(root, args.branch)
        if args.metadata_commit:
            update_metadata(root / 'docs/fdroiddata/com.zaelio.app.yml',
                            version, code, args.metadata_commit)
    except (OSError, ValueError) as error:
        parser.error(str(error))
    print(f'version={version}\ncode={code}\ntag=v{version}')
