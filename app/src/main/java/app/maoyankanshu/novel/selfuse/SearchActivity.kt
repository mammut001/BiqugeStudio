package app.maoyankanshu.novel.selfuse

import android.app.Activity
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.maoyankanshu.novel.selfuse.ui.screens.PrivacyConsentGate
import app.maoyankanshu.novel.selfuse.ui.theme.BiqugeTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Compose search / local import / Wikisource.
 *
 * Manifest: [Intent.ACTION_VIEW] (`content://` / `file://` TXT·EPUB),
 * [Intent.ACTION_SEND] and [Intent.ACTION_SEND_MULTIPLE] ([Intent.EXTRA_STREAM]
 * single Uri or ArrayList, capped at [ImportIntentUris.MAX_URIS]).
 * [EXTRA_IMPORT] = `"open_import"` — open SAF picker on launch ([AppIntents.importLocal]).
 * [onNewIntent] handles further shares while [singleTop].
 *
 * Local shelf filter, SAF/share import, and HTTPS Wikisource work run on
 * [rememberCoroutineScope] as tracked [Job]s. User cancel, back, or leaving
 * composition cancel those Jobs; [CancellationException] is rethrown and **not**
 * shown as search/import failure (no fail Toast / error liveRegion).
 * Activity already `finishing`/`destroyed` skips Toast / state ([canAcceptUi]).
 */
class SearchActivity : ComponentActivity() {

    private var intentUrisState = mutableStateOf<List<Uri>>(emptyList())
    private var privacyAccepted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val consentStore = PrivacyConsentStore.get(this)
        privacyAccepted = consentStore.hasAcceptedCurrentPolicy()
        val openImport = intent?.getBooleanExtra(EXTRA_IMPORT, false) ?: false
        if (privacyAccepted) applyImportIntent(intent)

