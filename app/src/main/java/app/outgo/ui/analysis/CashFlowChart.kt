package app.outgo.ui.analysis

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.outgo.R
import app.outgo.ui.LocalAppContainer
import app.outgo.ui.home.compactAmount
import app.outgo.ui.theme.ExpenseRed
import app.outgo.ui.theme.IncomeGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The cash flow as a node chart ([layoutCashNodes]) in a frame: incomes along the top and left
 * edges, spending along the bottom and right edges, the accounts standing in the middle. Each
 * account is a borderless rectangle with its icon, name and net change; each category a caret (an
 * arrow tag) pointing down or right, the way the money goes. Links run in level and upright lanes
 * around the nodes and each other ([routeCashLinks]); a transfer ends in an arrowhead. Node and link
 * sizes are only relative to the money; [full] gives the roomier sizes of the full screen.
 *
 * The layout and the routing run off the main thread, once per size and data; the text is measured
 * here. A tap on a node selects it ([onSelect]), which lights up its links and fades the rest; a tap
 * elsewhere clears it.
 */
@Composable
fun CashFlowChart(
    flow: CashFlowUi,
    full: Boolean,
    labels: CashLabels,
    units: Triple<String, String, String>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    progress: () -> Float,
    modifier: Modifier = Modifier,
    /** Grows past [modifier]'s height when the nodes need it, and scrolls. */
    scroll: Boolean = false,
    /** Accounts show their name and net change; otherwise just their icon, the rest on focus. */
    detail: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.labelSmall
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    val metrics = remember(full, detail, density) { cashMetrics(full, detail, density) }
    // Accounts only as tall as their content needs, plus half the room the size alone would leave.
    val sizes = remember(flow, metrics, labels, units, style, detail) {
        val plain = cashNodeSizes(flow, metrics)
        val contentH = FloatArray(flow.nodes.size) { i ->
            val node = flow.nodes[i]
            when {
                node.column != CashColumn.ACCOUNT -> 0f
                detail -> accountContent(node, plain.w[i], labels, units, measurer, style, density).height
                else -> accountIcon(plain.w[i], density)
            }
        }
        plain.halveAccountPadding(contentH)
    }
    // Focus labels, shown only for the selected node: the layout leaves no room for them. Every
    // caret has one, and an icon-only account (its name and net change).
    val caretTexts = remember(flow, labels, units, style, full, density, detail) {
        val maxW = with(density) { (if (full) 96.dp else 72.dp).toPx() }
        flow.nodes.map { node ->
            when {
                node.column != CashColumn.ACCOUNT ->
                    measurer.line(labels.of(node), style, maxW) to measurer.line(compactAmount(node.total, units), style, maxW)
                detail -> null
                else -> measurer.line(labels.of(node), style, maxW) to measurer.line(signedNet(node, units), style, maxW)
            }
        }
    }
    // Kept with the flow and texts it was made for: the state keeps its last value while a new one is
    // worked out, and that one must not be drawn over other data.
    val made by produceState<Triple<CashFlowUi, List<Pair<TextLayoutResult, TextLayoutResult>?>, CashNodeLayout>?>(null, flow, size, sizes, caretTexts, metrics) {
        if (size.width == 0 || size.height == 0) return@produceState
        val none = FloatArray(flow.nodes.size)
        val layout = withContext(Dispatchers.Default) {
            layoutCashNodes(flow, size.width.toFloat(), size.height.toFloat(), sizes, none, none, metrics)
        }
        value = Triple(flow, caretTexts, layout)
    }
    // By value, like produceState's keys: an equal flow rebuilt (a setting that changes nothing drawn)
    // keeps the layout, since produceState does not start again for it.
    val layout = made?.takeIf { it.first == flow && it.second == caretTexts }?.third
    val drawing = remember(layout, labels, units, style, density, metrics, detail) {
        layout?.let { nodeDrawing(flow, it, caretTexts, labels, units, measurer, style, density, metrics, detail, size.width.toFloat(), size.height.toFloat()) }
    }
    val icons = rememberNodeIcons(flow)
    // Each node its own painter: a vector painter caches one size, so two nodes sharing one at
    // different sizes (the joined account and a joined caret, say) would draw each other's leftovers.
    val glyphs = flow.nodes.mapIndexed { i, node -> key(i) { glyphOf(node)?.let { painterResource(it) } } }
    val select by rememberUpdatedState(onSelect)
    val picked by rememberUpdatedState(selected)
    // The focus crossfades: [focusFrom] fades out while [focusTo] fades in, as [focusMix] runs 0 to 1.
    var focusFrom by remember(flow) { mutableStateOf<Int?>(null) }
    var focusTo by remember(flow) { mutableStateOf(selected) }
    val focusMix = remember(flow) { Animatable(1f) }
    LaunchedEffect(flow, selected) {
        if (selected == focusTo) return@LaunchedEffect
        // ponytail: a tap mid-fade restarts from the old focus (a small jump), like the transfer flows chart.
        focusFrom = focusTo
        focusTo = selected
        focusMix.snapTo(0f)
        focusMix.animateTo(1f, tween(durationMillis = FOCUS_MS, easing = FastOutSlowInEasing))
    }

    // Full screen may scroll: the chart is then as tall as its nodes need at their own size.
    TallIfScrolling(scroll, modifier, { width ->
        val none = FloatArray(flow.nodes.size)
        cashNaturalHeight(flow, width, sizes, none, none, metrics)
    }, keys = listOf(flow, sizes, metrics)) { inner ->
        Canvas(
            modifier = inner
                .onSizeChanged { size = it }
                .pointerInput(drawing) {
                    val d = drawing ?: return@pointerInput
                    detectTapGestures { at ->
                        val hit = d.hits.indexOfFirst { it.contains(at) }.takeIf { it >= 0 }
                        select(if (hit == null || hit == picked) null else hit)
                    }
                },
        ) {
            val d = drawing ?: return@Canvas
            val g = d.layout
            val p = progress()
            val n = flow.nodes.size
            // A stale index (from data drawn before) counts as no focus.
            val sel = focusTo?.takeIf { it < n }
            val was = focusFrom?.takeIf { it < n }
            val t = focusMix.value
            // How much node [i] is focused: fading in, fading out, or not at all.
            fun focus(i: Int) = (if (i == sel) t else 0f) + (if (i == was) 1f - t else 0f)
            fun lit(s: Int?, l: Int) = s == null || flow.links[l].from == s || flow.links[l].to == s
            fun nodeLit(s: Int?, i: Int) = s == null || i == s || flow.links.any { (it.from == s && it.to == i) || (it.to == s && it.from == i) }
            // From the old focus's look to the new one's.
            fun mix(old: Float, new: Float) = old + (new - old) * t
            fun linkAlpha(s: Int?, l: Int) = when {
                s == null -> 0.5f
                lit(s, l) -> 0.8f
                else -> 0.08f
            }

            // Links first, the faded ones under the lit ones, so the selection reads on top.
            flow.links.indices.sortedBy { lit(sel, it) }.forEach { l ->
                val alpha = p * mix(linkAlpha(was, l), linkAlpha(sel, l))
                drawPath(d.links[l], d.linkColor[l], alpha = alpha, style = Stroke(g.linkW[l], join = StrokeJoin.Round))
                d.arrows[l]?.let { drawPath(it, d.linkColor[l], alpha = alpha) }
            }

            val radius = CornerRadius(metrics.accountRadius)
            val borderW = 1.5.dp.toPx()
            val border = Stroke(borderW, join = StrokeJoin.Round)
            flow.nodes.indices.forEach { i ->
                val node = flow.nodes[i]
                val fade = p * mix(if (nodeLit(was, i)) 1f else 0.3f, if (nodeLit(sel, i)) 1f else 0.3f)
                val color = d.nodeColor[i]
                val f = focus(i)
                if (node.column == CashColumn.ACCOUNT) {
                    val r = d.body[i]
                    // Opaque under the tint, so the links ending at its edges stop there.
                    drawRoundRect(scheme.surface, r.topLeft, r.size, radius, alpha = p)
                    drawRoundRect(color, r.topLeft, r.size, radius, alpha = 0.2f * fade)
                    if (f > 0f) {
                        // The outline grows out from the edge as it fades in.
                        val o = 2.5.dp.toPx() * f
                        drawRoundRect(scheme.onSurface, r.topLeft - Offset(o, o), Size(r.width + 2 * o, r.height + 2 * o), CornerRadius(radius.x + o), alpha = f * p, style = Stroke(borderW))
                    }
                    val box = d.icon[i]
                    val bitmap = node.iconId?.let { icons[it] }
                    when {
                        bitmap != null -> drawBitmap(bitmap, box, fade, ColorFilter.tint(color))
                        // The joined accounts have no icon of their own: the same dots as "Other" carets.
                        node.refId == OTHER_NODE -> glyphs[i]?.let { drawGlyph(it, box, fade, ColorFilter.tint(color)) }
                    }
                } else {
                    // The selection's outline under the caret, so it shows only around the edge: about as
                    // thick as an account's outline, and growing out from the edge as it fades in.
                    if (f > 0f) {
                        val w = borderW + (CARET_FOCUS_STROKE.toPx() - borderW) * f
                        drawPath(d.shapes[i], scheme.onSurface, alpha = 0.9f * f * p, style = Stroke(w, join = StrokeJoin.Round))
                    }
                    // Opaque first, so a link passing under a faded caret stays hidden.
                    drawPath(d.shapes[i], scheme.surface, alpha = p)
                    drawPath(d.shapes[i], color, alpha = fade)
                    drawPath(d.shapes[i], color, alpha = fade, style = border)
                    val box = d.icon[i]
                    val bitmap = node.iconId?.let { icons[it] }
                    val tint = ColorFilter.tint(Color.White)
                    when {
                        bitmap != null -> drawBitmap(bitmap, box, fade, tint)
                        node.iconId == null -> glyphs[i]?.let { drawGlyph(it, box, fade, tint) }
                    }
                }
            }
            flow.nodes.indices.forEach { i ->
                val faded = mix(if (nodeLit(was, i)) 1f else 0.35f, if (nodeLit(sel, i)) 1f else 0.35f)
                d.texts[i].forEach { tx -> drawText(tx.text, (tx.color ?: scheme.onSurface).copy(alpha = faded), tx.at) }
            }
            // The focused caret's label, over everything, fading in (and the old one's fading out).
            listOfNotNull(was, sel).distinct().forEach { s ->
                val a = focus(s) * p
                if (a <= 0f) return@forEach
                d.focus[s].forEach { tx ->
                    if (tx.pill) {
                        val corner = CornerRadius(6.dp.toPx())
                        drawRoundRect(scheme.surface, tx.box.topLeft, tx.box.size, corner, alpha = 0.95f * a)
                        drawRoundRect(d.nodeColor[s], tx.box.topLeft, tx.box.size, corner, alpha = a, style = Stroke(1.dp.toPx()))
                    }
                    drawText(tx.text, tx.color ?: scheme.onSurface, tx.at, alpha = a)
                }
            }
        }
    }
}

