package app.outgo.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import app.outgo.ui.theme.OnPendingContainer
import app.outgo.ui.theme.PendingContainer
import app.outgo.ui.theme.WarningDarkYellow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Color
import app.outgo.ui.component.WarningBadge
import app.outgo.ui.theme.WarningContainer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.outgo.R
import app.outgo.data.db.entity.AccountEntity
import app.outgo.data.db.entity.CategoryEntity
import app.outgo.data.db.entity.TradeEntity
import app.outgo.domain.TradeType
import app.outgo.ui.component.ExceptionBadge
import app.outgo.ui.component.IconView
import app.outgo.util.Money
import app.outgo.util.formatDayHeader
import app.outgo.util.formatTime

/** Day-grouped history list, shared by the History screen and the Home screen's inline section. */
sealed interface HistoryListItem {
    /** [type] is the day's one trade type, or [MIXED]: then [total] is what came in minus what went out. */
    data class Header(val dayLabel: String, val total: Long, val type: Int) : HistoryListItem
    data class Row(val trade: TradeEntity) : HistoryListItem

    companion object {
        const val MIXED = -1
    }
}

internal fun buildHistoryItems(trades: List<TradeEntity>): List<HistoryListItem> {
    val out = mutableListOf<HistoryListItem>()
    val days = trades.map { formatDayHeader(it.occurredAt) }
    var start = 0
    while (start < trades.size) {
        var end = start
        while (end < trades.size && days[end] == days[start]) end++
        val day = trades.subList(start, end)
        val type = day.first().type
        out += if (day.all { it.type == type }) {
            HistoryListItem.Header(days[start], day.sumOf { it.amount }, type)
        } else {
            // Transfers only move money between accounts: they leave the day's net alone.
            val net = day.sumOf {
                when {
                    it.type == TradeType.TRANSFER -> 0L
                    TradeType.isCredit(it.type) -> it.amount
                    else -> -it.amount
                }
            }
            HistoryListItem.Header(days[start], net, HistoryListItem.MIXED)
        }
        day.forEach { out += HistoryListItem.Row(it) }
        start = end
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
    /** Adjustments labelled "Opening balance" (see [HistoryUiState.openingIds]). */
    openingIds: Set<Long> = emptySet(),
    /** The draft list: an "x" on each row removes it. Null: no "x". */
    onRemove: ((Long) -> Unit)? = null,
    /** By trade id: why the row can't be saved, shown as a warning row (the draft list). */
    warnings: Map<Long, String> = emptyMap(),
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
    ) { item -> HistoryItem(item, categoriesById, accountsById, onOpenTrade, rowModifier, onRetry, openingIds, onRemove, warnings) }
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
    openingIds: Set<Long> = emptySet(),
    onRemove: ((Long) -> Unit)? = null,
    warnings: Map<Long, String> = emptyMap(),
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
                    HistoryListItem.MIXED -> Money.formatSigned(item.total)
                    else -> (if (TradeType.isCredit(item.type)) "+" else "-") + Money.format(item.total)
                } + ")",
                style = labelStyle.copy(fontSize = labelStyle.fontSize * 0.8f, lineHeight = labelStyle.lineHeight * 0.8f),
                color = when {
                    item.type == HistoryListItem.MIXED ->
                        if (item.total >= 0) app.outgo.ui.theme.IncomeGreen else app.outgo.ui.theme.ExpenseRed
                    item.type == TradeType.TRANSFER -> app.outgo.ui.theme.TransferBlue
                    TradeType.isCredit(item.type) -> app.outgo.ui.theme.IncomeGreen
                    else -> app.outgo.ui.theme.ExpenseRed
                },
            )
        }
        is HistoryListItem.Row -> {
            // An automatic transfer shows the budget it moves the unspent amount of.
            val category = categoriesById[item.trade.categoryId ?: item.trade.offsetCategoryId]
            val fromAccount = accountsById[item.trade.accountId]
            val toBudget = categoriesById[item.trade.toCategoryId]
            TradeRow(
            trade = item.trade,
            category = category,
            // Just the account's own name, a subaccount without its parent.
            fromPath = fromAccount?.name.orEmpty(),
            budgetPath = category?.let { c -> listOfNotNull(categoriesById[c.parentId]?.name, c.name).joinToString("/") },
            toBudget = toBudget,
            toBudgetPath = toBudget?.let { c -> listOfNotNull(categoriesById[c.parentId]?.name, c.name).joinToString("/") },
            fromAccount = fromAccount,
            toAccount = accountsById[item.trade.toAccountId],
            // An adjustment has no form of its own: the account's balance is where it is changed.
            onClick = if (TradeType.isAdjustment(item.trade.type)) null else { { onOpenTrade(item.trade.id) } },
            modifier = modifier,
            onRetry = onRetry,
            opening = item.trade.id in openingIds,
            onRemove = onRemove?.let { remove -> { remove(item.trade.id) } },
            warning = warnings[item.trade.id],
            )
        }
    }
}

