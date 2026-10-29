package app.outgo.ui.analysis

import androidx.compose.runtime.Immutable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Sizes for [cashNodeSizes] and [layoutCashNodes], in px. Every size is only relative to the money:
 * the largest node of a kind gets the top of its range, the others shrink with the square root of
 * their amount, so a small one stays visible next to a salary.
 */
@Immutable
class CashNodeMetrics(
    val accountW: ClosedFloatingPointRange<Float>,
    /** Always taller than [accountW]: an account is a standing rectangle. */
    val accountH: ClosedFloatingPointRange<Float>,
    /** A caret's length, tail to tip; it is [CARET_HEIGHT] as thick. */
    val caret: ClosedFloatingPointRange<Float>,
    val link: ClosedFloatingPointRange<Float>,
    /** Between a caret's body and its label. */
    val labelGap: Float,
    /** Between two carets of a row or a column. */
    val gap: Float,
    /** Room between the frame of carets and the accounts, and between two accounts: where links run. */
    val lanes: Float,
    /** The routing grid's cell; links, carets and account edges sit on it. */
    val cell: Float,
    /** Least room between two links running side by side. */
    val linkGap: Float,
    /** An account's rounded corners: links leave and enter only along the straight part of a side. */
    val accountRadius: Float,
    /** Least room between an account and a link that neither starts nor ends at it. */
    val accountMargin: Float,
    /** The radius a link's bends are drawn with. */
    val linkCorner: Float,
    /** A transfer's arrowhead's least length; a thick link's is [ARROW_LENGTH] times its width. */
    val arrow: Float,
)

/** A transfer's arrowhead is at least this many times as long as the link is thick. */
const val ARROW_LENGTH = 1.6f

/** An account is at least this many times as tall as it is wide. */
const val ACCOUNT_ASPECT = 1.15f

/** A caret's thickness against its length. */
const val CARET_HEIGHT = 0.72f

/** A caret's point, against its thickness: the head runs this far back from the tip. */
private const val CARET_HEAD = 0.5f

/** How deep the notch cuts into the tail, against the caret's thickness. */
private const val CARET_NOTCH = 0.3f

/** How far ahead of a caret's centre its icon sits, against the caret's thickness. */
private const val ICON_AHEAD = 0.03f

/** Each node's body size and each link's thickness, from [cashNodeSizes]. */
@Immutable
class CashNodeSizes(
    /** An account's width, a caret's length (tail to tip). */
    val w: FloatArray,
    /** An account's height, a caret's thickness. */
    val h: FloatArray,
    val linkW: FloatArray,
)

/** The sizes of [flow]'s nodes and links, square-root relative to the money (see [CashNodeMetrics]). */
fun cashNodeSizes(flow: CashFlowUi, m: CashNodeMetrics): CashNodeSizes {
    val nodes = flow.nodes
    val links = flow.links
    val hub = BooleanArray(nodes.size) { nodes[it].column == CashColumn.ACCOUNT }
    fun ClosedFloatingPointRange<Float>.at(t: Float) = start + (endInclusive - start) * t
    fun share(v: Long, most: Long) = sqrt(v.toDouble() / most.coerceAtLeast(1L)).toFloat().coerceIn(0f, 1f)
    val mostHub = nodes.filterIndexed { i, _ -> hub[i] }.maxOfOrNull { it.total } ?: 1L
    val mostCaret = nodes.filterIndexed { i, _ -> !hub[i] }.maxOfOrNull { it.total } ?: 1L
    val mostLink = links.maxOfOrNull { it.total } ?: 1L
    val w = FloatArray(nodes.size)
    val h = FloatArray(nodes.size)
    nodes.forEachIndexed { i, node ->
        if (hub[i]) {
            val t = share(node.total, mostHub)
            w[i] = m.accountW.at(t)
            h[i] = max(m.accountH.at(t), w[i] * ACCOUNT_ASPECT)
        } else {
            w[i] = m.caret.at(share(node.total, mostCaret))
            h[i] = w[i] * CARET_HEIGHT
        }
    }
    return CashNodeSizes(w, h, FloatArray(links.size) { m.link.at(share(links[it].total, mostLink)) })
}

/**
 * These sizes with each account's room above and below its content halved: [contentH] is how tall
 * each account's icon, name and net change stand together (0 for carets). Still taller than wide.
 */
