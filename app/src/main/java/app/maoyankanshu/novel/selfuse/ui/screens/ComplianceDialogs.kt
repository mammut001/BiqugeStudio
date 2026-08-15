package app.maoyankanshu.novel.selfuse.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.maoyankanshu.novel.selfuse.BuildConfig
import app.maoyankanshu.novel.selfuse.R

@Composable
fun PrivacyConsentGate(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    var showFullPolicy by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = { }) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.privacy_gate_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.privacy_gate_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = { showFullPolicy = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.privacy_gate_read_full))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onDecline,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.privacy_gate_decline))
                    }
                    Button(
                        onClick = onAccept,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.privacy_gate_accept))
                    }
                }
            }
        }
    }

    if (showFullPolicy) {
        PrivacyPolicyDialog(onDismiss = { showFullPolicy = false })
    }
}

@Composable
fun PrivacyPolicyDialog(onDismiss: () -> Unit) {
    ReadableTextDialog(
        title = stringResource(R.string.privacy_policy_title),
        body = rememberRawText(R.raw.privacy_policy_cn),
        onDismiss = onDismiss,
    )
}

@Composable
fun OpenSourceLicensesDialog(onDismiss: () -> Unit) {
    ReadableTextDialog(
        title = stringResource(R.string.open_source_title),
        body = rememberRawText(R.raw.open_source_notices_cn),
        onDismiss = onDismiss,
    )
}

@Composable
fun ComplianceCenterDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var showPrivacy by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }
    val developerName = stringResource(R.string.compliance_developer_name).trim()
    val developerContact = stringResource(R.string.compliance_developer_contact).trim()
    val missing = stringResource(R.string.compliance_missing_required)
    val recordNumber = stringResource(R.string.app_record_number).trim()
    val sourceUrl = stringResource(R.string.source_repository_url)
    val recordUrl = stringResource(R.string.miit_app_record_query_url)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.compliance_center_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Text(stringResource(R.string.compliance_version, BuildConfig.VERSION_NAME))
                Text(stringResource(R.string.compliance_package, BuildConfig.APPLICATION_ID))
                Text(stringResource(R.string.compliance_developer_label, developerName.ifEmpty { missing }))
                Text(stringResource(R.string.compliance_contact_label, developerContact.ifEmpty { missing }))

                TextButton(onClick = { showPrivacy = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.compliance_privacy_policy))
                }
                TextButton(onClick = { showLicenses = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.compliance_open_source))
                }
                TextButton(
                    onClick = { openUrl(context, sourceUrl) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.compliance_source_code))
                }
                if (recordNumber.isNotEmpty()) {
                    TextButton(
                        onClick = { openUrl(context, recordUrl) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.compliance_app_record, recordNumber))
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.compliance_close))
                }
            }
        }
    }

    if (showPrivacy) PrivacyPolicyDialog(onDismiss = { showPrivacy = false })
    if (showLicenses) OpenSourceLicensesDialog(onDismiss = { showLicenses = false })
}

@Composable
private fun ReadableTextDialog(
    title: String,
    body: String,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 680.dp)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                )
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.compliance_close))
                }
            }
        }
    }
}

@Composable
private fun rememberRawText(rawResId: Int): String {
    val context = LocalContext.current
    return remember(context, rawResId) {
        context.resources.openRawResource(rawResId).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
