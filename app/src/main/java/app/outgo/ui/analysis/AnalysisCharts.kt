package app.outgo.ui.analysis

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.outgo.R
import app.outgo.ui.home.compactAmount
import app.outgo.util.Money
import java.util.Locale
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.hypot
import kotlin.math.min

/**
 * Hand-drawn charts for the Analysis screen, in the same idiom as
 * [app.outgo.ui.home.StackedDivergingBarChart]: plain [Canvas], one
 * text measurer per chart, and every number already computed in [AnalysisModels].
 */

private val DashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))

/**
 * Grows 0f -> 1f the first time a page is shown, and sits at 1f on a revisit. A lambda, read by
 * the charts in their draw code: the intro redraws them without recomposing their sections.
 */
@Composable
internal fun introProgress(animate: Boolean): () -> Float {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(animate) {
        if (animate) progress.animateTo(1f, tween(durationMillis = 450)) else progress.snapTo(1f)
    }
    return remember(progress) { { progress.value } }
}

/**
 * Animated selection for [PieChart]: [lift] of the selected slice runs 0 -> 1 while the one it
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
private fun rememberValueAxis(values: List<Long>, zero: Boolean, heightPx: Int, xLabelHeightPx: Int, pad: Boolean = true): ValueAxis {
    val density = LocalDensity.current
    return remember(values, zero, heightPx, xLabelHeightPx, density, pad) {
        with(density) {
            val plotPx = heightPx - xLabelHeightPx - AxisGap.toPx() - LineHeadroom * 2
            valueAxis(values.minOrNull() ?: 0L, values.maxOrNull() ?: 0L, plotPx, MinTickGap.toPx(), zero, pad)
        }
    }
}

/** Where [value] sits on a plot [plotHeight] tall, keeping [LineHeadroom] clear at both ends. */
private fun ValueAxis.y(value: Long, plotHeight: Float): Float {
    val usable = plotHeight - LineHeadroom * 2
    return LineHeadroom + usable - ((value - low) / (high - low)).toFloat() * usable
}

private fun pathOf(points: List<Offset>): Path {
    val path = Path()
    points.forEachIndexed { index, point -> if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y) }
    return path
}

// ------------------------------------------------------------ line chart hover

/** A finger scrubbing a line chart, and the line it last picked out, which [hoverHit] favours. */
private class Scrub {
    var finger by mutableStateOf<Offset?>(null)

    /** Plain field: written while drawing, so it must not invalidate the draw. -1 before a pick. */
    var line = -1
}

/** How much closer another line must be before the hover leaves the line it is on. */
private val HoverStickiness = 24.dp

/**
 * Scrubs [scrub] along a line chart: a press that moves sideways right away, before a long press
 * would fire, follows the finger until it lifts. A press held still is left alone, so the card's
 * long-press drag still works, and so is a mostly vertical move, which scrolls the list.
 */
private fun Modifier.scrub(scrub: Scrub): Modifier = pointerInput(scrub) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val start = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            var moved: PointerInputChange? = null
            while (moved == null) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed || change.isConsumed) break
                val delta = change.position - down.position
                if (delta.getDistance() > viewConfiguration.touchSlop) {
                    if (abs(delta.x) <= abs(delta.y)) break
                    moved = change
                }
            }
            moved
        } ?: return@awaitEachGesture
        try {
            start.consume()
            scrub.finger = start.position
            drag(start.id) {
                it.consume()
                scrub.finger = it.position
            }
        } finally {
            scrub.finger = null
            scrub.line = -1
        }
    }
}

/** The point under a scrubbing finger: [line] and [point] index into the chart's point lists. */
private class HoverHit(val line: Int, val point: Int)

/**
 * On each line, the point nearest the finger across; of those, the one nearest the finger itself.
 * Measuring the last step in both axes lets a line that stops short (this month, up to today)
 * lose to one that runs under the finger. The line already picked counts [stickiness] closer, so
 * where lines cross or overlap the hover stays on it rather than flicking between them.
 */
