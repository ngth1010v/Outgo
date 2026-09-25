package app.outgo.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val LiftElevation = 8.dp
/** Negative: a lifted row shrinks slightly, reading as picked up rather than zoomed. */
private const val LiftScale = -0.03f
private val RowShape = RoundedCornerShape(12.dp)

/** A finger within this distance of an edge scrolls the list, faster the deeper it goes. */
private val EdgeZone = 56.dp
/** Per second, so the pace is the same at any refresh rate (12dp a frame at 60 Hz). */
private val MaxScrollSpeed = 720.dp
private const val MaxFrameSeconds = 1 / 30f

/** A lifted row trails the finger sideways at this fraction of its distance: a hint of freedom, not a move. */
private const val FollowX = 0.15f

/**
 * Long-press-and-drag reordering for a LazyColumn. The screen owns the order: [update] tells this
 * where the lifted row may land, and each move is applied to the screen's own list, so the other
 * rows slide aside through `animateItem`. The lifted row follows the finger; near an edge the list
 * scrolls under it.
 */
@Stable
class ReorderState internal constructor(
    private val listState: LazyListState,
    private val scope: CoroutineScope,
    private val edgeZone: Float,
    private val maxScroll: Float,
) {
    private var keys: List<Any> = emptyList()
    private var canDrag: (Any) -> Boolean = { false }
    private var isSlot: (Any?, Any?) -> Boolean = { _, _ -> false }
    private var onMove: (Any, Int) -> Unit = { _, _ -> }
    private var onDrop: (Any) -> Unit = {}

    /** A move went to the screen: wait for its new keys before judging the next one. */
    private var moving = false

    /** The lifted row's key; null while nothing is lifted. */
    var draggingKey: Any? by mutableStateOf(null)
        private set

    /** The finger's x in the root, for a drop zone at an edge; kept after a drop for onDrop to read. */
    var fingerX by mutableFloatStateOf(0f)
        private set
    private var startX = 0f

    /** The row gliding from the finger into its slot after a drop. */
    private var settlingKey: Any? by mutableStateOf(null)
    private val settle = Animatable(0f)
    private val settleX = Animatable(0f)

    /** Where the lifted row was when it lifted, in the viewport; the finger has moved it [dragY] since. */
    private var startTop = 0f
    /** Where the finger holds the row, from its top: tall rows reach an edge long before the finger. */
    private var grabY = 0f
    private var dragY by mutableFloatStateOf(0f)
    private var itemSize = 0
    private var edgeScroll: Job? = null

    private val visualTop get() = startTop + dragY

    /**
     * The list's rows as the screen draws them now, and its rules; call on every composition.
     * [isSlot] says whether the lifted row may sit between two rows (null past either end).
     * [onMove] gets the lifted row's key and its new index among the other rows.
     */
    fun update(
        keys: List<Any>,
        canDrag: (key: Any) -> Boolean,
        isSlot: (before: Any?, after: Any?) -> Boolean,
        onMove: (key: Any, to: Int) -> Unit,
        onDrop: (key: Any) -> Unit,
    ) {
        if (keys != this.keys) moving = false
        this.keys = keys
        this.canDrag = canDrag
        this.isSlot = isSlot
        this.onMove = onMove
        this.onDrop = onDrop
    }

    /** The row under the lifted row's centre. */
    fun hoveredKey(): Any? {
        val key = draggingKey ?: return null
        val center = visualTop + itemSize / 2f
        return listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.key != key && center >= it.offset && center < it.offset + it.size }?.key
    }

    internal fun isLifted(key: Any) = key == draggingKey || key == settlingKey

    internal fun offsetOf(key: Any): Float = when (key) {
        draggingKey -> find(key)?.let { visualTop - it.offset } ?: 0f
        settlingKey -> settle.value
        else -> 0f
    }

    /** Sideways the lifted row trails the finger at [FollowX] of its distance, then glides back. */
    internal fun offsetXOf(key: Any): Float = when (key) {
        draggingKey -> (fingerX - startX) * FollowX
        settlingKey -> settleX.value
        else -> 0f
    }

    private fun find(key: Any) = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }

    internal fun start(key: Any, x: Float, y: Float): Boolean {
        if (draggingKey != null || !canDrag(key)) return false
        val info = find(key) ?: return false
        startTop = info.offset.toFloat()
        itemSize = info.size
        dragY = 0f
        grabY = y
        startX = x
        fingerX = x
        moving = false
        draggingKey = key
        edgeScroll = scope.launch { followEdges(key) }
        return true
    }

    internal fun drag(dy: Float, x: Float) {
        if (draggingKey == null) return
        fingerX = x
        dragY += dy
        judge()
    }

    /**
     * Drops the row lifted by [key]. Every row's gesture calls this on cancel, even one only waiting
     * for a touch (its handler is reset when the list recycles its node), so it must not drop another.
     */
    internal fun end(key: Any) {
        if (key != draggingKey) return
        edgeScroll?.cancel()
        val offset = offsetOf(key)
        val offsetX = offsetXOf(key)
        // Undispatched, so the glide starts from the finger in the frame the row is let go.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            settle.snapTo(offset)
            settleX.snapTo(offsetX)
            settlingKey = key
            launch { settleX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
            draggingKey = null
            onDrop(key)
            try {
                settle.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
            } finally {
                if (settlingKey == key) settlingKey = null
            }
        }
    }

    /**
     * Moves the lifted row once its centre crosses the middle of the rows between it and the next
     * slot either way. Judging by that middle is what keeps it from bouncing back: after the move
     * those rows sit one lifted-row height further, so the centre is well clear of their new middle.
     */
    private fun judge() {
        val key = draggingKey ?: return
        if (moving) return
        val from = keys.indexOf(key).takeIf { it >= 0 } ?: return
        val rest = keys.filter { it != key }
        val visible = listState.layoutInfo.visibleItemsInfo.associateBy { it.key }
        val center = visualTop + itemSize / 2f
        fun slot(gap: Int) = isSlot(rest.getOrNull(gap - 1), rest.getOrNull(gap))
        // null while one end of the run is off screen; the edge scroll brings it in.
        fun middle(first: Int, last: Int): Float? {
            val top = visible[rest[first]] ?: return null
            val bottom = visible[rest[last]] ?: return null
            return (top.offset + bottom.offset + bottom.size) / 2f
        }
        // Not into a slot wholly off screen: bringing the row back there is a jump that snaps every
        // sliding row. The edge scroll brings the slot in. Its new top is where the passed rows start or end.
        val start = listState.layoutInfo.viewportStartOffset
        val end = listState.layoutInfo.viewportEndOffset
        val down = (from + 1..rest.size).firstOrNull { slot(it) }
        if (down != null && middle(from, down - 1)?.let { center > it } == true) {
            val last = visible.getValue(rest[down - 1])
            if (last.offset + last.size - itemSize < end) return move(key, down)
        }
        val up = (from - 1 downTo 0).firstOrNull { slot(it) }
        if (up != null && middle(up, from - 1)?.let { center < it } == true) {
            if (visible.getValue(rest[up]).offset + itemSize > start) move(key, up)
        }
    }

    private fun move(key: Any, to: Int) {
        moving = true
        // Keep the first visible index in place, not its key: when the move swaps that row the list
        // would otherwise jump to follow it, and the rows above snap instead of sliding.
        listState.requestScrollToItem(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        onMove(key, to)
    }

    private suspend fun followEdges(key: Any) {
        var last = withFrameNanos { it }
        while (draggingKey == key) {
            val now = withFrameNanos { it }
            // Capped, so a slow frame (a chart composing) never turns into one big jump.
            val seconds = ((now - last) / 1e9f).coerceAtMost(MaxFrameSeconds)
            last = now
            if (draggingKey != key) return
            if (find(key) == null) {
                // A move or a fold put the row off screen (it stays composed, pinned): bring it back under the finger.
                val index = keys.indexOf(key)
                if (index >= 0) listState.scrollToItem(index, -visualTop.roundToInt())
                continue
            }
            val height = listState.layoutInfo.viewportSize.height
            val finger = visualTop + grabY
            val depth = when {
                finger < edgeZone -> finger - edgeZone
                finger > height - edgeZone -> finger - (height - edgeZone)
                else -> 0f
            }
            if (depth != 0f) {
                listState.scrollBy((depth / edgeZone).coerceIn(-1f, 1f) * maxScroll * seconds)
                judge()
            }
        }
    }
}

