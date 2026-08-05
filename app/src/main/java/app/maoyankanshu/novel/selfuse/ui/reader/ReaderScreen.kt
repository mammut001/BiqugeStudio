package app.maoyankanshu.novel.selfuse.ui.reader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt
import app.maoyankanshu.novel.selfuse.Book
import app.maoyankanshu.novel.selfuse.BookmarkStore
import app.maoyankanshu.novel.selfuse.LibraryStore
import app.maoyankanshu.novel.selfuse.R
import app.maoyankanshu.novel.selfuse.ReaderActivity
import app.maoyankanshu.novel.selfuse.ReaderPreferences
import app.maoyankanshu.novel.selfuse.ReadingHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, FlowPreview::class)
@Composable
fun ReaderScreen(
    book: Book,
    activity: ComponentActivity,
    onClose: () -> Unit,
    /**
     * False while [book.text] is only a progressive first-window around saved progress.
     * Footer keeps the restored progress; full virtual pagination activates when true.
     */
    textFullyLoaded: Boolean = true,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val preferences = remember { ReaderPreferences.get(context) }
    val scope = rememberCoroutineScope()
    val textMeasurer = rememberTextMeasurer()
    val tapHint = stringResource(R.string.reader_tap_hint)
    val backCd = stringResource(R.string.reader_back_cd)
    val appearanceCd = stringResource(R.string.reader_appearance_cd)
    val findCd = stringResource(R.string.reader_find_cd)
    val bookmarksCd = stringResource(R.string.reader_bookmarks_cd)
    val tocCd = stringResource(R.string.reader_toc_cd)
    val ttsCd = stringResource(R.string.reader_tts_cd)
    val ttsStopCd = stringResource(R.string.reader_tts_stop_cd)
    val fontSmallerCd = stringResource(R.string.reader_font_smaller_cd)
    val fontLargerCd = stringResource(R.string.reader_font_larger_cd)
    val lineHeightSmallerCd = stringResource(R.string.reader_line_height_smaller_cd)
    val lineHeightLargerCd = stringResource(R.string.reader_line_height_larger_cd)

    var theme by remember { mutableIntStateOf(preferences.theme()) }
    var fontSizeSp by remember { mutableIntStateOf(preferences.fontSize()) }
    var lineHeightMultiplier by remember { mutableFloatStateOf(preferences.lineHeightMultiplier()) }
    var marginStep by remember { mutableIntStateOf(preferences.margin()) }
    var fontFamilyId by remember { mutableIntStateOf(preferences.fontFamily()) }
    var brightness by remember { mutableFloatStateOf(preferences.brightness()) }
    var keepScreenOn by remember { mutableStateOf(preferences.keepScreenOn()) }
    var volumePageTurn by remember { mutableStateOf(preferences.volumePageTurn()) }
    var pageTurnAnimation by remember { mutableStateOf(preferences.pageTurnAnimation()) }
    var paragraphIndent by remember { mutableStateOf(preferences.paragraphIndent()) }
    var autoNight by remember { mutableStateOf(preferences.autoNight()) }
    var customFontName by remember { mutableStateOf(preferences.customFontName()) }
    // In-page system TTS (no jump to legacy UI). Same engine family as Accessibility.
    var ttsState by remember { mutableStateOf(ReaderTtsState.Preparing) }
    var ttsRate by remember { mutableFloatStateOf(TtsRate.clamp(preferences.ttsRate())) }
    var ttsEnginePackage by remember {
        mutableStateOf(TtsEngineCatalog.normalizePackage(preferences.ttsEnginePackage()))
    }
    var ttsEngines by remember { mutableStateOf<List<TtsEngineOption>>(emptyList()) }
    var showTtsRateDialog by remember { mutableStateOf(false) }
    var showTtsEngineDialog by remember { mutableStateOf(false) }
    var autoPageTurnSec by remember { mutableIntStateOf(preferences.autoPageTurnSec()) }
    var batteryPercent by remember { mutableIntStateOf(-1) }
    var batteryCharging by remember { mutableStateOf(false) }
    var menuVisible by remember { mutableStateOf(false) }
    var showToc by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showFind by remember { mutableStateOf(false) }
    var showAppearance by remember { mutableStateOf(false) }
    var bookmarkVersion by remember { mutableIntStateOf(0) }

    val palette = remember(theme) { readerPalette(theme) }
    val fullTextChapterLabel = stringResource(R.string.reader_chapter_full)
    var chapters by remember(book.id) {
        mutableStateOf(listOf(Chapter(fullTextChapterLabel, 0)))
    }
    var chaptersLoaded by remember(book.id) { mutableStateOf(false) }

    // Chapter regex scanning is linear over the entire book. For a large import, defer it until
    // the TOC is requested so the first page is not competing with a multi-megabyte scan.
    // Also skip while only a progressive window is loaded (incomplete body).
    LaunchedEffect(book.id, book.text.length, showToc, textFullyLoaded) {
        if (!textFullyLoaded) return@LaunchedEffect
        if (chaptersLoaded) return@LaunchedEffect
        if (!showToc && book.text.length > PageIndex.MAX_EXACT_MEASURE_CHARS) return@LaunchedEffect
        val text = book.text
        val label = fullTextChapterLabel
        chapters = withContext(Dispatchers.Default) {
            ChapterIndex.findChapters(text, label)
        }
        chaptersLoaded = true
    }

    val controlsScrollState = rememberScrollState()
    // Survives progressive→full swap (keyed only on book.id) so saved progress is not reset.
    var progress by remember(book.id) {
        mutableIntStateOf(ProgressMath.clampProgress(book.position))
    }
    var currentChapter by remember { mutableIntStateOf(0) }
    var clock by remember { mutableStateOf(formatTime()) }
    // False until full-book pager sits on pageForProgress(saved). Blocks leave-save clobber.
    var restoreApplied by remember(book.id, textFullyLoaded) { mutableStateOf(false) }
    // Local layout ready (window or full); distinct from restoreApplied so window page-turns
    // are not reset to 0 on every reflow while progress commits stay gated.
    var layoutReady by remember(book.id, textFullyLoaded) { mutableStateOf(false) }

    // Large full-body books use virtual approximate pages (O(1) first body — no full index list).
    val useApproxPaging = textFullyLoaded && book.text.length > PageIndex.MAX_EXACT_MEASURE_CHARS
    var approxCharsPerPage by remember(book.id) {
        mutableIntStateOf(PageIndex.DEFAULT_APPROX_CHARS_PER_PAGE)
    }

    // Character anchor for reflow: keep nearest page after font/line/theme change.
    var anchorOffset by remember(book.id, book.text.length, textFullyLoaded) {
        mutableIntStateOf(
            if (!textFullyLoaded) {
                0
            } else {
                ((ProgressMath.clampProgress(book.position) / 1000f) * book.text.length)
                    .roundToInt()
                    .coerceIn(0, book.text.length.coerceAtLeast(0))
            },
        )
    }
    // Exact-measure books only. Empty until measured — [safePageText] returns "" so Compose
    // never lays out the whole multi‑MB string as one page (ANR regression guard).
    var pageStarts by remember(book.id, textFullyLoaded) { mutableStateOf(emptyList<Int>()) }
    var contentWidthPx by remember { mutableIntStateOf(0) }
    var contentHeightPx by remember { mutableIntStateOf(0) }

    val approxPageCount = if (useApproxPaging) {
        PageIndex.approximatePageCount(book.text.length, approxCharsPerPage)
    } else {
        1
    }
    val pageCount = when {
        useApproxPaging -> approxPageCount
        // Progressive window: paginate the window itself (small); body is visible immediately.
        !textFullyLoaded -> pageStarts.size.coerceAtLeast(1)
        else -> pageStarts.size.coerceAtLeast(1)
    }
    // Remount pager on progressive→full so initialPage is pageForProgress, not stale window index.
    val pagerState = key(book.id, textFullyLoaded) {
        val initialPagerPage = if (useApproxPaging) {
            OpenProgressGate.restoreTargetPage(
                ProgressMath.clampProgress(book.position),
                PageIndex.approximatePageCount(book.text.length, approxCharsPerPage),
            )
        } else {
            0
        }
        rememberPagerState(
            initialPage = initialPagerPage,
            pageCount = {
                when {
                    useApproxPaging -> {
                        PageIndex.approximatePageCount(book.text.length, approxCharsPerPage)
                    }
                    else -> pageStarts.size.coerceAtLeast(1)
                }
            },
        )
    }

    // Latest progress for leave-save: DisposableEffect keys only on book.id.
    val latestProgress by rememberUpdatedState(progress)

    val bodyFontFamily = remember(fontFamilyId, customFontName, context) {
        if (fontFamilyId == ReaderPreferences.FONT_CUSTOM && customFontName.isNotEmpty()) {
            ReaderCustomFont.loadFontFamily(context, customFontName) ?: readerFontFamily(ReaderPreferences.FONT_SERIF)
        } else {
            readerFontFamily(fontFamilyId)
        }
    }
    val bodyTextStyle = remember(
        fontSizeSp,
        lineHeightMultiplier,
        palette.onBackground,
        bodyFontFamily,
        paragraphIndent,
    ) {
        TextStyle(
            color = palette.onBackground,
            fontSize = fontSizeSp.sp,
            lineHeight = (fontSizeSp * lineHeightMultiplier).sp,
            fontFamily = bodyFontFamily,
            letterSpacing = 0.2.sp,
            textIndent = if (paragraphIndent) {
                TextIndent(firstLine = (fontSizeSp * 2).sp, restLine = 0.sp)
            } else {
                null
            },
        )
    }

    DisposableEffect(brightness) {
        applyBrightness(activity, brightness)
        onDispose { applyBrightness(activity, -1f) }
    }

    DisposableEffect(keepScreenOn) {
        val window = activity.window
        if (keepScreenOn) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Auto-night: re-resolve paper by local hour while the reader is open.
    LaunchedEffect(autoNight, preferences) {
        if (!autoNight) return@LaunchedEffect
        while (true) {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val resolved = ReaderReadingPolish.resolveEffectiveTheme(
                autoNightEnabled = true,
                hourOfDay = hour,
                manualTheme = theme,
                dayTheme = preferences.dayTheme(),
                nightTheme = preferences.nightThemeVariant(),
                startHour = preferences.autoNightStartHour(),
                endHour = preferences.autoNightEndHour(),
            )
            if (resolved != theme) {
                theme = resolved
                preferences.setTheme(resolved)
            }
            delay(60_000)
        }
    }

    DisposableEffect(book.id) {
        val appContext = context.applicationContext
        val bookId = book.id
        ReadingHistory.get(appContext).record(bookId)
        val started = android.os.SystemClock.elapsedRealtime()
        onDispose {
            // Do not use rememberCoroutineScope here — composition is leaving.
            // Process-lifetime IO scope + clamp 0…1000; stats skip non-positive duration.
            val ended = android.os.SystemClock.elapsedRealtime()
            val duration = ReaderLeaveSave.elapsedReadingMs(started, ended)
            val finalProgress = ProgressMath.clampProgress(latestProgress)
            ReaderLeaveSave.persistAsync(appContext, bookId, finalProgress, duration)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            clock = formatTime()
            delay(30_000)
        }
    }

    // Viewport page breaking / approx chars-per-page: first body for large books is O(1)
    // via virtual pages (no full index list). Exact measure stays for smaller bodies.
    LaunchedEffect(
        book.text,
        textFullyLoaded,
        fontSizeSp,
        lineHeightMultiplier,
        theme,
        marginStep,
        fontFamilyId,
        paragraphIndent,
        contentWidthPx,
        contentHeightPx,
    ) {
        if (contentWidthPx <= 0 || contentHeightPx <= 0) return@LaunchedEffect
        val style = bodyTextStyle
        val width = contentWidthPx
        val height = contentHeightPx
        val text = book.text

        if (useApproxPaging) {
            val fontPx = with(density) { fontSizeSp.sp.toPx() }
            val charsPerPage = PageIndex.approximateCharsPerPage(
                widthPx = width,
                heightPx = height,
                fontSizePx = fontPx,
                lineHeightMultiplier = lineHeightMultiplier,
            )
            approxCharsPerPage = charsPerPage
            val count = PageIndex.approximatePageCount(text.length, charsPerPage)
            val saved = ProgressMath.clampProgress(book.position)
            val targetPage = if (!layoutReady) {
                // Full-book open / progressive→full swap: always from saved progress.
                OpenProgressGate.restoreTargetPage(saved, count)
            } else {
                // Preserve reading position across reflow (font / margin / theme).
                val page = (anchorOffset / charsPerPage.coerceAtLeast(256))
                    .coerceIn(0, (count - 1).coerceAtLeast(0))
                page
            }
            val clamped = PageIndex.clampPageIndex(targetPage, count)
            if (pagerState.currentPage != clamped) {
                pagerState.scrollToPage(clamped)
            }
            if (!restoreApplied) {
                // Hold library progress at book.position; do not derive from pager page.
                progress = OpenProgressGate.afterRestoreApplied(saved)
                restoreApplied = true
            }
            layoutReady = true
            anchorOffset = PageIndex.approximateOffsetForPage(clamped, charsPerPage, text.length)
            currentChapter = ChapterIndex.chapterAtOffset(chapters, anchorOffset)
            return@LaunchedEffect
        }

        // Progressive window or small book: exact TextMeasurer path (body is bounded).
        val starts = if (text.isEmpty()) {
            listOf(0)
        } else {
            val layout = textMeasurer.measure(
                text = text,
                style = style,
                constraints = Constraints(maxWidth = width),
            )
            val lineCount = layout.lineCount
            if (lineCount <= 0) {
                listOf(0)
            } else {
                val tops = FloatArray(lineCount)
                val bottoms = FloatArray(lineCount)
                val chars = IntArray(lineCount)
                for (i in 0 until lineCount) {
                    tops[i] = layout.getLineTop(i)
                    bottoms[i] = layout.getLineBottom(i)
                    chars[i] = layout.getLineStart(i)
                }
                PageIndex.pageStartOffsets(tops, bottoms, chars, height.toFloat())
            }
        }
        pageStarts = starts
        val saved = ProgressMath.clampProgress(book.position)
        val targetPage = if (!layoutReady) {
            // Window preview: start of window (content already around progress).
            // Full small book: restore via progress.
            if (!textFullyLoaded) {
                0
            } else {
                OpenProgressGate.restoreTargetPage(saved, starts.size)
            }
        } else {
            PageIndex.pageForOffset(starts, anchorOffset)
        }
        val clamped = PageIndex.clampPageIndex(targetPage, starts.size)
        if (pagerState.currentPage != clamped) {
            pagerState.scrollToPage(clamped)
        }
        // Always keep held progress while the gate is closed (progressive window).
        if (!OpenProgressGate.mayCommitProgressFromPageTurn(textFullyLoaded, restoreApplied)) {
            progress = OpenProgressGate.afterRestoreApplied(saved)
        }
        // Open the commit gate only once the full body is loaded and pager is restored.
        if (textFullyLoaded && !restoreApplied) {
            restoreApplied = true
        }
        layoutReady = true
        anchorOffset = PageIndex.offsetForPage(starts, clamped)
        currentChapter = ChapterIndex.chapterAtOffset(chapters, anchorOffset)
    }

    // Approx pager remounts with restoreTargetPage before viewport measure — open the gate
    // so body/footer/progress agree without waiting on contentWidthPx.
    LaunchedEffect(textFullyLoaded, useApproxPaging, book.position) {
        if (!useApproxPaging || restoreApplied) return@LaunchedEffect
        val saved = ProgressMath.clampProgress(book.position)
        progress = OpenProgressGate.afterRestoreApplied(saved)
        restoreApplied = true
    }

    // Page turns → progress (0…1000) + chapter + anchor.
    // Must not commit progress until OpenProgressGate allows (avoids clobber on swap).
    LaunchedEffect(pagerState, pageStarts, chapters, useApproxPaging, approxCharsPerPage, textFullyLoaded, restoreApplied) {
        snapshotFlow {
            Triple(pagerState.currentPage, pageStarts, approxCharsPerPage)
        }
            .distinctUntilChanged()
            .collect { (page, starts, cpp) ->
                if (useApproxPaging) {
                    val count = PageIndex.approximatePageCount(book.text.length, cpp)
                    val p = PageIndex.clampPageIndex(page, count)
                    progress = OpenProgressGate.progressAfterPageTurn(
                        textFullyLoaded = textFullyLoaded,
                        restoreApplied = restoreApplied,
                        heldProgress = progress,
                        page = p,
                        pageCount = count,
                    )
                    val offset = PageIndex.approximateOffsetForPage(p, cpp, book.text.length)
                    anchorOffset = offset
                    currentChapter = ChapterIndex.chapterAtOffset(chapters, offset)
                } else {
                    val count = starts.size.coerceAtLeast(1)
                    val p = PageIndex.clampPageIndex(page, count)
                    progress = OpenProgressGate.progressAfterPageTurn(
                        textFullyLoaded = textFullyLoaded,
                        restoreApplied = restoreApplied,
                        heldProgress = progress,
                        page = p,
                        pageCount = count,
                    )
                    val offset = PageIndex.offsetForPage(starts, p)
                    anchorOffset = offset
                    currentChapter = ChapterIndex.chapterAtOffset(chapters, offset)
                }
            }
    }

    LaunchedEffect(book.id) {
        snapshotFlow { progress }
            .distinctUntilChanged()
            .debounce(500L)
            .collect { p ->
                val clamped = ProgressMath.clampProgress(p)
                withContext(Dispatchers.IO) {
                    LibraryStore.getForReading(context).savePosition(book.id, clamped)
                }
            }
    }

    fun animateToPage(target: Int) {
        val count = if (useApproxPaging) {
            PageIndex.approximatePageCount(book.text.length, approxCharsPerPage)
        } else {
            pageStarts.size
        }
        val page = PageIndex.clampPageIndex(target, count)
        if (page == pagerState.currentPage) return
        scope.launch {
            val ms = ReaderReadingPolish.pageTurnDurationMs(pageTurnAnimation)
            if (ms <= 0) {
                pagerState.scrollToPage(page)
            } else {
                pagerState.animateScrollToPage(
                    page = page,
                    animationSpec = tween(durationMillis = ms),
                )
            }
        }
    }

    fun jumpToOffset(offset: Int) {
        val clampedOffset = offset.coerceIn(0, book.text.length)
        anchorOffset = clampedOffset
        val page = if (useApproxPaging) {
            val cpp = approxCharsPerPage.coerceAtLeast(256)
            PageIndex.clampPageIndex(
                clampedOffset / cpp,
                PageIndex.approximatePageCount(book.text.length, cpp),
            )
        } else {
            PageIndex.pageForOffset(pageStarts, clampedOffset)
        }
        animateToPage(page)
        currentChapter = ChapterIndex.chapterAtOffset(chapters, clampedOffset)
    }

    fun jumpToProgress(p: Int) {
        val count = if (useApproxPaging) {
            PageIndex.approximatePageCount(book.text.length, approxCharsPerPage)
        } else {
            pageStarts.size
        }
        animateToPage(PageIndex.pageForProgress(p, count))
    }

    fun onReadingSurfaceTap(xFraction: Float) {
        when (PageIndex.tapZoneAction(xFraction)) {
            TapZoneAction.PREV_PAGE -> {
                animateToPage(PageIndex.stepPage(pagerState.currentPage, pageCount, -1))
            }
            TapZoneAction.NEXT_PAGE -> {
                animateToPage(PageIndex.stepPage(pagerState.currentPage, pageCount, 1))
            }
            TapZoneAction.TOGGLE_CHROME -> {
                menuVisible = !menuVisible
            }
        }
    }

    fun currentReadingOffset(): Int {
        return if (useApproxPaging) {
            PageIndex.approximateOffsetForPage(
                pagerState.currentPage,
                approxCharsPerPage,
                book.text.length,
            )
        } else {
            PageIndex.offsetForPage(pageStarts, pagerState.currentPage)
        }
    }

    // In-page system TTS — stays on Compose reader (no jump to legacy chrome).
    val ttsChunkJump = remember { mutableStateOf<(Int) -> Unit>({}) }
    ttsChunkJump.value = { jumpToOffset(it) }
    var ttsController by remember { mutableStateOf<ReaderTtsController?>(null) }
    DisposableEffect(Unit) {
        val ctrl = ReaderTtsController(
            context = context,
            onState = { ttsState = it },
            onChunkStart = { off -> ttsChunkJump.value(off) },
            onEnginesDiscovered = { list ->
                if (list.isNotEmpty()) ttsEngines = list
            },
        )
        ttsController = ctrl
        ctrl.prepare(
            rate = TtsRate.clamp(preferences.ttsRate()),
            enginePackage = TtsEngineCatalog.normalizePackage(preferences.ttsEnginePackage()),
        )
        onDispose {
            ctrl.shutdown()
            ttsController = null
        }
    }

    // Load installed engines (Oplus / Google / 讯飞 / …) for the picker.
    // Requires manifest <queries> for TTS_SERVICE (Android 11+ package visibility).
    // Also refresh when the engine dialog opens so the list is never stale.
    LaunchedEffect(showTtsEngineDialog) {
        ttsEngines = withContext(Dispatchers.Default) {
            TtsEngineCatalog.listInstalled(context)
        }
    }

    // Battery for Kindle-style footer (sticky + change broadcasts).
    DisposableEffect(Unit) {
        fun applyBattery(intent: Intent?) {
            if (intent == null) return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            batteryPercent = if (level < 0) -1 else ((level * 100f) / scale).toInt().coerceIn(0, 100)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            batteryCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val sticky = ContextCompat.registerReceiver(
            context,
            null,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        applyBattery(sticky)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) = applyBattery(intent)
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
        }
    }

    // Timed auto page-turn (paused when chrome open or TTS is speaking).
    LaunchedEffect(autoPageTurnSec, menuVisible, ttsState, pageCount) {
        val delayMs = AutoPageTurn.delayMs(autoPageTurnSec)
        if (delayMs <= 0L) return@LaunchedEffect
        while (true) {
            delay(delayMs)
            if (menuVisible) continue
            if (ttsState == ReaderTtsState.Speaking) continue
            val next = PageIndex.stepPage(pagerState.currentPage, pageCount, 1)
            if (next == pagerState.currentPage) break
            animateToPage(next)
        }
    }

    fun toggleInPageTts() {
        val ctrl = ttsController
        when (ttsState) {
            ReaderTtsState.Speaking -> ctrl?.stop()
            ReaderTtsState.Ready -> {
                if (book.text.isEmpty()) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.reader_tts_body_not_ready),
                        Toast.LENGTH_SHORT,
                    ).show()
                    return
                }
                // Prefer full body when loaded; progressive window still speaks what we have.
                val started = ctrl?.start(book.text, currentReadingOffset()) == true
                if (!started) {
                    // Engine not ready or speak rejected — offer engine picker.
                    showTtsEngineDialog = true
                    Toast.makeText(
                        context,
                        context.getString(R.string.reader_tts_pick_engine_hint),
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    // Hide chrome so taps don't fight TTS; user can show again if needed.
                    menuVisible = false
                }
            }
            ReaderTtsState.Preparing -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.reader_tts_preparing_toast),
                    Toast.LENGTH_SHORT,
                ).show()
                // Let user pick Google / 国产 while init finishes.
                showTtsEngineDialog = true
            }
            ReaderTtsState.Unavailable -> {
                // Re-prepare and open engine picker (Google / 国产) so user can switch.
                ctrl?.prepare(TtsRate.clamp(ttsRate), ttsEnginePackage)
                showTtsEngineDialog = true
                Toast.makeText(
                    context,
                    context.getString(R.string.reader_tts_unavailable),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    fun currentTtsEngineLabel(): String {
        val match = ttsEngines.firstOrNull {
            it.packageName == ttsEnginePackage
        }
        return match?.label
            ?: TtsEngineCatalog.friendlyLabel(ttsEnginePackage)
    }

    fun applyTtsEngine(packageName: String) {
        val pkg = TtsEngineCatalog.normalizePackage(packageName)
        ttsEnginePackage = pkg
        preferences.setTtsEnginePackage(pkg)
        ttsController?.stop()
        ttsController?.switchEngine(pkg, TtsRate.clamp(ttsRate))
        Toast.makeText(
            context,
            context.getString(R.string.reader_tts_engine_switching),
            Toast.LENGTH_SHORT,
        ).show()
    }

    // Volume keys → page turn when enabled (common CN novel-reader gesture).
    val volumeTurnEnabled by rememberUpdatedState(volumePageTurn)
    val latestPageCount by rememberUpdatedState(pageCount)
    DisposableEffect(activity, pagerState) {
        val readerActivity = activity as? ReaderActivity
        if (readerActivity != null) {
            readerActivity.volumePageTurnHandler = handler@{ keyCode ->
                if (!volumeTurnEnabled) return@handler false
                when (keyCode) {
                    android.view.KeyEvent.KEYCODE_VOLUME_UP -> {
                        animateToPage(
                            PageIndex.stepPage(pagerState.currentPage, latestPageCount, -1),
                        )
                        true
                    }
                    android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> {
                        animateToPage(
                            PageIndex.stepPage(pagerState.currentPage, latestPageCount, 1),
                        )
                        true
                    }
                    else -> false
                }
            }
        }
        onDispose {
            readerActivity?.volumePageTurnHandler = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            // Body + gap + footer: reserve gap so time/page never sits on the last line.
            val bodyFooterGap = PageLayout.BODY_FOOTER_GAP_DP.dp
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // Keep a clear band above the footer strip (not flush against page %).
                    .padding(bottom = bodyFooterGap),
            ) {
                // Kindle-style adjustable body margins (narrow / standard / wide).
                val padH = PageLayout.horizontalPadDp(marginStep).dp
                val padV = PageLayout.verticalPadDp(marginStep).dp
                val widthPx = with(density) { (maxWidth - padH * 2).toPx().toInt().coerceAtLeast(1) }
                // maxHeight already excludes footer gap; still subtract body pad for measure.
                val heightPx = with(density) { (maxHeight - padV * 2).toPx().toInt().coerceAtLeast(1) }
                // Publish measured viewport for page breaking (side-effect free after first frame).
                LaunchedEffect(widthPx, heightPx, marginStep) {
                    contentWidthPx = widthPx
                    contentHeightPx = heightPx
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        // Clip page paint so Visible overflow cannot bleed onto the footer.
                        .clipToBounds()
                        .semantics { contentDescription = tapHint },
                    userScrollEnabled = true,
                    beyondViewportPageCount = 1,
                ) { page ->
                    val pageBody = remember(
                        book.text,
                        pageStarts,
                        page,
                        useApproxPaging,
                        approxCharsPerPage,
                        restoreApplied,
                        book.position,
                        pageCount,
                    ) {
                        when {
                            useApproxPaging -> {
                                // Before restore, map the *current* pager slot through the gate
                                // so a stale index 0 never paints page-0 body at mid-book progress.
                                val bodyPage = if (page == pagerState.currentPage) {
                                    OpenProgressGate.displayPageForApprox(
                                        restoreApplied = restoreApplied,
                                        pagerPage = page,
                                        savedProgress = book.position,
                                        pageCount = PageIndex.approximatePageCount(
                                            book.text.length,
                                            approxCharsPerPage,
                                        ),
                                    )
                                } else {
                                    page
                                }
                                PageIndex.approximatePageText(
                                    book.text,
                                    approxCharsPerPage,
                                    bodyPage,
                                )
                            }
                            pageStarts.isEmpty() && book.text.isNotEmpty() -> {
                                // First frame before exact measure: bounded slice only
                                // (progressive window or small book). Never feed full text
                                // when length exceeds one approximate page.
                                PageIndex.approximatePageText(
                                    book.text,
                                    PageIndex.DEFAULT_APPROX_CHARS_PER_PAGE,
                                    page,
                                )
                            }
                            // Empty index + empty body → ""; incomplete index never yields
                            // the entire book as a single Compose page (ANR guard).
                            else -> PageIndex.safePageText(book.text, pageStarts, page)
                        }
                    }
                    // Left/right book-style turn: 3D tilt about the vertical edge while the
                    // pager scrolls (finger swipe or tap animateScrollToPage).
                    val pageOffset =
                        (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                    val turn = PageTurnEffect.transform(pageOffset, pageTurnAnimation)
                    // Page-local indent: only true paragraph starts (offset 0 or after \n).
                    // Applying firstLineIndent on every page re-wraps mid-paragraph lines and
                    // clips the last line — the longstanding half-line bug.
                    val pageStartOffset = when {
                        useApproxPaging -> PageIndex.approximateOffsetForPage(
                            page,
                            approxCharsPerPage,
                            book.text.length,
                        )
                        pageStarts.isNotEmpty() -> PageIndex.offsetForPage(pageStarts, page)
                        else -> 0
                    }
                    val pageTextStyle = remember(
                        bodyTextStyle,
                        paragraphIndent,
                        pageStartOffset,
                        book.text,
                    ) {
                        if (!paragraphIndent) {
                            bodyTextStyle
                        } else if (PageIndex.shouldApplyParagraphIndent(book.text, pageStartOffset)) {
                            bodyTextStyle
                        } else {
                            bodyTextStyle.copy(textIndent = null)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                // Density-aware camera so the Y-axis tilt reads as a page flip.
                                cameraDistance = 18f * density.density
                                transformOrigin = TransformOrigin(
                                    pivotFractionX = turn.pivotFractionX,
                                    pivotFractionY = 0.5f,
                                )
                                rotationY = turn.rotationY
                                alpha = turn.alpha
                                scaleX = turn.scale
                                scaleY = turn.scale
                                // Prevent 3D layer from cropping descenders at the bottom edge.
                                clip = false
                            }
                            .pointerInput(pageCount) {
                                detectTapGestures { offset ->
                                    val fraction = if (size.width > 0) {
                                        offset.x / size.width.toFloat()
                                    } else {
                                        0.5f
                                    }
                                    onReadingSurfaceTap(fraction)
                                }
                            },
                    ) {
                        SelectionContainer(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .fillMaxWidth()
                                .padding(horizontal = padH, vertical = padV),
                        ) {
                            Text(
                                text = pageBody,
                                modifier = Modifier.fillMaxWidth(),
                                style = pageTextStyle,
                                // Clip within the padded body; footer gap + line safety prevent cut-off.
                                overflow = TextOverflow.Clip,
                                softWrap = true,
                            )
                        }
                    }
                }
            }

            // Hide the thin footer while chrome is open so it does not sit under the
            // progress slider / control bar (they shared the same bottom edge before).
            if (!menuVisible) {
                val percent = ProgressMath.percentOfProgress(progress)
                val pageNum = PageLayout.displayPageNumber(pagerState.currentPage, pageCount)
                val pageTotal = PageLayout.displayPageCount(pageCount)
                val pageLocation = PageLayout.pageLocationLabel(pagerState.currentPage, pageCount)
                val pageLocationCd = stringResource(R.string.reader_page_location_cd, pageNum, pageTotal)
                val progressCd = stringResource(R.string.reader_progress_cd, percent)
                val batteryText = ReaderFooterFormat.batteryLabel(batteryPercent, batteryCharging)
                val batteryCd = ReaderFooterFormat.batteryContentDescription(
                    batteryPercent,
                    batteryCharging,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .semantics {
                            contentDescription = "$pageLocationCd，$progressCd，$batteryCd"
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "$clock · $batteryText",
                        color = palette.muted,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                    // Kindle-style page location + explicit percent.
                    Text(
                        text = "$pageLocation · $percent%",
                        color = palette.muted,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                    val chapterTitle = chapters.getOrNull(currentChapter)?.title.orEmpty()
                    Text(
                        text = if (chapterTitle.isNotEmpty()) {
                            buildString {
                                append(chapterTitle.take(12))
                                if (chapterTitle.length > 12) append('…')
                            }
                        } else {
                            ""
                        },
                        color = palette.muted,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = menuVisible,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            TopAppBar(
                title = {
                    Text(
                        text = book.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .semantics { contentDescription = backCd },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showAppearance = true },
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .semantics { contentDescription = appearanceCd },
                    ) {
                        Icon(Icons.Filled.FormatSize, contentDescription = null)
                    }
                    IconButton(
                        onClick = { showFind = true },
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .semantics { contentDescription = findCd },
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null)
                    }
                    IconButton(
                        onClick = { showBookmarks = true },
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .semantics { contentDescription = bookmarksCd },
                    ) {
                        Icon(Icons.Filled.Bookmark, contentDescription = null)
                    }
                    IconButton(
                        onClick = { showToc = true },
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .semantics { contentDescription = tocCd },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
                    }
                    // Tap: TTS start/stop. Long-press: pick engine (Google / 国产) + rate.
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .combinedClickable(
                                role = Role.Button,
                                onClick = { toggleInPageTts() },
                                onLongClick = { showTtsEngineDialog = true },
                            )
                            .semantics {
                                contentDescription = if (ttsState == ReaderTtsState.Speaking) {
                                    ttsStopCd
                                } else {
                                    ttsCd
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (ttsState == ReaderTtsState.Speaking) {
                                Icons.Filled.Stop
                            } else {
                                Icons.AutoMirrored.Filled.VolumeUp
                            },
                            contentDescription = null,
                            tint = palette.onBar,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.bar,
                    titleContentColor = palette.onBar,
                    navigationIconContentColor = palette.onBar,
                    actionIconContentColor = palette.onBar,
                ),
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
            )
        }

        AnimatedVisibility(
            visible = menuVisible,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Surface(
                color = palette.bar,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val sliderPercent = ProgressMath.percentOfProgress(progress)
                    val sliderProgressCd = stringResource(R.string.reader_progress_cd, sliderPercent)
                    // Percent label + slider on one row so progress is never “bar only”.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .semantics { contentDescription = sliderProgressCd },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "$sliderPercent%",
                            color = palette.onBar,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                        )
                        Slider(
                            value = progress.toFloat(),
                            onValueChange = {
                                progress = ProgressMath.clampProgress(it.roundToInt())
                            },
                            onValueChangeFinished = { jumpToProgress(progress) },
                            valueRange = 0f..1000f,
                            colors = SliderDefaults.colors(
                                thumbColor = palette.onBar,
                                activeTrackColor = palette.onBar,
                                inactiveTrackColor = palette.muted,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // Six controls need ≥48dp targets; SpaceEvenly when they fit,
                    // horizontalScroll when the bar is narrower than the labels.
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .widthIn(min = maxWidth)
                                .horizontalScroll(controlsScrollState)
                                .heightIn(min = 56.dp)
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ControlLabel(
                                text = stringResource(R.string.reader_prev_chapter),
                                enabled = currentChapter > 0,
                                color = palette.onBar,
                                onClick = {
                                    if (currentChapter > 0) {
                                        jumpToOffset(chapters[currentChapter - 1].start)
                                    }
                                },
                            )
                            ControlLabel(
                                text = stringResource(R.string.reader_font_smaller),
                                enabled = fontSizeSp > 14,
                                color = palette.onBar,
                                onClick = {
                                    fontSizeSp = (fontSizeSp - 1).coerceAtLeast(14)
                                    preferences.setFontSize(fontSizeSp)
                                },
                                contentDescription = fontSmallerCd,
                            )
                            ControlLabel(
                                text = stringResource(R.string.reader_font_larger),
                                enabled = fontSizeSp < 30,
                                color = palette.onBar,
                                onClick = {
                                    fontSizeSp = (fontSizeSp + 1).coerceAtMost(30)
                                    preferences.setFontSize(fontSizeSp)
                                },
                                contentDescription = fontLargerCd,
                            )
                            ControlLabel(
                                text = stringResource(R.string.reader_line_height_smaller),
                                enabled = lineHeightMultiplier > ReaderPreferences.MIN_LINE_HEIGHT,
                                color = palette.onBar,
                                onClick = {
                                    lineHeightMultiplier = (lineHeightMultiplier - 0.15f)
                                        .coerceAtLeast(ReaderPreferences.MIN_LINE_HEIGHT)
                                    preferences.setLineHeightMultiplier(lineHeightMultiplier)
                                },
                                contentDescription = lineHeightSmallerCd,
                            )
                            ControlLabel(
                                text = stringResource(R.string.reader_line_height_larger),
                                enabled = lineHeightMultiplier < ReaderPreferences.MAX_LINE_HEIGHT,
                                color = palette.onBar,
                                onClick = {
                                    lineHeightMultiplier = (lineHeightMultiplier + 0.15f)
                                        .coerceAtMost(ReaderPreferences.MAX_LINE_HEIGHT)
                                    preferences.setLineHeightMultiplier(lineHeightMultiplier)
                                },
                                contentDescription = lineHeightLargerCd,
                            )
                            ControlLabel(
                                text = stringResource(R.string.reader_next_chapter),
                                enabled = currentChapter < chapters.lastIndex,
                                color = palette.onBar,
                                onClick = {
                                    if (currentChapter < chapters.lastIndex) {
                                        jumpToOffset(chapters[currentChapter + 1].start)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showToc) {
        val currentSuffix = stringResource(R.string.reader_chapter_current_suffix)
        val tocJumpLabel = stringResource(R.string.reader_toc_jump_current)
        val tocJumpCd = stringResource(R.string.reader_toc_jump_current_cd)
        val tocScrubCd = stringResource(R.string.reader_toc_scrub_cd)
        val tocListState = rememberLazyListState()
        var scrubbing by remember { mutableStateOf(false) }
        var scrubIndex by remember { mutableIntStateOf(currentChapter) }
        // Open at the chapter being read — not always at the top of a long TOC.
        LaunchedEffect(showToc, currentChapter, chapters.size) {
            if (!showToc || chapters.isEmpty()) return@LaunchedEffect
            val index = ChapterIndex.tocScrollIndex(currentChapter, chapters.size)
            scrubIndex = index
            tocListState.scrollToItem(index)
        }
        ModalBottomSheet(
            onDismissRequest = { showToc = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.reader_toc),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .semantics { heading() },
                )
                TextButton(
                    onClick = {
                        scope.launch {
                            val index = ChapterIndex.tocScrollIndex(currentChapter, chapters.size)
                            scrubIndex = index
                            tocListState.animateScrollToItem(index)
                        }
                    },
                    enabled = chapters.isNotEmpty(),
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics {
                            contentDescription = tocJumpCd
                            role = Role.Button
                        },
                ) {
                    Icon(
                        Icons.Filled.MyLocation,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    Text(tocJumpLabel)
                }
            }
            // List + right-edge fast scrub: drag 1 → 20 → 30 instantly (not slow fling).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
            ) {
                LazyColumn(
                    state = tocListState,
                    contentPadding = PaddingValues(bottom = 32.dp, end = 36.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    itemsIndexed(chapters) { index, chapter ->
                        val selected = index == currentChapter
                        val highlight = scrubbing && index == scrubIndex
                        val chapterCd = chapter.title + if (selected) currentSuffix else ""
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = chapter.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = when {
                                        selected -> MaterialTheme.colorScheme.primary
                                        highlight -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                    fontWeight = if (selected || highlight) {
                                        FontWeight.SemiBold
                                    } else {
                                        FontWeight.Normal
                                    },
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minWidth = 48.dp, minHeight = 56.dp)
                                .clickable(role = Role.Button) {
                                    showToc = false
                                    jumpToOffset(chapter.start)
                                }
                                .semantics {
                                    contentDescription = chapterCd
                                    role = Role.Button
                                },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                if (chapters.size > 1) {
                    TocScrubRail(
                        chapterCount = chapters.size,
                        scrubIndex = scrubIndex,
                        scrubbing = scrubbing,
                        bubbleTitle = chapters.getOrNull(scrubIndex)?.title.orEmpty(),
                        contentDescription = tocScrubCd,
                        onScrub = { index ->
                            scrubbing = true
                            if (index != scrubIndex) {
                                scrubIndex = index
                                // Instant jump (not animate) so 1→20→30 feels snappy.
                                scope.launch {
                                    tocListState.scrollToItem(index)
                                }
                            }
                        },
                        onScrubEnd = { scrubbing = false },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(vertical = 8.dp, horizontal = 2.dp),
                    )
                }
            }
        }
    }

    if (showBookmarks) {
        val bookmarks = remember(bookmarkVersion, book.id) {
            BookmarkStore.get(context).list(book.id)
        }
        val addBookmarkLabel = stringResource(R.string.reader_add_bookmark)
        val addBookmarkCd = stringResource(R.string.reader_add_bookmark_cd)
        val bookmarksEmpty = stringResource(R.string.reader_bookmarks_empty)
        ModalBottomSheet(
            onDismissRequest = { showBookmarks = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Text(
                text = stringResource(R.string.reader_bookmarks),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .semantics { heading() },
            )
            TextButton(
                onClick = {
                    val label = chapters.getOrNull(currentChapter)?.title
                        ?: context.getString(R.string.reader_current_position)
                    BookmarkStore.get(context).add(
                        book.id,
                        ProgressMath.clampProgress(progress),
                        label,
                    )
                    bookmarkVersion++
                    Toast.makeText(
                        context,
                        context.getString(R.string.reader_bookmark_added),
                        Toast.LENGTH_SHORT,
                    ).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .padding(horizontal = 8.dp)
                    .semantics {
                        contentDescription = addBookmarkCd
                        role = Role.Button
                    },
            ) {
                Text(addBookmarkLabel)
            }
            if (bookmarks.isEmpty()) {
                Text(
                    text = bookmarksEmpty,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .semantics { contentDescription = bookmarksEmpty },
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 32.dp),
                    modifier = Modifier.heightIn(max = 360.dp),
                ) {
                    itemsIndexed(bookmarks) { index, mark ->
                        val pct = Math.round(mark.progress / 10f)
                        val itemLabel = stringResource(
                            R.string.reader_bookmark_list_item,
                            mark.label,
                            pct,
                        )
                        val itemCd = stringResource(
                            R.string.reader_bookmark_item_cd,
                            mark.label,
                            pct,
                        )
                        val deleteCd = stringResource(
                            R.string.reader_delete_bookmark_cd,
                            mark.label,
                        )
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = itemLabel,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            trailingContent = {
                                TextButton(
                                    onClick = {
                                        BookmarkStore.get(context).remove(book.id, index)
                                        bookmarkVersion++
                                    },
                                    modifier = Modifier
                                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                        .semantics {
                                            contentDescription = deleteCd
                                            role = Role.Button
                                        },
                                ) {
                                    Text(stringResource(R.string.reader_delete))
                                }
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minWidth = 48.dp, minHeight = 56.dp)
                                .clickable(role = Role.Button) {
                                    showBookmarks = false
                                    jumpToProgress(mark.progress)
                                }
                                .semantics {
                                    contentDescription = itemCd
                                    role = Role.Button
                                },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }

    if (showFind) {
        FindDialog(
            bookText = book.text,
            onDismiss = { showFind = false },
            onJump = { offset ->
                showFind = false
                jumpToOffset(offset)
            },
        )
    }

    // Custom font picker (TTF/OTF).
    val importFontLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = ReaderCustomFont.importFromUri(context, uri, uri.lastPathSegment)
        if (name != null) {
            // Drop previous custom file if renamed differently.
            val previous = preferences.customFontName()
            if (previous.isNotEmpty() && previous != name) {
                ReaderCustomFont.deleteCustomFont(context, previous)
            }
            preferences.setCustomFontName(name)
            preferences.setFontFamily(ReaderPreferences.FONT_CUSTOM)
            customFontName = name
            fontFamilyId = ReaderPreferences.FONT_CUSTOM
            Toast.makeText(
                context,
                context.getString(R.string.reader_font_import_ok, name),
                Toast.LENGTH_SHORT,
            ).show()
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.reader_font_import_fail),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    if (showAppearance) {
        AppearanceDialog(
            selectedTheme = theme,
            fontSize = fontSizeSp,
            lineHeightMultiplier = lineHeightMultiplier,
            marginStep = marginStep,
            fontFamilyId = fontFamilyId,
            brightness = brightness,
            keepScreenOn = keepScreenOn,
            volumePageTurn = volumePageTurn,
            pageTurnAnimation = pageTurnAnimation,
            paragraphIndent = paragraphIndent,
            autoNight = autoNight,
            customFontName = customFontName,
            autoNightStartHour = preferences.autoNightStartHour(),
            autoNightEndHour = preferences.autoNightEndHour(),
            ttsRate = ttsRate,
            ttsEnginePackage = ttsEnginePackage,
            ttsEngines = ttsEngines,
            autoPageTurnSec = autoPageTurnSec,
            onDismiss = { showAppearance = false },
            onTheme = { value ->
                val t = clampReaderTheme(value)
                preferences.setTheme(t)
                theme = t
                if (ReaderPreferences.isNightTheme(t)) {
                    preferences.setNightThemeVariant(t)
                } else {
                    preferences.setDayTheme(t)
                }
            },
            onFontSize = { size ->
                fontSizeSp = size
                preferences.setFontSize(size)
            },
            onLineHeightMultiplier = { mult ->
                lineHeightMultiplier = mult
                preferences.setLineHeightMultiplier(mult)
            },
            onMarginStep = { step ->
                marginStep = PageLayout.clampMarginStep(step)
                preferences.setMargin(marginStep)
            },
            onFontFamily = { id ->
                val clamped = clampFontFamily(id)
                if (clamped == ReaderPreferences.FONT_CUSTOM && customFontName.isEmpty()) {
                    importFontLauncher.launch(
                        arrayOf(
                            "font/ttf",
                            "font/otf",
                            "application/x-font-ttf",
                            "application/x-font-otf",
                            "application/octet-stream",
                            "*/*",
                        ),
                    )
                } else {
                    fontFamilyId = clamped
                    preferences.setFontFamily(fontFamilyId)
                }
            },
            onImportFont = {
                importFontLauncher.launch(
                    arrayOf(
                        "font/ttf",
                        "font/otf",
                        "application/x-font-ttf",
                        "application/x-font-otf",
                        "application/octet-stream",
                        "*/*",
                    ),
                )
            },
            onClearCustomFont = {
                val previous = preferences.customFontName()
                if (previous.isNotEmpty()) {
                    ReaderCustomFont.deleteCustomFont(context, previous)
                }
                preferences.clearCustomFont()
                customFontName = ""
                fontFamilyId = ReaderPreferences.FONT_SERIF
            },
            onBrightness = { value ->
                brightness = value
                preferences.setBrightness(value)
                applyBrightness(activity, value)
            },
            onKeepScreenOn = { enabled ->
                keepScreenOn = enabled
                preferences.setKeepScreenOn(enabled)
            },
            onVolumePageTurn = { enabled ->
                volumePageTurn = enabled
                preferences.setVolumePageTurn(enabled)
            },
            onPageTurnAnimation = { enabled ->
                pageTurnAnimation = enabled
                preferences.setPageTurnAnimation(enabled)
            },
            onParagraphIndent = { enabled ->
                paragraphIndent = enabled
                preferences.setParagraphIndent(enabled)
            },
            onAutoNight = { enabled ->
                autoNight = enabled
                preferences.setAutoNight(enabled)
                if (enabled) {
                    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                    val resolved = ReaderReadingPolish.resolveEffectiveTheme(
                        autoNightEnabled = true,
                        hourOfDay = hour,
                        manualTheme = theme,
                        dayTheme = preferences.dayTheme(),
                        nightTheme = preferences.nightThemeVariant(),
                        startHour = preferences.autoNightStartHour(),
                        endHour = preferences.autoNightEndHour(),
                    )
                    theme = resolved
                    preferences.setTheme(resolved)
                }
            },
            onTtsRate = { rate ->
                val clamped = TtsRate.clamp(rate)
                ttsRate = clamped
                preferences.setTtsRate(clamped)
                ttsController?.setSpeechRate(clamped)
            },
            onTtsEngine = { pkg -> applyTtsEngine(pkg) },
            onOpenTtsSettings = {
                try {
                    context.startActivity(TtsEngineCatalog.systemTtsSettingsIntent())
                } catch (_: Exception) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.reader_tts_unavailable),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            onAutoPageTurnSec = { sec ->
                autoPageTurnSec = AutoPageTurn.clampSec(sec)
                preferences.setAutoPageTurnSec(autoPageTurnSec)
            },
        )
    }

    if (showTtsRateDialog) {
        AlertDialog(
            onDismissRequest = { showTtsRateDialog = false },
            title = { Text(stringResource(R.string.reader_tts_rate_dialog_title)) },
            text = {
                Column {
                    TtsRate.PRESETS.forEach { preset ->
                        val selected = TtsRate.isPresetSelected(ttsRate, preset)
                        val label = TtsRate.label(preset)
                        Text(
                            text = if (selected) "● $label" else "○ $label",
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .clickable {
                                    val clamped = TtsRate.clamp(preset)
                                    ttsRate = clamped
                                    preferences.setTtsRate(clamped)
                                    ttsController?.setSpeechRate(clamped)
                                    showTtsRateDialog = false
                                }
                                .padding(vertical = 12.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTtsRateDialog = false }) {
                    Text(stringResource(R.string.reader_close))
                }
            },
        )
    }

    if (showTtsEngineDialog) {
        val engines = ttsEngines.ifEmpty {
            listOf(TtsEngineCatalog.systemDefaultOption())
        }
        val googleInstalled = TtsEngineCatalog.isGoogleTtsInstalled(context)
        AlertDialog(
            onDismissRequest = { showTtsEngineDialog = false },
            title = { Text(stringResource(R.string.reader_tts_engine_pick_title)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (!googleInstalled) {
                        Text(
                            text = stringResource(R.string.reader_tts_engine_no_google),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    if (engines.size <= 1) {
                        Text(
                            text = stringResource(R.string.reader_tts_engine_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    engines.forEach { engine ->
                        val selected = engine.packageName == ttsEnginePackage
                        Text(
                            text = if (selected) "● ${engine.label}" else "○ ${engine.label}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .clickable {
                                    applyTtsEngine(engine.packageName)
                                    showTtsEngineDialog = false
                                }
                                .padding(vertical = 12.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.reader_tts_rate_dialog_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    TtsRate.PRESETS.forEach { preset ->
                        val selected = TtsRate.isPresetSelected(ttsRate, preset)
                        val label = TtsRate.label(preset)
                        Text(
                            text = if (selected) "● $label" else "○ $label",
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .clickable {
                                    val clamped = TtsRate.clamp(preset)
                                    ttsRate = clamped
                                    preferences.setTtsRate(clamped)
                                    ttsController?.setSpeechRate(clamped)
                                }
                                .padding(vertical = 10.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            try {
                                context.startActivity(TtsEngineCatalog.systemTtsSettingsIntent())
                            } catch (_: Exception) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.reader_tts_unavailable),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.reader_tts_engine_open_settings))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTtsEngineDialog = false }) {
                    Text(stringResource(R.string.reader_close))
                }
            },
        )
    }
}


/**
 * Right-edge TOC scrub rail: drag to jump chapter index instantly (1 → 20 → 30),
 * with a floating bubble showing the chapter number and title.
 */
@Composable
private fun TocScrubRail(
    chapterCount: Int,
    scrubIndex: Int,
    scrubbing: Boolean,
    bubbleTitle: String,
    contentDescription: String,
    onScrub: (Int) -> Unit,
    onScrubEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var railHeightPx by remember { mutableIntStateOf(0) }
    val ticks = remember(chapterCount) { ChapterIndex.tocScrubTickLabels(chapterCount) }
    val bubbleLabel = if (bubbleTitle.isNotEmpty()) {
        stringResource(
            R.string.reader_toc_scrub_bubble_named,
            scrubIndex + 1,
            bubbleTitle.take(18),
        )
    } else {
        stringResource(R.string.reader_toc_scrub_bubble, scrubIndex + 1)
    }

    fun indexFromY(y: Float): Int {
        val h = railHeightPx.coerceAtLeast(1).toFloat()
        val fraction = (y / h).coerceIn(0f, 1f)
        return ChapterIndex.tocIndexForScrubFraction(fraction, chapterCount)
    }

    Box(
        modifier = modifier
            .width(40.dp)
            .fillMaxHeight()
            .semantics { this.contentDescription = contentDescription }
            .onSizeChanged { railHeightPx = it.height }
            .pointerInput(chapterCount) {
                detectTapGestures { offset ->
                    onScrub(indexFromY(offset.y))
                    onScrubEnd()
                }
            }
            .pointerInput(chapterCount) {
                detectDragGestures(
                    onDragStart = { offset -> onScrub(indexFromY(offset.y)) },
                    onDragEnd = { onScrubEnd() },
                    onDragCancel = { onScrubEnd() },
                    onDrag = { change, _ ->
                        change.consume()
                        onScrub(indexFromY(change.position.y))
                    },
                )
            },
    ) {
        // Soft track
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        )
        // Sparse chapter numbers along the rail (1 … 20 … 30 … last)
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ticks.forEach { oneBased ->
                Text(
                    text = oneBased.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
        }
        // Thumb
        val thumbFrac = ChapterIndex.tocScrubFractionForIndex(scrubIndex, chapterCount)
        val thumbY = ((railHeightPx - with(density) { 14.dp.toPx() }) * thumbFrac)
            .coerceAtLeast(0f)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, thumbY.roundToInt()) }
                .width(14.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
        // Floating bubble while dragging
        if (scrubbing && railHeightPx > 0) {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = RoundedCornerShape(8.dp),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset {
                        val by = (thumbY - with(density) { 18.dp.toPx() })
                            .coerceIn(0f, (railHeightPx - with(density) { 36.dp.toPx() }).coerceAtLeast(0f))
                        IntOffset(with(density) { (-120).dp.roundToPx() }, by.roundToInt())
                    }
                    .widthIn(max = 160.dp),
            ) {
                Text(
                    text = bubbleLabel,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/**
 * Material 3 text control for the reader bottom bar.
 * ≥48dp touch target, TalkBack [contentDescription] + [Role.Button].
 */
@Composable
private fun ControlLabel(
    text: String,
    enabled: Boolean,
    color: Color,
    onClick: () -> Unit,
    contentDescription: String = text,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        colors = ButtonDefaults.textButtonColors(
            contentColor = color,
            disabledContentColor = color.copy(alpha = 0.35f),
        ),
        modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .semantics {
                this.contentDescription = contentDescription
                this.role = Role.Button
            },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FindDialog(
    bookText: String,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Pair<Int, String>>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }

    fun runSearch() {
        val keyword = query.trim()
        if (keyword.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.reader_find_empty), Toast.LENGTH_SHORT).show()
            return
        }
        val content = bookText.lowercase(Locale.ROOT)
        val needle = keyword.lowercase(Locale.ROOT)
        val positions = ArrayList<Int>()
        var from = 0
        while (positions.size < 50) {
            val at = content.indexOf(needle, from)
            if (at < 0) break
            positions.add(at)
            from = at + needle.length.coerceAtLeast(1)
        }
        searched = true
        if (positions.isEmpty()) {
            results = emptyList()
            Toast.makeText(
                context,
                context.getString(R.string.reader_find_none, keyword),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        results = positions.map { at ->
            val start = (at - 20).coerceAtLeast(0)
            val end = (at + keyword.length + 36).coerceAtMost(bookText.length)
            val snippet = buildString {
                append(bookText.substring(start, at).replace('\n', ' '))
                append('【')
                append(bookText.substring(at, at + keyword.length))
                append('】')
                append(bookText.substring(at + keyword.length, end).replace('\n', ' '))
            }
            at to snippet
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_find_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.reader_find_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (results.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (results.size >= 50) {
                            stringResource(R.string.reader_find_results_capped, query.trim(), 50)
                        } else {
                            stringResource(R.string.reader_find_results, query.trim(), results.size)
                        },
                        style = MaterialTheme.typography.labelLarge,
                    )
                    LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                        itemsIndexed(results) { _, item ->
                            Text(
                                text = item.second,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .clickable { onJump(item.first) }
                                    .padding(vertical = 10.dp)
                                    .semantics {
                                        contentDescription = context.getString(
                                            R.string.reader_jump_match_cd,
                                            item.second,
                                        )
                                    },
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            HorizontalDivider()
                        }
                    }
                } else if (searched) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.reader_find_none, query.trim()),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { runSearch() },
                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
            ) {
                Text(stringResource(R.string.reader_search))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
            ) {
                Text(stringResource(R.string.reader_cancel))
            }
        },
    )
}



@Composable
private fun AppearanceDialog(
    selectedTheme: Int,
    fontSize: Int,
    lineHeightMultiplier: Float,
    marginStep: Int,
    fontFamilyId: Int,
    brightness: Float,
    keepScreenOn: Boolean,
    volumePageTurn: Boolean,
    pageTurnAnimation: Boolean,
    paragraphIndent: Boolean,
    autoNight: Boolean,
    customFontName: String,
    autoNightStartHour: Int,
    autoNightEndHour: Int,
    ttsRate: Float,
    ttsEnginePackage: String,
    ttsEngines: List<TtsEngineOption>,
    autoPageTurnSec: Int,
    onDismiss: () -> Unit,
    onTheme: (Int) -> Unit,
    onFontSize: (Int) -> Unit,
    onLineHeightMultiplier: (Float) -> Unit,
    onMarginStep: (Int) -> Unit,
    onFontFamily: (Int) -> Unit,
    onImportFont: () -> Unit,
    onClearCustomFont: () -> Unit,
    onBrightness: (Float) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onVolumePageTurn: (Boolean) -> Unit,
    onPageTurnAnimation: (Boolean) -> Unit,
    onParagraphIndent: (Boolean) -> Unit,
    onAutoNight: (Boolean) -> Unit,
    onTtsRate: (Float) -> Unit,
    onTtsEngine: (String) -> Unit,
    onOpenTtsSettings: () -> Unit,
    onAutoPageTurnSec: (Int) -> Unit,
) {
    val selectedSuffix = stringResource(R.string.reader_selected_suffix)
    val themeLabels = mapOf(
        ReaderPreferences.THEME_PAPER to stringResource(R.string.reader_theme_paper),
        ReaderPreferences.THEME_WHITE to stringResource(R.string.reader_theme_white),
        ReaderPreferences.THEME_PARCHMENT to stringResource(R.string.reader_theme_parchment),
        ReaderPreferences.THEME_EYE_CARE to stringResource(R.string.reader_theme_eye),
        ReaderPreferences.THEME_GREEN to stringResource(R.string.reader_theme_green),
        ReaderPreferences.THEME_PINK to stringResource(R.string.reader_theme_pink),
        ReaderPreferences.THEME_GRAY to stringResource(R.string.reader_theme_gray),
        ReaderPreferences.THEME_NIGHT to stringResource(R.string.reader_theme_night),
        ReaderPreferences.THEME_SOFT_NIGHT to stringResource(R.string.reader_theme_soft_night),
    )
    val papers = paperThemeOptions()
    val lineHeights = listOf(
        1.4f to stringResource(R.string.reader_line_height_compact),
        1.85f to stringResource(R.string.reader_line_height_standard),
        2.2f to stringResource(R.string.reader_line_height_relaxed),
    )
    val margins = listOf(
        ReaderPreferences.MARGIN_NARROW to (
            stringResource(R.string.reader_margin_narrow) to
                stringResource(R.string.reader_margin_narrow_cd)
            ),
        ReaderPreferences.MARGIN_STANDARD to (
            stringResource(R.string.reader_margin_standard) to
                stringResource(R.string.reader_margin_standard_cd)
            ),
        ReaderPreferences.MARGIN_WIDE to (
            stringResource(R.string.reader_margin_wide) to
                stringResource(R.string.reader_margin_wide_cd)
            ),
    )
    val fonts = listOf(
        ReaderPreferences.FONT_SERIF to (
            stringResource(R.string.reader_font_serif) to
                stringResource(R.string.reader_font_serif_cd)
            ),
        ReaderPreferences.FONT_SANS to (
            stringResource(R.string.reader_font_sans) to
                stringResource(R.string.reader_font_sans_cd)
            ),
        ReaderPreferences.FONT_DEFAULT to (
            stringResource(R.string.reader_font_system) to
                stringResource(R.string.reader_font_system_cd)
            ),
        ReaderPreferences.FONT_CUSTOM to (
            stringResource(R.string.reader_font_custom) to
                stringResource(R.string.reader_font_custom_cd)
            ),
    )
    val followSystem = brightness < 0f
    val brightnessPercent = if (followSystem) {
        -1
    } else {
        (brightness.coerceIn(0.08f, 1f) * 100f).roundToInt()
    }
    val appearanceScroll = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_appearance_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(appearanceScroll),
            ) {
                Text(
                    text = stringResource(R.string.reader_theme_section),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    papers.chunked(3).forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            row.forEach { paper ->
                                val label = themeLabels[paper.id].orEmpty()
                                val selected = paper.id == selectedTheme
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onTheme(paper.id) }
                                        .padding(4.dp)
                                        .semantics {
                                            contentDescription =
                                                label + if (selected) selectedSuffix else ""
                                        },
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(40.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(paper.swatch),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "Aa",
                                            color = paper.ink,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = if (selected) {
                                                FontWeight.Bold
                                            } else {
                                                FontWeight.Normal
                                            },
                                        )
                                    }
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                            }
                            repeat(3 - row.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                AppearanceToggleRow(
                    label = stringResource(R.string.reader_auto_night),
                    contentDescription = stringResource(R.string.reader_auto_night_cd),
                    checked = autoNight,
                    onCheckedChange = onAutoNight,
                )
                if (autoNight) {
                    Text(
                        text = stringResource(
                            R.string.reader_auto_night_hours,
                            autoNightStartHour,
                            autoNightEndHour,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.reader_font_family_section),
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    fonts.forEach { (id, labels) ->
                        val (label, cd) = labels
                        val selected = id == fontFamilyId
                        TextButton(
                            onClick = { onFontFamily(id) },
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .semantics {
                                    contentDescription =
                                        cd + if (selected) selectedSuffix else ""
                                },
                        ) {
                            Text(
                                text = label,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
                if (customFontName.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.reader_font_custom_using, customFontName),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val importFontLabel = stringResource(R.string.reader_font_import)
                val importFontCd = stringResource(R.string.reader_font_import_cd)
                val clearFontLabel = stringResource(R.string.reader_font_clear)
                val clearFontCd = stringResource(R.string.reader_font_clear_cd)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = onImportFont,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .semantics { contentDescription = importFontCd },
                    ) {
                        Text(importFontLabel)
                    }
                    if (customFontName.isNotEmpty()) {
                        TextButton(
                            onClick = onClearCustomFont,
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .semantics { contentDescription = clearFontCd },
                        ) {
                            Text(clearFontLabel)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.reader_font_size_label, fontSize),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { onFontSize((fontSize - 1).coerceAtLeast(14)) },
                        enabled = fontSize > 14,
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                    ) { Text(stringResource(R.string.reader_font_smaller)) }
                    TextButton(
                        onClick = { onFontSize((fontSize + 1).coerceAtMost(30)) },
                        enabled = fontSize < 30,
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                    ) { Text(stringResource(R.string.reader_font_larger)) }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.reader_line_height_label, lineHeightMultiplier),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            onLineHeightMultiplier(
                                (lineHeightMultiplier - 0.15f)
                                    .coerceAtLeast(ReaderPreferences.MIN_LINE_HEIGHT),
                            )
                        },
                        enabled = lineHeightMultiplier > ReaderPreferences.MIN_LINE_HEIGHT,
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                    ) { Text(stringResource(R.string.reader_line_height_smaller)) }
                    TextButton(
                        onClick = {
                            onLineHeightMultiplier(
                                (lineHeightMultiplier + 0.15f)
                                    .coerceAtMost(ReaderPreferences.MAX_LINE_HEIGHT),
                            )
                        },
                        enabled = lineHeightMultiplier < ReaderPreferences.MAX_LINE_HEIGHT,
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                    ) { Text(stringResource(R.string.reader_line_height_larger)) }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    lineHeights.forEach { (preset, label) ->
                        val selected = Math.abs(lineHeightMultiplier - preset) < 0.05f
                        TextButton(
                            onClick = { onLineHeightMultiplier(preset) },
                            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                        ) {
                            Text(
                                text = label,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.reader_tts_engine_title),
                    style = MaterialTheme.typography.bodyMedium,
                )
                val engineLabel = ttsEngines.firstOrNull { it.packageName == ttsEnginePackage }?.label
                    ?: TtsEngineCatalog.friendlyLabel(ttsEnginePackage)
                Text(
                    text = stringResource(R.string.reader_tts_engine_label, engineLabel),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val enginesForUi = ttsEngines.ifEmpty {
                    listOf(TtsEngineCatalog.systemDefaultOption())
                }
                enginesForUi.forEach { engine ->
                    val selected = engine.packageName == ttsEnginePackage
                    Text(
                        text = if (selected) "● ${engine.label}" else "○ ${engine.label}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .clickable { onTtsEngine(engine.packageName) }
                            .padding(vertical = 10.dp)
                            .semantics {
                                contentDescription =
                                    engine.label + if (selected) selectedSuffix else ""
                            },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
                TextButton(
                    onClick = onOpenTtsSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp),
                ) {
                    Text(stringResource(R.string.reader_tts_engine_open_settings))
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.reader_tts_rate_title),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(
                        R.string.reader_tts_rate_label,
                        TtsRate.label(ttsRate),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    TtsRate.PRESETS.forEach { preset ->
                        val selected = TtsRate.isPresetSelected(ttsRate, preset)
                        val label = TtsRate.label(preset)
                        TextButton(
                            onClick = { onTtsRate(preset) },
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .semantics {
                                    contentDescription =
                                        label + if (selected) selectedSuffix else ""
                                },
                        ) {
                            Text(
                                text = label,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.reader_auto_page_turn_title),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(
                        R.string.reader_auto_page_turn_label,
                        AutoPageTurn.label(autoPageTurnSec),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val autoPageTurnCdPrefix = stringResource(R.string.reader_auto_page_turn_cd)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .horizontalScroll(rememberScrollState()),
                ) {
                    AutoPageTurn.PRESETS_SEC.forEach { preset ->
                        val selected = AutoPageTurn.isPresetSelected(autoPageTurnSec, preset)
                        val label = AutoPageTurn.label(preset)
                        TextButton(
                            onClick = { onAutoPageTurnSec(preset) },
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .semantics {
                                    contentDescription =
                                        "$autoPageTurnCdPrefix $label" +
                                            if (selected) selectedSuffix else ""
                                },
                        ) {
                            Text(
                                text = label,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.reader_margin_label),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    margins.forEach { (step, labels) ->
                        val (label, cd) = labels
                        val selected = step == marginStep
                        TextButton(
                            onClick = { onMarginStep(step) },
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .semantics {
                                    contentDescription =
                                        cd + if (selected) selectedSuffix else ""
                                },
                        ) {
                            Text(
                                text = label,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (followSystem) {
                        stringResource(R.string.reader_brightness_title) +
                            " · " + stringResource(R.string.reader_brightness_follow)
                    } else {
                        stringResource(R.string.reader_brightness_value, brightnessPercent)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.reader_brightness_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
                )
                Slider(
                    value = if (followSystem) 0.55f else brightness.coerceIn(0.08f, 1f),
                    onValueChange = { onBrightness(it.coerceIn(0.08f, 1f)) },
                    valueRange = 0.08f..1f,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = { onBrightness(-1f) },
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                ) {
                    Text(stringResource(R.string.reader_brightness_system))
                }

                Spacer(Modifier.height(4.dp))
                AppearanceToggleRow(
                    label = stringResource(R.string.reader_keep_screen_on),
                    contentDescription = stringResource(R.string.reader_keep_screen_on_cd),
                    checked = keepScreenOn,
                    onCheckedChange = onKeepScreenOn,
                )
                AppearanceToggleRow(
                    label = stringResource(R.string.reader_volume_page_turn),
                    contentDescription = stringResource(R.string.reader_volume_page_turn_cd),
                    checked = volumePageTurn,
                    onCheckedChange = onVolumePageTurn,
                )
                AppearanceToggleRow(
                    label = stringResource(R.string.reader_page_turn_animation),
                    contentDescription = stringResource(R.string.reader_page_turn_animation_cd),
                    checked = pageTurnAnimation,
                    onCheckedChange = onPageTurnAnimation,
                )
                AppearanceToggleRow(
                    label = stringResource(R.string.reader_paragraph_indent),
                    contentDescription = stringResource(R.string.reader_paragraph_indent_cd),
                    checked = paragraphIndent,
                    onCheckedChange = onParagraphIndent,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
            ) {
                Text(stringResource(R.string.reader_close))
            }
        },
    )
}


@Composable
private fun AppearanceToggleRow(
    label: String,
    contentDescription: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (checked) "开" else "关",
            style = MaterialTheme.typography.labelLarge,
            color = if (checked) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}


private fun formatTime(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

private fun applyBrightness(activity: ComponentActivity, brightness: Float) {
    val attrs = activity.window.attributes
    attrs.screenBrightness = brightness
    activity.window.attributes = attrs
}