private fun hoverHit(scrub: Scrub, lines: List<List<Offset>>, stickiness: Float, only: Int = -1): HoverHit? {
    val finger = scrub.finger ?: return null
    var best: HoverHit? = null
    var bestDistance = Float.MAX_VALUE
    lines.forEachIndexed { line, points ->
        // A focused line keeps the hover to itself.
        if (only >= 0 && line != only) return@forEachIndexed
        val point = points.indices.minByOrNull { abs(points[it].x - finger.x) } ?: return@forEachIndexed
        val distance = (points[point] - finger).getDistance() - if (line == scrub.line) stickiness else 0f
        if (distance < bestDistance) {
            bestDistance = distance
            best = HoverHit(line, point)
        }
    }
    scrub.line = best?.line ?: -1
    return best
}

/** Alpha of [line]: [rest] with nothing hovered, full for the hovered line, faded for the others. */
private fun HoverHit?.alpha(line: Int, rest: Float): Float = when {
    this == null -> rest
    this.line == line -> 1f
    else -> rest * 0.3f
}

/** Stroke multiplier of [line]: the hovered line draws thicker. */
private fun HoverHit?.grow(line: Int): Float = if (this?.line == line) 1.5f else 1f

private class HoverLook(
    val measurer: TextMeasurer,
    val style: TextStyle,
    val cross: Color,
    val pill: Color,
    val text: Color,
    val ring: Color,
)

@Composable
private fun rememberHoverLook(): HoverLook {
    val scheme = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.labelSmall
    val measurer = rememberTextMeasurer()
    return remember(scheme, style, measurer) {
        HoverLook(measurer, style, scheme.onSurfaceVariant.copy(alpha = 0.7f), scheme.inverseSurface, scheme.inverseOnSurface, scheme.surface)
    }
}

/**
 * Crosshair through [point], a dot on it, [value] in a pill on the value axis and [xText] in a
 * pill on the x axis. Measured per frame, which only happens while a finger scrubs.
 */
private fun DrawScope.drawHover(
    look: HoverLook,
    point: Offset,
    color: Color,
    xText: String,
    value: Long,
    plotWidth: Float,
    plotHeight: Float,
) {
    drawLine(look.cross, Offset(point.x, 0f), Offset(point.x, plotHeight), strokeWidth = 1.5f)
    drawLine(look.cross, Offset(0f, point.y), Offset(plotWidth, point.y), strokeWidth = 1.5f)
    drawCircle(look.ring, radius = 9f, center = point)
    drawCircle(color, radius = 6f, center = point)

    val padX = 4.dp.toPx()
    val padY = 1.dp.toPx()
    fun pill(label: TextLayoutResult, left: Float, top: Float) {
        val pillSize = Size(label.size.width + padX * 2, label.size.height + padY * 2)
        drawRoundRect(look.pill, Offset(left, top), pillSize, CornerRadius(pillSize.height / 2f))
        drawText(label, look.text, Offset(left + padX, top + padY))
    }
    // Right-aligned to the edge, so an amount wider than the axis labels grows over the plot.
    val valueLabel = look.measurer.measure(Money.groupThousands(value), look.style)
    val valueHeight = valueLabel.size.height + padY * 2
    pill(
        valueLabel,
        size.width - valueLabel.size.width - padX * 2,
        (point.y - valueHeight / 2f).coerceAtMost(size.height - valueHeight).coerceAtLeast(0f),
    )
    val xLabel = look.measurer.measure(xText, look.style)
    val xWidth = xLabel.size.width + padX * 2
    pill(
        xLabel,
        (point.x - xWidth / 2f).coerceAtMost(size.width - xWidth).coerceAtLeast(0f),
        (plotHeight + AxisGap.toPx() - padY).coerceAtMost(size.height - xLabel.size.height - padY * 2),
    )
}

// ------------------------------------------------------------------ accounts

/**
 * Shades each day-to-day step between [a] and [b], [above] where [a] leads and [below] where it
 * trails. A step where they cross is split at the crossing so each side gets its own colour.
 */
private fun DrawScope.drawGap(a: List<Long>, b: List<Long>, x: (Int) -> Float, y: (Long) -> Float, above: Color, below: Color) {
    fun colorOf(diff: Long) = if (diff > 0) above else below
    fun fill(points: List<Offset>, color: Color) = drawPath(pathOf(points).apply { close() }, color, alpha = 0.2f)
    for (i in 0 until minOf(a.size, b.size) - 1) {
        val a0 = Offset(x(i), y(a[i]))
        val a1 = Offset(x(i + 1), y(a[i + 1]))
        val b0 = Offset(x(i), y(b[i]))
        val b1 = Offset(x(i + 1), y(b[i + 1]))
        val d0 = a[i] - b[i]
        val d1 = a[i + 1] - b[i + 1]
        if ((d0 > 0 && d1 < 0) || (d0 < 0 && d1 > 0)) {
            // Both lines are straight over the step, so they cross where the gap hits 0.
            val cross = lerp(a0, a1, d0.toFloat() / (d0 - d1))
            fill(listOf(a0, cross, b0), colorOf(d0))
            fill(listOf(cross, a1, b1), colorOf(d1))
        } else if (d0 != 0L || d1 != 0L) {
            fill(listOf(a0, a1, b1, b0), colorOf(d0 + d1))
        }
    }
}

