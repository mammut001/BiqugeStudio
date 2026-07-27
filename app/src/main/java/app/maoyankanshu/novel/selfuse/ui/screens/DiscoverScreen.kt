package app.maoyankanshu.novel.selfuse.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
    val characters = remember(books) { books.sumOf { it.text.length } }
    val started = remember(books) { books.count { it.position > 0 } }
    val inProgress = remember(books) {
        books.filter { it.position > 0 && it.position < 1000 }
    }
    val history = remember(historyVersion) { ReadingHistory.get(context).list() }
    val timeFormat = remember {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    }
    val todayLabel = remember(historyVersion) { ReadingStats.todayLabel(context) }

    val overviewBody = stringResource(
        R.string.discover_overview_body,
        todayLabel,
        books.size,
        started,
        characters,
    )
    val overviewCd = stringResource(
        R.string.discover_overview_cd,
        todayLabel,
        books.size,
        started,
        characters,
    )
    val clearHistory = stringResource(R.string.discover_clear_history)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column {
                Text(
                    text = stringResource(R.string.discover_overview_heading),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = overviewBody,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { contentDescription = overviewCd },
                )
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
                val book = LibraryStore.get(context).byId(entry.bookId) ?: return@items
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
                    onClick = {
                        ReadingHistory.get(context).clear()
                        onHistoryCleared()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = clearHistory },
                ) {
                    Text(clearHistory)
                }
            }
        }
    }
}
