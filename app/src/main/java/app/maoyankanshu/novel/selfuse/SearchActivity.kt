package app.maoyankanshu.novel.selfuse

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.maoyankanshu.novel.selfuse.ui.theme.BiqugeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Compose search / local import / Wikisource.
 * [EXTRA_IMPORT] = `"open_import"` — open SAF picker on launch (AppIntents.importLocal).
 */
class SearchActivity : ComponentActivity() {

    private var intentUriState = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val openImport = intent?.getBooleanExtra(EXTRA_IMPORT, false) ?: false
        val uri = extractUriFromIntent(intent)
        takePersistablePermissionIfGranted(this, intent, uri)
        intentUriState.value = uri

        setContent {
            BiqugeTheme(darkTheme = ReaderPreferences.get(this).nightMode()) {
                SearchScreen(
                    openImportOnStart = openImport,
                    initialUri = intentUriState.value,
                    onClose = { finish() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val uri = extractUriFromIntent(intent)
        takePersistablePermissionIfGranted(this, intent, uri)
        intentUriState.value = uri
    }

    companion object {
        /** Historical extra used by [AppIntents.importLocal]. */
        const val EXTRA_IMPORT: String = "open_import"

        fun takePersistablePermissionIfGranted(context: Context, intent: Intent?, uri: Uri?) {
            if (uri != null && uri.scheme == "content" && intent != null) {
                try {
                    val flags = intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
                    if (flags != 0) {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                } catch (_: Exception) {
                    // Ignored if provider does not grant persistable permissions
                }
            }
        }

        fun extractUriFromIntent(intent: Intent?): Uri? {
            if (intent == null) return null
            val action = intent.action
            if (Intent.ACTION_VIEW == action) {
                return intent.data
            } else if (Intent.ACTION_SEND == action) {
                val streamUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                }
                return streamUri ?: intent.data
            }
            return intent.data
        }
    }
}

private sealed interface SearchListState {
    data class LocalBooks(val books: List<Book>) : SearchListState
    data object WikiLoading : SearchListState
    data class WikiResults(val hits: List<WikisourceClient.Hit>) : SearchListState
    data class Message(val text: String) : SearchListState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchScreen(
    openImportOnStart: Boolean,
    initialUri: Uri?,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var libraryVersion by remember { mutableIntStateOf(0) }
    var listState by remember { mutableStateOf<SearchListState>(SearchListState.LocalBooks(emptyList())) }
    var pendingPicker by remember { mutableStateOf(openImportOnStart) }
    var pendingUri by remember(initialUri) { mutableStateOf(initialUri) }
    var localImporting by remember { mutableStateOf(false) }
    var wikiImportingTitle by remember { mutableStateOf<String?>(null) }

    val isBusy = listState is SearchListState.WikiLoading || localImporting || wikiImportingTitle != null

    val userAgent = stringResource(R.string.http_user_agent)
    val wikiAuthor = stringResource(R.string.search_wikisource_author)
    val localDefault = stringResource(R.string.search_local_default_name)
    val authorEpub = stringResource(R.string.search_local_author_epub)
    val authorTxt = stringResource(R.string.search_local_author_txt)
    val featuredTitle = stringResource(R.string.featured_sanguo_title)
    val featuredEpubUrl = stringResource(R.string.featured_sanguo_epub_url)
    val emptyLocal = stringResource(R.string.search_empty)
    val wikiHeader = stringResource(R.string.search_wikisource_header)
    val wikiNone = stringResource(R.string.search_wikisource_none)
    val wikiFail = stringResource(R.string.search_wikisource_fail)
    val wikiLoading = stringResource(R.string.search_wikisource_loading)
    val localImportingLabel = stringResource(R.string.search_importing_local)
    val searchClearCd = stringResource(R.string.search_clear_cd)

    fun refreshLocal() {
        if (isBusy) return
        val term = query.trim().lowercase()
        scope.launch {
            val books = withContext(Dispatchers.IO) {
                LibraryStore.get(context).books().filter { book ->
                    term.isEmpty() ||
                        book.title.lowercase().contains(term) ||
                        book.author.lowercase().contains(term)
                }
            }
            listState = if (books.isEmpty()) {
                SearchListState.Message(emptyLocal)
            } else {
                SearchListState.LocalBooks(books)
            }
        }
    }

    LaunchedEffect(libraryVersion) { refreshLocal() }

    fun importUri(uri: Uri) {
        localImporting = true
        scope.launch {
            try {
                val imported = withContext(Dispatchers.IO) {
                    val res = LocalBookImport.fromUri(
                        context = context,
                        uri = uri,
                        defaultName = localDefault,
                        authorEpub = authorEpub,
                        authorTxt = authorTxt,
                    )
                    LibraryStore.get(context).add(res.title, res.author, res.text)
                    res
                }
                libraryVersion++
                Toast.makeText(
                    context,
                    context.getString(R.string.search_local_ok, imported.title),
                    Toast.LENGTH_SHORT,
                ).show()
            } catch (e: Exception) {
                Log.e("SearchActivity", "Failed to import local book", e)
                val isOversized = e is IllegalArgumentException && (e.message?.contains("too large") == true || e.message?.contains("32MB") == true)
                val msgRes = if (isOversized) R.string.search_local_file_too_large else R.string.search_local_fail
                Toast.makeText(context, context.getString(msgRes), Toast.LENGTH_SHORT).show()
            } finally {
                localImporting = false
            }
        }
    }

    LaunchedEffect(pendingUri) {
        val uri = pendingUri
        if (uri != null) {
            pendingUri = null
            importUri(uri)
        }
    }

    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importUri(uri)
    }

    fun pickLocalFile() {
        if (isBusy) return
        openDocument.launch(arrayOf("text/plain", "application/epub+zip"))
    }

    LaunchedEffect(pendingPicker) {
        if (pendingPicker) {
            pendingPicker = false
            pickLocalFile()
        }
    }

    fun searchWiki() {
        if (isBusy) return
        val term = query.trim()
        if (term.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.search_query_required), Toast.LENGTH_SHORT).show()
            return
        }
        listState = SearchListState.WikiLoading
        scope.launch {
            try {
                val hits = withContext(Dispatchers.IO) {
                    WikisourceClient.search(term, userAgent)
                }
                listState = if (hits.isEmpty()) {
                    SearchListState.Message("$wikiHeader\n$wikiNone")
                } else {
                    SearchListState.WikiResults(hits)
                }
            } catch (_: Exception) {
                listState = SearchListState.Message(wikiFail)
            }
        }
    }

    fun importWikiPage(pageTitle: String) {
        if (isBusy) return
        wikiImportingTitle = pageTitle
        Toast.makeText(
            context,
            context.getString(R.string.search_importing_page, pageTitle),
            Toast.LENGTH_SHORT,
        ).show()
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val page = WikisourceClient.importPage(pageTitle, userAgent, wikiAuthor)
                    LibraryStore.get(context).add(page.title, page.author, page.text)
                }
                libraryVersion++
                Toast.makeText(context, context.getString(R.string.search_import_ok), Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(context, context.getString(R.string.search_import_fail), Toast.LENGTH_SHORT).show()
            } finally {
                wikiImportingTitle = null
            }
        }
    }

    val backCd = stringResource(R.string.search_back_cd)
    val queryCd = stringResource(R.string.search_query_cd)
    val searchCd = stringResource(R.string.search_action_cd)
    val importLocalCd = stringResource(R.string.search_import_local_cd)
    val importingLocalCd = stringResource(R.string.search_importing_local_cd)
    val wikiCd = stringResource(R.string.search_wikisource_cd)
    val featuredCd = stringResource(R.string.search_featured_cd)
    val epubCd = stringResource(R.string.search_featured_epub_cd)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.search_title),
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onClose,
                        enabled = !isBusy,
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
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    enabled = !isBusy,
                    singleLine = true,
                    label = { Text(stringResource(R.string.search_shelf)) },
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            IconButton(
                                onClick = {
                                    query = ""
                                    refreshLocal()
                                },
                                enabled = !isBusy,
                                modifier = Modifier
                                    .size(48.dp)
                                    .semantics { contentDescription = searchClearCd },
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = null)
                            }
                        }
                    } else null,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = queryCd },
                )
                Button(
                    onClick = { refreshLocal() },
                    enabled = !isBusy,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = searchCd },
                ) {
                    Text(stringResource(R.string.search_action))
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { pickLocalFile() },
                enabled = !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = if (localImporting) importingLocalCd else importLocalCd },
            ) {
                if (localImporting) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(localImportingLabel)
                    }
                } else {
                    Text(stringResource(R.string.search_import_local))
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { searchWiki() },
                enabled = !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = wikiCd },
            ) {
                if (listState is SearchListState.WikiLoading) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(stringResource(R.string.search_wikisource))
                    }
                } else {
                    Text(stringResource(R.string.search_wikisource))
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { importWikiPage(featuredTitle) },
                enabled = !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = featuredCd },
            ) {
                if (wikiImportingTitle == featuredTitle) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(stringResource(R.string.search_featured))
                    }
                } else {
                    Text(stringResource(R.string.search_featured))
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    if (!isBusy) {
                        context.startActivity(
                            AppIntents.remoteImport(context)
                                .putExtra(RemoteImportActivity.EXTRA_TITLE, featuredTitle)
                                .putExtra(RemoteImportActivity.EXTRA_URL, featuredEpubUrl),
                        )
                    }
                },
                enabled = !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = epubCd },
            ) {
                Text(stringResource(R.string.search_featured_epub))
            }
            Spacer(Modifier.height(12.dp))

            when (val state = listState) {
                is SearchListState.WikiLoading -> {
                    Text(
                        text = wikiLoading,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is SearchListState.Message -> {
                    Text(
                        text = state.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is SearchListState.LocalBooks -> {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.books, key = { it.id }) { book ->
                            val cd = stringResource(R.string.search_result_cd, book.title)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clickable(enabled = !isBusy) {
                                        context.startActivity(AppIntents.bookDetail(context, book.id))
                                    }
                                    .semantics { contentDescription = cd },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(book.title, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${book.author} · ${book.progressLabel()}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                is SearchListState.WikiResults -> {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        item {
                            Text(
                                text = wikiHeader,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.semantics { heading() },
                            )
                        }
                        items(state.hits, key = { it.title }) { hit ->
                            val cd = stringResource(R.string.search_wiki_result_cd, hit.title)
                            val isItemImporting = wikiImportingTitle == hit.title
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clickable(enabled = !isBusy) { importWikiPage(hit.title) }
                                    .semantics { contentDescription = cd },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    if (isItemImporting) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp,
                                            )
                                            Text(hit.title, style = MaterialTheme.typography.titleMedium)
                                        }
                                    } else {
                                        Text(hit.title, style = MaterialTheme.typography.titleMedium)
                                    }
                                    if (hit.summary.isNotEmpty()) {
                                        Text(
                                            hit.summary,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
