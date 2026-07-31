# Contributing to BiqugeStudio (阅笺)

Thanks for helping improve this local Android reader. This guide covers workflow, style, tests, pull-request scope, and secrets. For community norms see [CODE_OF_CONDUCT.md](./CODE_OF_CONDUCT.md). For vulnerability reports see [SECURITY.md](./SECURITY.md).

## Scope of this repository

- **In scope:** the `BiqugeStudio` Android app module (`:app`), its docs, and GitHub project hygiene under this tree.
- **Out of scope:** sibling projects or other apps (for example FocusApp). Do not open PRs that change unrelated trees.
- **License:** no open-source license has been chosen yet. That is an **explicit maintainer decision**. Do not add a `LICENSE` file or relicense third-party code without maintainer approval.

## Development environment

| Item | Expectation |
|------|-------------|
| IDE | Android Studio (current stable) |
| JDK | **17** (Android Studio bundled JBR is fine) |
| SDK | `compileSdk` / `targetSdk` **36**; install platform packages as prompted |
| minSdk | **23** |
| Build | Gradle **wrapper** only (`./gradlew`) — do not require a global Gradle install |

### Open the project

1. **File → Open** the `BiqugeStudio` directory (this repo root).
2. Let Android Studio sync Gradle; point at a local Android SDK if asked.
3. Run the `app` configuration on an emulator or device.

### Command-line workflow

```bash
# Optional on macOS when using Studio’s JBR
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

chmod +x gradlew

# Local check (mirrors CI intent)
./gradlew :app:testDebugUnitTest :app:lint :app:assembleDebug

# Debug APK
./gradlew :app:assembleDebug

# Release / Play (requires local signing files — never commit them)
./gradlew :app:assembleRelease
./gradlew :app:bundleRelease
```

Instrumented / Compose UI tests need a device or emulator and are **not** required for every PR:

```bash
# ./gradlew :app:connectedDebugAndroidTest
```

## Style and code conventions

- Prefer **Kotlin + Jetpack Compose + Material 3** for new UI. Keep `LegacyReaderActivity` (Java TTS / auto-scroll) unless a change explicitly migrates that path.
- Match surrounding code: package `app.maoyankanshu.novel.selfuse`, existing naming, and file layout under `app/src/main/java/...`.
- Do **not** change:

  | Contract | Rule |
  |--------|------|
  | `applicationId` / package | Keep `app.maoyankanshu.novel.selfuse` (coexistence with older installs) |
  | `ReaderActivity.EXTRA_ID` / `LegacyReaderActivity.EXTRA_ID` | Always `"book_id"` |
  | Library / SharedPreferences keys | No renames without a migration plan |
  | Display name | Change only `app_name` in `strings.xml` |
  | Auto Backup policy | Include-only lists in `res/xml/backup_rules.xml` + `data_extraction_rules.xml` must stay aligned with real storage paths; do not add orphan `<exclude>` paths (lint `FullBackupContent`). Update both XML files and `AndroidManifest` attributes together |

- **On-device storage map** (for backup and privacy docs): SharedPreferences `local_library`, `bookmarks`, `reader_preferences`, `reading_history`, `reading_stats`; files under `getFilesDir()/books` and `covers`.

- Network imports stay **HTTPS-only** (no cleartext HTTP). Local TXT/EPUB remain offline.
- Prefer small, focused diffs. Avoid drive-by refactors, dependency upgrades, or formatting-only churn in feature PRs.
- New user-facing strings go through resources when the app already localizes that surface.

## Tests

| Layer | Path | Command | When |
|-------|------|---------|------|
| JVM unit tests | `app/src/test/` | `./gradlew :app:testDebugUnitTest` | **Required** for logic you change (encoding, progress, import helpers, etc.) |
| Android Lint | — | `./gradlew :app:lint` | **Required** before merge; fix new issues you introduce |
| Debug assemble | — | `./gradlew :app:assembleDebug` | **Required** — PR must build |
| Instrumented | `app/src/androidTest/` | `connectedDebugAndroidTest` | When touching UI that already has smoke coverage, or adding meaningful UI tests |

CI runs unit tests, lint, and `assembleDebug` on every push and pull request (see [`.github/workflows/android.yml`](./.github/workflows/android.yml)). Keep PRs green there.

## Pull request scope

- **One concern per PR** (bugfix, feature, or docs). Split large work into reviewable steps.
- Fill out the PR template: summary, test plan, risk notes.
- Link related issues when they exist.
- Do not bundle secrets, keystores, personal `local.properties`, or binary dumps of user books.
- Screenshots or short notes help for UI changes; not required for pure logic/docs.
- Maintainers may ask to rebase or shrink scope before merge.

## Secrets and local-only files

**Never commit:**

| Path / pattern | Why |
|----------------|-----|
| `keystore.properties` | Signing passwords and paths |
| `keystore/` (`.jks` / keystores) | Release signing material |
| `local.properties` | Machine-specific SDK paths |
| API keys, tokens, Play service account JSON | Credentials |

These paths are listed in [`.gitignore`](./.gitignore). If you accidentally commit a secret, rotate it and contact maintainers privately ([SECURITY.md](./SECURITY.md)); do not rely on history rewrite alone.

Release builds need a **local** `keystore.properties` pointing at a keystore outside version control. Debug builds do not require it.

## Documentation

- Update [README.md](./README.md) when user-visible behavior, build steps, or CI commands change.
- Record user-visible and release-relevant work under **`[Unreleased]`** in [CHANGELOG.md](./CHANGELOG.md); maintainers move that block into a version section when cutting a release (see [RELEASECHECKLIST.md](./RELEASECHECKLIST.md)).
- Play / privacy process lives in [PRIVACY.md](./PRIVACY.md) and [RELEASECHECKLIST.md](./RELEASECHECKLIST.md); keep them consistent with code.
- If you change where library/progress/prefs live, update Auto Backup XML (`backup_rules` + `data_extraction_rules`), [PRIVACY.md](./PRIVACY.md) §5, and the README data-safety row in the same change.
- Dependency bumps: prefer Dependabot PRs from [`.github/dependabot.yml`](./.github/dependabot.yml) (Gradle + GitHub Actions, weekly); do not mix large version upgrades into unrelated feature PRs.

## Questions

Use GitHub Issues (bug or feature templates) for product and engineering discussion. Do not use public issues for security vulnerabilities.
