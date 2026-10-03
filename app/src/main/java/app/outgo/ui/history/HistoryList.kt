package app.outgo.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.outgo.ui.theme.OnPendingContainer
import app.outgo.ui.theme.PendingContainer
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
sealed interface HistoryListItem {
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
    categoriesById: Map<Long, CategoryEntity>,
    accountsById: Map<Long, AccountEntity>,
    onOpenTrade: (Long) -> Unit,
    rowModifier: Modifier = Modifier,
    /** Keeps keys unique per list: two tabs share day-header keys otherwise. */
    keyPrefix: String = "",
    /** Shows a Retry button on unfinished offset transfers; null hides it. */
    onRetry: ((TradeEntity) -> Unit)? = null,
) {
    items(
        items = items,
        key = { keyPrefix + historyItemKey(it) },
        // Without this every item is its own type, so Compose cannot reuse a scrolled-off row's
        // composition for the row scrolling in and rebuilds each one from scratch.
        contentType = { item ->
            when (item) {
                is HistoryListItem.Header -> "history_header"
                is HistoryListItem.Row -> "history_row"
            }
        },
    ) { item -> HistoryItem(item, categoriesById, accountsById, onOpenTrade, rowModifier, onRetry) }
}

internal fun historyItemKey(item: HistoryListItem): String = when (item) {
    is HistoryListItem.Header -> "h_${item.dayLabel}"
    is HistoryListItem.Row -> "r_${item.trade.id}"
}

/**
 * One day header or trade row; also drawn outside the list by Home's outgoing-tab overlay.
 * Takes the two lookup maps rather than the whole [HistoryUiState]: they keep their identity
 * across unrelated state updates (e.g. isLoadingMore), so visible rows skip recomposition.
 */
@Composable
internal fun HistoryItem(
    item: HistoryListItem,
    categoriesById: Map<Long, CategoryEntity>,
    accountsById: Map<Long, AccountEntity>,
    onOpenTrade: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onRetry: ((TradeEntity) -> Unit)? = null,
) {
    when (item) {
        is HistoryListItem.Header -> Row(
            modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
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
            category = categoriesById[item.trade.categoryId],
            fromAccount = accountsById[item.trade.accountId],
            toAccount = accountsById[item.trade.toAccountId],
            onClick = { onOpenTrade(item.trade.id) },
            modifier = modifier,
            onRetry = onRetry,
        )
    }
}

@Composable
private fun TradeRow(
    trade: TradeEntity,
    category: CategoryEntity?,
    fromAccount: AccountEntity?,
    toAccount: AccountEntity?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: ((TradeEntity) -> Unit)? = null,
) {
    val isTransfer = trade.type == TradeType.TRANSFER
    val pending = trade.pendingAmount
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 5.dp),
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
            // A transfer's category is the budget it was taken from.
            if (isTransfer && category != null) {
                Text(
                    stringResource(R.string.history_from_budget, category.name),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (!trade.note.isNullOrBlank()) {
                Text(trade.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            val isCredit = TradeType.isCredit(trade.type)
            Row(verticalAlignment = Alignment.CenterVertically) {
                // An unfinished transfer shows what it still has to move, not its 0.
                Text(
                    (if (isTransfer) "" else if (isCredit) "+" else "-") + Money.format(pending ?: trade.amount),
                    fontWeight = FontWeight.Bold,
                    color = when {
                        pending != null -> OnPendingContainer
                        isTransfer -> MaterialTheme.colorScheme.onSurface
                        isCredit -> app.outgo.ui.theme.IncomeGreen
                        else -> app.outgo.ui.theme.ExpenseRed
                    },
                )
                if (pending != null && onRetry != null) {
                    Icon(
                        painter = painterResource(R.drawable.ph_arrow_counter_clockwise),
                        contentDescription = stringResource(R.string.history_retry),
                        tint = OnPendingContainer,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(PendingContainer)
                            .clickable { onRetry(trade) }
                            .padding(3.dp)
                            .size(14.dp),
                    )
                }
            }
            Text(
                (fromAccount?.name ?: "") + " · " + formatTime(trade.occurredAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "⚠ n" on a pale yellow pill: how many offset transfers are still waiting for money. */
@Composable
internal fun PendingBadge(count: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.history_pending_count, count)
    Row(
        modifier = modifier
            .background(PendingContainer, RoundedCornerShape(8.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ph_warning),
            contentDescription = null,
            tint = OnPendingContainer,
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(2.dp))
        Text(count.toString(), style = MaterialTheme.typography.labelSmall, color = OnPendingContainer, fontWeight = FontWeight.Bold)
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
