package app.outgo.ui.analysis

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.outgo.ui.theme.ExpenseRed
import app.outgo.ui.theme.IncomeGreen
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/**
 * Hand-drawn charts for the Analysis screen, in the same idiom as
 * [app.outgo.ui.home.StackedDivergingBarChart]: plain [Canvas]/[drawWithCache], one
 * [TextMeasurer] per chart, and every number already computed in [AnalysisModels].
 */

private val DashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))

/** Grows 0f -> 1f the first time a month is shown, and sits at 1f on a revisit. */
@Composable
internal fun introProgress(animate: Boolean): Float {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(animate) {
        if (animate) progress.animateTo(1f, tween(durationMillis = 450)) else progress.snapTo(1f)
    }
    return progress.value
}

// ------------------------------------------------------------------ section 2

@Composable
fun DonutChart(
    donut: DonutUi,
    /** Read inside the draw lambda, so selecting a slice redraws without recomposing. */
    selectedRootId: () -> Long?,
    centerLabel: String,
    onSelect: (Long?) -> Unit,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val onSurface = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = androidx.compose.material3.MaterialTheme.typography.titleMedium
    val measurer = rememberTextMeasurer()
    val label = remember(centerLabel, labelStyle) { measurer.measure(centerLabel, labelStyle) }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(donut) {
                    detectTapGestures { tap ->
                        onSelect(sliceAt(donut, tap, size.width.toFloat(), size.height.toFloat()))
                    }
                },
        ) {
            val stroke = size.minDimension * 0.18f
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            val selectedId = selectedRootId()
            donut.slices.forEach { slice ->
                val selected = selectedId != null && slice.rootId == selectedId
                drawArc(
                    color = Color(slice.color),
                    startAngle = slice.startAngle,
                    sweepAngle = slice.sweepAngle * progress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    alpha = if (selectedId == null || selected) 1f else 0.35f,
                    style = Stroke(width = if (selected) stroke * 1.25f else stroke),
                )
            }
            drawText(
                textLayoutResult = label,
                color = onSurface,
                topLeft = Offset((size.width - label.size.width) / 2f, (size.height - label.size.height) / 2f),
            )
            if (donut.slices.isEmpty()) {
                drawCircle(color = onSurfaceVariant.copy(alpha = 0.2f), radius = diameter / 2f, style = Stroke(stroke))
            }
        }
    }
}

/** Which slice a tap landed on, or null for the hole in the middle / outside the ring. */
private fun sliceAt(donut: DonutUi, tap: Offset, width: Float, height: Float): Long? {
    val centre = Offset(width / 2f, height / 2f)
    val radius = min(width, height) / 2f
    val distance = hypot(tap.x - centre.x, tap.y - centre.y)
    if (distance < radius * 0.5f || distance > radius) return null
    var angle = Math.toDegrees(atan2((tap.y - centre.y).toDouble(), (tap.x - centre.x).toDouble())).toFloat()
    if (angle < -90f) angle += 360f
    return donut.slices.firstOrNull { angle >= it.startAngle && angle < it.startAngle + it.sweepAngle }?.rootId
}

// ------------------------------------------------------------------ section 4

@Composable
fun PaceChart(pace: PaceUi, progress: Float, modifier: Modifier = Modifier) {
    val lineColor = ExpenseRed
    val previousColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = modifier.drawWithCache {
            // Both paths are built once per (data, size) — never inside the draw lambda.
            val current = pathOf(pace.current, pace.daysInMonth, size.width, size.height)
            val previous = pathOf(pace.previous, pace.daysInMonth, size.width, size.height)
            onDrawBehind {
                drawLine(gridColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.5f)
                drawPath(previous, previousColor, alpha = 0.45f, style = Stroke(width = 3f, pathEffect = DashEffect))
                drawPath(current, lineColor, alpha = progress, style = Stroke(width = 5f))
            }
        },
    )
}

private fun pathOf(values: List<Float>, daysInMonth: Int, width: Float, height: Float): Path {
    val path = Path()
    if (values.isEmpty()) return path
    val stepX = width / (daysInMonth - 1).coerceAtLeast(1)
    values.forEachIndexed { index, value ->
        val x = index * stepX
        val y = height - value * height
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    return path
}

// ------------------------------------------------------------------ section 5

@Composable
fun TrendBars(
    bars: List<TrendBar>,
    labels: List<String>,
    averageFraction: Float,
    onSelect: (Int) -> Unit,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val barColor = ExpenseRed
    val axisColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant
    val style = androidx.compose.material3.MaterialTheme.typography.labelSmall
    val measurer = rememberTextMeasurer()
    val measured = remember(labels, style) { labels.map { measurer.measure(it, style) } }

    Canvas(
        modifier = modifier.pointerInput(bars) {
            detectTapGestures { tap ->
                val index = (tap.x / (size.width / bars.size.coerceAtLeast(1))).toInt()
                bars.getOrNull(index)?.let { onSelect(it.monthKey) }
            }
        },
    ) {
        val labelHeight = (measured.firstOrNull()?.size?.height ?: 0).toFloat() + 6f
        val plotHeight = size.height - labelHeight
        val slot = size.width / bars.size
        val barWidth = slot * 0.55f
        bars.forEachIndexed { index, bar ->
            val height = bar.fraction * plotHeight * progress
            drawRect(
                color = barColor,
                topLeft = Offset(index * slot + (slot - barWidth) / 2f, plotHeight - height),
                size = Size(barWidth, height),
                alpha = if (bar.selected) 1f else 0.4f,
            )
            measured.getOrNull(index)?.let { label ->
                drawText(
                    textLayoutResult = label,
                    color = axisColor,
                    topLeft = Offset(index * slot + (slot - label.size.width) / 2f, plotHeight + 4f),
                )
            }
        }
        val y = plotHeight - averageFraction * plotHeight
        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 2f, pathEffect = DashEffect)
    }
}

