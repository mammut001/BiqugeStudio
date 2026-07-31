package app.maoyankanshu.novel.selfuse

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.maoyankanshu.novel.selfuse.ui.reader.ChapterIndex
import app.maoyankanshu.novel.selfuse.ui.theme.BiqugeTheme

/**
 * Compose book detail. Intent extras unchanged:
 * [EXTRA_ID] = `"book_id"`, [EXTRA_EDIT] = `"edit_book"`.
 */
class BookDetailActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val bookId = intent.getStringExtra(EXTRA_ID)
        val openEdit = intent.getBooleanExtra(EXTRA_EDIT, false)
        val initial = bookId?.let { LibraryStore.get(this).byId(it) }
        if (initial == null) {
            finish()
            return
        }
        setContent {
            BiqugeTheme(darkTheme = ReaderPreferences.get(this).nightMode()) {
                BookDetailScreen(
                    initialBook = initial,
                    openEditOnStart = openEdit,
                    onClose = { finish() },
                )
            }
        }
    }

    companion object {
        const val EXTRA_ID: String = "book_id"
        const val EXTRA_EDIT: String = "edit_book"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookDetailScreen(
    initialBook: Book,
    openEditOnStart: Boolean,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var book by remember { mutableStateOf(initialBook) }
    var showEdit by remember { mutableStateOf(openEditOnStart) }
    var showDelete by remember { mutableStateOf(false) }

    val chapters = remember(book.id, book.text) {
        ChapterIndex.findChapters(book.text).size.coerceAtLeast(1)
    }
    val preview = remember(book.text) {
        val flat = book.text.trim()
        if (flat.length > 180) flat.take(180) + "…" else flat
    }
    val readLabel = if (book.position > 0) {
        stringResource(R.string.detail_continue_reading)
    } else {
        stringResource(R.string.detail_start_reading)
    }
    val backCd = stringResource(R.string.detail_back_cd)
    val readCd = stringResource(R.string.detail_read_cd)
    val editCd = stringResource(R.string.detail_edit_cd)
    val exportCd = stringResource(R.string.detail_export_cd)
    val deleteCd = stringResource(R.string.detail_delete_cd)
    val previewCd = stringResource(R.string.detail_preview_cd)
    val meta = stringResource(
        R.string.detail_meta,
        chapters,
        book.text.length,
        book.progressLabel(),
    )

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                LibraryStore.get(context).exportBook(book.id, stream)
            }
            Toast.makeText(context, context.getString(R.string.detail_export_ok), Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(context, context.getString(R.string.detail_export_fail), Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
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
                            .heightIn(min = 48.dp)
                            .semantics { contentDescription = backCd },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = book.author,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = meta,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = preview,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { contentDescription = previewCd },
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { context.startActivity(AppIntents.reader(context, book.id)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = readCd },
            ) {
                Text(readLabel)
            }
            OutlinedButton(
                onClick = { showEdit = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = editCd },
            ) {
                Text(stringResource(R.string.detail_edit))
            }
            OutlinedButton(
                onClick = {
                    val safe = book.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    exportLauncher.launch("$safe.txt")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = exportCd },
            ) {
                Text(stringResource(R.string.detail_export))
            }
            OutlinedButton(
                onClick = { showDelete = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = deleteCd },
            ) {
                Text(
                    text = stringResource(R.string.detail_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showEdit) {
        var title by remember(book.id) { mutableStateOf(book.title) }
        var author by remember(book.id) { mutableStateOf(book.author) }
        val editTitleText = stringResource(R.string.detail_edit_title)
        val titleHintText = stringResource(R.string.detail_title_hint)
        val authorHintText = stringResource(R.string.detail_author_hint)
        val titleRequiredText = stringResource(R.string.detail_title_required)
        val authorRequiredText = stringResource(R.string.detail_author_required)
        val saveText = stringResource(R.string.detail_save)
        val cancelText = stringResource(R.string.detail_cancel)

        val cleanTitle = title.trim()
        val cleanAuthor = author.trim()

        AlertDialog(
            onDismissRequest = { showEdit = false },
            title = {
                Text(
                    text = editTitleText,
                    modifier = Modifier.semantics { heading() },
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        singleLine = true,
                        label = { Text(titleHintText) },
                        isError = cleanTitle.isEmpty(),
                        supportingText = if (cleanTitle.isEmpty()) {
                            { Text(titleRequiredText, color = MaterialTheme.colorScheme.error) }
                        } else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = titleHintText },
                    )
                    OutlinedTextField(
                        value = author,
                        onValueChange = { author = it },
                        singleLine = true,
                        label = { Text(authorHintText) },
                        isError = cleanAuthor.isEmpty(),
                        supportingText = if (cleanAuthor.isEmpty()) {
                            { Text(authorRequiredText, color = MaterialTheme.colorScheme.error) }
                        } else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = authorHintText },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        LibraryStore.get(context).updateMetadata(book.id, cleanTitle, cleanAuthor)
                        book = LibraryStore.get(context).byId(book.id) ?: book
                        showEdit = false
                    },
                    enabled = cleanTitle.isNotEmpty() && cleanAuthor.isNotEmpty(),
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics { contentDescription = saveText },
                ) {
                    Text(saveText)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showEdit = false },
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics { contentDescription = cancelText },
                ) {
                    Text(cancelText)
                }
            },
        )
    }

    if (showDelete) {
        val deleteTitleText = stringResource(R.string.detail_delete_title)
        val deleteText = stringResource(R.string.detail_delete)
        val cancelText = stringResource(R.string.detail_cancel)

        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = {
                Text(
                    text = deleteTitleText,
                    modifier = Modifier.semantics { heading() },
                )
            },
            text = {
                Text(stringResource(R.string.detail_delete_body, book.title))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        LibraryStore.get(context).remove(book.id)
                        showDelete = false
                        onClose()
                    },
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics { contentDescription = deleteText },
                ) {
                    Text(deleteText)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDelete = false },
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics { contentDescription = cancelText },
                ) {
                    Text(cancelText)
                }
            },
        )
    }
}
