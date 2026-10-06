package app.outgo.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.outgo.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** One row of the flattened tree; parents, children and plus rows are each a lazy item. */
private sealed interface TreeEntry {
    val key: Any
}

private data class ParentEntry<T>(val item: T, override val key: Any) : TreeEntry

private data class ChildEntry<T>(val item: T, val parentId: Long, override val key: Any) : TreeEntry

private data class AddChildEntry<T>(val parent: T, val parentId: Long) : TreeEntry {
    override val key: Any get() = "add-$parentId"
}

private data class AddParentEntry(override val key: Any) : TreeEntry

/** The parent a child row or a slot next to it belongs to. */
private fun ownerOf(entry: TreeEntry?): Long? = when (entry) {
    is ChildEntry<*> -> entry.parentId
    is AddChildEntry<*> -> entry.parentId
    else -> null
}

/** How long a lifted child hovers over a folded parent before it unfolds to take it. */
private const val HOVER_EXPAND_MS = 500L

/**
 * A two-level list (categories, accounts): each parent folds open onto its children and a plus row
 * that adds one, with a plus row adding a parent at the end. A long press lifts a row to reorder
 * it: a parent among parents (every parent folds for the drag), a child among the children of any
 * unfolded parent. A parent's only child can't leave it. [onReorder] gets each changed list's ids
 * by its parent id (null: the parents). [addParentKey] keys the last plus row; a different key per
 * list makes a list switch replace it instead of sliding it to the other list's end.
 */
