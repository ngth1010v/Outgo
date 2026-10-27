package app.outgo.ui.analysis

import app.outgo.data.db.dao.CashFlowRow
import app.outgo.domain.TradeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val BANK = 1L
private const val CASH = 2L
private const val SAVINGS = 3L

private val ACCOUNTS = mapOf(
    BANK to Triple("Bank", null, 1),
    CASH to Triple("Cash", null, 2),
    SAVINGS to Triple("Savings", null, 3),
)

private fun income(account: Long, category: Long, total: Long) =
    CashFlowRow(TradeType.INCOME, account, null, category, "in$category", null, 10, total)

private fun expense(account: Long, category: Long, total: Long) =
    CashFlowRow(TradeType.EXPENSE, account, null, category, "out$category", null, 20, total)

private fun transfer(from: Long, to: Long?, total: Long) =
    CashFlowRow(TradeType.TRANSFER, from, to, null, null, null, null, total)

private fun adjust(type: Int, account: Long, total: Long) =
    CashFlowRow(type, account, null, null, null, null, null, total)

private fun CashFlowUi.node(kind: CashKind, ref: Long = 0L) = nodes.indexOfFirst { it.kind == kind && it.refId == ref }

private fun CashFlowUi.link(from: Int, to: Int) = links.firstOrNull { it.from == from && it.to == to }?.total

class CashFlowTest {

    @Test
    fun `nothing moved gives no chart`() {
        assertNull(buildCashFlow(emptyList(), ACCOUNTS))
    }

    @Test
    fun `income runs through the account into expenses and the rest is only summed up`() {
        val flow = buildCashFlow(listOf(income(BANK, 100, 1_000), expense(BANK, 200, 600)), ACCOUNTS)!!
        val bank = flow.node(CashKind.ACCOUNT, BANK)
        assertEquals(1_000L, flow.link(flow.node(CashKind.INCOME, 100), bank))
        assertEquals(600L, flow.link(bank, flow.node(CashKind.EXPENSE, 200)))
        assertEquals(2, flow.links.size)
        assertEquals(400L, flow.kept)
        assertEquals(0L, flow.fromBalance)
        assertEquals(1_000L, flow.nodes[bank].total)
        // Named from the rows and the account map.
        assertEquals("in100", flow.nodes[flow.node(CashKind.INCOME, 100)].name)
        assertEquals("Bank", flow.nodes[bank].name)
    }

    @Test
    fun `spending more than came in is summed up as drawn from the balance`() {
        val flow = buildCashFlow(listOf(income(BANK, 100, 300), expense(BANK, 200, 500)), ACCOUNTS)!!
        assertEquals(200L, flow.fromBalance)
        assertEquals(0L, flow.kept)
        assertEquals(2, flow.links.size)
    }

    @Test
    fun `transfers link two accounts`() {
        val flow = buildCashFlow(
            listOf(income(BANK, 100, 1_000), transfer(BANK, CASH, 300), expense(CASH, 200, 300)),
            ACCOUNTS,
        )!!
        val bank = flow.node(CashKind.ACCOUNT, BANK)
        val cash = flow.node(CashKind.ACCOUNT, CASH)
        assertEquals(300L, flow.link(bank, cash))
        assertTrue(flow.isTransfer(flow.links.first { it.from == bank && it.to == cash }))
        assertEquals(700L, flow.kept)
    }

    @Test
    fun `switches leave out transfers and adjustments`() {
        val rows = listOf(
            income(BANK, 100, 1_000),
            transfer(BANK, CASH, 300),
            adjust(TradeType.ADJUST_IN, CASH, 50),
            adjust(TradeType.ADJUST_OUT, BANK, 20),
        )
        val all = buildCashFlow(rows, ACCOUNTS)!!
        assertTrue(all.node(CashKind.ADJUST_IN) >= 0)
        assertTrue(all.node(CashKind.ADJUST_OUT) >= 0)

        val bare = buildCashFlow(rows, ACCOUNTS, cashFlowOptions(listOf(CASH_HIDE_TRANSFERS, CASH_HIDE_ADJUSTMENTS), 5))!!
        assertEquals(-1, bare.node(CashKind.ADJUST_IN))
        assertTrue(bare.links.none { bare.isTransfer(it) })
        // Only Bank had anything left to draw.
        assertEquals(listOf(BANK), bare.column(CashColumn.ACCOUNT).map { bare.nodes[it].refId })
    }

