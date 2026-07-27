package app.maoyankanshu.novel.selfuse.ui.screens

import android.content.Intent
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.maoyankanshu.novel.selfuse.Book
import app.maoyankanshu.novel.selfuse.BookDetailActivity
import app.maoyankanshu.novel.selfuse.LibraryStore
import app.maoyankanshu.novel.selfuse.ReadingHistory
import app.maoyankanshu.novel.selfuse.ReadingStats
import app.maoyankanshu.novel.selfuse.ui.components.BookCard
import java.text.DateFormat
import java.util.Date

@Composable
fun DiscoverScreen(
    books: List<Book>,
    historyVersion: Int,
    onHistoryCleared: () -> Unit,
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
                    text = "阅读概览",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "今日阅读：$todayLabel\n书籍：${books.size} 本\n已开始：$started 本\n本地文本：$characters 字",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics {
                        contentDescription =
                            "今日阅读 $todayLabel，共 ${books.size} 本书，已开始 $started 本，本地文本 $characters 字"
                    },
                )
            }
        }

        item {
            Text(
                text = "继续阅读",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .semantics { heading() },
            )
        }

        if (inProgress.isEmpty()) {
            item {
                Text(
                    text = "尚没有进行中的阅读。打开书架中的书即可开始记录进度。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(inProgress, key = { "progress-${it.id}" }) { book ->
                BookCard(
                    book = book,
                    subtitle = "${book.title} · ${book.progressLabel()}",
                    onClick = {
                        context.startActivity(
                            Intent(context, BookDetailActivity::class.java)
                                .putExtra(BookDetailActivity.EXTRA_ID, book.id),
                        )
                    },
                )
            }
        }

        if (history.isNotEmpty()) {
            item {
                Text(
                    text = "最近阅读",
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
                    subtitle = "${book.title} · ${timeFormat.format(Date(entry.at))}",
                    onClick = {
                        context.startActivity(
                            Intent(context, BookDetailActivity::class.java)
                                .putExtra(BookDetailActivity.EXTRA_ID, book.id),
                        )
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
                        .semantics { contentDescription = "清除阅读历史" },
                ) {
                    Text("清除阅读历史")
                }
            }
        }
    }
}
