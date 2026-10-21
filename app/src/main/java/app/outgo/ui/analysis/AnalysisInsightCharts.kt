package app.outgo.ui.analysis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.outgo.R
import app.outgo.ui.home.compactAmount
import app.outgo.ui.theme.ExpenseRed
import app.outgo.ui.theme.IncomeGreen
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Hand-drawn charts of the Trades, Savings and Budgets groups, in the idiom of [AnalysisCharts]:
 * plain [Canvas], text measured once per input (never per frame), numbers precomputed in
 * [AnalysisInsights]. Each one fills the fixed box its card gives it.
 */

private val Gap = 4.dp
private val CellRadius = CornerRadius(6f)
private val Dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))

/** The value of [value] on a 0..1 heat scale against [max]: square-rooted, so small days still show. */
private fun heat(value: Long, max: Long): Float = if (max <= 0L || value == 0L) 0f else sqrt(abs(value).toFloat() / max).coerceIn(0f, 1f)

/** A heat cell's fill: faint when empty, then [base] from light to full. */
private fun heatColor(base: Color, t: Float, empty: Color): Color = if (t <= 0f) empty else base.copy(alpha = 0.18f + 0.82f * t)

/** Readable text over a heat cell of strength [t]. */
private fun heatText(t: Float, normal: Color): Color = if (t > 0.55f) Color.White else normal

@Composable
private fun rememberUnits(): Triple<String, String, String> = Triple(
    stringResource(R.string.unit_thousand),
    stringResource(R.string.unit_million),
    stringResource(R.string.unit_billion),
)

/** One text line measured to fit [width] px, ellipsized. */
private fun TextMeasurer.fit(text: String, style: TextStyle, width: Int): TextLayoutResult =
    measure(text, style, overflow = TextOverflow.Ellipsis, maxLines = 1, constraints = Constraints(maxWidth = width.coerceAtLeast(1)))

/**
 * Sideways scrubbing that leaves a press held still to the card's long-press drag and a vertical
 * move to the list's scroll, like the line charts' hover. [onMove] gets the finger's x, null on lift.
 */
private fun Modifier.scrubX(onMove: (Float?) -> Unit): Modifier = pointerInput(Unit) {
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
            onMove(start.position.x)
            drag(start.id) {
                it.consume()
                onMove(it.position.x)
            }
        } finally {
            onMove(null)
        }
    }
}

/** A pill of [lines] centred over [x] at the top of the chart, kept inside it. */
private fun DrawScope.drawTopPill(lines: List<TextLayoutResult>, x: Float, fill: Color, text: Color) {
    if (lines.isEmpty()) return
    val padX = 6.dp.toPx()
    val padY = 3.dp.toPx()
    val width = lines.maxOf { it.size.width } + padX * 2
    val height = lines.sumOf { it.size.height } + padY * 2
    val left = (x - width / 2f).coerceIn(0f, (size.width - width).coerceAtLeast(0f))
    drawRoundRect(fill, Offset(left, 0f), Size(width, height), CornerRadius(8.dp.toPx()))
    var top = padY
    lines.forEach {
        drawText(it, text, Offset(left + padX, top))
        top += it.size.height
    }
}

// ------------------------------------------------------------------ calendar

/**
 * The month as a Monday-first calendar of six week rows, each day shaded by [values] (red for
 * money out, green for money in; with [diverging] the sign picks). A tap hands the day (1-based)
 * to [onSelect], or null off the days; the [selected] day is ringed.
 */
