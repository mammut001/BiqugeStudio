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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.maoyankanshu.novel.selfuse.Book
import app.maoyankanshu.novel.selfuse.BookDetailActivity
import app.maoyankanshu.novel.selfuse.RemoteImportActivity
import app.maoyankanshu.novel.selfuse.SearchActivity
import app.maoyankanshu.novel.selfuse.WebImportActivity
import app.maoyankanshu.novel.selfuse.ui.components.BookCard

@Composable
fun StoreScreen(
    books: List<Book>,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current

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
                    text = "本地书库",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "已收录 ${books.size} 本书 · 所有内容均可离线阅读",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(context, SearchActivity::class.java)
                                .putExtra(SearchActivity.EXTRA_IMPORT, true),
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "导入新的 TXT 或 EPUB 文件" },
                ) {
                    Text("导入新的 TXT 文件")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        context.startActivity(Intent(context, RemoteImportActivity::class.java))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "从我的直链下载 TXT 或 EPUB" },
                ) {
                    Text("从我的直链下载 TXT / EPUB")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        context.startActivity(Intent(context, WebImportActivity::class.java))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "从授权网页导入文章" },
                ) {
                    Text("从授权网页导入文章")
                }
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
            )
        }
    }
}
