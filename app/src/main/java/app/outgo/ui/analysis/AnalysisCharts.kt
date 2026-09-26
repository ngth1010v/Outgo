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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.outgo.R
import app.outgo.ui.home.compactAmount
import app.outgo.ui.theme.ExpenseRed
import app.outgo.ui.theme.IncomeGreen
import app.outgo.ui.theme.TransferBlue
import java.time.Year
import java.time.YearMonth
import java.util.Locale
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/**
 * Hand-drawn charts for the Analysis screen, in the same idiom as
 * [app.outgo.ui.home.StackedDivergingBarChart]: plain [Canvas], one
 * text measurer per chart, and every number already computed in [AnalysisModels].
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
    centerLabelColor: Color,
    /** Change against the previous period, drawn under [centerLabel]. */
    centerDelta: String,
    centerDeltaColor: Color,
    onSelect: (Long?) -> Unit,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val outline = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.titleMedium
    val deltaStyle = MaterialTheme.typography.labelMedium
    val measurer = rememberTextMeasurer()
    val label = remember(centerLabel, labelStyle) { measurer.measure(centerLabel, labelStyle) }
    val delta = remember(centerDelta, deltaStyle) { measurer.measure(centerDelta, deltaStyle) }
    val rings = remember(expense, income, mode) { ringsOf(expense, income, mode) }
    val emphasis = rememberSliceEmphasis(selectedRootId)

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
            val dim = emphasis.dim.value
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
                    val lift = emphasis.of(slice.rootId)
                    drawArc(
                        color = Color(slice.color),
                        startAngle = slice.startAngle,
                        sweepAngle = slice.sweepAngle * progress,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        // Selected: full colour and 1.25x as thick; the rest fade to 0.35 while one is.
                        alpha = 1f - 0.65f * dim * (1f - lift),
                        style = Stroke(width = stroke * (1f + 0.25f * lift)),
                    )
                }
            }
            // The amount and its change are stacked around the middle of the hole.
            val top = (size.height - label.size.height - delta.size.height) / 2f
            drawText(
                textLayoutResult = label,
                color = centerLabelColor,
                topLeft = Offset((size.width - label.size.width) / 2f, top),
            )
            drawText(
                textLayoutResult = delta,
                color = centerDeltaColor,
                topLeft = Offset((size.width - delta.size.width) / 2f, top + label.size.height),
            )
        }
    }
}

/**
 * Animated selection for [DonutChart]: [lift] of the selected slice runs 0 -> 1 while the one it
 * replaces runs back from wherever it was, and [dim] fades the other slices in and out.
 */
private class SliceEmphasis {
    val dim = Animatable(0f)
    var selected by mutableStateOf<Long?>(null)
    var released by mutableStateOf<Long?>(null)
    val lift = Animatable(0f)
    val drop = Animatable(0f)

    fun of(rootId: Long?): Float = when {
        rootId == null -> 0f
        rootId == selected -> lift.value
        rootId == released -> drop.value
        else -> 0f
    }
}

private val SliceSpec = tween<Float>(durationMillis = 220)

/** Follows [selectedRootId] outside composition, so a tap animates the ring without recomposing it. */
@Composable
private fun rememberSliceEmphasis(selectedRootId: () -> Long?): SliceEmphasis {
    val emphasis = remember { SliceEmphasis() }
    val current by rememberUpdatedState(selectedRootId)
    LaunchedEffect(emphasis) {
        var first = true
        snapshotFlow { current() }.collect { id ->
            val target = if (id == null) 0f else 1f
            if (first) {
                // A revisited page shows its selection as it was, without replaying it.
                first = false
                emphasis.selected = id
                emphasis.lift.snapTo(target)
                emphasis.dim.snapTo(target)
                return@collect
            }
            emphasis.drop.snapTo(emphasis.lift.value)
            emphasis.released = emphasis.selected
            emphasis.lift.snapTo(0f)
            emphasis.selected = id
            launch { emphasis.drop.animateTo(0f, SliceSpec) }
            launch { emphasis.lift.animateTo(target, SliceSpec) }
            launch { emphasis.dim.animateTo(target, SliceSpec) }
        }
    }
    return emphasis
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
fun PaceChart(pace: PaceUi, mode: AnalysisMode, zero: Boolean, progress: Float, modifier: Modifier = Modifier) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val fadedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val style = MaterialTheme.typography.labelSmall
    val measurer = rememberTextMeasurer()

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
    var height by remember { mutableIntStateOf(0) }
    val values = remember(series) { series.flatMap { (line, _) -> line.current + line.previous } }
    val axis = rememberValueAxis(values, zero, height, xTicks.firstOrNull()?.second?.size?.height ?: 0)
    val yTicks = rememberAxisLabels(axis.ticks)

    Canvas(modifier = modifier.onSizeChanged { height = it.height }) {
        val gutter = gutterOf(yTicks)
        val labelHeight = (xTicks.firstOrNull()?.second?.size?.height ?: 0).toFloat() + AxisGap.toPx()
        val plotWidth = size.width - gutter
        val plotHeight = size.height - labelHeight
        fun y(value: Long) = axis.y(value, plotHeight)

        yTicks.forEach { (value, label) ->
            val y = y(value)
            drawLine(gridColor, Offset(0f, y), Offset(plotWidth, y), strokeWidth = if (value == 0L) 2f else 1f)
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
                linePath(line.previous, pace.daysInMonth, plotWidth, ::y),
                fadedColor,
                alpha = 0.4f,
                style = Stroke(width = 3f, pathEffect = DashEffect),
            )
            drawPath(
                linePath(line.current, pace.daysInMonth, plotWidth, ::y),
                color,
                alpha = progress,
                style = Stroke(width = 5f),
            )
        }
    }
}