@Composable
fun rememberReorderState(listState: LazyListState): ReorderState {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    return remember(listState, density) {
        with(density) { ReorderState(listState, scope, EdgeZone.toPx(), MaxScrollSpeed.toPx()) }
    }
}

/**
 * A row of a reorderable list: a long press lifts it, then it follows the finger while the other
 * rows slide aside. Put it on the item's root, keyed as in [ReorderState.update]; spacing padding
 * goes before it so the lifted shadow hugs the row. Rows that never lift use [slideItem].
 */
@Composable
fun LazyItemScope.reorderableItem(state: ReorderState, key: Any): Modifier {
    val lifted = state.isLifted(key)
    val dragging = key == state.draggingKey
    val lift by animateFloatAsState(if (dragging) 1f else 0f, label = "lift")
    val haptic = LocalHapticFeedback.current
    // Pinned while lifted, so the list keeps it (and its gesture) alive even when a move puts it off screen.
    val pinnable = LocalPinnableContainer.current
    DisposableEffect(dragging, pinnable) {
        val handle = if (dragging) pinnable?.pin() else null
        onDispose { handle?.release() }
    }
    // The row's own coordinates move with its layer, so the finger is tracked in root space.
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    fun rootX(local: Offset) = coords[0]?.takeIf { it.isAttached }?.localToRoot(local)?.x ?: local.x
    return slideItem(lifted)
        .zIndex(if (lifted) 1f else 0f)
        .graphicsLayer {
            translationY = state.offsetOf(key)
            translationX = state.offsetXOf(key)
            scaleX = 1f + LiftScale * lift
            scaleY = scaleX
            shadowElevation = LiftElevation.toPx() * lift
            shape = RowShape
        }
        .onGloballyPositioned { coords[0] = it }
        .pointerInput(state, key) {
            detectDragGesturesAfterLongPress(
                onDragStart = { if (state.start(key, rootX(it), it.y)) haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                onDrag = { change, amount ->
                    change.consume()
                    state.drag(amount.y, rootX(change.position))
                },
                onDragEnd = { state.end(key) },
                onDragCancel = { state.end(key) },
            )
        }
}

/**
 * A row of a reorderable list that slides when rows move around it. Rows appear and leave without
 * fading: a fade would replay the old rows over new ones when the whole list is swapped (a type
 * swipe), after the swipe has already shown the new ones. [lifted] rows are placed by the finger.
 */
fun LazyItemScope.slideItem(lifted: Boolean = false): Modifier = Modifier.animateItem(
    fadeInSpec = null,
    placementSpec = if (lifted) null else spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold),
    fadeOutSpec = null,
)