@Composable
fun CalendarHeatmap(
    daysInMonth: Int,
    firstWeekday: Int,
    values: List<Long>,
    base: Color,
    diverging: Boolean,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.labelSmall
    val measurer = rememberTextMeasurer()
    val weekdays = stringArrayResource(R.array.weekday_narrow)
    val headers = remember(weekdays.toList(), style) { weekdays.map { measurer.measure(it, style) } }
    val numbers = remember(daysInMonth, style) { (1..daysInMonth).map { measurer.measure(it.toString(), style) } }
    val max = remember(values) { values.maxOfOrNull { abs(it) } ?: 0L }
    val headerPx = (headers.firstOrNull()?.size?.height ?: 0)
    val select by rememberUpdatedState(onSelect)
    val layout by rememberUpdatedState(firstWeekday to daysInMonth)

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures { tap ->
                val top = headerPx + Gap.toPx()
                val cellW = size.width / 7f
                val cellH = (size.height - top) / 6f
                if (tap.y < top) return@detectTapGestures select(null)
                val index = ((tap.y - top) / cellH).toInt() * 7 + (tap.x / cellW).toInt()
                val day = index - layout.first + 1
                select(day.takeIf { it in 1..layout.second })
            }
        },
    ) {
        val top = headerPx + Gap.toPx()
        val cellW = size.width / 7f
        val cellH = (size.height - top) / 6f
        val inset = 1.5.dp.toPx()
        headers.forEachIndexed { i, label ->
            drawText(label, scheme.onSurfaceVariant, Offset(i * cellW + (cellW - label.size.width) / 2f, 0f))
        }
        val p = progress()
        val empty = scheme.onSurface.copy(alpha = 0.05f)
        for (d in 0 until daysInMonth) {
            val pos = firstWeekday + d
            val left = (pos % 7) * cellW + inset
            val y = top + (pos / 7) * cellH + inset
            val value = values.getOrElse(d) { 0L }
            val t = heat(value, max) * p
            val color = if (diverging) (if (value >= 0) IncomeGreen else ExpenseRed) else base
            val cell = Size(cellW - inset * 2, cellH - inset * 2)
            drawRoundRect(heatColor(color, t, empty), Offset(left, y), cell, CellRadius)
            if (selected == d + 1) drawRoundRect(scheme.primary, Offset(left, y), cell, CellRadius, style = Stroke(2.dp.toPx()))
            val number = numbers[d]
            drawText(number, heatText(t, scheme.onSurfaceVariant), Offset(left + (cell.width - number.size.width) / 2f, y + (cell.height - number.size.height) / 2f))
        }
    }
}

// ------------------------------------------------------------------ week grid

/**
 * Seven weekday rows (Monday first) of [DAY_SLOTS] four-hour cells, shaded by [grid]'s amounts in
 * [base]. A tap hands the cell's index (weekday * [DAY_SLOTS] + slot) to [onSelect].
 */
@Composable
fun WeekHeatmap(grid: WeekGridUi, base: Color, selected: Int?, onSelect: (Int?) -> Unit, progress: () -> Float, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.labelSmall
    val measurer = rememberTextMeasurer()
    val weekdays = stringArrayResource(R.array.weekday_abbrev)
    val rows = remember(weekdays.toList(), style) { weekdays.map { measurer.measure(it, style) } }
    val slots = remember(style) { (0 until DAY_SLOTS).map { measurer.measure("${it * (24 / DAY_SLOTS)}h", style) } }
    val labelW = (rows.maxOfOrNull { it.size.width } ?: 0)
    val bottomH = (slots.firstOrNull()?.size?.height ?: 0)
    val select by rememberUpdatedState(onSelect)

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures { tap ->
                val left = labelW + Gap.toPx()
                val cellW = (size.width - left) / DAY_SLOTS
                val cellH = (size.height - bottomH - Gap.toPx()) / WEEK_DAYS
                val col = ((tap.x - left) / cellW).toInt()
                val row = (tap.y / cellH).toInt()
                select(if (tap.x >= left && col in 0 until DAY_SLOTS && row in 0 until WEEK_DAYS) row * DAY_SLOTS + col else null)
            }
        },
    ) {
        val left = labelW + Gap.toPx()
        val cellW = (size.width - left) / DAY_SLOTS
        val plotH = size.height - bottomH - Gap.toPx()
        val cellH = plotH / WEEK_DAYS
        val inset = 1.5.dp.toPx()
        val max = grid.max
        val p = progress()
        val empty = scheme.onSurface.copy(alpha = 0.05f)
        rows.forEachIndexed { r, label ->
            drawText(label, scheme.onSurfaceVariant, Offset(0f, r * cellH + (cellH - label.size.height) / 2f))
            for (c in 0 until DAY_SLOTS) {
                val t = heat(grid.amount(r, c), max) * p
                val topLeft = Offset(left + c * cellW + inset, r * cellH + inset)
                val cell = Size(cellW - inset * 2, cellH - inset * 2)
                drawRoundRect(heatColor(base, t, empty), topLeft, cell, CellRadius)
                if (selected == r * DAY_SLOTS + c) drawRoundRect(scheme.primary, topLeft, cell, CellRadius, style = Stroke(2.dp.toPx()))
            }
        }
        slots.forEachIndexed { c, label ->
            // On the slot's left edge: the hour it starts at.
            drawText(label, scheme.onSurfaceVariant, Offset(left + c * cellW + inset, plotH + Gap.toPx()))
        }
    }
}

