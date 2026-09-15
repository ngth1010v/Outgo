package app.outgo.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.outgo.domain.BudgetLevel

/** Shared "remaining / spent-of-total + progress bar" block used by Home and Category rows. */
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
            Text(remainingText, color = color, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Text(spentOfTotalText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            color = color,
            trackColor = color.copy(alpha = 0.15f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
        )
    }
}

@Composable
fun BudgetLevel.toColor(): Color = when (this) {
    BudgetLevel.OK -> app.outgo.ui.theme.IncomeGreen
    BudgetLevel.WARNING -> app.outgo.ui.theme.BudgetWarningYellow
    BudgetLevel.OVER -> app.outgo.ui.theme.ExpenseRed
}
