package app.outgo.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.outgo.R
import app.outgo.data.db.dao.BudgetWithProgress
import app.outgo.domain.BudgetKind
import app.outgo.domain.budgetLevel
import app.outgo.ui.LocalAppContainer
import app.outgo.ui.component.BudgetProgressBlock
import app.outgo.ui.component.IconPickerSheet
import app.outgo.ui.component.IconView
import app.outgo.ui.component.PlusRow
import app.outgo.ui.component.toColor
import app.outgo.ui.nav.HistoryType
import app.outgo.util.Money
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(onOpenHistory: (HistoryType) -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer { HomeViewModel(container.accountRepository, container.budgetRepository, container.statDao) }
        },
    )
    val state by viewModel.state.collectAsState()
    var showAddGoal by remember { mutableStateOf(false) }

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Column {
                    Text(
                        stringResource(R.string.home_total_balance),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(Money.format(state.totalBalance), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
            }

            item {
                Text(stringResource(R.string.home_budgets), style = MaterialTheme.typography.titleMedium)
            }

            if (state.budgets.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.home_no_budgets),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(state.budgets, key = { it.id }) { budget ->
                    Card { BudgetRow(budget, modifier = Modifier.padding(12.dp)) }
                }
            }

            item { PlusRow(onClick = { showAddGoal = true }, modifier = Modifier.fillMaxWidth()) }

            item {
                Column {
                    Text(stringResource(R.string.home_last_5_months), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    StackedDivergingBarChart(totals = state.monthlyTotals, months = state.months)
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { onOpenHistory(HistoryType.EXPENSE) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.home_expense_history))
                    }
                    OutlinedButton(onClick = { onOpenHistory(HistoryType.INCOME) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.home_income_history))
                    }
                }
            }
        }
    }

    if (showAddGoal) {
        CreateSavingGoalSheet(onDismiss = { showAddGoal = false })
    }
}

@Composable
private fun BudgetRow(budget: BudgetWithProgress, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconView(iconId = budget.displayIconId, size = 32.dp)
            Spacer(Modifier.width(12.dp))
            Text(budget.displayName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        }
        if (budget.kind == BudgetKind.LIMIT) {
            val limit = budget.limitAmount ?: 0L
            val level = budgetLevel(budget.spent, limit)
            BudgetProgressBlock(
                remainingText = Money.formatSigned(limit - budget.spent),
                spentOfTotalText = stringResource(R.string.category_spent_of_budget, Money.format(budget.spent), Money.format(limit)),
                progress = if (limit > 0) budget.spent.toFloat() / limit.toFloat() else 0f,
                color = level.toColor(),
            )
        } else {
            val target = budget.targetAmount ?: 0L
            BudgetProgressBlock(
                remainingText = Money.format((target - budget.saved).coerceAtLeast(0)),
                spentOfTotalText = stringResource(R.string.category_spent_of_budget, Money.format(budget.saved), Money.format(target)),
                progress = if (target > 0) budget.saved.toFloat() / target.toFloat() else 0f,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateSavingGoalSheet(onDismiss: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val accounts by container.accountRepository.observeActive().collectAsState(initial = emptyList())

    var name by remember { mutableStateOf("") }
    var targetText by remember { mutableStateOf("") }
    var accountId by remember { mutableStateOf<Long?>(null) }
    var iconId by remember { mutableStateOf<Long?>(null) }
    var showIconPicker by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.home_add_saving_goal), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconView(iconId = iconId, size = 48.dp, modifier = Modifier.padding(end = 12.dp))
                OutlinedButton(onClick = { showIconPicker = true }) { Text(stringResource(R.string.common_choose_icon)) }
            }
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.category_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = targetText,
                onValueChange = { targetText = it.filter { c -> c.isDigit() } },
                label = { Text(stringResource(R.string.balance_current_balance_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            Text(stringResource(R.string.trade_account_label), style = MaterialTheme.typography.labelLarge)
            LazyColumn(modifier = Modifier.height((accounts.size.coerceAtMost(4) * 48).dp)) {
                items(accounts, key = { it.id }) { account ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(selected = accountId == account.id, onClick = { accountId = account.id })
                        IconView(iconId = account.iconId, size = 24.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(account.name)
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            val target = targetText.toLongOrNull() ?: 0L
            val valid = name.isNotBlank() && target > 0 && accountId != null
            androidx.compose.material3.Button(
                onClick = {
                    scope.launch {
                        container.budgetRepository.createSavingGoal(name, iconId, accountId!!, target, null)
                        onDismiss()
                    }
                },
                enabled = valid,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.common_save)) }
        }
    }

    if (showIconPicker) {
        IconPickerSheet(onIconSelected = { iconId = it; showIconPicker = false }, onDismiss = { showIconPicker = false })
    }
}
