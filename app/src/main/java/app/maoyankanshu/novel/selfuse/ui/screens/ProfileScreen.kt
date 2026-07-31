package app.maoyankanshu.novel.selfuse.ui.screens

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.maoyankanshu.novel.selfuse.BuildConfig
import app.maoyankanshu.novel.selfuse.LibraryStore
import app.maoyankanshu.novel.selfuse.ProfileBackupOutcomes
import app.maoyankanshu.novel.selfuse.R
import app.maoyankanshu.novel.selfuse.ReaderPreferences
import app.maoyankanshu.novel.selfuse.canAcceptUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Profile / settings: reading prefs, SAF backup (CreateDocument) and restore (OpenDocument).
 *
 * Export / import run on [rememberCoroutineScope] as tracked [Job]s on [Dispatchers.IO].
 * User cancel or leaving composition cancels those Jobs; [CancellationException] is rethrown
 * and **not** shown as failure Toast / error dialog. Host Activity already
 * `finishing`/`destroyed` skips Toast / state ([canAcceptUi]).
 */
@Composable
fun ProfileScreen(
    contentPadding: PaddingValues,
    onLibraryRestored: () -> Unit,
    onDarkThemeChanged: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val activity = context as? Activity
    // Cancelled when ProfileScreen leaves composition (tab switch / process path).
    val scope = rememberCoroutineScope()
    val appName = stringResource(R.string.app_name)
    val preferences = remember { ReaderPreferences.get(context) }
    var fontSize by remember { mutableIntStateOf(preferences.fontSize()) }
    var nightMode by remember { mutableStateOf(preferences.nightMode()) }
    var showAbout by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var loadingText by remember { mutableStateOf("") }
    var loadingCd by remember { mutableStateOf("") }
    /** True while export Job is active; false while restore Job is active. */
    var loadingIsBackup by remember { mutableStateOf(true) }
    var backupJob by remember { mutableStateOf<Job?>(null) }
    var restoreJob by remember { mutableStateOf<Job?>(null) }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    val backupOk = stringResource(R.string.profile_backup_ok)
    val backupFail = stringResource(R.string.profile_backup_fail)
    val backupInProgress = stringResource(R.string.profile_backup_in_progress)
    val backupInProgressCd = stringResource(R.string.profile_backup_in_progress_cd)
    val restoreInProgress = stringResource(R.string.profile_restore_in_progress)
    val restoreInProgressCd = stringResource(R.string.profile_restore_in_progress_cd)
    val restoreEmptyFail = stringResource(R.string.profile_restore_empty_fail)
    val restoreInvalidFormat = stringResource(R.string.profile_restore_invalid_format)
    val cancelBackupLabel = stringResource(R.string.profile_cancel_backup)
    val cancelBackupCd = stringResource(R.string.profile_cancel_backup_cd)
    val cancelRestoreLabel = stringResource(R.string.profile_cancel_restore)
    val cancelRestoreCd = stringResource(R.string.profile_cancel_restore_cd)

    val smallerCd = stringResource(R.string.profile_font_smaller)
    val largerCd = stringResource(R.string.profile_font_larger)
    val nightCd = stringResource(
        if (nightMode) R.string.profile_night_off else R.string.profile_night_on
    )

    fun clearBusyFlags() {
        isLoading = false
        backupJob = null
        restoreJob = null
    }

    fun cancelActiveWork() {
        backupJob?.cancel()
        restoreJob?.cancel()
        clearBusyFlags()
        // Soft cancel: no fail Toast / error dialog (CancellationException path is silent too).
    }

    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (isLoading) return@rememberLauncherForActivityResult
        errorMessage = null
        isLoading = true
        loadingIsBackup = true
        loadingText = backupInProgress
        loadingCd = backupInProgressCd
        backupJob = scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        LibraryStore.get(context).exportTo(stream)
                    } ?: throw IllegalStateException("openOutputStream returned null")
                }
                ensureActive()
                if (!activity.canAcceptUi()) return@launch
                when (ProfileBackupOutcomes.backupNotice(cancelled = false, hardError = false)) {
                    ProfileBackupOutcomes.BackupNotice.SUCCESS -> {
                        isLoading = false
                        backupJob = null
                        Toast.makeText(context, backupOk, Toast.LENGTH_SHORT).show()
                    }
                    ProfileBackupOutcomes.BackupNotice.NONE,
                    ProfileBackupOutcomes.BackupNotice.FAIL,
                    -> Unit
                }
            } catch (cancel: CancellationException) {
                // User cancel / leave composition: never backup_fail dialog.
                if (activity.canAcceptUi()) {
                    clearBusyFlags()
                }
                throw cancel
            } catch (e: Exception) {
                if (!activity.canAcceptUi()) return@launch
                if (!ProfileBackupOutcomes.shouldSurfaceAsFailure(e)) throw e
                Log.e("YueJianProfile", "Unable to export library backup", e)
                isLoading = false
                backupJob = null
                errorMessage = ProfileBackupOutcomes.failMessage(backupFail, e)
            }
        }
    }

    val restoreBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (isLoading) return@rememberLauncherForActivityResult
        errorMessage = null
        isLoading = true
        loadingIsBackup = false
        loadingText = restoreInProgress
        loadingCd = restoreInProgressCd
        restoreJob = scope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        LibraryStore.get(context).importFrom(stream)
                    } ?: 0
                }
                ensureActive()
                if (!activity.canAcceptUi()) return@launch
                when (
                    ProfileBackupOutcomes.restoreNotice(
                        cancelled = false,
                        count = count,
                        hardError = false,
                    )
                ) {
                    ProfileBackupOutcomes.RestoreNotice.SUCCESS -> {
                        isLoading = false
                        restoreJob = null
                        Toast.makeText(
                            context,
                            context.getString(R.string.profile_restore_ok, count),
                            Toast.LENGTH_SHORT,
                        ).show()
                        onLibraryRestored()
                    }
                    ProfileBackupOutcomes.RestoreNotice.EMPTY -> {
                        isLoading = false
                        restoreJob = null
                        errorMessage = restoreEmptyFail
                    }
                    ProfileBackupOutcomes.RestoreNotice.NONE,
                    ProfileBackupOutcomes.RestoreNotice.INVALID,
                    -> Unit
                }
            } catch (cancel: CancellationException) {
                // User cancel / leave: never restore fail dialog.
                if (activity.canAcceptUi()) {
                    clearBusyFlags()
                }
                throw cancel
            } catch (e: Exception) {
                if (!activity.canAcceptUi()) return@launch
                if (!ProfileBackupOutcomes.shouldSurfaceAsFailure(e)) throw e
                Log.e("YueJianProfile", "Unable to restore library backup", e)
                isLoading = false
                restoreJob = null
                errorMessage = ProfileBackupOutcomes.failMessage(restoreInvalidFormat, e)
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
                            toggleChecked = nightMode,
                            onToggle = { checked ->
                                preferences.setNightMode(checked)
                                nightMode = preferences.nightMode()
                                onDarkThemeChanged(nightMode)
                            },
                            contentDescription = nightCd,
                            trailingContent = {
                                // Row owns interaction (Role.Switch); Switch is visual state only.
                                Switch(
                                    checked = nightMode,
                                    onCheckedChange = null,
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
            // Back / outside tap soft-cancels the Job (no fail Toast / dialog).
            Dialog(onDismissRequest = { cancelActiveWork() }) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    modifier = Modifier.semantics {
                        contentDescription = loadingCd
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
                                text = loadingText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.semantics {
                                    contentDescription = loadingCd
                                    liveRegion = LiveRegionMode.Polite
                                },
                            )
                        }
                        val cancelLabel = if (loadingIsBackup) cancelBackupLabel else cancelRestoreLabel
                        val cancelCd = if (loadingIsBackup) cancelBackupCd else cancelRestoreCd
                        OutlinedButton(
                            onClick = { cancelActiveWork() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .semantics { contentDescription = cancelCd },
                        ) {
                            Text(cancelLabel)
                        }
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
            text = {
                Text(
                    text = msg,
                    modifier = Modifier.semantics {
                        contentDescription = msg
                        liveRegion = LiveRegionMode.Polite
                    },
                )
            },
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

/**
 * Settings list row: ≥48×56dp target, Material 3 color roles.
 *
 * - [onClick]: TalkBack [Role.Button] via [Modifier.clickable] + [semantics]
 *   (same pattern as StoreScreen import rows).
 * - [toggleChecked]/[onToggle]: TalkBack [Role.Switch] via [Modifier.toggleable]
 *   for night-mode style switch rows (trailing Switch is visual-only).
 */
@Composable
private fun ProfileItemRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    toggleChecked: Boolean? = null,
    onToggle: ((Boolean) -> Unit)? = null,
    contentDescription: String? = null,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    val checked = toggleChecked
    val toggle = onToggle
    val interactionModifier = when {
        checked != null && toggle != null -> Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = toggle,
        )
        onClick != null -> Modifier.clickable(
            enabled = enabled,
            role = Role.Button,
            onClick = onClick,
        )
        else -> Modifier
    }
    val semanticRole = when {
        checked != null && toggle != null -> Role.Switch
        onClick != null -> Role.Button
        else -> null
    }

    val itemModifier = Modifier
        .fillMaxWidth()
        .defaultMinSize(minWidth = 48.dp, minHeight = 56.dp)
        .then(interactionModifier)
        .padding(horizontal = 16.dp, vertical = 12.dp)
        .semantics {
            contentDescription?.let { this.contentDescription = it }
            semanticRole?.let { this.role = it }
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
