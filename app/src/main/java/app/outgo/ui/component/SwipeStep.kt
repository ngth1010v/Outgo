package app.outgo.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

private val SWIPE_DISTANCE = 40.dp
private const val SETTLE_MS = 200

/** Horizontal shift of the content a [swipeStep] drags; apply it with [swipeShift]. */
@Composable
fun rememberSwipeOffset() = remember { Animatable(0f) }

/** Moves the content with the finger. Put it after (inside) the [swipeStep] that drives [offset]. */
fun Modifier.swipeShift(offset: Animatable<Float, AnimationVector1D>) = graphicsLayer { translationX = offset.value }

/**
 * Horizontal swipe = step to a neighbor. [target] gets `next = true` when the finger moves
 * right-to-left (go to the right neighbor) and the touch-down position, and returns the step
 * to run on release, or null to leave the swipe to an outer [swipeStep] (inner ones see it first).
 * A claimed swipe is consumed, so it cancels clicks underneath. While dragging, [offset] follows
 * the finger; on a step the new content slides in from the side the finger came from.
 */
@Composable
fun Modifier.swipeStep(
    offset: Animatable<Float, AnimationVector1D>,
    target: (next: Boolean, down: Offset) -> (() -> Unit)?,
): Modifier {
    val currentTarget by rememberUpdatedState(target)
    val scope = rememberCoroutineScope()
    return pointerInput(Unit) {
        val minDistance = SWIPE_DISTANCE.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var step: (() -> Unit)? = null
            var dx = 0f
            val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, overSlop ->
                step = currentTarget(overSlop < 0, down.position)
                if (step != null) {
                    change.consume()
                    dx = overSlop
                }
            } ?: return@awaitEachGesture
            val claimed = step ?: return@awaitEachGesture
            val next = dx < 0
            // Only toward the claimed neighbor: there is nothing to show on the other side.
            fun shift() = if (next) dx.coerceAtMost(0f) else dx.coerceAtLeast(0f)
            scope.launch { offset.snapTo(shift()) }
            val finished = horizontalDrag(start.id) { change ->
                dx += change.positionChange().x
                change.consume()
                scope.launch { offset.snapTo(shift()) }
            }
            val width = size.width.toFloat()
            scope.launch {
                if (finished && abs(shift()) > minDistance) {
                    claimed()
                    // The new content sits where it would in a pager: one width past the old one.
                    offset.snapTo(shift() + if (next) width else -width)
                }
                offset.animateTo(0f, tween(SETTLE_MS))
            }
        }
    }
}
