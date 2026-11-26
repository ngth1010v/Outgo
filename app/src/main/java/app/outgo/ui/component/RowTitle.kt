package app.outgo.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow

/**
 * A tree row's name, an optional gray [label] (Savings, budget exception…) and the first line of
 * its [description] in italics, "…" when cut or when the description has more lines.
 * Under the name, the line-height padding is trimmed, halving the gap to the first gray line.
 */
@Composable
fun RowTitle(name: String, label: String?, description: String?, modifier: Modifier = Modifier) = Column(modifier) {
    val lines = description?.trim()?.lines().orEmpty()
    val desc = lines.firstOrNull()?.let { if (lines.size > 1) "${it.trimEnd()}…" else it }
    val hasSub = label != null || desc != null
    val body = MaterialTheme.typography.bodyLarge
    Text(
        name,
        style = if (hasSub) body.copy(lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.LastLineBottom)) else body,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    val small = MaterialTheme.typography.labelSmall
    val firstSmall = small.copy(lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.FirstLineTop))
    val gray = MaterialTheme.colorScheme.onSurfaceVariant
    if (label != null) Text(label, style = firstSmall, color = gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
    if (desc != null) {
        Text(
            desc,
            style = if (label == null) firstSmall else small,
            color = gray,
            fontStyle = FontStyle.Italic,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
