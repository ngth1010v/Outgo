package app.outgo.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.outgo.R
import app.outgo.data.db.entity.AccountEntity
import app.outgo.data.db.entity.CategoryEntity
import app.outgo.data.db.entity.TradeEntity
import app.outgo.domain.TradeType
import app.outgo.ui.component.IconView
import app.outgo.util.Money
import app.outgo.util.formatDayHeader
import app.outgo.util.formatTime

/** Day-grouped history list, shared by the History screen and the Home screen's inline section. */
internal sealed interface HistoryListItem {
    data class Header(val dayLabel: String, val total: Long, val type: Int) : HistoryListItem
    data class Row(val trade: TradeEntity) : HistoryListItem
}

internal fun buildHistoryItems(trades: List<TradeEntity>): List<HistoryListItem> {
    val out = mutableListOf<HistoryListItem>()
    var headerIndex = -1
    var lastDay: String? = null
    for (trade in trades) {
        val day = formatDayHeader(trade.occurredAt)
        if (day != lastDay) {
            headerIndex = out.size
            out += HistoryListItem.Header(day, 0L, trade.type)
            lastDay = day
        }
        // The day total is only known once the day is fully consumed, so accumulate into the header in place.
        val header = out[headerIndex] as HistoryListItem.Header
        out[headerIndex] = header.copy(total = header.total + trade.amount)
        out += HistoryListItem.Row(trade)
    }
    return out
}

/**
 * Emits the list into an existing [LazyListScope] rather than owning its own LazyColumn, so the
 * Home screen can inline it under its other sections and still get LazyColumn's windowing
 * (only visible rows are composed) over one scroll container.
 */
internal fun LazyListScope.historyItems(
    items: List<HistoryListItem>,
    state: HistoryUiState,
    onOpenTrade: (Long) -> Unit,
) {
    items(items, key = { item ->
        when (item) {
            is HistoryListItem.Header -> "h_${item.dayLabel}"
            is HistoryListItem.Row -> "r_${item.trade.id}"
        }
    }) { item ->
        when (item) {
            is HistoryListItem.Header -> Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    item.dayLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                val labelStyle = MaterialTheme.typography.labelLarge
                Text(
                    text = "(" + when (item.type) {
                        TradeType.TRANSFER -> Money.format(item.total)
                        else -> (if (TradeType.isCredit(item.type)) "+" else "-") + Money.format(item.total)
                    } + ")",
                    style = labelStyle.copy(fontSize = labelStyle.fontSize * 0.8f, lineHeight = labelStyle.lineHeight * 0.8f),
                    color = when {
                        item.type == TradeType.TRANSFER -> app.outgo.ui.theme.TransferBlue
                        TradeType.isCredit(item.type) -> app.outgo.ui.theme.IncomeGreen
                        else -> app.outgo.ui.theme.ExpenseRed
                    },
                )
            }
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
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 5.dp),
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

/** Fetches the next keyset page once the scroll reaches within 10 items of the end of the list. */
@Composable
internal fun LoadMoreOnScrollEnd(
    listState: androidx.compose.foundation.lazy.LazyListState,
    canLoadMore: Boolean,
    loadMore: () -> Unit,
) {
    val shouldLoadMore by androidx.compose.runtime.remember(listState) {
        androidx.compose.runtime.derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= info.totalItemsCount - 10
        }
    }
    androidx.compose.runtime.LaunchedEffect(shouldLoadMore, canLoadMore) {
        if (shouldLoadMore && canLoadMore) loadMore()
    }
}
