package app.outgo.ui.analysis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
) {
    val scheme = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.labelSmall
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    val metrics = remember(full, density) { cashMetrics(full, density) }
    // Accounts only as tall as their content needs, plus half the room the size alone would leave.
    val sizes = remember(flow, metrics, labels, units, style) {
        val plain = cashNodeSizes(flow, metrics)
        val contentH = FloatArray(flow.nodes.size) { i ->
            val node = flow.nodes[i]
            if (node.column == CashColumn.ACCOUNT) accountContent(node, plain.w[i], labels, units, measurer, style, density).height else 0f
        }
        plain.halveAccountPadding(contentH)
    }
    // Caret labels, shown only for the selected caret: the layout leaves no room for them.
    val caretTexts = remember(flow, labels, units, style, full, density) {
        val maxW = with(density) { (if (full) 96.dp else 72.dp).toPx() }
        flow.nodes.map { node ->
            if (node.column == CashColumn.ACCOUNT) null
            else measurer.line(labels.of(node), style, maxW) to measurer.line(compactAmount(node.total, units), style, maxW)
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
    val layout = made?.takeIf { it.first === flow && it.second === caretTexts }?.third
    val drawing = remember(layout, labels, units, style, density) {
        layout?.let { nodeDrawing(flow, it, caretTexts, labels, units, measurer, style, density, size.width.toFloat(), size.height.toFloat()) }
    }
    val icons = rememberNodeIcons(flow)
    val glyphs = CaretGlyphs(
        other = painterResource(R.drawable.ph_dots_three),
        adjust = painterResource(R.drawable.ph_pencil_simple),
        accounts = painterResource(R.drawable.ph_swap_horizontal),
    )
    val select by rememberUpdatedState(onSelect)
    val picked by rememberUpdatedState(selected)

    Canvas(
        modifier = modifier
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
        val sel = selected
        fun lit(l: Int) = sel == null || flow.links[l].from == sel || flow.links[l].to == sel
        fun nodeLit(i: Int) = sel == null || i == sel || flow.links.any { (it.from == sel && it.to == i) || (it.to == sel && it.from == i) }

        // Links first, the faded ones under the lit ones, so the selection reads on top.
        flow.links.indices.sortedBy { lit(it) }.forEach { l ->
            val alpha = p * when {
                sel == null -> 0.5f
                lit(l) -> 0.8f
                else -> 0.08f
            }
            drawPath(d.links[l], d.linkColor[l], alpha = alpha, style = Stroke(g.linkW[l], join = StrokeJoin.Round))
            d.arrows[l]?.let { drawPath(it, d.linkColor[l], alpha = alpha) }
        }

        val radius = CornerRadius(10.dp.toPx())
        val border = Stroke(1.5.dp.toPx(), join = StrokeJoin.Round)
        flow.nodes.indices.forEach { i ->
            val node = flow.nodes[i]
            val fade = p * if (nodeLit(i)) 1f else 0.3f
            val color = d.nodeColor[i]
            if (node.column == CashColumn.ACCOUNT) {
                val r = d.body[i]
                // Opaque under the tint, so the links ending at its edges stop there.
                drawRoundRect(scheme.surface, r.topLeft, r.size, radius, alpha = p)
                drawRoundRect(color, r.topLeft, r.size, radius, alpha = 0.2f * fade)
                if (i == sel) {
                    val o = 2.5.dp.toPx()
                    drawRoundRect(scheme.onSurface, r.topLeft - Offset(o, o), Size(r.width + 2 * o, r.height + 2 * o), CornerRadius(radius.x + o), style = Stroke(1.5.dp.toPx()))
                }
                val box = d.icon[i]
                val bitmap = node.iconId?.let { icons[it] }
                if (bitmap != null) drawBitmap(bitmap, box, fade, ColorFilter.tint(color))
            } else {
                // The selection's outline under the caret, so it shows only around the edge.
                if (i == sel) drawPath(d.shapes[i], scheme.onSurface, alpha = 0.9f * p, style = Stroke(6.5.dp.toPx(), join = StrokeJoin.Round))
                // Opaque first, so a link passing under a faded caret stays hidden.
                drawPath(d.shapes[i], scheme.surface, alpha = p)
                drawPath(d.shapes[i], color, alpha = fade)
                drawPath(d.shapes[i], color, alpha = fade, style = border)
                val box = d.icon[i]
                val bitmap = node.iconId?.let { icons[it] }
                val tint = ColorFilter.tint(Color.White)
                when {
                    bitmap != null -> drawBitmap(bitmap, box, fade, tint)
                    node.iconId == null -> glyphs.of(node)?.let { drawGlyph(it, box, fade, tint) }
                }
            }
        }
        flow.nodes.indices.forEach { i ->
            val faded = if (nodeLit(i)) 1f else 0.35f
            d.texts[i].forEach { t -> drawText(t.text, (t.color ?: scheme.onSurface).copy(alpha = faded), t.at) }
        }
        // The selected caret's label, over everything.
        if (sel != null) {
            d.focus[sel].forEach { t ->
                if (t.pill) {
                    val corner = CornerRadius(6.dp.toPx())
                    drawRoundRect(scheme.surface, t.box.topLeft, t.box.size, corner, alpha = 0.95f * p)
                    drawRoundRect(d.nodeColor[sel], t.box.topLeft, t.box.size, corner, alpha = p, style = Stroke(1.dp.toPx()))
                }
                drawText(t.text, t.color ?: scheme.onSurface, t.at)
            }
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

/** Room around a selected caret's label text, inside its backdrop. */
private val LABEL_INSET = 3.dp

/** The chart's sizes, in px: roomier in full screen. */
private fun cashMetrics(full: Boolean, density: Density): CashNodeMetrics = with(density) {
    if (full) {
        CashNodeMetrics(
            accountW = 64.dp.toPx()..88.dp.toPx(), accountH = 92.dp.toPx()..128.dp.toPx(),
            caret = 30.dp.toPx()..44.dp.toPx(), link = 2.dp.toPx()..10.dp.toPx(),
            labelGap = 2.dp.toPx(), gap = 8.dp.toPx(), lanes = 32.dp.toPx(), cell = 4.dp.toPx(), linkGap = 3.dp.toPx(),
        )
    } else {
        CashNodeMetrics(
            accountW = 54.dp.toPx()..66.dp.toPx(), accountH = 80.dp.toPx()..100.dp.toPx(),
            caret = 22.dp.toPx()..32.dp.toPx(), link = 1.5.dp.toPx()..7.dp.toPx(),
            labelGap = 1.dp.toPx(), gap = 4.dp.toPx(), lanes = 22.dp.toPx(), cell = 3.dp.toPx(), linkGap = 2.dp.toPx(),
        )
    }
}

/** What a caret with no icon of its own shows. */
private class CaretGlyphs(val other: Painter, val adjust: Painter, val accounts: Painter) {
    fun of(node: CashNode): Painter? = when {
        node.refId == OTHER_NODE -> other
        node.kind == CashKind.ADJUST_IN || node.kind == CashKind.ADJUST_OUT -> adjust
        node.kind == CashKind.OTHER_IN || node.kind == CashKind.OTHER_OUT -> accounts
        else -> null
    }
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
    val sign = if (node.net > 0) "+" else if (node.net < 0) "−" else ""
    AccountContent(
        icon = min(width * 0.36f, 24.dp.toPx()),
        name = measurer.line(labels.of(node), bold, textW, lines = 2),
        net = measurer.line(sign + compactAmount(abs(node.net), units), bold, textW),
        gap = 4.dp.toPx(),
    )
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
        if (account[i]) {
            val r = body[i]
            val c = accountContent(node, r.width, labels, units, measurer, style, density)
            val name = c.name
            val net = c.net
            var top = r.center.y - c.height / 2f
            icon += Rect(Offset(r.center.x - c.icon / 2f, top), Size(c.icon, c.icon))
            top += c.icon + c.gap
            val netColor = when {
                node.net > 0 -> IncomeGreen
                node.net < 0 -> ExpenseRed
                else -> null
            }
            texts += listOf(
                NodeText(name, Offset(r.center.x - name.size.width / 2f, top), null),
                NodeText(net, Offset(r.center.x - net.size.width / 2f, top + name.size.height), netColor),
            )
        } else {
            val s = g.h[i] * 0.56f
            val (cx, cy) = g.iconCentre(i)
            icon += Rect(Offset(cx - s / 2f, cy - s / 2f), Size(s, s))
            texts += emptyList<NodeText>()
        }
    }

    // A caret's label shows only while it is selected, on top of everything: beside the caret where
    // it fits inside the chart, clear of the other nodes if it can be.
    val inset = LABEL_INSET.toPx()
    val away = 4.dp.toPx()
    val focus = (0 until n).map { i ->
        val (name, amount) = caretTexts[i] ?: return@map emptyList()
        val w = max(name.size.width, amount.size.width) + 2 * inset
        val h = (name.size.height + amount.size.height).toFloat() + inset
        val b = body[i]
        val spots = listOf(
            Offset(b.center.x - w / 2f, b.bottom + away),
            Offset(b.center.x - w / 2f, b.top - away - h),
            Offset(b.right + away, b.center.y - h / 2f),
            Offset(b.left - away - w, b.center.y - h / 2f),
        ).map { Rect(it, Size(w, h)) }
        fun inside(r: Rect) = r.left >= 0f && r.top >= 0f && r.right <= width && r.bottom <= height
        fun clear(r: Rect) = (0 until n).none { it != i && body[it].overlaps(r) }
        val box = spots.firstOrNull { inside(it) && clear(it) } ?: spots.firstOrNull(::inside) ?: spots.first().let { r ->
            // Nowhere fits: below, pushed back inside.
            r.translate(-min(r.left, 0f) - max(r.right - width, 0f), -min(r.top, 0f) - max(r.bottom - height, 0f))
        }
        val top = box.top + inset / 2f
        listOf(
            NodeText(name, Offset(box.center.x - name.size.width / 2f, top), null, box, pill = true),
            NodeText(amount, Offset(box.center.x - amount.size.width / 2f, top + name.size.height), null),
        )
    }

    // Links: rounded corners; a transfer stops short of the receiver for its arrowhead.
    val corner = 8.dp.toPx()
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
            val arrowLen = min(max(w * 1.6f, 9.dp.toPx()), len)
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

    // A caret is small: its tap target reaches a finger's width around it.
    val finger = 6.dp.toPx()
    val hits = (0 until n).map { i -> if (account[i]) body[i] else body[i].inflate(finger) }
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
