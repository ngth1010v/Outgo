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

private fun card(hidden: List<Long> = emptyList()) = ChartCard(0, ChartType.TRADE_CASH_FLOW, hidden = hidden)

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

        val bare = buildCashFlow(rows, ACCOUNTS, cashFlowOptions(card(listOf(CASH_HIDE_TRANSFERS, CASH_HIDE_ADJUSTMENTS)), 5))!!
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
            cashFlowOptions(card(listOf(SAVINGS)), 5),
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
        val flow = buildCashFlow(rows, ACCOUNTS, cashFlowOptions(card(emptyList()), 5))!!
        val uses = flow.column(CashColumn.USE).map { flow.nodes[it] }
        assertEquals(listOf(208L, 207L, 206L, 205L, OTHER_NODE), uses.map { it.refId })
        assertEquals((1..4).sumOf { it * 100L }, uses.last().total)
    }

    @Test
    fun `accounts beyond the limit join one Other account`() {
        val accounts = (1L..6L).associateWith { Triple("a$it", null, 0) }
        val rows = (1L..6L).map { income(it, 100, it * 1_000) }
        val flow = buildCashFlow(rows, accounts, cashFlowOptions(card(emptyList()), 3))!!
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
        val flow = buildCashFlow(rows, accounts, cashFlowOptions(card(emptyList()), 5, 3))!!
        assertEquals(300L, flow.fromBalance)
        assertEquals(10_000L + 5_000L + 200L, flow.kept)
        // The joined node shows the sum of its accounts' changes.
        assertEquals(-100L, flow.nodes[flow.node(CashKind.ACCOUNT, OTHER_NODE)].net)
        assertEquals(10_000L, flow.nodes[flow.node(CashKind.ACCOUNT, 1)].net)
    }

    // ------------------------------------------------------------------- levels and filter

    @Test
    fun `subaccounts and sub-categories are drawn as their own nodes when asked`() {
        // Bank (1) has subaccounts 11 and 12; category 100 has sub-categories 101 and 102.
        val accounts = ACCOUNTS + mapOf(11L to Triple("Bank A", null, 0), 12L to Triple("Bank B", null, 0))
        fun sub(type: Int, account: Long, category: Long?, to: Long?, toParent: Long?, total: Long) = CashFlowRow(
            type, BANK, toParent, category?.let { 100L }, category?.let { "parent" }, null, 1, total,
            subAccountId = account, toSubAccountId = to, subCategoryId = category, subCategoryName = category?.let { "sub$it" },
        )
        val rows = listOf(
            sub(TradeType.INCOME, 11, 101, null, null, 1_000),
            sub(TradeType.INCOME, 12, 102, null, null, 500),
            sub(TradeType.TRANSFER, 11, null, 12, BANK, 300),
        )
        val parents = buildCashFlow(rows, accounts)!!
        assertEquals(listOf(BANK), parents.column(CashColumn.ACCOUNT).map { parents.nodes[it].refId })
        assertEquals(listOf(100L), parents.column(CashColumn.SOURCE).map { parents.nodes[it].refId })
        // A transfer between two subaccounts of one parent only shows at the subaccount level.
        assertTrue(parents.links.none { parents.isTransfer(it) })

        val subs = buildCashFlow(rows, accounts, cashFlowOptions(card().copy(expandedAccounts = listOf(BANK), expandedCategories = listOf(100L)), NO_LIMIT))!!
        assertEquals(setOf(11L, 12L), subs.column(CashColumn.ACCOUNT).map { subs.nodes[it].refId }.toSet())
        assertEquals(listOf("sub101", "sub102"), subs.column(CashColumn.SOURCE).map { subs.nodes[it].name })
        assertEquals(300L, subs.link(subs.node(CashKind.ACCOUNT, 11), subs.node(CashKind.ACCOUNT, 12)))
    }

    @Test
    fun `each parent picks its own level, and a hidden parent hides its subaccounts`() {
        // Bank (1) has subaccounts 11 and 12; Cash (2) has 21.
        val accounts = ACCOUNTS + mapOf(11L to Triple("Bank A", null, 0), 12L to Triple("Bank B", null, 0), 21L to Triple("Wallet", null, 0))
        fun inc(parent: Long, sub: Long, total: Long) =
            CashFlowRow(TradeType.INCOME, parent, null, 100, "in", null, 1, total, subAccountId = sub)
        val rows = listOf(inc(BANK, 11, 1_000), inc(BANK, 12, 500), inc(CASH, 21, 200))
        fun drawn(card: ChartCard) = buildCashFlow(rows, accounts, cashFlowOptions(card, NO_LIMIT))!!
            .let { f -> f.column(CashColumn.ACCOUNT).map { f.nodes[it].refId }.toSet() }

        // Only Bank is split; Cash stays one node.
        assertEquals(setOf(11L, 12L, CASH), drawn(card().copy(expandedAccounts = listOf(BANK))))
        // A subaccount switched off goes, while its parent is split...
        assertEquals(setOf(11L, CASH), drawn(card(listOf(12L)).copy(expandedAccounts = listOf(BANK))))
        // ...and means nothing while it isn't.
        assertEquals(setOf(BANK, CASH), drawn(card(listOf(12L))))
        // A parent switched off takes its subaccounts with it.
        assertEquals(setOf(CASH), drawn(card(listOf(BANK)).copy(expandedAccounts = listOf(BANK))))
    }

    @Test
    fun `a hidden sub-category goes only while its parent is split`() {
        fun out(sub: Long, total: Long) =
            CashFlowRow(TradeType.EXPENSE, BANK, null, 200, "food", null, 1, total, subCategoryId = sub, subCategoryName = "s$sub")
        val rows = listOf(income(BANK, 100, 5_000), out(201, 300), out(202, 100))
        val split = buildCashFlow(rows, ACCOUNTS, cashFlowOptions(card().copy(expandedCategories = listOf(200L), hiddenCategories = listOf(202L)), NO_LIMIT))!!
        assertEquals(listOf(201L), split.column(CashColumn.USE).map { split.nodes[it].refId })
        val whole = buildCashFlow(rows, ACCOUNTS, cashFlowOptions(card().copy(hiddenCategories = listOf(202L)), NO_LIMIT))!!
        assertEquals(400L, whole.expense)
    }

    @Test
    fun `a hidden category's money is left out`() {
        val rows = listOf(income(BANK, 100, 1_000), expense(BANK, 200, 300), expense(BANK, 201, 100))
        val flow = buildCashFlow(rows, ACCOUNTS, cashFlowOptions(card().copy(hiddenCategories = listOf(200L)), 5))!!
        assertEquals(-1, flow.node(CashKind.EXPENSE, 200))
        assertEquals(100L, flow.expense)
    }

    @Test
    fun `with no limit nothing joins Other`() {
        val accounts = (1L..9L).associateWith { Triple("a$it", null, 0) }
        val rows = (1L..9L).map { income(it, 100 + it, it * 1_000) } + (1L..9L).map { expense(it, 200 + it, it * 10) }
        val flow = buildCashFlow(rows, accounts, cashFlowOptions(card(), NO_LIMIT))!!
        assertTrue(flow.nodes.none { it.refId == OTHER_NODE })
        assertEquals(9, flow.column(CashColumn.ACCOUNT).size)
        assertEquals(9, flow.column(CashColumn.USE).size)
    }

    @Test
    fun `the cash flow's levels and hidden categories are saved with the card`() {
        val card = ChartCard(
            0, ChartType.TRADE_CASH_FLOW, hidden = listOf(CASH_HIDE_TRANSFERS, 4L), hiddenCategories = listOf(7L, 9L),
            expandedAccounts = listOf(1L, 3L), expandedCategories = listOf(5L), accountDetail = true,
        )
        val plain = ChartCard(0, ChartType.TRADE_CASH_FLOW, fitScreen = true)
        val offered = setOf(ChartType.TRADE_CASH_FLOW)
        assertEquals(listOf(card, plain), decodeLayout(encodeLayout(listOf(card, plain)), emptyList(), offered))
        // Saved before these existed: parents, nothing hidden.
        assertEquals(listOf(ChartCard(0, ChartType.TRADE_CASH_FLOW)), decodeLayout("TRADE_CASH_FLOW.ALL.....", emptyList(), offered))
    }

    // ------------------------------------------------------------------- nodes

    private val metrics = CashNodeMetrics(
        accountW = 150f..230f, accountH = 220f..320f, caret = 70f..110f, link = 4f..24f,
        labelGap = 6f, gap = 16f, lanes = 50f, cell = 8f, linkGap = 6f,
        accountRadius = 20f, accountMargin = 8f, linkCorner = 16f, arrow = 18f,
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
    fun `links leave and enter accounts away from their rounded corners`() {
        val flow = busyFlow()
        for ((width, height) in listOf(1100f to 2000f, 1100f to 900f)) {
            val g = layout(flow, width, height)
            flow.links.forEachIndexed { l, link ->
                val r = g.routes[l]
                val half = g.linkW[l] / 2f
                fun check(i: Int, x: Float, y: Float) {
                    val b = g.box(flow, i)
                    val c = metrics.accountRadius
                    val onLevelSide = abs(y - b[1]) < 0.6f || abs(y - b[3]) < 0.6f
                    if (onLevelSide) {
                        assertTrue("link $l at a corner of $i", x - half >= b[0] + c - 0.5f && x + half <= b[2] - c + 0.5f)
                    } else {
                        assertTrue("link $l at a corner of $i", y - half >= b[1] + c - 0.5f && y + half <= b[3] - c + 0.5f)
                    }
                }
                if (flow.nodes[link.from].column == CashColumn.ACCOUNT) check(link.from, r[0], r[1])
                if (flow.nodes[link.to].column == CashColumn.ACCOUNT) check(link.to, r[r.size - 2], r[r.size - 1])
            }
        }
    }

    @Test
    fun `links keep a margin from accounts they don't touch`() {
        val flow = busyFlow()
        for ((width, height) in listOf(1100f to 2000f, 1100f to 900f)) {
            val g = layout(flow, width, height)
            // The router works on a grid, so allow half a cell less than the margin.
            val mg = metrics.accountMargin - metrics.cell / 2f
            flow.links.forEachIndexed { l, link ->
                val r = g.routes[l]
                val half = g.linkW[l] / 2f
                flow.column(CashColumn.ACCOUNT).filter { it != link.from && it != link.to }.forEach { i ->
                    val b = g.box(flow, i)
                    for (k in 2 until r.size step 2) {
                        val x0 = min(r[k - 2], r[k]) - half
                        val x1 = max(r[k - 2], r[k]) + half
                        val y0 = min(r[k - 1], r[k + 1]) - half
                        val y1 = max(r[k - 1], r[k + 1]) + half
                        val hits = x1 > b[0] - mg && x0 < b[2] + mg && y1 > b[1] - mg && y0 < b[3] + mg
                        assertTrue("link $l runs too close to account $i", !hits)
                    }
                }
            }
        }
    }

    @Test
    fun `a bend next to an account leaves room for its rounding and the arrowhead`() {
        val flow = busyFlow()
        for ((width, height) in listOf(1100f to 2000f, 1100f to 900f)) {
            val g = layout(flow, width, height)
            flow.links.forEachIndexed { l, link ->
                val r = g.routes[l]
                if (r.size < 6) return@forEachIndexed // straight: no bend at all
                val w = g.linkW[l]
                val bend = metrics.linkCorner + w / 2f
                if (flow.nodes[link.from].column == CashColumn.ACCOUNT) {
                    val first = abs(r[2] - r[0]) + abs(r[3] - r[1])
                    assertTrue("link $l bends right out of its account ($first)", first >= bend - 0.5f)
                }
                if (flow.nodes[link.to].column == CashColumn.ACCOUNT) {
                    val k = r.size - 2
                    val last = abs(r[k] - r[k - 2]) + abs(r[k + 1] - r[k - 1])
                    val need = bend + if (flow.isTransfer(link)) max(w * ARROW_LENGTH, metrics.arrow) else 0f
                    assertTrue("link $l bends right into its account ($last < $need)", last >= need - 0.5f)
                }
            }
        }
    }

    @Test
    fun `at its natural height every account keeps its own size`() {
        val flow = busyFlow()
        val n = flow.nodes.size
        val caret = { i: Int -> flow.nodes[i].column != CashColumn.ACCOUNT }
        val labelW = FloatArray(n) { if (caret(it)) 170f else 0f }
        val labelH = FloatArray(n) { if (caret(it)) 80f else 0f }
        val sizes = cashNodeSizes(flow, metrics)
        // Squeezed into a short box the accounts shrink; at the natural height they don't.
        val short = layoutCashNodes(flow, 700f, 500f, sizes, labelW, labelH, metrics)
        assertTrue(flow.column(CashColumn.ACCOUNT).any { short.h[it] < sizes.h[it] - 0.5f })
        val tall = cashNaturalHeight(flow, 700f, sizes, labelW, labelH, metrics)
        val g = layoutCashNodes(flow, 700f, tall, sizes, labelW, labelH, metrics)
        flow.column(CashColumn.ACCOUNT).forEach { assertEquals(sizes.h[it], g.h[it], 0.5f) }
    }

    @Test
    fun `the same data gives the same picture`() {
        val a = layout(busyFlow())
        val b = layout(busyFlow())
        assertTrue(a.x.contentEquals(b.x) && a.y.contentEquals(b.y))
        a.routes.indices.forEach { assertTrue(a.routes[it].contentEquals(b.routes[it])) }
    }
}