// ---------------------------------------------------------------- grouped bars

/**
 * Two bars per bucket side by side, [first] and [second] (shares, 0..1), on one percentage axis.
 * Scrubbing across picks a bucket out and shows [describe]'s lines for it.
 */
@Composable
fun PairedShareBars(
    labels: List<String>,
    first: List<Float>,
    second: List<Float>,
    firstColor: Color,
    secondColor: Color,
    describe: (Int) -> List<String>,
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.labelSmall
    val measurer = rememberTextMeasurer()
    val xLabels = remember(labels, style) { labels.map { measurer.measure(it, style) } }
    // A nice top: the next 25% above the largest share.
    val top = remember(first, second) { (((first + second).maxOrNull() ?: 0f) * 4f).let { kotlin.math.ceil(it.toDouble()).toFloat() / 4f }.coerceIn(0.25f, 1f) }
    val ticks = remember(top, style) { generateSequence(0.25f) { it + 0.25f }.takeWhile { it <= top + 0.001f }.map { it to measurer.measure("${Math.round(it * 100)}%", style) }.toList() }
    var hovered by remember { mutableStateOf<Int?>(null) }
    // The bucket width as last drawn: a plain field, written while drawing.
    val slotPx = remember { FloatArray(1) }
    val hoverText = remember(hovered, labels) { hovered?.let { i -> describe(i).map { measurer.measure(it, style) } }.orEmpty() }

    Canvas(
        modifier = modifier.scrubX { x ->
            hovered = x?.let { if (slotPx[0] > 0f) (it / slotPx[0]).toInt().coerceIn(0, labels.lastIndex) else null }
        },
    ) {
        val gutter = (ticks.maxOfOrNull { it.second.size.width } ?: 0) + Gap.toPx()
        val plotW = size.width - gutter
        val labelH = xLabels.firstOrNull()?.size?.height ?: 0
        val plotH = size.height - labelH - Gap.toPx()
        val slot = plotW / labels.size.coerceAtLeast(1)
        slotPx[0] = slot
        val barW = slot * 0.32f
        fun y(v: Float) = plotH - (v / top) * plotH
        drawLine(scheme.outlineVariant, Offset(0f, plotH), Offset(plotW, plotH), strokeWidth = 2f)
        ticks.forEach { (v, label) ->
            drawLine(scheme.outlineVariant, Offset(0f, y(v)), Offset(plotW, y(v)), strokeWidth = 1f)
            drawText(label, scheme.onSurfaceVariant, Offset(plotW + Gap.toPx(), y(v) - label.size.height / 2f))
        }
        val p = progress()
        labels.indices.forEach { i ->
            val alpha = if (hovered == null || hovered == i) 1f else 0.4f
            val center = i * slot + slot / 2f
            val a = first.getOrElse(i) { 0f } * p
            val b = second.getOrElse(i) { 0f } * p
            drawRect(firstColor, Offset(center - barW - 1f, y(a)), Size(barW, plotH - y(a)), alpha = alpha)
            drawRect(secondColor, Offset(center + 1f, y(b)), Size(barW, plotH - y(b)), alpha = alpha)
            xLabels.getOrNull(i)?.let { drawText(it, scheme.onSurfaceVariant, Offset(center - it.size.width / 2f, plotH + Gap.toPx())) }
        }
        hovered?.let { drawTopPill(hoverText, it * slot + slot / 2f, scheme.inverseSurface, scheme.inverseOnSurface) }
    }
}

