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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.outgo.R
import app.outgo.ui.theme.ExpenseRed
import app.outgo.ui.theme.IncomeGreen
import app.outgo.util.Money

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
        Box(modifier = Modifier.fillMaxWidth().height(9.dp).padding(top = 4.dp)) {
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

/** "<left> remain" while under budget, else "<over> over" (call site should also switch the color to red). */
@Composable
fun budgetRemainingText(spent: Long, limit: Long): String {
    val remaining = limit - spent
    return if (remaining >= 0) {
        stringResource(R.string.balance_savings_remain, Money.groupThousands(remaining))
    } else {
        stringResource(R.string.category_budget_over, Money.groupThousands(-remaining))
    }
}

/** The category's own color while under budget, forced to red once spend passes the limit. */
fun budgetRemainingColor(spent: Long, limit: Long, categoryColor: Color): Color =
    if (spent > limit) ExpenseRed else categoryColor

/** "<left> to go" while under target, "Done" on target, "Done - <over> ahead" past it. */
@Composable
fun savingsProgressText(saved: Long, target: Long): String {
    val remaining = target - saved
    val done = stringResource(R.string.balance_savings_done)
    return when {
        remaining > 0 -> stringResource(R.string.balance_savings_to_go, Money.groupThousands(remaining))
        remaining == 0L -> done
        else -> "$done - " + stringResource(R.string.balance_savings_ahead, Money.groupThousands(-remaining))
    }
}

/** Label + bar color for a savings row: green once the target is hit, else the account's own color. */
fun savingsProgressColor(saved: Long, target: Long, accountColor: Color): Color =
    if (saved >= target) IncomeGreen else accountColor
