package app.outgo.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.outgo.R
import app.outgo.ui.analysis.AccountLegend
import app.outgo.ui.analysis.BalanceLine
import app.outgo.ui.analysis.DailyBalanceChart
import app.outgo.ui.theme.ExpenseRed
import app.outgo.ui.theme.IncomeGreen
import app.outgo.util.Money
import java.time.LocalDate

/**
 * Shared "remaining / spent-of-total + progress bar" block used by Home, Category and Balance rows.
 * A tap toggles a panel under the bar: what is left per remaining day, and this month's running
 * [current] (named [lineName], loaded by [loadDays]) against a straight dashed pace up to [total].
 */
@Composable
fun BudgetProgressBlock(
    remainingText: String,
    spentOfTotalText: String,
    progress: Float,
    color: Color,
    current: Long,
    total: Long,
    lineName: String,
    loadDays: suspend () -> List<Long>,
    /** Spending under the pace is good (budget); saving under it is not. Picks the shading colours. */
    greenWhenLower: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth().padding(top = 4.dp)) {
        Column(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(remainingText, color = color, style = MaterialTheme.typography.bodySmall)
                Text(spentOfTotalText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Fixed to the bar's own height: the caret overflows it instead of making the row taller.
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(5.dp), verticalAlignment = Alignment.CenterVertically) {
                // Two overlapping bars: a blank track behind, the colored progress on top.
                Box(modifier = Modifier.weight(1f).height(5.dp)) {
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
                Icon(
                    painterResource(if (expanded) R.drawable.ph_caret_down else R.drawable.ph_caret_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp).requiredSize(14.dp),
                )
            }
        }
        AnimatedVisibility(expanded) {
            PacePanel(color, current, total, lineName, loadDays, greenWhenLower)
        }
    }
}

@Composable
private fun PacePanel(color: Color, current: Long, total: Long, lineName: String, loadDays: suspend () -> List<Long>, greenWhenLower: Boolean) {
    val today = remember { LocalDate.now() }
    val daysInMonth = today.lengthOfMonth()
    // [current] changes whenever a trade lands in this month, so it doubles as the reload key.
    val days by produceState<List<Long>?>(null, current) { value = loadDays() }
    val perDay = (total - current).coerceAtLeast(0) / (daysInMonth - today.dayOfMonth + 1)
    // Even share of [total] from the 1st through today, minus [current]. Budget: what can still be
    // spent today; savings: what still has to be saved today to catch up with that pace.
    val pace = total * today.dayOfMonth / daysInMonth
    val todayAmount = (pace - current).coerceAtLeast(0)
    val planColor = MaterialTheme.colorScheme.outlineVariant.toArgb()
    val planName = stringResource(R.string.progress_line_plan)
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                stringResource(R.string.progress_per_day, Money.format(perDay)),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.alignByBaseline(),
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Text(
                stringResource(
                    if (greenWhenLower) R.string.progress_today_spend else R.string.progress_today_save,
                    Money.format(todayAmount),
                ),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.alignByBaseline(),
                color = color,
            )
        }
        val lines = remember(days, total, color, planColor, lineName, planName) {
            val plan = (0 until daysInMonth).map { total * it / (daysInMonth - 1).coerceAtLeast(1) }
            listOfNotNull(
                days?.let { BalanceLine(0, lineName, color.toArgb(), it) },
                BalanceLine(1, planName, planColor, plan, dashed = true),
            )
        }
        DailyBalanceChart(
            lines,
            daysInMonth,
            zero = true,
            progress = 1f,
            modifier = Modifier.fillMaxWidth().height(140.dp),
            gapColors = if (greenWhenLower) ExpenseRed to IncomeGreen else IncomeGreen to ExpenseRed,
            pad = false,
        )
        AccountLegend(lines)
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
