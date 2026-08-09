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
import androidx.compose.runtime.getValue
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
        setContent {
            BiqugeTheme(darkTheme = ReaderPreferences.get(this).nightMode()) {
                WebImportScreen(
                    onClose = { finish() },
                    onImported = { finish() },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebImportScreen(
    onClose: () -> Unit,
    onImported: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var importJob by remember { mutableStateOf<Job?>(null) }
    var urlError by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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
        importJob?.cancel()
        importJob = null
        loading = false
        if (leave) onClose()
    }

    fun startImport() {
        if (loading) return
        urlError = null
        errorMessage = null
        val rawUrl = url.trim()
        if (!ProgressMath.isHttpsUrl(rawUrl)) {
            urlError = context.getString(R.string.https_url_required)
            return
        }
        loading = true
        importJob = scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    WebImportFetcher.fetch(
                        rawUrl = rawUrl,
                        preferredTitle = title,
                        userAgent = userAgent,
                        defaultTitle = defaultTitle,
                    )
                }
                ensureActive()
                if (!activity.canAcceptUi()) return@launch
                val author = "$authorPrefix\n${result.sourceUrl}"
                val footer = context.getString(R.string.web_source_footer, result.sourceUrl)
                // Large article concatenation and library persistence are storage/allocation work;
                // keep both away from Compose/main so a successful download does not end in a
                // noticeable UI freeze while the book is being saved.
                withContext(Dispatchers.IO) {
                    LibraryStore.get(context).add(
                        result.title,
                        author,
                        result.body + footer,
                    )
                }
                ensureActive()
                if (!activity.canAcceptUi()) return@launch
                Toast.makeText(
                    context,
                    context.getString(R.string.web_import_ok, result.title),
                    Toast.LENGTH_SHORT,
                ).show()
                onImported()
            } catch (cancel: CancellationException) {
                // User cancel, back, or leave composition: never treat as web_import_fail.
                if (activity.canAcceptUi()) {
                    loading = false
                    importJob = null
                }
                throw cancel
            } catch (error: Exception) {
                if (!activity.canAcceptUi()) return@launch
                Log.e("YueJianWebImport", "Unable to import web page", error)
                errorMessage = context.getString(R.string.web_import_fail)
                loading = false
                importJob = null
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
                    errorMessage = null
                },
                enabled = !loading,
                singleLine = true,
                label = { Text(stringResource(R.string.web_title_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = titleCd },
            )
            OutlinedTextField(
                value = url,
                onValueChange = {
                    url = it
                    urlError = null
                    errorMessage = null
                },
                enabled = !loading,
                singleLine = true,
                label = { Text(stringResource(R.string.https_hint_web)) },
                isError = urlError != null,
                supportingText = if (urlError != null) {
                    {
                        Text(
                            text = urlError!!,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics {
                                contentDescription = urlError!!
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
                            contentDescription = errorMessage!!
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
                Button(
                    onClick = { startImport() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = importCd },
                ) {
                    Text(stringResource(R.string.web_import_action))
                }
            }
        }
    }
}
