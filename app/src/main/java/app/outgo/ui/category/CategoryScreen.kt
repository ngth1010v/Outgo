package app.outgo.ui.category

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.outgo.R
import app.outgo.data.db.dao.BudgetWithProgress
import app.outgo.data.db.entity.CategoryEntity
import app.outgo.data.repo.CategoryColorPalette
import app.outgo.domain.CategoryKind
import app.outgo.ui.LocalAppContainer
import app.outgo.ui.component.BudgetProgressBlock
import app.outgo.ui.component.ColorPickerGrid
import app.outgo.ui.component.ConfirmDialog
import app.outgo.ui.component.IconPickerSheet
import app.outgo.ui.component.IconView
import app.outgo.ui.component.PlusRow
import app.outgo.ui.component.budgetRemainingColor
import app.outgo.ui.component.budgetRemainingText
import app.outgo.util.Money

private sealed interface EditTarget {
    data object NewParent : EditTarget
    data class NewChild(val parentId: Long, val parentColor: Int) : EditTarget
    data class Edit(val category: CategoryEntity) : EditTarget
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen() {
    val container = LocalAppContainer.current
    val viewModel: CategoryViewModel = viewModel(
        factory = viewModelFactory { initializer { CategoryViewModel(container.categoryRepository, container.budgetRepository) } },
    )
    val state by viewModel.state.collectAsState()
    var expanded by remember { mutableStateOf(setOf<Long>()) }
    var editTarget by remember { mutableStateOf<EditTarget?>(null) }

    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(R.string.nav_category)) }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
            ) {
                SegmentedButton(
                    selected = state.type == CategoryKind.EXPENSE,
                    onClick = { viewModel.setType(CategoryKind.EXPENSE) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text(stringResource(R.string.category_expense_tab)) }
                SegmentedButton(
                    selected = state.type == CategoryKind.INCOME,
                    onClick = { viewModel.setType(CategoryKind.INCOME) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text(stringResource(R.string.category_income_tab)) }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.parents, key = { it.id }) { parent ->
                    val isExpanded = expanded.contains(parent.id)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CategoryRow(
                            category = parent,
                            budget = state.budgetsByCategory[parent.id],
                            onRowClick = { editTarget = EditTarget.Edit(parent) },
                            trailing = {
                                Icon(
                                    painter = painterResource(if (isExpanded) R.drawable.ph_caret_down else R.drawable.ph_caret_right),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .clickable { expanded = if (isExpanded) expanded - parent.id else expanded + parent.id }
                                        .padding(8.dp),
                                )
                            },
                        )
                        if (isExpanded) {
                            state.childrenByParent[parent.id].orEmpty().forEach { child ->
                                CategoryRow(
                                    category = child,
                                    budget = state.budgetsByCategory[child.id],
                                    onRowClick = { editTarget = EditTarget.Edit(child) },
                                    modifier = Modifier.padding(start = 20.dp),
                                )
                            }
                            PlusRow(
                                onClick = { editTarget = EditTarget.NewChild(parent.id, parent.color) },
                                modifier = Modifier
                                    .padding(start = 20.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                            )
                        }
                    }
                }
                item {
                    PlusRow(
                        onClick = { editTarget = EditTarget.NewParent },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                    )
                }
            }
        }
    }

    editTarget?.let { target ->
        EditCategorySheet(
            target = target,
            currentType = state.type,
            viewModel = viewModel,
            onDismiss = { editTarget = null },
        )
    }
}

@Composable
private fun CategoryRow(
    category: CategoryEntity,
    budget: BudgetWithProgress?,
    onRowClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp).clickable(onClick = onRowClick),
        ) {
            IconView(iconId = category.iconId, size = 28.dp, color = category.color)
            Spacer(Modifier.width(12.dp))
            Text(category.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            trailing?.invoke()
        }
        if (budget != null) {
            val limit = budget.limitAmount ?: 0L
            BudgetProgressBlock(
                remainingText = budgetRemainingText(budget.spent, limit),
                spentOfTotalText = stringResource(
                    R.string.category_spent_of_budget,
                    Money.groupThousands(budget.spent),
                    Money.groupThousands(limit),
                ),
                progress = if (limit > 0) budget.spent.toFloat() / limit.toFloat() else 0f,
                color = budgetRemainingColor(budget.spent, limit, Color(category.color)),
                modifier = Modifier.padding(start = 40.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditCategorySheet(
    target: EditTarget,
    currentType: Int,
    viewModel: CategoryViewModel,
    onDismiss: () -> Unit,
) {
    val existing = (target as? EditTarget.Edit)?.category
    val effectiveType = existing?.type ?: currentType
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var iconId by remember { mutableStateOf(existing?.iconId) }
    var color by remember {
        mutableStateOf(existing?.color ?: (target as? EditTarget.NewChild)?.parentColor ?: CategoryColorPalette[0])
    }
    var budgetText by remember { mutableStateOf("") }
    var showIconPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var hasTrades by remember { mutableStateOf(false) }
    var childCount by remember { mutableStateOf(0) }
    val defaultChildName = stringResource(R.string.category_default_child_name)

    LaunchedEffect(existing?.id) {
        if (existing != null) {
            hasTrades = viewModel.hasTrades(existing.id)
            if (existing.isParent) childCount = viewModel.childCount(existing.id)
        }
    }

    val title = when {
        existing != null -> stringResource(R.string.category_edit_title)
        target is EditTarget.NewChild -> stringResource(R.string.category_create_child_title)
        else -> stringResource(R.string.category_create_parent_title)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconView(iconId = iconId, size = 48.dp, color = color, modifier = Modifier.padding(end = 12.dp))
                OutlinedButton(onClick = { showIconPicker = true }) { Text(stringResource(R.string.common_choose_icon)) }
            }
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.category_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (effectiveType == CategoryKind.EXPENSE) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = budgetText,
                    onValueChange = { budgetText = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.category_budget_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(12.dp))

            Text(stringResource(R.string.category_color_label), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            ColorPickerGrid(selected = color, onSelect = { color = it })
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    val budget = budgetText.toLongOrNull()
                    when {
                        existing != null -> viewModel.update(existing, name, iconId, color, budget)
                        target is EditTarget.NewChild -> viewModel.createChild(target.parentId, name, iconId, color, budget)
                        else -> viewModel.createParent(name, iconId, color, budget, defaultChildName)
                    }
                    onDismiss()
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.common_save)) }

            if (existing != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showIconPicker) {
        IconPickerSheet(onIconSelected = { iconId = it; showIconPicker = false }, onDismiss = { showIconPicker = false })
    }

    if (showDeleteConfirm && existing != null) {
        val message = when {
            existing.isParent && childCount > 0 -> stringResource(R.string.category_delete_parent_confirm_message, childCount)
            hasTrades -> stringResource(R.string.category_archive_confirm_message)
            else -> stringResource(R.string.category_delete_child_confirm_message)
        }
        ConfirmDialog(
            title = stringResource(R.string.category_delete_confirm_title),
            message = message,
            onConfirm = {
                viewModel.deleteOrArchive(existing)
                showDeleteConfirm = false
                onDismiss()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}
