package app.maoyankanshu.novel.selfuse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.maoyankanshu.novel.selfuse.AppIntents
import app.maoyankanshu.novel.selfuse.Book
import app.maoyankanshu.novel.selfuse.R
import app.maoyankanshu.novel.selfuse.ReadingHistory
import app.maoyankanshu.novel.selfuse.ReadingStats
import app.maoyankanshu.novel.selfuse.ui.components.BookCard
import app.maoyankanshu.novel.selfuse.ui.components.EmptyState
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ReadingTimeRange(val dayCount: Int) {
    TODAY(1),
    LAST_7_DAYS(7),
    LAST_30_DAYS(30),
}

@Composable
fun DiscoverScreen(
    books: List<Book>,
    historyVersion: Int,
    onHistoryCleared: () -> Unit,
    onOpenShelf: () -> Unit,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showClearDialog by remember { mutableStateOf(false) }
    // Preserve the selected range through rotation/process recreation so returning to “我的”
    // does not unexpectedly snap a 7/30-day view back to Today.
    var timeRangeName by rememberSaveable { mutableStateOf(ReadingTimeRange.TODAY.name) }
    val timeRange = ReadingTimeRange.entries.firstOrNull { it.name == timeRangeName }
        ?: ReadingTimeRange.TODAY
    val characters = remember(books) { LibraryListModels.totalCharacters(books) }
    val started = remember(books) { LibraryListModels.startedCount(books) }
    val inProgress = remember(books) { LibraryListModels.inProgressBooks(books) }
    val booksById = remember(books) { books.associateBy { it.id } }

    // Recent-reading persistence is small but still storage/parsing work. Keep it outside
    // composition/main just like the shelf's Recent sort so a long history never delays a frame.
    var history by remember { mutableStateOf<List<ReadingHistory.Entry>>(emptyList()) }
    LaunchedEffect(historyVersion) {
        history = withContext(Dispatchers.IO) {
            ReadingHistory.get(context).list()
        }
    }
    val visibleHistory = remember(history, booksById) {
        history.filter { booksById.containsKey(it.bookId) }
    }
    val timeFormat = remember {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    }

    // Keep day keys as well as millis: the chart can now explain its time axis instead of showing
    // anonymous bars, while all SharedPreferences work remains off the Compose/main thread.
    var dayEntries by remember { mutableStateOf<List<ReadingStats.DayEntry>>(emptyList()) }
    LaunchedEffect(historyVersion, timeRange) {
        dayEntries = withContext(Dispatchers.IO) {
            ReadingStats.days(context, timeRange.dayCount)
        }
    }
    val dayMillis = remember(dayEntries) { dayEntries.map { it.millis } }
    val rangeMillis = remember(dayMillis) { dayMillis.sum() }
    val rangeLabel = ReadingStats.formatDuration(rangeMillis)
    val dailyAverageMillis = if (timeRange.dayCount > 0) rangeMillis / timeRange.dayCount else 0L
    val dailyAverageLabel = ReadingStats.formatDuration(dailyAverageMillis)
    val activeDays = remember(dayMillis) { dayMillis.count { it > 0L } }
    val bestDayMillis = remember(dayMillis) { dayMillis.maxOrNull() ?: 0L }
    val bestDayLabel = ReadingStats.formatDuration(bestDayMillis)
    val rangeStatLabel = when (timeRange) {
        ReadingTimeRange.TODAY -> stringResource(R.string.discover_stat_today)
        ReadingTimeRange.LAST_7_DAYS -> stringResource(R.string.discover_stat_last_7_days)
        ReadingTimeRange.LAST_30_DAYS -> stringResource(R.string.discover_stat_last_30_days)
    }

    val overviewCd = stringResource(
        R.string.discover_overview_cd,
        rangeStatLabel,
        rangeLabel,
        books.size,
        started,
        characters,
    )
    val clearHistory = stringResource(R.string.discover_clear_history)
    val chartCd = stringResource(
        R.string.discover_duration_chart_cd,
        rangeStatLabel,
        rangeLabel,
        dailyAverageLabel,
        activeDays,
        bestDayLabel,
    )
    val rangeTodayCd = stringResource(R.string.discover_range_today_cd)
    val range7Cd = stringResource(R.string.discover_range_last_7_days_cd)
    val range30Cd = stringResource(R.string.discover_range_last_30_days_cd)

    if (showClearDialog) {
        val dialogTitle = stringResource(R.string.discover_clear_history_title)
        val dialogBody = stringResource(R.string.discover_clear_history_body)
        val confirmText = stringResource(R.string.discover_clear_history_confirm)
        val cancelText = stringResource(R.string.discover_clear_history_cancel)
        val confirmCd = stringResource(R.string.discover_clear_history_confirm_cd)
        val cancelCd = stringResource(R.string.discover_clear_history_cancel_cd)

        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = {
                Text(
                    text = dialogTitle,
                    modifier = Modifier.semantics { heading() },
                )
            },
            text = { Text(dialogBody) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                ReadingHistory.get(context).clear()
                            }
                            onHistoryCleared()
                        }
                    },
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics { contentDescription = confirmCd },
                ) {
                    Text(confirmText)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearDialog = false },
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics { contentDescription = cancelCd },
                ) {
                    Text(cancelText)
                }
            },
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = overviewCd },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.discover_overview_heading),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(12.dp))
                    ReadingTimeRangeChips(
                        selected = timeRange,
                        onSelected = { timeRangeName = it.name },
                        todayCd = rangeTodayCd,
                        last7Cd = range7Cd,
                        last30Cd = range30Cd,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        OverviewStatTile(
                            label = rangeStatLabel,
                            value = rangeLabel,
                            modifier = Modifier.weight(1f),
                        )
                        OverviewStatTile(
                            label = stringResource(R.string.discover_stat_daily_average),
                            value = dailyAverageLabel,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    ReadingDurationBars(
                        dayEntries = dayEntries,
                        contentDescription = chartCd,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        OverviewStatTile(
                            label = stringResource(R.string.discover_stat_active_days),
                            value = stringResource(R.string.discover_stat_days, activeDays),
                            modifier = Modifier.weight(1f),
                        )
                        OverviewStatTile(
                            label = stringResource(R.string.discover_stat_best_day),
                            value = bestDayLabel,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        OverviewStatTile(
                            label = stringResource(R.string.discover_stat_books),
                            value = stringResource(R.string.discover_stat_count, books.size),
                            modifier = Modifier.weight(1f),
                        )
                        OverviewStatTile(
                            label = stringResource(R.string.discover_stat_started),
                            value = stringResource(R.string.discover_stat_count, started),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    OverviewStatTile(
                        label = stringResource(R.string.discover_stat_characters),
                        value = stringResource(R.string.discover_stat_chars, characters),
                    )
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.discover_continue_heading),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .semantics { heading() },
            )
        }

        if (inProgress.isEmpty()) {
            item {
                EmptyState(
                    title = stringResource(R.string.discover_empty_progress_cd),
                    body = stringResource(R.string.discover_empty_progress),
                    contentDescription = stringResource(R.string.discover_empty_progress_cd),
                    primaryLabel = stringResource(R.string.cta_open_shelf),
                    primaryDescription = stringResource(R.string.cta_open_shelf),
                    onPrimary = onOpenShelf,
                )
            }
        } else {
            items(inProgress, key = { "progress-${it.id}" }) { book ->
                BookCard(
                    book = book,
                    onClick = {
                        context.startActivity(AppIntents.bookDetail(context, book.id))
                    },
                    onContinueReading = {
                        context.startActivity(AppIntents.reader(context, book.id))
                    },
                )
            }
        }

        if (visibleHistory.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.discover_history_heading),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .semantics { heading() },
                )
            }

            items(visibleHistory, key = { "history-${it.bookId}-${it.at}" }) { entry ->
                val book = booksById[entry.bookId] ?: return@items
                BookCard(
                    book = book,
                    subtitle = timeFormat.format(Date(entry.at)),
                    onClick = {
                        context.startActivity(AppIntents.bookDetail(context, book.id))
                    },
                    onContinueReading = {
                        context.startActivity(AppIntents.reader(context, book.id))
                    },
                )
            }

            item {
                OutlinedButton(
                    onClick = { showClearDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics { contentDescription = clearHistory },
                ) {
                    Text(clearHistory)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadingTimeRangeChips(
    selected: ReadingTimeRange,
    onSelected: (ReadingTimeRange) -> Unit,
    todayCd: String,
    last7Cd: String,
    last30Cd: String,
) {
    // Flow instead of a fixed Row: three localized labels still remain tappable at large font
    // scale or on narrow devices instead of being squeezed/clipped off-screen.
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FilterChip(
            selected = selected == ReadingTimeRange.TODAY,
            onClick = { onSelected(ReadingTimeRange.TODAY) },
            label = { Text(stringResource(R.string.discover_range_today)) },
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics { contentDescription = todayCd },
        )
        FilterChip(
            selected = selected == ReadingTimeRange.LAST_7_DAYS,
            onClick = { onSelected(ReadingTimeRange.LAST_7_DAYS) },
            label = { Text(stringResource(R.string.discover_range_last_7_days)) },
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics { contentDescription = last7Cd },
        )
        FilterChip(
            selected = selected == ReadingTimeRange.LAST_30_DAYS,
            onClick = { onSelected(ReadingTimeRange.LAST_30_DAYS) },
            label = { Text(stringResource(R.string.discover_range_last_30_days)) },
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics { contentDescription = last30Cd },
        )
    }
}

@Composable
private fun ReadingDurationBars(
    dayEntries: List<ReadingStats.DayEntry>,
    contentDescription: String,
) {
    val dayMillis = remember(dayEntries) { dayEntries.map { it.millis } }
    val maxMillis = dayMillis.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { this.contentDescription = contentDescription },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            horizontalArrangement = Arrangement.spacedBy(if (dayMillis.size > 14) 2.dp else 4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            dayMillis.forEach { millis ->
                val fraction = (millis.toFloat() / maxMillis.toFloat()).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(trackColor),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fraction.coerceAtLeast(if (millis > 0L) 0.08f else 0f))
                            .clip(RoundedCornerShape(3.dp))
                            .background(barColor),
                    )
                }
            }
        }
        if (dayEntries.size > 1) {
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = dayKeyAxisLabel(dayEntries.first().dayKey),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.discover_chart_today),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}

private fun dayKeyAxisLabel(dayKey: String): String {
    if (dayKey.length != 8) return dayKey
    return "${dayKey.substring(4, 6)}/${dayKey.substring(6, 8)}"
}

@Composable
private fun OverviewStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