/** Gap between the plot and its axis labels. */
private val AxisGap = 4.dp

/**
 * Compact value-axis labels ("1.5M") for [values], measured once per list, never per frame.
 * A negative value is labelled with its sign, for the lower half of a diverging chart.
 */
@Composable
private fun rememberAxisLabels(values: List<Long>): List<Pair<Long, TextLayoutResult>> {
    val style = MaterialTheme.typography.labelSmall
    val measurer = rememberTextMeasurer()
    val units = Triple(
        stringResource(R.string.unit_thousand),
        stringResource(R.string.unit_million),
        stringResource(R.string.unit_billion),
    )
    return remember(values, style, units) { values.map { it to measurer.measure(compactAmount(it, units), style) } }
}

/** Width the value-axis labels take on the right of the plot, gap included. */
private fun Density.gutterOf(labels: List<Pair<Long, TextLayoutResult>>): Float =
    (labels.maxOfOrNull { it.second.size.width } ?: 0) + AxisGap.toPx()

/** Half the widest line stroke, so a line at either end of the axis is drawn whole. */
private const val LineHeadroom = 3f

/** Closest two value-axis ticks may sit; the knob for how dense the axis is. */
private val MinTickGap = 24.dp

/**
 * [valueAxis] fitted to [values] on a chart [heightPx] tall, of which [xLabelHeightPx] (plus
 * [AxisGap]) goes to the day/month labels. The height is only known after the first layout, so
 * that first frame has no ticks.
 */
@Composable
private fun rememberValueAxis(values: List<Long>, zero: Boolean, heightPx: Int, xLabelHeightPx: Int): ValueAxis {
    val density = LocalDensity.current
    return remember(values, zero, heightPx, xLabelHeightPx, density) {
        with(density) {
            val plotPx = heightPx - xLabelHeightPx - AxisGap.toPx() - LineHeadroom * 2
            valueAxis(values.minOrNull() ?: 0L, values.maxOrNull() ?: 0L, plotPx, MinTickGap.toPx(), zero)
        }
    }
}

/** Where [value] sits on a plot [plotHeight] tall, keeping [LineHeadroom] clear at both ends. */
private fun ValueAxis.y(value: Long, plotHeight: Float): Float {
    val usable = plotHeight - LineHeadroom * 2
    return LineHeadroom + usable - ((value - low) / (high - low)).toFloat() * usable
}

private fun linePath(values: List<Long>, daysInMonth: Int, width: Float, y: (Long) -> Float): Path {
    val path = Path()
    if (values.isEmpty()) return path
    val stepX = width / (daysInMonth - 1).coerceAtLeast(1)
    values.forEachIndexed { index, value ->
        val x = index * stepX
        if (index == 0) path.moveTo(x, y(value)) else path.lineTo(x, y(value))
    }
    return path
}

/** Value i sits at xs[i], a fraction of [width]; [fromZero] first starts the line at 0 on the left edge. */
private fun monthEndPath(values: List<Long>, xs: List<Float>, width: Float, fromZero: Boolean, y: (Long) -> Float): Path {
    val path = Path()
    if (fromZero) path.moveTo(0f, y(0))
    values.forEachIndexed { index, value ->
        val x = xs[index] * width
        if (index == 0 && !fromZero) path.moveTo(x, y(value)) else path.lineTo(x, y(value))
    }
    return path
}

// -------------------------------------------------------------- sections 5, Y2

