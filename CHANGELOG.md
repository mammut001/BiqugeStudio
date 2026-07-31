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

### Changed

- README and `RELEASECHECKLIST.md`: changelog, dependency-update, and release-tag guidance.

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
