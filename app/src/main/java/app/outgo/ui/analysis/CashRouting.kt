package app.outgo.ui.analysis

import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/*
 * The cash flow's link router: each link finds its own way on a grid of [CashNodeMetrics.cell]-px
 * cells, in level and upright runs only, the way a metro map's lines do. A link costs a little per
 * cell and more per bend, more again where it crosses another link, and a great deal where it would
 * run along another one (so links keep their own lanes, [CashNodeMetrics.linkGap] apart); it never
 * goes through a node, nor out past the carets' tips and tails ([CashNodeLayout.frame]), so it never
 * goes round to the far side of a caret. A link may leave or enter an account by any of its sides,
 * along the straight part only (clear of the [CashNodeMetrics.accountRadius] corners), and keeps
 * [CashNodeMetrics.accountMargin] away from every account it neither leaves nor enters.
 * Links leaving or entering the same caret may share their run: they read as one band that splits.
 * The largest links are routed first, so they get the straightest ways.
 *
 * Then [spread] slides each link's steps (a short run between two longer ones going the same way)
 * to the middle of the room it has, steps sharing a channel evenly apart, so the bends don't bunch up
 * next to the carets.
 */

private const val STEP = 10
private const val BEND = 60
private const val CROSS = 45
private const val ALONG = 4000

/** Most cells one search may look at before it gives up on a link (it then goes the plain way). */
private const val SEARCH_LIMIT = 400_000

// Directions, in the order of [DX]/[DY].
private val DX = intArrayOf(1, 0, -1, 0)
private val DY = intArrayOf(0, 1, 0, -1)
private const val RIGHT = 0
private const val DOWN = 1
private const val LEFT = 2
private const val UP = 3

private fun level(dir: Int) = dir == RIGHT || dir == LEFT

/**
 * Every link's path for [layout] (whose own [CashNodeLayout.routes] are ignored), [width] by
 * [height] px: x, y pairs from where the link leaves its source to where it enters its target.
 */
