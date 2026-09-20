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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import app.outgo.ui.history.HistoryViewModel
import app.outgo.ui.history.LoadMoreOnScrollEnd
import app.outgo.ui.history.buildHistoryItems
import app.outgo.ui.history.historyItems
import app.outgo.ui.nav.HistoryType
import app.outgo.ui.theme.ExpenseRed
import app.outgo.ui.theme.IncomeGreen
import app.outgo.util.Money

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenTrade: (Long) -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer { HomeViewModel(container.accountRepository, container.budgetRepository) }
        },
    )
    val state by viewModel.state.collectAsState()

    var historyType by rememberSaveable { mutableStateOf(HistoryType.EXPENSE) }
    // One ViewModel per tab, keyed so switching tabs keeps each tab's already-loaded pages.
    val historyViewModel: HistoryViewModel = viewModel(
        key = "home-history-${historyType.arg}",
        factory = viewModelFactory {
            initializer {
                HistoryViewModel(container.tradeRepository, container.accountRepository, container.categoryRepository, historyType)
            }
        },
    )
    val historyState by historyViewModel.state.collectAsState()
    // The list is a one-shot fetch, not a Flow: re-read it when we come back from editing a trade.
    LaunchedEffect(historyViewModel) { historyViewModel.refresh() }
    val historyRows = remember(historyState.trades) { buildHistoryItems(historyState.trades) }

    val listState = rememberLazyListState()
    LoadMoreOnScrollEnd(listState, historyState.canLoadMore, historyViewModel::loadMore)

    Scaffold { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.3.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                    BalanceBlock(R.string.home_available_balance, state.availableBalance, primary = true)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        BalanceBlock(R.string.home_savings_balance, state.savingsBalance, primary = false)
                        BalanceBlock(R.string.home_total_balance, state.totalBalance, primary = false)
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
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
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
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
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(stringResource(R.string.home_history_section), style = MaterialTheme.typography.titleMedium)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        HistoryType.entries.forEachIndexed { index, type ->
                            SegmentedButton(
                                selected = type == historyType,
                                onClick = { historyType = type },
                                shape = SegmentedButtonDefaults.itemShape(index, HistoryType.entries.size),
                            ) {
                                Text(
                                    stringResource(
                                        when (type) {
                                            HistoryType.EXPENSE -> R.string.trade_expense
                                            HistoryType.INCOME -> R.string.trade_income
                                            HistoryType.TRANSFER -> R.string.trade_transfer
                                        },
                                    ),
                                )
                            }
                        }
                    }
                }
            }

            if (historyRows.isEmpty()) {
                if (!historyState.isLoading) {
                    item {
                        Text(
                            stringResource(R.string.history_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                historyItems(historyRows, historyState, onOpenTrade)
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
            MonthAmountBlock(budget.spent, budget.prevSpent, greenWhenLower = true)
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

/**
 * This month's amount with its deviation from last month underneath. Gray when unchanged,
 * otherwise green/red per [greenWhenLower] —
 * spending less is good for a budget, saving less is not.
 */
@Composable
private fun MonthAmountBlock(current: Long, previous: Long, greenWhenLower: Boolean) {
    // Negative gap pulls the ratio up against the amount, same trick as BalanceBlock.
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy((-4).dp)) {
        Text(Money.format(current), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
        // No previous month to divide by: any amount at all counts as a full 100% swing.
        val percent = when {
            previous != 0L -> Math.round((current - previous) * 100.0 / previous)
            current > 0L -> 100L
            current < 0L -> -100L
            else -> 0L
        }
        Text(
            text = when {
                percent == 0L -> "~0%"
                percent > 0 -> "+$percent%"
                else -> "$percent%"
            },
            style = MaterialTheme.typography.bodySmall,
            color = when {
                percent == 0L -> MaterialTheme.colorScheme.onSurfaceVariant
                (percent < 0) == greenWhenLower -> IncomeGreen
                else -> ExpenseRed
            },
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
            MonthAmountBlock(row.monthlyIncome, row.prevMonthlyIncome, greenWhenLower = false)
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
