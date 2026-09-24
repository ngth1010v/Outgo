package app.outgo.ui.home

import androidx.annotation.StringRes
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import app.outgo.ui.component.rememberSwipeLevel
import app.outgo.ui.component.savingsProgressColor
import app.outgo.ui.component.savingsProgressText
import app.outgo.ui.component.swipeShift
import app.outgo.ui.component.swipeStep
import app.outgo.ui.history.HistoryViewModel
import app.outgo.ui.history.LoadMoreOnScrollEnd
import app.outgo.ui.history.HistoryListItem
import app.outgo.ui.history.historyItemKey
import app.outgo.ui.history.historyItems
import app.outgo.ui.nav.HistoryType
import app.outgo.ui.theme.ExpenseRed
import app.outgo.ui.theme.IncomeGreen
import app.outgo.util.Money
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(visible: Boolean, onOpenTrade: (Long) -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                HomeViewModel(container.accountRepository, container.budgetRepository, container.settingRepository)
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    var historyType by rememberSaveable { mutableStateOf(HistoryType.EXPENSE) }
    // One ViewModel per tab, keyed so switching tabs keeps each tab's already-loaded pages. All
    // three exist and load, so a history swipe draws the neighbor tab's rows at once.
    val historyViewModels = HistoryType.entries.associateWith { type ->
        viewModel<HistoryViewModel>(
            key = "home-history-${type.arg}",
            factory = viewModelFactory {
                initializer {
                    HistoryViewModel(container.tradeRepository, container.accountRepository, container.categoryRepository, type)
                }
            },
        )
    }
    val historyViewModel = historyViewModels.getValue(historyType)
    // Collected only to recompose on change; the value is read from the flow itself because
    // collectAsState's holder outlives a ViewModel swap and would show the previous tab's rows
    // for a frame after a switch.
    historyViewModel.state.collectAsStateWithLifecycle().value
    val historyState = historyViewModel.state.value
    // The list is a one-shot fetch, not a Flow: re-read it whenever Home is shown again (it
    // stays composed while hidden), e.g. after adding or editing a trade.
    historyViewModels.values.forEach { vm -> LaunchedEffect(vm, visible) { vm.refresh() } }
    val historyRows = historyState.items

    val listState = rememberLazyListState()
    LoadMoreOnScrollEnd(listState, historyState.canLoadMore, historyViewModel::loadMore)

    // Switching history tabs swaps the rows instantly. A shorter new tab would shrink the list
    // under the current scroll position and LazyColumn would snap up; a tall filler after the
    // rows keeps the position, then the list eases up until the filler's top reaches the
    // viewport bottom, and the filler is removed with nothing left to jump.
    var holdScroll by remember { mutableStateOf(false) }
    fun switchHistory(type: HistoryType) {
        if (type == historyType) return
        historyType = type
        holdScroll = true
    }
    // Moves only the history rows: the header and its type tabs stay put, like tabs over a pager.
    val historySwipe = rememberSwipeLevel { next, down ->
        // Only in the history section (its header and below): Expense <-> Income <-> Transfer.
        // Above it, or past either end, the tab level takes the swipe.
        if (!listState.isInHistory(down)) return@rememberSwipeLevel null
        val type = HistoryType.entries.getOrNull(historyType.ordinal + if (next) 1 else -1)
        type?.let { { switchHistory(it) } }
    }
    // Restarts on every switch (new ViewModel) and once a first-time tab finishes loading, so it
    // only measures the new tab's real rows. Measuring earlier would aim at the wrong place.
    LaunchedEffect(historyViewModel, historyState.isLoading, holdScroll) {
        if (!holdScroll || historyState.isLoading) return@LaunchedEffect
        withFrameNanos { } // effects start once the composition is applied; this waits out its layout
        val layout = listState.layoutInfo
        layout.visibleItemsInfo.firstOrNull { it.key == HOLD_SCROLL_KEY }?.let { filler ->
            try {
                listState.animateScrollBy(
                    (filler.offset - layout.viewportEndOffset).toFloat(),
                    tween(HOLD_SCROLL_MS, easing = EaseInOut),
                )
            } catch (e: CancellationException) {
                // A newer switch restarted this effect: it owns the filler now.
                if (!isActive) throw e
                // Otherwise a finger stopped the scroll; drop the filler all the same.
            }
        }
        holdScroll = false
    }

    // Read here, not inside the LazyColumn lambda: that lambda runs lazily and would pick up a
    // new tab's prefix before the rows it goes with.
    val historyKeyPrefix = historyType.arg

    Scaffold { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().swipeStep(historySwipe).padding(horizontal = 16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(top = HISTORY_TOP_PADDING, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(HISTORY_SPACING),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.3.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BalanceBlock(
                                R.string.home_available_balance,
                                state.availableBalance,
                                primary = true,
                                visible = !state.availableBalanceHidden,
                                modifier = Modifier.weight(1f),
                            )
                            RevealToggle(
                                hidden = state.availableBalanceHidden,
                                showLabel = R.string.home_show_available_balance,
                                hideLabel = R.string.home_hide_available_balance,
                                onClick = viewModel::toggleAvailableBalance,
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                                BalanceBlock(R.string.home_savings_balance, state.savingsBalance, primary = false, visible = !state.otherBalancesHidden)
                                BalanceBlock(R.string.home_total_balance, state.totalBalance, primary = false, visible = !state.otherBalancesHidden)
                            }
                            RevealToggle(
                                hidden = state.otherBalancesHidden,
                                showLabel = R.string.home_show_savings_and_total_balance,
                                hideLabel = R.string.home_hide_savings_and_total_balance,
                                onClick = viewModel::toggleOtherBalances,
                            )
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

                item(key = HISTORY_HEADER_KEY) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(stringResource(R.string.home_history_section), style = MaterialTheme.typography.titleMedium)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            HistoryType.entries.forEachIndexed { index, type ->
                                SegmentedButton(
                                    selected = type == historyType,
                                    onClick = { switchHistory(type) },
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
                                modifier = Modifier.swipeShift(historySwipe),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    historyItems(
                        historyRows, historyState.categoriesById, historyState.accountsById, onOpenTrade,
                        rowModifier = Modifier.swipeShift(historySwipe),
                        keyPrefix = historyKeyPrefix,
                    )
                }
                if (holdScroll) {
                    item(key = HOLD_SCROLL_KEY) { Spacer(Modifier.height(HOLD_SCROLL_HEIGHT)) }
                }
            }
            // The neighbor tabs' rows, lined up with the current ones.
            if (historySwipe.moving) {
                listOf(-1, 1).forEach { page ->
                    HistoryType.entries.getOrNull(historyType.ordinal + page)?.let { type ->
                        key(type) {
                            NeighborHistory(
                                viewModel = historyViewModels.getValue(type),
                                listState = listState,
                                currentRows = historyRows,
                                currentKeyPrefix = historyKeyPrefix,
                                onOpenTrade = onOpenTrade,
                                modifier = Modifier.swipeShift(historySwipe, page),
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val HOLD_SCROLL_KEY = "history_hold_scroll"
private const val HISTORY_HEADER_KEY = "history_header"

/**
 * Whether [down] (in list coordinates) lands in the history section. With the header scrolled
 * out of view, the section fills the list iff the top row is a history row: those rows have String
 * keys, the unkeyed ones above the header do not.
 */
private fun LazyListState.isInHistory(down: Offset): Boolean {
    val info = layoutInfo
    val header = info.visibleItemsInfo.firstOrNull { it.key == HISTORY_HEADER_KEY }
        ?: return info.visibleItemsInfo.firstOrNull()?.key is String
    return down.y >= header.offset - info.viewportStartOffset
}

/**
 * Another history tab's rows over Home's list, where a switch to it would put them: under the
 * header while it shows, else at the current rows' scroll position (which the switch keeps).
 */
@Composable
private fun NeighborHistory(
    viewModel: HistoryViewModel,
    listState: LazyListState,
    currentRows: List<HistoryListItem>,
    currentKeyPrefix: String,
    onOpenTrade: (Long) -> Unit,
    modifier: Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    // Read once: the list does not scroll during a horizontal swipe.
    val (topPadding, rowsState) = remember {
        val info = listState.layoutInfo
        val header = info.visibleItemsInfo.firstOrNull { it.key == HISTORY_HEADER_KEY }
        if (header != null) {
            val top = with(density) { (header.offset + header.size - info.viewportStartOffset).toDp() } + HISTORY_SPACING
            top to LazyListState()
        } else {
            val firstKey = info.visibleItemsInfo.firstOrNull()?.key
            val index = currentRows.indexOfFirst { currentKeyPrefix + historyItemKey(it) == firstKey }.coerceAtLeast(0)
            HISTORY_TOP_PADDING to LazyListState(index, listState.firstVisibleItemScrollOffset)
        }
    }
    LazyColumn(
        state = rowsState,
        userScrollEnabled = false,
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = topPadding, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(HISTORY_SPACING),
    ) {
        if (state.items.isEmpty()) {
            if (!state.isLoading) {
                item {
                    Text(
                        stringResource(R.string.history_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            historyItems(state.items, state.categoriesById, state.accountsById, onOpenTrade)
        }
    }
}

private val HISTORY_TOP_PADDING = 48.dp
private val HISTORY_SPACING = 10.dp

/** Duration of the ease back up over the hold-scroll filler after a history tab switch. */
private const val HOLD_SCROLL_MS = 500

// ponytail: fixed height, a switch scrolled deeper than this into the history still snaps
// partway; size it from the scrolled distance if lists ever get that long.
private val HOLD_SCROLL_HEIGHT = 30_000.dp

/**
 * Eye / eye-slash button masking a balance figure. The glyph is deliberately small next to the
 * balance type, but the [IconButton] keeps its default 48dp touch target.
 */
@Composable
private fun RevealToggle(
    hidden: Boolean,
    @StringRes showLabel: Int,
    @StringRes hideLabel: Int,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(
            painterResource(if (hidden) R.drawable.ph_eye_slash else R.drawable.ph_eye),
            contentDescription = stringResource(if (hidden) showLabel else hideLabel),
            modifier = Modifier.size(REVEAL_ICON_SIZE),
            tint = LocalContentColor.current.copy(alpha = REVEAL_ICON_ALPHA),
        )
    }
}

/** 0.6x Material's 24dp default, so the toggles sit quietly beside the amounts. */
private val REVEAL_ICON_SIZE = 14.4.dp

/** Faded to 30% of the inherited content colour, so the toggles read as secondary to the amounts. */
private const val REVEAL_ICON_ALPHA = 0.3f

/**
 * Balance label + amount. Primary amount is 1.2x headlineMedium; secondary label is 80% of the
 * primary label and secondary amount 60% of the primary amount, muted.
 */
@Composable
private fun BalanceBlock(@StringRes label: Int, amount: Long, primary: Boolean, visible: Boolean = true, modifier: Modifier = Modifier) {
    val labelBase = MaterialTheme.typography.labelLarge
    val labelScale = if (primary) 1f else 0.8f
    val base = MaterialTheme.typography.headlineMedium
    val scale = if (primary) 1.2f else 1.2f * 0.6f
    // Negative gap pulls the primary amount up to tighten label-amount spacing.
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(if (primary) (-3.1).dp else 0.dp)) {
        Text(
            stringResource(label),
            style = labelBase.copy(fontSize = labelBase.fontSize * labelScale, lineHeight = labelBase.lineHeight * labelScale),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            if (visible) Money.format(amount) else Money.formatHidden(),
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
