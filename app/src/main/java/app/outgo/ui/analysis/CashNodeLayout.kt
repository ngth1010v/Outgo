package app.outgo.ui.analysis

import androidx.compose.runtime.Immutable
import kotlin.math.PI
import kotlin.math.abs
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
    /**
     * The least width an account keeps when the layout shrinks the accounts to fit: room for its
     * links side by side along one side. 0 for carets.
     */
    val minW: FloatArray = FloatArray(w.size),
)

/**
 * The sizes of [flow]'s nodes and links (see [CashNodeMetrics]). Carets and links go by the money,
 * square-root relative. An account is the smallest of its range, or just big enough for its links
 * each to have its own lane between the rounded corners (as the router spaces them) if that is more.
 */
fun cashNodeSizes(flow: CashFlowUi, m: CashNodeMetrics): CashNodeSizes {
    val nodes = flow.nodes
    val links = flow.links
    val hub = BooleanArray(nodes.size) { nodes[it].column == CashColumn.ACCOUNT }
    fun ClosedFloatingPointRange<Float>.at(t: Float) = start + (endInclusive - start) * t
    fun share(v: Long, most: Long) = sqrt(v.toDouble() / most.coerceAtLeast(1L)).toFloat().coerceIn(0f, 1f)
    val mostCaret = nodes.filterIndexed { i, _ -> !hub[i] }.maxOfOrNull { it.total } ?: 1L
    val mostLink = links.maxOfOrNull { it.total } ?: 1L
    val linkW = FloatArray(links.size) { m.link.at(share(links[it].total, mostLink)) }

    // How the router spaces links side by side (see routeCashLinks): a link keeps [keep] cells clear
    // either side of its centre, and takes [wide] cells either side itself.
    val g = m.cell
    fun wide(l: Int) = floor(linkW[l] / 2f / g + 0.5f)
    fun keep(l: Int) = floor((linkW[l] / 2f + m.linkGap) / g)
    // The length of side the links [ids] need, side by side between the rounded corners: half the
    // outer two, the router's least spacing between each two, and a cell for the grid's rounding.
    fun need(ids: List<Int>): Float {
        if (ids.isEmpty()) return 0f
        // Two cells: one for the grid's rounding, one spare, as the router may not pack them tight.
        var len = 2 * m.accountRadius + 2 * g + (linkW[ids.first()] + linkW[ids.last()]) / 2f
        for (k in 1 until ids.size) {
            val a = ids[k - 1]
            val b = ids[k]
            len += (max(wide(a) + keep(b), wide(b) + keep(a)) + 1f) * g
        }
        return len
    }
    // The least width for a way's links (in or out), which leave or enter by two sides (out: bottom
    // and right; in: top and left). The upright side is [ACCOUNT_ASPECT] times the level one, so it
    // takes its share of them: the widest first, each to the side that keeps the width smaller.
    fun least(ids: List<Int>): Float {
        val level = ArrayList<Int>()
        val upright = ArrayList<Int>()
        ids.sortedByDescending { linkW[it] }.forEach { l ->
            val onLevel = max(need(level + l), need(upright) / ACCOUNT_ASPECT)
            val onUpright = max(need(level), need(upright + l) / ACCOUNT_ASPECT)
            if (onLevel <= onUpright) level += l else upright += l
        }
        return max(need(level), need(upright) / ACCOUNT_ASPECT)
    }

    val w = FloatArray(nodes.size)
    val h = FloatArray(nodes.size)
    val minW = FloatArray(nodes.size)
    nodes.indices.forEach { i ->
        if (hub[i]) {
            val outs = links.indices.filter { links[it].from == i }
            val ins = links.indices.filter { links[it].to == i }
            minW[i] = max(least(outs), least(ins))
            // The range's smallest, or just enough for the links.
            w[i] = max(m.accountW.start, minW[i])
            h[i] = max(m.accountH.start, w[i] * ACCOUNT_ASPECT)
        } else {
            w[i] = m.caret.at(share(nodes[i].total, mostCaret))
            h[i] = w[i] * CARET_HEIGHT
        }
    }
    return CashNodeSizes(w, h, linkW, minW)
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
    return CashNodeSizes(w, nh, linkW, minW)
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
 * The part of [layoutCashNodes] that needs only the width: the accounts' transfer levels, each
 * caret's account, and which edge each caret goes to (rows first, the rest to the columns).
 */
private class FramePlan(
    val w: FloatArray,
    val h: FloatArray,
    val hub: BooleanArray,
    val order: List<Int>,
    /** [order] by transfer level: no account gets more by transfer from one of its own level or a later one. */
    val levels: List<List<Int>>,
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
    /** The left and right edges of the room the accounts stand in. */
    val midL: Float,
    val midR: Float,
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
    val levels = order.groupBy { level[it] }.values.toList()
    // Roughly how far right (or down) each account stands, 0 to 1: which carets go to the rows.
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

    val g = m.cell
    val midL = Math.round((leftW + m.lanes) / g) * g
    val midR = Math.round((width - rightW - m.lanes) / g) * g
    return FramePlan(w, h, hub, order, levels, ::step, home, incomes, spends, side, leftW, rightW, topH, bottomH, ::colH, midL, midR)
}

/**
 * [levels]' accounts in lines [crossLen] long: each level's in order along a line, a lane apart, and
 * a new line (of the same level) where the next one doesn't fit. [cross] is an account's size along
 * a line. No account gets money by transfer from one in its own line or a later one.
 */
private fun packLines(levels: List<List<Int>>, cross: (Int) -> Float, crossLen: Float, lane: Float): List<List<Int>> {
    val lines = ArrayList<List<Int>>()
    levels.forEach { level ->
        var line = ArrayList<Int>()
        var len = 0f
        level.forEach { i ->
            val next = if (line.isEmpty()) cross(i) else len + lane + cross(i)
            if (line.isNotEmpty() && next > crossLen) {
                lines += line
                line = arrayListOf(i)
                len = cross(i)
            } else {
                line += i
                len = next
            }
        }
        if (line.isNotEmpty()) lines += line
    }
    return lines
}

/** How deep [lines] stand together, each as deep as its deepest account ([main]), a lane between. */
private fun linesDepth(lines: List<List<Int>>, main: (Int) -> Float, lane: Float): Float =
    lines.sumOf { line -> line.maxOf(main).toDouble() }.toFloat() + lane * (lines.size - 1).coerceAtLeast(0)

/** How long the longest of [lines] is, its accounts ([cross]) a lane apart. */
private fun linesLength(lines: List<List<Int>>, cross: (Int) -> Float, lane: Float): Float =
    lines.maxOfOrNull { line -> line.sumOf { cross(it).toDouble() }.toFloat() + lane * (line.size - 1) } ?: 0f

/**
 * The room links need in each gap of [lines]: before the first line, between each two, after the
 * last. A link crosses the gaps between its account's line and the edge its caret is on, when that
 * caret faces the lines (the top row for rows, the left column for columns; the bottom row or right
 * column on the far side), and a transfer the gaps between its accounts' lines. [across]: the lines
 * are columns, the levels going left to right.
 */
private fun gapNeeds(flow: CashFlowUi, lines: List<List<Int>>, side: IntArray, linkW: FloatArray, across: Boolean, m: CashNodeMetrics): FloatArray {
    val lineOf = IntArray(flow.nodes.size) { -1 }
    lines.forEachIndexed { k, line -> line.forEach { lineOf[it] = k } }
    val before = if (across) LEFT else TOP
    val after = if (across) RIGHT else BOTTOM
    val need = FloatArray(lines.size + 1)
    flow.links.forEachIndexed { l, link ->
        val a = lineOf[link.from]
        val b = lineOf[link.to]
        val crossed = when {
            a >= 0 && b >= 0 -> (min(a, b) + 1)..max(a, b)
            b >= 0 && side[link.from] == before -> 0..b
            a >= 0 && side[link.to] == after -> (a + 1)..lines.size
            else -> IntRange.EMPTY
        }
        crossed.forEach { need[it] += linkW[l] + m.linkGap }
    }
    return need
}

/**
 * How tall [layoutCashNodes] needs [width] px to be for every node to keep its own size: the
 * accounts' rows at full size with the room their links need between them (or their columns, if
 * there are more levels and they fit side by side, when that is less), and each side column's
 * carets. A scrolling full screen draws the chart this tall (or the screen's height, if that is more).
 */
fun cashNaturalHeight(flow: CashFlowUi, width: Float, sizes: CashNodeSizes, labelW: FloatArray, labelH: FloatArray, m: CashNodeMetrics): Float {
    val p = planFrame(flow, width, sizes, labelW, labelH, m)
    val midW = max(p.midR - p.midL, 1f)
    val rows = packLines(p.levels, { p.w[it] }, midW, m.lanes)
    val need = gapNeeds(flow, rows, p.side, sizes.linkW, across = false, m)
    var depth = rows.sumOf { line -> line.maxOf { p.h[it] }.toDouble() }.toFloat() +
        need.indices.sumOf { k -> max(need[k], if (k == 0 || k == rows.size) 0f else m.lanes).toDouble() }.toFloat()
    if (p.levels.size > 1 && linesDepth(p.levels, { p.w[it] }, m.lanes) <= midW) {
        depth = min(depth, linesLength(p.levels, { p.h[it] }, m.lanes))
    }
    // A lane above and below, and a cell either end for the grid's rounding.
    val accounts = if (p.order.isEmpty()) 0f else depth + 2 * m.lanes + 2 * m.cell
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
 * The accounts stand in lines by transfer level (an account that gets more from another by transfer
 * than it sends back comes in a later line), so transfers run down or right too. The lines are rows,
 * levels top to bottom, or columns, levels left to right: whichever keeps the accounts biggest, and
 * when both do, rows if there is one level (side by side, each under its incomes and over its
 * spending), else the levels along the longer side. A level too long for one line takes more. The
 * room between the lines goes by how many links cross it; along a line, each account stands as near
 * the middle of its links' other ends as the others allow, reordered a few times to find the order
 * with the shortest links and fewest crossings. Each caret goes as close as its row (or column)
 * allows to the account it trades most with; the top and bottom rows fill first, the carets that
 * don't fit go to the columns. Then [routeCashLinks] finds each link's way around the nodes and the
 * other links. [sizes] come from [cashNodeSizes]; [labelW] and [labelH] are each caret's label size
 * (0 for accounts, whose label goes inside).
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
    val links = flow.links
    val n = nodes.size
    val w = p.w
    val h = p.h
    val hub = p.hub
    val g = m.cell
    fun snapCentre(v: Float) = (floor(v / g) + 0.5f) * g
    fun snapEdge(v: Float) = Math.round(v / g) * g
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

    val x = FloatArray(n)
    val y = FloatArray(n)
    val midL = p.midL
    val midT = snapEdge(topH + m.lanes)
    val midB = snapEdge(height - bottomH - m.lanes)
    val midW = max(p.midR - midL, 1f)
    val midH = max(midB - midT, 1f)
    // Tips on one line under the top row, tails on one line above the bottom row, and the same down
    // the columns: the labels hang off the far side.
    val tipY = snapEdge(topH)
    val tailY = snapEdge(height - bottomH)
    val tipX = snapEdge(leftW)
    val tailX = snapEdge(width - rightW)

    // How far the accounts shrink to fit: as little as the lines allow, never below the width their
    // links need ([CashNodeSizes.minW]), never wider than tall.
    val fullW = w.copyOf()
    val fullH = h.copyOf()
    val minW = sizes.minW
    fun shrunkW(i: Int, f: Float) = max(fullW[i] * f, minW[i])
    fun shrunkH(i: Int, f: Float) = max(fullH[i] * f, shrunkW(i, f) * ACCOUNT_ASPECT)
    // [across]: the lines are columns, the levels going left to right; else rows, top to bottom.
    fun mainLen(across: Boolean) = if (across) midW else midH
    fun crossLen(across: Boolean) = if (across) midH else midW
    fun crossOf(i: Int, f: Float, across: Boolean) = if (across) shrunkH(i, f) else shrunkW(i, f)
    fun mainOf(i: Int, f: Float, across: Boolean) = if (across) shrunkW(i, f) else shrunkH(i, f)
    fun lines(across: Boolean, f: Float) = packLines(p.levels, { crossOf(it, f, across) }, crossLen(across), m.lanes)
    fun fits(across: Boolean, f: Float): Boolean {
        val ls = lines(across, f)
        return linesDepth(ls, { mainOf(it, f, across) }, m.lanes) <= mainLen(across) &&
            linesLength(ls, { crossOf(it, f, across) }, m.lanes) <= crossLen(across)
    }
    fun largest(across: Boolean): Float {
        if (fits(across, 1f)) return 1f
        var lo = 0.3f
        var hi = 1f
        repeat(16) {
            val mid = (lo + hi) / 2f
            if (fits(across, mid)) lo = mid else hi = mid
        }
        return lo
    }
    val fRows = if (p.order.isEmpty()) 1f else largest(false)
    val fColumns = if (p.order.size < 2) 0f else largest(true)
    // Carets' pointing and labels, set with the carets (see placeCarets).
    val down = BooleanArray(n)
    val labelX = FloatArray(n)
    val labelTop = FloatArray(n)

    // How hard each link pulls its ends together: by its money, as thick as the thickest link for the
    // most (a link's own thickness goes by the square root, so many small links would outweigh a
    // salary and pull the accounts away from it).
    val mostMoney = links.maxOfOrNull { it.total }?.coerceAtLeast(1L) ?: 1L
    val pull = FloatArray(links.size) { m.link.endInclusive * links[it].total / mostMoney }

    // Everything placed for rows or columns ([across]); how bad it looks (see cost).
    fun arrangeFor(across: Boolean): Float {
        val f = if (across) fColumns else fRows
        p.order.forEach {
            w[it] = shrunkW(it, f)
            h[it] = shrunkH(it, f)
        }
        val firstLines = lines(across, f)
        fun mainSize(i: Int) = if (across) w[i] else h[i]
        fun crossSize(i: Int) = if (across) h[i] else w[i]

        // The gaps before, between and after the lines: each the room its links need (at least a lane
        // between two lines), then what is left shared evenly. Short of room, each gets a lane and a
        // share of what is left by how much more it needs.
        val thick = firstLines.map { line -> line.maxOf(::mainSize) }
        val base = FloatArray(firstLines.size + 1) { if (it == 0 || it == firstLines.size) 0f else m.lanes }
        val need = gapNeeds(flow, firstLines, side, sizes.linkW, across, m)
        val gapWant = FloatArray(base.size) { max(base[it], need[it]) }
        val room = mainLen(across) - thick.sum()
        val gap = if (gapWant.sum() <= room) {
            val spare = (room - gapWant.sum()) / base.size
            FloatArray(base.size) { gapWant[it] + spare }
        } else {
            val spare = (room - base.sum()).coerceAtLeast(0f)
            val more = gapWant.sum() - base.sum()
            FloatArray(base.size) { base[it] + if (more > 0f) spare * (gapWant[it] - base[it]) / more else 0f }
        }
        val mainFrom = if (across) midL else midT
        val crossFrom = if (across) midT else midL
        val crossTo = crossFrom + crossLen(across)
        // Where each line stands across the levels: its first and last edge.
        val bandFrom = FloatArray(firstLines.size)
        val bandTo = FloatArray(firstLines.size)
        run {
            var at = mainFrom + gap[0]
            firstLines.indices.forEach { k ->
                bandFrom[k] = at
                bandTo[k] = at + thick[k]
                at += thick[k] + gap[k + 1]
            }
        }
        val lineOf = IntArray(n) { -1 }
        firstLines.forEachIndexed { k, line -> line.forEach { lineOf[it] = k } }

        // Each line's accounts (in order of [want]) as near centred on its [want] as the others allow.
        fun placeAccounts(lines: List<List<Int>>, want: FloatArray) {
            var at = mainFrom + gap[0]
            lines.forEachIndexed { k, line ->
                val starts = spreadStarts(line.map { want[it] }, line.map(::crossSize), crossFrom, crossTo, m.lanes)
                line.forEachIndexed { j, i ->
                    val mainStart = at + (thick[k] - mainSize(i)) / 2f
                    val left = snapEdge(if (across) mainStart else starts[j])
                    val top = snapEdge(if (across) starts[j] else mainStart)
                    x[i] = left + w[i] / 2f
                    y[i] = top + h[i] / 2f
                }
                at += thick[k] + gap[k + 1]
            }
        }

        // Carets: each as near its account as its row or column allows, kept in order, apart, inside.
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
        fun placeCarets() {
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
        }

        // Where along the lines each node's links pull an account: a caret on an edge the lines run
        // along counts where it stands, one on an edge across their ends counts as that edge.
        fun cross(i: Int) = if (across) y[i] else x[i]
        fun endCross(i: Int) = when (side[i]) {
            TOP -> if (across) tipY else x[i]
            BOTTOM -> if (across) tailY else x[i]
            LEFT -> if (across) y[i] else tipX
            RIGHT -> if (across) y[i] else tailX
            else -> cross(i)
        }
        fun barycentres(): FloatArray {
            val sum = FloatArray(n)
            val weight = FloatArray(n)
            links.forEachIndexed { l, link ->
                val lw = pull[l]
                if (hub[link.from]) {
                    sum[link.from] += endCross(link.to) * lw
                    weight[link.from] += lw
                }
                if (hub[link.to]) {
                    sum[link.to] += endCross(link.from) * lw
                    weight[link.to] += lw
                }
            }
            return FloatArray(n) { if (weight[it] > 0f) sum[it] / weight[it] else cross(it) }
        }
        // How bad a placement looks, links taken between the nodes' middles: their length (the more
        // money, the more it counts), and much more for each crossing, and for each account in the box between
        // a link's ends (its level and upright runs go somewhere in there, so they must go round it).
        fun cost(): Float {
            val penalty = m.lanes * 4f
            var c = 0f
            links.forEachIndexed { l, link ->
                c += pull[l] * (abs(x[link.to] - x[link.from]) + abs(y[link.to] - y[link.from]))
                p.order.forEach { i ->
                    if (i != link.from && i != link.to &&
                        x[i] + w[i] / 2f > min(x[link.from], x[link.to]) && x[i] - w[i] / 2f < max(x[link.from], x[link.to]) &&
                        y[i] + h[i] / 2f > min(y[link.from], y[link.to]) && y[i] - h[i] / 2f < max(y[link.from], y[link.to])
                    ) {
                        c += sizes.linkW[l] * penalty * 2f
                    }
                }
            }
            // A link that runs past a whole line of accounts it neither leaves nor enters has to weave
            // between them.
            links.forEachIndexed { l, link ->
                val a = if (across) x[link.from] else y[link.from]
                val b = if (across) x[link.to] else y[link.to]
                bandFrom.indices.forEach { k ->
                    if (k != lineOf[link.from] && k != lineOf[link.to] && min(a, b) < bandFrom[k] && max(a, b) > bandTo[k]) {
                        c += sizes.linkW[l] * penalty
                    }
                }
            }
            for (a in links.indices) for (b in a + 1 until links.size) {
                val la = links[a]
                val lb = links[b]
                if (la.from == lb.from || la.from == lb.to || la.to == lb.from || la.to == lb.to) continue
                if (segmentsCross(x[la.from], y[la.from], x[la.to], y[la.to], x[lb.from], y[lb.from], x[lb.to], y[lb.to])) {
                    c += (sizes.linkW[a] + sizes.linkW[b]) * penalty
                }
            }
            return c
        }

        // Where each account wants to stand along its line.
        var lines = firstLines
        var want = FloatArray(n)
        // Each wants to stand halfway between the middle of its links' other ends ([pull]) and its share
        // of the line, so they don't bunch up.
        fun shareLines(pull: FloatArray?) = lines.forEach { line ->
            line.forEachIndexed { j, i ->
                val slot = crossFrom + (crossTo - crossFrom) * (j + 0.5f) / line.size
                want[i] = if (pull == null) slot else (pull[i] + slot) / 2f
            }
        }
        fun arrange(ws: FloatArray): Float {
            lines = lines.map { line -> line.sortedBy { ws[it] } }
            placeAccounts(lines, ws)
            placeCarets()
            return cost()
        }
        shareLines(null)
        var bestCost = Float.MAX_VALUE
        var best = want.copyOf()
        if (p.order.size > 1) {
            // A few rounds: place, then sort each line by where its accounts' links pull them.
            repeat(4) {
                val c = arrange(want)
                if (c < bestCost) {
                    bestCost = c
                    best = want.copyOf()
                }
                val b = barycentres()
                lines = lines.map { line -> line.sortedBy { b[it] } }
                shareLines(b)
            }
            // Then each account, the busiest first, tries a few places along its line (clear of the links
            // passing by on their way to an edge, say), and keeps the best.
            want = best
            repeat(2) {
                p.order.forEach { i ->
                    val was = want[i]
                    var at = was
                    for (k in 0..CROSS_TRIES) {
                        want[i] = crossFrom + (crossTo - crossFrom) * k / CROSS_TRIES
                        val c = arrange(want)
                        if (c < bestCost - 0.5f) {
                            bestCost = c
                            at = want[i]
                        }
                    }
                    want[i] = at
                }
            }
        }
        return arrange(want)
    }

    // Whichever keeps the accounts bigger; when both keep them alike, rows for one level (side by
    // side, each under its incomes and over its spending), else whichever looks better here.
    val across = when {
        fColumns > fRows + 0.02f -> true
        fRows > fColumns + 0.02f -> false
        p.levels.size < 2 -> false
        else -> arrangeFor(true) < arrangeFor(false)
    }
    arrangeFor(across)

    val frame = floatArrayOf(tipX, tipY, tailX, tailY)
    val layout = CashNodeLayout(x, y, w, h, down, labelX, labelTop, sizes.linkW, emptyList(), frame)
    val routes = routeCashLinks(flow, layout, labelW, labelH, width, height, m)
    return CashNodeLayout(x, y, w, h, down, labelX, labelTop, sizes.linkW, routes, frame)
}

/** How many places along its line, end to end less one, each account tries. */
private const val CROSS_TRIES = 8

/** Whether segments (ax, ay)-(bx, by) and (cx, cy)-(dx, dy) cross, each strictly through the other. */
private fun segmentsCross(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float, dx: Float, dy: Float): Boolean {
    fun turn(px: Float, py: Float, qx: Float, qy: Float, rx: Float, ry: Float) = (qx - px) * (ry - py) - (qy - py) * (rx - px)
    val d1 = turn(cx, cy, dx, dy, ax, ay)
    val d2 = turn(cx, cy, dx, dy, bx, by)
    val d3 = turn(ax, ay, bx, by, cx, cy)
    val d4 = turn(ax, ay, bx, by, dx, dy)
    return d1 * d2 < 0f && d3 * d4 < 0f
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
