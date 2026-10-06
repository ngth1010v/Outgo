package app.outgo.ui.history

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.outgo.data.db.entity.TradeEntity
import app.outgo.ui.component.OutgoSegmentedButton
import app.outgo.ui.component.rememberSwipeLevel
import app.outgo.ui.component.swipeShift
import app.outgo.ui.component.swipeStep
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.outgo.R
import app.outgo.ui.LocalAppContainer
import app.outgo.ui.nav.HistoryType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    type: HistoryType,
    onBack: () -> Unit,
    onOpenTrade: (Long) -> Unit,
    dayStartMillis: Long? = null,
) {
    val container = LocalAppContainer.current
    val viewModel: HistoryViewModel = viewModel(
        key = "history-${type.arg}-${dayStartMillis ?: 0L}",
        factory = viewModelFactory {
            initializer {
                HistoryViewModel(
                    container.tradeRepository,
                    container.accountRepository,
                    container.categoryRepository,
                    type,
                    dayStartMillis,
                )
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(viewModel) { viewModel.refresh() }

    val items = state.items
    LoadMoreOnScrollEnd(listState, state.canLoadMore, viewModel::loadMore)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        when (type) {
                            HistoryType.EXPENSE -> stringResource(R.string.home_expense_history)
                            HistoryType.INCOME -> stringResource(R.string.home_income_history)
                            HistoryType.TRANSFER -> stringResource(R.string.home_transfer_history)
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ph_caret_right), contentDescription = null, modifier = Modifier.rotate(180f))
                    }
                },
            )
        },
    ) { padding ->
        if (items.isEmpty() && !state.isLoading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(R.string.history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            historyItems(items, state.categoriesById, state.accountsById, onOpenTrade)
        }
    }
}

/**
 * The History tab: Expense / Income / Transfer, swiped like the Category tab's types. Each type
 * keeps its own ViewModel and scroll position; [visible] re-reads the lists when the tab shows again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryTabScreen(visible: Boolean, onOpenTrade: (Long) -> Unit) {
    val container = LocalAppContainer.current
    var historyType by rememberSaveable { mutableStateOf(HistoryType.EXPENSE) }
    // All three exist and load, so a swipe draws the neighbor type's rows at once.
    val viewModels = HistoryType.entries.associateWith { type ->
        viewModel<HistoryViewModel>(
            key = "tab-history-${type.arg}",
            factory = viewModelFactory {
                initializer {
                    HistoryViewModel(container.tradeRepository, container.accountRepository, container.categoryRepository, type)
                }
            },
        )
    }
    // The list is a one-shot fetch, not a Flow: re-read it whenever the tab is shown again (it
    // stays composed while hidden), e.g. after adding or editing a trade.
    viewModels.values.forEach { vm -> LaunchedEffect(vm, visible) { vm.refresh() } }
    val listStates = HistoryType.entries.associateWith { rememberLazyListState() }
    val pendingTransfers by remember { container.tradeRepository.pendingCount }.collectAsStateWithLifecycle(0)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val transferVm = viewModels.getValue(HistoryType.TRANSFER)
    val onRetry: (TradeEntity) -> Unit = { trade ->
        scope.launch {
            if (container.tradeRepository.settle(trade.id)) {
                transferVm.refresh()
            } else {
                val from = transferVm.state.value.accountsById[trade.accountId]?.name.orEmpty()
                Toast.makeText(context, context.getString(R.string.history_retry_not_enough, from), Toast.LENGTH_SHORT).show()
            }
        }
    }
    // Expense <-> Income <-> Transfer; past either end the tab level takes the swipe.
    val typeSwipe = rememberSwipeLevel { next, _ ->
        HistoryType.entries.getOrNull(historyType.ordinal + if (next) 1 else -1)?.let { { historyType = it } }
    }

    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(R.string.nav_history)) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).swipeStep(typeSwipe)) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
            ) {
                HistoryType.entries.forEachIndexed { index, type ->
                    OutgoSegmentedButton(
                        selected = type == historyType,
                        onClick = { historyType = type },
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
            Box(Modifier.weight(1f)) {
                listOf(0, -1, 1).forEach { page ->
                    if (page != 0 && !typeSwipe.moving) return@forEach
                    val type = HistoryType.entries.getOrNull(historyType.ordinal + page) ?: return@forEach
                    key(type) {
                        HistoryTypeList(
                            viewModel = viewModels.getValue(type),
                            listState = listStates.getValue(type),
                            pendingTransfers = if (type == HistoryType.TRANSFER) pendingTransfers else 0,
                            onOpenTrade = onOpenTrade,
                            onRetry = onRetry,
                            modifier = Modifier.swipeShift(typeSwipe, page),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryTypeList(
    viewModel: HistoryViewModel,
    listState: LazyListState,
    /** Shows the unfinished-transfers banner atop the rows when above 0. */
    pendingTransfers: Int,
    onOpenTrade: (Long) -> Unit,
    onRetry: ((TradeEntity) -> Unit)?,
    modifier: Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LoadMoreOnScrollEnd(listState, state.canLoadMore, viewModel::loadMore)
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (pendingTransfers > 0) item { PendingBanner(pendingTransfers) }
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
            historyItems(state.items, state.categoriesById, state.accountsById, onOpenTrade, onRetry = onRetry)
        }
    }
}
