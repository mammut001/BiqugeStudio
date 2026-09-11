# 阅笺 10-round usability audit

**Date:** 2026-08-16  
**Workspace HEAD:** `d01e5c5` (`fix/slider-seek-no-flicker`, not merged to `main`)  
**Compared-to `main`:** `3115c3c` Fill reader pages down to the footer (#22)  
**Method:** read shipped Compose/Java units, run JVM reader tests, exercise emulator `Medium_Phone` (1080×2400) package `app.maoyankanshu.novel.selfuse`.  
**This document does not implement fixes.** In-flight slider work is described as it exists on this branch; it is **not** claimed “fixed in this goal”.

**Unit tests (grounding):** `PageIndexTest` 33, `ProgressMathTest` 5, `OpenProgressGateTest` 8, `PageLayoutTest` 9, `ProgressiveOpenTest` 13, `ReaderLeaveSaveTest` 5 — **73 tests, 0 failures, 0 errors**. Full Gradle log + parsed XML: implementer scratch `audit-unit.txt`.

**Device:** emulator-5554 available. Reader open + slider seek captured as `audit-r1-reader-open.png` and `audit-r2-slider-after.png`. Physical PKG110 was not attached for this audit.

---

## Round 1 — Reader body / pagination

**Surface:** `PageIndex` + `ReaderScreen` body `HorizontalPager` + `PageLayout`  
**Exercised:** `useApproxPaging = textFullyLoaded && book.text.length > PageIndex.MAX_EXACT_MEASURE_CHARS` (200_000). Exact path uses `TextMeasurer` + `pageStartOffsets`. Approx path slices `approximatePageText` by char count. Emulator opened 22 万字《残卷》(hard-wrapped TXT). Footer showed `791 / 981 · 81%`; last line sat just above the clock strip.

**Finding — P1 (usable but looks broken):** Large books never get line-accurate pages. `PageIndex.MAX_EXACT_MEASURE_CHARS = 200_000` (`PageIndex.kt`). A 220k-char novel is forced onto virtual char slices. Combined with Compose wrap, a source line of ~22 CJK plus screen wrap leaves 1–3 orphan characters on the next line (see emulator `audit-r1-reader-open.png`: “拂过 / 一枚枚玉简… 里默 / 念一遍”).

**Repro:** Import a TXT longer than 200_000 characters with existing newlines; open reader; look at any mid-book page.

**Not a half-blank page on this build:** fill/safety is `APPROX_FILL_FACTOR = 0.97`, `PAGE_BOTTOM_SAFETY_LINE_FRACTION = 0.2`, `BODY_FOOTER_GAP_DP = 4` (`PageIndex.kt`, `PageLayout.kt`). The old “lower half empty” layout is not present on `main` #22 / this branch.

---

## Round 2 — Progress slider / page jump

**Surface:** `ReaderScreen` chrome `Slider` (`valueRange 0f..1000f`) → `jumpToProgress` → `animateToPage`  
**Exercised:** Source of `animateToPage` / `shouldAnimatePageTurn`; emulator swipe on the SeekBar from ~left to ~75%.

**Finding — P0 on `main` (unusable seek):** `main` (`3115c3c`) `animateToPage` always calls `pagerState.animateScrollToPage` when page-turn animation is on (`ReaderReadingPolish.pageTurnDurationMs` = 280). HorizontalPager then walks every intermediate page with `PageTurnEffect` 3D tilt. Dragging the bottom slider from 0% to mid-book flashes hundreds of pages.

- **Severity:** P0 (cannot seek; “疯狂闪屏”)  
- **Shipped path (`main`):** `ReaderScreen.kt` `Slider.onValueChangeFinished` → `jumpToProgress` → `animateToPage` → `animateScrollToPage`  
- **Repro:** Open a multi-hundred-page book → tap center → drag the bottom progress slider across a large range.

**Workspace note (not a “fixed in this goal” claim):** Branch `fix/slider-seek-no-flicker` (`d01e5c5`, PR #23) adds `PageIndex.shouldAnimatePageTurn` (true only when `|from-to|==1`) and `sliderScrubbing` so far jumps `scrollToPage`. Emulator seek on that branch landed on a stable page at 75% (`audit-r2-slider-after.png`) without a multi-page animation. **`main` still has the P0.**

---

## Round 3 — Open / restore progress

**Surface:** `ReaderActivity.openBook` + `OpenProgressGate` + `ReaderScreen` `restoreApplied` / `key(book.id, textFullyLoaded)` pager remount  
**Exercised:** `OpenProgressGateTest` (holds saved progress until restore); emulator re-opened 《残卷》 and landed at `791 / 981 · 81%` (previous session progress).

**Finding — P1:** Progressive open remounts the entire pager when `textFullyLoaded` flips (`ReaderScreen.kt` `key(book.id, textFullyLoaded)`). Window pass uses `pageStarts` on a 48 KiB slice (`ProgressiveTextOpen.FIRST_WINDOW_BYTES`) with `textFullyLoaded = false`, so `OpenProgressGate` **blocks** page-turn commits (`mayCommitProgressFromPageTurn` requires both flags). That is correct for not clobbering 0%. Side effect: during the window, user page-turns are discarded from library progress; after swap the pager remounts to `pageForProgress(saved)`. Fast tap-turn during the first second of a >512 KiB file can feel like “I turned pages and it jumped back”.

**Repro:** Open a file ≥ 512 KiB (`ProgressiveTextOpen.PROGRESSIVE_BYTE_THRESHOLD`); tap next page immediately; wait for full decode.

**No defect on restore math itself:** `OpenProgressGate.progressAfterPageTurn` holds `heldProgress` until `textFullyLoaded && restoreApplied` (8/8 tests green).

---

## Round 4 — Import / share-in

**Surface:** `SearchActivity` + `LocalBookImport` + `ImportIntentUris` + SAF `OpenDocument`  
**Exercised:** Source of picker MIME, 32 MiB cap, VIEW/SEND URI filter; prior session successfully imported `content://media/external/downloads/…` TXT via VIEW.

**Finding — P1:** In-app picker is `OpenDocument.launch(arrayOf("text/plain", "application/epub+zip"))` (`SearchActivity.kt`). Many Downloads / WeChat / QQ providers expose TXT as `application/octet-stream` or `*/*`. Those files **do not appear** in the picker. Share/Open-with still works (manifest includes `*/*` on SEND). Users who only tap「导入本地 TXT / EPUB」can conclude “import is broken”.

**Repro:** Put a `.txt` from a chat app into Downloads without `text/plain`; tap 导入书籍 → 导入本地; file is missing. Share the same file to 阅笺 and it imports.

**Also:** `LocalBookImport.MAX_IMPORT_BYTES = 32 * 1024 * 1024`; empty body throws `IllegalArgumentException("empty")`. Not unusable for typical novels; hard fail for huge or empty files.

---

## Round 5 — Main shelf / chrome shell

**Surface:** `BiqugeApp` `Scaffold` + `ShelfScreen` + `BookCard` + `StoreScreen`  
**Exercised:** Emulator shelf: 继续阅读 / 全部书籍; tap「继续阅读」opens `ReaderActivity`; card body `onClick` opens `BookDetailActivity` (`ShelfScreen.openDetail`).

**Finding — P2 (friction, not crash):** Tapping the book card never opens the reader. Only the tonal「开始阅读 / 继续阅读」button calls `openReader`. A user who taps the title/cover lands on detail and must tap 开始阅读 again. Empty shelf has a working empty-state CTA to import.

**Repro:** On a non-empty shelf, tap the book title (not the purple button) → detail, not reader.

**No defect:** Bottom tabs 书架 / 导入书籍 / 我的 navigate; `StoreScreen` is import-only (no second library list).

---

## Round 6 — Hard-wrap TXT + paragraph indent

**Surface:** `PageIndex.shouldApplyParagraphIndent` + `ReaderScreen` `pageTextStyle` / default `ReaderPreferences.paragraphIndent`  
**Exercised:** Emulator 《残卷》 generated as 22-char hard-wrapped lines. Visual: staircase / mid-phrase line breaks (see Round 1 screenshot).

**Finding — P1 (reading quality — this is a large part of “没法用”):** `shouldApplyParagraphIndent` returns true whenever the previous char is `\n` (`PageIndex.kt`). Hard-wrapped 笔趣阁-style TXT uses `\n` as a visual line, not a paragraph. With indent on, **every wrapped line** gets a first-line indent (`bodyTextStyle.textIndent`), so a 22-char line plus 2-em indent overflows and leaves a dangling character. Approx paging then slices mid-line. Result is a page that is “full” but not readable as prose.

**Repro:** Import a TXT that already wraps at ~20–30 CJK per line; leave 段首缩进 on (default); open any page.

---

## Round 7 — Progressive large-TXT first window

**Surface:** `ProgressiveTextOpen` + `ReaderActivity.openBook`  
**Exercised:** `ProgressiveOpenTest` 13/13; source of `firstWindowText` / `byteOffsetForProgress`.

**Finding — P2:** First window is 48 KiB centered on `byteOffsetForProgress(fileSize, savedProgress)` — a **byte** mapping, not a character/page mapping. UTF-8 CJK is 3 bytes/char, so the window is not guaranteed to start on a paragraph or even the same “page” the user last saw. After full decode, Round 3 remount corrects to `pageForProgress`. First paint can show the wrong neighborhood for ~one decode of a multi-MB file.

**Repro:** Save progress mid-book on a >512 KiB UTF-8 novel; kill and reopen; watch the first screen before “全文” settles.

**No defect:** Window decode is bounded; empty/missing `book_id` finishes the activity (`ReaderActivity.openBook`).

---

## Round 8 — Leave-save / resume

**Surface:** `ReaderLeaveSave.persistAsync` + `ReaderScreen` `snapshotFlow { progress }.debounce(500)` + `BiqugeApp` `awaitIdle`  
**Exercised:** `ReaderLeaveSaveTest` 5/5; source of 500 ms debounce and 1_500 ms `awaitIdle`.

**Finding — P2:** Progress is debounced 500 ms (`ReaderScreen.kt`). A force-stop or process death within that window can drop the last slider/page position. `awaitIdle(1500)` on shelf `ON_RESUME` can still show stale “已读 0%” if IO is slow. Emulator reopen in Round 3 **did** restore 81%, so the happy path works.

**Repro:** Drag slider or turn a page and immediately swipe the app away from recents (kill) within 500 ms; reopen.

**No defect on stats double-count:** `ReaderActivity` only records RESUMED time after first readable body (`ReaderActiveSession`).

---

## Round 9 — Reader chrome / tap zones

**Surface:** `PageIndex.tapZoneAction` (left/center/right thirds) + chrome `AnimatedVisibility` + appearance / TOC / TTS icons  
**Exercised:** `PageIndexTest` tap-zone cases; emulator center tap opened chrome; volume hook exists on `ReaderActivity`.

**Finding — no defect on this check:** Zones are Kindle-style thirds (`ZONE_LEFT_END = 1/3`, `ZONE_RIGHT_START = 2/3`). Center toggles chrome; chrome hides the footer so the slider is not stacked on the clock. Adjacent tap/volume still animate (intended). Appearance / TOC / TTS icons are reachable from the top bar.

---

## Round 10 — TTS follow + deferred TOC on large books

**Surface:** `ReaderTtsController` / `TtsPageFollow` + `ReaderScreen` chapter `LaunchedEffect`  
**Exercised:** Source of deferred chapter scan; `TtsPageFollow.cuesForApproximatePages` maps chunk offsets onto char-page boundaries.

**Finding — P2 (two related gaps, not crashes):**

1. **TOC deferred:** For `book.text.length > 200_000`, chapters are **not** scanned until the user opens TOC (`ReaderScreen.kt` `if (!showToc && book.text.length > MAX_EXACT_MEASURE_CHARS) return`). Footer chapter label stays「全文」; 上一章/下一章 do nothing useful until TOC is opened once.

2. **TTS page follow on approx books:** `TtsPageFollow` uses the same char-grid pages as Round 1. Follow can flip the pager at a character boundary that is mid-sentence on screen (same orphan-line problem). ColorOS engine workarounds exist in-tree; they are not re-audited here as a new product surface.

**Repro (TOC):** Open a >200k-char book; look at footer chapter and tap 下一章 **without** opening 目录 first.

---

## Unusable / P0–P1 index

| Sev | Defect | Shipped path | One-line repro |
|-----|--------|--------------|----------------|
| **P0** | Slider seek strobes through every page | `main` `ReaderScreen.animateToPage` → `animateScrollToPage` | Open a long book → chrome → drag progress slider far |
| **P1** | Approx char-pages + wrap = unreadable lines | `PageIndex.MAX_EXACT_MEASURE_CHARS` + `approximatePageText` | Open any TXT >200k chars |
| **P1** | Hard-wrap `\n` treated as paragraph indent | `PageIndex.shouldApplyParagraphIndent` + default indent on | Open a 笔趣阁-style hard-wrapped TXT |
| **P1** | SAF picker hides many TXT | `SearchActivity` `OpenDocument` MIME `text/plain` + `epub` only | Import from Downloads when provider MIME is `octet-stream` |
| **P1** | Progressive→full pager remount jumps | `ReaderScreen` `key(book.id, textFullyLoaded)` | Open ≥512 KiB file and turn pages during the first window |
| P2 | Card tap opens detail, not reader | `ShelfScreen.openDetail` | Tap book title on shelf |
| P2 | Last 500 ms progress can be lost | `snapshotFlow(progress).debounce(500)` | Kill app immediately after a page turn |
| P2 | Large-book TOC/chapter buttons idle | chapter `LaunchedEffect` skips until `showToc` | Open >200k book; tap 下一章 |

---

## What is *not* claimed

- This audit does **not** merge PR #23 or change production code.
- Fill-to-footer (#22 on `main`) is already shipped; Round 1 did **not** re-find the half-blank page.
- No store/compliance/new-feature review.
