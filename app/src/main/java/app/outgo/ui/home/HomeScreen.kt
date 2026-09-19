package app.outgo.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.outgo.R
import app.outgo.data.db.dao.AccountWithProgress
import app.outgo.data.db.dao.BudgetWithProgress
import app.outgo.ui.LocalAppContainer
import app.outgo.ui.component.BudgetProgressBlock
import app.outgo.ui.component.IconView
import app.outgo.ui.component.budgetRemainingColor
import app.outgo.ui.component.budgetRemainingText
import app.outgo.ui.component.savingsProgressColor
import app.outgo.ui.component.savingsProgressText
import app.outgo.ui.nav.HistoryType
import app.outgo.util.Money

@Composable
fun HomeScreen(onOpenHistory: (HistoryType) -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer { HomeViewModel(container.accountRepository, container.budgetRepository) }
        },
    )
    val state by viewModel.state.collectAsState()

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.3.dp)) {
                    BalanceBlock(R.string.home_available_balance, state.availableBalance, primary = true)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        BalanceBlock(R.string.home_savings_balance, state.savingsBalance, primary = false)
                        BalanceBlock(R.string.home_total_balance, state.totalBalance, primary = false)
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.home_budgets), style = MaterialTheme.typography.titleMedium)
                    if (state.budgets.isEmpty()) {
                        Text(
                            stringResource(R.string.home_no_budgets),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.budgets.forEach { budget ->
                                Card { BudgetRow(budget, modifier = Modifier.padding(12.dp)) }
                            }
                        }
                    }
                }
            }

            if (state.savingsAccounts.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.home_savings), style = MaterialTheme.typography.titleMedium)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.savingsAccounts.forEach { account ->
                                Card { SavingsAccountRow(account, modifier = Modifier.padding(12.dp)) }
                            }
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.home_history_section), style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { onOpenHistory(HistoryType.EXPENSE) }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.trade_expense))
                        }
                        OutlinedButton(onClick = { onOpenHistory(HistoryType.INCOME) }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.trade_income))
                        }
                        OutlinedButton(onClick = { onOpenHistory(HistoryType.TRANSFER) }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.trade_transfer))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Balance label + amount. Primary amount is 1.2x headlineMedium; secondary label is 80% of the
 * primary label and secondary amount 60% of the primary amount, muted.
 */
@Composable
private fun BalanceBlock(@StringRes label: Int, amount: Long, primary: Boolean) {
    val labelBase = MaterialTheme.typography.labelLarge
    val labelScale = if (primary) 1f else 0.8f
    val base = MaterialTheme.typography.headlineMedium
    val scale = if (primary) 1.2f else 1.2f * 0.6f
    // Negative gap pulls the primary amount up to tighten label-amount spacing.
    Column(verticalArrangement = Arrangement.spacedBy(if (primary) (-3.1).dp else 0.dp)) {
        Text(
            stringResource(label),
            style = labelBase.copy(fontSize = labelBase.fontSize * labelScale, lineHeight = labelBase.lineHeight * labelScale),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            Money.format(amount),
            style = base.copy(fontSize = base.fontSize * scale, lineHeight = base.lineHeight * scale),
            fontWeight = if (primary) FontWeight.Bold else FontWeight.Normal,
            color = if (primary) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BudgetRow(budget: BudgetWithProgress, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconView(iconId = budget.displayIconId, size = 32.dp, color = budget.categoryColor)
            Spacer(Modifier.width(12.dp))
            Text(budget.displayName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        }
        val limit = budget.limitAmount ?: 0L
        BudgetProgressBlock(
            remainingText = budgetRemainingText(budget.spent, limit),
            spentOfTotalText = stringResource(
                R.string.category_spent_of_budget,
                Money.groupThousands(budget.spent),
                Money.groupThousands(limit),
            ),
            progress = if (limit > 0) budget.spent.toFloat() / limit.toFloat() else 0f,
            color = budgetRemainingColor(budget.spent, limit, budget.categoryColor?.let { Color(it) } ?: MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun SavingsAccountRow(row: AccountWithProgress, modifier: Modifier = Modifier) {
    val account = row.account
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconView(iconId = account.iconId, size = 32.dp, color = account.color)
            Spacer(Modifier.width(12.dp))
            Text(account.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(Money.format(account.balance), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
        }
        val target = account.savingsTarget
        if (target != null && target > 0) {
            BudgetProgressBlock(
                remainingText = savingsProgressText(row.monthlyIncome, target),
                spentOfTotalText = stringResource(
                    R.string.balance_savings_of_target,
                    Money.groupThousands(row.monthlyIncome),
                    Money.groupThousands(target),
                ),
                progress = row.monthlyIncome.toFloat() / target.toFloat(),
                color = savingsProgressColor(row.monthlyIncome, target, Color(account.color)),
            )
        }
    }
}
