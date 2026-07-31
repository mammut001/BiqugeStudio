package app.maoyankanshu.novel.selfuse

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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.maoyankanshu.novel.selfuse.ui.reader.ProgressMath
import app.maoyankanshu.novel.selfuse.ui.theme.BiqugeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Compose HTTPS direct-link TXT/EPUB import.
 * Intent extras unchanged: [EXTRA_TITLE], [EXTRA_URL].
 */
class RemoteImportActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val suggestedTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val suggestedUrl = intent.getStringExtra(EXTRA_URL).orEmpty()
        setContent {
            BiqugeTheme(darkTheme = ReaderPreferences.get(this).nightMode()) {
                RemoteImportScreen(
                    initialTitle = suggestedTitle,
                    initialUrl = suggestedUrl,
                    onClose = { finish() },
                    onImported = { finish() },
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
private fun RemoteImportScreen(
    initialTitle: String,
    initialUrl: String,
    onClose: () -> Unit,
    onImported: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(initialTitle) }
    var url by remember { mutableStateOf(initialUrl) }
    var loading by remember { mutableStateOf(false) }
    var urlError by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val backCd = stringResource(R.string.remote_back_cd)
    val titleCd = stringResource(R.string.remote_title_cd)
    val urlCd = stringResource(R.string.remote_url_cd)
    val downloadCd = stringResource(R.string.remote_download_cd)
    val userAgent = stringResource(R.string.http_user_agent)
    val authorEpub = stringResource(R.string.remote_author_epub)
    val authorTxt = stringResource(R.string.remote_author_txt)
    val defaultEpub = stringResource(R.string.remote_default_epub)
    val defaultTxt = stringResource(R.string.remote_default_txt)

    fun startDownload() {
        urlError = null
        errorMessage = null
        val rawUrl = url.trim()
        if (!ProgressMath.isHttpsUrl(rawUrl)) {
            urlError = context.getString(R.string.https_url_required)
            return
        }
        loading = true
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    RemoteImportDownloader.download(
                        rawUrl = rawUrl,
                        preferredTitle = title,
                        userAgent = userAgent,
                        defaultEpubTitle = defaultEpub,
                        defaultTxtTitle = defaultTxt,
                        authorEpub = authorEpub,
                        authorTxt = authorTxt,
                    )
                }
                LibraryStore.get(context).add(
                    result.title,
                    result.author,
                    result.text,
                    result.coverBytes,
                )
                Toast.makeText(
                    context,
                    context.getString(R.string.remote_import_ok, result.title),
                    Toast.LENGTH_SHORT,
                ).show()
                onImported()
            } catch (error: Exception) {
                Log.e("YueJianRemoteImport", "Unable to import direct file", error)
                errorMessage = context.getString(R.string.remote_import_fail)
                loading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.remote_import_heading),
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onClose,
                        enabled = !loading,
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
                text = stringResource(R.string.remote_import_note),
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
                label = { Text(stringResource(R.string.remote_title_hint)) },
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
                label = { Text(stringResource(R.string.https_hint_remote)) },
                isError = urlError != null,
                supportingText = if (urlError != null) {
                    {
                        Text(
                            text = urlError!!,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics { contentDescription = urlError!! },
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
                        .semantics { contentDescription = errorMessage!! },
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { startDownload() },
                enabled = !loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = downloadCd },
            ) {
                if (loading) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Text(stringResource(R.string.remote_downloading))
                    }
                } else {
                    Text(stringResource(R.string.remote_download))
                }
            }
        }
    }
}
