# Repository Guidelines

## Non-Negotiable Completion Checklist
Before reporting a code change as done:

1. Decide whether docs changed.
   - If behavior, commands, schema, project structure, release flow, or user-facing features changed, update the relevant docs in the same change (`README.md`, `CHANGELOG.md`, `AGENTS.md`, or feature docs).
   - If docs are not needed, mention why in the final response.
2. Run `./gradlew testDebugUnitTest` unless the user explicitly asks not to.
3. For UI, resource, manifest, Gradle, or build-impacting changes, also run `./gradlew assembleDebug`.
4. Inspect command output for real failures, warnings, skipped tests, or build issues; do not rely only on the final success line.
5. Do not create commits unless the user explicitly asks.

## Project Structure & Module Ownership
This is a single-module Android app without Google Play Services.

- `app/src/main/java/com/zaelio/app/` contains app code.
- `MainActivity.java` owns routing, lifecycle, top app bar actions, and bottom navigation.
- `TrackingDatabase.java` handles SQLite schema, destructive upgrades, seed data, and data access. Current schema version is 8.
- `theme/ThemeStore.java` stores theme mode, accent color, font scale, field size, session field collapse defaults, and derived palette values.
- `ui/AppUi.java` builds shared Material-style widgets, dialogs, spacing, touch-size constants, list rows, action icons, and input helpers.
- `ui/SettingsUi.java` renders settings, data transfer/about screens, and session field collapse defaults.
- `HomeUi.java` renders session/tracker overviews, delete gestures, overflow-menu delete, and overview drag ordering.
- `ReorderHelper.java` contains shared constrained drag-reorder touch logic.
- `TrackerFlowUi.java` owns tracker editor, session flow, tracker selection, session autosave debounce/batching, and timer lifecycle.
- `FieldInputUi.java` renders session field controls, collapsible field cards, timers, numeric controls, and multiline text.
- `DeleteGestureHelper.java` owns shared long-press/left-swipe delete behavior and delete dialog guarding.
- `TrackerJsonRepository.java`, `BackupJsonRepository.java`, `JsonUtil.java`, `FormatUtil.java`, and `Models.java` cover JSON persistence, backup import/export, formatting, and models.
- `app/src/main/res/` contains resources and styles.
- `app/src/main/AndroidManifest.xml` defines the entry point and the `VIBRATE` permission used for delete-selection feedback.

## Build, Test, and Development Commands
Use the Gradle wrapper from the repository root.

- `./gradlew assembleDebug` builds a debug APK at `app/build/outputs/apk/debug/zaelio-debug.apk`.
- `./gradlew testDebugUnitTest` runs local JVM/Robolectric tests.
- `./gradlew assembleRelease` builds a release APK if signing env vars are configured; otherwise it writes an unsigned APK at `app/build/outputs/apk/release/zaelio.apk`.
- `./gradlew clean` removes build outputs.

Release signing env vars:

- `ANDROID_KEYSTORE_PATH`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Keep Gradle wrapper files committed: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, and `gradle/wrapper/gradle-wrapper.properties`.

## Coding Style
Follow the existing Java style:

- 4-space indentation.
- Package names lowercase; app code uses `com.zaelio.app`.
- Keep package declarations aligned with file paths.
- `PascalCase` for classes; `camelCase` for methods, fields, and variables.
- Prefer descriptive UI helpers such as `primaryButton()`, `showHome()`, and `navItem()`.
- Keep changes small and localized; reuse existing helpers before adding new ones.

## UI Guidelines
Keep UI changes consistent with the current Material 3 direction:

- Top app bar with app title and overflow menu.
- Bottom navigation with two equal-width tabs.
- Icon and label color indicate the selected tab only.
- Footer touch areas stay rectangular and extend to the edges.
- Settings, data transfer, and about screens stay compact and scrollable on small screens.
- Shared screen/dialog helpers belong in `ui/AppUi.java`.
- Screen-specific settings logic belongs in `ui/SettingsUi.java`.
- Overview lists, delete gestures, and drag ordering belong in `HomeUi.java`.
- Shared delete gesture mechanics belong in `DeleteGestureHelper.java`.
- Shared reorder mechanics belong in `ReorderHelper.java`; persistence stays with the owning screen/database code.
- Tracker editing and session routing belong in `TrackerFlowUi.java`.
- Session input widgets and collapse UI belong in `FieldInputUi.java`.
- Avoid scattering repeated `ui.px(...)` literals through screen code; add/reuse `AppUi` spacing/touch helpers.
- Use subtle, short animations for expand/collapse; avoid animating initial screen render.

