package app.outgo.ui.category

import app.outgo.util.MonthKey
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Switch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.imePadding
import androidx.activity.ComponentActivity
import app.outgo.data.db.entity.AccountEntity
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.outgo.R
import app.outgo.data.db.dao.BudgetWithProgress
import app.outgo.data.db.entity.CategoryEntity
import app.outgo.data.repo.BudgetSetting
import app.outgo.data.repo.DefaultCategoryColor
import app.outgo.domain.BudgetOffset
import app.outgo.domain.CategoryKind
import app.outgo.ui.LocalAppContainer
import app.outgo.ui.component.BudgetProgressBlock
import app.outgo.ui.component.OutgoSegmentedButton
import app.outgo.ui.component.ColorPickerGrid
import app.outgo.ui.component.ConfirmDialog
import app.outgo.ui.component.EditorScaffold
import app.outgo.ui.component.IconPickerSheet
import app.outgo.ui.component.IconView
import app.outgo.ui.component.MonthPicker
import app.outgo.ui.component.onBlur
import app.outgo.ui.component.rememberAutoSave
import app.outgo.ui.component.PlusRow
import app.outgo.ui.component.budgetRemainingColor
import app.outgo.ui.component.budgetRemainingText
import app.outgo.ui.component.rememberReorderState
import app.outgo.ui.component.rememberSwipeLevel
import app.outgo.ui.component.reorderableItem
import app.outgo.ui.component.slideItem
import app.outgo.ui.component.swipeShift
import app.outgo.ui.component.swipeStep
import app.outgo.util.Money
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest

private sealed interface EditTarget {
    data object NewParent : EditTarget
    data class NewChild(val parentId: Long, val parentColor: Int) : EditTarget
    data class Edit(val category: CategoryEntity) : EditTarget
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(onOpenEditor: (id: Long?, parentId: Long?) -> Unit) {
    val viewModel = categoryViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(setOf<Long>()) }
    // Expense <-> Income; past either end the tab level takes the swipe.
    val typeSwipe = rememberSwipeLevel { next, _ ->
        val type = if (next) CategoryKind.INCOME else CategoryKind.EXPENSE
        if (type == state.type) null else { { viewModel.setType(type) } }
    }
    val listState = rememberLazyListState()

    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(R.string.nav_category)) }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).swipeStep(typeSwipe),
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
            ) {
                OutgoSegmentedButton(
                    selected = state.type == CategoryKind.EXPENSE,
                    onClick = { viewModel.setType(CategoryKind.EXPENSE) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text(stringResource(R.string.category_expense_tab)) }
                OutgoSegmentedButton(
                    selected = state.type == CategoryKind.INCOME,
                    onClick = { viewModel.setType(CategoryKind.INCOME) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text(stringResource(R.string.category_income_tab)) }
            }

            Box(Modifier.weight(1f)) {
                @Composable
                fun list(shown: CategoryUiState, shownListState: LazyListState, modifier: Modifier) = CategoryList(
                    state = shown,
                    listState = shownListState,
                    expanded = expanded,
                    onToggle = { id -> expanded = if (id in expanded) expanded - id else expanded + id },
                    onEdit = { target ->
                        when (target) {
                            is EditTarget.Edit -> onOpenEditor(target.category.id, null)
                            is EditTarget.NewChild -> onOpenEditor(null, target.parentId)
                            EditTarget.NewParent -> onOpenEditor(null, null)
                        }
                    },
                    onReorder = viewModel::reorder,
                    modifier = modifier,
                )
                list(state, listState, Modifier.swipeShift(typeSwipe))
                val other = state.other
                if (typeSwipe.moving && other != null) {
                    // At the scroll position the type switch would keep.
                    val otherListState = rememberLazyListState(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
                    val page = if (other.type == CategoryKind.INCOME) 1 else -1
                    list(other, otherListState, Modifier.swipeShift(typeSwipe, page))
                }
            }
        }
    }

}

/**
 * The Category tab's own ViewModel, from the activity's store: the tab lives outside the NavHost,
 * and the editor route shares it so a save outlives the editor being popped.
 */