/**
 * [content] filling [modifier]'s box, or with [scroll], as tall as [natural] (px, for the box's width)
 * when that is more, scrolling. [keys] are what [natural] reads.
 */
@Composable
private fun TallIfScrolling(
    scroll: Boolean,
    modifier: Modifier,
    natural: (Float) -> Float,
    keys: List<Any?>,
    content: @Composable (Modifier) -> Unit,
) {
    // One structure either way, so switching keeps the chart (and its layout state) in place.
    BoxWithConstraints(modifier) {
        val width = constraints.maxWidth.toFloat()
        val tall = if (scroll) remember(width, keys) { natural(width) } else 0f
        val height = with(LocalDensity.current) { max(constraints.maxHeight.toFloat(), ceil(tall)).toDp() }
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState(), enabled = scroll)) {
            content(Modifier.fillMaxWidth().height(height))
        }
    }
}

/** The names the chart gives the nodes it names itself: the special kinds and the joined ones. */
@Immutable
data class CashLabels(
    val other: String,
    val otherAccounts: String,
    val adjustIn: String,
    val adjustOut: String,
) {
    fun of(node: CashNode): String = when {
        node.refId == OTHER_NODE -> other
        node.kind == CashKind.ADJUST_IN -> adjustIn
        node.kind == CashKind.ADJUST_OUT -> adjustOut
        node.kind == CashKind.OTHER_IN || node.kind == CashKind.OTHER_OUT -> otherAccounts
        else -> node.name
    }
}