## Persistence and Performance Notes
Be careful before changing database, editor, autosave, or import/export flows.

- Database migrations must be backward compatible. `TrackingDatabase.migrateToFieldsOnly()` is sensitive to cursor column indexes.
- Editing a tracker through `TrackerJsonRepository.updateTracker()` rebuilds fields and can delete existing `field_records`; avoid accidental session data loss.
- Overview ordering is persisted through `overviewOrder`; new rows should remain visible near the top and migrations should preserve the old newest-first default order.
- Session input autosave should stay debounced and batched; avoid per-keystroke or per-timer-tick database writes.
- Timer state in `TrackerFlowUi` is in-memory and should be cleared on screen exit or activity destruction.
- Timer UI ticks should update display only; persistence should happen on user changes, debounce flush, or exit.
- Delete candidate feedback must remain clear even when the accent color is red; keep non-color cues such as strikethrough/scale/alpha alongside vibration.
- Delete dialogs should not stack; interactive inputs/dropdowns should not accidentally trigger delayed delete gestures.

## Testing Guidelines
Unit tests live under `app/src/test/` and use JUnit 4 plus Robolectric for Android SQLite coverage. Instrumented tests, if needed, belong under `app/src/androidTest/`.

Name tests after the behavior being verified, such as `TrackingDatabaseTest`.

Prioritize tests for:

- SQLite schema shape and destructive upgrades, especially keeping removed item tables/columns out.
- Tracker/session JSON import/export and editor autosave behavior.
- Session record preservation when tracker definitions are edited or imported.
- Numeric, duration, and string field parsing, field-size behavior, autosave batching, and session field collapse behavior.
- Delete gesture edge cases that could trigger accidental or stacked dialogs.

## Documentation Rules
Update docs in the same change when any of these change:

- User-facing features or behavior.
- Settings, gestures, navigation, import/export, persistence, or backup behavior.
- Commands, build tooling, release flow, CI, or environment setup.
- Database schema version, migrations, project structure, or module ownership.
- Known risks or maintenance guidance.

Common targets:

- `README.md` for user-visible features, build instructions, project structure, and testing notes.
- `CHANGELOG.md` for user-visible changes under `Unreleased` unless doing a release.
- `AGENTS.md` for contributor/agent workflow, ownership, risks, and required checks.
- `docs/` or Fastlane metadata for release/F-Droid/package-specific changes.

If docs are not updated, final response must include: `Docs unchanged: <reason>`.

## Release & F-Droid Guidelines
For every new public version, prefer `scripts/release.sh`; it prompts for version, changelog, checks, commit/tag, push, and updates F-Droid metadata in a second commit with the full release commit hash.

Manual flow:

1. Increase `versionCode` and `versionName` in `app/build.gradle`.
2. Update `CHANGELOG.md` with user-visible changes.
3. Run `./gradlew testDebugUnitTest` and, for release-impacting changes, `./gradlew assembleDebug` or `./gradlew assembleRelease`.
4. Commit the version change, then create a matching tag such as `v1.1.0`.
5. Push the branch and tag; the tag triggers `.github/workflows/release.yml` to build and attach the signed APK to a GitHub Release.
6. For F-Droid, ensure `LICENSE`, README metadata, `fastlane/metadata/android/en-US/`, screenshots, and changelog are current.
7. Always update `docs/fdroiddata/com.zaelio.app.yml` and submitted `fdroiddata` metadata for the new `versionCode` with a full commit hash, `Binaries`, and `AllowedAPKSigningKeys`.

Before F-Droid submission or dependency/toolchain upgrades, verify F-Droid buildserver support for the current Android Gradle Plugin and `compileSdk`.

Keep the app free of proprietary services, trackers, ads, Firebase, and Google Play Services.

## Commit & Pull Request Guidelines
Do not create git commits unless the user explicitly asks for a commit. The only routine exception is an explicitly requested release/tagging flow.

Recent history uses short, imperative commit messages. Keep commits focused and descriptive.

Pull requests should include:

- Short summary of the user-visible change.
- Build/runtime impact.
- Screenshots or recordings for UI changes.
- Linked issues when applicable.

## Security & Configuration
- Do not commit `local.properties`, keystores, passwords, API keys, or other machine-specific files.
- GitHub release signing must use repository secrets.
- The app is Android-only, SQLite-backed, and should not gain hidden Google service dependencies.
- For F-Droid/OSS builds, only use free/open dependencies.
