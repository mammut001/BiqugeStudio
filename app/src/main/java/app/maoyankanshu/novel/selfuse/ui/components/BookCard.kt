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
import androidx.compose.ui.graphics.StrokeCap
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.shadow
import app.maoyankanshu.novel.selfuse.ui.theme.BookSerif
import app.maoyankanshu.novel.selfuse.ui.theme.cardContainerColor
import app.maoyankanshu.novel.selfuse.Book
import app.maoyankanshu.novel.selfuse.R
import app.maoyankanshu.novel.selfuse.ui.reader.ProgressMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Fixed offline cover tile (no network images). */
private val CoverWidth = 60.dp
private val CoverHeight = 84.dp
private val CoverShape = RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp, topEnd = 8.dp, bottomEnd = 8.dp)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookCard(
    book: Book,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    /** TalkBack hint for the long-press action; shelf shows a menu, elsewhere opens details. */
    longClickHint: String? = null,
    onContinueReading: (() -> Unit)? = null,
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
        val hint = longClickHint?.takeIf { it.isNotBlank() }
        if (onLongClick != null && hint != null) append("。" + hint)
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
    // Offline file only — decode on IO with inSampleSize for the 60×84dp tile.
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
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        colors = CardDefaults.cardColors(
            containerColor = cardContainerColor,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        // Cover/meta → one footer row (progress + % + CTA). Progress keeps its own weighted
        // slot so the CTA never sits on top of the track.
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(width = CoverWidth, height = CoverHeight)
                        .shadow(elevation = 3.dp, shape = CoverShape, clip = false)
                        .clip(CoverShape)
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
                            color = Color.White.copy(alpha = 0.92f),
                            fontSize = 26.sp,
                            fontFamily = BookSerif,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    // Spine: soft highlight + crease on the binding edge so tiles read as books.
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxHeight()
                            .width(5.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color.Black.copy(alpha = 0.18f), Color.White.copy(alpha = 0.12f)),
                                ),
                            ),
                    )
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
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = book.author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = subtitle ?: progressLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .semantics(mergeDescendants = true) {
                            contentDescription = "阅读进度 $progressLabel，$percentLabel"
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        strokeCap = StrokeCap.Butt,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                    Text(
                        text = percentLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        modifier = Modifier.widthIn(min = 34.dp),
                    )
                }

                if (showContinueReading && onContinueReading != null) {
                    Spacer(Modifier.width(14.dp))
                    // Compact tonal pill; Material still guarantees a ≥48dp touch target.
                    FilledTonalButton(
                        onClick = onContinueReading,
                        contentPadding = PaddingValues(start = 16.dp, end = 12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        modifier = Modifier
                            .heightIn(min = 36.dp)
                            .semantics { contentDescription = "$actionLabel ${book.title}" },
                    ) {
                        Text(
                            text = actionLabel,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Spacer(Modifier.size(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Deterministic cover gradient from book id/title — offline only, no remote art.
 * Muted, ink-like tones (cloth-bound editions) instead of saturated material colors.
 */
private fun coverGradient(seed: String): Brush {
    val palette = listOf(
        Color(0xFF8A5A44) to Color(0xFF5E3A2B), // clay
        Color(0xFF3F4E63) to Color(0xFF263244), // ink blue
        Color(0xFF55664F) to Color(0xFF344231), // moss
        Color(0xFF7A4B57) to Color(0xFF4F2D37), // plum wine
        Color(0xFF3E5E5C) to Color(0xFF24403E), // teal slate
        Color(0xFF9A7443) to Color(0xFF6B4D26), // ochre
        Color(0xFF5B5470) to Color(0xFF39334B), // dusk
        Color(0xFF6B6259) to Color(0xFF443D36), // stone
    )
    val (start, end) = palette[coverPaletteIndex(seed, palette.size)]
    return Brush.linearGradient(listOf(start, end))
}

/** Stable non-negative palette index even when [String.hashCode] is [Int.MIN_VALUE]. */
internal fun coverPaletteIndex(seed: String, paletteSize: Int): Int {
    require(paletteSize > 0) { "paletteSize must be positive" }
    return Math.floorMod(seed.hashCode(), paletteSize)
}