// ------------------------------------------------------------------ section 6

/** One diverging row: increases run right from the centre in red, decreases left in green. */
@Composable
fun MoverBar(fraction: Float, increase: Boolean, progress: Float, modifier: Modifier = Modifier) {
    val color = if (increase) ExpenseRed else IncomeGreen
    val gridColor = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier = modifier) {
        val centre = size.width / 2f
        val width = fraction * centre * progress
        drawRect(
            color = color,
            topLeft = Offset(if (increase) centre else centre - width, size.height * 0.2f),
            size = Size(width, size.height * 0.6f),
        )
        drawLine(gridColor, Offset(centre, 0f), Offset(centre, size.height), strokeWidth = 1.5f)
    }
}

// ------------------------------------------------------------------ section 7

@Composable
fun HeatmapGrid(
    heatmap: HeatmapUi,
    onDayClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 7,
) {
    val base = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
    val heat = ExpenseRed
    val onSurfaceVariant = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
    val style = androidx.compose.material3.MaterialTheme.typography.labelSmall
    val measurer = rememberTextMeasurer()
    val dayLabels = remember(heatmap.cells.size, style) {
        heatmap.cells.map { measurer.measure(it.dayOfMonth.toString(), style) }
    }

    Canvas(
        modifier = modifier.pointerInput(heatmap) {
            detectTapGestures { tap ->
                val cell = size.width / columns.toFloat()
                val column = (tap.x / cell).toInt().coerceIn(0, columns - 1)
                val row = (tap.y / cell).toInt()
                val index = row * columns + column - heatmap.leadingBlanks
                heatmap.cells.getOrNull(index)?.let { onDayClick(it.startMillis) }
            }
        },
    ) {
        val cell = size.width / columns
        val pad = cell * 0.08f
        heatmap.cells.forEachIndexed { index, day ->
            val slot = index + heatmap.leadingBlanks
            val x = (slot % columns) * cell
            val y = (slot / columns) * cell
            drawRoundRect(
                color = if (day.intensity == 0f) base else lerpColor(base, heat, day.intensity),
                topLeft = Offset(x + pad, y + pad),
                size = Size(cell - pad * 2, cell - pad * 2),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cell * 0.18f),
            )
            val label = dayLabels[index]
            drawText(
                textLayoutResult = label,
                color = if (day.intensity > 0.55f) Color.White else onSurfaceVariant,
                topLeft = Offset(x + (cell - label.size.width) / 2f, y + (cell - label.size.height) / 2f),
            )
        }
    }
}

private fun lerpColor(from: Color, to: Color, fraction: Float): Color =
    androidx.compose.ui.graphics.lerp(from, to, fraction.coerceIn(0f, 1f))

// ------------------------------------------------------------------ section 8

@Composable
fun WeekdayBars(
    bars: List<WeekdayBar>,
    labels: List<String>,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val barColor = ExpenseRed
    val axisColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
    val style = androidx.compose.material3.MaterialTheme.typography.labelSmall
    val measurer = rememberTextMeasurer()
    val measured = remember(labels, style) { labels.map { measurer.measure(it, style) } }

    Canvas(modifier = modifier) {
        val labelHeight = (measured.firstOrNull()?.size?.height ?: 0).toFloat() + 6f
        val plotHeight = size.height - labelHeight
        val slot = size.width / bars.size.coerceAtLeast(1)
        val barWidth = slot * 0.5f
        bars.forEachIndexed { index, bar ->
            val height = bar.fraction * plotHeight * progress
            drawRect(
                color = barColor,
                topLeft = Offset(index * slot + (slot - barWidth) / 2f, plotHeight - height),
                size = Size(barWidth, height),
                alpha = 0.85f,
            )
            measured.getOrNull(index)?.let { label ->
                drawText(
                    textLayoutResult = label,
                    color = axisColor,
                    topLeft = Offset(index * slot + (slot - label.size.width) / 2f, plotHeight + 4f),
                )
            }
        }
    }
}

// ------------------------------------------------------------------ section 9

/** Two bars per bucket: share of transactions next to share of money. */
@Composable
fun BucketBars(bucket: SizeBucket, progress: Float, modifier: Modifier = Modifier) {
    val countColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
    val moneyColor = ExpenseRed
    val track = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier = modifier) {
        val barHeight = size.height * 0.36f
        val gap = size.height * 0.28f
        listOf(bucket.countPercent to countColor, bucket.moneyPercent to moneyColor)
            .forEachIndexed { index, (percent, color) ->
                val y = index * (barHeight + gap)
                drawRoundRect(
                    color = track,
                    topLeft = Offset(0f, y),
                    size = Size(size.width, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barHeight / 2f),
                )
                drawRoundRect(
                    color = color,
                    topLeft = Offset(0f, y),
                    size = Size(size.width * (percent / 100f) * progress, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barHeight / 2f),
                )
            }
    }
}

/** A flat coloured block used by legends and list bullets. */
@Composable
fun ColorDot(color: Color, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    Canvas(modifier = modifier.size(size)) { drawCircle(color) }
}
