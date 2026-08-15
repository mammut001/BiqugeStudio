package app.maoyankanshu.novel.selfuse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.maoyankanshu.novel.selfuse.R
import app.maoyankanshu.novel.selfuse.ReadingStats

@Composable
internal fun ReadingHabitInsights(
    entries: List<ReadingStats.DayEntry>,
    weeklyGoalMillis: Long,
    onWeeklyGoalChanged: (Long) -> Unit,
) {
    val summary = remember(entries) { ReadingInsights.summary(entries) }
    val heatmapEntries = remember(entries) { ReadingInsights.heatmapEntries(entries) }
    val goalHours = (weeklyGoalMillis / (60L * 60L * 1000L)).coerceAtLeast(1L).toInt()
    val goalFraction = if (weeklyGoalMillis <= 0L) 0f else
        (summary.currentWeekMillis.toFloat() / weeklyGoalMillis.toFloat()).coerceIn(0f, 1f)
    val goalProgress = stringResource(
        R.string.discover_weekly_goal_progress,
        ReadingStats.formatDuration(summary.currentWeekMillis),
        goalHours,
    )
    val heatmapCd = stringResource(
        R.string.discover_heatmap_cd,
        summary.currentStreakDays,
        summary.longestStreakDays,
    )
    var showGoalDialog by remember { mutableStateOf(false) }

    if (showGoalDialog) {
        AlertDialog(
            onDismissRequest = { showGoalDialog = false },
            title = { Text(stringResource(R.string.discover_weekly_goal_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 3, 5, 7).forEach { hours ->
                        OutlinedButton(
                            onClick = {
                                onWeeklyGoalChanged(hours * 60L * 60L * 1000L)
                                showGoalDialog = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.discover_weekly_goal_option, hours))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGoalDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.discover_weekly_goal),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = goalProgress,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { showGoalDialog = true }) {
                Text(stringResource(R.string.discover_weekly_goal_adjust))
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(goalFraction)
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }

        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            InsightValue(
                label = stringResource(R.string.discover_current_streak),
                value = stringResource(R.string.discover_stat_days, summary.currentStreakDays),
                modifier = Modifier.weight(1f),
            )
            InsightValue(
                label = stringResource(R.string.discover_longest_streak),
                value = stringResource(R.string.discover_stat_days, summary.longestStreakDays),
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.discover_heatmap_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.discover_heatmap_period),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
            )
        }
        Spacer(Modifier.height(8.dp))
        ReadingHeatmap(
            entries = heatmapEntries,
            contentDescription = heatmapCd,
        )
        Spacer(Modifier.height(6.dp))
        HeatmapLegend()
    }
}

@Composable
private fun InsightValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReadingHeatmap(
    entries: List<ReadingStats.DayEntry>,
    contentDescription: String,
) {
    val weeks = remember(entries) { entries.chunked(7) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { this.contentDescription = contentDescription },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        weeks.forEach { week ->
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                week.forEach { entry ->
                    val level = ReadingInsights.heatLevel(entry.millis)
                    val color = when (level) {
                        1 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                        2 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                        3 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.70f)
                        4 -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(2.dp))
                            .background(color),
                    )
                }
                repeat((7 - week.size).coerceAtLeast(0)) {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeatmapLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.discover_heatmap_less),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
        )
        Spacer(Modifier.padding(horizontal = 3.dp))
        (0..4).forEach { level ->
            val color = when (level) {
                1 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                2 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                3 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.70f)
                4 -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f)
            }
            Box(
                modifier = Modifier
                    .padding(horizontal = 1.dp)
                    .height(10.dp)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
        Spacer(Modifier.padding(horizontal = 3.dp))
        Text(
            text = stringResource(R.string.discover_heatmap_more),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
        )
    }
}