@Composable
private fun categoryViewModel(): CategoryViewModel {
    val container = LocalAppContainer.current
    return viewModel(
        viewModelStoreOwner = LocalContext.current as ComponentActivity,
        factory = viewModelFactory { initializer { CategoryViewModel(container.categoryRepository, container.budgetRepository, container.accountRepository) } },
    )
}

/** The category editor as its own screen; see [Routes.CATEGORY_EDIT_PATTERN][app.outgo.ui.nav.Routes.CATEGORY_EDIT_PATTERN]. */
@Composable
fun CategoryEditScreen(categoryId: Long?, parentId: Long?, onClose: () -> Unit) {
    val viewModel = categoryViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val all = listOfNotNull(state, state.other).flatMap { it.parents + it.childrenByParent.values.flatten() }
    // Nothing to draw for the moment a deleted category is still on screen before the pop.
    val target = when {
        categoryId != null -> all.firstOrNull { it.id == categoryId }?.let { EditTarget.Edit(it) }
        parentId != null -> all.firstOrNull { it.id == parentId }?.let { EditTarget.NewChild(it.id, it.color) }
        else -> EditTarget.NewParent
    } ?: return
    EditCategoryScreen(target, state.type, state.budgetsByCategory, state.accounts, viewModel, onClose)
}

/** One row of the flattened category list; parents, children and plus rows are each a lazy item. */
private sealed interface CategoryEntry {
    val key: Any
}

private data class ParentEntry(val category: CategoryEntity) : CategoryEntry {
    override val key: Any get() = category.id
}

private data class ChildEntry(val category: CategoryEntity) : CategoryEntry {
    override val key: Any get() = category.id
}

private data class AddChildEntry(val parent: CategoryEntity) : CategoryEntry {
    override val key: Any get() = "add-${parent.id}"
}

/** Keyed per type, so a type switch replaces it instead of sliding it to the other list's end. */
private data class AddParentEntry(val type: Int) : CategoryEntry {
    override val key: Any get() = "add-$type"
}

/** The parent a child row or a slot next to it belongs to. */
private fun ownerOf(entry: CategoryEntry?): Long? = when (entry) {
    is ChildEntry -> entry.category.parentId
    is AddChildEntry -> entry.parent.id
    else -> null
}

/** How long a lifted child hovers over a folded parent before it unfolds to take it. */
private const val HOVER_EXPAND_MS = 500L

/**
 * One type's categories; drawn for both types while a type swipe moves. A long press lifts a row
 * to reorder it: a parent among parents (every parent folds for the drag), a child among the
 * children of any unfolded parent. A parent's only child can't leave it.
 */