// ------------------------------------------------------------------ bar + line

/**
 * One bar per month ([bars], labelled above with [barText]) with a line over them ([line], its own
 * scale). Scrubbing across picks a month out and shows [describe]'s lines for it.
 */
@Composable
fun BarLineChart(
    labels: List<String>,
    bars: List<Long>,
    line: List<Long>,
    barColor: Color,
    lineColor: Color,
    barText: (Long) -> String,
    describe: (Int) -> List<String>,
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.labelSmall
    val measurer = rememberTextMeasurer()
    val xLabels = remember(labels, style) { labels.map { measurer.measure(it, style) } }
    val barLabels = remember(bars, style) { bars.map { measurer.measure(barText(it), style) } }
    var hovered by remember { mutableStateOf<Int?>(null) }
    var width by remember { mutableIntStateOf(0) }
    val hoverText = remember(hovered, labels, bars, line) { hovered?.let { i -> describe(i).map { measurer.measure(it, style) } }.orEmpty() }
    val barMax = remember(bars) { (bars.maxOrNull() ?: 0L).coerceAtLeast(1L) }
    val lineMax = remember(line) { (line.maxOrNull() ?: 0L).coerceAtLeast(1L) }

    Canvas(modifier = modifier.onSizeChanged { width = it.width }.scrubX { x -> hovered = x?.let { (it / (width / labels.size.coerceAtLeast(1))).toInt().coerceIn(0, labels.lastIndex) } }) {
        val labelH = xLabels.firstOrNull()?.size?.height ?: 0
        val valueH = barLabels.firstOrNull()?.size?.height ?: 0
        val plotTop = valueH + Gap.toPx()
        val plotH = size.height - labelH - Gap.toPx()
        val usable = plotH - plotTop
        val slot = size.width / labels.size.coerceAtLeast(1)
        val barW = slot * 0.5f
        val p = progress()
        drawLine(scheme.outlineVariant, Offset(0f, plotH), Offset(size.width, plotH), strokeWidth = 2f)
        labels.indices.forEach { i ->
            val alpha = if (hovered == null || hovered == i) 1f else 0.4f
            val center = i * slot + slot / 2f
            val h = usable * (bars.getOrElse(i) { 0L }.toFloat() / barMax) * p
            drawRoundRect(barColor, Offset(center - barW / 2f, plotH - h), Size(barW, h), CornerRadius(4f), alpha = alpha)
            barLabels.getOrNull(i)?.let { drawText(it, scheme.onSurfaceVariant, Offset(center - it.size.width / 2f, plotH - h - it.size.height - 1f), alpha = alpha) }
            xLabels.getOrNull(i)?.let { drawText(it, scheme.onSurfaceVariant, Offset(center - it.size.width / 2f, plotH + Gap.toPx())) }
        }
        // The line keeps clear of the bar labels: it spans the lower 80% of the plot.
        val points = line.mapIndexed { i, v -> Offset(i * slot + slot / 2f, plotH - usable * 0.8f * (v.toFloat() / lineMax) * p) }
        if (points.size > 1) {
            val path = Path().apply { points.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } }
            drawPath(path, lineColor, style = Stroke(3.dp.toPx()))
        }
        points.forEachIndexed { i, o ->
            drawCircle(scheme.surface, 5.dp.toPx(), o)
            drawCircle(lineColor, if (hovered == i) 4.5.dp.toPx() else 3.5.dp.toPx(), o)
        }
        hovered?.let { drawTopPill(hoverText, it * slot + slot / 2f, scheme.inverseSurface, scheme.inverseOnSurface) }
    }
}

// --------------------------------------------------------------- transfer flow

