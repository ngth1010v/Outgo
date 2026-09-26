package app.outgo.ui.analysis

import app.outgo.data.db.dao.AccountBalance
import app.outgo.data.db.dao.AccountFlow
import app.outgo.data.db.dao.AccountMove
import app.outgo.data.db.dao.FLOW_TRANSFER_IN
import app.outgo.data.db.dao.TradeSlim
import app.outgo.domain.TradeType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class AnalysisLayoutTest {

    @Test
    fun `never saved gives the slot defaults`() {
        val cards = decodeLayout(null, LayoutSlot.YEAR_CATEGORIES, firstId = 10)
        assertEquals(LayoutSlot.YEAR_CATEGORIES.charts, cards.map { it.type })
        assertEquals((10L until 10L + cards.size).toList(), cards.map { it.id })
    }

    @Test
    fun `a page offers each chart once, with its own icon, and its defaults are all offered`() {
        LayoutSlot.entries.forEach { slot ->
            val offered = slot.groups.flatMap { it.charts }
            assertEquals(slot.name, offered.size, offered.toSet().size)
            assertEquals(slot.name, offered.size, offered.map { it.icon }.toSet().size)
            assertEquals(slot.name, slot.charts.toSet(), offered.toSet())
        }
    }

    @Test
    fun `saved empty list stays empty`() {
        assertEquals(emptyList<ChartCard>(), decodeLayout("", LayoutSlot.MONTH_CATEGORIES, 0))
    }

    @Test
    fun `round trip keeps order, mode, account and zero, skips unknown charts`() {
        val cards = listOf(
            ChartCard(0, ChartType.ACCOUNT_LARGEST, AnalysisMode.INCOME, 7),
            ChartCard(1, ChartType.NET_FLOW),
            ChartCard(2, ChartType.ACCOUNT_DONUT, AnalysisMode.ALL),
            ChartCard(3, ChartType.BALANCE_TREND, zero = true),
        )
        val encoded = encodeLayout(cards)
        assertEquals(cards, decodeLayout(encoded, LayoutSlot.MONTH_ACCOUNTS, 0))
        val withFuture = "FUTURE_CHART.ALL.;$encoded"
        assertEquals(cards, decodeLayout(withFuture, LayoutSlot.MONTH_ACCOUNTS, 0))
        // Saved before the zero option existed.
        assertEquals(listOf(ChartCard(0, ChartType.NET_FLOW)), decodeLayout("NET_FLOW.ALL.", LayoutSlot.MONTH_ACCOUNTS, 0))
    }


    @Test
    fun `accounts split kinds, sign net flow and build balances from the opening`() {
        val accounts = mapOf(1L to Triple("Cash", null, 1), 2L to Triple("Bank", null, 2))
        val flows = listOf(
            AccountFlow(202607, 1, TradeType.EXPENSE, 100),
            AccountFlow(202608, 1, TradeType.EXPENSE, 300),
            AccountFlow(202608, 2, TradeType.INCOME, 1_000),
            // Bank -> Cash 200
            AccountFlow(202608, 2, TradeType.TRANSFER, 200),
            AccountFlow(202608, 1, FLOW_TRANSFER_IN, 200),
        )
        val rows = listOf(
            TradeSlim(1, TradeType.EXPENSE, 300, 0, null, null, accountId = 1),
            TradeSlim(2, TradeType.INCOME, 1_000, 0, null, null, accountId = 2),
        )
        val ui = buildAccounts(
            flows = flows,
            opening = listOf(AccountBalance(1, 500)),
            period = listOf(202608),
            previous = listOf(202607),
            trend = listOf(202607, 202608),
            rows = rows,
            accounts = accounts,
            categories = emptyMap(),
            otherName = "Other",
        )
        assertEquals(listOf(1L), ui.expense.rows.map { it.rootId })
        assertEquals(200L, ui.expense.deltaAmount)
        assertEquals(listOf(2L), ui.income.rows.map { it.rootId })
        // Bank +1000 -200, Cash -300 +200.
        assertEquals(listOf(2L to 800L, 1L to -100L), ui.netFlow.map { it.rootId to it.delta })
        assertEquals(listOf(listOf(400L, 300L), listOf(0L, 800L)), ui.balance.lines.map { it.values })
        assertEquals(listOf(1L), ui.largest.getValue(1).expense.map { it.tradeId })
        assertEquals(listOf(2L), ui.largest.getValue(2).all.map { it.tradeId })
    }

    @Test
    fun `value axis pads the data, or takes in 0, with ticks at least the gap apart`() {
        // 1000..1100 padded by 10 each side; 50 is the smallest 1/2/5 step 20px apart on 100px.
        assertEquals(ValueAxis(990.0, 1110.0, listOf(1_000L, 1_050L, 1_100L)), valueAxis(1_000, 1_100, 100f, 20f, zero = false))
        // Pinned to 0 at the bottom, padded only at the top.
        assertEquals(ValueAxis(0.0, 1210.0, listOf(0L, 500L, 1_000L)), valueAxis(1_000, 1_100, 100f, 20f, zero = true))
        // All below 0: pinned at the top instead.
        assertEquals(0.0, valueAxis(-500, -100, 100f, 20f, zero = true).high, 0.0)
        // A flat line is widened by a tenth of its value.
        assertEquals(ValueAxis(450.0, 550.0, listOf(460L, 480L, 500L, 520L, 540L)), valueAxis(500, 500, 100f, 20f, zero = false))
        // No height yet, no ticks.
        assertEquals(emptyList<Long>(), valueAxis(0, 10, 0f, 20f, zero = false).ticks)
    }

    @Test
    fun `daily balance runs from the month's opening, stops at today on the live month`() {
        val accounts = mapOf(1L to Triple("Cash", null, 1), 2L to Triple("Bank", null, 2))
        fun at(day: Int) = LocalDate.of(2026, 8, day).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val moves = listOf(
            AccountMove(1, at(2), -100),
            // Bank -> Cash 50 on the 3rd.
            AccountMove(2, at(3), -50),
            AccountMove(1, at(3), 50),
        )
        val ui = buildDailyBalance(
            opening = listOf(AccountBalance(1, 500)),
            // July moved Bank to 1000 before August started.
            flows = listOf(AccountFlow(202607, 2, TradeType.INCOME, 1_000), AccountFlow(202608, 2, TradeType.INCOME, 9)),
            moves = moves,
            month = 202608,
            accounts = accounts,
            zone = ZoneOffset.UTC,
            nowMillis = at(4),
        )
        assertEquals(31, ui.daysInMonth)
        assertEquals(listOf(1L, 2L), ui.lines.map { it.accountId })
        assertEquals(listOf(500L, 400L, 450L, 450L), ui.lines[0].values)
        assertEquals(listOf(1_000L, 1_000L, 950L, 950L), ui.lines[1].values)

        val past = buildDailyBalance(emptyList(), emptyList(), moves, 202608, accounts, ZoneOffset.UTC, nowMillis = at(31) + 86_400_000L * 40)
        assertEquals(31, past.lines[0].values.size)
    }
}
