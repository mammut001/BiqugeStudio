package app.maoyankanshu.novel.selfuse

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.maoyankanshu.novel.selfuse.ui.reader.ProgressMath
import app.maoyankanshu.novel.selfuse.ui.theme.BiqugeTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Compose HTTPS single-page HTML import (replaces LinearLayout Java UI).
 * Manifest component remains `.WebImportActivity`.
 *
 * Fetch work uses [rememberCoroutineScope] as a tracked [Job].
 * User cancel, back, or leaving composition cancel the Job;
 * [CancellationException] is rethrown and **not** shown as import failure.
 */
class WebImportActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val suggestedTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val suggestedUrl = intent.getStringExtra(EXTRA_URL).orEmpty()
        setContent {
            BiqugeTheme(darkTheme = ReaderPreferences.get(this).nightMode()) {
                WebImportScreen(
                    initialTitle = suggestedTitle,
                    initialUrl = suggestedUrl,
                    onClose = { finish() },
                    onImported = { bookId, added ->
                        startActivity(AppIntents.bookDetailJustImported(this, bookId, added))
                        finish()
                    },
                )
            }
        }
    }

    companion object {
        const val EXTRA_TITLE: String = "title"
        const val EXTRA_URL: String = "url"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebImportScreen(
    initialTitle: String = "",
    initialUrl: String = "",
    onClose: () -> Unit,
    onImported: (String, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(initialTitle) }
    var url by remember { mutableStateOf(initialUrl) }
    var loading by remember { mutableStateOf(false) }
    var importJob by remember { mutableStateOf<Job?>(null) }
    var cancellationSignal by remember { mutableStateOf<RemoteImportCancellationSignal?>(null) }
    val importSessions = remember { ImportSessionTracker() }
    var urlError by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var lastFailureDetail by remember { mutableStateOf<DownloadImportFailure.Detail?>(null) }
    // Same 429 window as the direct-link page: extra taps replay remaining time
    // and must not extend the cooldown or fire another request.
    val rateLimitThrottle = remember { RateLimitThrottle() }

    val backCd = stringResource(R.string.web_back_cd)
    val titleCd = stringResource(R.string.web_title_cd)
    val urlCd = stringResource(R.string.web_url_cd)
    val importCd = stringResource(R.string.web_import_cd)
    val cancelCd = stringResource(R.string.web_cancel_import_cd)
    val importingLabel = stringResource(R.string.web_importing)
    val userAgent = stringResource(R.string.http_user_agent)
    val defaultTitle = stringResource(R.string.web_default_title)
    val authorPrefix = stringResource(R.string.web_author_prefix)

    fun cancelImport(leave: Boolean) {
        cancellationSignal?.cancel()
        importSessions.invalidate()
        cancellationSignal = null
        importJob?.cancel()
        importJob = null
        loading = false
        if (leave) onClose()
    }

    DisposableEffect(Unit) {
        onDispose {
            cancellationSignal?.cancel()
            importJob?.cancel()
        }
    }

    fun startImport() {
        if (loading) return
        urlError = null
        errorMessage = null
        lastFailureDetail = null
        val rawUrl = url.trim()
        val nowMs = android.os.SystemClock.elapsedRealtime()
        val cooldownRemainingMs = rateLimitThrottle.remainingMs(rawUrl, nowMs)
        if (cooldownRemainingMs > 0L) {
            val remainingSeconds = (cooldownRemainingMs + 999L) / 1_000L
            errorMessage = context.getString(
                R.string.download_import_rate_limited_wait,
                remainingSeconds,
            )
            lastFailureDetail = DownloadImportFailure.Detail(
                DownloadImportFailure.Kind.RATE_LIMITED,
                httpStatus = 429,
                retryAfterSeconds = remainingSeconds,
            )
            return
        }
        if (!ProgressMath.isHttpsUrl(rawUrl)) {
            urlError = context.getString(R.string.https_url_required)
            return
        }
        loading = true
        val sessionToken = importSessions.start()
        val signal = RemoteImportCancellationSignal()
        cancellationSignal = signal
        importJob = scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    WebImportFetcher.fetch(
                        rawUrl = rawUrl,
                        preferredTitle = title,
                        userAgent = userAgent,
                        defaultTitle = defaultTitle,
                        cancellationSignal = signal,
                    )
                }
                ensureActive()
                if (!activity.canAcceptUi()) return@launch
                val author = "$authorPrefix\n${result.sourceUrl}"
                val footer = context.getString(R.string.web_source_footer, result.sourceUrl)
                // Large article concatenation and library persistence are storage/allocation work;
                // keep both away from Compose/main so a successful download does not end in a
                // noticeable UI freeze while the book is being saved.
                val addResult = withContext(Dispatchers.IO) {
                    LibraryStore.get(context).addOrGetExisting(
                        result.title,
                        author,
                        result.body + footer,
                        null,
                        true,
                    )
                }
                ensureActive()
                if (!activity.canAcceptUi()) return@launch
                rateLimitThrottle.clear(rawUrl)
                onImported(addResult.id, addResult.added)
            } catch (cancel: CancellationException) {
                // User cancel, back, or leave composition: never treat as web_import_fail.
                if (activity.canAcceptUi() && importSessions.owns(sessionToken)) {
                    loading = false
                    importJob = null
                    cancellationSignal = null
                }
                throw cancel
            } catch (error: Exception) {
                // A disconnected socket can surface as IOException before the coroutine
                // resumes; convert it back to silent cancellation when the Job was cancelled.
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (!activity.canAcceptUi() || !importSessions.owns(sessionToken)) return@launch
                Log.e("YueJianWebImport", "Unable to import web page", error)
                // 与直链页一致：429/5xx 吃分类文案，其余回退通用提示。
                val failure = DownloadImportFailure.classify(error)
                errorMessage = context.downloadImportFailureMessage(failure, R.string.web_import_fail)
                lastFailureDetail = failure
                if (failure.kind == DownloadImportFailure.Kind.RATE_LIMITED) {
                    rateLimitThrottle.record(
                        rawUrl,
                        android.os.SystemClock.elapsedRealtime(),
                        DownloadImportFailure.rateLimitCooldownMs(failure),
                    )
                }
                loading = false
                importJob = null
                cancellationSignal = null
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.web_import_heading),
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (loading) cancelImport(leave = true) else onClose()
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
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.web_import_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                },
                enabled = !loading,
                singleLine = true,
                label = { Text(stringResource(R.string.web_title_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = titleCd },
            )
            val clipboard = LocalClipboardManager.current
            val pasteEmpty = stringResource(R.string.remote_paste_empty)
            OutlinedTextField(
                value = url,
                onValueChange = {
                    url = it
                    urlError = null
                    errorMessage = null
                    lastFailureDetail = DownloadImportFailure.detailAfterUrlEdited(lastFailureDetail)
                },
                trailingIcon = {
                    TextButton(
                        onClick = {
                            val clip = clipboard.getText()?.text?.trim().orEmpty()
                            if (clip.isEmpty()) {
                                Toast.makeText(context, pasteEmpty, Toast.LENGTH_SHORT).show()
                            } else {
                                url = clip
                                urlError = null
                                errorMessage = null
                                lastFailureDetail = DownloadImportFailure.detailAfterUrlEdited(lastFailureDetail)
                            }
                        },
                        enabled = !loading,
                        modifier = Modifier.semantics {
                            contentDescription = context.getString(R.string.remote_paste_cd)
                        },
                    ) {
                        Text(stringResource(R.string.remote_paste))
                    }
                },
                isError = urlError != null,
                supportingText = if (urlError != null) {
                    {
                        Text(
                            text = urlError!!,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics {
                                liveRegion = LiveRegionMode.Polite
                            },
                        )
                    }
                } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = urlCd },
            )
            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            liveRegion = LiveRegionMode.Polite
                        },
                )
            }
            if (loading) {
                Text(
                    text = importingLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = importingLabel
                            liveRegion = LiveRegionMode.Polite
                        },
                )
            }
            Spacer(Modifier.height(8.dp))
            if (loading) {
                OutlinedButton(
                    onClick = { cancelImport(leave = false) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = cancelCd },
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(stringResource(R.string.web_cancel_import))
                    }
                }
            } else {
                val failed = errorMessage != null
                val retryUseful = DownloadImportFailure.isRetryEnabled(lastFailureDetail)
                OutlinedButton(
                    onClick = {
                        val seed = url.trim().takeIf { ProgressMath.isHttpsUrl(it) } ?: return@OutlinedButton
                        context.startActivity(AppIntents.browserImport(context, seed))
                    },
                    enabled = !loading && ProgressMath.isHttpsUrl(url.trim()),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .semantics {
                            contentDescription = context.getString(R.string.web_to_browser_cd)
                        },
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.web_to_browser_action))
                        Text(
                            stringResource(R.string.web_to_browser_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = {
                        context.startActivity(AppIntents.remoteImport(context, title, url))
                    },
                    enabled = !loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .semantics {
                            contentDescription = context.getString(R.string.web_to_remote_cd)
                        },
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.web_to_remote_action))
                        Text(
                            stringResource(R.string.web_to_remote_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { startImport() },
                    enabled = retryUseful,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .semantics {
                            contentDescription = if (failed) {
                                context.getString(R.string.remote_retry_download_cd)
                            } else {
                                importCd
                            }
                        },
                ) {
                    Text(
                        stringResource(
                            if (failed) R.string.remote_retry_download else R.string.web_import_action
                        )
                    )
                }
            }
        }
    }
}
