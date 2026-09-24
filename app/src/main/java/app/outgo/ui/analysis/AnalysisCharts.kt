package app.outgo.ui.analysis

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.outgo.R
import app.outgo.ui.home.compactAmount
import app.outgo.ui.theme.ExpenseRed
import app.outgo.ui.theme.IncomeGreen
import app.outgo.ui.theme.TransferBlue
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/**
 * Hand-drawn charts for the Analysis screen, in the same idiom as
 * [app.outgo.ui.home.StackedDivergingBarChart]: plain [Canvas]/[drawWithCache], one
 * [TextMeasurer] per chart, and every number already computed in [AnalysisModels].
 *
 * In [AnalysisMode.ALL] a chart draws both kinds at once — expense red, income green — rather than
 * a net figure, so the gross amounts stay visible.
 */

private val DashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))

/** Grows 0f -> 1f the first time a page is shown, and sits at 1f on a revisit. */
@Composable
internal fun introProgress(animate: Boolean): Float {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(animate) {
        if (animate) progress.animateTo(1f, tween(durationMillis = 450)) else progress.snapTo(1f)
    }
    return progress.value
}

internal fun colorOf(mode: AnalysisMode): Color = if (mode == AnalysisMode.INCOME) IncomeGreen else ExpenseRed

// ------------------------------------------------------------------ section 2

/**
 * One ring per kind. In All mode expense is the outer ring and income the inner one, so the two
 * category sets stay separable instead of being summed into a meaningless whole.
 */
@Composable
fun DonutChart(
    expense: DonutUi,
    income: DonutUi,
    mode: AnalysisMode,
    /** Read inside the draw lambda, so selecting a slice redraws without recomposing. */
    selectedRootId: () -> Long?,
    centerLabel: String,
    onSelect: (Long?) -> Unit,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val outline = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.titleMedium
    val measurer = rememberTextMeasurer()
    val label = remember(centerLabel, labelStyle) { measurer.measure(centerLabel, labelStyle) }
    val rings = remember(expense, income, mode) { ringsOf(expense, income, mode) }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(rings) {
                    detectTapGestures { tap ->
                        onSelect(sliceAt(rings, tap, size.width.toFloat(), size.height.toFloat()))
                    }
                },
        ) {
            val selectedId = selectedRootId()
            val radius = size.minDimension / 2f
            rings.forEachIndexed { index, ring ->
                val stroke = radius * (if (rings.size == 1) 0.36f else 0.26f)
                val inset = if (index == 0) stroke / 2f else stroke * 1.9f
                val diameter = size.minDimension - inset * 2f
                val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                val arcSize = Size(diameter, diameter)
                if (ring.slices.isEmpty()) {
                    drawCircle(outline.copy(alpha = 0.15f), radius = diameter / 2f, style = Stroke(stroke))
                }
                ring.slices.forEach { slice ->
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
            }
            drawText(
                textLayoutResult = label,
                color = onSurface,
                topLeft = Offset((size.width - label.size.width) / 2f, (size.height - label.size.height) / 2f),
            )
        }
    }
}

private fun ringsOf(expense: DonutUi, income: DonutUi, mode: AnalysisMode): List<DonutUi> = when (mode) {
    AnalysisMode.EXPENSE -> listOf(expense)
    AnalysisMode.INCOME -> listOf(income)
    AnalysisMode.ALL -> listOf(expense, income)
}

/** Which slice a tap landed on, or null for the hole in the middle / outside the outermost ring. */
private fun sliceAt(rings: List<DonutUi>, tap: Offset, width: Float, height: Float): Long? {
    val centre = Offset(width / 2f, height / 2f)
    val radius = min(width, height) / 2f
    val distance = hypot(tap.x - centre.x, tap.y - centre.y) / radius
    if (distance > 1f) return null
    // Bands mirror the stroke geometry above: outer ring first, inner ring underneath it.
    val ring = when {
        rings.size == 1 -> if (distance < 0.55f) return null else rings[0]
        distance > 0.72f -> rings[0]
        distance > 0.45f -> rings[1]
        else -> return null
    }
    var angle = Math.toDegrees(atan2((tap.y - centre.y).toDouble(), (tap.x - centre.x).toDouble())).toFloat()
    if (angle < -90f) angle += 360f
    return ring.slices.firstOrNull { angle >= it.startAngle && angle < it.startAngle + it.sweepAngle }?.rootId
}

// ------------------------------------------------------------------ section 4

/**
 * Cumulative spend (or income) day by day, against the same run of the previous month. The value
 * axis rescales to whichever kind the mode switch shows, so a single kind always fills the chart.
 */