@Composable
private fun CategoryList(
    state: CategoryUiState,
    listState: LazyListState,
    expanded: Set<Long>,
    onToggle: (Long) -> Unit,
    onEdit: (EditTarget) -> Unit,
    onReorder: (Map<Long?, List<Long>>) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The order a drag is working on; the database's next emission replaces it.
    var parents by remember(state.parents) { mutableStateOf(state.parents) }
    var children by remember(state.childrenByParent) { mutableStateOf(state.childrenByParent) }
    val reorder = rememberReorderState(listState)
    val dragged = reorder.draggingKey
    val draggingParent = dragged != null && parents.any { it.id == dragged }
    val shownExpanded = if (draggingParent) emptySet() else expanded
    val entries = remember(state.type, parents, children, shownExpanded) {
        buildList {
            parents.forEach { parent ->
                add(ParentEntry(parent))
                if (parent.id in shownExpanded) {
                    children[parent.id].orEmpty().forEach { add(ChildEntry(it)) }
                    add(AddChildEntry(parent))
                }
            }
            add(AddParentEntry(state.type))
        }
    }
    val byKey = remember(entries) { entries.associateBy { it.key } }
    // The saved parent of the lifted child; a parent's only child stays inside it.
    val home = dragged?.let { key -> state.childrenByParent.entries.firstOrNull { e -> e.value.any { it.id == key } }?.key }
    val lone = home != null && state.childrenByParent[home]?.size == 1

    reorder.update(
        keys = entries.map { it.key },
        canDrag = { byKey[it] is ParentEntry || byKey[it] is ChildEntry },
        isSlot = { before, after ->
            val b = byKey[before]
            val a = byKey[after]
            if (draggingParent) {
                a is ParentEntry || a is AddParentEntry
            } else {
                (b is ChildEntry || b is ParentEntry) && (a is ChildEntry || a is AddChildEntry) && (!lone || ownerOf(a) == home)
            }
        },
        onMove = { key, to ->
            val after = entries.filter { it.key != key }.getOrNull(to)
            if (draggingParent) {
                val others = parents.filter { it.id != key }
                val at = (after as? ParentEntry)?.let { a -> others.indexOfFirst { it.id == a.category.id } } ?: others.size
                parents = others.toMutableList().apply { add(at, parents.first { it.id == key }) }
            } else {
                val target = ownerOf(after) ?: return@update
                val moved = children.values.flatten().first { it.id == key }.copy(parentId = target)
                val without = children.mapValues { (_, list) -> list.filter { it.id != key } }
                val list = without[target].orEmpty()
                val at = (after as? ChildEntry)?.let { a -> list.indexOfFirst { it.id == a.category.id } } ?: list.size
                children = without + (target to list.toMutableList().apply { add(at, moved) })
            }
        },
        onDrop = { key ->
            if (parents.any { it.id == key }) {
                onReorder(mapOf(null to parents.map { it.id }))
            } else {
                val from = state.childrenByParent.entries.firstOrNull { e -> e.value.any { it.id == key } }?.key
                val to = children.entries.firstOrNull { e -> e.value.any { it.id == key } }?.key
                onReorder(listOfNotNull(from, to).distinct().associate { id -> id to children[id].orEmpty().map { it.id } })
            }
        },
    )

    // A lifted child hovering over a folded parent unfolds it, so it can drop inside.
    val currentByKey by rememberUpdatedState(byKey)
    val currentExpanded by rememberUpdatedState(expanded)
    val currentOnToggle by rememberUpdatedState(onToggle)
    LaunchedEffect(dragged, draggingParent) {
        if (dragged == null || draggingParent) return@LaunchedEffect
        snapshotFlow { reorder.hoveredKey() }.collectLatest { key ->
            if (currentByKey[key] is ParentEntry && key !in currentExpanded) {
                delay(HOVER_EXPAND_MS)
                currentOnToggle(key as Long)
            }
        }
    }

    // A lifted row's release also ends a tap on it: that tap must not open the editor.
    val edit = { target: EditTarget -> if (reorder.draggingKey == null) onEdit(target) }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(entries, key = { it.key }, contentType = { it::class }) { entry ->
            when (entry) {
                is ParentEntry -> {
                    val parent = entry.category
                    CategoryRow(
                        category = parent,
                        budget = state.budgetsByCategory[parent.id]?.takeIf { it.enabled },
                        onRowClick = { edit(EditTarget.Edit(parent)) },
                        modifier = reorderableItem(reorder, entry.key),
                        trailing = {
                            Icon(
                                painter = painterResource(
                                    if (parent.id in shownExpanded) R.drawable.ph_caret_down else R.drawable.ph_caret_right,
                                ),
                                contentDescription = null,
                                modifier = Modifier
                                    .clickable { onToggle(parent.id) }
                                    .padding(8.dp),
                            )
                        },
                    )
                }
                is ChildEntry -> CategoryRow(
                    category = entry.category,
                    budget = state.budgetsByCategory[entry.category.id]?.takeIf { it.enabled },
                    onRowClick = { edit(EditTarget.Edit(entry.category)) },
                    modifier = Modifier.padding(start = 20.dp).then(reorderableItem(reorder, entry.key)),
                )
                is AddChildEntry -> PlusRow(
                    onClick = { edit(EditTarget.NewChild(entry.parent.id, entry.parent.color)) },
                    modifier = Modifier
                        .then(slideItem())
                        .padding(start = 20.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                )
                is AddParentEntry -> PlusRow(
                    onClick = { edit(EditTarget.NewParent) },
                    modifier = Modifier
                        .then(slideItem())
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                )
            }
        }
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
            val trades = LocalAppContainer.current.tradeRepository
            val limit = budget.effectiveLimit
            BudgetProgressBlock(
                remainingText = budgetRemainingText(budget.spent, limit),
                spentOfTotalText = stringResource(
                    R.string.category_spent_of_budget,
                    Money.groupThousands(budget.spent),
                    Money.groupThousands(limit),
                ),
                progress = if (limit > 0) budget.spent.toFloat() / limit.toFloat() else 0f,
                color = budgetRemainingColor(budget.spent, limit, Color(category.color)),
                current = budget.spent,
                total = limit,
                lineName = stringResource(R.string.progress_line_spent),
                loadDays = { trades.budgetDays(category.id) },
                greenWhenLower = true,
                modifier = Modifier.padding(start = 40.dp),
            )
        }
    }
}