    @Test
    fun `a hidden account's transfers come from and go to Other accounts`() {
        val flow = buildCashFlow(
            listOf(income(BANK, 100, 1_000), transfer(BANK, SAVINGS, 400), transfer(SAVINGS, CASH, 100)),
            ACCOUNTS,
            cashFlowOptions(listOf(SAVINGS), 5),
        )!!
        assertEquals(-1, flow.node(CashKind.ACCOUNT, SAVINGS))
        assertEquals(400L, flow.link(flow.node(CashKind.ACCOUNT, BANK), flow.node(CashKind.OTHER_OUT)))
        assertEquals(100L, flow.link(flow.node(CashKind.OTHER_IN), flow.node(CashKind.ACCOUNT, CASH)))
    }

    @Test
    fun `a transfer inside one parent account is left out`() {
        val flow = buildCashFlow(listOf(income(BANK, 100, 500), transfer(BANK, BANK, 200)), ACCOUNTS)!!
        assertTrue(flow.links.none { flow.isTransfer(it) })
        assertEquals(500L, flow.nodes[flow.node(CashKind.ACCOUNT, BANK)].total)
    }

    @Test
    fun `small categories join Other, which goes last`() {
        val rows = listOf(income(BANK, 100, 10_000)) + (1..8).map { expense(BANK, 200L + it, it * 100L) }
        val flow = buildCashFlow(rows, ACCOUNTS, cashFlowOptions(emptyList(), 5))!!
        val uses = flow.column(CashColumn.USE).map { flow.nodes[it] }
        assertEquals(listOf(208L, 207L, 206L, 205L, OTHER_NODE), uses.map { it.refId })
        assertEquals((1..4).sumOf { it * 100L }, uses.last().total)
    }

    @Test
    fun `accounts beyond the limit join one Other account`() {
        val accounts = (1L..6L).associateWith { Triple("a$it", null, 0) }
        val rows = (1L..6L).map { income(it, 100, it * 1_000) }
        val flow = buildCashFlow(rows, accounts, cashFlowOptions(emptyList(), 3))!!
        val drawn = flow.column(CashColumn.ACCOUNT).map { flow.nodes[it].refId }
        assertEquals(listOf(6L, 5L, OTHER_NODE), drawn)
        assertEquals((1L..4L).sumOf { it * 1_000 }, flow.nodes[flow.node(CashKind.ACCOUNT, OTHER_NODE)].total)
    }

    @Test
    fun `kept and from balance count each real account, even inside Other`() {
        val accounts = (1L..4L).associateWith { Triple("a$it", null, 0) }
        // Account 3 gains 200, account 4 loses 300; with three accounts drawn they share "Other".
        val rows = listOf(
            income(1, 100, 10_000), income(2, 100, 5_000),
            income(3, 100, 1_000), expense(3, 200, 800),
            income(4, 100, 500), expense(4, 200, 800),
        )
        val flow = buildCashFlow(rows, accounts, cashFlowOptions(emptyList(), 5, 3))!!
        assertEquals(300L, flow.fromBalance)
        assertEquals(10_000L + 5_000L + 200L, flow.kept)
        // The joined node shows the sum of its accounts' changes.
        assertEquals(-100L, flow.nodes[flow.node(CashKind.ACCOUNT, OTHER_NODE)].net)
        assertEquals(10_000L, flow.nodes[flow.node(CashKind.ACCOUNT, 1)].net)
    }

    // ------------------------------------------------------------------- nodes

    private val metrics = CashNodeMetrics(
        accountW = 150f..230f, accountH = 220f..320f, caret = 70f..110f, link = 4f..24f,
        labelGap = 6f, gap = 16f, lanes = 50f, cell = 8f, linkGap = 6f,
    )

    private fun busyFlow() = buildCashFlow(
        listOf(
            income(BANK, 100, 9_000_000), income(CASH, 101, 40_000), income(BANK, 102, 500_000),
            transfer(BANK, CASH, 300_000), transfer(BANK, SAVINGS, 2_000_000), transfer(SAVINGS, CASH, 50_000),
            transfer(CASH, BANK, 20_000),
            expense(CASH, 200, 320_000), expense(BANK, 201, 6_000_000), expense(BANK, 202, 15_000),
            expense(SAVINGS, 203, 900_000), expense(CASH, 201, 80_000),
            adjust(TradeType.ADJUST_IN, CASH, 70_000),
        ),
        ACCOUNTS,
    )!!

