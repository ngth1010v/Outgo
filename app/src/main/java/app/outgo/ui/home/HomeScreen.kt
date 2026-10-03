package app.outgo.ui.home

import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import app.outgo.data.db.entity.TradeEntity
import app.outgo.ui.history.PendingBadge
import androidx.annotation.StringRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.lerp
import app.outgo.ui.component.rememberReorderState
import app.outgo.ui.component.reorderableItem
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.layout.layout
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
import app.outgo.ui.theme.WarningDarkYellow
import app.outgo.util.Money
import app.outgo.util.MonthKey
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
    val pendingTransfers by remember { container.tradeRepository.pendingCount }.collectAsStateWithLifecycle(0)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val onRetry: (TradeEntity) -> Unit = { trade ->
        scope.launch {
            if (container.tradeRepository.settle(trade.id)) {
                historyViewModels.getValue(HistoryType.TRANSFER).refresh()
            } else {
                val from = historyState.accountsById[trade.accountId]?.name.orEmpty()
                Toast.makeText(context, context.getString(R.string.history_retry_not_enough, from), Toast.LENGTH_SHORT).show()
            }
        }
    }

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

    // The balance rows' order while a drag works on it; the saved order's next emission replaces it.
    var balanceOrder by remember(state.order) {
        // Rows arriving or reordered from the database keep the list at the same index, not with
        // its first visible row's key: opening Home at its top must show the new first row.
        listState.requestScrollToItem(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        mutableStateOf(state.order)
    }
    val reorder = rememberReorderState(listState)
    reorder.update(
        keys = balanceOrder,
        canDrag = { true },
        isSlot = { _, _ -> true },
        onMove = { key, to ->
            val moved = key as HomeBalance
            balanceOrder = balanceOrder.filter { it != moved }.toMutableList().apply { add(to, moved) }
        },
        onDrop = { viewModel.setOrder(balanceOrder) },
    )
    var info by rememberSaveable { mutableStateOf<HomeBalance?>(null) }
    var shortfallOpen by rememberSaveable { mutableStateOf(false) }

    Scaffold { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().swipeStep(historySwipe).padding(horizontal = 16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(top = HISTORY_TOP_PADDING, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(HISTORY_SPACING),
            ) {
                items(balanceOrder, key = { it }) { balance ->
                    BalanceRow(
                        balance,
                        state,
                        // The first of the saved order, not of the one being dragged: the rows
                        // keep their size while a finger moves them, then morph after the drop.
                        large = balance == state.order.firstOrNull(),
                        onToggle = viewModel::toggleHidden,
                        onInfo = { info = it },
                        onWarning = { shortfallOpen = true },
                        // The rows are spaced by their own padding, not the list's item spacing.
                        modifier = Modifier.absorbSpacingBelow(HISTORY_SPACING)
                            .then(reorderableItem(reorder, balance))
                            .fillMaxWidth()
                            // Opaque, so a lifted row hides the rows it passes over.
                            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(12.dp)),
                    )
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 10.dp, bottom = 10.dp)) {
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
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            stringResource(
                                                when (type) {
                                                    HistoryType.EXPENSE -> R.string.trade_expense
                                                    HistoryType.INCOME -> R.string.trade_income
                                                    HistoryType.TRANSFER -> R.string.trade_transfer
                                                },
                                            ),
                                        )
                                        if (type == HistoryType.TRANSFER && pendingTransfers > 0) {
                                            PendingBadge(pendingTransfers, Modifier.padding(start = 6.dp))
                                        }
                                    }
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
                        onRetry = onRetry,
                    )
                }
                if (holdScroll) {
                    item(key = HOLD_SCROLL_KEY) { Spacer(Modifier.height(HOLD_SCROLL_HEIGHT)) }
                }
            }
            info?.let { BalanceInfoDialog(it, state, onDismiss = { info = null }) }
            if (shortfallOpen) BudgetShortfallDialog(state, onDismiss = { shortfallOpen = false })
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
 * keys, the ones above the header have none or a [HomeBalance] key.
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
 * One of Home's balance rows: the label with its (i) over the amount, and the eye at the right
 * edge, after the budgets' shortfall warning when there is one. [large] (the first row) only changes the amount's size and weight, animated, so a row
 * moved to or from the top grows or shrinks in place.
 */
