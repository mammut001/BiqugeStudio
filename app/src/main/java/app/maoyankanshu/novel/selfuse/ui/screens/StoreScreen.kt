package app.maoyankanshu.novel.selfuse.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.maoyankanshu.novel.selfuse.AppIntents
import app.maoyankanshu.novel.selfuse.R

/**
 * Focused import destination.
 *
 * The shelf is the single place that renders the user's books. This screen only
 * explains and launches supported import paths, avoiding a second full library
 * list that previously duplicated the shelf and made the two tabs ambiguous.
 */
@Composable
fun StoreScreen(
    bookCount: Int,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    val importLocal = stringResource(R.string.import_local_txt_epub)
    val importLocalCd = stringResource(R.string.import_local_txt_epub_cd)
    val importLocalSub = stringResource(R.string.store_import_local_subtitle)
    val importRemote = stringResource(R.string.import_remote_txt_epub)
    val importRemoteCd = stringResource(R.string.import_remote_txt_epub_cd)
    val importRemoteSub = stringResource(R.string.store_import_remote_subtitle)
    val importWeb = stringResource(R.string.import_web_article)
    val importWebCd = stringResource(R.string.import_web_article_cd)
    val importWebSub = stringResource(R.string.store_import_web_subtitle)
    val summary = stringResource(R.string.store_library_summary, bookCount)
    val summaryCd = stringResource(R.string.store_library_summary_cd, bookCount)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                Text(
                    text = stringResource(R.string.store_import_section),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { contentDescription = summaryCd },
                )
            }
        }

        item {
            // Browser download → Share / Open with → 阅笺 (no Download-folder hunt).
            val shareTipTitle = stringResource(R.string.store_share_import_title)
            val shareTipBody = stringResource(R.string.store_share_import_body)
            val shareTipCd = stringResource(R.string.store_share_import_cd)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = shareTipCd },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            text = shareTipTitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.semantics { heading() },
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = shareTipBody,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }

        item {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    StoreImportListItem(
                        icon = Icons.AutoMirrored.Filled.InsertDriveFile,
                        title = importLocal,
                        subtitle = importLocalSub,
                        contentDescription = importLocalCd,
                        onClick = {
                            context.startActivity(AppIntents.importLocal(context))
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    StoreImportListItem(
                        icon = Icons.Filled.CloudDownload,
                        title = importRemote,
                        subtitle = importRemoteSub,
                        contentDescription = importRemoteCd,
                        onClick = {
                            context.startActivity(AppIntents.remoteImport(context))
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    StoreImportListItem(
                        icon = Icons.Filled.Language,
                        title = importWeb,
                        subtitle = importWebSub,
                        contentDescription = importWebCd,
                        onClick = {
                            context.startActivity(AppIntents.webImport(context))
                        },
                    )
                }
            }
        }
    }
}

/**
 * Material 3 import row: ≥48dp target, TalkBack [contentDescription], AppIntents via [onClick].
 */
@Composable
private fun StoreImportListItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    contentDescription: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minWidth = 48.dp, minHeight = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            },
    )
}