internal fun routeCashLinks(
    flow: CashFlowUi,
    layout: CashNodeLayout,
    labelW: FloatArray,
    labelH: FloatArray,
    width: Float,
    height: Float,
    m: CashNodeMetrics,
): List<FloatArray> {
    val g = m.cell
    val cols = max(1, ceil(width / g).toInt())
    val rows = max(1, ceil(height / g).toInt())
    val cells = cols * rows
    val nodes = flow.nodes
    val hub = BooleanArray(nodes.size) { nodes[it].column == CashColumn.ACCOUNT }

    // Every node's box (and a caret's label's): links keep out of them.
    val boxes = nodes.indices.map { i ->
        if (hub[i]) {
            floatArrayOf(layout.x[i] - layout.w[i] / 2f, layout.y[i] - layout.h[i] / 2f, layout.x[i] + layout.w[i] / 2f, layout.y[i] + layout.h[i] / 2f)
        } else {
            layout.body(i)
        }
    }
    val blocked = BooleanArray(cells)
    fun block(l: Float, t: Float, r: Float, b: Float) {
        val c0 = max(0, floor(l / g - 0.5f).toInt() + 1)
        val c1 = min(cols - 1, ceil(r / g - 0.5f).toInt() - 1)
        val r0 = max(0, floor(t / g - 0.5f).toInt() + 1)
        val r1 = min(rows - 1, ceil(b / g - 0.5f).toInt() - 1)
        for (cy in r0..r1) for (cx in c0..c1) blocked[cy * cols + cx] = true
    }
    nodes.indices.forEach { i ->
        val b = boxes[i]
        block(b[0], b[1], b[2], b[3])
        if (!hub[i] && labelW[i] > 0f) block(layout.labelX[i] - labelW[i] / 2f, layout.labelTop[i], layout.labelX[i] + labelW[i] / 2f, layout.labelTop[i] + labelH[i])
    }
    // A thin ring around each account that only its own links may cross: -1 none, -2 several
    // accounts', else the account's node index.
    val halo = IntArray(cells) { -1 }
    nodes.indices.filter { hub[it] }.forEach { i ->
        val b = boxes[i]
        val mg = m.accountMargin
        val c0 = max(0, floor((b[0] - mg) / g - 0.5f).toInt() + 1)
        val c1 = min(cols - 1, ceil((b[2] + mg) / g - 0.5f).toInt() - 1)
        val r0 = max(0, floor((b[1] - mg) / g - 0.5f).toInt() + 1)
        val r1 = min(rows - 1, ceil((b[3] + mg) / g - 0.5f).toInt() - 1)
        for (cy in r0..r1) for (cx in c0..c1) {
            val cell = cy * cols + cx
            if (!blocked[cell]) halo[cell] = if (halo[cell] == -1 || halo[cell] == i) i else -2
        }
    }
    // The same rings, as boxes, for [spread].
    val keepOut = nodes.indices.map { i ->
        val b = boxes[i]
        if (hub[i]) floatArrayOf(b[0] - m.accountMargin, b[1] - m.accountMargin, b[2] + m.accountMargin, b[3] + m.accountMargin) else b
    }
    // Nothing runs out past the carets: the frame's outside is blocked too.
    val frame = layout.frame
    for (r in 0 until rows) for (c in 0 until cols) {
        val x = (c + 0.5f) * g
        val y = (r + 0.5f) * g
        if (x < frame[0] || x > frame[2] || y < frame[1] || y > frame[3]) blocked[r * cols + c] = true
    }

    // Which link runs where, level (H) and upright (V): -1 none, -2 several, else its owner.
    val ownH = IntArray(cells) { -1 }
    val ownV = IntArray(cells) { -1 }
    fun mark(own: IntArray, cell: Int, owner: Int) {
        own[cell] = when (own[cell]) {
            -1, owner -> owner
            else -> -2
        }
    }

    fun col(x: Float) = floor(x / g).toInt().coerceIn(0, cols - 1)
    fun row(y: Float) = floor(y / g).toInt().coerceIn(0, rows - 1)
    fun cx(c: Int) = (c + 0.5f) * g
    fun cy(r: Int) = (r + 0.5f) * g

    // Links of one caret share their way; any other link is someone else's.
    val owners = IntArray(flow.links.size) { l ->
        val link = flow.links[l]
        when {
            !hub[link.from] -> link.from
            !hub[link.to] -> link.to
            else -> nodes.size + l
        }
    }

    val gCost = IntArray(cells * 4)
    val parent = IntArray(cells * 4)
    val goalDir = IntArray(cells) { -1 }
    val routes = arrayOfNulls<FloatArray>(flow.links.size)

    flow.links.indices.sortedByDescending { flow.links[it].total }.forEach { l ->
        val link = flow.links[l]
        val w = layout.linkW[l]
        val owner = owners[l]
        val keep = floor((w / 2f + m.linkGap) / g).toInt()
        val wide = floor(w / 2f / g + 0.5f).toInt()

        // What entering [cell] moving [d] costs, beyond the step: other links alongside or crossed.
        // -1 when the link would touch a node or leave the frame there.
        fun enter(cell: Int, d: Int): Int {
            val c = cell % cols
            val r = cell / cols
            val lv = level(d)
            var cost = 0
            for (k in -keep..keep) {
                val bc = if (lv) c else c + k
                val br = if (lv) r + k else r
                if (bc !in 0 until cols || br !in 0 until rows) return -1
                val b = br * cols + bc
                if (abs(k) <= wide && blocked[b]) return -1
                val near = halo[b]
                if (abs(k) <= wide && near != -1 && near != link.from && near != link.to) return -1
                val along = if (lv) ownH[b] else ownV[b]
                if (along != -1 && along != owner) cost += ALONG
            }
            val across = if (lv) ownV[cell] else ownH[cell]
            if (across != -1 && across != owner) cost += CROSS
            return cost
        }

        // A port: the cell next to the node, the way the link moves there, and the point on the
        // node's edge (or the caret's tip or notch) it is drawn from or to.
        // [run]: on a lengthened port, the cells between the node and [cell], in path order; [runCost]
        // what entering them costs.
        data class Port(val cell: Int, val dir: Int, val x: Float, val y: Float, val bias: Int, val run: IntArray = IntArray(0), val runCost: Int = 0)
        // [ox], [oy]: the link's other end. Only the sides that face it, so the link never leaves (or
        // enters) the account going away from that end and has to turn back past it; every side when
        // none does (the other end overlaps the account). Of those, money comes in at the top and left
        // and goes out at the bottom and right, where possible: the account is sized for that
        // (cashNodeSizes), so the ways in don't take the lanes meant for the ways out.
        fun accountPorts(i: Int, out: Boolean, ox: Float, oy: Float): List<Port> {
            val (left, top, right, bottom) = boxes[i].toList()
            var facing = booleanArrayOf(ox > right, oy > bottom, ox < left, oy < top)
            if (facing.none { it }) facing = BooleanArray(4) { true }
            val own = if (out) booleanArrayOf(true, true, false, false) else booleanArrayOf(false, false, true, true)
            val preferred = BooleanArray(4) { facing[it] && own[it] }
            if (preferred.any { it }) facing = preferred
            val ports = ArrayList<Port>()
            // Only the straight part of a side: the rounded corners are no place to start or end.
            val corner = m.accountRadius
            val inset = w / 2f + g / 2f
            // Every lane along each side the link fits on; the middle ones cheapest, so a lone link
            // sits centred.
            for (c in col(left + inset)..col(right - inset)) {
                if (cx(c) - w / 2f < left + corner || cx(c) + w / 2f > right - corner) continue
                val bias = abs(cx(c) - layout.x[i]).toInt() / g.toInt()
                if (facing[UP]) ports += Port(row(top - g / 2f) * cols + c, if (out) UP else DOWN, cx(c), top, bias)
                if (facing[DOWN]) ports += Port(row(bottom + g / 2f) * cols + c, if (out) DOWN else UP, cx(c), bottom, bias)
            }
            for (r in row(top + inset)..row(bottom - inset)) {
                if (cy(r) - w / 2f < top + corner || cy(r) + w / 2f > bottom - corner) continue
                val bias = abs(cy(r) - layout.y[i]).toInt() / g.toInt()
                if (facing[LEFT]) ports += Port(r * cols + col(left - g / 2f), if (out) LEFT else RIGHT, left, cy(r), bias)
                if (facing[RIGHT]) ports += Port(r * cols + col(right + g / 2f), if (out) RIGHT else LEFT, right, cy(r), bias)
            }
            // The facing sides too short for this link: any side, as before.
            if (ports.isEmpty() && !facing.all { it }) return accountPorts(i, out, Float.NaN, Float.NaN)
            return ports
        }
        fun caretPort(i: Int, out: Boolean): Port {
            val (px, py) = layout.linkEnd(i, income = out, width = w)
            val b = layout.body(i)
            val dir = if (layout.down[i]) DOWN else RIGHT
            val cell = if (layout.down[i]) {
                row(if (out) b[3] + g / 2f else b[1] - g / 2f) * cols + col(px)
            } else {
                row(py) * cols + col(if (out) b[2] + g / 2f else b[0] - g / 2f)
            }
            return Port(cell, dir, px, py, 0)
        }
        // An account's port moved [lead] cells out, so the link runs straight that far from or into the
        // account: room for its bend, and a transfer's arrowhead. Null where that run is blocked.
        fun lengthen(p: Port, lead: Int, out: Boolean): Port? {
            if (lead <= 1) return p
            // Out: away from the node, the way the link moves. In: back against it.
            val sx = if (out) DX[p.dir] else -DX[p.dir]
            val sy = if (out) DY[p.dir] else -DY[p.dir]
            val cells = IntArray(lead) { k ->
                val c = p.cell % cols + sx * k
                val r = p.cell / cols + sy * k
                if (c !in 0 until cols || r !in 0 until rows) return null
                r * cols + c
            }
            // Path order: out, from the node; in, towards it.
            val run = if (out) cells.copyOfRange(0, lead - 1) else cells.copyOfRange(1, lead).reversedArray()
            var cost = 0
            for (cell in run) {
                val e = enter(cell, p.dir)
                if (e < 0) return null
                cost += e
            }
            return p.copy(cell = cells[lead - 1], run = run, runCost = cost)
        }
        fun lead(room: Float) = ceil(room / g + 0.5f).toInt()
        val bendRoom = m.linkCorner + w / 2f
        val leadOut = lead(bendRoom)
        val leadIn = lead(if (flow.isTransfer(link)) bendRoom + max(w * ARROW_LENGTH, m.arrow) else bendRoom)
        val fromCaret = if (hub[link.from]) null else caretPort(link.from, out = true)
        val toCaret = if (hub[link.to]) null else caretPort(link.to, out = false)
        val plainStarts = fromCaret?.let { listOf(it) }
            ?: accountPorts(link.from, out = true, toCaret?.x ?: layout.x[link.to], toCaret?.y ?: layout.y[link.to])
        val plainGoals = toCaret?.let { listOf(it) }
            ?: accountPorts(link.to, out = false, fromCaret?.x ?: layout.x[link.from], fromCaret?.y ?: layout.y[link.from])
        if (plainStarts.isEmpty() || plainGoals.isEmpty()) {
            // An account too small for this link on every side: straight between the middles.
            routes[l] = floatArrayOf(layout.x[link.from], layout.y[link.from], layout.x[link.to], layout.y[link.to])
            return@forEach
        }

        // A*, over (cell, direction it was entered in). The goal state found, or -1.
        fun search(starts: List<Port>, goals: List<Port>): Int {
            if (starts.isEmpty() || goals.isEmpty()) return -1
            gCost.fill(Int.MAX_VALUE)
            goals.forEach { goalDir[it.cell] = it.dir }
            val gx0 = goals.minOf { it.cell % cols }
            val gx1 = goals.maxOf { it.cell % cols }
            val gy0 = goals.minOf { it.cell / cols }
            val gy1 = goals.maxOf { it.cell / cols }
            fun guess(cell: Int): Int {
                val c = cell % cols
                val r = cell / cols
                val dx = if (c < gx0) gx0 - c else if (c > gx1) c - gx1 else 0
                val dy = if (r < gy0) gy0 - r else if (r > gy1) r - gy1 else 0
                return (dx + dy) * STEP
            }
            val open = PriorityQueue<Long>()
            fun push(state: Int, cost: Int) {
                gCost[state] = cost
                open += ((cost + guess(state / 4)).toLong() shl 32) or state.toLong()
            }
            starts.forEach { p ->
                val extra = enter(p.cell, p.dir)
                if (extra < 0) return@forEach
                val state = p.cell * 4 + p.dir
                val cost = extra + p.runCost + p.bias * 3
                if (cost < gCost[state]) {
                    parent[state] = -1
                    push(state, cost)
                }
            }
            var found = -1
            var looked = 0
            while (open.isNotEmpty() && looked < SEARCH_LIMIT) {
                val top = open.poll()
                val state = (top and 0xffffffffL).toInt()
                val cost = gCost[state]
                if ((top ushr 32).toInt() - guess(state / 4) > cost) continue
                looked++
                val cell = state / 4
                val dir = state % 4
                if (goalDir[cell] == dir) {
                    found = state
                    break
                }
                val c = cell % cols
                val r = cell / cols
                for (nd in 0 until 4) {
                    if (nd == (dir + 2) % 4) continue
                    val nc = c + DX[nd]
                    val nr = r + DY[nd]
                    if (nc !in 0 until cols || nr !in 0 until rows) continue
                    val next = nr * cols + nc
                    val extra = enter(next, nd)
                    if (extra < 0) continue
                    val nCost = cost + STEP + extra + if (nd != dir) BEND else 0
                    val ns = next * 4 + nd
                    if (nCost < gCost[ns]) {
                        parent[ns] = state
                        push(ns, nCost)
                    }
                }
            }
            goals.forEach { goalDir[it.cell] = -1 }
            return found
        }

        // First with a straight run at each account end; where no way has room for that, without.
        var starts = plainStarts.map { if (hub[link.from]) lengthen(it, leadOut, out = true) else it }.filterNotNull()
        var goals = plainGoals.map { if (hub[link.to]) lengthen(it, leadIn, out = false) else it }.filterNotNull()
        var found = search(starts, goals)
        if (found < 0) {
            starts = plainStarts
            goals = plainGoals
            found = search(starts, goals)
        }

        if (found < 0) {
            // No way through: the plain way, out, across and in.
            val s = starts.minBy { it.bias }
            val e = goals.minBy { it.bias }
            routes[l] = if (level(s.dir)) floatArrayOf(s.x, s.y, e.x, s.y, e.x, e.y) else floatArrayOf(s.x, s.y, s.x, e.y, e.x, e.y)
            return@forEach
        }

        // Back from the goal: the cells, then only the corners between the two ends.
        val path = ArrayList<Int>()
        var st = found
        while (st >= 0) {
            path += st
            st = parent[st]
        }
        path.reverse()
        val first = path.first()
        val start = starts.first { it.cell == first / 4 && it.dir == first % 4 }
        val goal = goals.first { it.cell == found / 4 && it.dir == found % 4 }
        // The straight runs at the ends belong to the path too (no corners in them).
        path.addAll(0, start.run.map { it * 4 + start.dir })
        path.addAll(goal.run.map { it * 4 + goal.dir })
        val points = ArrayList<Float>()
        points += start.x
        points += start.y
        for (k in 1 until path.size) {
            if (path[k - 1] % 4 != path[k] % 4) {
                val corner = path[k - 1] / 4
                points += cx(corner % cols)
                points += cy(corner / cols)
            }
        }
        points += goal.x
        points += goal.y
        routes[l] = points.toFloatArray()

        // Its lane is taken now.
        for (k in path.indices) {
            val cell = path[k] / 4
            val d = path[k] % 4
            val dirs = if (k + 1 < path.size && path[k + 1] % 4 != d) listOf(d, path[k + 1] % 4) else listOf(d)
            dirs.forEach { dd ->
                val lv = level(dd)
                val c = cell % cols
                val r = cell / cols
                for (j in -wide..wide) {
                    val bc = if (lv) c else c + j
                    val br = if (lv) r + j else r
                    if (bc in 0 until cols && br in 0 until rows) mark(if (lv) ownH else ownV, br * cols + bc, owner)
                }
            }
        }
    }
    val out = routes.map { it ?: FloatArray(0) }.toMutableList()
    spread(flow, out, owners, keepOut, layout.linkW, frame, m)
    return out
}

