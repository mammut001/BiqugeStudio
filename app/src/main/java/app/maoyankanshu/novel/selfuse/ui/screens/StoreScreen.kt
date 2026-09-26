package app.maoyankanshu.novel.selfuse.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.maoyankanshu.novel.selfuse.AppIntents
import app.maoyankanshu.novel.selfuse.R
import app.maoyankanshu.novel.selfuse.ui.components.IconBadge
import app.maoyankanshu.novel.selfuse.ui.theme.cardContainerColor

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
    val importBrowser = stringResource(R.string.import_browser_download)
    val importBrowserCd = stringResource(R.string.import_browser_download_cd)
    val importBrowserSub = stringResource(R.string.store_import_browser_subtitle)
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
            // Top bar already names the page; lead with the library summary instead of repeating it.
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .semantics { contentDescription = summaryCd },
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = cardContainerColor),
            ) {
                Column {
                    StoreImportListItem(
                        icon = Icons.Filled.Public,
                        title = importBrowser,
                        subtitle = importBrowserSub,
                        contentDescription = importBrowserCd,
                        onClick = {
                            context.startActivity(AppIntents.browserImport(context))
                        },
                    )
                    RowDivider()
                    StoreImportListItem(
                        icon = Icons.AutoMirrored.Filled.InsertDriveFile,
                        title = importLocal,
                        subtitle = importLocalSub,
                        contentDescription = importLocalCd,
                        onClick = {
                            context.startActivity(AppIntents.importLocal(context))
                        },
                    )
                    RowDivider()
                    StoreImportListItem(
                        icon = Icons.Filled.CloudDownload,
                        title = importRemote,
                        subtitle = importRemoteSub,
                        contentDescription = importRemoteCd,
                        onClick = {
                            context.startActivity(AppIntents.remoteImport(context))
                        },
                    )
                    RowDivider()
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

        item {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(14.dp),
                )
                Text(
                    text = stringResource(R.string.online_import_scope_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
        }
    }
}

/** Hairline divider inset past the icon badge. */
@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 70.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
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
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = { IconBadge(icon) },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
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