/** A node's colour: its own, or grey for the special kinds and the joined ones. */
fun cashNodeColor(node: CashNode): Color = Color(node.color ?: OTHER_COLOR)

/** How long the focus takes to move from one node to another. */
private const val FOCUS_MS = 220

/**
 * The focused caret's outline stroke, drawn under the caret, so about half of it shows outside the
 * caret's own border: roughly an account outline's thickness.
 */
private val CARET_FOCUS_STROKE = 4.5.dp

/** Room around a selected caret's label text, inside its backdrop. */
private val LABEL_INSET = 3.dp

/** The chart's sizes, in px: roomier in full screen. */
private fun cashMetrics(full: Boolean, detail: Boolean, density: Density): CashNodeMetrics = with(density) {
    val plain = if (full) {
        CashNodeMetrics(
            accountW = 64.dp.toPx()..88.dp.toPx(), accountH = 92.dp.toPx()..128.dp.toPx(),
            caret = 30.dp.toPx()..44.dp.toPx(), link = 2.dp.toPx()..10.dp.toPx(),
            labelGap = 2.dp.toPx(), gap = 8.dp.toPx(), lanes = 32.dp.toPx(), cell = 4.dp.toPx(), linkGap = 3.dp.toPx(),
            accountRadius = 10.dp.toPx(), accountMargin = 4.dp.toPx(), linkCorner = 8.dp.toPx(), arrow = 9.dp.toPx(),
        )
    } else {
        CashNodeMetrics(
            accountW = 54.dp.toPx()..66.dp.toPx(), accountH = 80.dp.toPx()..100.dp.toPx(),
            caret = 22.dp.toPx()..32.dp.toPx(), link = 1.5.dp.toPx()..7.dp.toPx(),
            labelGap = 1.dp.toPx(), gap = 4.dp.toPx(), lanes = 22.dp.toPx(), cell = 3.dp.toPx(), linkGap = 2.dp.toPx(),
            accountRadius = 10.dp.toPx(), accountMargin = 3.dp.toPx(), linkCorner = 8.dp.toPx(), arrow = 9.dp.toPx(),
        )
    }
    if (detail) return@with plain
    // Icon-only accounts: about a caret's size, their corners rounder for their size.
    val small = if (full) 36.dp.toPx()..48.dp.toPx() else 30.dp.toPx()..40.dp.toPx()
    CashNodeMetrics(
        accountW = small, accountH = small.start * ACCOUNT_ASPECT..small.endInclusive * ACCOUNT_ASPECT,
        caret = plain.caret, link = plain.link, labelGap = plain.labelGap, gap = plain.gap, lanes = plain.lanes,
        cell = plain.cell, linkGap = plain.linkGap, accountRadius = 8.dp.toPx(), accountMargin = plain.accountMargin,
        linkCorner = plain.linkCorner, arrow = plain.arrow,
    )
}