fun CashNodeSizes.halveAccountPadding(contentH: FloatArray): CashNodeSizes {
    val nh = h.copyOf()
    contentH.forEachIndexed { i, c ->
        if (c > 0f) nh[i] = maxOf((h[i] + c) / 2f, c, w[i] * ACCOUNT_ASPECT)
    }
    return CashNodeSizes(w, nh, linkW)
}

/**
 * Where [layoutCashNodes] put everything. A node's body is centred on ([x], [y]), [w] by [h]: an
 * account's rectangle, or a caret [w] long and [h] thick, pointing down ([down]) or right. A caret's
 * label is centred on [labelX], its top at [labelTop]. [routes] holds each link's path as x, y pairs,
 * from where it leaves its source to where it enters its target, every segment level or upright.
 * [frame] (left, top, right, bottom) is where links may run: inside the carets' tips and tails, so a
 * link never goes round to the far side of a caret.
 */
@Immutable
class CashNodeLayout(
    val x: FloatArray,
    val y: FloatArray,
    val w: FloatArray,
    val h: FloatArray,
    val down: BooleanArray,
    val labelX: FloatArray,
    val labelTop: FloatArray,
    val linkW: FloatArray,
    val routes: List<FloatArray>,
    val frame: FloatArray,
) {
    private fun angle(i: Int) = if (down[i]) (PI / 2).toFloat() else 0f

    /** The tip of caret [i]: income leaves it from here. */
    fun tip(i: Int): Pair<Float, Float> = along(i, w[i] / 2f)

    /** The inner corner of caret [i]'s notch: spending comes into it here. */
    fun notch(i: Int): Pair<Float, Float> = along(i, -w[i] / 2f + h[i] * CARET_NOTCH)

    /**
     * Where a link [width] thick meets caret [i], drawn under it. An income's starts a little back
     * from the tip, where the head is as wide as the link, so it runs on from the head's slopes; a
     * spending's ends in the notch's inner corner, its square end hidden in the caret's body.
     */
    fun linkEnd(i: Int, income: Boolean, width: Float): Pair<Float, Float> {
        if (!income) return notch(i)
        val head = h[i] * CARET_HEAD
        return along(i, w[i] / 2f - (width * head / h[i]).coerceAtMost(head))
    }

    /** The point [d] px from caret [i]'s centre along the way it points. */
    private fun along(i: Int, d: Float) = (x[i] + cos(angle(i)) * d) to (y[i] + sin(angle(i)) * d)

    /** Caret [i]'s outline, tip first, clockwise. */
    fun outline(i: Int): List<Pair<Float, Float>> {
        val hw = w[i] / 2f
        val hh = h[i] / 2f
        val head = h[i] * CARET_HEAD
        val local = listOf(hw to 0f, hw - head to hh, -hw to hh, -hw + h[i] * CARET_NOTCH to 0f, -hw to -hh, hw - head to -hh)
        val c = cos(angle(i))
        val s = sin(angle(i))
        return local.map { (px, py) -> (x[i] + px * c - py * s) to (y[i] + px * s + py * c) }
    }

    /**
     * Where caret [i]'s icon is centred: in its body, a little toward the head, where the caret is
     * still thick enough for it.
     */
    fun iconCentre(i: Int): Pair<Float, Float> = along(i, h[i] * ICON_AHEAD)

    /** The body's bounds: left, top, right, bottom. */
    fun body(i: Int): FloatArray {
        val bw = if (down[i]) h[i] else w[i]
        val bh = if (down[i]) w[i] else h[i]
        return floatArrayOf(x[i] - bw / 2f, y[i] - bh / 2f, x[i] + bw / 2f, y[i] + bh / 2f)
    }
}

/** Where a caret sits in the frame. */
private const val TOP = 0
private const val LEFT = 1
private const val BOTTOM = 2
private const val RIGHT = 3

/**
 * The part of [layoutCashNodes] that needs only the width: the accounts' staircase order, each
 * caret's account, and which edge each caret goes to (rows first, the rest to the columns).
 */
private class FramePlan(
    val w: FloatArray,
    val h: FloatArray,
    val hub: BooleanArray,
    val order: List<Int>,
    val step: (Int) -> Float,
    val home: IntArray,
    val incomes: List<Int>,
    val spends: List<Int>,
    val side: IntArray,
    val leftW: Float,
    val rightW: Float,
    val topH: Float,
    val bottomH: Float,
    /** A column caret's room, label included. */
    val colH: (Int) -> Float,
)