        setContent {
            var accepted by remember { mutableStateOf(privacyAccepted) }
            // Observe share / open-with URIs so SEND and onNewIntent recompose SearchScreen.
            val sharedUris by intentUrisState
            BiqugeTheme(
                darkTheme = if (accepted) ReaderPreferences.get(this).nightMode() else false,
            ) {
                if (accepted) {
                    SearchScreen(
                        openImportOnStart = openImport,
                        initialUris = sharedUris,
                        onClose = { finish() },
                    )
                } else {
                    PrivacyConsentGate(
                        onAccept = {
                            consentStore.acceptCurrentPolicy()
                            privacyAccepted = true
                            applyImportIntent(intent)
                            accepted = true
                        },
                        onDecline = { finish() },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (privacyAccepted) applyImportIntent(intent)
    }

    private fun applyImportIntent(intent: Intent?) {
        val uris = extractUrisFromIntent(intent)
        ImportIntentUris.takeReadPermissionsIfPossible(this, intent, uris)
        intentUrisState.value = uris
    }

    companion object {
        /** Historical extra used by [AppIntents.importLocal] / [ImportIntentUris.EXTRA_IMPORT]. */
        const val EXTRA_IMPORT: String = ImportIntentUris.EXTRA_IMPORT

        fun extractUriFromIntent(intent: Intent?): Uri? = ImportIntentUris.extractUri(intent)

        fun extractUrisFromIntent(intent: Intent?): List<Uri> = ImportIntentUris.extractUris(intent)
    }
}

private sealed interface SearchListState {
    data object LocalLoading : SearchListState
    data class LocalBooks(val books: List<Book>) : SearchListState
    data object WikiLoading : SearchListState
    data class WikiResults(val hits: List<WikisourceClient.Hit>) : SearchListState
    data class Message(val text: String) : SearchListState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchScreen(
    openImportOnStart: Boolean,
    initialUris: List<Uri>,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val keyboardController = LocalSoftwareKeyboardController.current
    // Cancelled when SearchScreen leaves composition (back / finish / process death path).
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val preferences = remember { ReaderPreferences.get(context) }
    var searchHistory: List<String> by remember {
        mutableStateOf(preferences.searchHistory().toList())
    }
    fun recordHistory(raw: String) {
        preferences.pushSearchHistory(raw)
        searchHistory = preferences.searchHistory().toList()
    }
    var libraryVersion by remember { mutableIntStateOf(0) }
    var searchToken by remember { mutableIntStateOf(0) }
    var listState by remember { mutableStateOf<SearchListState>(SearchListState.LocalBooks(emptyList())) }
    var localImporting by remember { mutableStateOf(false) }
    // Batch progress for multi-file import ("3 / 20"); 0/0 means single/unknown.
    var localImportDone by remember { mutableIntStateOf(0) }
    var localImportTotal by remember { mutableIntStateOf(0) }
    var failedLocalUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var wikiImportingTitle by remember { mutableStateOf<String?>(null) }
    // Errors are tracked per task so a wiki failure never lingers over local search UI (and vice versa).
    var localImportError by remember { mutableStateOf<String?>(null) }
    var wikiSearchError by remember { mutableStateOf<String?>(null) }
    var wikiImportError by remember { mutableStateOf<String?>(null) }
    var failedWikiTitle by remember { mutableStateOf<String?>(null) }
    // One-shot flag: LaunchedEffect(pendingPicker) re-ran when flipping true→false and
    // could open the SAF document picker twice on EXTRA_IMPORT startup.
    var autoOpenPickerDone by remember { mutableStateOf(false) }
    var localImportJob by remember { mutableStateOf<Job?>(null) }
    var wikiSearchJob by remember { mutableStateOf<Job?>(null) }
    var wikiImportJob by remember { mutableStateOf<Job?>(null) }
    var localSearchJob by remember { mutableStateOf<Job?>(null) }
    var localImportCancellationSignal by remember { mutableStateOf<StreamCancellationSignal?>(null) }
    var wikiSearchCancellationSignal by remember { mutableStateOf<RemoteImportCancellationSignal?>(null) }
    var wikiImportCancellationSignal by remember { mutableStateOf<RemoteImportCancellationSignal?>(null) }
    val localImportSessions = remember { ImportSessionTracker() }
    val wikiSearchSessions = remember { ImportSessionTracker() }
    val wikiImportSessions = remember { ImportSessionTracker() }

    val isBusy = listState is SearchListState.WikiLoading ||
        listState is SearchListState.LocalLoading ||
        localImporting ||
        wikiImportingTitle != null

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
    val wikiLoading = stringResource(R.string.search_wikisource_loading)
    val localLoadingLabel = stringResource(R.string.search_local_loading)
    val localImportingLabel = stringResource(R.string.search_importing_local)
    val searchClearCd = stringResource(R.string.search_clear_cd)
    val cancelLocalLabel = stringResource(R.string.search_cancel_local)
    val cancelLocalCd = stringResource(R.string.search_cancel_local_cd)
    val cancelWikiSearchLabel = stringResource(R.string.search_cancel_wiki_search)
    val cancelWikiSearchCd = stringResource(R.string.search_cancel_wiki_search_cd)
    val cancelWikiImportLabel = stringResource(R.string.search_cancel_wiki_import)
    val cancelWikiImportCd = stringResource(R.string.search_cancel_wiki_import_cd)

    fun clearBusyFlagsKeepingList() {
        localImporting = false
        wikiImportingTitle = null
        localImportJob = null
        wikiSearchJob = null
        wikiImportJob = null
        localSearchJob = null
        if (listState is SearchListState.WikiLoading || listState is SearchListState.LocalLoading) {
            // Drop loading sentinel so UI is not stuck busy after soft cancel.
            listState = SearchListState.LocalBooks(emptyList())
        }
    }

    fun refreshLocal(recordTerm: Boolean = true) {
        if (localImporting || wikiImportingTitle != null || listState is SearchListState.WikiLoading) return
        localImportError = null
        if (recordTerm && query.isNotBlank()) recordHistory(query)
        localSearchJob?.cancel()
        val currentToken = ++searchToken
        val term = query.trim().lowercase()
        listState = SearchListState.LocalLoading
        localSearchJob = scope.launch {
            try {
                val books = withContext(Dispatchers.IO) {
                    // Title/author filter only — no need to decode multi‑MB bodies.
                    LibraryStore.getForListing(context).booksForListing().filter { book ->
                        term.isEmpty() ||
                            book.title.lowercase().contains(term) ||
                            book.author.lowercase().contains(term)
                    }
                }
                if (!activity.canAcceptUi()) return@launch
                if (currentToken == searchToken) {
                    listState = if (books.isEmpty()) {
                        SearchListState.Message(emptyLocal)
                    } else {
                        SearchListState.LocalBooks(books)
                    }
                }
            } catch (cancel: CancellationException) {
                // Superseded search or leave: never treat as failure UI.
                if (activity.canAcceptUi() && currentToken == searchToken) {
                    if (listState is SearchListState.LocalLoading) {
                        listState = SearchListState.LocalBooks(emptyList())
                    }
                    localSearchJob = null
                }
                throw cancel
            }
        }
    }

    fun cancelActiveWork(leave: Boolean) {
        val abortedListLoad =
            listState is SearchListState.WikiLoading || listState is SearchListState.LocalLoading
        val abortedLocalImport = localImportJob != null
        localImportJob?.cancel()
        wikiSearchJob?.cancel()
        wikiImportJob?.cancel()
        localSearchJob?.cancel()
        localImportCancellationSignal?.cancel()
        wikiSearchCancellationSignal?.cancel()
        wikiImportCancellationSignal?.cancel()
        localImportCancellationSignal = null
        wikiSearchCancellationSignal = null
        wikiImportCancellationSignal = null
        localImportSessions.invalidate()
        wikiSearchSessions.invalidate()
        wikiImportSessions.invalidate()
        clearBusyFlagsKeepingList()
        if (leave) {
            onClose()
            return
        }
        // Soft cancel of an in-flight list load: restore local shelf.
        // Soft cancel of local/wiki *import* keeps the current list (e.g. WikiResults).
        if (abortedListLoad && activity.canAcceptUi()) {
            localImportError = null
            refreshLocal(recordTerm = false)
        } else if (abortedLocalImport && activity.canAcceptUi()) {
            // A cancelled batch can already have committed earlier files. Trigger a fresh
            // listing without relying on the cancelled coroutine's late catch/finally.
            libraryVersion++
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            wikiSearchCancellationSignal?.cancel()
            wikiImportCancellationSignal?.cancel()
            localImportCancellationSignal?.cancel()
            localImportSessions.invalidate()
            wikiSearchSessions.invalidate()
            wikiImportSessions.invalidate()
            localImportJob?.cancel()
            wikiSearchJob?.cancel()
            wikiImportJob?.cancel()
            localSearchJob?.cancel()
        }
    }

    LaunchedEffect(libraryVersion) { refreshLocal(recordTerm = false) }

    fun importUris(uris: List<Uri>, isRetry: Boolean = false) {
        if (uris.isEmpty()) return
        if (localImporting || wikiImportingTitle != null || listState is SearchListState.WikiLoading) return
        localImporting = true
        localImportError = null
        localImportDone = 0
        localImportTotal = uris.size
        if (!isRetry) failedLocalUris = emptyList()
        val sessionToken = localImportSessions.start()
        val signal = StreamCancellationSignal()
        localImportCancellationSignal = signal
        localImportJob = scope.launch {
            var added = 0
            var existing = 0
            var fail = 0
            var lastTitle: String? = null
            var lastBookId: String? = null
            var lastErrorOversized = false
            val failed = ArrayList<Uri>()
            try {
                for (uri in uris) {
                    ensureActive()
                    try {
                        val (imported, addResult) = withContext(Dispatchers.IO) {
                            val res = LocalBookImport.fromUri(
                                context = context,
                                uri = uri,
                                defaultName = localDefault,
                                authorEpub = authorEpub,
                                authorTxt = authorTxt,
                                cancellationSignal = signal,
                            )
                            // The decoder is blocking but stream-cancellable. Do not enter the
                            // non-suspending library write after cancellation won the race.
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            val result = LibraryStore.get(context).addOrGetExisting(
                                res.title,
                                res.author,
                                res.text,
                                res.coverBytes,
                                true,
                            )
                            res to result
                        }
                        if (addResult.added) added++ else existing++
                        lastTitle = imported.title
                        lastBookId = addResult.id
                        localImportDone = added + existing + fail
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (e: Exception) {
                        // A cancelled SAF wrapper reports InterruptedIOException from the
                        // blocking read; restore coroutine cancellation before counting/logging
                        // it as a corrupt file.
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        if (!SearchWorkOutcomes.shouldSurfaceAsFailure(e)) throw e
                        Log.e("SearchActivity", "Failed to import local book", e)
                        fail++
                        failed.add(uri)
                        localImportDone = added + existing + fail
                        lastErrorOversized = lastErrorOversized || SearchWorkOutcomes.isOversizedImportError(e)
                    }
                }
                if (!activity.canAcceptUi() || !localImportSessions.owns(sessionToken)) return@launch
                if (added > 0) libraryVersion++
                failedLocalUris = failed.toList()
                when (val notice = SearchWorkOutcomes.localBatchNotice(added, existing, fail, cancelled = false)) {
                    SearchWorkOutcomes.LocalBatchNotice.SINGLE_OK,
                    SearchWorkOutcomes.LocalBatchNotice.SINGLE_EXISTING,
                    -> {
                        val bookId = lastBookId
                        if (SearchWorkOutcomes.opensDetailConfirmation(notice) && !bookId.isNullOrEmpty()) {
                            // Same confirmation as browser / 直链 / wiki: new vs already-on-shelf.
                            context.startActivity(
                                AppIntents.bookDetailJustImported(
                                    context,
                                    bookId,
                                    SearchWorkOutcomes.confirmationJustAdded(notice),
                                ),
                            )
                        } else if (notice == SearchWorkOutcomes.LocalBatchNotice.SINGLE_OK) {
                            val msg = if (initialUris.isNotEmpty()) {
                                context.getString(R.string.search_share_import_ok, added)
                            } else {
                                context.getString(R.string.search_local_ok, lastTitle)
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(
                                context,
                                context.getString(R.string.search_local_existing, lastTitle),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                    SearchWorkOutcomes.LocalBatchNotice.MULTI_OK -> {
                        val msg = if (initialUris.isNotEmpty()) {
                            context.getString(R.string.search_share_import_ok, added)
                        } else {
                            context.getString(R.string.search_local_ok_multi, added)
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                    SearchWorkOutcomes.LocalBatchNotice.MULTI_WITH_EXISTING -> {
                        Toast.makeText(
                            context,
                            context.getString(R.string.search_local_ok_with_existing, added, existing),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    SearchWorkOutcomes.LocalBatchNotice.ALL_EXISTING -> {
                        Toast.makeText(
                            context,
                            context.getString(R.string.search_local_existing_multi, existing),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    SearchWorkOutcomes.LocalBatchNotice.PARTIAL -> {
                        Toast.makeText(
                            context,
                            context.getString(
                                R.string.search_local_result_partial,
                                added,
                                existing,
                                fail,
                            ),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    SearchWorkOutcomes.LocalBatchNotice.ALL_FAIL -> {
                        val msgRes = if (lastErrorOversized) {
                            R.string.search_local_file_too_large
                        } else {
                            R.string.search_local_fail
                        }
                        localImportError = context.getString(msgRes)
                    }
                    SearchWorkOutcomes.LocalBatchNotice.NONE -> Unit
                }
            } catch (cancel: CancellationException) {
                // User cancel / back / leave: no fail Toast; keep books already added.
                // Aborted run offers no retry (partial uris were never attempted).
                if (activity.canAcceptUi() && localImportSessions.owns(sessionToken)) {
                    if (added > 0) libraryVersion++
                    failedLocalUris = emptyList()
                    localImporting = false
                    localImportDone = 0
                    localImportTotal = 0
                    localImportJob = null
                    localImportCancellationSignal = null
                }
                throw cancel
            } catch (e: Exception) {
                if (!activity.canAcceptUi() || !localImportSessions.owns(sessionToken)) return@launch
                Log.e("SearchActivity", "Failed local import batch", e)
                failedLocalUris = uris.toList()
                localImportError = context.getString(R.string.search_local_fail)
            } finally {
                if (activity.canAcceptUi() && localImportSessions.owns(sessionToken)) {
                    localImporting = false
                    localImportDone = 0
                    localImportTotal = 0
                    localImportJob = null
                    localImportCancellationSignal = null
                }
            }
        }
    }

    fun importUri(uri: Uri) = importUris(listOf(uri))

    // VIEW / SEND / SEND_MULTIPLE (and onNewIntent via intentUrisState recomposition).
    // Key on list contents so an identical recompose does not re-import the same batch.
    LaunchedEffect(initialUris) {
        if (initialUris.isNotEmpty()) {
            importUris(initialUris)
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
        openDocument.launch(LocalBookImport.OPEN_DOCUMENT_MIME_TYPES)
    }

    // Open SAF at most once when started with EXTRA_IMPORT (AppIntents.importLocal).
    // Skip if the intent already carries VIEW/SEND URIs (multi-import path).
    LaunchedEffect(Unit) {
        if (openImportOnStart && !autoOpenPickerDone && initialUris.isEmpty()) {
            autoOpenPickerDone = true
            openDocument.launch(LocalBookImport.OPEN_DOCUMENT_MIME_TYPES)
        }
    }

    fun searchWiki() {
        if (isBusy) return
        wikiSearchError = null
        val term = query.trim()
        if (term.isEmpty()) {
            wikiSearchError = context.getString(R.string.search_query_required)
            return
        }
        recordHistory(term)
        listState = SearchListState.WikiLoading
        val sessionToken = wikiSearchSessions.start()
        val signal = RemoteImportCancellationSignal()
        wikiSearchCancellationSignal = signal
        wikiSearchJob = scope.launch {
            try {
                val hits = withContext(Dispatchers.IO) {
                    WikisourceClient.search(term, userAgent, signal)
                }
                ensureActive()
                if (!activity.canAcceptUi() || !wikiSearchSessions.owns(sessionToken)) return@launch
                listState = if (hits.isEmpty()) {
                    SearchListState.Message("$wikiHeader\n$wikiNone")
                } else {
                    SearchListState.WikiResults(hits)
                }
                wikiSearchJob = null
                wikiSearchCancellationSignal = null
            } catch (cancel: CancellationException) {
                // Cancel / back / leave: never failure Toast or error liveRegion.
                if (activity.canAcceptUi() && wikiSearchSessions.owns(sessionToken)) {
                    if (listState is SearchListState.WikiLoading) {
                        listState = SearchListState.LocalBooks(emptyList())
                    }
                    wikiSearchJob = null
                    wikiSearchCancellationSignal = null
                }
                throw cancel
            } catch (e: Exception) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (!activity.canAcceptUi() || !wikiSearchSessions.owns(sessionToken)) return@launch
                Log.e("SearchActivity", "Failed to search Wikisource", e)
                // 与导入一致：429/5xx/网络中断各有说法，其余回退通用搜索失败。
                val searchFailure = context.downloadImportFailureMessage(e, R.string.search_wikisource_fail)
                wikiSearchError = searchFailure
                listState = SearchListState.Message(searchFailure)
                wikiSearchJob = null
                wikiSearchCancellationSignal = null
            }
        }
    }

    fun importWikiPage(pageTitle: String) {
        if (isBusy) return
        wikiImportingTitle = pageTitle
        wikiImportError = null
        failedWikiTitle = null
        val sessionToken = wikiImportSessions.start()
        val signal = RemoteImportCancellationSignal()
        wikiImportCancellationSignal = signal
        if (activity.canAcceptUi()) {
            Toast.makeText(
                context,
                context.getString(R.string.search_importing_page, pageTitle),
                Toast.LENGTH_SHORT,
            ).show()
        }
        wikiImportJob = scope.launch {
            try {
                val addResult = withContext(Dispatchers.IO) {
                    val page = WikisourceClient.importPage(pageTitle, userAgent, wikiAuthor, signal)
                    // Cancellation can disconnect the socket on an IO thread. Check again before
                    // entering the non-suspending library write in the same withContext block.
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    LibraryStore.get(context).addOrGetExisting(page.title, page.author, page.text, null, true)
                }
                ensureActive()
                if (!activity.canAcceptUi() || !wikiImportSessions.owns(sessionToken)) return@launch
                if (addResult.added) libraryVersion++
                // 与浏览器 tab / 直链 / 网页一致：成功后落到详情页横幅确认，
                // 不再靠 Toast 一闪而过，不用回书架翻找。
                context.startActivity(AppIntents.bookDetailJustImported(context, addResult.id, addResult.added))
            } catch (cancel: CancellationException) {
                // Cancel / back / leave: never search_import_fail.
                if (activity.canAcceptUi() && wikiImportSessions.owns(sessionToken)) {
                    wikiImportingTitle = null
                    wikiImportJob = null
                    wikiImportCancellationSignal = null
                }
                throw cancel
            } catch (e: Exception) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (!activity.canAcceptUi() || !wikiImportSessions.owns(sessionToken)) return@launch
                Log.e("SearchActivity", "Failed to import Wikisource page", e)
                // 与直链/网页/浏览器一致：429/5xx 吃分类文案，其余回退通用提示。
                wikiImportError = context.downloadImportFailureMessage(e, R.string.search_import_fail)
                failedWikiTitle = pageTitle
            } finally {
                if (activity.canAcceptUi() && wikiImportSessions.owns(sessionToken)) {
                    wikiImportingTitle = null
                    wikiImportJob = null
                    wikiImportCancellationSignal = null
                }
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
                        onClick = {
                            if (isBusy) cancelActiveWork(leave = true) else onClose()
                        },
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
                    onValueChange = {
                        query = it
                        localImportError = null
                        wikiSearchError = null
                        wikiImportError = null
                    },
                    enabled = !isBusy,
                    singleLine = true,
                    label = { Text(stringResource(R.string.search_shelf)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        refreshLocal()
                        keyboardController?.hide()
                    }),
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            IconButton(
                                onClick = {
                                    query = ""
                                    localImportError = null
                                    wikiSearchError = null
                                    wikiImportError = null
                                    refreshLocal(recordTerm = false)
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
            if (searchHistory.isNotEmpty() && !isBusy) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.search_history_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(R.string.search_history_clear),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                            .semantics {
                                contentDescription = context.getString(R.string.search_history_clear_cd)
                            }
                            .clickable(
                                role = Role.Button,
                                onClick = {
                                    preferences.clearSearchHistory()
                                    searchHistory = emptyList()
                                },
                            ),
                    )
                }
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    searchHistory.forEach { term ->
                        SuggestionChip(
                            onClick = {
                                query = term
                                localImportError = null
                                wikiSearchError = null
                                wikiImportError = null
                                refreshLocal()
                                keyboardController?.hide()
                            },
                            enabled = !isBusy,
                            label = { Text(term) },
                            modifier = Modifier.semantics {
                                contentDescription = context.getString(R.string.search_history_cd, term)
                            },
                        )
                    }
                }
            }
            // Wiki import progress: which title is downloading (with cancel).
            val importingTitle = wikiImportingTitle
            if (importingTitle != null) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {
                            liveRegion = LiveRegionMode.Polite
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = context.getString(
                            R.string.search_importing_page,
                            importingTitle,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { cancelActiveWork(leave = false) },
                        modifier = Modifier.semantics {
                            contentDescription = cancelWikiImportCd
                        },
                    ) {
                        Text(cancelWikiImportLabel)
                    }
                }
            }
            // Wiki import errors show with retry next to the search row.
            if (wikiImportError != null) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = wikiImportError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                            .semantics {
                                liveRegion = LiveRegionMode.Polite
                            },
                    )
                    val retryTitle = failedWikiTitle
                    if (retryTitle != null && !isBusy) {
                        TextButton(
                            onClick = { importWikiPage(retryTitle) },
                            modifier = Modifier.semantics {
                                contentDescription = context.getString(
                                    R.string.search_import_retry_cd,
                                    retryTitle,
                                )
                            },
                        ) {
                            Text(
                                stringResource(
                                    R.string.search_import_retry,
                                    retryTitle.take(12) + if (retryTitle.length > 12) "…" else "",
                                )
                            )
                        }
                    }
                }
            }
            // Wiki search errors show under the history chips, above the local section.
            if (wikiSearchError != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = wikiSearchError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .semantics {
                            liveRegion = LiveRegionMode.Polite
                        },
                )
            }
            // Local import errors sit directly above the local import button.
            if (localImportError != null && !localImporting) {
                Text(
                    text = localImportError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .semantics {
                            liveRegion = LiveRegionMode.Polite
                        },
                )
                Spacer(Modifier.height(4.dp))
            }
            Spacer(Modifier.height(10.dp))
            if (localImporting) {
                val importingText = if (localImportTotal > 1) {
                    context.getString(
                        R.string.search_importing_local_progress,
                        (localImportDone + 1).coerceAtMost(localImportTotal),
                        localImportTotal,
                    )
                } else {
                    localImportingLabel
                }
                Text(
                    text = importingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            liveRegion = LiveRegionMode.Polite
                        },
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { cancelActiveWork(leave = false) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = cancelLocalCd },
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(cancelLocalLabel)
                    }
                }
            } else {
                OutlinedButton(
                    onClick = { pickLocalFile() },
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = importLocalCd },
                ) {
                    Text(stringResource(R.string.search_import_local))
                }
                val retryUris = failedLocalUris
                if (retryUris.isNotEmpty() && !isBusy) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { importUris(retryUris, isRetry = true) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .semantics {
                                contentDescription = context.getString(R.string.search_local_retry_cd)
                            },
                    ) {
                        Text(stringResource(R.string.search_local_retry, retryUris.size))
                    }
                }
                Text(
                    text = stringResource(R.string.search_share_import_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            if (listState is SearchListState.WikiLoading) {
                OutlinedButton(
                    onClick = { cancelActiveWork(leave = false) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = cancelWikiSearchCd },
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(cancelWikiSearchLabel)
                    }
                }
            } else {
                OutlinedButton(
                    onClick = { searchWiki() },
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = wikiCd },
                ) {
                    Text(stringResource(R.string.search_wikisource))
                }
            }
            Spacer(Modifier.height(8.dp))
            if (wikiImportingTitle == featuredTitle) {
                OutlinedButton(
                    onClick = { cancelActiveWork(leave = false) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = cancelWikiImportCd },
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(cancelWikiImportLabel)
                    }
                }
            } else {
                OutlinedButton(
                    onClick = { importWikiPage(featuredTitle) },
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = featuredCd },
                ) {
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
                is SearchListState.LocalLoading -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .semantics(mergeDescendants = true) {
                                liveRegion = LiveRegionMode.Polite
                            },
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = localLoadingLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                is SearchListState.WikiLoading -> {
                    Text(
                        text = wikiLoading,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics {
                            liveRegion = LiveRegionMode.Polite
                        },
                    )
                }
                is SearchListState.Message -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = state.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // Only the empty-wiki-result message gets the hint; local empty
                        // and error messages keep their single-line form.
                        if (state.text.contains(wikiNone)) {
                            Text(
                                text = stringResource(R.string.search_wikisource_none_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
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
                                        // Same as shelf/discover cards: tap reads,
                                        // detail stays one long-press away via the reader menu.
                                        context.startActivity(AppIntents.reader(context, book.id))
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