/**
 * The month's transfers as bands from the sending accounts (left) to the receiving ones (right),
 * each as thick as its amount, in the sender's colour. [names] gives each account's name and
 * colour; [OTHER_NODE] is named [otherLabel].
 */
@Composable
fun TransferFlowChart(
    flow: TransferFlowUi,
    names: Map<Long, Triple<String, Long?, Int>>,
    otherLabel: String,
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.labelSmall
    val measurer = rememberTextMeasurer()
    val units = rememberUnits()
    var width by remember { mutableIntStateOf(0) }
    val labelW = (width * 0.3f).toInt()
    fun name(id: Long) = if (id == OTHER_NODE) otherLabel else names[id]?.first.orEmpty()
    fun colorOf(id: Long) = if (id == OTHER_NODE) OTHER_COLOR else names[id]?.third ?: OTHER_COLOR
    val texts = remember(flow, labelW, style, units) {
        fun side(nodes: List<FlowNode>) = nodes.map { n ->
            measurer.fit(name(n.id), style, labelW) to measurer.measure(compactAmount(n.total, units), style)
        }
        side(flow.sources) to side(flow.targets)
    }

    Canvas(modifier = modifier.onSizeChanged { width = it.width }) {
        val nodeW = 6.dp.toPx()
        val gap = 6.dp.toPx()
        val leftX = labelW + Gap.toPx()
        val rightX = size.width - labelW - Gap.toPx() - nodeW
        val total = flow.total.coerceAtLeast(1L).toFloat()
        fun layoutSide(nodes: List<FlowNode>): Map<Long, Pair<Float, Float>> {
            val usable = size.height - gap * (nodes.size - 1).coerceAtLeast(0)
            var y = 0f
            return nodes.associate { n ->
                val h = usable * n.total / total
                val span = y to h
                y += h + gap
                n.id to span
            }
        }
        val src = layoutSide(flow.sources)
        val dst = layoutSide(flow.targets)
        val p = progress()

        // Each band takes the next slice of its source node (in target order) and of its target node (in source order).
        val srcUsed = HashMap<Long, Float>()
        val dstUsed = HashMap<Long, Float>()
        val srcOrder = flow.sources.withIndex().associate { (i, n) -> n.id to i }
        val bandTop = HashMap<FlowLink, Pair<Float, Float>>()
        flow.links.forEach { l ->
            val (y, h) = src.getValue(l.from)
            val used = srcUsed[l.from] ?: 0f
            val bandH = h * l.total / flow.sources.first { it.id == l.from }.total
            bandTop[l] = (y + used) to bandH
            srcUsed[l.from] = used + bandH
        }
        flow.links.sortedWith(compareBy<FlowLink> { flow.targets.indexOfFirst { n -> n.id == it.to } }.thenBy { srcOrder[it.from] }).forEach { l ->
            val (y0, h) = bandTop.getValue(l)
            val (ty, th) = dst.getValue(l.to)
            val used = dstUsed[l.to] ?: 0f
            val bandH = th * l.total / flow.targets.first { it.id == l.to }.total
            dstUsed[l.to] = used + bandH
            val y1 = ty + used
            val x0 = leftX + nodeW
            val x1 = rightX
            val mid = (x0 + x1) / 2f
            val path = Path().apply {
                moveTo(x0, y0)
                cubicTo(mid, y0, mid, y1, x1, y1)
                lineTo(x1, y1 + bandH)
                cubicTo(mid, y1 + bandH, mid, y0 + h, x0, y0 + h)
                close()
            }
            drawPath(path, Color(colorOf(l.from)), alpha = 0.4f * p)
        }
        // Nodes, then their labels: two lines centred on the node, nudged apart when nodes are thin.
        fun labels(nodes: List<FlowNode>, spans: Map<Long, Pair<Float, Float>>, measured: List<Pair<TextLayoutResult, TextLayoutResult>>, x: Float, alignRight: Boolean) {
            var floor = 0f
            nodes.forEachIndexed { i, n ->
                val (y, h) = spans.getValue(n.id)
                drawRect(Color(colorOf(n.id)), Offset(x, y), Size(nodeW, h), alpha = p)
                val (nameText, amountText) = measured[i]
                val blockH = nameText.size.height + amountText.size.height
                val top = (y + h / 2f - blockH / 2f).coerceAtLeast(floor).coerceAtMost(size.height - blockH)
                floor = top + blockH
                fun place(t: TextLayoutResult) = if (alignRight) x - Gap.toPx() - t.size.width else x + nodeW + Gap.toPx()
                drawText(nameText, scheme.onSurface, Offset(place(nameText), top))
                drawText(amountText, scheme.onSurfaceVariant, Offset(place(amountText), top + nameText.size.height))
            }
        }
        labels(flow.sources, src, texts.first, leftX, alignRight = true)
        labels(flow.targets, dst, texts.second, rightX, alignRight = false)
    }
}

