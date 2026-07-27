package app.maoyankanshu.novel.selfuse.ui.reader

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.maoyankanshu.novel.selfuse.Book
import app.maoyankanshu.novel.selfuse.BookmarkStore
import app.maoyankanshu.novel.selfuse.LibraryStore
import app.maoyankanshu.novel.selfuse.R
import app.maoyankanshu.novel.selfuse.ReaderPreferences
import app.maoyankanshu.novel.selfuse.ReadingHistory
import app.maoyankanshu.novel.selfuse.ReadingStats
import kotlinx.coroutines.CoroutineScope
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
) {
    val context = LocalContext.current
    val preferences = remember { ReaderPreferences.get(context) }
    val scope = rememberCoroutineScope()
    val tapHint = stringResource(R.string.reader_tap_hint)
    val backCd = stringResource(R.string.reader_back_cd)
    val appearanceCd = stringResource(R.string.reader_appearance_cd)
    val findCd = stringResource(R.string.reader_find_cd)
    val bookmarksCd = stringResource(R.string.reader_bookmarks_cd)
    val tocCd = stringResource(R.string.reader_toc_cd)
    val fontSmallerCd = stringResource(R.string.reader_font_smaller_cd)
    val fontLargerCd = stringResource(R.string.reader_font_larger_cd)
    val lineHeightSmallerCd = stringResource(R.string.reader_line_height_smaller_cd)
    val lineHeightLargerCd = stringResource(R.string.reader_line_height_larger_cd)

    var theme by remember { mutableIntStateOf(preferences.theme()) }
    var fontSizeSp by remember { mutableIntStateOf(preferences.fontSize()) }
    var lineHeightMultiplier by remember { mutableFloatStateOf(preferences.lineHeightMultiplier()) }
    var menuVisible by remember { mutableStateOf(false) }
    var showToc by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showFind by remember { mutableStateOf(false) }
    var showAppearance by remember { mutableStateOf(false) }
    var bookmarkVersion by remember { mutableIntStateOf(0) }

    val palette = remember(theme) { readerPalette(theme) }
    val chapters = remember(book.id, book.text) {
        ChapterIndex.findChapters(book.text, context.getString(R.string.reader_chapter_full))
    }

    val scrollState = rememberScrollState()
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var progress by remember { mutableIntStateOf(book.position.coerceIn(0, 1000)) }
    var currentChapter by remember { mutableIntStateOf(0) }
    var clock by remember { mutableStateOf(formatTime()) }
    var initialRestored by remember { mutableStateOf(false) }

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
            val duration = android.os.SystemClock.elapsedRealtime() - started
            val finalProgress = progress
            CoroutineScope(Dispatchers.IO).launch {
                ReadingStats.add(appContext, duration)
                LibraryStore.get(appContext).savePosition(bookId, finalProgress)
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            clock = formatTime()
            delay(30_000)
        }
    }

    LaunchedEffect(textLayout, scrollState.maxValue) {
        if (initialRestored || textLayout == null || scrollState.maxValue <= 0) return@LaunchedEffect
        val target = ProgressMath.scrollYForProgress(book.position, scrollState.maxValue)
        scrollState.scrollTo(target)
        progress = book.position.coerceIn(0, 1000)
        currentChapter = ChapterIndex.chapterAtOffset(
            chapters,
            offsetForScrollY(textLayout!!, target),
        )
        initialRestored = true
    }

    LaunchedEffect(scrollState, textLayout, chapters) {
        snapshotFlow { scrollState.value to scrollState.maxValue }
            .distinctUntilChanged()
            .collect { (value, max) ->
                if (max <= 0) return@collect
                val p = ProgressMath.progressForScrollY(value, max)
                progress = p
                textLayout?.let { layout ->
                    currentChapter = ChapterIndex.chapterAtOffset(
                        chapters,
                        offsetForScrollY(layout, value),
                    )
                }
            }
    }

    LaunchedEffect(book.id) {
        snapshotFlow { progress }
            .distinctUntilChanged()
            .debounce(500L)
            .collect { p ->
                withContext(Dispatchers.IO) {
                    LibraryStore.get(context).savePosition(book.id, p)
                }
            }
    }

    fun scrollToOffset(offset: Int) {
        val layout = textLayout ?: return
        val clamped = offset.coerceIn(0, book.text.length)
        val line = layout.getLineForOffset(clamped)
        val y = layout.getLineTop(line).toInt().coerceIn(0, scrollState.maxValue.coerceAtLeast(0))
        scope.launch { scrollState.animateScrollTo(y) }
    }

    fun scrollToProgress(p: Int) {
        val max = scrollState.maxValue
        if (max <= 0) return
        scope.launch { scrollState.animateScrollTo(ProgressMath.scrollYForProgress(p, max)) }
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
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { menuVisible = !menuVisible })
                    },
            ) {
                SelectionContainer {
                    Text(
                        text = book.text,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 18.dp, vertical = 16.dp)
                            .semantics { contentDescription = tapHint },
                        style = TextStyle(
                            color = palette.onBackground,
                            fontSize = fontSizeSp.sp,
                            lineHeight = (fontSizeSp * lineHeightMultiplier).sp,
                            fontFamily = FontFamily.Serif,
                            letterSpacing = 0.2.sp,
                        ),
                        onTextLayout = { textLayout = it },
                    )
                }
            }

            val percent = (progress / 10f).roundToInt()
            val progressCd = stringResource(R.string.reader_progress_cd, percent)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .semantics { contentDescription = progressCd },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = clock,
                    color = palette.muted,
                    style = MaterialTheme.typography.labelMedium,
                )
                val chapterTitle = chapters.getOrNull(currentChapter)?.title.orEmpty()
                Text(
                    text = buildString {
                        if (chapterTitle.isNotEmpty()) {
                            append(chapterTitle.take(12))
                            if (chapterTitle.length > 12) append('…')
                            append(" · ")
                        }
                        append("$percent%")
                    },
                    color = palette.muted,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
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
                                scrollToOffset(chapters[currentChapter - 1].start)
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
                                scrollToOffset(chapters[currentChapter + 1].start)
                            }
                        },
                    )
                }
            }
        }
    }

    if (showToc) {
        ModalBottomSheet(
            onDismissRequest = { showToc = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Text(
                text = stringResource(R.string.reader_toc),
                style = MaterialTheme.typography.titleMedium,
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
                    Text(
                        text = chapter.title,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .clickable {
                                showToc = false
                                scrollToOffset(chapter.start)
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                            .semantics {
                                contentDescription = chapter.title +
                                    if (selected) {
                                        context.getString(R.string.reader_chapter_current_suffix)
                                    } else {
                                        ""
                                    }
                            },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (showBookmarks) {
        val bookmarks = remember(bookmarkVersion, book.id) {
            BookmarkStore.get(context).list(book.id)
        }
        ModalBottomSheet(
            onDismissRequest = { showBookmarks = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Text(
                text = stringResource(R.string.reader_bookmarks),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .semantics { heading() },
            )
            TextButton(
                onClick = {
                    val label = chapters.getOrNull(currentChapter)?.title
                        ?: context.getString(R.string.reader_current_position)
                    BookmarkStore.get(context).add(book.id, progress, label)
                    bookmarkVersion++
                    Toast.makeText(
                        context,
                        context.getString(R.string.reader_bookmark_added),
                        Toast.LENGTH_SHORT,
                    ).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .padding(horizontal = 8.dp),
            ) {
                Text(stringResource(R.string.reader_add_bookmark))
            }
            LazyColumn(
                contentPadding = PaddingValues(bottom = 32.dp),
                modifier = Modifier.heightIn(max = 360.dp),
            ) {
                itemsIndexed(bookmarks) { index, mark ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val pct = Math.round(mark.progress / 10f)
                        Text(
                            text = stringResource(R.string.reader_bookmark_list_item, mark.label, pct),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .clickable {
                                    showBookmarks = false
                                    scrollToProgress(mark.progress)
                                }
                                .padding(vertical = 14.dp)
                                .semantics {
                                    contentDescription = context.getString(
                                        R.string.reader_bookmark_item_cd,
                                        mark.label,
                                        pct,
                                    )
                                },
                        )
                        TextButton(
                            onClick = {
                                BookmarkStore.get(context).remove(book.id, index)
                                bookmarkVersion++
                            },
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .semantics {
                                    contentDescription = context.getString(
                                        R.string.reader_delete_bookmark_cd,
                                        mark.label,
                                    )
                                },
                        ) {
                            Text(stringResource(R.string.reader_delete))
                        }
                    }
                    HorizontalDivider()
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
                scrollToOffset(offset)
            },
        )
    }

    if (showAppearance) {
        AppearanceDialog(
            selectedTheme = theme,
            fontSize = fontSizeSp,
            lineHeightMultiplier = lineHeightMultiplier,
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
        )
    }
}

@Composable
private fun ControlLabel(
    text: String,
    enabled: Boolean,
    color: Color,
    onClick: () -> Unit,
    contentDescription: String = text,
) {
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) color else color.copy(alpha = 0.35f),
            style = MaterialTheme.typography.labelLarge,
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
    onDismiss: () -> Unit,
    onTheme: (Int) -> Unit,
    onFontSize: (Int) -> Unit,
    onLineHeightMultiplier: (Float) -> Unit,
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

private fun offsetForScrollY(layout: TextLayoutResult, scrollY: Int): Int {
    val line = layout.getLineForVerticalPosition(scrollY.toFloat().coerceAtLeast(0f))
    return layout.getLineStart(line)
}

private fun applyBrightness(activity: ComponentActivity, brightness: Float) {
    val attrs = activity.window.attributes
    attrs.screenBrightness = brightness
    activity.window.attributes = attrs
}