/** What a node with no icon of its own shows: the joined ones dots, adjustments a pencil, accounts not shown arrows. */
private fun glyphOf(node: CashNode): Int? = when {
    node.refId == OTHER_NODE -> R.drawable.ph_dots_three
    node.kind == CashKind.ADJUST_IN || node.kind == CashKind.ADJUST_OUT -> R.drawable.ph_pencil_simple
    node.kind == CashKind.OTHER_IN || node.kind == CashKind.OTHER_OUT -> R.drawable.ph_swap_horizontal
    else -> null
}

private fun DrawScope.drawGlyph(painter: Painter, box: Rect, alpha: Float, tint: ColorFilter) {
    translate(box.left, box.top) {
        with(painter) { draw(box.size, alpha, tint) }
    }
}

private fun DrawScope.drawBitmap(bitmap: ImageBitmap, box: Rect, alpha: Float, tint: ColorFilter) = drawImage(
    bitmap,
    dstOffset = IntOffset(box.left.roundToInt(), box.top.roundToInt()),
    dstSize = IntSize(box.width.roundToInt(), box.height.roundToInt()),
    alpha = alpha,
    colorFilter = tint,
)

/** The nodes' icons, accounts' and categories': the cached ones at once, the rest as they load. */
@Composable
private fun rememberNodeIcons(flow: CashFlowUi): Map<Long, ImageBitmap> {
    val store = LocalAppContainer.current.iconStore
    val ids = remember(flow) { flow.nodes.mapNotNull { it.iconId }.distinct() }
    var icons by remember(ids) { mutableStateOf(ids.mapNotNull { id -> store.cached(id)?.let { id to it } }.toMap()) }
    LaunchedEffect(ids) {
        if (icons.size < ids.size) icons = ids.mapNotNull { id -> store.bitmapFor(id)?.let { id to it } }.toMap()
    }
    return icons
}

