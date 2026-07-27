package app.maoyankanshu.novel.selfuse.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.maoyankanshu.novel.selfuse.Book
import app.maoyankanshu.novel.selfuse.BookDetailActivity
import app.maoyankanshu.novel.selfuse.LibraryStore
import app.maoyankanshu.novel.selfuse.ReaderActivity
import app.maoyankanshu.novel.selfuse.ui.components.BookCard

@Composable
fun ShelfScreen(
    books: List<Book>,
    onLibraryChanged: () -> Unit,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    var menuBook by remember { mutableStateOf<Book?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (books.isEmpty()) {
            item {
                Text(
                    text = "书架还是空的\n\n从“导入”选择 TXT 文件，或在搜索中添加示例书籍。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "书架为空，可从导入或搜索添加书籍" },
                )
            }
        }

        items(books, key = { it.id }) { book ->
            BookCard(
                book = book,
                onClick = {
                    context.startActivity(
                        Intent(context, BookDetailActivity::class.java)
                            .putExtra(BookDetailActivity.EXTRA_ID, book.id),
                    )
                },
                onLongClick = { menuBook = book },
            )
        }

        item {
            Text(
                text = "长按书籍可继续阅读、置顶、编辑或删除。本地书架只保存在此设备。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }

    menuBook?.let { book ->
        BookActionsDialog(
            book = book,
            onDismiss = { menuBook = null },
            onContinue = {
                context.startActivity(
                    Intent(context, ReaderActivity::class.java)
                        .putExtra(ReaderActivity.EXTRA_ID, book.id),
                )
                menuBook = null
            },
            onPin = {
                LibraryStore.get(context).moveToTop(book.id)
                menuBook = null
                onLibraryChanged()
            },
            onEdit = {
                context.startActivity(
                    Intent(context, BookDetailActivity::class.java)
                        .putExtra(BookDetailActivity.EXTRA_ID, book.id)
                        .putExtra(BookDetailActivity.EXTRA_EDIT, true),
                )
                menuBook = null
            },
            onDelete = {
                LibraryStore.get(context).remove(book.id)
                menuBook = null
                onLibraryChanged()
            },
        )
    }
}

@Composable
private fun BookActionsDialog(
    book: Book,
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
    onPin: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = book.title,
                modifier = Modifier.semantics { heading() },
            )
        },
        text = {
            Column {
                DialogAction("继续阅读", onContinue)
                DialogAction("置顶到书架首位", onPin)
                DialogAction("编辑书名和作者", onEdit)
                DialogAction("从书架删除", onDelete)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun DialogAction(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = label },
    ) {
        Text(label, modifier = Modifier.fillMaxWidth())
    }
}