/** The General section's inputs, saved together. */
private data class CategoryGeneral(val name: String, val description: String, val iconId: Long?, val color: Int)

@Composable
private fun EditCategoryScreen(
    target: EditTarget,
    currentType: Int,
    budgets: Map<Long, BudgetWithProgress>,
    accounts: List<AccountEntity>,
    viewModel: CategoryViewModel,
    onDismiss: () -> Unit,
) {
    val existing = (target as? EditTarget.Edit)?.category
    val effectiveType = existing?.type ?: currentType
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var description by remember { mutableStateOf(existing?.description.orEmpty()) }
    var iconId by remember { mutableStateOf(existing?.iconId) }
    var color by remember {
        mutableStateOf(existing?.color ?: (target as? EditTarget.NewChild)?.parentColor ?: DefaultCategoryColor)
    }
    // The Budget section shows (and saves) one month's settings; it opens on this month's.
    var month by remember { mutableIntStateOf(MonthKey.current()) }
    var form by remember { mutableStateOf(BudgetForm.of(existing?.let { budgets[it.id] })) }
    var showIconPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var hasTrades by remember { mutableStateOf(false) }
    var childCount by remember { mutableStateOf(0) }
    val defaultChildName = stringResource(R.string.category_default_child_name)
    val scope = rememberCoroutineScope()

    LaunchedEffect(existing?.id) {
        if (existing != null) {
            hasTrades = viewModel.hasTrades(existing.id)
            if (existing.isParent) childCount = viewModel.childCount(existing.id)
        }
    }

    // Editing saves as it goes: General as one, the Budget section as the shown month's settings.
    val generalSave = existing?.let {
        rememberAutoSave(CategoryGeneral(name, description, iconId, color)) { g ->
            g.name.isNotBlank().also { ok -> if (ok) viewModel.update(it, g.name, g.iconId, g.color, g.description.trim().ifEmpty { null }) }
        }
    }
    val budgetSave = existing?.takeIf { effectiveType == CategoryKind.EXPENSE }?.let {
        rememberAutoSave(form) { f ->
            f.complete.also { ok -> if (ok) viewModel.setBudgetMonth(it.id, month, f.toSetting()) }
        }
    }
    fun showMonth(m: Int) {
        if (budgetSave == null || existing == null) {
            month = m
            return
        }
        // The month being left keeps its pending change.
        budgetSave.flush()
        month = m
        scope.launch {
            val loaded = BudgetForm.of(viewModel.budgetAt(existing.id, m))
            budgetSave.reset(loaded)
            form = loaded
        }
    }

    val title = when {
        existing != null -> stringResource(R.string.category_edit_title)
        target is EditTarget.NewChild -> stringResource(R.string.category_create_child_title)
        else -> stringResource(R.string.category_create_parent_title)
    }

    // Creating: compared against the values the editor opened with, for the discard check.
    val fields = listOf(name, description, iconId, color, month, form)
    val initialFields = remember { fields }
    EditorScaffold(
        title = title,
        onCancel = onDismiss,
        dirty = existing == null && fields != initialFields,
        onSave = if (existing != null) {
            null
        } else {
            {
                val setting = form.toSetting().takeIf { form.enabled && effectiveType == CategoryKind.EXPENSE }
                if (target is EditTarget.NewChild) {
                    viewModel.createChild(target.parentId, name, iconId, color, setting, month, description.trim().ifEmpty { null })
                } else {
                    viewModel.createParent(name, iconId, color, setting, month, defaultChildName, description.trim().ifEmpty { null })
                }
                onDismiss()
            }
        },
        saveEnabled = name.isNotBlank() && (effectiveType != CategoryKind.EXPENSE || form.complete),
        onDelete = if (existing != null) { { showDeleteConfirm = true } } else null,
    ) {
        SectionHeader(stringResource(R.string.category_section_general), stringResource(R.string.category_general_info))
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
            isError = existing != null && name.isBlank(),
            // Editing: a blank name goes back to the saved one when the field is left.
            modifier = Modifier.fillMaxWidth().onBlur { if (name.isBlank()) generalSave?.let { name = it.saved.name } },
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text(stringResource(R.string.common_description_hint)) },
            minLines = 1,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.category_color_label), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        ColorPickerGrid(selected = color, onSelect = { color = it })

        // Only spending categories have a budget.
        if (effectiveType == CategoryKind.EXPENSE) {
            Spacer(Modifier.height(24.dp))
            SectionHeader(stringResource(R.string.category_section_budget), stringResource(R.string.category_budget_info)) {
                MonthPicker(month, ::showMonth)
                SectionSwitch(checked = form.enabled, onCheckedChange = { form = form.copy(enabled = it) })
            }
            if (form.enabled) {
                val none = stringResource(R.string.budget_offset_none)
                val budgetLabel = stringResource(R.string.budget_offset_budget)
                val budgetTargets = listOf(PickOption(BudgetOffset.SELF, stringResource(R.string.budget_offset_self), iconId, color)) +
                    budgets.values.filter { it.categoryId != null && it.categoryId != existing?.id }
                        .map { PickOption(it.categoryId!!, it.displayName, it.displayIconId, it.categoryColor) }
                val accountOptions = accounts.map { PickOption<Long?>(it.id, it.name, it.iconId, it.color) }
                val to = stringResource(R.string.budget_offset_add_to)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = form.limitText,
                        onValueChange = { form = form.copy(limitText = it.filter { c -> c.isDigit() }) },
                        label = { Text(stringResource(R.string.category_budget_limit)) },
                        singleLine = true,
                        isError = existing != null && (form.limitText.toLongOrNull() ?: 0L) <= 0,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        // Editing: an invalid limit goes back to the saved one when the field is left,
                        // and the whole section too if that month had none (it was off).
                        modifier = Modifier.fillMaxWidth().onBlur {
                            val saved = budgetSave?.saved ?: return@onBlur
                            if (!form.complete) form = form.copy(limitText = saved.limitText).takeIf { it.complete } ?: saved
                        },
                    )

                    OffsetPicker(
                        stringResource(R.string.budget_over_action),
                        form.overOn,
                        listOf(PickOption(false, none), PickOption(true, budgetLabel)),
                    ) { form = form.copy(overOn = it) }
                    if (form.overOn) {
                        OffsetPicker(budgetLabel, form.overTarget, budgetTargets, indent = true) { form = form.copy(overTarget = it) }
                    }

                    OffsetPicker(
                        stringResource(R.string.budget_under_action),
                        form.underMode,
                        listOf(
                            PickOption(UnderMode.NONE, none),
                            PickOption(UnderMode.BUDGET, budgetLabel),
                            PickOption(UnderMode.ACCOUNT, stringResource(R.string.budget_offset_account)),
                        ),
                    ) { form = form.copy(underMode = it) }
                    when (form.underMode) {
                        UnderMode.NONE -> Unit
                        UnderMode.BUDGET ->
                            OffsetPicker(to, form.underTarget, budgetTargets, indent = true) { form = form.copy(underTarget = it) }
                        UnderMode.ACCOUNT -> {
                            OffsetPicker(stringResource(R.string.budget_offset_from_account), form.fromAccount, accountOptions, indent = true) {
                                form = form.copy(fromAccount = it, toAccount = form.toAccount.takeIf { a -> a != it })
                            }
                            OffsetPicker(to, form.toAccount, accountOptions.filter { it.value != form.fromAccount }, indent = true) {
                                form = form.copy(toAccount = it)
                            }
                        }
                    }
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
                generalSave?.stop()
                budgetSave?.stop()
                viewModel.deleteOrArchive(existing)
                showDeleteConfirm = false
                onDismiss()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

/**
 * A section's title, then a small (i) opening [info] (what the section and its inputs do) in a
 * dialog, with an optional control (a [SectionSwitch]) at its right end.
 */
@Composable
internal fun SectionHeader(title: String, info: String, action: (@Composable () -> Unit)? = null) {
    var showInfo by rememberSaveable { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        // Title sits 10dp below the section above and 8dp above its content.
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp).heightIn(min = 28.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(start = 2.dp).size(28.dp).clip(CircleShape).clickable { showInfo = true },
        ) {
            Icon(
                painterResource(R.drawable.ph_info),
                contentDescription = stringResource(R.string.home_balance_info, title),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        action?.invoke()
    }
    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text(title) },
            text = { Text(info, modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text(stringResource(R.string.common_ok)) } },
        )
    }
}