/**
 * Slides each link's steps to spread them out. A step is a run between two runs going the same way
 * (down, across, down): it may move anywhere between their far ends, and so it goes to the middle of
 * the room it has there; steps that share a channel (overlapping, and with overlapping room) are set
 * evenly across it, in the order they already had. A step stays clear of the nodes ([boxes]) and of
 * other links' runs, lane-wide, never comes nearer an end than a bend's radius (an arrowhead's more
 * for a transfer), and stays inside the [frame].
 */
private fun spread(
    flow: CashFlowUi,
    routes: MutableList<FloatArray>,
    owners: IntArray,
    boxes: List<FloatArray>,
    linkW: FloatArray,
    frame: FloatArray,
    m: CashNodeMetrics,
) {
    val g = m.cell
    // Room a bend needs from a link's end, and a transfer's arrowhead too: as the router left it.
    fun bendRoom(l: Int) = max(g * 3, m.linkCorner + linkW[l] / 2f)
    fun arrowRoom(l: Int) = max(g * 5, max(linkW[l] * ARROW_LENGTH, m.arrow))

    class Step(val l: Int, val k: Int, val lv: Boolean, val lo: Float, val hi: Float, val a: Float, val b: Float, var target: Float = 0f)

    // Whether link [l]'s run from (x0, y0) to (x1, y1) keeps clear of the nodes and of the other
    // links' runs going the same way, and (when [inside]) stays in the frame. A step's in and out
    // runs only grow or shrink between their fixed ends (one may start in a caret's head, past the
    // frame's line), so only the step itself is held to the frame.
    fun clear(l: Int, x0: Float, y0: Float, x1: Float, y1: Float, inside: Boolean = false): Boolean {
        val w = linkW[l]
        val link = flow.links[l]
        val l0 = min(x0, x1) - w / 2f
        val r0 = max(x0, x1) + w / 2f
        val t0 = min(y0, y1) - w / 2f
        val b0 = max(y0, y1) + w / 2f
        if (inside && (l0 < frame[0] - 0.5f || r0 > frame[2] + 0.5f || t0 < frame[1] - 0.5f || b0 > frame[3] + 0.5f)) return false
        for (i in boxes.indices) {
            if (i == link.from || i == link.to) continue
            val b = boxes[i]
            if (r0 > b[0] && l0 < b[2] && b0 > b[1] && t0 < b[3]) return false
        }
        val lv = y0 == y1
        for (o in routes.indices) {
            if (o == l || owners[o] == owners[l]) continue
            val r = routes[o]
            val room = (w + linkW[o]) / 2f + m.linkGap
            for (k in 2 until r.size step 2) {
                val ox0 = r[k - 2]
                val oy0 = r[k - 1]
                val ox1 = r[k]
                val oy1 = r[k + 1]
                if ((oy0 == oy1) != lv) continue
                if (lv) {
                    if (abs(oy0 - y0) >= room) continue
                    if (min(max(x0, x1), max(ox0, ox1)) - max(min(x0, x1), min(ox0, ox1)) > 0f) return false
                } else {
                    if (abs(ox0 - x0) >= room) continue
                    if (min(max(y0, y1), max(oy0, oy1)) - max(min(y0, y1), min(oy0, oy1)) > 0f) return false
                }
            }
        }
        return true
    }

    // Whether step [s] may sit at [at]: its three runs (in, across, out) all clear.
    fun fits(s: Step, at: Float): Boolean {
        val r = routes[s.l]
        val k = s.k
        val px = r[2 * k - 2]
        val py = r[2 * k - 1]
        val nx = r[2 * k + 4]
        val ny = r[2 * k + 5]
        return if (s.lv) {
            clear(s.l, px, py, px, at) && clear(s.l, s.a, at, s.b, at, inside = true) && clear(s.l, nx, at, nx, ny)
        } else {
            clear(s.l, px, py, at, py) && clear(s.l, at, s.a, at, s.b, inside = true) && clear(s.l, at, ny, nx, ny)
        }
    }

    repeat(2) {
        for (lv in listOf(true, false)) {
            // The steps: run k (points k, k+1) between runs going the same way.
            val steps = ArrayList<Step>()
            routes.forEachIndexed { l, r ->
                val count = r.size / 2
                for (k in 1 until count - 2) {
                    val x0 = r[2 * k]
                    val y0 = r[2 * k + 1]
                    val x1 = r[2 * k + 2]
                    val y1 = r[2 * k + 3]
                    if ((y0 == y1) != lv || (x0 == x1 && y0 == y1)) continue
                    val before = if (lv) r[2 * k - 1] else r[2 * k - 2]
                    val after = if (lv) r[2 * k + 5] else r[2 * k + 4]
                    val here = if (lv) y0 else x0
                    if ((here - before) * (after - here) <= 0f) continue
                    val bendRoom = bendRoom(l)
                    val endRoom = if (k + 2 == count - 1 && flow.isTransfer(flow.links[l])) bendRoom + arrowRoom(l) else bendRoom
                    val lo = min(before, after) + if (before < after) bendRoom else endRoom
                    val hi = max(before, after) - if (before < after) endRoom else bendRoom
                    if (hi <= lo) continue
                    steps += Step(l, k, lv, lo, hi, if (lv) min(x0, x1) else min(y0, y1), if (lv) max(x0, x1) else max(y0, y1))
                }
            }
            fun at(s: Step) = routes[s.l][if (s.lv) 2 * s.k + 1 else 2 * s.k]
            // Channels: steps that overlap along their run and in their room.
            val group = IntArray(steps.size) { it }
            fun find(i: Int): Int {
                var x = i
                while (group[x] != x) x = group[x]
                return x
            }
            for (i in steps.indices) for (j in i + 1 until steps.size) {
                val p = steps[i]
                val q = steps[j]
                if (min(p.b, q.b) > max(p.a, q.a) && min(p.hi, q.hi) > max(p.lo, q.lo)) group[find(i)] = find(j)
            }
            steps.indices.groupBy(::find).values.forEach { ids ->
                val sorted = ids.sortedBy { at(steps[it]) }
                sorted.forEachIndexed { n, i ->
                    val s = steps[i]
                    s.target = s.lo + (s.hi - s.lo) * (n + 1) / (sorted.size + 1)
                }
            }
            // Each to the free spot nearest its target, on the grid, the longest first.
            steps.sortedByDescending { it.b - it.a }.forEach { s ->
                val now = at(s)
                var best = now
                var bestOff = abs(now - s.target)
                var c = floor(s.lo / g) * g + g / 2f
                while (c <= s.hi) {
                    val off = abs(c - s.target)
                    if (c >= s.lo && off < bestOff && fits(s, c)) {
                        best = c
                        bestOff = off
                    }
                    c += g
                }
                val r = routes[s.l]
                if (s.lv) {
                    r[2 * s.k + 1] = best
                    r[2 * s.k + 3] = best
                } else {
                    r[2 * s.k] = best
                    r[2 * s.k + 2] = best
                }
            }
        }
    }
}
