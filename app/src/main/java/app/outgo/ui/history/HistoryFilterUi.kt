package app.outgo.ui.history

import app.outgo.ui.component.MoneyTextField
import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.outgo.R
import app.outgo.data.db.dao.BudgetWithProgress
import app.outgo.data.db.entity.AccountEntity
import app.outgo.data.db.entity.CategoryEntity
import app.outgo.domain.CategoryKind
import app.outgo.domain.TradeType
import app.outgo.ui.category.OffsetPicker
import app.outgo.ui.category.PickOption
import app.outgo.ui.component.BudgetSheet
import app.outgo.ui.component.CompactOutlinedTextField
import app.outgo.ui.component.GroupedPickerSheet
import app.outgo.ui.component.IconView
import app.outgo.ui.component.PickCell
import app.outgo.ui.component.lighten
import app.outgo.util.Money
import app.outgo.util.formatDate
import java.util.Calendar

/** An account, category or budget as the filter row and sheet show it. */
private data class FilterItem(val label: String, val iconId: Long?, val color: Int?)

private fun AccountEntity.item() = FilterItem(name, iconId, color)
private fun CategoryEntity.item() = FilterItem(name, iconId, color)
private fun text(label: String) = FilterItem(label, null, null)

/** A budget by its category: from the budget list, else the category itself (a budget since removed). */
private fun budgetItem(categoryId: Long, budgets: List<BudgetWithProgress>, categoriesById: Map<Long, CategoryEntity>): FilterItem? =
    budgets.firstOrNull { it.categoryId == categoryId }?.let { FilterItem(it.displayName, it.displayIconId, it.categoryColor) }
        ?: categoriesById[categoryId]?.item()

/** A parent account, then its subaccount when one is picked. */
private fun accountItems(parentId: Long?, subId: Long?, accountsById: Map<Long, AccountEntity>): List<FilterItem> =
    listOfNotNull(parentId?.let { accountsById[it]?.item() }, subId?.let { accountsById[it]?.item() })

@Composable
private fun typeLabel(type: Int?): String = stringResource(
    when (type) {
        TradeType.EXPENSE -> R.string.trade_expense
        TradeType.INCOME -> R.string.trade_income
        TradeType.TRANSFER -> R.string.trade_transfer
        HistoryFilter.ADJUSTMENT -> R.string.history_type_adjustment
        else -> R.string.history_filter_any
    },
)

/**
 * The row atop the History list: the current filter on the left, the filter button on the right.
 * `<type> | <account>/<subaccount> | <category>/<subcategory>`, or for transfers
 * `<type> | <kind> | <from>/<subaccount>/<budget> > <to>/<subaccount>`,
 * then the dates, the note searched and the amount range.
 */
@Composable
internal fun HistoryFilterRow(
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
            } else {
                // Groups of chips, "|" between groups.
                val groups = mutableListOf<List<FilterItem>>()
                filter.type?.let { groups += listOf(text(typeLabel(it))) }
                if (filter.isTransfer) {
                    filter.automatic?.let {
                        groups += listOf(text(stringResource(if (it) R.string.history_filter_automatic else R.string.history_filter_manual)))
                    }
                    groups += accountItems(filter.accountId, filter.subAccountId, accountsById) +
                        listOfNotNull(filter.budgetId?.let { budgetItem(it, budgets, categoriesById) })
                } else {
                    groups += accountItems(filter.accountId, filter.subAccountId, accountsById)
                    groups += listOfNotNull(
                        filter.categoryId?.let { categoriesById[it]?.item() },
                        filter.subCategoryId?.let { categoriesById[it]?.item() },
                    )
                }
                val from = filter.fromDay?.let(::formatDate)
                val to = filter.toDay?.let(::formatDate)
                when {
                    from != null && to != null -> groups += listOf(text("$from – $to"))
                    from != null -> groups += listOf(text(stringResource(R.string.history_filter_since, from)))
                    to != null -> groups += listOf(text(stringResource(R.string.history_filter_until, to)))
                }
                if (filter.note.isNotBlank()) groups += listOf(text("“${filter.note.trim()}”"))
                val min = filter.minAmount?.let(Money::groupThousands)
                val max = filter.maxAmount?.let(Money::groupThousands)
                when {
                    min != null && max != null -> groups += listOf(text("$min – $max"))
                    min != null -> groups += listOf(text("≥ $min"))
                    max != null -> groups += listOf(text("≤ $max"))
                }
                val shown = groups.filter { it.isNotEmpty() }
                shown.forEachIndexed { index, group ->
                    if (index > 0) Separator("|")
                    ChipGroup(group)
                }
                val toAccount = if (filter.isTransfer) accountItems(filter.toAccountId, filter.toSubAccountId, accountsById) else emptyList()
                if (toAccount.isNotEmpty()) {
                    Icon(
                        painterResource(R.drawable.ph_arrow_right),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    ChipGroup(toAccount)
                }
            }
        }
        // 32dp, not the default 48dp: keeps the row tight above the list.
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

