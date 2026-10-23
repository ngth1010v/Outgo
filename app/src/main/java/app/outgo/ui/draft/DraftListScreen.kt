package app.outgo.ui.draft

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.key
import app.outgo.ui.history.HistoryItem
import app.outgo.ui.history.historyItemKey
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.saveable.rememberSaveable
import app.outgo.ui.component.BudgetCard
import app.outgo.ui.component.BudgetDraftChange
import app.outgo.ui.component.OutgoSegmentedButton
import app.outgo.ui.component.SavingDraftChange
import app.outgo.ui.component.SavingsCard
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import app.outgo.ui.component.SwipeableSnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.outgo.R
import app.outgo.ui.LocalAppContainer
import app.outgo.ui.component.ConfirmDialog
import app.outgo.ui.theme.OnPendingContainer
import app.outgo.ui.theme.PendingContainer
import kotlinx.coroutines.launch

/**
 * The draft list: the transactions added with "Add to draft", drawn like History (no filter).
 * A row opens its editor, its "x" removes it; at the bottom, back to adding, clear, or save them all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftListScreen(onBack: () -> Unit, onOpenDraft: (Long) -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: DraftListViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                DraftListViewModel(
                    container.draftStore,
                    container.tradeRepository,
                    container.accountRepository,
                    container.categoryRepository,
                    container.budgetRepository,
                )
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalContext.current.resources
    var askClear by remember { mutableStateOf(false) }
    var askPush by remember { mutableStateOf(false) }
    var showImpact by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is DraftListEvent.Pushed -> scope.launch {
                    snackbarHostState.showSnackbar(resources.getQuantityString(R.plurals.draft_pushed, event.count, event.count))
                }
                is DraftListEvent.Error -> scope.launch { snackbarHostState.showSnackbar(event.message) }
            }
        }
    }
    // Emptied (pushed, cleared, or the last "x"): there is no list left to show.
    LaunchedEffect(state.isLoading, state.drafts.isEmpty()) {
        if (!state.isLoading && state.drafts.isEmpty()) onBack()
    }

    val problemLabels = mapOf(
        DraftProblem.ACCOUNT to stringResource(R.string.draft_problem_account),
        DraftProblem.TO_ACCOUNT to stringResource(R.string.draft_problem_to_account),
        DraftProblem.CATEGORY to stringResource(R.string.draft_problem_category),
        DraftProblem.BUDGET to stringResource(R.string.draft_problem_budget),
    )
    val overdrawnLabel = stringResource(R.string.trade_warning_negative_balance)
    val warnings = remember(state.problems, state.overdrawn, problemLabels, overdrawnLabel) {
        state.overdrawn.mapValues { overdrawnLabel.format(it.value.name) } +
            state.problems.mapValues { problemLabels.getValue(it.value) }
    }

    Scaffold(
        snackbarHost = { SwipeableSnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.draft_list_title, state.drafts.size)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ph_caret_right),
                            contentDescription = stringResource(R.string.common_back),
                            modifier = Modifier.rotate(180f),
                        )
                    }
                },
            )
        },
    ) { padding ->
        // The buttons follow the rows: at the bottom of the screen while the rows leave room, after
        // the last row (scrolled to) when they don't.
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(horizontal = 16.dp),
            ) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    OutgoSegmentedButton(
                        selected = !showImpact,
                        onClick = { showImpact = false },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text(stringResource(R.string.draft_tab_list), maxLines = 1) }
                    OutgoSegmentedButton(
                        selected = showImpact,
                        onClick = { showImpact = true },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text(stringResource(R.string.draft_tab_impact), maxLines = 1) }
                }
                if (showImpact) {
                    ImpactContent(viewModel)
                } else {
                    if (state.invalidCount > 0) InvalidBanner(state.invalidCount)
                    state.items.forEach { item ->
                        key(historyItemKey(item)) {
                            HistoryItem(
                                item = item,
                                categoriesById = state.categoriesById,
                                accountsById = state.accountsById,
                                onOpenTrade = onOpenDraft,
                                onRemove = viewModel::remove,
                                warnings = warnings,
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f).heightIn(min = 16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.draft_continue), maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { askClear = true },
                        enabled = !state.isPushing,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.draft_clear), color = MaterialTheme.colorScheme.error, maxLines = 1)
                    }
                }
                // No spacer: each button's 48dp touch area already leaves 8dp between them, as between the two above.
                // The Outlined buttons' default height, so the three match.
                Button(
                    onClick = { if (state.invalidCount > 0) askPush = true else viewModel.push() },
                    enabled = !state.isPushing && state.validCount > 0,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.draft_push, state.validCount), fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (askClear) {
        ConfirmDialog(
            title = stringResource(R.string.draft_clear_confirm_title),
            message = pluralStringResource(R.plurals.draft_clear_confirm_message, state.drafts.size, state.drafts.size),
            confirmLabel = stringResource(R.string.draft_clear),
            onConfirm = {
                askClear = false
                viewModel.clear()
            },
            onDismiss = { askClear = false },
        )
    }

    if (askPush) {
        ConfirmDialog(
            title = stringResource(R.string.draft_push_confirm_title),
            message = pluralStringResource(R.plurals.draft_push_confirm_message, state.invalidCount, state.invalidCount, state.validCount),
            confirmLabel = stringResource(R.string.draft_push_confirm),
            onConfirm = {
                askPush = false
                viewModel.push()
            },
            onDismiss = { askPush = false },
        )
    }
}

/** "⚠ n transactions can't be saved" atop the list, like History's unfinished-transfer banner. */
@Composable
private fun InvalidBanner(count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .background(PendingContainer, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ph_warning),
            contentDescription = null,
            tint = OnPendingContainer,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            pluralStringResource(R.plurals.draft_invalid_count, count, count),
            style = MaterialTheme.typography.bodyMedium,
            color = OnPendingContainer,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * The Impact tab: this month's budgets and savings targets the drafts change, as Home's cards
 * with the figures as they'd be once the drafts are saved. The drafts' part is faded on the bar
 * and dashed on the chart (tap the bar).
 */
@Composable
private fun ImpactContent(viewModel: DraftListViewModel) {
    // Collected here only, so the budget and savings queries run while the tab is shown.
    val impact by viewModel.impact.collectAsStateWithLifecycle()
    if (impact.isLoading) return
    // Home's sections: a title, then its cards 8dp apart.
    Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (impact.budgets.isEmpty() && impact.savings.isEmpty()) {
            Text(
                stringResource(R.string.draft_impact_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }
        if (impact.budgets.isNotEmpty()) {
            Text(stringResource(R.string.draft_impact_budgets), style = MaterialTheme.typography.titleMedium)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                impact.budgets.forEach { b ->
                    key("b" + b.budget.id) {
                        BudgetCard(b.budget, BudgetDraftChange(b.spentAfter, b.limitAfter, b.addedByDay))
                    }
                }
            }
        }
        if (impact.savings.isNotEmpty()) {
            Text(stringResource(R.string.draft_impact_savings), style = MaterialTheme.typography.titleMedium)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                impact.savings.forEach { sv ->
                    key("s" + sv.row.account.id) {
                        SavingsCard(sv.row, SavingDraftChange(sv.savedAfter, sv.addedByDay))
                    }
                }
            }
        }
        if (impact.otherMonthCount > 0) {
            Text(
                pluralStringResource(R.plurals.draft_impact_other_month, impact.otherMonthCount, impact.otherMonthCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
