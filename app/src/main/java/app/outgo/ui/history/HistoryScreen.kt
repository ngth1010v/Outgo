package app.outgo.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.outgo.R
import app.outgo.data.db.entity.AccountEntity
import app.outgo.data.db.entity.CategoryEntity
import app.outgo.data.db.entity.TradeEntity
import app.outgo.domain.TradeType
import app.outgo.ui.LocalAppContainer
import app.outgo.ui.component.IconView
import app.outgo.ui.nav.HistoryType
import app.outgo.util.Money
import app.outgo.util.formatDayHeader
import app.outgo.util.formatTime

private sealed interface HistoryListItem {
    data class Header(val dayLabel: String) : HistoryListItem
    data class Row(val trade: TradeEntity) : HistoryListItem
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(type: HistoryType, onBack: () -> Unit, onOpenTrade: (Long) -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: HistoryViewModel = viewModel(
        key = "history-${type.arg}",
        factory = viewModelFactory {
            initializer {
                HistoryViewModel(container.tradeRepository, container.accountRepository, container.categoryRepository, type)
            }
        },
    )
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(viewModel) { viewModel.refresh() }

    val items = remember(state.trades) {
        val out = mutableListOf<HistoryListItem>()
        var lastDay: String? = null
        for (trade in state.trades) {
            val day = formatDayHeader(trade.occurredAt)
            if (day != lastDay) {
                out += HistoryListItem.Header(day)
                lastDay = day
            }
            out += HistoryListItem.Row(trade)
        }
        out
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= items.size - 10
        }
    }
    LaunchedEffect(shouldLoadMore, state.canLoadMore) {
        if (shouldLoadMore && state.canLoadMore) viewModel.loadMore()
    }

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
            items(items, key = { item ->
                when (item) {
                    is HistoryListItem.Header -> "h_${item.dayLabel}"
                    is HistoryListItem.Row -> "r_${item.trade.id}"
                }
            }) { item ->
                when (item) {
                    is HistoryListItem.Header -> Text(
                        item.dayLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                    is HistoryListItem.Row -> TradeRow(
                        trade = item.trade,
                        category = state.categoriesById[item.trade.categoryId],
                        fromAccount = state.accountsById[item.trade.accountId],
                        toAccount = state.accountsById[item.trade.toAccountId],
                        onClick = { onOpenTrade(item.trade.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TradeRow(
    trade: TradeEntity,
    category: CategoryEntity?,
    fromAccount: AccountEntity?,
    toAccount: AccountEntity?,
    onClick: () -> Unit,
) {
    val isTransfer = trade.type == TradeType.TRANSFER
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isTransfer) {
            TransferIconStack(fromAccount = fromAccount, toAccount = toAccount)
        } else {
            IconView(iconId = category?.iconId, size = 32.dp, color = category?.color)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                (if (isTransfer) toAccount?.name else category?.name) ?: stringResource(R.string.history_adjustment_note),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (!trade.note.isNullOrBlank()) {
                Text(trade.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            val isCredit = TradeType.isCredit(trade.type)
            Text(
                (if (isTransfer) "" else if (isCredit) "+" else "-") + Money.format(trade.amount),
                fontWeight = FontWeight.Bold,
                color = when {
                    isTransfer -> MaterialTheme.colorScheme.onSurface
                    isCredit -> app.outgo.ui.theme.IncomeGreen
                    else -> app.outgo.ui.theme.ExpenseRed
                },
            )
            Text(
                (fromAccount?.name ?: "") + " · " + formatTime(trade.occurredAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Transfer row leading visual: from-account icon, a neutral arrow, then to-account icon. */
@Composable
private fun TransferIconStack(fromAccount: AccountEntity?, toAccount: AccountEntity?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconView(iconId = fromAccount?.iconId, size = 28.dp, color = fromAccount?.color)
        Icon(
            painter = painterResource(R.drawable.ph_arrow_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp).padding(horizontal = 1.dp),
        )
        IconView(iconId = toAccount?.iconId, size = 28.dp, color = toAccount?.color)
    }
}
