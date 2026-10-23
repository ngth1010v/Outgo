package app.outgo.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.outgo.R
import app.outgo.data.db.dao.AccountWithProgress
import app.outgo.data.db.dao.BudgetWithProgress
import app.outgo.ui.LocalAppContainer
import app.outgo.ui.theme.WarningContainer
import app.outgo.util.Money

/**
 * What unsaved drafts would do to a budget this month: its spend and limit (after "To budget"
 * top-ups) once they're saved, and their spend run up by day, day 1 to today.
 */
@Immutable
data class BudgetDraftChange(val spentAfter: Long, val limitAfter: Long, val addedByDay: List<Long>)

/** What unsaved drafts would add to a savings account this month, as [BudgetDraftChange]. */
@Immutable
data class SavingDraftChange(val savedAfter: Long, val addedByDay: List<Long>)

/**
 * A budget's card, as Home lists them: amber with an "Over budget" badge once overspent. With
 * [draft] (the draft list's Impact tab) everything shows as it would once the drafts are saved.
 */
@Composable
fun BudgetCard(budget: BudgetWithProgress, draft: BudgetDraftChange? = null) {
    val over = (draft?.spentAfter ?: budget.spent) > (draft?.limitAfter ?: budget.effectiveLimit)
    Box {
        Card(colors = if (over) CardDefaults.cardColors(containerColor = WarningContainer) else CardDefaults.cardColors()) {
            BudgetRow(budget, modifier = Modifier.padding(12.dp), draft = draft)
        }
        if (over) WarningBadge(stringResource(R.string.category_over_budget_warning))
    }
}

/** A savings account's card, as Home lists them; [draft] as in [BudgetCard]. */
@Composable
fun SavingsCard(row: AccountWithProgress, draft: SavingDraftChange? = null) {
    Card { SavingsAccountRow(row, modifier = Modifier.padding(12.dp), draft = draft) }
}

/**
 * Name, this month's spend against last month's, and the progress bar. With [draft]: the figures
 * after the drafts, "(±amount)" beside the ratio, the drafts' part faded on the bar and dashed on
 * the chart.
 */
@Composable
fun BudgetRow(budget: BudgetWithProgress, modifier: Modifier = Modifier, draft: BudgetDraftChange? = null) {
    val trades = LocalAppContainer.current.tradeRepository
    val before = budget.spent
    val spent = draft?.spentAfter ?: before
    val limit = draft?.limitAfter ?: budget.effectiveLimit
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconView(iconId = budget.displayIconId, size = 32.dp, color = budget.categoryColor)
            Spacer(Modifier.width(12.dp))
            Text(budget.displayName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            MonthAmountBlock(spent, budget.prevSpent, greenWhenLower = true, draftDelta = draft?.let { spent - before })
        }
        BudgetProgressBlock(
            remainingText = budgetRemainingText(spent, limit),
            spentOfTotalText = stringResource(
                R.string.category_spent_of_budget,
                Money.groupThousands(spent),
                Money.groupThousands(limit),
            ),
            progress = share(before, limit, empty = 0f),
            color = budgetRemainingColor(spent, limit),
            current = before,
            total = limit,
            lineName = stringResource(R.string.progress_line_spent),
            loadDays = { budget.categoryId?.let { trades.budgetDays(it) }.orEmpty() },
            greenWhenLower = true,
            draft = draft?.let {
                DraftOverlay(share(spent, limit, empty = 0f), spent, it.addedByDay, stringResource(R.string.progress_line_with_drafts))
            },
        )
    }
}

/** A savings account's row, like [BudgetRow]; the bar only while it has a target this month. */
@Composable
fun SavingsAccountRow(row: AccountWithProgress, modifier: Modifier = Modifier, draft: SavingDraftChange? = null) {
    val trades = LocalAppContainer.current.tradeRepository
    val account = row.account
    val before = row.monthlyIncome
    val saved = draft?.savedAfter ?: before
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconView(iconId = account.iconId, size = 32.dp, color = account.color)
            Spacer(Modifier.width(12.dp))
            Text(account.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            MonthAmountBlock(saved, row.prevMonthlyIncome, greenWhenLower = false, draftDelta = draft?.let { saved - before })
        }
        val target = row.monthlyTarget
        if (target != null) {
            BudgetProgressBlock(
                remainingText = savingsProgressText(saved, target),
                spentOfTotalText = stringResource(
                    R.string.balance_savings_of_target,
                    Money.groupThousands(saved),
                    Money.groupThousands(target),
                ),
                // A summed target can be 0 (no subaccount has one): nothing to save is done.
                progress = share(before, target, empty = 1f),
                color = savingsProgressColor(saved, target, Color(account.color)),
                current = before,
                total = target,
                lineName = stringResource(R.string.progress_line_saved),
                loadDays = { trades.savingDaysThisMonth(account.id) },
                greenWhenLower = false,
                draft = draft?.let {
                    DraftOverlay(share(saved, target, empty = 1f), saved, it.addedByDay, stringResource(R.string.progress_line_with_drafts))
                },
            )
        }
    }
}

/** [part] of [whole]; [empty] when there is no whole to divide by. */
private fun share(part: Long, whole: Long, empty: Float): Float = if (whole > 0) part.toFloat() / whole.toFloat() else empty
