package app.outgo.ui.trade

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.outgo.R
import app.outgo.data.db.entity.AccountEntity
import app.outgo.data.db.entity.CategoryEntity
import app.outgo.domain.CategoryKind
import app.outgo.ui.LocalAppContainer
import app.outgo.ui.component.AmountField
import app.outgo.ui.component.ConfirmDialog
import app.outgo.ui.component.IconView
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
                TradeViewModel(container.categoryRepository, container.accountRepository, container.tradeRepository, editingTradeId)
            }
        },
    )
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showAllCategories by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val savedLabel = stringResource(R.string.trade_saved)
    val undoLabel = stringResource(R.string.trade_undo)

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is TradeEvent.Saved -> {
                    val result = snackbarHostState.showSnackbar(savedLabel, actionLabel = undoLabel)
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
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ExpenseIncomeToggle(type = state.type, onTypeChange = viewModel::onTypeChange)

            AmountField(
                amount = state.amount,
                onAmountChange = viewModel::onAmountChange,
                isIncome = state.type == CategoryKind.INCOME,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            SelectedCategoryChip(state.selectedCategory)

            CategoryPickerSection(
                title = stringResource(R.string.trade_recent),
                categories = state.picker.recent,
                selectedId = state.selectedCategory?.id,
                onSelect = viewModel::onCategorySelected,
                onSeeAll = { showAllCategories = true },
            )
            CategoryPickerSection(
                title = stringResource(R.string.trade_top_used),
                categories = state.picker.top,
                selectedId = state.selectedCategory?.id,
                onSelect = viewModel::onCategorySelected,
            )

            AccountDropdown(
                accounts = state.accounts,
                selectedAccount = state.selectedAccount,
                onSelect = { viewModel.onAccountSelected(it.id) },
            )

            DateTimeRow(
                occurredAt = state.occurredAt,
                onDateChange = viewModel::onDateChange,
                onTimeChange = viewModel::onTimeChange,
            )

            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::onNoteChange,
                label = { Text(stringResource(R.string.trade_note_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.isEditing) {
                TextButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(painterResource(R.drawable.ph_trash), contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text("  " + stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
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

@Composable
private fun ExpenseIncomeToggle(type: Int, onTypeChange: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = type == CategoryKind.EXPENSE,
            onClick = { onTypeChange(CategoryKind.EXPENSE) },
            shape = SegmentedButtonDefaults.itemShape(0, 2),
        ) { Text(stringResource(R.string.trade_expense)) }
        SegmentedButton(
            selected = type == CategoryKind.INCOME,
            onClick = { onTypeChange(CategoryKind.INCOME) },
            shape = SegmentedButtonDefaults.itemShape(1, 2),
        ) { Text(stringResource(R.string.trade_income)) }
    }
}

@Composable
private fun SelectedCategoryChip(category: CategoryEntity?) {
    if (category == null) return
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        IconView(iconId = category.iconId, size = 24.dp)
        Spacer(Modifier.width(8.dp))
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
private fun CategoryCell(category: CategoryEntity, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clickable(onClick = onClick).padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .then(
                    if (selected) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    } else {
                        Modifier
                    },
                )
                .padding(2.dp),
            contentAlignment = Alignment.Center,
        ) {
            IconView(iconId = category.iconId, size = 40.dp)
        }
        Text(
            category.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun AccountDropdown(accounts: List<AccountEntity>, selectedAccount: AccountEntity?, onSelect: (AccountEntity) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(stringResource(R.string.trade_account_label), style = MaterialTheme.typography.labelLarge)
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .clickable { expanded = true }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconView(iconId = selectedAccount?.iconId, size = 24.dp)
                Spacer(Modifier.width(8.dp))
                Text(selectedAccount?.name ?: "—", modifier = Modifier.weight(1f))
                Icon(painterResource(R.drawable.ph_caret_down), contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                accounts.forEach { account ->
                    DropdownMenuItem(
                        text = { Text(account.name) },
                        leadingIcon = { IconView(iconId = account.iconId, size = 24.dp) },
                        onClick = { onSelect(account); expanded = false },
                    )
                }
            }
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllCategoriesSheet(type: Int, onSelect: (CategoryEntity) -> Unit, onDismiss: () -> Unit) {
    val container = LocalAppContainer.current
    val all by container.categoryRepository.observeAllOfType(type).collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }
    val filtered = remember(all, query) {
        if (query.isBlank()) all.filter { it.parentId != null } else all.filter { it.parentId != null && it.name.contains(query, ignoreCase = true) }
    }
    val parentsById = remember(all) { all.filter { it.parentId == null }.associateBy { it.id } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
            Text(stringResource(R.string.trade_select_category_title), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.trade_search_category_hint)) },
                leadingIcon = { Icon(painterResource(R.drawable.ph_magnifying_glass), contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            LazyVerticalGrid(columns = GridCells.Fixed(4)) {
                items(filtered, key = { it.id }) { category ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onSelect(category) }.padding(8.dp),
                    ) {
                        IconView(iconId = category.iconId, size = 40.dp)
                        Text(category.name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        parentsById[category.parentId]?.let {
                            Text(it.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