/**
 * A section header's on/off switch, drawn at 75% (39x24dp) so it doesn't make the header taller
 * than its title row. Compose still widens its touch area to the 48dp minimum.
 */
@Composable
internal fun SectionSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier.size(39.dp, 24.dp).scale(0.75f),
    )
}

/** Where a budget's unspent amount goes: nowhere, another budget's limit, or another account. */
private enum class UnderMode { NONE, BUDGET, ACCOUNT }

/** The Budget section's inputs for one month, as typed (the limit is still text). */
private data class BudgetForm(
    val enabled: Boolean,
    val limitText: String,
    val overOn: Boolean,
    val overTarget: Long,
    val underMode: UnderMode,
    val underTarget: Long,
    val fromAccount: Long?,
    val toAccount: Long?,
) {
    /** On needs a limit, and an account offset two different accounts. */
    val complete: Boolean
        get() = !enabled || ((limitText.toLongOrNull() ?: 0L) > 0 &&
            (underMode != UnderMode.ACCOUNT || (fromAccount != null && toAccount != null && fromAccount != toAccount)))

    fun toSetting() = BudgetSetting(
        enabled = enabled,
        limit = limitText.toLongOrNull() ?: 0L,
        overTarget = overTarget.takeIf { overOn },
        underTarget = underTarget.takeIf { underMode == UnderMode.BUDGET },
        underFromAccount = fromAccount.takeIf { underMode == UnderMode.ACCOUNT },
        underToAccount = toAccount.takeIf { underMode == UnderMode.ACCOUNT },
    )

    companion object {
        fun of(s: BudgetSetting?) = BudgetForm(
            enabled = s?.enabled ?: false,
            limitText = s?.limit?.takeIf { it > 0 }?.toString().orEmpty(),
            overOn = s?.overTarget != null,
            overTarget = s?.overTarget ?: BudgetOffset.SELF,
            underMode = when {
                s?.underFromAccount != null -> UnderMode.ACCOUNT
                s?.underTarget != null -> UnderMode.BUDGET
                else -> UnderMode.NONE
            },
            underTarget = s?.underTarget ?: BudgetOffset.SELF,
            fromAccount = s?.underFromAccount,
            toAccount = s?.underToAccount,
        )

        /** A budget row's settings in the month it was read for. */
        fun of(b: BudgetWithProgress?) = of(
            b?.let { BudgetSetting(it.enabled, it.limitAmount ?: 0L, it.overTarget, it.underTarget, it.underFromAccount, it.underToAccount) },
        )
    }
}