private fun planFrame(flow: CashFlowUi, width: Float, sizes: CashNodeSizes, labelW: FloatArray, labelH: FloatArray, m: CashNodeMetrics): FramePlan {
    val nodes = flow.nodes
    val n = nodes.size
    val links = flow.links
    // Copies: the accounts may shrink to fit.
    val w = sizes.w.copyOf()
    val h = sizes.h.copyOf()
    val hub = BooleanArray(n) { nodes[it].column == CashColumn.ACCOUNT }

    // Accounts in transfer order: the receiver of a net transfer after its sender (capped, so a cycle
    // of transfers ends), then the busiest first.
    val moved = HashMap<Pair<Int, Int>, Long>()
    links.forEach { if (flow.isTransfer(it)) moved[it.from to it.to] = (moved[it.from to it.to] ?: 0L) + it.total }
    val downstream = moved.filter { (k, v) -> v > (moved[k.second to k.first] ?: 0L) }.keys
    val hubs = (0 until n).filter { hub[it] }
    val level = IntArray(n)
    repeat(hubs.size) {
        downstream.forEach { (a, b) -> level[b] = max(level[b], min(level[a] + 1, hubs.size - 1)) }
    }
    val order = hubs.sortedWith(compareBy<Int> { level[it] }.thenBy { it })
    val rank = IntArray(n)
    order.forEachIndexed { k, i -> rank[i] = k }
    // Where along the staircase each account stands, 0 (top-left) to 1 (bottom-right).
    fun step(i: Int) = if (order.size == 1) 0.5f else rank[i].toFloat() / (order.size - 1)

    // Each caret's account: the one it trades most with.
    val home = IntArray(n) { -1 }
    val best = LongArray(n)
    links.forEach { link ->
        val caret = if (hub[link.from]) link.to else link.from
        val account = if (hub[link.from]) link.from else link.to
        if (hub[caret] || !hub[account]) return@forEach
        if (link.total > best[caret]) {
            best[caret] = link.total
            home[caret] = account
        }
    }
    fun homeStep(i: Int) = if (home[i] >= 0) step(home[i]) else 0.5f

    // A caret's room in a row (pointing down) and in a column (pointing right), label included.
    fun rowW(i: Int) = max(h[i], labelW[i])
    fun rowH(i: Int) = w[i] + m.labelGap + labelH[i]
    fun colW(i: Int) = max(w[i], labelW[i])
    fun colH(i: Int) = h[i] + m.labelGap + labelH[i]
    fun length(ids: List<Int>, size: (Int) -> Float) = ids.sumOf { size(it).toDouble() }.toFloat() + m.gap * (ids.size - 1).coerceAtLeast(0)

    // Rows first; whatever doesn't fit goes to the column. Incomes for the accounts furthest right
    // take the top row (the left column is far from them), spending of those furthest left the bottom.
    val incomes = (0 until n).filter { nodes[it].column == CashColumn.SOURCE }.sortedByDescending { homeStep(it) }
    val spends = (0 until n).filter { nodes[it].column == CashColumn.USE }.sortedBy { homeStep(it) }
    val side = IntArray(n) { -1 }
    var leftW = 0f
    var rightW = 0f
    repeat(2) {
        fun fill(ids: List<Int>, row: Int, column: Int, room: Float): Float {
            val inRow = ArrayList<Int>()
            ids.forEach { i ->
                if (length(inRow + i) { rowW(it) } <= room) inRow += i
            }
            ids.forEach { side[it] = if (it in inRow) row else column }
            return ids.filter { side[it] == column }.maxOfOrNull { colW(it) } ?: 0f
        }
        val room = width - leftW - rightW - 2 * m.gap
        leftW = fill(incomes, TOP, LEFT, room)
        rightW = fill(spends, BOTTOM, RIGHT, room)
    }
    val topH = incomes.filter { side[it] == TOP }.maxOfOrNull { rowH(it) } ?: 0f
    val bottomH = spends.filter { side[it] == BOTTOM }.maxOfOrNull { rowH(it) } ?: 0f

    return FramePlan(w, h, hub, order, ::step, home, incomes, spends, side, leftW, rightW, topH, bottomH, ::colH)
}

/**
 * How tall [layoutCashNodes] needs [width] px to be for every node to keep its own size: the
 * accounts' staircase at full size with a lane between each two, and each side column's carets.
 * A scrolling full screen draws the chart this tall (or the screen's height, if that is more).
 */
