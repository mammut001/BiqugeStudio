package app.maoyankanshu.novel.selfuse.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Shared empty-state block with optional primary / secondary CTAs for TalkBack-friendly copy.
 */
@Composable
fun EmptyState(
    title: String,
    body: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    primaryLabel: String? = null,
    primaryDescription: String? = null,
    onPrimary: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    secondaryDescription: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        // Merge only the descriptive copy. Keeping the action buttons outside this merged
        // semantics node makes each CTA independently focusable/clickable in TalkBack.
        Column(
            modifier = Modifier.semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
            },
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onPrimary != null && primaryLabel != null) {
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .semantics {
                        this.contentDescription = primaryDescription ?: primaryLabel
                    },
            ) {
                Text(primaryLabel)
            }
        }
        if (onSecondary != null && secondaryLabel != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .semantics {
                        this.contentDescription = secondaryDescription ?: secondaryLabel
                    },
            ) {
                Text(secondaryLabel)
            }
        }
    }
}
