package app.outgo.ui.history

import app.outgo.ui.component.MoneyTextField
import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import app.outgo.ui.component.IconView
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
 * `<type> | <account> | <category>/<subcategory>`, or for transfers `<type> | <kind> | <from>/<budget> > <to>`,
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
                    groups += listOfNotNull(
                        filter.accountId?.let { accountsById[it]?.item() },
                        filter.budgetId?.let { budgetItem(it, budgets, categoriesById) },
                    )
                } else {
                    groups += listOfNotNull(filter.accountId?.let { accountsById[it]?.item() })
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
                val toAccount = filter.toAccountId?.takeIf { filter.isTransfer }?.let { accountsById[it]?.item() }
                if (toAccount != null) {
                    Icon(
                        painterResource(R.drawable.ph_arrow_right),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    FilterChip(toAccount)
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
    fun <T> options(items: List<T>, pick: (T) -> PickOption<Long?>) = listOf(PickOption<Long?>(null, any)) + items.map(pick)
    val accounts = options(accountsById.values.filter { !it.archived && !it.isParent }.sortedBy { it.sortOrder }) {
        PickOption(it.id, it.name, it.iconId, it.color)
    }
    val kind = if (filter.type == TradeType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
    fun categories(parentId: Long?) = options(
        categoriesById.values.filter { it.parentId == parentId && !it.archived && (parentId != null || it.type == kind) }.sortedBy { it.sortOrder },
    ) { PickOption(it.id, it.name, it.iconId, it.color) }

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
                    onChange(filter.copy(type = it, categoryId = null, subCategoryId = null, budgetId = null, toAccountId = null, automatic = null))
                }
            }
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
                // Categories are per type: pick Expense or Income first (adjustments have none).
                if (filter.hasCategories) {
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
            }
            DateField(stringResource(R.string.history_filter_from_date), filter.fromDay) { onChange(filter.copy(fromDay = it)) }
            DateField(stringResource(R.string.history_filter_to_date), filter.toDay) { onChange(filter.copy(toDay = it)) }
            OutlinedTextField(
                value = filter.note,
                onValueChange = { onChange(filter.copy(note = it.take(100))) },
                label = { Text(stringResource(R.string.history_filter_note)) },
                singleLine = true,
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

/** A day picked in the system date picker, shown like an [OffsetPicker] row; × clears it. */
@Composable
private fun DateField(label: String, dayStart: Long?, onChange: (Long?) -> Unit) {
    val context = LocalContext.current
    val shape = OutlinedTextFieldDefaults.shape
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable {
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
            }
            .padding(start = 12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            dayStart?.let(::formatDate) ?: stringResource(R.string.history_filter_any),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        if (dayStart == null) {
            Icon(
                painter = painterResource(R.drawable.ph_caret_down),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, end = 12.dp).size(12.dp),
            )
        } else {
            IconButton(onClick = { onChange(null) }, modifier = Modifier.size(40.dp)) {
                Icon(painterResource(R.drawable.ph_x), contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
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
    )
}