fun cashNaturalHeight(flow: CashFlowUi, width: Float, sizes: CashNodeSizes, labelW: FloatArray, labelH: FloatArray, m: CashNodeMetrics): Float {
    val p = planFrame(flow, width, sizes, labelW, labelH, m)
    // The staircase steps by the tallest account; a lane above, below and between each two, and a
    // cell either end for the grid's rounding.
    val tallest = p.order.maxOfOrNull { p.h[it] } ?: 0f
    val accounts = tallest * p.order.size + m.lanes * (p.order.size + 1) + 2 * m.cell
    fun column(edge: Int, ids: List<Int>): Float {
        val inColumn = ids.filter { p.side[it] == edge }
        return inColumn.sumOf { p.colH(it).toDouble() }.toFloat() + m.gap * (inColumn.size + 1)
    }
    val columns = max(column(LEFT, p.incomes), column(RIGHT, p.spends))
    return p.topH + p.bottomH + max(accounts, columns)
}

/**
 * Lays [flow] out [width] by [height] px as a frame: incomes along the top edge and down the left
 * edge, spending along the bottom edge and down the right edge, the accounts in the middle. Money
 * only flows down and to the right: an income's caret points down (top row) or right (left column)
 * at its account, a spending's caret points down or right away from it, and an account takes money
 * in at its top and left sides and pays it out at its bottom and right sides.
 *
 * The accounts stand in a staircase from the top-left to the bottom-right, an account that gets more
 * from another by transfer than it sends back after that one, so transfers run down and right too.
 * Each caret goes as close as its row (or column) allows to the account it trades most with; the top
 * and bottom rows fill first, the carets that don't fit go to the columns. Then [routeCashLinks] finds
 * each link's way around the nodes and the other links. [sizes] come from [cashNodeSizes]; [labelW]
 * and [labelH] are each caret's label size (0 for accounts, whose label goes inside).
 */
