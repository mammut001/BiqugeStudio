# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html)
aligned with `versionName` / `versionCode` in `app/build.gradle`.

Dates and bullets below are derived from **git history** in this repository.
They do **not** assert a Google Play ship date, store listing URL, or remote CI status.

## [Unreleased]

### Added

- `CHANGELOG.md` (this file) with Keep a Changelog layout.
- Dependabot weekly updates for Gradle and GitHub Actions (`.github/dependabot.yml`).
- JVM tests: blank/whitespace/HTTP remote URL rejection; pure `canAcceptUi` finishing/destroyed matrix (`ImportUiGate`).
- Remote/Web import: in-flight **取消下载 / 取消导入** (`Job.cancel`) with strings + TalkBack content descriptions; loading status polite `liveRegion`.
- `ReaderLeaveSave` + `ProgressMath.clampProgress`: pure leave-duration / 0…1000 helpers with JVM tests (`ReaderLeaveSaveTest`).
- `SearchWorkOutcomes`: pure cancel / local multi-URI batch Toast / oversized-file classification with JVM tests (`SearchWorkOutcomesTest`).
- Search: in-flight **取消导入 / 取消搜索** (`Job.cancel`) strings + TalkBack content descriptions.
- `ProfileBackupOutcomes`: pure cancel / backup Toast / restore success·empty·invalid classification with JVM tests (`ProfileBackupOutcomesTest`).
- Profile: in-flight **取消备份 / 取消恢复** (`Job.cancel`) strings + TalkBack content descriptions.
- `BookDetailExportOutcomes`: pure cancel / export Toast classification with JVM tests (`BookDetailExportOutcomesTest`).
- Book detail: in-flight **取消导出** (`Job.cancel`) strings + TalkBack content descriptions.

### Changed

- README and `RELEASECHECKLIST.md`: changelog, dependency-update, and release-tag guidance.
- README: document API 23 vs `networkSecurityConfig` (API 24+), HTTPS import `Job` cancel (button + back), TalkBack `liveRegion` on remote/web loading and errors.
- `canAcceptUi` extracted to `ImportUiGate.kt` for shared use and JVM tests.
- README: Compose reader leave-save uses process-lifetime IO scope (not per-`onDispose` `CoroutineScope`) and `rememberUpdatedState` for latest 0…1000 progress.
- README: `SearchActivity` shares cancel / `canAcceptUi` conventions with Remote/Web import (local URI + HTTPS Wikisource).
- README: `ProfileScreen` CreateDocument/OpenDocument backup shares cancel / `canAcceptUi` / polite `liveRegion` conventions.
- README: `BookDetailActivity` CreateDocument single-book TXT export shares cancel / `canAcceptUi` / polite `liveRegion` conventions.

### Fixed

- `RemoteImportActivity` / `WebImportActivity`: track import `Job` on `rememberCoroutineScope`; rethrow `CancellationException` (user cancel / back / leave) so it is never shown as import failure; skip Toast / state / finish when the host Activity cannot accept UI.
- Loading no longer disables the back affordance (back cancels the Job and leaves).
- TalkBack: polite `liveRegion` on remote/web URL validation, loading status, and import failure messages.
- `LibraryStore.save`: single `SharedPreferences.edit().apply()` (CommitPrefEdits lint).
- `ReaderScreen` `DisposableEffect(book.id)` `onDispose`: no unstructured `CoroutineScope(Dispatchers.IO).launch` per leave; clamp progress 0…1000; skip non-positive reading duration for `ReadingStats`.
- `SearchActivity`: track local shelf / SAF-URI import / HTTPS Wikisource work as `Job`s on `rememberCoroutineScope`; rethrow `CancellationException` so cancel/back/leave is never fail Toast or error liveRegion; clear busy flags; skip Toast/state when host cannot accept UI; back stays enabled while busy.
- `ProfileScreen`: track CreateDocument backup / OpenDocument restore as `Job`s on `rememberCoroutineScope` + `Dispatchers.IO`; rethrow `CancellationException` so cancel/leave is never fail Toast or error dialog; clear busy flags; skip Toast/state when host cannot accept UI; soft cancel via dialog button / dismiss; polite `liveRegion` on loading and error dialog text.
- `BookDetailActivity`: track CreateDocument TXT export (`LibraryStore.exportBook`) as a `Job` on `rememberCoroutineScope` + `Dispatchers.IO`; rethrow `CancellationException` so cancel/leave is never fail Toast; clear busy flags; skip Toast/state when host cannot accept UI; soft cancel via dialog button / dismiss; polite `liveRegion` on loading status.

## [1.0.1] — versionCode 2

`versionName` / `versionCode` set in git (`app/build.gradle`) on 2026-07-27.
Summary of commits on the 1.0.1 line through the Auto Backup policy work (2026-07-31).

### Added

- Compose + Material 3 surfaces for shelf (filters, sort, optional author grouping), store, discover, profile, search/import, book detail, HTTPS remote/web import, and Compose `ReaderActivity`.
- System share/open import: `ACTION_VIEW` / `ACTION_SEND` / `ACTION_SEND_MULTIPLE` (TXT/EPUB; multi-URI capped at 20).
- EPUB: OPF `dc:title` / `dc:creator`, optional cover extract (≤ 2 MiB) with BookCard display and ZIP backup of covers, MIME `application/epub+zip` when the name lacks `.epub`.
- Chapter headings: Chinese matter + English Chapter/Prologue/Epilogue; Roman numerals with false-positive rejection.
- Import safety: 32 MiB local/stream bounds, HTTPS Content-Length fail-fast, EPUB ZipInputStream expansion cap.
- Encoding: BOM/XML-aware EPUB decode; UTF-32 LE/BE BOM for TXT/EPUB.
- Classic TTS / auto-scroll via `LegacyReaderActivity` entry from Compose reader toolbar.
- Accessibility: TalkBack roles/labels, ≥ 48dp targets on key lists and profile rows.
- Unit tests for offline library/import helpers and shelf filters; instrumented Compose smoke wiring (Espresso 3.7+ force for API 35/36).
- OSS hygiene: `CONTRIBUTING`, `CODE_OF_CONDUCT`, `SECURITY`, issue/PR templates, GitHub Actions workflow for unit test + lint + `assembleDebug`.
- Explicit Auto Backup include-only rules (`backup_rules.xml` + `data_extraction_rules.xml`) for library prefs, books, and covers.

### Changed

- `compileSdk` / `targetSdk` **36**; SplashScreen API; display-name unification via `app_name`.
- Reader progress saves debounced off the main thread; cover decode off the main thread with bounded sample size.
- Legacy seed-author matching for older library rows.

### Fixed

- Search SAF import opens the picker once per `EXTRA_IMPORT`.
- Remote import type/title detection after HTTP redirects.
- Reader/API minSdk 23 NewApi resolution without blanket suppress or desugaring.
- BookCard “unstarted” progress label and TalkBack CTA at position 0.
- EPUB spine chapter join and HTML/entity stripping for cleaner body text.
