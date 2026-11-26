package app.outgo.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow

/**
 * A tree row's name and the first line of its [description] in gray, "…" when cut or when
 * the description has more lines. Under the name, the line-height padding is trimmed, halving the
 * gap to the description.
 */
@Composable
fun RowTitle(name: String, description: String?, modifier: Modifier = Modifier) = Column(modifier) {
    val lines = description?.trim()?.lines().orEmpty()
    val desc = lines.firstOrNull()?.let { if (lines.size > 1) "${it.trimEnd()}…" else it }
    val body = MaterialTheme.typography.bodyLarge
    Text(
        name,
        style = if (desc != null) body.copy(lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.LastLineBottom)) else body,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    if (desc != null) {
        Text(
            desc,
            style = MaterialTheme.typography.labelSmall.copy(lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.FirstLineTop)),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
