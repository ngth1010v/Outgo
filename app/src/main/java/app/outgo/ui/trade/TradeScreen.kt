package app.outgo.ui.trade

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.outgo.R
import app.outgo.data.db.dao.BudgetWithProgress
import app.outgo.data.db.entity.AccountEntity
import app.outgo.data.db.entity.CategoryEntity
import app.outgo.domain.CategoryKind
import app.outgo.domain.TradeType
import app.outgo.ui.LocalAppContainer
import app.outgo.ui.component.rememberDiscardGuard
import app.outgo.ui.component.OutgoSegmentedButton
import app.outgo.ui.component.AmountField
import app.outgo.ui.component.ConfirmDialog
import app.outgo.ui.component.AccountSheet
import app.outgo.ui.component.BudgetSheet
import app.outgo.ui.component.GroupedPickerSheet
import app.outgo.ui.component.IconView
import app.outgo.ui.component.PickCell
import app.outgo.ui.component.rememberSwipeLevel
import app.outgo.ui.component.swipeNeighbor
import app.outgo.ui.component.swipeShift
import app.outgo.ui.component.swipeStep
import app.outgo.util.formatDate
import app.outgo.util.formatTime
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeScreen(editingTradeId: Long?, onClose: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: TradeViewModel = viewModel(
        key = "trade-$editingTradeId",
        factory = viewModelFactory {
            initializer {
                TradeViewModel(container.categoryRepository, container.accountRepository, container.tradeRepository, container.budgetRepository, editingTradeId)
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showAllCategories by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // Expense <-> Income <-> Transfer; past either end the tab level takes the swipe.
    val typeSwipe = rememberSwipeLevel { next, _ ->
        val type = TRADE_TYPES.getOrNull(TRADE_TYPES.indexOf(state.type) + if (next) 1 else -1)
        if (state.isEditing || type == null) null else { { viewModel.onTypeChange(type) } }
    }
    // Editing only: the loaded trade's fields, to tell whether leaving would drop changes.
    val fields = listOf(state.type, state.amount, state.selectedCategory?.id, state.selectedAccountId, state.selectedToAccountId, state.occurredAt, state.note)
    var initialFields by remember { mutableStateOf<List<Any?>?>(null) }
    LaunchedEffect(state.isLoading) { if (!state.isLoading && initialFields == null) initialFields = fields }
    val cancel = if (state.isEditing) rememberDiscardGuard(initialFields != null && fields != initialFields, onClose) else onClose
    val savedLabel = stringResource(R.string.trade_saved)
    val undoLabel = stringResource(R.string.trade_undo)

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is TradeEvent.Saved -> {
                    val result = snackbarHostState.showSnackbar(savedLabel, actionLabel = undoLabel, duration = SnackbarDuration.Short)
                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                        viewModel.undo(event.tradeId)
                    }
                }
                TradeEvent.SavedAndClose, TradeEvent.DeletedAndClose -> onClose()
                is TradeEvent.Error -> scope.launch { snackbarHostState.showSnackbar(event.message) }
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .swipeStep(typeSwipe)
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 36.dp, end = 16.dp, bottom = 16.dp),
        ) {
            ExpenseIncomeToggle(type = state.type, onTypeChange = viewModel::onTypeChange, enabled = !state.isEditing)
            Box {
                @Composable
                fun form(shown: TradeUiState, modifier: Modifier) = TradeForm(
                    state = shown,
                    viewModel = viewModel,
                    onSeeAll = { showAllCategories = true },
                    onClose = cancel,
                    onDelete = { showDeleteConfirm = true },
                    modifier = modifier,
                )
                form(state, Modifier.swipeShift(typeSwipe))
                // The neighbor types' forms, as the type switch would show them.
                if (typeSwipe.moving) {
                    listOf(-1, 1).forEach { page ->
                        TRADE_TYPES.getOrNull(TRADE_TYPES.indexOf(state.type) + page)?.let { type ->
                            form(
                                state.copy(type = type, selectedCategory = null, selectedParentCategory = null),
                                Modifier.swipeNeighbor().swipeShift(typeSwipe, page),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAllCategories) {
        AllCategoriesSheet(
            type = state.type,
            onSelect = {
                viewModel.onCategorySelected(it)
                showAllCategories = false
            },
            onDismiss = { showAllCategories = false },
        )
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.history_delete_confirm_title),
            message = "",
            onConfirm = {
                showDeleteConfirm = false
                viewModel.deleteEditingTrade()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

private val TRADE_TYPES = listOf(CategoryKind.EXPENSE, CategoryKind.INCOME, TradeType.TRANSFER)

/** Everything below the type toggle; drawn once per type while a type swipe moves. */
@Composable
private fun TradeForm(
    state: TradeUiState,
    viewModel: TradeViewModel,
    onSeeAll: () -> Unit,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Spacer(Modifier.height(16.dp))

        // An automatic transfer shows its fields but takes no input: a layer on top eats the taps.
        Box {
            Column {
                AmountField(
                    amount = state.amount,
                    onAmountChange = viewModel::onAmountChange,
                    isIncome = state.type == CategoryKind.INCOME,
                    isTransfer = state.isTransfer,
                    modifier = Modifier.padding(vertical = 8.dp),
                )

                if (!state.isTransfer) {
                    SelectedCategoryChip(state.selectedParentCategory, state.selectedCategory)
                    Spacer(Modifier.height(8.dp))

                    CategoryPickerSection(
                        title = stringResource(R.string.trade_recent),
                        categories = state.picker.recent,
                        selectedId = state.selectedCategory?.id,
                        onSelect = viewModel::onCategorySelected,
                        onSeeAll = onSeeAll,
                    )
                    Spacer(Modifier.height(16.dp))
                    CategoryPickerSection(
                        title = stringResource(R.string.trade_top_used),
                        categories = state.picker.top,
                        selectedId = state.selectedCategory?.id,
                        onSelect = viewModel::onCategorySelected,
                    )
                    Spacer(Modifier.height(16.dp))
                }

                AccountDropdown(
                    label = if (state.isTransfer) stringResource(R.string.trade_from_account_label) else stringResource(R.string.trade_account_label),
                    accounts = state.accounts,
                    selectedAccount = state.selectedAccount,
                    onSelect = { viewModel.onAccountSelected(it.id) },
                )
                Spacer(Modifier.height(16.dp))

                if (state.isTransfer) {
                    BudgetDropdown(
                        budgets = state.budgetOptions,
                        selected = state.selectedCategory,
                        onSelect = viewModel::onBudgetSelected,
                    )
                    Spacer(Modifier.height(16.dp))

                    AccountDropdown(
                        label = stringResource(R.string.trade_to_account_label),
                        accounts = state.accounts,
                        selectedAccount = state.selectedToAccount,
                        onSelect = { viewModel.onToAccountSelected(it.id) },
                    )
                    Spacer(Modifier.height(16.dp))
                }

                DateTimeRow(
                    occurredAt = state.occurredAt,
                    onDateChange = viewModel::onDateChange,
                    onTimeChange = viewModel::onTimeChange,
                )
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = state.note,
                    onValueChange = viewModel::onNoteChange,
                    label = { Text(stringResource(R.string.trade_note_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.locked) Box(Modifier.matchParentSize().pointerInput(Unit) { detectTapGestures { } })
        }
        Spacer(Modifier.height(16.dp))

        if (state.locked) {
            Text(
                stringResource(R.string.trade_auto_transfer_locked),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp))
                    .padding(12.dp),
            )
            Spacer(Modifier.height(16.dp))
            TextButton(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp)),
            ) {
                Text(stringResource(R.string.common_close))
            }
            return@Column
        }

        if (state.isEditing) {
            TextButton(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp)),
            ) {
                Text(stringResource(R.string.common_cancel))
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onDelete,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(20.dp)),
            ) {
                Icon(painterResource(R.drawable.ph_trash), contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Text("  " + stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(16.dp))
        }

        androidx.compose.material3.Button(
            onClick = viewModel::save,
            enabled = state.isValid && !state.isSaving,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(stringResource(R.string.trade_save), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ExpenseIncomeToggle(type: Int, onTypeChange: (Int) -> Unit, enabled: Boolean = true) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        OutgoSegmentedButton(
            selected = type == CategoryKind.EXPENSE,
            onClick = { onTypeChange(CategoryKind.EXPENSE) },
            enabled = enabled,
            shape = SegmentedButtonDefaults.itemShape(0, 3),
        ) { Text(stringResource(R.string.trade_expense)) }
        OutgoSegmentedButton(
            selected = type == CategoryKind.INCOME,
            onClick = { onTypeChange(CategoryKind.INCOME) },
            enabled = enabled,
            shape = SegmentedButtonDefaults.itemShape(1, 3),
        ) { Text(stringResource(R.string.trade_income)) }
        OutgoSegmentedButton(
            selected = type == TradeType.TRANSFER,
            onClick = { onTypeChange(TradeType.TRANSFER) },
            enabled = enabled,
            shape = SegmentedButtonDefaults.itemShape(2, 3),
        ) { Text(stringResource(R.string.trade_transfer)) }
    }
}

@Composable
private fun SelectedCategoryChip(parent: CategoryEntity?, category: CategoryEntity?) {
    if (category == null) return
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        if (parent != null) {
            IconView(iconId = parent.iconId, size = 20.dp, color = parent.color)
            Spacer(Modifier.width(6.dp))
            Text(parent.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(6.dp))
            Text("/", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
        }
        IconView(iconId = category.iconId, size = 20.dp, color = category.color)
        Spacer(Modifier.width(6.dp))
        Text(category.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CategoryPickerSection(
    title: String,
    categories: List<CategoryEntity>,
    selectedId: Long?,
    onSelect: (CategoryEntity) -> Unit,
    onSeeAll: (() -> Unit)? = null,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            if (onSeeAll != null) {
                TextButton(onClick = onSeeAll) { Text(stringResource(R.string.trade_all_categories) + " ›") }
            }
        }
        if (categories.isEmpty()) {
            Text(
                stringResource(R.string.trade_no_categories),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                categories.take(5).forEach { category ->
                    CategoryCell(
                        category = category,
                        selected = category.id == selectedId,
                        onClick = { onSelect(category) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(5 - categories.size.coerceAtMost(5)) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CategoryCell(category: CategoryEntity, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) =
    PickCell(category.name, category.iconId, category.color, selected, onClick, modifier)

/** The account field: tapping it opens [AccountSheet]. A subaccount named unlike its parent shows as "parent / sub". */
@Composable
private fun AccountDropdown(
    accounts: List<AccountEntity>,
    selectedAccount: AccountEntity?,
    onSelect: (AccountEntity) -> Unit,
    label: String = stringResource(R.string.trade_account_label),
) {
    var open by remember { mutableStateOf(false) }
    val parent = accounts.find { it.id == selectedAccount?.parentId }
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .clickable { open = true }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconView(iconId = selectedAccount?.iconId, size = 24.dp, color = selectedAccount?.color)
            Spacer(Modifier.width(8.dp))
            val name = selectedAccount?.name ?: "—"
            Text(if (parent == null || parent.name == name) name else "${parent.name} / $name", modifier = Modifier.weight(1f))
            Icon(painterResource(R.drawable.ph_caret_down), contentDescription = null)
        }
    }
    if (open) {
        AccountSheet(
            accounts = accounts,
            selectedId = selectedAccount?.id,
            onSelect = { onSelect(it); open = false },
            onDismiss = { open = false },
        )
    }
}

/**
 * A transfer's optional "From budget", styled like [AccountDropdown]: tapping it opens [BudgetSheet].
 * [selected] is the transfer's category; a budget removed since still shows by that category's name.
 */
@Composable
private fun BudgetDropdown(
    budgets: List<BudgetWithProgress>,
    selected: CategoryEntity?,
    onSelect: (Long?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val budget = budgets.find { it.categoryId == selected?.id }
    Column {
        Text(stringResource(R.string.trade_from_budget_label), style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .clickable { open = true }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected != null) {
                IconView(iconId = budget?.displayIconId ?: selected.iconId, size = 24.dp, color = budget?.categoryColor ?: selected.color)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                budget?.displayName ?: selected?.name ?: stringResource(R.string.trade_no_budget),
                color = if (selected == null) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                modifier = Modifier.weight(1f),
            )
            Icon(painterResource(R.drawable.ph_caret_down), contentDescription = null)
        }
    }
    if (open) {
        BudgetSheet(
            budgets = budgets,
            selectedId = selected?.id,
            onSelect = { onSelect(it); open = false },
            onDismiss = { open = false },
        )
    }
}

@Composable
private fun DateTimeRow(occurredAt: Long, onDateChange: (Long) -> Unit, onTimeChange: (Int, Int) -> Unit) {
    val context = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        OutlineBox(
            text = formatDate(occurredAt),
            icon = R.drawable.ph_calendar_blank,
            modifier = Modifier.weight(1f),
            onClick = {
                val cal = Calendar.getInstance().apply { timeInMillis = occurredAt }
                DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        val picked = Calendar.getInstance().apply { set(year, month, day, 0, 0, 0) }
                        onDateChange(picked.timeInMillis)
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH),
                ).show()
            },
        )
        OutlineBox(
            text = formatTime(occurredAt),
            icon = R.drawable.ph_clock,
            modifier = Modifier.weight(1f),
            onClick = {
                val cal = Calendar.getInstance().apply { timeInMillis = occurredAt }
                TimePickerDialog(
                    context,
                    { _, hour, minute -> onTimeChange(hour, minute) },
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE),
                    true,
                ).show()
            },
        )
    }
}

@Composable
private fun OutlineBox(text: String, icon: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null)
        Text(text)
    }
}

@Composable
private fun AllCategoriesSheet(type: Int, onSelect: (CategoryEntity) -> Unit, onDismiss: () -> Unit) {
    val container = LocalAppContainer.current
    // Remembered: a fresh Flow per recomposition would re-run the query.
    val all by remember(type) { container.categoryRepository.observeAllOfType(type) }.collectAsStateWithLifecycle(initialValue = emptyList())
    GroupedPickerSheet(
        title = stringResource(R.string.trade_select_category_title),
        searchHint = stringResource(R.string.trade_search_category_hint),
        items = all,
        parentId = { it.parentId },
        id = { it.id },
        name = { it.name },
        selectedId = null,
        cell = { item, selected, onClick, modifier -> CategoryCell(item, selected, onClick, modifier) },
        onSelect = onSelect,
        onDismiss = onDismiss,
    )
}
