package app.maoyankanshu.novel.selfuse.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.maoyankanshu.novel.selfuse.Book
import app.maoyankanshu.novel.selfuse.R
import app.maoyankanshu.novel.selfuse.ui.reader.ProgressMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Fixed offline cover tile (no network images). */
private val CoverWidth = 56.dp
private val CoverHeight = 74.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookCard(
    book: Book,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onContinueReading: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showContinueReading: Boolean = true,
) {
    val progressFraction = (book.position.coerceIn(0, 1000) / 1000f)
    val progressLabel = book.progressLabel()
    val percent = ProgressMath.percentOfProgress(book.position)
    val percentLabel = "$percent%"
    val actionLabel = if (book.position <= 0) {
        stringResource(R.string.detail_start_reading)
    } else {
        stringResource(R.string.detail_continue_reading)
    }
    val description = buildString {
        append(book.title)
        append("，作者 ")
        append(book.author)
        append("，")
        append(subtitle ?: progressLabel)
        append("，进度 ")
        append(percentLabel)
        if (onLongClick != null) append("。长按打开更多操作")
        if (showContinueReading && onContinueReading != null) {
            append("。可")
            append(actionLabel)
        }
    }
    val coverBrush = remember(book.id, book.title) {
        coverGradient(book.id + book.title)
    }
    val initial = remember(book.title) {
        book.title.trim().firstOrNull()?.toString() ?: "书"
    }
    // Offline file only — decode on IO with inSampleSize for the 56×74dp tile.
    // Loading / missing / malformed → null → deterministic gradient + initial letter.
    val density = LocalDensity.current
    val reqWidthPx = with(density) { CoverWidth.roundToPx() }
    val reqHeightPx = with(density) { CoverHeight.roundToPx() }
    var decodedCover by remember(book.coverPath, reqWidthPx, reqHeightPx) {
        mutableStateOf<Bitmap?>(null)
    }
    LaunchedEffect(book.coverPath, reqWidthPx, reqHeightPx) {
        val path = book.coverPath
        if (path == null) {
            decodedCover = null
            return@LaunchedEffect
        }
        // Reset immediately so path changes do not briefly show a stale bitmap.
        decodedCover = null
        decodedCover = withContext(Dispatchers.IO) {
            CoverBitmap.decodeFile(path, reqWidthPx, reqHeightPx)
        }
    }
    // Local snapshot for smart-cast (delegated mutableState is not smart-castable).
    val coverBitmap = decodedCover

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = description
                role = Role.Button
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        // Cover/meta → progress (bar + %) → CTA. Clear vertical stack so the
        // “开始阅读” button never sits on top of the progress track.
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(width = CoverWidth, height = CoverHeight)
                        .clip(RoundedCornerShape(8.dp))
                        .then(
                            if (coverBitmap == null) Modifier.background(coverBrush) else Modifier,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (coverBitmap != null) {
                        Image(
                            bitmap = coverBitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(
                            text = initial,
                            color = Color.White.copy(alpha = 0.95f),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = CoverHeight)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = book.author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = subtitle ?: progressLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Own row under the cover block — never squeezed into the cover column.
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = "阅读进度 $progressLabel，$percentLabel"
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Text(
                    text = percentLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.widthIn(min = 36.dp),
                )
            }

            if (showContinueReading && onContinueReading != null) {
                Spacer(Modifier.height(12.dp))
                // Material 3 tonal CTA: ≥48dp target, TalkBack label, RTL-safe trailing arrow.
                FilledTonalButton(
                    onClick = onContinueReading,
                    modifier = Modifier
                        .align(Alignment.End)
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = "$actionLabel ${book.title}" },
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                }
            }
        }
    }
}

/** Deterministic warm gradient from book id/title — offline only, no remote art. */
private fun coverGradient(seed: String): Brush {
    val palette = listOf(
        Color(0xFF8D6E63) to Color(0xFF5D4037),
        Color(0xFF6D4C41) to Color(0xFF3E2723),
        Color(0xFF78909C) to Color(0xFF455A64),
        Color(0xFF5C6BC0) to Color(0xFF3949AB),
        Color(0xFF00897B) to Color(0xFF00695C),
        Color(0xFF7E57C2) to Color(0xFF5E35B1),
        Color(0xFFEF6C00) to Color(0xFFE65100),
        Color(0xFF546E7A) to Color(0xFF37474F),
    )
    val (start, end) = palette[coverPaletteIndex(seed, palette.size)]
    return Brush.linearGradient(listOf(start, end))
}

/** Stable non-negative palette index even when [String.hashCode] is [Int.MIN_VALUE]. */
internal fun coverPaletteIndex(seed: String, paletteSize: Int): Int {
    require(paletteSize > 0) { "paletteSize must be positive" }
    return Math.floorMod(seed.hashCode(), paletteSize)
}