/** One choice of an [OffsetPicker]; a budget or account choice carries its icon. */
internal data class PickOption<T>(val value: T, val label: String, val iconId: Long? = null, val color: Int? = null) {
    val hasIcon: Boolean get() = iconId != null || color != null
}

/** "Label ........ [icon] Choice ▾" in a bordered row. A value missing from [options] (not picked yet, or gone) shows "—". */
@Composable
internal fun <T> OffsetPicker(
    label: String,
    value: T,
    options: List<PickOption<T>>,
    indent: Boolean = false,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    // Same corners as the outlined text fields above.
    val shape = OutlinedTextFieldDefaults.shape
    val selected = options.firstOrNull { it.value == value }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(start = if (indent) 16.dp else 0.dp)
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable { open = true }
            .padding(horizontal = 12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Box {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selected?.hasIcon == true) {
                    IconView(iconId = selected.iconId, size = 22.dp, color = selected.color)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    selected?.label ?: "—",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    painter = painterResource(R.drawable.ph_caret_down),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp).size(12.dp),
                )
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        leadingIcon = if (option.hasIcon) {
                            { IconView(iconId = option.iconId, size = 24.dp, color = option.color) }
                        } else {
                            null
                        },
                        onClick = {
                            open = false
                            onSelect(option.value)
                        },
                    )
                }
            }
        }
    }
}