@Composable
fun PaceChart(pace: PaceUi, mode: AnalysisMode, progress: Float, modifier: Modifier = Modifier) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val fadedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val style = MaterialTheme.typography.labelSmall
    val measurer = rememberTextMeasurer()
    val units = Triple(
        stringResource(R.string.unit_thousand),
        stringResource(R.string.unit_million),
        stringResource(R.string.unit_billion),
    )

    val max = pace.maxOf(mode)
    // Axis ticks and their measured labels are computed once per (data, mode), never per frame.
    val yTicks = remember(max, style, units) {
        valueTicks(max).map { it to measurer.measure(compactAmount(it, units), style) }
    }
    val xTicks = remember(pace.daysInMonth, style) {
        axisDays(pace.daysInMonth).map { day ->
            day to measurer.measure(String.format(Locale.US, "%02d", day), style)
        }
    }
    val series = remember(pace, mode) {
        when (mode) {
            AnalysisMode.EXPENSE -> listOf(pace.expense to ExpenseRed)
            AnalysisMode.INCOME -> listOf(pace.income to IncomeGreen)
            AnalysisMode.ALL -> listOf(pace.expense to ExpenseRed, pace.income to IncomeGreen)
        }
    }

    Canvas(modifier = modifier) {
        val gutter = (yTicks.maxOfOrNull { it.second.size.width } ?: 0) + AxisGap.toPx()
        val labelHeight = (xTicks.firstOrNull()?.second?.size?.height ?: 0).toFloat() + AxisGap.toPx()
        val plotWidth = size.width - gutter
        val plotHeight = size.height - labelHeight

        yTicks.forEach { (value, label) ->
            val y = plotHeight - value.toFloat() / max * plotHeight
            drawLine(gridColor, Offset(0f, y), Offset(plotWidth, y), strokeWidth = 1f)
            drawText(
                textLayoutResult = label,
                color = axisColor,
                topLeft = Offset(plotWidth + AxisGap.toPx(), y - label.size.height / 2f),
            )
        }

        xTicks.forEachIndexed { index, (_, label) ->
            val x = plotWidth * index / (xTicks.size - 1).coerceAtLeast(1)
            // The first and last labels are pulled inside the plot so they are not clipped.
            val left = (x - label.size.width / 2f).coerceIn(0f, plotWidth - label.size.width)
            drawText(label, color = axisColor, topLeft = Offset(left, plotHeight + AxisGap.toPx()))
        }

        series.forEach { (line, color) ->
            drawPath(
                linePath(line.previous, pace.daysInMonth, max, plotWidth, plotHeight, LineHeadroom),
                fadedColor,
                alpha = 0.4f,
                style = Stroke(width = 3f, pathEffect = DashEffect),
            )
            drawPath(
                linePath(line.current, pace.daysInMonth, max, plotWidth, plotHeight, LineHeadroom),
                color,
                alpha = progress,
                style = Stroke(width = 5f),
            )
        }
    }
}

/** Gap between the plot and its axis labels. */
private val AxisGap = 4.dp

/** Half the widest line stroke, so a line at the maximum is drawn whole. */
private const val LineHeadroom = 3f

/** [headroom] keeps the peak's stroke from being clipped by the top edge of the plot. */
private fun linePath(
    values: List<Long>,
    daysInMonth: Int,
    max: Long,
    width: Float,
    height: Float,
    headroom: Float,
): Path {
    val path = Path()
    if (values.isEmpty()) return path
    val stepX = width / (daysInMonth - 1).coerceAtLeast(1)
    val usable = height - headroom
    values.forEachIndexed { index, value ->
        val x = index * stepX
        val y = height - value.toFloat() / max * usable
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    return path
}

private fun fractionPath(values: List<Float>, points: Int, width: Float, height: Float): Path {
    val path = Path()
    if (values.isEmpty()) return path
    val stepX = width / (points - 1).coerceAtLeast(1)
    values.forEachIndexed { index, value ->
        val x = index * stepX
        val y = height - value * height
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    return path
}

// -------------------------------------------------------------- sections 5, Y2

/**
 * Month bars. In All mode income grows up from a shared baseline and expense down, the same
 * idiom as the Home chart; otherwise one kind grows up from the bottom.
 */
@Composable
fun MonthBars(
    bars: List<MonthBar>,
    labels: List<String>,
    mode: AnalysisMode,
    averageFraction: Float,
    onSelect: (Int) -> Unit,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val style = MaterialTheme.typography.labelSmall
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
        val slot = size.width / bars.size.coerceAtLeast(1)
        val barWidth = slot * 0.55f
        val diverging = mode == AnalysisMode.ALL
        val baseline = if (diverging) plotHeight / 2f else plotHeight

        bars.forEachIndexed { index, bar ->
            val x = index * slot + (slot - barWidth) / 2f
            val alpha = if (bar.selected || bars.none { it.selected }) 1f else 0.4f
            if (diverging) {
                val up = bar.incomeFraction * baseline * progress
                val down = bar.expenseFraction * (plotHeight - baseline) * progress
                drawRect(IncomeGreen, Offset(x, baseline - up), Size(barWidth, up), alpha = alpha)
                drawRect(ExpenseRed, Offset(x, baseline), Size(barWidth, down), alpha = alpha)
            } else {
                val fraction = if (mode == AnalysisMode.INCOME) bar.incomeFraction else bar.expenseFraction
                val height = fraction * plotHeight * progress
                drawRect(colorOf(mode), Offset(x, plotHeight - height), Size(barWidth, height), alpha = alpha)
            }
            measured.getOrNull(index)?.let { label ->
                drawText(
                    textLayoutResult = label,
                    color = axisColor,
                    topLeft = Offset(index * slot + (slot - label.size.width) / 2f, plotHeight + 4f),
                )
            }
        }

        if (diverging) {
            drawLine(gridColor, Offset(0f, baseline), Offset(size.width, baseline), strokeWidth = 1.5f)
        } else {
            val y = plotHeight - averageFraction * plotHeight
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 2f, pathEffect = DashEffect)
        }
    }
}