@Composable
private fun BalanceRow(
    balance: HomeBalance,
    state: HomeUiState,
    large: Boolean,
    onToggle: (HomeBalance) -> Unit,
    onInfo: (HomeBalance) -> Unit,
    onWarning: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t by animateFloatAsState(
        if (large) 1f else 0f,
        tween(ROW_MORPH_MS, easing = FastOutSlowInEasing),
        label = "balance-row-size",
    )
    val amountStyle = lerp(
        MaterialTheme.typography.titleMedium.scaled(1.2f).copy(fontWeight = FontWeight.Normal),
        MaterialTheme.typography.headlineMedium.scaled(1.2f).copy(fontWeight = FontWeight.Bold),
        t,
    )
    val warn = balance == HomeBalance.BUDGETS && state.budgetShortfall > 0
    Row(modifier.padding(horizontal = ROW_PADDING_H, vertical = ROW_PADDING_V), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(lerp(AMOUNT_GAP, LARGE_AMOUNT_GAP, t))) {
            val labelBase = MaterialTheme.typography.labelLarge.scaled(1.2f)
            // Compact rows get a smaller label; the large row keeps the full size.
            BalanceLabel(balance, lerp(labelBase.scaled(0.8f), labelBase, t), onInfo)
            Text(
                if (balance in state.hidden) Money.formatHidden() else Money.format(state.amount(balance)),
                style = amountStyle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
        if (warn) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(start = 4.dp).size(SMALL_BUTTON_SIZE).clip(CircleShape).clickable(onClick = onWarning),
            ) {
                Icon(
                    painterResource(R.drawable.ph_warning),
                    contentDescription = stringResource(R.string.home_budgets_short_warning),
                    modifier = Modifier.size(18.dp),
                    tint = WarningDarkYellow,
                )
            }
        }
        RevealToggle(balance, hidden = balance in state.hidden, onClick = onToggle)
    }
}

/** Why the budgets balance shows its warning: what the budgets still need against what is available. */
@Composable
private fun BudgetShortfallDialog(state: HomeUiState, onDismiss: () -> Unit) {
    fun fig(amount: Long, b: HomeBalance) = if (b in state.hidden) Money.formatHidden() else Money.format(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ph_warning), contentDescription = null, tint = WarningDarkYellow) },
        title = { Text(stringResource(R.string.home_budgets_short_title)) },
        text = {
            Text(
                stringResource(
                    R.string.home_budgets_short_message,
                    fig(state.budgetRemaining, HomeBalance.BUDGETS),
                    fig(state.availableBalance, HomeBalance.AVAILABLE),
                    fig(state.budgetShortfall, HomeBalance.BUDGETS),
                ),
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_ok)) } },
    )
}

private fun TextStyle.scaled(factor: Float) = copy(fontSize = fontSize * factor, lineHeight = lineHeight * factor)

/** How long a balance row takes to grow or shrink when it moves to or from the top. */
private const val ROW_MORPH_MS = 350

/** Around each balance row's content; with no list spacing between them, two vertical ones part the rows. */
private val ROW_PADDING_H = 3.6.dp
private val ROW_PADDING_V = 2.4.dp

/**
 * Negative: pull the amount up under its label, into the line spacing of both texts. Tuned by
 * measuring the glyph gap on screen: ~5dp on a compact row, ~6dp on the large one. With the
 * texts at 1.2x, the compact gap is pulled in further to stay ~5dp, and the large one opened to ~12dp.
 */
private val AMOUNT_GAP = (-5.0).dp
private val LARGE_AMOUNT_GAP = (-3.2).dp

/**
 * Reports [space] less height than the content takes, so the list's item spacing after this item
 * falls on the content's own bottom instead of below it. The content still draws in full, but
 * its bottom [space] lies outside the item's bounds and does not take touches.
 */
private fun Modifier.absorbSpacingBelow(space: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, (placeable.height - space.roundToPx()).coerceAtLeast(0)) { placeable.place(0, 0) }
}

/**
 * A balance's name followed by its small (i), all one button that opens [BalanceInfoDialog]. Its
 * height is the text's own: a separate square touch box around the (i) would be taller than a
 * compact label and push the rows apart.
 */
@Composable
private fun BalanceLabel(
    balance: HomeBalance,
    style: TextStyle,
    onInfo: (HomeBalance) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(balance.label)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClickLabel = stringResource(R.string.home_balance_info, label)) { onInfo(balance) },
    ) {
        Text(label, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(
            painterResource(R.drawable.ph_info),
            contentDescription = null,
            modifier = Modifier.padding(start = 6.dp).size(SMALL_ICON_SIZE),
            tint = LocalContentColor.current.copy(alpha = SMALL_ICON_ALPHA),
        )
    }
}

/**
 * Eye / eye-slash button masking one balance figure. A 32dp target rather than Material's 48dp
 * keeps the four secondary rows compact; the glyph itself is deliberately small and faded.
 */
@Composable
private fun RevealToggle(balance: HomeBalance, hidden: Boolean, onClick: (HomeBalance) -> Unit) {
    val label = stringResource(balance.label)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.padding(start = 4.dp).size(SMALL_BUTTON_SIZE).clip(CircleShape).clickable { onClick(balance) },
    ) {
        Icon(
            painterResource(if (hidden) R.drawable.ph_eye_slash else R.drawable.ph_eye),
            contentDescription = stringResource(if (hidden) R.string.home_show_balance else R.string.home_hide_balance, label),
            modifier = Modifier.size(SMALL_ICON_SIZE),
            tint = LocalContentColor.current.copy(alpha = SMALL_ICON_ALPHA),
        )
    }
}

