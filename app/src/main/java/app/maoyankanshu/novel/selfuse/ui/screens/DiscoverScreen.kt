package app.maoyankanshu.novel.selfuse.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.maoyankanshu.novel.selfuse.AppIntents
import app.maoyankanshu.novel.selfuse.Book
import app.maoyankanshu.novel.selfuse.LibraryStore
import app.maoyankanshu.novel.selfuse.R
import app.maoyankanshu.novel.selfuse.ReadingHistory
import app.maoyankanshu.novel.selfuse.ReadingStats
import app.maoyankanshu.novel.selfuse.ui.components.BookCard
import app.maoyankanshu.novel.selfuse.ui.components.EmptyState
import java.text.DateFormat
import java.util.Date

@Composable
fun DiscoverScreen(
    books: List<Book>,
    historyVersion: Int,
    onHistoryCleared: () -> Unit,
    onOpenShelf: () -> Unit,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    var showClearDialog by remember { mutableStateOf(false) }
    val characters = remember(books) { books.sumOf { it.text.length } }
    val started = remember(books) { books.count { it.position > 0 } }
    val inProgress = remember(books) {
        books.filter { it.position > 0 && it.position < 1000 }
    }
    val booksById = remember(books) { books.associateBy { it.id } }
    val history = remember(historyVersion) { ReadingHistory.get(context).list() }
    val timeFormat = remember {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    }
    val todayLabel = remember(historyVersion) { ReadingStats.todayLabel(context) }

    val overviewCd = stringResource(
        R.string.discover_overview_cd,
        todayLabel,
        books.size,
        started,
        characters,
    )
    val clearHistory = stringResource(R.string.discover_clear_history)

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
                        ReadingHistory.get(context).clear()
                        onHistoryCleared()
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        OverviewStatTile(
                            label = stringResource(R.string.discover_stat_today),
                            value = todayLabel,
                            modifier = Modifier.weight(1f),
                        )
                        OverviewStatTile(
                            label = stringResource(R.string.discover_stat_books),
                            value = stringResource(R.string.discover_stat_count, books.size),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        OverviewStatTile(
                            label = stringResource(R.string.discover_stat_started),
                            value = stringResource(R.string.discover_stat_count, started),
                            modifier = Modifier.weight(1f),
                        )
                        OverviewStatTile(
                            label = stringResource(R.string.discover_stat_characters),
                            value = stringResource(R.string.discover_stat_chars, characters),
                            modifier = Modifier.weight(1f),
                        )
                    }
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

        if (history.isNotEmpty()) {
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

            items(history, key = { "history-${it.bookId}-${it.at}" }) { entry ->
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