    private fun layout(flow: CashFlowUi, width: Float = 1100f, height: Float = 2000f): CashNodeLayout {
        val n = flow.nodes.size
        val caret = { i: Int -> flow.nodes[i].column != CashColumn.ACCOUNT }
        return layoutCashNodes(
            flow, width, height, cashNodeSizes(flow, metrics),
            FloatArray(n) { if (caret(it)) 170f else 0f }, FloatArray(n) { if (caret(it)) 80f else 0f }, metrics,
        )
    }

    /** Left, top, right, bottom of node [i]'s body. */
    private fun CashNodeLayout.box(flow: CashFlowUi, i: Int) =
        if (flow.nodes[i].column == CashColumn.ACCOUNT) floatArrayOf(x[i] - w[i] / 2, y[i] - h[i] / 2, x[i] + w[i] / 2, y[i] + h[i] / 2) else body(i)

    @Test
    fun `incomes stand above or left of the accounts, spending below or right`() {
        val flow = busyFlow()
        for ((width, height) in listOf(1100f to 2000f, 1100f to 900f)) {
            val g = layout(flow, width, height)
            val accounts = flow.column(CashColumn.ACCOUNT).map { g.box(flow, it) }
            flow.nodes.indices.filter { flow.nodes[it].column != CashColumn.ACCOUNT }.forEach { i ->
                val b = g.box(flow, i)
                if (flow.nodes[i].column == CashColumn.SOURCE) {
                    assertTrue("income $i", accounts.all { a -> b[3] <= a[1] } || accounts.all { a -> b[2] <= a[0] })
                } else {
                    assertTrue("spending $i", accounts.all { a -> b[1] >= a[3] } || accounts.all { a -> b[0] >= a[2] })
                }
                assertTrue("node $i inside", b[0] >= -0.5f && b[1] >= -0.5f && b[2] <= width + 0.5f && b[3] <= height + 0.5f)
            }
            flow.column(CashColumn.ACCOUNT).forEach { assertTrue(g.h[it] > g.w[it]) }
        }
    }

    @Test
    fun `nodes never overlap`() {
        val flow = busyFlow()
        val g = layout(flow)
        val n = flow.nodes.size
        for (a in 0 until n) for (b in a + 1 until n) {
            val p = g.box(flow, a)
            val q = g.box(flow, b)
            assertTrue("nodes $a and $b overlap", p[2] <= q[0] || q[2] <= p[0] || p[3] <= q[1] || q[3] <= p[1])
        }
    }

    @Test
    fun `sizes are relative, so a small one still shows`() {
        val flow = busyFlow()
        val s = cashNodeSizes(flow, metrics)
        val bank = flow.node(CashKind.ACCOUNT, BANK)
        flow.column(CashColumn.ACCOUNT).forEach { assertTrue(s.w[it] <= s.w[bank] + 0.01f && s.w[it] >= metrics.accountW.start - 0.01f) }
        val salary = flow.node(CashKind.INCOME, 100)
        val tiny = flow.node(CashKind.EXPENSE, 202)
        assertTrue(s.w[salary] > s.w[tiny] && s.w[tiny] >= metrics.caret.start - 0.01f)
        val small = flow.links.indexOfFirst { it.total == 15_000L }
        val large = flow.links.indexOfFirst { it.total == 9_000_000L }
        assertEquals(metrics.link.endInclusive, s.linkW[large], 0.01f)
        assertTrue(s.linkW[small] > metrics.link.start && s.linkW[small] < s.linkW[large])
    }

    @Test
    fun `links run level or upright, from an edge of their source to an edge of their target`() {
        val flow = busyFlow()
        val g = layout(flow)
        val eps = 0.6f
        flow.links.forEachIndexed { l, link ->
            val r = g.routes[l]
            assertTrue(r.size >= 4)
            for (k in 2 until r.size step 2) {
                assertTrue("link $l runs aslant", abs(r[k] - r[k - 2]) < eps || abs(r[k + 1] - r[k - 1]) < eps)
            }
            val from = g.box(flow, link.from)
            val to = g.box(flow, link.to)
            if (flow.nodes[link.from].column == CashColumn.ACCOUNT) {
                assertTrue("link $l start", onEdge(r[0], r[1], from, eps))
            } else {
                assertEquals(g.linkEnd(link.from, true, g.linkW[l]).first, r[0], eps)
                assertEquals(g.linkEnd(link.from, true, g.linkW[l]).second, r[1], eps)
            }
            val ex = r[r.size - 2]
            val ey = r[r.size - 1]
            if (flow.nodes[link.to].column == CashColumn.ACCOUNT) {
                assertTrue("link $l end", onEdge(ex, ey, to, eps))
            } else {
                assertEquals(g.notch(link.to).first, ex, eps)
                assertEquals(g.notch(link.to).second, ey, eps)
            }
        }
    }