// ------------------------------------------------------------ line chart focus

/** How far from a line a tap may land and still focus it. */
private val FocusTapRadius = 20.dp

/** Each line's points as last drawn, for the tap hit test. A plain field: written while drawing. */
private class LineGeometry {
    var points: List<List<Offset>> = emptyList()
}

/** The focused line's id, kept after the focus clears so the ray and pill can fade out with it. */
private class ShownFocus {
    var id: Long? = null
}

private fun distanceToSegment(p: Offset, a: Offset, b: Offset): Float {
    val ab = b - a
    val length2 = ab.x * ab.x + ab.y * ab.y
    val t = if (length2 == 0f) 0f else (((p.x - a.x) * ab.x + (p.y - a.y) * ab.y) / length2).coerceIn(0f, 1f)
    return (p - (a + ab * t)).getDistance()
}

/** Index of the line nearest [tap], if one runs within [radius] of it; -1 otherwise. */
private fun lineAt(lines: List<List<Offset>>, tap: Offset, radius: Float): Int {
    var best = -1
    var bestDistance = radius
    lines.forEachIndexed { index, points ->
        if (points.isEmpty()) return@forEachIndexed
        val distance = if (points.size == 1) {
            (points[0] - tap).getDistance()
        } else {
            (0 until points.size - 1).minOf { distanceToSegment(tap, points[it], points[it + 1]) }
        }
        if (distance <= bestDistance) {
            bestDistance = distance
            best = index
        }
    }
    return best
}

/** What [drawBalanceLines] needs of the focused line: its index, how far the focus has faded in, its pill. */
private class FocusDraw(val line: Int, val amount: Float, val label: TextLayoutResult?, val text: Color)

/**
 * A dashed ray from the focused line's last point to the value axis, and the last value in a pill
 * of the line's colour on the axis, right-aligned to the edge when wider than the axis.
 */
private fun DrawScope.drawFocus(focus: FocusDraw, last: Offset, color: Color, plotWidth: Float) {
    val label = focus.label ?: return
    val alpha = focus.amount
    drawLine(color, last, Offset(plotWidth, last.y), strokeWidth = 1.5.dp.toPx(), pathEffect = DashEffect, alpha = alpha)
    val padX = 4.dp.toPx()
    val padY = 1.dp.toPx()
    val pillSize = Size(label.size.width + padX * 2, label.size.height + padY * 2)
    val left = plotWidth.coerceAtMost(size.width - pillSize.width).coerceAtLeast(0f)
    val top = (last.y - pillSize.height / 2f).coerceAtMost(size.height - pillSize.height).coerceAtLeast(0f)
    drawRoundRect(color, Offset(left, top), pillSize, CornerRadius(pillSize.height / 2f), alpha = alpha)
    drawText(label, focus.text, Offset(left + padX, top + padY), alpha = alpha)
}

/**
 * One line per account. The one under [scrub] is picked out; failing that, the [focus]ed one,
 * with the others faded and its ray and pill drawn. [xText] names point i on the x axis.
 */
