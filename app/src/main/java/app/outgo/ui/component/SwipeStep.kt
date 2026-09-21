package app.outgo.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

private val SWIPE_DISTANCE = 40.dp
private const val SETTLE_MS = 200

/** Step to run on release, or null when that side has nothing to step to. */
typealias SwipeTarget = (next: Boolean, down: Offset) -> (() -> Unit)?

/**
 * One level of swipeable content, like a pager: the content shifted by [offset], its neighbors one
 * [width] to either side. [target] gets `next = true` for the right neighbor (finger moving
 * right-to-left) and the touch-down position; it is updated on every composition.
 */
@Stable
class SwipeLevel internal constructor(internal var target: SwipeTarget) {
    internal val offset = Animatable(0f)
    internal var width = 0f

    /** Dragged or settling: draw the neighbors (see [swipeShift] with a page) only while this holds. */
    val moving by derivedStateOf { offset.value != 0f }
}

@Composable
fun rememberSwipeLevel(target: SwipeTarget): SwipeLevel =
    remember { SwipeLevel(target) }.also { it.target = target }

/**
 * The level an inner [swipeStep] hands a swipe to when it has no step that way (e.g. the tab
 * level under a screen's types). Its target sees the down position in the inner level's coordinates.
 */
val LocalSwipeParent = staticCompositionLocalOf<SwipeLevel?> { null }

/**
 * Draws the content at its place in [level]: [page] 0 is the current content, -1 / 1 the left /
 * right neighbor. Put it after (inside) the [swipeStep] that drives [level].
 */
fun Modifier.swipeShift(level: SwipeLevel, page: Int = 0) =
    graphicsLayer { translationX = level.offset.value + page * level.width }

/**
 * A neighbor drawn over the current content without taking up room: it keeps its own height but
 * reports none, so it never grows a scrolling parent. Combine with [swipeShift].
 */
fun Modifier.swipeNeighbor() = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Int.MAX_VALUE))
    layout(placeable.width, 0) { placeable.place(0, 0) }
}

/**
 * Horizontal swipe = step to a neighbor of [level], either way, the direction free to change
 * mid-drag. A direction [level] has no step for goes to [LocalSwipeParent] live, so crossing the
 * start point hands the drag between the two. A level with no step either way leaves the swipe to
 * the parent's own [swipeStep] (inner ones see it first). A claimed swipe is consumed, so it
 * cancels clicks underneath. On a step the new content slides in from the side the finger came from.
 */
@Composable
fun Modifier.swipeStep(level: SwipeLevel): Modifier {
    val parent = LocalSwipeParent.current
    val scope = rememberCoroutineScope()
    return onSizeChanged { level.width = it.width.toFloat() }.pointerInput(level, parent) {
        val minDistance = SWIPE_DISTANCE.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            fun owner(next: Boolean): Pair<SwipeLevel, () -> Unit>? =
                level.target(next, down.position)?.let { level to it }
                    ?: parent?.let { p -> p.target(next, down.position)?.let { p to it } }
            // Resolved once per swipe: the steps read the state at touch-down.
            val prev = owner(false)
            val next = owner(true)
            if (prev?.first != level && next?.first != level) return@awaitEachGesture
            var dx = 0f
            val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, overSlop ->
                change.consume()
                dx = overSlop
            } ?: return@awaitEachGesture
            fun ownerNow() = if (dx < 0) next else prev
            fun shift() {
                val owner = ownerNow()?.first
                scope.launch {
                    level.offset.snapTo(if (owner == level) dx else 0f)
                    parent?.offset?.snapTo(if (owner == parent) dx else 0f)
                }
            }
            shift()
            val finished = horizontalDrag(start.id) { change ->
                dx += change.positionChange().x
                change.consume()
                shift()
            }
            val (owner, step) = ownerNow() ?: return@awaitEachGesture
            val moved = dx
            scope.launch {
                if (finished && abs(moved) > minDistance) {
                    step()
                    // The new content sits where it would in a pager: one width past the old one.
                    owner.offset.snapTo(moved + if (moved < 0) owner.width else -owner.width)
                }
                owner.offset.animateTo(0f, tween(SETTLE_MS))
            }
        }
    }
}
