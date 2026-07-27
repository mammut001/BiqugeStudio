package app.maoyankanshu.novel.selfuse.ui.screens

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.maoyankanshu.novel.selfuse.BuildConfig
import app.maoyankanshu.novel.selfuse.LibraryStore
import app.maoyankanshu.novel.selfuse.R
import app.maoyankanshu.novel.selfuse.ReaderPreferences

@Composable
fun ProfileScreen(
    contentPadding: PaddingValues,
    onLibraryRestored: () -> Unit,
    onDarkThemeChanged: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val appName = stringResource(R.string.app_name)
    val preferences = remember { ReaderPreferences.get(context) }
    var fontSize by remember { mutableIntStateOf(preferences.fontSize()) }
    var nightMode by remember { mutableStateOf(preferences.nightMode()) }
    var showAbout by remember { mutableStateOf(false) }

    val backupOk = stringResource(R.string.profile_backup_ok)
    val backupFail = stringResource(R.string.profile_backup_fail)

    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                LibraryStore.get(context).exportTo(stream)
            }
            Toast.makeText(context, backupOk, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(context, backupFail, Toast.LENGTH_SHORT).show()
        }
    }

    val restoreBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val count = context.contentResolver.openInputStream(uri)?.use { stream ->
                LibraryStore.get(context).importFrom(stream)
            } ?: 0
            Toast.makeText(
                context,
                context.getString(R.string.profile_restore_ok, count),
                Toast.LENGTH_SHORT,
            ).show()
            onLibraryRestored()
        } catch (_: Exception) {
            Toast.makeText(context, backupFail, Toast.LENGTH_SHORT).show()
        }
    }

    val fontSizeLabel = stringResource(R.string.profile_font_size, fontSize)
    val fontSizeCd = stringResource(R.string.profile_font_size_cd, fontSize)
    val smaller = stringResource(R.string.profile_font_smaller)
    val larger = stringResource(R.string.profile_font_larger)
    val nightLabel = stringResource(
        if (nightMode) R.string.profile_night_off else R.string.profile_night_on,
    )
    val backup = stringResource(R.string.profile_backup)
    val restore = stringResource(R.string.profile_restore)
    val display = stringResource(R.string.profile_display_settings)
    val aboutLabel = stringResource(R.string.about_app, appName)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.profile_settings_heading),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = fontSizeLabel,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { contentDescription = fontSizeCd },
        )

        OutlinedButton(
            onClick = {
                preferences.setFontSize(fontSize - 1)
                fontSize = preferences.fontSize()
            },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = smaller },
        ) {
            Text(smaller)
        }
        OutlinedButton(
            onClick = {
                preferences.setFontSize(fontSize + 1)
                fontSize = preferences.fontSize()
            },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = larger },
        ) {
            Text(larger)
        }
        OutlinedButton(
            onClick = {
                val enabled = !nightMode
                preferences.setNightMode(enabled)
                nightMode = preferences.nightMode()
                onDarkThemeChanged(nightMode)
            },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = nightLabel },
        ) {
            Text(nightLabel)
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = { createBackup.launch(context.getString(R.string.backup_file_name, appName)) },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = backup },
        ) {
            Text(backup)
        }
        OutlinedButton(
            onClick = { restoreBackup.launch(arrayOf("application/zip", "*/*")) },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = restore },
        ) {
            Text(restore)
        }
        OutlinedButton(
            onClick = {
                context.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
            },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = display },
        ) {
            Text(display)
        }
        OutlinedButton(
            onClick = { showAbout = true },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = aboutLabel },
        ) {
            Text(aboutLabel)
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.profile_no_account_note, appName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("$appName ${BuildConfig.VERSION_NAME}") },
            text = {
                Text(
                    text = stringResource(R.string.about_body, appName),
                    modifier = Modifier.semantics {
                        contentDescription =
                            context.getString(R.string.privacy_summary_cd)
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) {
                    Text(stringResource(R.string.about_ok))
                }
            },
        )
    }
}
