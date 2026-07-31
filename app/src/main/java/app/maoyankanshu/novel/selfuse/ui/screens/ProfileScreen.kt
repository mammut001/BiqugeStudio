package app.maoyankanshu.novel.selfuse.ui.screens

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.maoyankanshu.novel.selfuse.BuildConfig
import app.maoyankanshu.novel.selfuse.LibraryStore
import app.maoyankanshu.novel.selfuse.R
import app.maoyankanshu.novel.selfuse.ReaderPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProfileScreen(
    contentPadding: PaddingValues,
    onLibraryRestored: () -> Unit,
    onDarkThemeChanged: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appName = stringResource(R.string.app_name)
    val preferences = remember { ReaderPreferences.get(context) }
    var fontSize by remember { mutableIntStateOf(preferences.fontSize()) }
    var nightMode by remember { mutableStateOf(preferences.nightMode()) }
    var showAbout by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var loadingText by remember { mutableStateOf("") }
    var loadingCd by remember { mutableStateOf("") }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    val backupOk = stringResource(R.string.profile_backup_ok)
    val backupFail = stringResource(R.string.profile_backup_fail)
    val backupInProgress = stringResource(R.string.profile_backup_in_progress)
    val backupInProgressCd = stringResource(R.string.profile_backup_in_progress_cd)
    val restoreInProgress = stringResource(R.string.profile_restore_in_progress)
    val restoreInProgressCd = stringResource(R.string.profile_restore_in_progress_cd)
    val restoreEmptyFail = stringResource(R.string.profile_restore_empty_fail)
    val restoreInvalidFormat = stringResource(R.string.profile_restore_invalid_format)

    val smallerCd = stringResource(R.string.profile_font_smaller)
    val largerCd = stringResource(R.string.profile_font_larger)
    val nightCd = stringResource(
        if (nightMode) R.string.profile_night_off else R.string.profile_night_on
    )

    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isLoading = true
        loadingText = backupInProgress
        loadingCd = backupInProgressCd
        scope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    LibraryStore.get(context).exportTo(stream)
                }
                withContext(Dispatchers.Main) {
                    isLoading = false
                    Toast.makeText(context, backupOk, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    errorMessage = backupFail + if (e.localizedMessage != null) "\n(${e.localizedMessage})" else ""
                }
            }
        }
    }

    val restoreBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isLoading = true
        loadingText = restoreInProgress
        loadingCd = restoreInProgressCd
        scope.launch(Dispatchers.IO) {
            try {
                val count = context.contentResolver.openInputStream(uri)?.use { stream ->
                    LibraryStore.get(context).importFrom(stream)
                } ?: 0
                withContext(Dispatchers.Main) {
                    isLoading = false
                    if (count > 0) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.profile_restore_ok, count),
                            Toast.LENGTH_SHORT,
                        ).show()
                        onLibraryRestored()
                    } else {
                        errorMessage = restoreEmptyFail
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    val detail = e.localizedMessage ?: ""
                    errorMessage = restoreInvalidFormat + if (detail.isNotEmpty()) "\n($detail)" else ""
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.profile_settings_heading),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )

            // Section 1: Reading Preferences & Appearance
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ProfileSectionHeader(stringResource(R.string.profile_section_reading))
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        ProfileItemRow(
                            icon = Icons.Filled.FormatSize,
                            title = stringResource(R.string.profile_font_size_title),
                            subtitle = stringResource(R.string.profile_font_size_subtitle, fontSize),
                            enabled = !isLoading,
                            contentDescription = stringResource(R.string.profile_font_size_cd, fontSize),
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            preferences.setFontSize(fontSize - 1)
                                            fontSize = preferences.fontSize()
                                        },
                                        enabled = !isLoading && fontSize > 12,
                                        modifier = Modifier
                                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                            .semantics { contentDescription = smallerCd },
                                    ) {
                                        Icon(Icons.Filled.Remove, contentDescription = null)
                                    }
                                    IconButton(
                                        onClick = {
                                            preferences.setFontSize(fontSize + 1)
                                            fontSize = preferences.fontSize()
                                        },
                                        enabled = !isLoading && fontSize < 36,
                                        modifier = Modifier
                                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                            .semantics { contentDescription = largerCd },
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = null)
                                    }
                                }
                            },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ProfileItemRow(
                            icon = Icons.Filled.DarkMode,
                            title = stringResource(R.string.profile_night_title),
                            subtitle = stringResource(R.string.profile_night_subtitle),
                            enabled = !isLoading,
                            contentDescription = nightCd,
                            trailingContent = {
                                Switch(
                                    checked = nightMode,
                                    onCheckedChange = { enabled ->
                                        preferences.setNightMode(enabled)
                                        nightMode = preferences.nightMode()
                                        onDarkThemeChanged(nightMode)
                                    },
                                    enabled = !isLoading,
                                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                                )
                            },
                        )
                    }
                }
            }

            // Section 2: Data & Backup
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ProfileSectionHeader(stringResource(R.string.profile_section_data))
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        ProfileItemRow(
                            icon = Icons.Filled.Backup,
                            title = stringResource(R.string.profile_backup_title),
                            subtitle = stringResource(R.string.profile_backup_subtitle),
                            enabled = !isLoading,
                            onClick = {
                                createBackup.launch(context.getString(R.string.backup_file_name, appName))
                            },
                            contentDescription = stringResource(R.string.profile_backup),
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ProfileItemRow(
                            icon = Icons.Filled.Restore,
                            title = stringResource(R.string.profile_restore_title),
                            subtitle = stringResource(R.string.profile_restore_subtitle),
                            enabled = !isLoading,
                            onClick = {
                                restoreBackup.launch(arrayOf("application/zip", "*/*"))
                            },
                            contentDescription = stringResource(R.string.profile_restore),
                        )
                    }
                }
            }

            // Section 3: System & About
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ProfileSectionHeader(stringResource(R.string.profile_section_system))
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        ProfileItemRow(
                            icon = Icons.Filled.Settings,
                            title = stringResource(R.string.profile_display_title),
                            subtitle = stringResource(R.string.profile_display_subtitle),
                            enabled = !isLoading,
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
                            },
                            contentDescription = stringResource(R.string.profile_display_settings),
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ProfileItemRow(
                            icon = Icons.Filled.Info,
                            title = stringResource(R.string.profile_about_title, appName),
                            subtitle = stringResource(R.string.profile_about_subtitle, BuildConfig.VERSION_NAME),
                            enabled = !isLoading,
                            onClick = { showAbout = true },
                            contentDescription = stringResource(R.string.about_app, appName),
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.profile_no_account_note, appName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        if (isLoading) {
            Dialog(onDismissRequest = {}) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    modifier = Modifier.semantics { contentDescription = loadingCd },
                ) {
                    Row(
                        modifier = Modifier.padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp,
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = loadingText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("$appName ${BuildConfig.VERSION_NAME}") },
            text = {
                Text(
                    text = stringResource(R.string.about_body, appName),
                    modifier = Modifier.semantics {
                        contentDescription = context.getString(R.string.privacy_summary_cd)
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { showAbout = false },
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                ) {
                    Text(stringResource(R.string.about_ok))
                }
            },
        )
    }

    errorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text(stringResource(R.string.profile_error_dialog_title)) },
            text = { Text(msg) },
            confirmButton = {
                TextButton(
                    onClick = { errorMessage = null },
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                ) {
                    Text(stringResource(R.string.profile_error_dialog_confirm))
                }
            },
        )
    }
}

@Composable
private fun ProfileSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

@Composable
private fun ProfileItemRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    val itemModifier = Modifier
        .fillMaxWidth()
        .defaultMinSize(minWidth = 48.dp, minHeight = 56.dp)
        .then(
            if (onClick != null) {
                Modifier.clickable(enabled = enabled, onClick = onClick)
            } else {
                Modifier
            }
        )
        .padding(horizontal = 16.dp, vertical = 12.dp)
        .semantics {
            contentDescription?.let { this.contentDescription = it }
        }

    Row(
        modifier = itemModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
            if (subtitle.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                )
            }
        }
        if (trailingContent != null) {
            Spacer(Modifier.width(8.dp))
            trailingContent()
        }
    }
}
