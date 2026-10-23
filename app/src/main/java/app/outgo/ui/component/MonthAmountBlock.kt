package app.outgo.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.outgo.ui.theme.ExpenseRed
import app.outgo.ui.theme.IncomeGreen
import app.outgo.util.Money

/**
 * This month's amount with its deviation from last month underneath. Gray when unchanged,
 * otherwise green/red per [greenWhenLower] —
 * spending less is good for a budget, saving less is not.
 * With [draftDelta] (what unsaved drafts add, already in [current]), "(±amount)" follows the ratio.
 */
@Composable
fun MonthAmountBlock(current: Long, previous: Long, greenWhenLower: Boolean, draftDelta: Long? = null) {
    // Negative gap pulls the ratio up against the amount, same trick as BalanceBlock.
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy((-4).dp)) {
        // A budget's amount is money spent ("-", red), a savings account's is money saved ("+", green).
        val signed = if (greenWhenLower) -current else current
        Text(
            Money.formatSigned(signed),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyLarge,
            color = signedColor(signed),
        )
        // No previous month to divide by: no percent ("—"), a made-up +100% would read as a real swing.
        val percent = when {
            previous != 0L -> Math.round((current - previous) * 100.0 / previous)
            current == 0L -> 0L
            else -> null
        }
        Row {
            Text(
                text = when {
                    percent == null -> "—"
                    percent == 0L -> "~0%"
                    percent > 0 -> "+$percent%"
                    else -> "$percent%"
                },
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    percent == null || percent == 0L -> MaterialTheme.colorScheme.onSurfaceVariant
                    (percent < 0) == greenWhenLower -> IncomeGreen
                    else -> ExpenseRed
                },
            )
            if (draftDelta != null && draftDelta != 0L) {
                val signedDelta = if (greenWhenLower) -draftDelta else draftDelta
                Text(
                    "(" + Money.formatSignedNoCurrency(signedDelta) + ")",
                    style = MaterialTheme.typography.bodySmall,
                    color = signedColor(signedDelta),
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun signedColor(signed: Long) = when {
    signed > 0L -> IncomeGreen
    signed < 0L -> ExpenseRed
    else -> MaterialTheme.colorScheme.onSurface
}
