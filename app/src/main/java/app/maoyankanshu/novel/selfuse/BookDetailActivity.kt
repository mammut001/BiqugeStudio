package app.maoyankanshu.novel.selfuse

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.maoyankanshu.novel.selfuse.ui.reader.ChapterIndex
import app.maoyankanshu.novel.selfuse.ui.theme.BiqugeTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Compose book detail. Intent extras unchanged:
 * [EXTRA_ID] = `"book_id"`, [EXTRA_EDIT] = `"edit_book"`.
 *
 * Single-book TXT export (CreateDocument) runs on [rememberCoroutineScope] as a tracked [Job]
 * on [Dispatchers.IO]. User cancel or leaving composition cancels that Job;
 * [CancellationException] is rethrown and **not** shown as failure Toast. Host Activity already
 * `finishing`/`destroyed` skips Toast / state ([canAcceptUi]). Export still uses SAF streams only.
 */
class BookDetailActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val bookId = intent.getStringExtra(EXTRA_ID)
        val openEdit = intent.getBooleanExtra(EXTRA_EDIT, false)
        if (bookId.isNullOrEmpty()) {
            finish()
            return
        }
        setContent {
            BiqugeTheme(darkTheme = ReaderPreferences.get(this).nightMode()) {
                BookDetailRoute(
                    bookId = bookId,
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

/**
 * Load the requested multi-MB body away from Activity.onCreate/main before composing the detail.
 * Detail needs the full body for preview/chapter count, but it does not need to block first frame.
 */
@Composable
private fun BookDetailRoute(
    bookId: String,
    openEditOnStart: Boolean,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var initialBook by remember(bookId) { mutableStateOf<Book?>(null) }
    var loadFinished by remember(bookId) { mutableStateOf(false) }

    LaunchedEffect(bookId) {
        initialBook = withContext(Dispatchers.IO) {
            LibraryStore.getForReading(context).byId(bookId)
        }
        loadFinished = true
    }

    val loaded = initialBook
    if (loaded == null) {
        if (loadFinished) {
            LaunchedEffect(bookId, loadFinished) { onClose() }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.semantics {
                        contentDescription = context.getString(R.string.reader_open_loading)
                        liveRegion = LiveRegionMode.Polite
                    },
                )
            }
        }
        return
    }

    BookDetailScreen(
        initialBook = loaded,
        openEditOnStart = openEditOnStart,
        onClose = onClose,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookDetailScreen(
    initialBook: Book,
    openEditOnStart: Boolean,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    // Cancelled when BookDetailScreen leaves composition (finish / process path).
    val scope = rememberCoroutineScope()
    var book by remember { mutableStateOf(initialBook) }
    var showEdit by remember { mutableStateOf(openEditOnStart) }
    var showDelete by remember { mutableStateOf(false) }

    var isExporting by remember { mutableStateOf(false) }
    var exportJob by remember { mutableStateOf<Job?>(null) }

    // Chapter scanning is linear over the full book. Keep it off Compose/main for large TXT.
    var chapters by remember(book.id) { mutableIntStateOf(1) }
    LaunchedEffect(book.id, book.text) {
        chapters = withContext(Dispatchers.Default) {
            ChapterIndex.findChapters(book.text).size.coerceAtLeast(1)
        }
    }
    // Preview only needs a tiny prefix; avoid trim() over the entire multi-MB body.
    val preview = remember(book.text) {
        detailPreview(book.text)
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
    val exportOk = stringResource(R.string.detail_export_ok)
    val exportFail = stringResource(R.string.detail_export_fail)
    val exportInProgress = stringResource(R.string.detail_export_in_progress)
    val exportInProgressCd = stringResource(R.string.detail_export_in_progress_cd)
    val cancelExportLabel = stringResource(R.string.detail_cancel_export)
    val cancelExportCd = stringResource(R.string.detail_cancel_export_cd)
    val meta = stringResource(
        R.string.detail_meta,
        chapters,
        book.text.length,
        book.progressLabel(),
    )

    fun clearExportBusy() {
        isExporting = false
        exportJob = null
    }

    fun cancelExportWork() {
        exportJob?.cancel()
        clearExportBusy()
        // Soft cancel: no fail Toast (CancellationException path is silent too).
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (isExporting) return@rememberLauncherForActivityResult
        val bookId = book.id
        isExporting = true
        exportJob = scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        LibraryStore.get(context).exportBook(bookId, stream)
                    } ?: throw IllegalStateException("openOutputStream returned null")
                }
                ensureActive()
                if (!activity.canAcceptUi()) return@launch
                when (
                    BookDetailExportOutcomes.exportNotice(
                        cancelled = false,
                        hardError = false,
                    )
                ) {
                    BookDetailExportOutcomes.ExportNotice.SUCCESS -> {
                        clearExportBusy()
                        Toast.makeText(context, exportOk, Toast.LENGTH_SHORT).show()
                    }
                    BookDetailExportOutcomes.ExportNotice.NONE,
                    BookDetailExportOutcomes.ExportNotice.FAIL,
                    -> Unit
                }
            } catch (cancel: CancellationException) {
                // User cancel / leave composition: never export_fail Toast.
                if (activity.canAcceptUi()) {
                    clearExportBusy()
                }
                throw cancel
            } catch (e: Exception) {
                if (!activity.canAcceptUi()) return@launch
                if (!BookDetailExportOutcomes.shouldSurfaceAsFailure(e)) throw e
                Log.e("YueJianDetail", "Unable to export book TXT", e)
                clearExportBusy()
                Toast.makeText(context, exportFail, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                    enabled = !isExporting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = readCd },
                ) {
                    Text(readLabel)
                }
                OutlinedButton(
                    onClick = { showEdit = true },
                    enabled = !isExporting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = editCd },
                ) {
                    Text(stringResource(R.string.detail_edit))
                }
                OutlinedButton(
                    onClick = {
                        if (isExporting) return@OutlinedButton
                        val safe = book.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                        exportLauncher.launch("$safe.txt")
                    },
                    enabled = !isExporting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = exportCd },
                ) {
                    Text(stringResource(R.string.detail_export))
                }
                OutlinedButton(
                    onClick = { showDelete = true },
                    enabled = !isExporting,
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

        if (isExporting) {
            // Back / outside tap soft-cancels the Job (no fail Toast).
            Dialog(onDismissRequest = { cancelExportWork() }) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    modifier = Modifier.semantics {
                        contentDescription = exportInProgressCd
                        liveRegion = LiveRegionMode.Polite
                    },
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                strokeWidth = 3.dp,
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = exportInProgress,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.semantics {
                                    contentDescription = exportInProgressCd
                                    liveRegion = LiveRegionMode.Polite
                                },
                            )
                        }
                        OutlinedButton(
                            onClick = { cancelExportWork() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .semantics { contentDescription = cancelExportCd },
                        ) {
                            Text(cancelExportLabel)
                        }
                    }
                }
            }
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
                        showEdit = false
                        val bookId = book.id
                        scope.launch {
                            val updated = withContext(Dispatchers.IO) {
                                val store = LibraryStore.get(context)
                                store.updateMetadata(bookId, cleanTitle, cleanAuthor)
                                store.byId(bookId)
                            }
                            if (updated != null && activity.canAcceptUi()) {
                                book = updated
                            }
                        }
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
                        showDelete = false
                        val bookId = book.id
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                LibraryStore.get(context).remove(bookId)
                                ReadingHistory.get(context).remove(bookId)
                            }
                            if (activity.canAcceptUi()) onClose()
                        }
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

/** Small bounded detail preview; never scans/copies the whole body just to trim it. */
private fun detailPreview(text: String): String {
    if (text.isEmpty()) return ""
    var start = 0
    while (start < text.length && text[start].isWhitespace()) start++
    if (start >= text.length) return ""
    val end = (start + 180).coerceAtMost(text.length)
    val snippet = text.substring(start, end).trimEnd()
    return if (end < text.length) "$snippet…" else snippet
}
