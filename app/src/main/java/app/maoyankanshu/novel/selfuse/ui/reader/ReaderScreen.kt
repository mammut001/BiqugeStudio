package app.maoyankanshu.novel.selfuse.ui.reader

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.maoyankanshu.novel.selfuse.AppIntents
import app.maoyankanshu.novel.selfuse.Book
import app.maoyankanshu.novel.selfuse.BookmarkStore
import app.maoyankanshu.novel.selfuse.LibraryStore
import app.maoyankanshu.novel.selfuse.R
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
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
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
    val legacyCd = stringResource(R.string.reader_legacy_cd)
    val fontSmallerCd = stringResource(R.string.reader_font_smaller_cd)
    val fontLargerCd = stringResource(R.string.reader_font_larger_cd)
    val lineHeightSmallerCd = stringResource(R.string.reader_line_height_smaller_cd)
    val lineHeightLargerCd = stringResource(R.string.reader_line_height_larger_cd)

    var theme by remember { mutableIntStateOf(preferences.theme()) }
    var fontSizeSp by remember { mutableIntStateOf(preferences.fontSize()) }
    var lineHeightMultiplier by remember { mutableFloatStateOf(preferences.lineHeightMultiplier()) }
    var marginStep by remember { mutableIntStateOf(preferences.margin()) }
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
    // Restored progress is authoritative until the user turns pages on a fully loaded book.
    var progress by remember(book.id) {
        mutableIntStateOf(ProgressMath.clampProgress(book.position))
    }
    var currentChapter by remember { mutableIntStateOf(0) }
    var clock by remember { mutableStateOf(formatTime()) }
    var initialRestored by remember(book.id, textFullyLoaded) { mutableStateOf(false) }

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
    val initialPagerPage = if (useApproxPaging) {
        PageIndex.pageForProgress(book.position, approxPageCount)
    } else {
        0
    }
    val pagerState = rememberPagerState(
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

    // Latest progress for leave-save: DisposableEffect keys only on book.id.
    val latestProgress by rememberUpdatedState(progress)

    val bodyTextStyle = remember(fontSizeSp, lineHeightMultiplier, palette.onBackground) {
        TextStyle(
            color = palette.onBackground,
            fontSize = fontSizeSp.sp,
            lineHeight = (fontSizeSp * lineHeightMultiplier).sp,
            fontFamily = FontFamily.Serif,
            letterSpacing = 0.2.sp,
        )
    }

    DisposableEffect(Unit) {
        applyBrightness(activity, preferences.brightness())
        onDispose { applyBrightness(activity, -1f) }
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
            val targetPage = if (!initialRestored) {
                initialRestored = true
                PageIndex.pageForProgress(book.position, count)
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
            progress = PageIndex.progressForPage(clamped, count)
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
        val targetPage = if (!initialRestored) {
            // Window preview: show from the start of the window (content already around progress).
            // Full small book: restore via progress.
            initialRestored = true
            if (!textFullyLoaded) {
                0
            } else {
                PageIndex.pageForProgress(book.position, starts.size)
            }
        } else {
            PageIndex.pageForOffset(starts, anchorOffset)
        }
        val clamped = PageIndex.clampPageIndex(targetPage, starts.size)
        if (pagerState.currentPage != clamped) {
            pagerState.scrollToPage(clamped)
        }
        if (textFullyLoaded) {
            progress = PageIndex.progressForPage(clamped, starts.size)
        }
        // else: keep restored book.position in [progress] until full text swaps in
        anchorOffset = PageIndex.offsetForPage(starts, clamped)
        currentChapter = ChapterIndex.chapterAtOffset(chapters, anchorOffset)
    }

    // Page turns → progress (0…1000) + chapter + anchor.
    LaunchedEffect(pagerState, pageStarts, chapters, useApproxPaging, approxCharsPerPage, textFullyLoaded) {
        snapshotFlow {
            Triple(pagerState.currentPage, pageStarts, approxCharsPerPage)
        }
            .distinctUntilChanged()
            .collect { (page, starts, cpp) ->
                if (useApproxPaging) {
                    val count = PageIndex.approximatePageCount(book.text.length, cpp)
                    val p = PageIndex.clampPageIndex(page, count)
                    progress = PageIndex.progressForPage(p, count)
                    val offset = PageIndex.approximateOffsetForPage(p, cpp, book.text.length)
                    anchorOffset = offset
                    currentChapter = ChapterIndex.chapterAtOffset(chapters, offset)
                } else {
                    val count = starts.size.coerceAtLeast(1)
                    val p = PageIndex.clampPageIndex(page, count)
                    if (textFullyLoaded) {
                        progress = PageIndex.progressForPage(p, count)
                    }
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
            // ~280ms left/right turn so tap and swipe both feel like page flips.
            pagerState.animateScrollToPage(
                page = page,
                animationSpec = tween(durationMillis = 280),
            )
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
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                // Kindle-style adjustable body margins (narrow / standard / wide).
                val padH = PageLayout.horizontalPadDp(marginStep).dp
                val padV = PageLayout.verticalPadDp(marginStep).dp
                val widthPx = with(density) { (maxWidth - padH * 2).toPx().toInt().coerceAtLeast(1) }
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
                    ) {
                        when {
                            useApproxPaging -> {
                                // O(page size) slice — never the whole multi‑MB string.
                                PageIndex.approximatePageText(
                                    book.text,
                                    approxCharsPerPage,
                                    page,
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
                    val turn = PageTurnEffect.transform(pageOffset)
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
                        SelectionContainer {
                            Text(
                                text = pageBody,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = padH, vertical = padV),
                                style = bodyTextStyle,
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .semantics {
                            contentDescription = "$pageLocationCd，$progressCd"
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = clock,
                        color = palette.muted,
                        style = MaterialTheme.typography.labelMedium,
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
                    IconButton(
                        onClick = {
                            LibraryStore.getForReading(context).savePosition(
                                book.id,
                                ProgressMath.clampProgress(progress),
                            )
                            context.startActivity(AppIntents.legacyReader(context, book.id))
                        },
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .semantics { contentDescription = legacyCd },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
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
        ModalBottomSheet(
            onDismissRequest = { showToc = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Text(
                text = stringResource(R.string.reader_toc),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .semantics { heading() },
            )
            LazyColumn(
                contentPadding = PaddingValues(bottom = 32.dp),
                modifier = Modifier.heightIn(max = 420.dp),
            ) {
                itemsIndexed(chapters) { index, chapter ->
                    val selected = index == currentChapter
                    val chapterCd = chapter.title + if (selected) currentSuffix else ""
                    ListItem(
                        headlineContent = {
                            Text(
                                text = chapter.title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
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

    if (showAppearance) {
        AppearanceDialog(
            selectedTheme = theme,
            fontSize = fontSizeSp,
            lineHeightMultiplier = lineHeightMultiplier,
            marginStep = marginStep,
            onDismiss = { showAppearance = false },
            onTheme = { value ->
                preferences.setTheme(value)
                theme = value
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
        )
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
    onDismiss: () -> Unit,
    onTheme: (Int) -> Unit,
    onFontSize: (Int) -> Unit,
    onLineHeightMultiplier: (Float) -> Unit,
    onMarginStep: (Int) -> Unit,
) {
    val selectedSuffix = stringResource(R.string.reader_selected_suffix)
    val themes = listOf(
        ReaderPreferences.THEME_PAPER to stringResource(R.string.reader_theme_paper),
        ReaderPreferences.THEME_NIGHT to stringResource(R.string.reader_theme_night),
        ReaderPreferences.THEME_EYE_CARE to stringResource(R.string.reader_theme_eye),
    )
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_appearance_title)) },
        text = {
            Column {
                themes.forEach { (value, label) ->
                    val selected = value == selectedTheme
                    Text(
                        text = if (selected) "● $label" else "○ $label",
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .clickable { onTheme(value) }
                            .padding(vertical = 12.dp)
                            .semantics {
                                contentDescription =
                                    label + if (selected) selectedSuffix else ""
                            },
                    )
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

private fun formatTime(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

private fun applyBrightness(activity: ComponentActivity, brightness: Float) {
    val attrs = activity.window.attributes
    attrs.screenBrightness = brightness
    activity.window.attributes = attrs
}