@Composable
fun <T> TreeList(
    parents: List<T>,
    childrenByParent: Map<Long, List<T>>,
    id: (T) -> Long,
    listState: LazyListState,
    expanded: Set<Long>,
    onToggle: (Long) -> Unit,
    onOpen: (T) -> Unit,
    onAddChild: (T) -> Unit,
    onAddParent: () -> Unit,
    onReorder: (Map<Long?, List<Long>>) -> Unit,
    row: @Composable (item: T, onClick: () -> Unit, modifier: Modifier, trailing: (@Composable () -> Unit)?) -> Unit,
    modifier: Modifier = Modifier,
    addParentKey: Any = "add",
) {
    // The order a drag is working on; the database's next emission replaces it.
    var shownParents by remember(parents) { mutableStateOf(parents) }
    var children by remember(childrenByParent) { mutableStateOf(childrenByParent) }
    val reorder = rememberReorderState(listState)
    val dragged = reorder.draggingKey
    val draggingParent = dragged != null && shownParents.any { id(it) == dragged }
    // Parents folding open or shut, by how open each is (0 to 1): their rows are one panel, clipped
    // from the bottom, that grows or shrinks; a shut one's rows stay listed until it has closed.
    // Each row's last measured height, to lay the panel out.
    val folding = remember { mutableStateMapOf<Long, Animatable<Float, AnimationVector1D>>() }
    val heights = remember { HashMap<Any, Int>() }
    val scope = rememberCoroutineScope()
    fun fold(parentId: Long, opening: Boolean) {
        // From where a fold in the other direction got to, if one is running.
        val open = Animatable(folding[parentId]?.value ?: if (opening) 0f else 1f)
        folding[parentId] = open
        scope.launch {
            open.animateTo(if (opening) 1f else 0f, spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = 0.001f))
            if (folding[parentId] === open) folding.remove(parentId)
        }
    }
    fun toggle(parentId: Long) {
        fold(parentId, opening = parentId !in expanded)
        onToggle(parentId)
    }
    // A lifted parent folds every open parent shut for the drag and opens them again on its drop.
    // Set in the same snapshot as the folds start, so no panel flashes shut or open for a frame.
    var foldedForDrag by remember { mutableStateOf(emptySet<Long>()) }
    LaunchedEffect(draggingParent) {
        if (draggingParent) {
            expanded.forEach { fold(it, opening = false) }
            foldedForDrag = expanded
        } else if (foldedForDrag.isNotEmpty()) {
            foldedForDrag.filter { it in expanded }.forEach { fold(it, opening = true) }
            foldedForDrag = emptySet()
        }
    }
    val shownExpanded = expanded - foldedForDrag
    val shownFolding = folding.keys.toSet()
    val entries = remember(addParentKey, shownParents, children, shownExpanded, shownFolding) {
        buildList {
            shownParents.forEach { parent ->
                val parentId = id(parent)
                add(ParentEntry(parent, parentId))
                if (parentId in shownExpanded || parentId in shownFolding) {
                    children[parentId].orEmpty().forEach { add(ChildEntry(it, parentId, id(it))) }
                    add(AddChildEntry(parent, parentId))
                }
            }
            add(AddParentEntry(addParentKey))
        }
    }
    val byKey = remember(entries) { entries.associateBy { it.key } }
    // The saved parent of the lifted child; a parent's only child stays inside it.
    val home = dragged?.let { key -> childrenByParent.entries.firstOrNull { e -> e.value.any { id(it) == key } }?.key }
    val lone = home != null && childrenByParent[home]?.size == 1

    reorder.update(
        keys = entries.map { it.key },
        canDrag = { byKey[it] is ParentEntry<*> || byKey[it] is ChildEntry<*> },
        isSlot = { before, after ->
            val b = byKey[before]
            val a = byKey[after]
            if (draggingParent) {
                a is ParentEntry<*> || a is AddParentEntry
            } else {
                (b is ChildEntry<*> || b is ParentEntry<*>) && (a is ChildEntry<*> || a is AddChildEntry<*>) && (!lone || ownerOf(a) == home)
            }
        },
        onMove = { key, to ->
            val after = entries.filter { it.key != key }.getOrNull(to)
            if (draggingParent) {
                val others = shownParents.filter { id(it) != key }
                val at = (after as? ParentEntry<*>)?.let { a -> others.indexOfFirst { id(it) == a.key } } ?: others.size
                shownParents = others.toMutableList().apply { add(at, shownParents.first { id(it) == key }) }
            } else {
                val target = ownerOf(after) ?: return@update
                val moved = children.values.flatten().first { id(it) == key }
                val without = children.mapValues { (_, list) -> list.filter { id(it) != key } }
                val list = without[target].orEmpty()
                val at = (after as? ChildEntry<*>)?.let { a -> list.indexOfFirst { id(it) == a.key } } ?: list.size
                children = without + (target to list.toMutableList().apply { add(at, moved) })
            }
        },
        onDrop = { key ->
            if (shownParents.any { id(it) == key }) {
                onReorder(mapOf(null to shownParents.map(id)))
            } else {
                val from = childrenByParent.entries.firstOrNull { e -> e.value.any { id(it) == key } }?.key
                val to = children.entries.firstOrNull { e -> e.value.any { id(it) == key } }?.key
                onReorder(listOfNotNull(from, to).distinct().associate { p -> p to children[p].orEmpty().map(id) })
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
            if (currentByKey[key] is ParentEntry<*> && key !in currentExpanded) {
                delay(HOVER_EXPAND_MS)
                currentOnToggle(key as Long)
            }
        }
    }

    // A lifted row's release also ends a tap on it: that tap must not open the editor.
    fun guarded(action: () -> Unit) = { if (reorder.draggingKey == null) action() }
    // The gap above each row is part of the row (no spacedBy), so a folding panel takes its gaps along.
    // While one folds, the rows below follow its edge frame by frame instead of sliding after it.
    val slide = folding.isEmpty()
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        itemsIndexed(entries, key = { _, it -> it.key }, contentType = { _, it -> it::class }) { index, entry ->
            val gap = Modifier.padding(top = if (index == 0) 0.dp else 8.dp)
            // A child row (or plus row) of [parentId]: always measured, clipped while its panel folds.
            // The list measures rows top down, so an opening panel's first frame (at 0) records them all.
            fun nested(parentId: Long): Modifier {
                val open = folding[parentId]
                val panel = children[parentId].orEmpty().map(id) + "add-$parentId"
                // Clipped only while folding: a lifted row draws outside its bounds.
                return (if (open == null) Modifier else Modifier.clipToBounds()).layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    heights[entry.key] = placeable.height
                    val shown = if (open == null) {
                        placeable.height
                    } else {
                        val top = panel.takeWhile { it != entry.key }.sumOf { heights[it] ?: 0 }
                        (panel.sumOf { heights[it] ?: 0 } * open.value - top).roundToInt().coerceIn(0, placeable.height)
                    }
                    layout(placeable.width, shown) { placeable.place(0, 0) }
                }
            }
            @Suppress("UNCHECKED_CAST")
            when (entry) {
                is ParentEntry<*> -> {
                    val parent = entry.item as T
                    val parentId = id(parent)
                    row(parent, guarded { onOpen(parent) }, gap.then(reorderableItem(reorder, entry.key, slide))) {
                        Icon(
                            painter = painterResource(
                                if (parentId in shownExpanded) R.drawable.ph_caret_down else R.drawable.ph_caret_right,
                            ),
                            contentDescription = null,
                            modifier = Modifier
                                .clickable { toggle(parentId) }
                                .padding(8.dp),
                        )
                    }
                }
                is ChildEntry<*> -> {
                    val child = entry.item as T
                    row(
                        child,
                        guarded { onOpen(child) },
                        nested(entry.parentId).then(gap).padding(start = 20.dp).then(reorderableItem(reorder, entry.key, slide)),
                        null,
                    )
                }
                is AddChildEntry<*> -> PlusRow(
                    onClick = guarded { onAddChild(entry.parent as T) },
                    modifier = nested(entry.parentId)
                        .then(slideItem(slide = slide))
                        .then(gap)
                        .padding(start = 20.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                )
                is AddParentEntry -> PlusRow(
                    onClick = guarded(onAddParent),
                    modifier = Modifier
                        .then(slideItem(slide = slide))
                        .then(gap)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                )
            }
        }
    }
}