/** One line of text at [at]; [color] null for the default. On a backdrop ([box]) when [pill]. */
private class NodeText(val text: TextLayoutResult, val at: Offset, val color: Color?, val box: Rect = Rect.Zero, val pill: Boolean = false)

private class NodeDrawing(
    val layout: CashNodeLayout,
    /** An account's rectangle (a caret's has [shapes] instead). */
    val body: List<Rect>,
    /** A caret's outline; empty for accounts. */
    val shapes: List<Path>,
    /** Where a node's icon goes. */
    val icon: List<Rect>,
    val nodeColor: List<Color>,
    /** Each link's centre line, stroked [CashNodeLayout.linkW] wide. */
    val links: List<Path>,
    /** A transfer's arrowhead at the receiving account. */
    val arrows: List<Path?>,
    val linkColor: List<Color>,
    /** An account's name and net change (empty for carets). */
    val texts: List<List<NodeText>>,
    /** A caret's name and amount, shown only while it is selected (empty for accounts). */
    val focus: List<List<NodeText>>,
    /** Where a tap selects each node. */
    val hits: List<Rect>,
)

private fun TextMeasurer.line(text: String, style: TextStyle, width: Float, lines: Int = 1): TextLayoutResult =
    measure(
        text, style.copy(textAlign = TextAlign.Center), overflow = TextOverflow.Ellipsis, maxLines = lines,
        constraints = Constraints(maxWidth = width.toInt().coerceAtLeast(1)),
    )

/** An account's content: its icon's size, name, net change and the gap under the icon, [height] in all. */
private class AccountContent(val icon: Float, val name: TextLayoutResult, val net: TextLayoutResult, val gap: Float) {
    val height get() = icon + gap + name.size.height + net.size.height
}