    /** Whether (x, y) lies on one of box [b]'s four edges. */
    private fun onEdge(x: Float, y: Float, b: FloatArray, eps: Float): Boolean {
        val acrossX = x >= b[0] - eps && x <= b[2] + eps
        val acrossY = y >= b[1] - eps && y <= b[3] + eps
        return (acrossX && (abs(y - b[1]) < eps || abs(y - b[3]) < eps)) || (acrossY && (abs(x - b[0]) < eps || abs(x - b[2]) < eps))
    }

    @Test
    fun `no link goes round to the far side of a caret`() {
        val flow = busyFlow()
        for ((width, height) in listOf(1100f to 2000f, 1100f to 900f)) {
            val g = layout(flow, width, height)
            val f = g.frame
            flow.links.indices.forEach { l ->
                val r = g.routes[l]
                // Every corner inside the frame; only the ends may reach into a caret past its line.
                for (k in 2 until r.size - 2 step 2) {
                    assertTrue("link $l leaves the frame", r[k] >= f[0] - 0.5f && r[k] <= f[2] + 0.5f && r[k + 1] >= f[1] - 0.5f && r[k + 1] <= f[3] + 0.5f)
                }
            }
        }
    }

    @Test
    fun `steps spread out instead of hugging the carets`() {
        val flow = busyFlow()
        val g = layout(flow)
        // A link into a bottom-row caret that has to step sideways does so well clear of the row.
        val f = g.frame
        flow.links.forEachIndexed { l, link ->
            val to = link.to
            if (flow.nodes[to].column != CashColumn.USE || !g.down[to]) return@forEachIndexed
            val r = g.routes[l]
            if (r.size < 8) return@forEachIndexed
            // The last level run before the final drop.
            val y = r[r.size - 3]
            assertTrue("link $l steps right above the caret", f[3] - y > metrics.cell * 4)
        }
    }

    @Test
    fun `links go around the nodes and never run along each other`() {
        val flow = busyFlow()
        val g = layout(flow)
        data class Seg(val l: Int, val x0: Float, val y0: Float, val x1: Float, val y1: Float)
        val segs = flow.links.indices.flatMap { l ->
            val r = g.routes[l]
            (2 until r.size step 2).map { k -> Seg(l, min(r[k - 2], r[k]), min(r[k - 1], r[k + 1]), max(r[k - 2], r[k]), max(r[k - 1], r[k + 1])) }
        }
        segs.forEach { s ->
            val link = flow.links[s.l]
            flow.nodes.indices.filter { it != link.from && it != link.to }.forEach { i ->
                val b = g.box(flow, i)
                assertTrue("link ${s.l} runs through node $i", !(s.x1 > b[0] && s.x0 < b[2] && s.y1 > b[1] && s.y0 < b[3]))
            }
        }
        // Links of different owners never share a lane: parallel runs side by side stay their half
        // widths apart.
        fun owner(l: Int): Int {
            val link = flow.links[l]
            return when {
                flow.nodes[link.from].column != CashColumn.ACCOUNT -> link.from
                flow.nodes[link.to].column != CashColumn.ACCOUNT -> link.to
                else -> -1 - l
            }
        }
        for (a in segs.indices) for (b in a + 1 until segs.size) {
            val p = segs[a]
            val q = segs[b]
            if (p.l == q.l || owner(p.l) == owner(q.l)) continue
            val pLevel = p.y0 == p.y1
            if (pLevel != (q.y0 == q.y1)) continue
            val room = (g.linkW[p.l] + g.linkW[q.l]) / 2f
            val apart = if (pLevel) abs(p.y0 - q.y0) else abs(p.x0 - q.x0)
            val shared = if (pLevel) min(p.x1, q.x1) - max(p.x0, q.x0) else min(p.y1, q.y1) - max(p.y0, q.y0)
            if (shared > room) assertTrue("links ${p.l} and ${q.l} share a lane", apart >= room)
        }
    }

    @Test
    fun `the same data gives the same picture`() {
        val a = layout(busyFlow())
        val b = layout(busyFlow())
        assertTrue(a.x.contentEquals(b.x) && a.y.contentEquals(b.y))
        a.routes.indices.forEach { assertTrue(a.routes[it].contentEquals(b.routes[it])) }
    }
}