fun layoutCashNodes(
    flow: CashFlowUi,
    width: Float,
    height: Float,
    sizes: CashNodeSizes,
    labelW: FloatArray,
    labelH: FloatArray,
    m: CashNodeMetrics,
): CashNodeLayout {
    val p = planFrame(flow, width, sizes, labelW, labelH, m)
    val nodes = flow.nodes
    val n = nodes.size
    val w = p.w
    val h = p.h
    val g = m.cell
    fun snapCentre(v: Float) = (floor(v / g) + 0.5f) * g
    fun snapEdge(v: Float) = Math.round(v / g) * g
    val order = p.order
    val step = p.step
    val home = p.home
    val incomes = p.incomes
    val spends = p.spends
    val side = p.side
    val leftW = p.leftW
    val rightW = p.rightW
    val topH = p.topH
    val bottomH = p.bottomH
    fun rowW(i: Int) = max(h[i], labelW[i])
    val colH = p.colH

    // The accounts' staircase, in the middle, from its top-left corner to its bottom-right one. Two
    // accounts in a row must clear each other one way or the other, with a lane between: if neither
    // way has the room, every account shrinks until one has (never wider than tall).
    val x = FloatArray(n)
    val y = FloatArray(n)
    val midL = snapEdge(leftW + m.lanes)
    val midR = snapEdge(width - rightW - m.lanes)
    val midT = snapEdge(topH + m.lanes)
    val midB = snapEdge(height - bottomH - m.lanes)
    val midW = max(midR - midL, 1f)
    val midH = max(midB - midT, 1f)
    if (order.size > 1) {
        val gaps = (order.size - 1).toFloat()
        fun fits(f: Float): Boolean {
            val bw = order.maxOf { w[it] } * f
            val bh = order.maxOf { h[it] } * f
            return (midW - bw) / gaps >= bw + m.lanes || (midH - bh) / gaps >= bh + m.lanes
        }
        if (!fits(1f)) {
            var lo = 0.3f
            var hi = 1f
            repeat(16) {
                val mid = (lo + hi) / 2f
                if (fits(mid)) lo = mid else hi = mid
            }
            order.forEach {
                w[it] *= lo
                h[it] = max(h[it] * lo, w[it] * ACCOUNT_ASPECT)
            }
        }
    } else if (order.size == 1) {
        val i = order.first()
        if (h[i] > midH) {
            w[i] = min(w[i], midH / ACCOUNT_ASPECT)
            h[i] = max(midH, w[i] * ACCOUNT_ASPECT)
        }
    }
    order.forEach { i ->
        val left = snapEdge(midL + (midW - w[i]).coerceAtLeast(0f) * step(i))
        val top = snapEdge(midT + (midH - h[i]).coerceAtLeast(0f) * step(i))
        x[i] = left + w[i] / 2f
        y[i] = top + h[i] / 2f
    }

    // Carets: each as near its account as its row or column allows, kept in order, apart, inside.
    val down = BooleanArray(n)
    val labelX = FloatArray(n)
    val labelTop = FloatArray(n)
    // Each wants to stand halfway between its account and its share of the row, so the carets of one
    // account don't bunch up (and crowd their links) while the others leave the row empty.
    fun place(ids: List<Int>, along: (Int) -> Float, size: (Int) -> Float, from: Float, to: Float, put: (Int, Float) -> Unit) {
        val sorted = ids.sortedBy(along)
        val wants = sorted.mapIndexed { k, i ->
            val slot = from + (to - from) * (k + 0.5f) / sorted.size
            (along(i) + slot) / 2f
        }
        val starts = spreadStarts(wants, sorted.map(size), from, to, m.gap)
        sorted.forEachIndexed { k, i -> put(i, snapCentre(starts[k] + size(i) / 2f)) }
    }
    fun homeX(i: Int) = if (home[i] >= 0) x[home[i]] else width / 2f
    fun homeY(i: Int) = if (home[i] >= 0) y[home[i]] else height / 2f
    // Tips on one line under the top row, tails on one line above the bottom row, and the same down
    // the columns: the labels hang off the far side.
    val tipY = snapEdge(topH)
    val tailY = snapEdge(height - bottomH)
    val tipX = snapEdge(leftW)
    val tailX = snapEdge(width - rightW)
    place(incomes.filter { side[it] == TOP }, ::homeX, ::rowW, leftW, width - rightW) { i, cx ->
        down[i] = true
        x[i] = cx
        y[i] = tipY - w[i] / 2f
        labelX[i] = cx
        labelTop[i] = tipY - w[i] - m.labelGap - labelH[i]
    }
    place(spends.filter { side[it] == BOTTOM }, ::homeX, ::rowW, leftW, width - rightW) { i, cx ->
        down[i] = true
        x[i] = cx
        y[i] = tailY + w[i] / 2f
        labelX[i] = cx
        labelTop[i] = tailY + w[i] + m.labelGap
    }
    place(incomes.filter { side[it] == LEFT }, ::homeY, colH, topH, height - bottomH) { i, cy ->
        x[i] = tipX - w[i] / 2f
        y[i] = cy - (m.labelGap + labelH[i]) / 2f
        labelX[i] = (tipX - labelW[i] / 2f).coerceAtMost(x[i])
        labelTop[i] = y[i] + h[i] / 2f + m.labelGap
    }
    place(spends.filter { side[it] == RIGHT }, ::homeY, colH, topH, height - bottomH) { i, cy ->
        x[i] = tailX + w[i] / 2f
        y[i] = cy - (m.labelGap + labelH[i]) / 2f
        labelX[i] = (tailX + labelW[i] / 2f).coerceAtLeast(x[i])
        labelTop[i] = y[i] + h[i] / 2f + m.labelGap
    }
    // A column caret's centre goes on the grid too, so its link runs along a lane.
    (0 until n).filter { side[it] == LEFT || side[it] == RIGHT }.forEach { i ->
        val snapped = snapCentre(y[i])
        labelTop[i] += snapped - y[i]
        y[i] = snapped
    }

    val frame = floatArrayOf(tipX, tipY, tailX, tailY)
    val layout = CashNodeLayout(x, y, w, h, down, labelX, labelTop, sizes.linkW, emptyList(), frame)
    val routes = routeCashLinks(flow, layout, labelW, labelH, width, height, m)
    return CashNodeLayout(x, y, w, h, down, labelX, labelTop, sizes.linkW, routes, frame)
}

/**
 * Starts of blocks [sizes] long, in order, each as close to centred on its [centers] (ascending) as
 * the others allow: [spacing] apart, inside [from]..[to] when they fit.
 */
internal fun spreadStarts(centers: List<Float>, sizes: List<Float>, from: Float, to: Float, spacing: Float): FloatArray {
    val out = FloatArray(centers.size)
    var floor = from
    centers.indices.forEach { i ->
        out[i] = max(centers[i] - sizes[i] / 2f, floor)
        floor = out[i] + sizes[i] + spacing
    }
    var ceiling = to
    centers.indices.reversed().forEach { i ->
        out[i] = max(from, min(out[i], ceiling - sizes[i]))
        ceiling = out[i] - spacing
    }
    return out
}