private fun DrawScope.drawBalanceLines(
    lines: List<BalanceLine>,
    points: List<List<Offset>>,
    scrub: Scrub,
    progress: Float,
    focus: FocusDraw?,
    look: HoverLook,
    plotWidth: Float,
    plotHeight: Float,
    xText: (Int) -> String,
) {
    val focused = focus?.line ?: -1
    // Only a full focus locks the hover; a fading one lets it pick any line again.
    val hit = hoverHit(scrub, points, HoverStickiness.toPx(), if (focus != null && focus.amount == 1f) focused else -1)
    val fade = focus?.amount ?: 0f
    lines.forEachIndexed { index, line ->
        val color = Color(line.color)
        val alpha = when {
            hit != null -> hit.alpha(index, progress)
            index == focused -> progress
            else -> progress * (1f - 0.7f * fade)
        }
        val grow = when {
            hit != null -> hit.grow(index)
            index == focused -> 1f + 0.5f * fade
            else -> 1f
        }
        if (line.values.size == 1) {
            drawCircle(color, radius = 5f * grow, center = points[index][0], alpha = alpha)
        } else {
            drawPath(pathOf(points[index]), color, alpha = alpha, style = Stroke(width = 4f * grow, pathEffect = if (line.dashed) DashEffect else null))
        }
    }
    if (focus != null && focused in points.indices && points[focused].isNotEmpty()) {
        drawFocus(focus, points[focused].last(), Color(lines[focused].color), plotWidth)
    }
    hit?.let {
        drawHover(look, points[it.line][it.point], Color(lines[it.line].color), xText(it.point + lines[it.line].start), lines[it.line].values[it.point], plotWidth, plotHeight)
    }
}

/**
 * One line per account through its end-of-day balances. Unless [zero], the value axis hugs the
 * lines rather than reaching down to 0, so a day's movement stays visible on a large balance.
 * Seven day labels, always the 1st and the month's last day, each under its own day.
 * With [gapColors] (above, below) the gap between lines[0] and lines[1] is shaded by which one leads.
 * Without [pad] the value axis stops at the data, with no headroom above or below it.
 * Tapping a line hands its id to [onFocus], tapping off every line hands null; the line whose
 * id is [focusedId] stays bright while the others fade, with its last value marked on the axis.
 */
@Composable
fun DailyBalanceChart(
    lines: List<BalanceLine>,
    daysInMonth: Int,
    zero: Boolean,
    progress: () -> Float,
    modifier: Modifier = Modifier,
    gapColors: Pair<Color, Color>? = null,
    pad: Boolean = true,
    focusedId: Long? = null,
    onFocus: ((Long?) -> Unit)? = null,
    /** One name per point (a year's months) in place of the day numbers. */
    xLabels: List<String>? = null,
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val style = MaterialTheme.typography.labelSmall
    val measurer = rememberTextMeasurer()
    val xText: (Int) -> String = { i -> xLabels?.getOrNull(i) ?: String.format(Locale.US, "%02d", i + 1) }
    val xTicks = remember(daysInMonth, style, xLabels) {
        axisDays(daysInMonth, if (xLabels != null) 6 else 7).map { day -> day to measurer.measure(xText(day - 1), style) }
    }
    var height by remember { mutableIntStateOf(0) }
    val values = remember(lines) { lines.flatMap { it.values } }
    val axis = rememberValueAxis(values, zero, height, xTicks.firstOrNull()?.second?.size?.height ?: 0, pad)
    val yTicks = rememberAxisLabels(axis.ticks)
    val scrub = remember { Scrub() }
    val look = rememberHoverLook()

    val geometry = remember { LineGeometry() }
    val shown = remember { ShownFocus() }
    val focusIndex = lines.indexOfFirst { it.accountId == focusedId }
    if (focusIndex >= 0) shown.id = focusedId
    val shownIndex = lines.indexOfFirst { it.accountId == shown.id }
    val focusAmount = animateFloatAsState(if (focusIndex >= 0) 1f else 0f, tween(durationMillis = 220), label = "lineFocus")
    val units = Triple(
        stringResource(R.string.unit_thousand),
        stringResource(R.string.unit_million),
        stringResource(R.string.unit_billion),
    )
    val focusLabel = remember(lines, shownIndex, style, units) {
        lines.getOrNull(shownIndex)?.values?.lastOrNull()?.let { measurer.measure(compactAmount(it, units), style) }
    }
    val focusText = remember(lines, shownIndex) {
        val color = lines.getOrNull(shownIndex)?.let { Color(it.color) } ?: Color.Black
        if (color.luminance() > 0.5f) Color.Black else Color.White
    }
    val currentLines by rememberUpdatedState(lines)
    val currentOnFocus by rememberUpdatedState(onFocus)
    val tap = if (onFocus == null) Modifier else Modifier.pointerInput(Unit) {
        detectTapGestures { offset ->
            val index = lineAt(geometry.points, offset, FocusTapRadius.toPx())
            currentOnFocus?.invoke(currentLines.getOrNull(index)?.accountId)
        }
    }

    Canvas(modifier = modifier.onSizeChanged { height = it.height }.then(tap).scrub(scrub)) {
        val gutter = gutterOf(yTicks)
        val plotWidth = size.width - gutter
        val plotHeight = size.height - (xTicks.firstOrNull()?.second?.size?.height ?: 0) - AxisGap.toPx()
        fun y(value: Long) = axis.y(value, plotHeight)
        // Inset by half a label so 01 and the last day sit centred under their points.
        val inset = (xTicks.maxOfOrNull { it.second.size.width } ?: 0) / 2f
        val step = (plotWidth - inset * 2) / (daysInMonth - 1).coerceAtLeast(1)
        fun x(index: Int) = inset + index * step

        val points = lines.map { line -> line.values.mapIndexed { index, value -> Offset(x(line.start + index), y(value)) } }
        geometry.points = points
        val amount = focusAmount.value
        val focus = if (shownIndex >= 0 && amount > 0f) FocusDraw(shownIndex, amount, focusLabel, focusText) else null
        // Where the focus pill sits on the axis, so the tick labels under it can fade out of its way.
        val pillY = focus?.let { points.getOrNull(it.line)?.lastOrNull()?.y }
        val pillHalf = ((focusLabel?.size?.height ?: 0) + 2.dp.toPx()) / 2f

        yTicks.forEach { (value, label) ->
            val lineY = y(value)
            drawLine(gridColor, Offset(0f, lineY), Offset(plotWidth, lineY), strokeWidth = if (value == 0L) 2f else 1f)
            val covered = pillY != null && abs(lineY - pillY) < pillHalf + label.size.height / 2f
            drawText(
                label,
                color = axisColor,
                topLeft = Offset(plotWidth + AxisGap.toPx(), lineY - label.size.height / 2f),
                alpha = if (covered) 1f - amount else 1f,
            )
        }
        xTicks.forEach { (day, label) ->
            drawText(label, color = axisColor, topLeft = Offset(x(day - 1) - label.size.width / 2f, plotHeight + AxisGap.toPx()))
        }
        if (gapColors != null && lines.size >= 2) {
            drawGap(lines[0].values, lines[1].values, ::x, ::y, gapColors.first, gapColors.second)
        }
        drawBalanceLines(lines, points, scrub, progress(), focus, look, plotWidth, plotHeight, xText)
    }
}