/** What a balance means, plus its formula filled in with the current figures (masked ones stay masked). */
@Composable
private fun BalanceInfoDialog(balance: HomeBalance, state: HomeUiState, onDismiss: () -> Unit) {
    fun fig(b: HomeBalance, amount: Long = state.amount(b)) = if (b in state.hidden) Money.formatHidden() else Money.format(amount)
    val free = fig(HomeBalance.FREE)
    val budgets = fig(HomeBalance.BUDGETS)
    val savings = fig(HomeBalance.SAVINGS)
    val available = fig(HomeBalance.AVAILABLE)
    val total = fig(HomeBalance.TOTAL)
    val (explain, formula) = when (balance) {
        HomeBalance.FREE -> R.string.home_free_info to stringResource(R.string.home_free_formula, total, savings, budgets, free)
        HomeBalance.BUDGETS -> R.string.home_budgets_info to stringResource(
            R.string.home_budgets_formula, fig(HomeBalance.BUDGETS, state.budgetRemaining), available, budgets,
        )
        HomeBalance.SAVINGS -> R.string.home_savings_info to stringResource(R.string.home_savings_formula, savings)
        HomeBalance.AVAILABLE -> R.string.home_available_info to stringResource(R.string.home_available_formula, available)
        HomeBalance.TOTAL -> R.string.home_total_info to stringResource(R.string.home_total_formula, available, savings, total)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(balance.label)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(explain))
                Text(formula, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_ok)) } },
    )
}

@get:StringRes
private val HomeBalance.label: Int
    get() = when (this) {
        HomeBalance.FREE -> R.string.home_free_balance
        HomeBalance.BUDGETS -> R.string.home_budgets_balance
        HomeBalance.SAVINGS -> R.string.home_savings_balance
        HomeBalance.AVAILABLE -> R.string.home_available_balance
        HomeBalance.TOTAL -> R.string.home_total_balance
    }

/** Touch target of the eye buttons. */
private val SMALL_BUTTON_SIZE = 28.dp

/** 0.6x Material's 24dp default, so the buttons sit quietly beside the labels and amounts. */
private val SMALL_ICON_SIZE = 14.4.dp

/** Faded to 30% of the inherited content colour, so the buttons read as secondary to the amounts. */
private const val SMALL_ICON_ALPHA = 0.3f

@Composable
private fun BudgetRow(budget: BudgetWithProgress, modifier: Modifier = Modifier) {
    val trades = LocalAppContainer.current.tradeRepository
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconView(iconId = budget.displayIconId, size = 32.dp, color = budget.categoryColor)
            Spacer(Modifier.width(12.dp))
            Text(budget.displayName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            MonthAmountBlock(budget.spent, budget.prevSpent, greenWhenLower = true)
        }
        val limit = budget.effectiveLimit
        BudgetProgressBlock(
            remainingText = budgetRemainingText(budget.spent, limit),
            spentOfTotalText = stringResource(
                R.string.category_spent_of_budget,
                Money.groupThousands(budget.spent),
                Money.groupThousands(limit),
            ),
            progress = if (limit > 0) budget.spent.toFloat() / limit.toFloat() else 0f,
            color = budgetRemainingColor(budget.spent, limit, budget.categoryColor?.let { Color(it) } ?: MaterialTheme.colorScheme.primary),
            current = budget.spent,
            total = limit,
            lineName = stringResource(R.string.progress_line_spent),
            loadDays = { budget.categoryId?.let { trades.budgetDays(it) }.orEmpty() },
            greenWhenLower = true,
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
    val trades = LocalAppContainer.current.tradeRepository
    val account = row.account
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconView(iconId = account.iconId, size = 32.dp, color = account.color)
            Spacer(Modifier.width(12.dp))
            Text(account.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            MonthAmountBlock(row.monthlyIncome, row.prevMonthlyIncome, greenWhenLower = false)
        }
        val target = account.savingsTargetIn(MonthKey.current())
        if (target != null) {
            BudgetProgressBlock(
                remainingText = savingsProgressText(row.monthlyIncome, target),
                spentOfTotalText = stringResource(
                    R.string.balance_savings_of_target,
                    Money.groupThousands(row.monthlyIncome),
                    Money.groupThousands(target),
                ),
                progress = row.monthlyIncome.toFloat() / target.toFloat(),
                color = savingsProgressColor(row.monthlyIncome, target, Color(account.color)),
                current = row.monthlyIncome,
                total = target,
                lineName = stringResource(R.string.progress_line_saved),
                loadDays = { trades.savingDaysThisMonth(account.id) },
                greenWhenLower = false,
            )
        }
    }
}
