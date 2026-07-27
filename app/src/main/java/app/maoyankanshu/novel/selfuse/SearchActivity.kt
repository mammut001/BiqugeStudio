package app.maoyankanshu.novel.selfuse

import android.os.Bundle
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val openImport = intent.getBooleanExtra(EXTRA_IMPORT, false)
        setContent {
            BiqugeTheme(darkTheme = ReaderPreferences.get(this).nightMode()) {
                SearchScreen(
                    openImportOnStart = openImport,
                    onClose = { finish() },
                )
            }
        }
    }

    companion object {
        /** Historical extra used by [AppIntents.importLocal]. */
        const val EXTRA_IMPORT: String = "open_import"
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
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var libraryVersion by remember { mutableIntStateOf(0) }
    var listState by remember { mutableStateOf<SearchListState>(SearchListState.LocalBooks(emptyList())) }
    var pendingPicker by remember { mutableStateOf(openImportOnStart) }

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

    fun refreshLocal() {
        val term = query.trim().lowercase()
        val books = LibraryStore.get(context).books().filter { book ->
            term.isEmpty() ||
                book.title.lowercase().contains(term) ||
                book.author.lowercase().contains(term)
        }
        listState = if (books.isEmpty()) {
            SearchListState.Message(emptyLocal)
        } else {
            SearchListState.LocalBooks(books)
        }
    }

    LaunchedEffect(libraryVersion) { refreshLocal() }

    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val imported = LocalBookImport.fromUri(
                context = context,
                uri = uri,
                defaultName = localDefault,
                authorEpub = authorEpub,
                authorTxt = authorTxt,
            )
            LibraryStore.get(context).add(imported.title, imported.author, imported.text)
            libraryVersion++
            Toast.makeText(
                context,
                context.getString(R.string.search_local_ok, imported.title),
                Toast.LENGTH_SHORT,
            ).show()
        } catch (_: Exception) {
            Toast.makeText(context, context.getString(R.string.search_local_fail), Toast.LENGTH_SHORT).show()
        }
    }

    fun pickLocalFile() {
        openDocument.launch(arrayOf("text/plain", "application/epub+zip"))
    }

    LaunchedEffect(pendingPicker) {
        if (pendingPicker) {
            pendingPicker = false
            pickLocalFile()
        }
    }

    fun searchWiki() {
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
        Toast.makeText(
            context,
            context.getString(R.string.search_importing_page, pageTitle),
            Toast.LENGTH_SHORT,
        ).show()
        scope.launch {
            try {
                val page = withContext(Dispatchers.IO) {
                    WikisourceClient.importPage(pageTitle, userAgent, wikiAuthor)
                }
                LibraryStore.get(context).add(page.title, page.author, page.text)
                libraryVersion++
                Toast.makeText(context, context.getString(R.string.search_import_ok), Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(context, context.getString(R.string.search_import_fail), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val backCd = stringResource(R.string.search_back_cd)
    val queryCd = stringResource(R.string.search_query_cd)
    val searchCd = stringResource(R.string.search_action_cd)
    val importLocalCd = stringResource(R.string.search_import_local_cd)
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
                    singleLine = true,
                    label = { Text(stringResource(R.string.search_shelf)) },
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = queryCd },
                )
                Button(
                    onClick = {
                        // Local shelf filter; if last mode was wiki, still re-filter local.
                        refreshLocal()
                    },
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
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = importLocalCd },
            ) {
                Text(stringResource(R.string.search_import_local))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { searchWiki() },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = wikiCd },
            ) {
                Text(stringResource(R.string.search_wikisource))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { importWikiPage(featuredTitle) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = featuredCd },
            ) {
                Text(stringResource(R.string.search_featured))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        AppIntents.remoteImport(context)
                            .putExtra(RemoteImportActivity.EXTRA_TITLE, featuredTitle)
                            .putExtra(RemoteImportActivity.EXTRA_URL, featuredEpubUrl),
                    )
                },
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
                                    .clickable {
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
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clickable { importWikiPage(hit.title) }
                                    .semantics { contentDescription = cd },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(hit.title, style = MaterialTheme.typography.titleMedium)
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