/** [node]'s content in an account rectangle [width] px wide. */
private fun accountContent(
    node: CashNode,
    width: Float,
    labels: CashLabels,
    units: Triple<String, String, String>,
    measurer: TextMeasurer,
    style: TextStyle,
    density: Density,
): AccountContent = with(density) {
    val textW = width - 2 * 5.dp.toPx()
    val bold = style.copy(fontWeight = FontWeight.Medium)
    AccountContent(
        icon = min(width * 0.36f, 24.dp.toPx()),
        name = measurer.line(labels.of(node), bold, textW, lines = 2),
        net = measurer.line(signedNet(node, units), bold, textW),
        gap = 4.dp.toPx(),
    )
}

/** An icon-only account's icon size in a rectangle [width] px wide. */
private fun accountIcon(width: Float, density: Density): Float = with(density) { min(width * 0.56f, 24.dp.toPx()) }

/** An account's net change with its sign: + kept, − drawn from its balance. */
private fun signedNet(node: CashNode, units: Triple<String, String, String>): String {
    val sign = if (node.net > 0) "+" else if (node.net < 0) "−" else ""
    return sign + compactAmount(abs(node.net), units)
}

/** The colour an account's net change is written in. */
private fun netColor(node: CashNode): Color? = when {
    node.net > 0 -> IncomeGreen
    node.net < 0 -> ExpenseRed
    else -> null
}

/** Income links take the income's colour, spending links the spending's, transfers the sender's. */
private fun linkColors(flow: CashFlowUi): List<Color> = flow.links.map { link ->
    val to = flow.nodes[link.to]
    if (to.column == CashColumn.USE) cashNodeColor(to) else cashNodeColor(flow.nodes[link.from])
}

