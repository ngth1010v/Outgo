package app.outgo.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.outgo.domain.BudgetLevel

/** Shared "remaining / spent-of-total + progress bar" block used by Home, Category and Balance rows. */
@Composable
fun BudgetProgressBlock(
    remainingText: String,
    spentOfTotalText: String,
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(remainingText, color = color, style = MaterialTheme.typography.bodySmall)
            Text(spentOfTotalText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // Two overlapping bars: a blank track behind, the colored progress on top.
        Box(modifier = Modifier.fillMaxWidth().height(6.dp).padding(top = 4.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(color),
            )
        }
    }
}

@Composable
fun BudgetLevel.toColor(): Color = when (this) {
    BudgetLevel.OK -> app.outgo.ui.theme.IncomeGreen
    BudgetLevel.WARNING -> app.outgo.ui.theme.BudgetWarningYellow
    BudgetLevel.OVER -> app.outgo.ui.theme.ExpenseRed
}