/** [items], joined by "/". */
@Composable
private fun ChipGroup(items: List<FilterItem>) {
    items.forEachIndexed { index, item ->
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

/** Edits the History filter; every pick applies at once. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryFilterSheet(
    filter: HistoryFilter,
    accountsById: Map<Long, AccountEntity>,
    categoriesById: Map<Long, CategoryEntity>,
    budgets: List<BudgetWithProgress>,
    onChange: (HistoryFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    val any = stringResource(R.string.history_filter_any)
    val kind = if (filter.type == TradeType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
    fun categories(parentId: Long?) = anyAnd(
        any,
        categoriesById.values.filter { it.parentId == parentId && !it.archived && (parentId != null || it.type == kind) }.sortedBy { it.sortOrder },
    ) { PickOption(it.id, it.name, it.iconId, it.color) }
    val categoryTitle = stringResource(R.string.trade_select_category_title)
    val categoryHint = stringResource(R.string.trade_search_category_hint)

    // Opened tall: the sheet has text fields, and the keyboard would cover a half-open one.
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.history_filter), style = MaterialTheme.typography.titleMedium)
            OffsetPicker(
                stringResource(R.string.history_filter_type),
                filter.type,
                listOf(null, TradeType.EXPENSE, TradeType.INCOME, TradeType.TRANSFER, HistoryFilter.ADJUSTMENT).map { PickOption(it, typeLabel(it)) },
            ) {
                // Category, budget and transfer picks belong to one type: a new type drops them.
                if (it != filter.type) {
                    onChange(
                        filter.copy(
                            type = it, categoryId = null, subCategoryId = null, budgetId = null,
                            toAccountId = null, toSubAccountId = null, automatic = null,
                        ),
                    )
                }
            }
            // Right under the type, whatever it is: a new type keeps it.
            AccountPicker(
                stringResource(if (filter.isTransfer) R.string.history_filter_from else R.string.history_filter_account),
                filter.accountId,
                filter.subAccountId,
                accountsById,
            ) { parent, sub -> onChange(filter.copy(accountId = parent, subAccountId = sub)) }
            if (filter.isTransfer) {
                OffsetPicker(
                    stringResource(R.string.history_filter_kind),
                    filter.automatic,
                    listOf(
                        PickOption<Boolean?>(null, any),
                        PickOption(true, stringResource(R.string.history_filter_automatic)),
                        PickOption(false, stringResource(R.string.history_filter_manual)),
                    ),
                ) { onChange(filter.copy(automatic = it)) }
                OffsetPicker(
                    stringResource(R.string.history_filter_budget),
                    filter.budgetId,
                    anyAnd(any, budgets.filter { it.categoryId != null }) {
                        PickOption(it.categoryId, it.displayName, it.displayIconId, it.categoryColor)
                    },
                    sheet = { dismiss ->
                        BudgetSheet(
                            budgets = budgets,
                            selectedId = filter.budgetId,
                            onSelect = { onChange(filter.copy(budgetId = it)); dismiss() },
                            onDismiss = dismiss,
                            noneLabel = any,
                        )
                    },
                ) {}
                AccountPicker(
                    stringResource(R.string.history_filter_to),
                    filter.toAccountId,
                    filter.toSubAccountId,
                    accountsById,
                ) { parent, sub -> onChange(filter.copy(toAccountId = parent, toSubAccountId = sub)) }
            } else if (filter.hasCategories) {
                // Categories are per type: pick Expense or Income first (adjustments have none).
                val parents = categories(null)
                OffsetPicker(
                    stringResource(R.string.history_filter_category),
                    filter.categoryId,
                    parents,
                    sheet = { dismiss ->
                        GridPickerSheet(categoryTitle, categoryHint, parents, filter.categoryId, section = "", dismiss) {
                            // Another parent's subcategory no longer applies.
                            if (it != filter.categoryId) onChange(filter.copy(categoryId = it, subCategoryId = null))
                        }
                    },
                ) {}
                // A subcategory needs its category.
                filter.categoryId?.let { parentId ->
                    val children = categories(parentId)
                    OffsetPicker(
                        stringResource(R.string.history_filter_subcategory),
                        filter.subCategoryId,
                        children,
                        indent = true,
                        sheet = { dismiss ->
                            GridPickerSheet(
                                categoryTitle, categoryHint, children, filter.subCategoryId,
                                section = categoriesById[parentId]?.name.orEmpty(), dismiss,
                            ) { onChange(filter.copy(subCategoryId = it)) }
                        },
                    ) {}
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField(stringResource(R.string.history_filter_from_date), filter.fromDay, Modifier.weight(1f)) {
                    onChange(filter.copy(fromDay = it))
                }
                DateField(stringResource(R.string.history_filter_to_date), filter.toDay, Modifier.weight(1f)) {
                    onChange(filter.copy(toDay = it))
                }
            }
            CompactOutlinedTextField(
                value = filter.note,
                onValueChange = { onChange(filter.copy(note = it.take(100))) },
                label = stringResource(R.string.history_filter_note),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AmountFilterField(stringResource(R.string.history_filter_min), filter.minAmount, Modifier.weight(1f)) {
                    onChange(filter.copy(minAmount = it))
                }
                AmountFilterField(stringResource(R.string.history_filter_max), filter.maxAmount, Modifier.weight(1f)) {
                    onChange(filter.copy(maxAmount = it))
                }
            }
            OutlinedButton(onClick = { onChange(HistoryFilter()) }, enabled = !filter.isEmpty, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.history_filter_clear))
            }
        }
    }
}

/** "Any", then [items] as picker options. */
private fun <T> anyAnd(any: String, items: List<T>, pick: (T) -> PickOption<Long?>): List<PickOption<Long?>> =
    listOf(PickOption<Long?>(null, any)) + items.map(pick)

/**
 * A parent account row, then, once one is picked, an indented subaccount row. A parent alone matches
 * all its subaccounts. [onChange] gets the new parent and subaccount.
 */
@Composable
private fun AccountPicker(
    label: String,
    parentId: Long?,
    subId: Long?,
    accountsById: Map<Long, AccountEntity>,
    onChange: (parent: Long?, sub: Long?) -> Unit,
) {
    val any = stringResource(R.string.history_filter_any)
    val title = stringResource(R.string.trade_select_account_title)
    val hint = stringResource(R.string.trade_search_account_hint)
    val active = accountsById.values.filter { !it.archived }.sortedBy { it.sortOrder }
    fun options(accounts: List<AccountEntity>) = anyAnd(any, accounts) { PickOption(it.id, it.name, it.iconId, it.color) }
    val parents = options(active.filter { it.isParent })
    OffsetPicker(
        label,
        parentId,
        parents,
        sheet = { dismiss ->
            // Another parent's subaccount no longer applies.
            GridPickerSheet(title, hint, parents, parentId, section = "", dismiss) { if (it != parentId) onChange(it, null) }
        },
    ) {}
    parentId?.let { parent ->
        val subs = options(active.filter { it.parentId == parent })
        OffsetPicker(
            stringResource(R.string.history_filter_subaccount),
            subId,
            subs,
            indent = true,
            sheet = { dismiss ->
                GridPickerSheet(title, hint, subs, subId, section = accountsById[parent]?.name.orEmpty(), dismiss) { onChange(parent, it) }
            },
        ) {}
    }
}

/** One cell of [GridPickerSheet]; [SECTION_ID] is its one section. */
private data class GridPick(val id: Long, val parentId: Long?, val name: String, val iconId: Long?, val color: Int)

private const val SECTION_ID = -1L

/**
 * [options] as a grid of icons, like the Trade screen's pickers: "Any" (the first option, null) as a
 * button on top, the rest in one section titled [section] (none when blank). A pick closes it.
 */
@Composable
private fun GridPickerSheet(
    title: String,
    searchHint: String,
    options: List<PickOption<Long?>>,
    selectedId: Long?,
    section: String,
    dismiss: () -> Unit,
    onSelect: (Long?) -> Unit,
) {
    val items = remember(options, section) {
        listOf(GridPick(SECTION_ID, null, section, null, 0)) +
            options.mapNotNull { o -> o.value?.let { GridPick(it, SECTION_ID, o.label, o.iconId, o.color ?: 0) } }
    }
    GroupedPickerSheet(
        title = title,
        searchHint = searchHint,
        items = items,
        parentId = { it.parentId },
        id = { it.id },
        name = { it.name },
        selectedId = selectedId,
        cell = { item, selected, onClick, modifier -> PickCell(item.name, item.iconId, item.color, selected, onClick, modifier, wrap = true) },
        onSelect = { onSelect(it.id); dismiss() },
        onDismiss = dismiss,
        header = {
            TextButton(onClick = { onSelect(null); dismiss() }) { Text(options.first().label) }
        },
    )
}

/**
 * A day picked in the system date picker, drawn like the amount fields: an outlined field with its
 * label inside; × clears it.
 */
@Composable
private fun DateField(label: String, dayStart: Long?, modifier: Modifier, onChange: (Long?) -> Unit) {
    val context = LocalContext.current
    Box(modifier) {
        CompactOutlinedTextField(
            value = dayStart?.let(::formatDate).orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = label,
            trailingIcon = {
                if (dayStart == null) {
                    Icon(painterResource(R.drawable.ph_caret_down), contentDescription = null, modifier = Modifier.size(12.dp))
                } else {
                    IconButton(onClick = { onChange(null) }) {
                        Icon(painterResource(R.drawable.ph_x), contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        // Taps open the date picker instead of focusing the field; the × (48dp wide) stays tappable.
        Box(
            Modifier
                .matchParentSize()
                .padding(end = if (dayStart == null) 0.dp else 48.dp)
                .clickable(interactionSource = null, indication = null) {
                    val cal = Calendar.getInstance().apply { dayStart?.let { timeInMillis = it } }
                    DatePickerDialog(
                        context,
                        { _, year, month, day ->
                            val picked = Calendar.getInstance().apply {
                                clear()
                                set(year, month, day)
                            }
                            onChange(picked.timeInMillis)
                        },
                        cal.get(Calendar.YEAR),
                        cal.get(Calendar.MONTH),
                        cal.get(Calendar.DAY_OF_MONTH),
                    ).show()
                },
        )
    }
}

/** An amount bound; empty means none. Keeps its own text so "4." can be typed on the way to "4.5". */
@Composable
private fun AmountFilterField(label: String, amount: Long?, modifier: Modifier, onChange: (Long?) -> Unit) {
    var text by remember { mutableStateOf(amount?.let(Money::inputText).orEmpty()) }
    val shown = if (text.isEmpty() && amount == null || Money.parseInput(text) == amount) text else amount?.let(Money::inputText).orEmpty()
    MoneyTextField(
        text = shown,
        onTextChange = {
            text = it
            onChange(it.takeIf(String::isNotEmpty)?.let(Money::parseInput))
        },
        label = label,
        modifier = modifier,
        compact = true,
    )
}
