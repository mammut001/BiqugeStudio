package app.maoyankanshu.novel.selfuse

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
 * Compose HTTPS single-page HTML import (replaces LinearLayout Java UI).
 * Manifest component remains `.WebImportActivity`. SearchActivity stays Java.
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
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    val backCd = stringResource(R.string.web_back_cd)
    val titleCd = stringResource(R.string.web_title_cd)
    val urlCd = stringResource(R.string.web_url_cd)
    val importCd = stringResource(R.string.web_import_cd)
    val userAgent = stringResource(R.string.http_user_agent)
    val defaultTitle = stringResource(R.string.web_default_title)
    val authorPrefix = stringResource(R.string.web_author_prefix)

    fun startImport() {
        val rawUrl = url.trim()
        if (!ProgressMath.isHttpsUrl(rawUrl)) {
            Toast.makeText(context, context.getString(R.string.https_url_required), Toast.LENGTH_SHORT).show()
            return
        }
        loading = true
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    WebImportFetcher.fetch(
                        rawUrl = rawUrl,
                        preferredTitle = title,
                        userAgent = userAgent,
                        defaultTitle = defaultTitle,
                    )
                }
                val author = "$authorPrefix\n${result.sourceUrl}"
                val text = result.body + context.getString(R.string.web_source_footer, result.sourceUrl)
                LibraryStore.get(context).add(result.title, author, text)
                Toast.makeText(
                    context,
                    context.getString(R.string.web_import_ok, result.title),
                    Toast.LENGTH_SHORT,
                ).show()
                onImported()
            } catch (_: Exception) {
                Toast.makeText(
                    context,
                    context.getString(R.string.web_import_fail),
                    Toast.LENGTH_LONG,
                ).show()
                loading = false
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
                text = stringResource(R.string.web_import_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                enabled = !loading,
                singleLine = true,
                label = { Text(stringResource(R.string.web_title_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = titleCd },
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                enabled = !loading,
                singleLine = true,
                label = { Text(stringResource(R.string.https_hint_web)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = urlCd },
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { startImport() },
                enabled = !loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = importCd },
            ) {
                Text(
                    if (loading) {
                        stringResource(R.string.web_importing)
                    } else {
                        stringResource(R.string.web_import_action)
                    },
                )
            }
        }
    }
}