// ------------------------------------------------------------------ section 6

/** One diverging row: increases run right, decreases left. */
@Composable
fun MoverBar(fraction: Float, increase: Boolean, kindColor: Color, progress: Float, modifier: Modifier = Modifier) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier = modifier) {
        val centre = size.width / 2f
        val width = fraction * centre * progress
        drawRect(
            color = kindColor,
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
    val base = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val style = MaterialTheme.typography.labelSmall
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
                color = if (day.intensity == 0f) base else lerp(base, ExpenseRed, day.intensity.coerceIn(0f, 1f)),
                topLeft = Offset(x + pad, y + pad),
                size = Size(cell - pad * 2, cell - pad * 2),
                cornerRadius = CornerRadius(cell * 0.18f),
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

// ------------------------------------------------------------------ section 8

@Composable
fun WeekdayBars(bars: List<WeekdayBar>, labels: List<String>, progress: Float, modifier: Modifier = Modifier) {
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val style = MaterialTheme.typography.labelSmall
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
                color = ExpenseRed,
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
    val countColor = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier = modifier) {
        val barHeight = size.height * 0.36f
        val gap = size.height * 0.28f
        listOf(bucket.countPercent to countColor, bucket.moneyPercent to ExpenseRed)
            .forEachIndexed { index, (percent, color) ->
                val y = index * (barHeight + gap)
                drawTrackBar(y, barHeight, size.width, track)
                drawRoundRect(
                    color = color,
                    topLeft = Offset(0f, y),
                    size = Size(size.width * (percent / 100f) * progress, barHeight),
                    cornerRadius = CornerRadius(barHeight / 2f),
                )
            }
    }
}

// ----------------------------------------------------------------- section 11

/** A single filled bar on a track, used by the transfer rows. */
@Composable
fun TransferBar(fraction: Float, progress: Float, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier = modifier) {
        val barHeight = size.height * 0.5f
        val y = (size.height - barHeight) / 2f
        drawTrackBar(y, barHeight, size.width, track)
        drawRoundRect(
            color = TransferBlue,
            topLeft = Offset(0f, y),
            size = Size(size.width * fraction * progress, barHeight),
            cornerRadius = CornerRadius(barHeight / 2f),
        )
    }
}

private fun DrawScope.drawTrackBar(y: Float, height: Float, width: Float, color: Color) {
    drawRoundRect(color, Offset(0f, y), Size(width, height), CornerRadius(height / 2f))
}

// ----------------------------------------------------------------- section Y4

/** Cumulative year-to-date against the same run of months last year. */
@Composable
fun YoyChart(series: YoySeries, mode: AnalysisMode, progress: Float, modifier: Modifier = Modifier) {
    val color = colorOf(mode)
    val fadedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = modifier.drawWithCache {
            val current = fractionPath(series.current, 12, size.width, size.height)
            val previous = fractionPath(series.previous, 12, size.width, size.height)
            onDrawBehind {
                drawLine(gridColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.5f)
                drawPath(previous, fadedColor, alpha = 0.4f, style = Stroke(width = 3f, pathEffect = DashEffect))
                drawPath(current, color, alpha = progress, style = Stroke(width = 5f))
            }
        },
    )
}

/** A flat coloured block used by legends and list bullets. */
@Composable
fun ColorDot(color: Color, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    Canvas(modifier = modifier.size(size)) { drawCircle(color) }
}