/**
 * A whole pie of [rows]' slices, the selected one sliding out from the centre while the rest fade,
 * animated by [rememberSliceEmphasis]. Slices are thin-edged in the surface colour, so two of a
 * similar colour stay apart. The intro sweeps the whole pie round from 12 o'clock.
 */
@Composable
fun PieChart(
    rows: List<ShareRow>,
    /** Read inside the draw lambda, so selecting a slice redraws without recomposing. */
    selectedId: () -> Long?,
    onSelect: (Long?) -> Unit,
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val edge = MaterialTheme.colorScheme.surface
    val empty = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
    val slices = remember(rows) { rows.filter { it.sweepAngle > 0f } }
    val emphasis = rememberSliceEmphasis(selectedId)

    Canvas(
        modifier = modifier.pointerInput(slices) {
            detectTapGestures { tap -> onSelect(pieSliceAt(slices, tap, size.width.toFloat(), size.height.toFloat())) }
        },
    ) {
        val dim = emphasis.dim.value
        // Room around the pie for a slice to slide out into.
        val radius = size.minDimension / 2f / (1f + PieLift)
        if (slices.isEmpty()) {
            drawCircle(empty, radius)
            return@Canvas
        }
        val p = progress()
        slices.forEach { slice ->
            val lift = emphasis.of(slice.id)
            val start = -90f + (slice.startAngle + 90f) * p
            val sweep = slice.sweepAngle * p
            val mid = Math.toRadians((start + sweep / 2f).toDouble())
            val shift = radius * PieLift * lift
            val topLeft = center + Offset((cos(mid) * shift).toFloat(), (sin(mid) * shift).toFloat()) - Offset(radius, radius)
            val arcSize = Size(radius * 2f, radius * 2f)
            drawArc(Color(slice.color), start, sweep, useCenter = true, topLeft = topLeft, size = arcSize, alpha = 1f - 0.65f * dim * (1f - lift))
            if (slices.size > 1) drawArc(edge, start, sweep, useCenter = true, topLeft = topLeft, size = arcSize, style = Stroke(1.5.dp.toPx()))
        }
    }
}

