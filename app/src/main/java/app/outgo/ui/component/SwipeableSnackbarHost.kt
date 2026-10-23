package app.outgo.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/** A [SnackbarHost] whose snackbar can be swiped left or right to dismiss it. */
@Composable
fun SwipeableSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState, modifier) { data -> SwipeToDismissSnackbar(data) }
}

@Composable
private fun SwipeToDismissSnackbar(data: SnackbarData) {
    // Fresh drag state for every message, so a swiped-away offset never carries over.
    key(data) {
        val offset = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()
        Snackbar(
            snackbarData = data,
            modifier = Modifier
                .graphicsLayer {
                    translationX = offset.value
                    alpha = if (size.width == 0f) 1f else 1f - (abs(offset.value) / size.width).coerceIn(0f, 1f)
                }
                // Consumes the drag (like swipeStep), so the tab swipe around the screen ignores it.
                .pointerInput(Unit) {
                    val flingVelocity = 800.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
                        val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, overSlop ->
                            change.consume()
                            scope.launch { offset.snapTo(offset.value + overSlop) }
                        } ?: return@awaitEachGesture
                        tracker.addPosition(start.uptimeMillis, start.position)
                        horizontalDrag(start.id) { change ->
                            tracker.addPosition(change.uptimeMillis, change.position)
                            change.consume()
                            val dx = change.positionChange().x
                            scope.launch { offset.snapTo(offset.value + dx) }
                        }
                        val velocity = tracker.calculateVelocity().x
                        val width = size.width.toFloat()
                        scope.launch {
                            val x = offset.value
                            val dismiss = abs(x) > width * 0.35f ||
                                (abs(velocity) > flingVelocity && sign(velocity) == sign(x))
                            if (dismiss) {
                                offset.animateTo(sign(x) * width, initialVelocity = velocity)
                                data.dismiss()
                            } else {
                                offset.animateTo(0f, initialVelocity = velocity)
                            }
                        }
                    }
                },
        )
    }
}