private fun nodeDrawing(
    flow: CashFlowUi,
    g: CashNodeLayout,
    caretTexts: List<Pair<TextLayoutResult, TextLayoutResult>?>,
    labels: CashLabels,
    units: Triple<String, String, String>,
    measurer: TextMeasurer,
    style: TextStyle,
    density: Density,
    metrics: CashNodeMetrics,
    detail: Boolean,
    width: Float,
    height: Float,
): NodeDrawing = with(density) {
    val n = flow.nodes.size
    val account = BooleanArray(n) { flow.nodes[it].column == CashColumn.ACCOUNT }
    val body = (0 until n).map { i ->
        if (account[i]) Rect(Offset(g.x[i] - g.w[i] / 2f, g.y[i] - g.h[i] / 2f), Size(g.w[i], g.h[i]))
        else g.body(i).let { Rect(it[0], it[1], it[2], it[3]) }
    }
    val shapes = (0 until n).map { i ->
        Path().apply {
            if (account[i]) return@apply
            val pts = g.outline(i)
            moveTo(pts[0].first, pts[0].second)
            pts.drop(1).forEach { (x, y) -> lineTo(x, y) }
            close()
        }
    }

    // An account: icon, name (two lines at most) and net change, centred in its rectangle.
    val texts = ArrayList<List<NodeText>>()
    val icon = ArrayList<Rect>()
    for (i in 0 until n) {
        val node = flow.nodes[i]
        if (account[i] && !detail) {
            // The icon, centred; and under it the name, then the net change, when the rectangle (sized
            // for its links) has room for them whole. The rest shows on focus.
            val r = body[i]
            val s = accountIcon(r.width, density)
            val pad = 4.dp.toPx()
            val gap = 2.dp.toPx()
            val bold = style.copy(fontWeight = FontWeight.Medium)
            val textW = r.width - 2 * pad
            val room = r.height - 2 * pad
            val name = measurer.line(labels.of(node), bold, textW, lines = 2)
            val net = measurer.line(signedNet(node, units), bold, textW)
            fun whole(t: TextLayoutResult) = !t.hasVisualOverflow && t.size.width <= textW
            val withName = s + gap + name.size.height
            val shown = when {
                // The joined accounts: their dots alone, the names and sum show on focus.
                node.refId == OTHER_NODE -> emptyList()
                textW <= 0f || !whole(name) || withName > room -> emptyList()
                whole(net) && withName + net.size.height <= room -> listOf(name, net)
                else -> listOf(name)
            }
            var top = r.center.y - (s + if (shown.isEmpty()) 0f else gap + shown.sumOf { it.size.height }.toFloat()) / 2f
            icon += Rect(Offset(r.center.x - s / 2f, top), Size(s, s))
            top += s + gap
            texts += shown.mapIndexed { k, t ->
                NodeText(t, Offset(r.center.x - t.size.width / 2f, top), if (k == 1) netColor(node) else null)
                    .also { top += t.size.height }
            }
        } else if (account[i]) {
            val r = body[i]
            val c = accountContent(node, r.width, labels, units, measurer, style, density)
            val name = c.name
            val net = c.net
            var top = r.center.y - c.height / 2f
            icon += Rect(Offset(r.center.x - c.icon / 2f, top), Size(c.icon, c.icon))
            top += c.icon + c.gap
            texts += listOf(
                NodeText(name, Offset(r.center.x - name.size.width / 2f, top), null),
                NodeText(net, Offset(r.center.x - net.size.width / 2f, top + name.size.height), netColor(node)),
            )
        } else {
            val s = g.h[i] * 0.56f
            val (cx, cy) = g.iconCentre(i)
            icon += Rect(Offset(cx - s / 2f, cy - s / 2f), Size(s, s))
            texts += emptyList<NodeText>()
        }
    }

    // A node's label shows only while it is selected, on top of everything: beside it (above, below,
    // either side or at a corner), always inside the chart, where it covers least of the node's own
    // links, then of the other nodes, then of the other links.
    val inset = LABEL_INSET.toPx()
    val away = 4.dp.toPx()
    // How long a stretch of [l]'s route (lane-wide) runs under [r].
    fun covered(l: Int, r: Rect): Float {
        val pts = g.routes[l]
        val t = g.linkW[l] / 2f
        var len = 0f
        for (k in 2 until pts.size step 2) {
            val x0 = min(pts[k - 2], pts[k])
            val x1 = max(pts[k - 2], pts[k])
            val y0 = min(pts[k - 1], pts[k + 1])
            val y1 = max(pts[k - 1], pts[k + 1])
            if (x1 + t <= r.left || x0 - t >= r.right || y1 + t <= r.top || y0 - t >= r.bottom) continue
            len += max(0f, if (y0 == y1) min(x1, r.right) - max(x0, r.left) else min(y1, r.bottom) - max(y0, r.top))
        }
        return len
    }
    // [r] moved the least way that brings it inside the chart (its top-left corner, if it is bigger).
    fun inside(r: Rect): Rect {
        val dx = if (r.right > width) width - r.right else 0f
        val dy = if (r.bottom > height) height - r.bottom else 0f
        val moved = r.translate(dx, dy)
        return moved.translate(max(-moved.left, 0f), max(-moved.top, 0f))
    }
    val focus = (0 until n).map { i ->
        val (name, amount) = caretTexts[i] ?: return@map emptyList()
        val w = max(name.size.width, amount.size.width) + 2 * inset
        val h = (name.size.height + amount.size.height).toFloat() + inset
        val b = body[i]
        val left = b.left - away - w
        val right = b.right + away
        val above = b.top - away - h
        val below = b.bottom + away
        val midX = b.center.x - w / 2f
        val midY = b.center.y - h / 2f
        // Above and below: centred, then from the middle to either side (clear of a link down the
        // middle), then past either side. Beside: centred, then from the middle up or down.
        val besideX = listOf(right, left)
        val spots = (
            listOf(below, above).flatMap { y -> listOf(midX, b.center.x + away, b.center.x - away - w, right, left).map { Offset(it, y) } } +
                listOf(midY, b.center.y + away, b.center.y - away - h).flatMap { y -> besideX.map { Offset(it, y) } }
            ).map { inside(Rect(it, Size(w, h))) }
        val own = flow.links.indices.filter { flow.links[it].from == i || flow.links[it].to == i }
        val others = flow.links.indices.filter { it !in own }
        val box = spots.withIndex().minBy { (k, r) ->
            // Its own node and the others under it count as much as the longest link could; then its
            // own links, then the others'; the earlier spot wins a tie.
            val nodes = (0 until n).count { body[it].overlaps(r) } * (width + height) * 4f
            nodes + own.sumOf { covered(it, r).toDouble() }.toFloat() * 3f + others.sumOf { covered(it, r).toDouble() }.toFloat() + k * 0.01f
        }.value
        val top = box.top + inset / 2f
        listOf(
            NodeText(name, Offset(box.center.x - name.size.width / 2f, top), null, box, pill = true),
            NodeText(amount, Offset(box.center.x - amount.size.width / 2f, top + name.size.height), if (account[i]) netColor(flow.nodes[i]) else null),
        )
    }

    // Links: rounded corners; a transfer stops short of the receiver for its arrowhead.
    val corner = metrics.linkCorner
    val arrows = arrayOfNulls<Path>(flow.links.size)
    val links = flow.links.mapIndexed { l, link ->
        val pts = g.routes[l].copyOf()
        val w = g.linkW[l]
        if (flow.isTransfer(link) && pts.size >= 4) {
            val k = pts.size - 2
            val dx = pts[k] - pts[k - 2]
            val dy = pts[k + 1] - pts[k - 1]
            val len = max(hypot(dx, dy), 0.01f)
            val ux = dx / len
            val uy = dy / len
            val arrowLen = min(max(w * ARROW_LENGTH, metrics.arrow), len)
            val half = w / 2f + 4.dp.toPx()
            val tipX = pts[k]
            val tipY = pts[k + 1]
            val bx = tipX - ux * arrowLen
            val by = tipY - uy * arrowLen
            arrows[l] = Path().apply {
                moveTo(tipX, tipY)
                lineTo(bx - uy * half, by + ux * half)
                lineTo(bx + uy * half, by - ux * half)
                close()
            }
            // Under the arrowhead's base by a hair, so no gap shows between them.
            pts[k] = bx + ux
            pts[k + 1] = by + uy
        }
        roundedPath(pts, corner)
    }

    // A caret (and an icon-only account) is small: its tap target reaches a finger's width around it.
    val finger = 6.dp.toPx()
    val hits = (0 until n).map { i -> if (account[i] && detail) body[i] else body[i].inflate(finger) }
    NodeDrawing(g, body, shapes, icon, flow.nodes.map(::cashNodeColor), links, arrows.toList(), linkColors(flow), texts, focus, hits)
}

/** A path through the points ([x, y] pairs) with each corner rounded, [radius] at most. */
private fun roundedPath(pts: FloatArray, radius: Float): Path = Path().apply {
    val count = pts.size / 2
    if (count == 0) return@apply
    moveTo(pts[0], pts[1])
    for (k in 1 until count - 1) {
        val px = pts[2 * k - 2]
        val py = pts[2 * k - 1]
        val x = pts[2 * k]
        val y = pts[2 * k + 1]
        val nx = pts[2 * k + 2]
        val ny = pts[2 * k + 3]
        val inLen = hypot(x - px, y - py)
        val outLen = hypot(nx - x, ny - y)
        val r = minOf(radius, inLen / 2f, outLen / 2f)
        if (r < 0.5f) {
            lineTo(x, y)
            continue
        }
        lineTo(x - (x - px) / inLen * r, y - (y - py) / inLen * r)
        quadraticTo(x, y, x + (nx - x) / outLen * r, y + (ny - y) / outLen * r)
    }
    lineTo(pts[2 * count - 2], pts[2 * count - 1])
}
