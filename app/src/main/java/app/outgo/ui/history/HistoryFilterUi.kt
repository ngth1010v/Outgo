package app.outgo.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.outgo.R
import app.outgo.data.db.dao.BudgetWithProgress
import app.outgo.data.db.entity.AccountEntity
import app.outgo.data.db.entity.CategoryEntity
import app.outgo.ui.category.OffsetPicker
import app.outgo.ui.category.PickOption
import app.outgo.ui.component.IconView
import app.outgo.ui.component.lighten
import app.outgo.ui.nav.HistoryType

/** An account, category or budget as the filter row and sheet show it. */
private data class FilterItem(val label: String, val iconId: Long?, val color: Int?)

private fun AccountEntity.item() = FilterItem(name, iconId, color)
private fun CategoryEntity.item() = FilterItem(name, iconId, color)

/** A budget by its category: from the budget list, else the category itself (a budget since removed). */
private fun budgetItem(categoryId: Long, budgets: List<BudgetWithProgress>, categoriesById: Map<Long, CategoryEntity>): FilterItem? =
    budgets.firstOrNull { it.categoryId == categoryId }?.let { FilterItem(it.displayName, it.displayIconId, it.categoryColor) }
        ?: categoriesById[categoryId]?.item()

/**
 * The row atop a History list: the current filter on the left, the filter button on the right.
 * Expense/Income: `<account> | <category>/<subcategory>`. Transfer: `<kind> | <from>/<budget> > <to>`.
 */
@Composable
internal fun HistoryFilterRow(
    type: HistoryType,
    filter: HistoryFilter,
    accountsById: Map<Long, AccountEntity>,
    categoriesById: Map<Long, CategoryEntity>,
    budgets: List<BudgetWithProgress>,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (filter.isEmpty) {
                Text(
                    stringResource(R.string.history_filter_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (type == HistoryType.TRANSFER) {
                val kind = filter.automatic?.let {
                    FilterItem(stringResource(if (it) R.string.history_filter_automatic else R.string.history_filter_manual), null, null)
                }
                val from = filter.accountId?.let { accountsById[it]?.item() }
                val budget = filter.budgetId?.let { budgetItem(it, budgets, categoriesById) }
                val to = filter.toAccountId?.let { accountsById[it]?.item() }
                ChipGroup(listOf(kind))
                if (kind != null && (from != null || budget != null || to != null)) Separator("|")
                ChipGroup(listOf(from, budget))
                if (to != null) {
                    Icon(
                        painterResource(R.drawable.ph_arrow_right),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    FilterChip(to)
                }
            } else {
                val account = filter.accountId?.let { accountsById[it]?.item() }
                val category = filter.categoryId?.let { categoriesById[it]?.item() }
                val sub = filter.subCategoryId?.let { categoriesById[it]?.item() }
                ChipGroup(listOf(account))
                if (account != null && category != null) Separator("|")
                ChipGroup(listOf(category, sub))
            }
        }
        // 32dp, not the default 48dp: keeps the row tight between the type buttons and the list.
        IconButton(onClick = onOpen, modifier = Modifier.size(32.dp)) {
            Icon(
                painterResource(R.drawable.ph_funnel_simple),
                contentDescription = stringResource(R.string.history_filter),
                tint = if (filter.isEmpty) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** The set ones of [items], joined by "/". */
@Composable
private fun ChipGroup(items: List<FilterItem?>) {
    items.filterNotNull().forEachIndexed { index, item ->
        if (index > 0) Separator("/")
        FilterChip(item)
    }
}

@Composable
private fun Separator(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Icon + name on one background: the item's color lightened, as behind its icon. */
@Composable
private fun FilterChip(item: FilterItem) {
    val hasIcon = item.iconId != null || item.color != null
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(item.color?.let { Color(it).lighten() } ?: MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
            .padding(start = if (hasIcon) 3.dp else 10.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
    ) {
        if (hasIcon) {
            IconView(iconId = item.iconId, size = 20.dp, color = item.color)
            Spacer(Modifier.width(5.dp))
        }
        Text(item.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
    }
}

/** Edits one type's filter; every pick applies at once. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryFilterSheet(
    type: HistoryType,
    filter: HistoryFilter,
    accountsById: Map<Long, AccountEntity>,
    categoriesById: Map<Long, CategoryEntity>,
    budgets: List<BudgetWithProgress>,
    onChange: (HistoryFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    val any = stringResource(R.string.history_filter_any)
    fun <T> options(items: List<T>, pick: (T) -> PickOption<Long?>) = listOf(PickOption<Long?>(null, any)) + items.map(pick)
    val accounts = options(accountsById.values.filter { !it.archived }.sortedBy { it.sortOrder }) {
        PickOption(it.id, it.name, it.iconId, it.color)
    }
    fun categories(parentId: Long?) = options(
        categoriesById.values.filter { it.parentId == parentId && !it.archived }.sortedBy { it.sortOrder },
    ) { PickOption(it.id, it.name, it.iconId, it.color) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.history_filter), style = MaterialTheme.typography.titleMedium)
            if (type == HistoryType.TRANSFER) {
                OffsetPicker(
                    stringResource(R.string.history_filter_kind),
                    filter.automatic,
                    listOf(
                        PickOption<Boolean?>(null, any),
                        PickOption(true, stringResource(R.string.history_filter_automatic)),
                        PickOption(false, stringResource(R.string.history_filter_manual)),
                    ),
                ) { onChange(filter.copy(automatic = it)) }
                OffsetPicker(stringResource(R.string.history_filter_from), filter.accountId, accounts) {
                    onChange(filter.copy(accountId = it))
                }
                OffsetPicker(
                    stringResource(R.string.history_filter_budget),
                    filter.budgetId,
                    options(budgets.filter { it.categoryId != null }) {
                        PickOption(it.categoryId, it.displayName, it.displayIconId, it.categoryColor)
                    },
                ) { onChange(filter.copy(budgetId = it)) }
                OffsetPicker(stringResource(R.string.history_filter_to), filter.toAccountId, accounts) {
                    onChange(filter.copy(toAccountId = it))
                }
            } else {
                OffsetPicker(stringResource(R.string.history_filter_account), filter.accountId, accounts) {
                    onChange(filter.copy(accountId = it))
                }
                OffsetPicker(stringResource(R.string.history_filter_category), filter.categoryId, categories(null)) {
                    // Another parent's subcategory no longer applies.
                    if (it != filter.categoryId) onChange(filter.copy(categoryId = it, subCategoryId = null))
                }
                // A subcategory needs its category.
                filter.categoryId?.let { parentId ->
                    OffsetPicker(
                        stringResource(R.string.history_filter_subcategory),
                        filter.subCategoryId,
                        categories(parentId),
                        indent = true,
                    ) { onChange(filter.copy(subCategoryId = it)) }
                }
            }
            OutlinedButton(onClick = { onChange(HistoryFilter()) }, enabled = !filter.isEmpty, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.history_filter_clear))
            }
        }
    }
}