/**
 * Month bars. In All mode income grows up from 0 and expense down, the same idiom as the Home
 * chart; otherwise one kind grows up. Unless [zero], the axis fits the bars, and a bar grows from
 * the bottom of the plot when 0 is below it.
 */
@Composable
fun MonthBars(
    bars: List<MonthBar>,
    labels: List<String>,
    mode: AnalysisMode,
    average: Long,
    zero: Boolean,
    onSelect: (Int) -> Unit,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val style = MaterialTheme.typography.labelSmall
    val measurer = rememberTextMeasurer()
    val measured = remember(labels, style) { labels.map { measurer.measure(it, style) } }
    val diverging = mode == AnalysisMode.ALL
    var height by remember { mutableIntStateOf(0) }
    // In All mode expense counts down, below 0.
    val values = remember(bars, mode) {
        when (mode) {
            AnalysisMode.EXPENSE -> bars.map { it.expense }
            AnalysisMode.INCOME -> bars.map { it.income }
            AnalysisMode.ALL -> bars.flatMap { listOf(it.income, -it.expense) }
        }
    }
    val axis = rememberValueAxis(values, zero, height, measured.firstOrNull()?.size?.height ?: 0)
    val yTicks = rememberAxisLabels(axis.ticks)

    Canvas(
        modifier = modifier
            .onSizeChanged { height = it.height }
            .pointerInput(bars, yTicks) {
                detectTapGestures { tap ->
                    val index = (tap.x / ((size.width - gutterOf(yTicks)) / bars.size.coerceAtLeast(1))).toInt()
                    bars.getOrNull(index)?.let { onSelect(it.monthKey) }
                }
            },
    ) {
        val labelHeight = (measured.firstOrNull()?.size?.height ?: 0).toFloat() + 6f
        val plotHeight = size.height - labelHeight
        val plotWidth = size.width - gutterOf(yTicks)
        val slot = plotWidth / bars.size.coerceAtLeast(1)
        val barWidth = slot * 0.55f
        fun y(value: Long) = axis.y(value, plotHeight)
        val baseline = y(0).coerceIn(0f, plotHeight)

        yTicks.forEach { (value, label) ->
            val y = y(value)
            drawLine(gridColor, Offset(0f, y), Offset(plotWidth, y), strokeWidth = 1f)
            drawText(label, color = axisColor, topLeft = Offset(plotWidth + AxisGap.toPx(), y - label.size.height / 2f))
        }

        bars.forEachIndexed { index, bar ->
            val x = index * slot + (slot - barWidth) / 2f
            val alpha = if (bar.selected || bars.none { it.selected }) 1f else 0.4f
            // Positive grows up from the baseline, negative down.
            fun drawBar(value: Long, color: Color) {
                val grown = (baseline - y(value)) * progress
                drawRect(color, Offset(x, if (grown >= 0f) baseline - grown else baseline), Size(barWidth, abs(grown)), alpha = alpha)
            }
            if (diverging) {
                drawBar(bar.income, IncomeGreen)
                drawBar(-bar.expense, ExpenseRed)
            } else {
                drawBar(if (mode == AnalysisMode.INCOME) bar.income else bar.expense, colorOf(mode))
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
            drawLine(gridColor, Offset(0f, baseline), Offset(plotWidth, baseline), strokeWidth = 1.5f)
        } else {
            val y = y(average)
            drawLine(gridColor, Offset(0f, y), Offset(plotWidth, y), strokeWidth = 2f, pathEffect = DashEffect)
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

/** Average spend per weekday. Unless [zero], the axis fits the bars, which then grow from the bottom of the plot. */
@Composable
fun WeekdayBars(bars: List<WeekdayBar>, labels: List<String>, zero: Boolean, progress: Float, modifier: Modifier = Modifier) {
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val style = MaterialTheme.typography.labelSmall
    val measurer = rememberTextMeasurer()
    val measured = remember(labels, style) { labels.map { measurer.measure(it, style) } }
    var height by remember { mutableIntStateOf(0) }
    val values = remember(bars) { bars.map { it.average } }
    val axis = rememberValueAxis(values, zero, height, measured.firstOrNull()?.size?.height ?: 0)
    val yTicks = rememberAxisLabels(axis.ticks)

    Canvas(modifier = modifier.onSizeChanged { height = it.height }) {
        val labelHeight = (measured.firstOrNull()?.size?.height ?: 0).toFloat() + 6f
        val plotHeight = size.height - labelHeight
        val plotWidth = size.width - gutterOf(yTicks)
        val slot = plotWidth / bars.size.coerceAtLeast(1)
        val barWidth = slot * 0.5f
        fun y(value: Long) = axis.y(value, plotHeight)
        val baseline = y(0).coerceIn(0f, plotHeight)
        yTicks.forEach { (value, label) ->
            val lineY = y(value)
            drawLine(gridColor, Offset(0f, lineY), Offset(plotWidth, lineY), strokeWidth = 1f)
            drawText(label, color = axisColor, topLeft = Offset(plotWidth + AxisGap.toPx(), lineY - label.size.height / 2f))
        }
        bars.forEachIndexed { index, bar ->
            val height = (baseline - y(bar.average)).coerceAtLeast(0f) * progress
            drawRect(
                color = ExpenseRed,
                topLeft = Offset(index * slot + (slot - barWidth) / 2f, baseline - height),
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

/**
 * Cumulative year-to-date against the same run of months last year, on a day-of-year axis: each
 * month's running total sits on that month's last day. With [zero] both lines start at 0 on 01/01;
 * otherwise they start at January's total and the axis fits the lines.
 */
@Composable
fun YoyChart(series: YoySeries, year: Int, mode: AnalysisMode, zero: Boolean, progress: Float, modifier: Modifier = Modifier) {
    val color = colorOf(mode)
    val fadedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val style = MaterialTheme.typography.labelSmall
    val measurer = rememberTextMeasurer()
    val length = Year.of(year).length()
    // Seven equal gaps' worth of day-of-year labels, always 01/01 and 31/12 at the ends.
    val xTicks = remember(year, style) {
        axisDays(length, 7).map { day ->
            val date = Year.of(year).atDay(day)
            measurer.measure(String.format(Locale.US, "%02d/%02d", date.dayOfMonth, date.monthValue), style)
        }
    }
    val monthEnds = remember(year) {
        (1..12).map { (YearMonth.of(year, it).atEndOfMonth().dayOfYear - 1f) / (length - 1) }
    }
    var height by remember { mutableIntStateOf(0) }
    val values = remember(series) { series.current + series.previous }
    val axis = rememberValueAxis(values, zero, height, xTicks.firstOrNull()?.size?.height ?: 0)
    val yTicks = rememberAxisLabels(axis.ticks)

    Canvas(modifier = modifier.onSizeChanged { height = it.height }) {
        val plotHeight = size.height - (xTicks.firstOrNull()?.size?.height ?: 0) - AxisGap.toPx()
        // Inset by half a label so 01/01 and 31/12 sit centred under the ends, keeping the gaps equal.
        val inset = (xTicks.maxOfOrNull { it.size.width } ?: 0) / 2f
        val gridWidth = size.width - gutterOf(yTicks)
        val plotWidth = gridWidth - inset * 2
        fun y(value: Long) = axis.y(value, plotHeight)
        yTicks.forEach { (value, label) ->
            val lineY = y(value)
            drawLine(gridColor, Offset(0f, lineY), Offset(gridWidth, lineY), strokeWidth = if (value == 0L) 1.5f else 1f)
            drawText(label, color = fadedColor, topLeft = Offset(gridWidth + AxisGap.toPx(), lineY - label.size.height / 2f))
        }
        xTicks.forEachIndexed { index, label ->
            val x = inset + plotWidth * index / (xTicks.size - 1)
            drawText(label, color = fadedColor, topLeft = Offset(x - label.size.width / 2f, plotHeight + AxisGap.toPx()))
        }
        translate(left = inset) {
            drawPath(
                monthEndPath(series.previous, monthEnds, plotWidth, zero, ::y),
                fadedColor,
                alpha = 0.4f,
                style = Stroke(width = 3f, pathEffect = DashEffect),
            )
            drawPath(
                monthEndPath(series.current, monthEnds, plotWidth, zero, ::y),
                color,
                alpha = progress,
                style = Stroke(width = 5f),
            )
        }
    }
}

/** A flat coloured block used by legends and list bullets. */
@Composable
fun ColorDot(color: Color, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    Canvas(modifier = modifier.size(size)) { drawCircle(color) }
}

// ------------------------------------------------------------------ accounts

/** One line per account through its month-end balances; [zero] makes the axis take in 0. */
@Composable
fun BalanceChart(balance: BalanceTrendUi, labels: List<String>, zero: Boolean, progress: Float, modifier: Modifier = Modifier) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val style = MaterialTheme.typography.labelSmall
    val measurer = rememberTextMeasurer()
    val xLabels = remember(labels, style) { labels.map { measurer.measure(it, style) } }
    var height by remember { mutableIntStateOf(0) }
    val values = remember(balance) { balance.lines.flatMap { it.values } }
    val axis = rememberValueAxis(values, zero, height, xLabels.firstOrNull()?.size?.height ?: 0)
    val yTicks = rememberAxisLabels(axis.ticks)

    Canvas(modifier = modifier.onSizeChanged { height = it.height }) {
        val gutter = gutterOf(yTicks)
        val plotWidth = size.width - gutter
        val plotHeight = size.height - (xLabels.firstOrNull()?.size?.height ?: 0) - AxisGap.toPx()
        fun y(value: Long) = axis.y(value, plotHeight)
        // Inset by half a label so the first and last months sit centred under their points.
        val inset = (xLabels.maxOfOrNull { it.size.width } ?: 0) / 2f
        val step = (plotWidth - inset * 2) / (balance.months.size - 1).coerceAtLeast(1)
        fun x(index: Int) = inset + index * step

        yTicks.forEach { (value, label) ->
            val lineY = y(value)
            drawLine(gridColor, Offset(0f, lineY), Offset(plotWidth, lineY), strokeWidth = if (value == 0L) 2f else 1f)
            drawText(label, color = axisColor, topLeft = Offset(plotWidth + AxisGap.toPx(), lineY - label.size.height / 2f))
        }
        xLabels.forEachIndexed { index, label ->
            drawText(label, color = axisColor, topLeft = Offset(x(index) - label.size.width / 2f, plotHeight + AxisGap.toPx()))
        }
        balance.lines.forEach { line ->
            val color = Color(line.color)
            if (line.values.size == 1) {
                drawCircle(color, radius = 5f, center = Offset(x(0), y(line.values[0])), alpha = progress)
                return@forEach
            }
            val path = Path()
            line.values.forEachIndexed { index, value ->
                if (index == 0) path.moveTo(x(index), y(value)) else path.lineTo(x(index), y(value))
            }
            drawPath(path, color, alpha = progress, style = Stroke(width = 4f))
        }
    }
}

/**
 * One line per account through its end-of-day balances. Unless [zero], the value axis hugs the
 * lines rather than reaching down to 0, so a day's movement stays visible on a large balance.
 * Seven day labels, always the 1st and the month's last day, each under its own day.
 */
@Composable
fun DailyBalanceChart(lines: List<BalanceLine>, daysInMonth: Int, zero: Boolean, progress: Float, modifier: Modifier = Modifier) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val style = MaterialTheme.typography.labelSmall
    val measurer = rememberTextMeasurer()
    val xTicks = remember(daysInMonth, style) {
        axisDays(daysInMonth, 7).map { day -> day to measurer.measure(String.format(Locale.US, "%02d", day), style) }
    }
    var height by remember { mutableIntStateOf(0) }
    val values = remember(lines) { lines.flatMap { it.values } }
    val axis = rememberValueAxis(values, zero, height, xTicks.firstOrNull()?.second?.size?.height ?: 0)
    val yTicks = rememberAxisLabels(axis.ticks)

    Canvas(modifier = modifier.onSizeChanged { height = it.height }) {
        val gutter = gutterOf(yTicks)
        val plotWidth = size.width - gutter
        val plotHeight = size.height - (xTicks.firstOrNull()?.second?.size?.height ?: 0) - AxisGap.toPx()
        fun y(value: Long) = axis.y(value, plotHeight)
        // Inset by half a label so 01 and the last day sit centred under their points.
        val inset = (xTicks.maxOfOrNull { it.second.size.width } ?: 0) / 2f
        val step = (plotWidth - inset * 2) / (daysInMonth - 1).coerceAtLeast(1)
        fun x(index: Int) = inset + index * step

        yTicks.forEach { (value, label) ->
            val lineY = y(value)
            drawLine(gridColor, Offset(0f, lineY), Offset(plotWidth, lineY), strokeWidth = if (value == 0L) 2f else 1f)
            drawText(label, color = axisColor, topLeft = Offset(plotWidth + AxisGap.toPx(), lineY - label.size.height / 2f))
        }
        xTicks.forEach { (day, label) ->
            drawText(label, color = axisColor, topLeft = Offset(x(day - 1) - label.size.width / 2f, plotHeight + AxisGap.toPx()))
        }
        lines.forEach { line ->
            val color = Color(line.color)
            if (line.values.size == 1) {
                drawCircle(color, radius = 5f, center = Offset(x(0), y(line.values[0])), alpha = progress)
                return@forEach
            }
            val path = Path()
            line.values.forEachIndexed { index, value ->
                if (index == 0) path.moveTo(x(index), y(value)) else path.lineTo(x(index), y(value))
            }
            drawPath(path, color, alpha = progress, style = Stroke(width = 4f))
        }
    }
}
