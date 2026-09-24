package app.outgo.ui.category

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import app.outgo.ui.component.rememberReorderState
import app.outgo.ui.component.rememberSwipeLevel
import app.outgo.ui.component.reorderableItem
import app.outgo.ui.component.slideItem
import app.outgo.ui.component.swipeShift
import app.outgo.ui.component.swipeStep
import app.outgo.util.Money
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

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
    val state by viewModel.state.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(setOf<Long>()) }
    var editTarget by remember { mutableStateOf<EditTarget?>(null) }
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

            Box(Modifier.weight(1f)) {
                @Composable
                fun list(shown: CategoryUiState, shownListState: LazyListState, modifier: Modifier) = CategoryList(
                    state = shown,
                    listState = shownListState,
                    expanded = expanded,
                    onToggle = { id -> expanded = if (id in expanded) expanded - id else expanded + id },
                    onEdit = { editTarget = it },
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

    editTarget?.let { target ->
        EditCategorySheet(
            target = target,
            currentType = state.type,
            viewModel = viewModel,
            onDismiss = { editTarget = null },
        )
    }
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
                        budget = state.budgetsByCategory[parent.id],
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
                    budget = state.budgetsByCategory[entry.category.id],
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
        // Scrollable so a field stays reachable above the keyboard when the sheet is taller than the space left.
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
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