/** How far a selected slice slides out, as a share of the radius. */
private const val PieLift = 0.08f

/** Which slice a tap landed on, or null outside the pie. */
private fun pieSliceAt(slices: List<ShareRow>, tap: Offset, width: Float, height: Float): Long? {
    val radius = min(width, height) / 2f
    if (hypot(tap.x - width / 2f, tap.y - height / 2f) > radius) return null
    var angle = Math.toDegrees(atan2((tap.y - height / 2f).toDouble(), (tap.x - width / 2f).toDouble())).toFloat()
    if (angle < -90f) angle += 360f
    return slices.firstOrNull { angle >= it.startAngle && angle < it.startAngle + it.sweepAngle }?.id
}

/**
 * One bar per month, stacked from [lines] in their order (each line one value per month):
 * positive balances stack up from 0, negative ones down from it. The axis always takes in 0.
 * Scrubbing across picks out a month and names the stack's total.
 */
@Composable
fun StackedBalanceBars(lines: List<BalanceLine>, labels: List<String>, progress: () -> Float, modifier: Modifier = Modifier) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val edge = MaterialTheme.colorScheme.surface
    val style = MaterialTheme.typography.labelSmall
    val measurer = rememberTextMeasurer()
    val xLabels = remember(labels, style) { labels.map { measurer.measure(it, style) } }
    var height by remember { mutableIntStateOf(0) }
    // Each month's top and bottom: the axis has to hold the stacks, not the single values.
    val values = remember(lines, labels) {
        labels.indices.flatMap { m ->
            listOf(lines.sumOf { it.values.getOrElse(m) { 0L }.coerceAtLeast(0L) }, lines.sumOf { it.values.getOrElse(m) { 0L }.coerceAtMost(0L) })
        }
    }
    val axis = rememberValueAxis(values, zero = true, height, xLabels.firstOrNull()?.size?.height ?: 0)
    val yTicks = rememberAxisLabels(axis.ticks)
    val scrub = remember { Scrub() }
    val look = rememberHoverLook()

    Canvas(modifier = modifier.onSizeChanged { height = it.height }.scrub(scrub)) {
        val plotWidth = size.width - gutterOf(yTicks)
        val plotHeight = size.height - (xLabels.firstOrNull()?.size?.height ?: 0) - AxisGap.toPx()
        val slot = plotWidth / labels.size.coerceAtLeast(1)
        val barWidth = slot * 0.55f
        fun y(value: Long) = axis.y(value, plotHeight)
        val baseline = y(0)
        val hovered = scrub.finger?.let { (it.x / slot).toInt().coerceIn(0, labels.size - 1) }

        yTicks.forEach { (value, label) ->
            val lineY = y(value)
            drawLine(gridColor, Offset(0f, lineY), Offset(plotWidth, lineY), strokeWidth = if (value == 0L) 2f else 1f)
            drawText(label, color = axisColor, topLeft = Offset(plotWidth + AxisGap.toPx(), lineY - label.size.height / 2f))
        }
        labels.indices.forEach { m ->
            val x = m * slot + (slot - barWidth) / 2f
            val alpha = if (hovered == null || hovered == m) 1f else 0.4f
            var up = 0L
            var down = 0L
            lines.forEach { line ->
                val value = line.values.getOrElse(m) { 0L }
                if (value == 0L) return@forEach
                val from = if (value > 0) up else down
                val to = from + value
                if (value > 0) up = to else down = to
                // The stack grows out of the baseline with the intro.
                val top = baseline + (y(maxOf(from, to)) - baseline) * progress()
                val bottom = baseline + (y(minOf(from, to)) - baseline) * progress()
                drawRect(Color(line.color), Offset(x, top), Size(barWidth, bottom - top), alpha = alpha)
                drawLine(edge, Offset(x, top), Offset(x + barWidth, top), strokeWidth = 1.dp.toPx())
            }
            xLabels.getOrNull(m)?.let { label ->
                drawText(label, color = axisColor, topLeft = Offset(m * slot + (slot - label.size.width) / 2f, plotHeight + AxisGap.toPx()))
            }
        }
        hovered?.let { m ->
            val total = lines.sumOf { it.values.getOrElse(m) { 0L } }
            drawHover(look, Offset(m * slot + slot / 2f, y(total)), axisColor, labels[m], total, plotWidth, plotHeight)
        }
    }
}