@Composable
private fun TradeRow(
    trade: TradeEntity,
    category: CategoryEntity?,
    fromPath: String,
    budgetPath: String?,
    toBudget: CategoryEntity?,
    toBudgetPath: String?,
    fromAccount: AccountEntity?,
    toAccount: AccountEntity?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onRetry: ((TradeEntity) -> Unit)? = null,
    /** An account's opening balance, not a later adjustment (see [TradeDao.openingAdjustmentIds]). */
    opening: Boolean = false,
    /** A draft's "x": removes it from the draft list. */
    onRemove: (() -> Unit)? = null,
    /** Why the draft can't be saved: the row turns into a warning row. */
    warning: String? = null,
) {
    if (onRemove == null) {
        TradeRowContent(trade, category, fromPath, budgetPath, toBudget, toBudgetPath, fromAccount, toAccount, onClick, modifier, onRetry, opening)
        return
    }
    // A draft: a rounded row with its remove button as a darker strip at the end, full height. One
    // that needs attention is tinted and badged like Balance's and Categories' warning rows.
    val shape = RoundedCornerShape(12.dp)
    // A normal draft takes the Analysis cards' color: the background, a touch darker.
    val rowColor = if (warning != null) {
        WarningContainer
    } else {
        androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.onBackground, 0.035f)
    }
    val stripColor = if (warning != null) {
        androidx.compose.ui.graphics.lerp(WarningContainer, Color.Black, 0.08f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    Box(modifier.fillMaxWidth().padding(vertical = if (warning != null) 4.dp else 2.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clip(shape)
                .background(rowColor),
        ) {
            Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
                TradeRowContent(
                    trade, category, fromPath, budgetPath, toBudget, toBudgetPath, fromAccount, toAccount, onClick,
                    Modifier, onRetry, opening,
                )
                if (warning != null) {
                    Text(
                        warning,
                        style = MaterialTheme.typography.bodySmall,
                        color = WarningDarkYellow,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxHeight()
                    .background(stripColor)
                    .clickable(onClick = onRemove)
                    .padding(horizontal = 8.4.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ph_x),
                    contentDescription = stringResource(R.string.draft_remove),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (warning != null) WarningBadge(contentDescription = null)
    }
}

@Composable
private fun TradeRowContent(
    trade: TradeEntity,
    category: CategoryEntity?,
    fromPath: String,
    budgetPath: String?,
    toBudget: CategoryEntity?,
    toBudgetPath: String?,
    fromAccount: AccountEntity?,
    toAccount: AccountEntity?,
    onClick: (() -> Unit)?,
    modifier: Modifier,
    onRetry: ((TradeEntity) -> Unit)?,
    opening: Boolean,
) {
    val isTransfer = trade.type == TradeType.TRANSFER
    val pending = trade.pendingAmount
    Row(
        modifier = modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left out of budgets / savings targets, by the trade itself or by its subcategory/subaccount:
        // a badge on the category icon (an expense's budget, an income's account) or the "to" account.
        if (isTransfer) {
            // A transfer's category is the budget it was taken from.
            TransferIconStack(
                fromAccount = fromAccount,
                toAccount = toAccount,
                budget = category,
                toBudget = toBudget,
            )
        } else {
            val exempt = when (trade.type) {
                TradeType.EXPENSE -> trade.budgetExempt || category?.isExempt == true
                TradeType.INCOME -> trade.savingExempt || fromAccount?.isExempt == true
                else -> false
            }
            Box {
                // An adjustment has no category: it shows its account.
                val adjustment = TradeType.isAdjustment(trade.type)
                IconView(
                    iconId = if (adjustment) fromAccount?.iconId else category?.iconId,
                    size = 32.dp,
                    color = if (adjustment) fromAccount?.color else category?.color,
                )
                if (exempt) ExceptionBadge(32.dp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                (if (isTransfer) toAccount?.name else category?.name)
                    ?: stringResource(
                        when {
                            // Only a draft can name what is gone: a saved trade's category and accounts can't be deleted.
                            isTransfer -> R.string.history_deleted_account
                            !TradeType.isAdjustment(trade.type) -> R.string.history_deleted_category
                            opening -> R.string.history_opening_balance
                            else -> R.string.history_adjustment_note
                        },
                    ),
                style = MaterialTheme.typography.bodyLarge,
            )
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
            // A transfer taken from / topping up a budget names it on a line of its own, above the
            // account: "From", "From → To" or "→ To".
            val budgets = if (isTransfer && (budgetPath != null || toBudgetPath != null)) {
                listOfNotNull(budgetPath, toBudgetPath?.let { "→ $it" }).joinToString(" ")
            } else {
                null
            }
            if (budgets != null) {
                Text(budgets, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "$fromPath · " + formatTime(trade.occurredAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A yellow "⚠": some offset transfers are still waiting for money ([PendingBanner] says how many). */
@Composable
internal fun PendingBadge(count: Int, modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(R.drawable.ph_warning),
        contentDescription = pluralStringResource(R.plurals.history_pending_count, count, count),
        tint = WarningDarkYellow,
        modifier = modifier.size(16.dp),
    )
}

/** "⚠ n unfinished transfers" in a pale yellow box, atop the transfer history. */
@Composable
internal fun PendingBanner(count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
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
            pluralStringResource(R.plurals.history_pending_count, count, count),
            style = MaterialTheme.typography.bodyMedium,
            color = OnPendingContainer,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * Transfer row leading visual: from-account icon, a neutral arrow, then to-account icon. The
 * budget a transfer was taken from sits as a badge on the from-account icon's corner, the budget
 * it tops up on the to-account icon's. No exception badge here: the budget badges take that corner.
 */
@Composable
private fun TransferIconStack(
    fromAccount: AccountEntity?,
    toAccount: AccountEntity?,
    budget: CategoryEntity?,
    toBudget: CategoryEntity?,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box {
            IconView(iconId = fromAccount?.iconId, size = 28.dp, color = fromAccount?.color)
            if (budget != null) {
                // Nudged right ~20% and down ~33% of its size so it reads as a badge hanging off the corner.
                IconView(
                    iconId = budget.iconId,
                    size = 17.25.dp,
                    color = budget.color,
                    // A small shadow lifts the badge off the account icon it overlaps.
                    modifier = Modifier.align(Alignment.BottomEnd).offset(x = 3.5.dp, y = 5.75.dp).shadow(2.dp, CircleShape),
                )
            }
        }
        Icon(
            painter = painterResource(R.drawable.ph_arrow_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp).padding(horizontal = 1.dp),
        )
        Box {
            IconView(iconId = toAccount?.iconId, size = 28.dp, color = toAccount?.color)
            if (toBudget != null) {
                IconView(
                    iconId = toBudget.iconId,
                    size = 17.25.dp,
                    color = toBudget.color,
                    modifier = Modifier.align(Alignment.BottomEnd).offset(x = 3.5.dp, y = 5.75.dp).shadow(2.dp, CircleShape),
                )
            }
        }
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