// ----------------------------------------------------------------- bullet bar

/**
 * A progress bar for [value] against [goal]: [value] filled in [fill], [projected] behind it as a
 * ghost, a tick at [goal] and a small marker at [pace]. The scale takes in all of them.
 */
@Composable
fun BulletBar(value: Long, goal: Long, pace: Long, projected: Long, fill: Color, progress: () -> Float, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Canvas(modifier = modifier) {
        val scale = max(max(goal, value), max(projected, pace)).coerceAtLeast(1L) * 1.05f
        fun x(v: Long) = size.width * (v.coerceAtLeast(0L) / scale)
        val barH = size.height * 0.55f
        val top = (size.height - barH) / 2f
        val radius = CornerRadius(barH / 2f)
        val p = progress()
        drawRoundRect(scheme.onSurface.copy(alpha = 0.08f), Offset(0f, top), Size(size.width, barH), radius)
        if (projected > value) drawRoundRect(fill.copy(alpha = 0.25f), Offset(0f, top), Size(x(projected) * p, barH), radius)
        drawRoundRect(fill, Offset(0f, top), Size(x(value) * p, barH), radius)
        // The goal: a full-height tick. The pace: a dashed one, shorter.
        val goalX = x(goal)
        drawLine(scheme.onSurface, Offset(goalX, 0f), Offset(goalX, size.height), strokeWidth = 2.dp.toPx())
        if (pace in 1 until goal) {
            val paceX = x(pace)
            drawLine(scheme.onSurfaceVariant, Offset(paceX, top - 2.dp.toPx()), Offset(paceX, top + barH + 2.dp.toPx()), strokeWidth = 1.5.dp.toPx(), pathEffect = Dash)
        }
    }
}

// ------------------------------------------------------------------ waterfall

/**
 * [steps] as rows: a total step is a bar from 0, any other step floats from the running total to
 * where it moves it, green up and red down, with its name on the left and amount on the right.
 */
@Composable
fun WaterfallChart(
    steps: List<WaterfallStep>,
    label: (StepKind) -> String,
    amount: (WaterfallStep) -> String,
    totalColor: Color,
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.labelSmall
    val strong = style.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    val measurer = rememberTextMeasurer()
    var width by remember { mutableIntStateOf(0) }
    val names = remember(steps, style, width) { steps.map { measurer.fit(label(it.kind), if (it.total) strong else style, (width * 0.36f).toInt()) } }
    val amounts = remember(steps, style) { steps.map { measurer.measure(amount(it), if (it.total) strong else style) } }
    // Each step's start and end on the value scale.
    val spans = remember(steps) {
        var running = 0L
        steps.map { s ->
            if (s.total) {
                running = s.amount
                0L to s.amount
            } else {
                val from = running
                running += s.amount
                from to running
            }
        }
    }

    Canvas(modifier = modifier.onSizeChanged { width = it.width }) {
        if (steps.isEmpty()) return@Canvas
        val nameW = (names.maxOfOrNull { it.size.width } ?: 0) + Gap.toPx() * 2
        val amountW = (amounts.maxOfOrNull { it.size.width } ?: 0) + Gap.toPx() * 2
        val barLeft = nameW
        val barW = (size.width - nameW - amountW).coerceAtLeast(1f)
        val low = minOf(0L, spans.minOf { minOf(it.first, it.second) })
        val high = maxOf(0L, spans.maxOf { maxOf(it.first, it.second) }).coerceAtLeast(low + 1)
        fun x(v: Long) = barLeft + barW * ((v - low).toFloat() / (high - low))
        val rowH = minOf(size.height / steps.size, 24.dp.toPx())
        val barH = rowH * 0.6f
        val p = progress()
        val zeroX = x(0)
        drawLine(scheme.outlineVariant, Offset(zeroX, 0f), Offset(zeroX, size.height), strokeWidth = 1f)
        steps.forEachIndexed { i, step ->
            val (from, to) = spans[i]
            val y = i * rowH + (rowH - barH) / 2f
            val color = when {
                step.total -> if (step.amount < 0) ExpenseRed else totalColor
                step.amount >= 0 -> IncomeGreen
                else -> ExpenseRed
            }
            val a = x(from)
            val b = a + (x(to) - a) * p
            drawRoundRect(color, Offset(minOf(a, b), y), Size(abs(b - a).coerceAtLeast(2f), barH), CornerRadius(3f))
            // A thin link to the next row's start.
            if (i < steps.lastIndex) {
                val endX = x(to)
                drawLine(scheme.onSurfaceVariant.copy(alpha = 0.5f), Offset(endX, y + barH), Offset(endX, (i + 1) * rowH + (rowH - barH) / 2f), strokeWidth = 1f, pathEffect = Dash)
            }
            val name = names[i]
            drawText(name, scheme.onSurface, Offset(0f, i * rowH + (rowH - name.size.height) / 2f))
            val value = amounts[i]
            drawText(value, if (step.total) scheme.onSurface else color, Offset(size.width - value.size.width, i * rowH + (rowH - value.size.height) / 2f))
        }
    }
}

// ------------------------------------------------------------------ dot strip

/**
 * A day strip (1 .. [days]) with a dot per month on the day in [values] (oldest first, fading in
 * toward the newest, which is ringed). Null months have no dot.
 */
@Composable
fun DayDotStrip(values: List<Int?>, days: Int, color: Color, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Canvas(modifier = modifier) {
        val r = minOf(size.height / 2f - 1f, 5.dp.toPx())
        fun x(day: Int) = r + (size.width - r * 2) * (day - 1) / (days - 1).coerceAtLeast(1)
        val mid = size.height / 2f
        drawLine(scheme.outlineVariant, Offset(r, mid), Offset(size.width - r, mid), strokeWidth = 1.5f)
        listOf(1, 10, 20, days).forEach { d -> drawLine(scheme.outlineVariant, Offset(x(d), mid - 3.dp.toPx()), Offset(x(d), mid + 3.dp.toPx()), strokeWidth = 1.5f) }
        values.forEachIndexed { i, day ->
            if (day == null) return@forEachIndexed
            val newest = i == values.lastIndex
            val alpha = 0.25f + 0.75f * i / (values.size - 1).coerceAtLeast(1)
            if (newest) drawCircle(scheme.surface, r + 1.5.dp.toPx(), Offset(x(day), mid))
            drawCircle(color, if (newest) r else r * 0.75f, Offset(x(day), mid), alpha = alpha)
        }
    }
}

/** The day labels under a [DayDotStrip] of [days]: 1, 10, 20 and the last, each under its tick. */
@Composable
fun DayDotAxis(days: Int, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.labelSmall
    val measurer = rememberTextMeasurer()
    val labels = remember(days, style) { listOf(1, 10, 20, days).map { it to measurer.measure(it.toString(), style) } }
    Canvas(modifier = modifier) {
        val r = 5.dp.toPx()
        labels.forEach { (d, t) ->
            val x = r + (size.width - r * 2) * (d - 1) / (days - 1).coerceAtLeast(1)
            drawText(t, scheme.onSurfaceVariant, Offset((x - t.size.width / 2f).coerceIn(0f, size.width - t.size.width), 0f))
        }
    }
}
