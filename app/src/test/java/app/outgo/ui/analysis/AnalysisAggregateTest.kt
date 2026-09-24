package app.outgo.ui.analysis

import app.outgo.data.db.dao.MonthCategoryTotal
import app.outgo.data.db.dao.TradeSlim
import app.outgo.data.db.dao.TransferTotal
import app.outgo.domain.CategoryKind
import app.outgo.domain.TradeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private val ZONE: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")

private fun millis(year: Int, month: Int, day: Int, hour: Int = 12): Long =
    LocalDate.of(year, month, day).atTime(LocalTime.of(hour, 0)).atZone(ZONE).toInstant().toEpochMilli()

private fun trade(
    amount: Long,
    year: Int,
    month: Int,
    day: Int,
    id: Long = 0,
    categoryId: Long? = 1L,
    type: Int = TradeType.EXPENSE,
) = TradeSlim(id = id, type = type, amount = amount, occurredAt = millis(year, month, day), categoryId = categoryId, note = null)

private fun stat(monthKey: Int, rootId: Long, total: Long, type: Int = CategoryKind.EXPENSE) =
    MonthCategoryTotal(monthKey, rootId, type, "cat$rootId", 0, null, total)

private fun trades(
    rows: List<TradeSlim>,
    month: Int,
    weekdayRows: List<TradeSlim> = emptyList(),
    transfers: List<TransferTotal> = emptyList(),
    nowMillis: Long = millis(2026, 9, 1),
) = buildTrades(rows, weekdayRows, transfers, month, emptyMap(), emptyMap(), ZONE, nowMillis)

private fun stats(totals: List<MonthCategoryTotal>, month: Int, nowMillis: Long = millis(2026, 8, 31)) =
    buildStats(totals, month, "Other", ZONE, nowMillis)

class AnalysisAggregateTest {

    // -------------------------------------------------------------- weekday averages

    @Test
    fun `weekday average divides by calendar days, not by trade count`() {
        // June–August 2026. August 2026 has 5 Mondays (3, 10, 17, 24, 31);
        // June has 5 and July has 4 -> 14 Mondays in the range.
        val rows = listOf(trade(700, 2026, 8, 3), trade(300, 2026, 8, 10))
        val bars = buildWeekday(rows, month = 202608, zone = ZONE, nowMillis = millis(2026, 9, 1))
        val monday = bars.single { it.weekday == 1 }
        assertEquals(1000L / 14, monday.average)
        // Two trades on Mondays must NOT average to 500.
        assertTrue(monday.average < 500)
        assertEquals(0L, bars.single { it.weekday == 3 }.average)
    }

    @Test
    fun `weekday average counts only days up to today`() {
        // "Today" is 15 Aug 2026 (a Saturday), so the range is 1 Jun … 15 Aug:
        // Mondays on 1, 8, 15, 22, 29 Jun + 6, 13, 20, 27 Jul + 3, 10 Aug = 11.
        val rows = listOf(trade(1100, 2026, 8, 3))
        val bars = buildWeekday(rows, month = 202608, zone = ZONE, nowMillis = millis(2026, 8, 15))
        assertEquals(100L, bars.single { it.weekday == 1 }.average)
    }

    @Test
    fun `weekday ignores income and trades outside the three-month range`() {
        val rows = listOf(
            trade(700, 2026, 5, 4),
            trade(9_999, 2026, 8, 3, type = TradeType.INCOME),
            trade(700, 2026, 8, 3),
        )
        val bars = buildWeekday(rows, month = 202608, zone = ZONE, nowMillis = millis(2026, 9, 1))
        assertEquals(700L / 14, bars.single { it.weekday == 1 }.average)
    }

    // ---------------------------------------------------------------- median buckets

    @Test
    fun `buckets split on multiples of the median`() {
        // Median of 5 values = 100. Edges: 50, 200, 500.
        val ui = buildBuckets(listOf(10L, 60L, 100L, 300L, 900L))
        assertEquals(100L, ui.median)
        assertEquals(listOf(1, 2, 1, 1), ui.buckets.map { it.count })
        assertEquals(listOf(10L, 160L, 300L, 900L), ui.buckets.map { it.amount })
        assertEquals(20f, ui.buckets[0].countPercent)
        assertEquals(40f, ui.buckets[1].countPercent)
        // Money share is independent of the transaction share: 1 in 5 purchases, 66% of the money.
        assertEquals(900f * 100 / 1370, ui.buckets[3].moneyPercent, 0.01f)
    }

    @Test
    fun `buckets use the average of the two middle values on an even count`() {
        assertEquals(150L, buildBuckets(listOf(100L, 200L, 300L, 100L)).median)
    }

    @Test
    fun `bucket edges are inclusive at the bottom and exclusive at the top`() {
        // Median 150 -> edges 75/300/750. 50 < 75 -> first bucket; 100, 200 and 500 land in
        // the second and third; nothing reaches the last one.
        val ui = buildBuckets(listOf(50L, 100L, 200L, 500L))
        assertEquals(150L, ui.median)
        assertEquals(listOf(1, 2, 1, 0), ui.buckets.map { it.count })
        assertEquals(4, ui.buckets.sumOf { it.count })
    }

    // -------------------------------------------------------------- cumulative series

    @Test
    fun `cumulative series accumulates and stops at today in the live month`() {
        val daily = LongArray(31) // August 2026
        daily[0] = 100
        daily[2] = 400
        daily[9] = 500
        val pace = buildPace(daily, LongArray(31), LongArray(31), LongArray(31), 202608, ZONE, millis(2026, 8, 10))
        assertEquals(10, pace.expense.current.size)
        assertEquals(1000L, pace.expense.currentTotal)
        assertEquals(31, pace.daysInMonth)
        // Fractions of the max total: 100, 500, 1000 on days 1, 3, 10.
        assertEquals(0.1f, pace.expense.current[0], 0.0001f)
        assertEquals(0.5f, pace.expense.current[2], 0.0001f)
        assertEquals(1f, pace.expense.current[9], 0.0001f)
    }

    @Test
    fun `a past month draws the whole month and shares the axis with the previous one`() {
        val daily = LongArray(31).also { it[0] = 100 }
        val prev = LongArray(30).also { it[0] = 400 }
        val pace = buildPace(daily, prev, LongArray(31), LongArray(30), 202608, ZONE, millis(2026, 10, 1))
        assertEquals(31, pace.expense.current.size)
        assertEquals(30, pace.expense.previous.size)
        assertEquals(400L, pace.maxTotal)
        assertEquals(0.25f, pace.expense.current[30], 0.0001f)
        assertEquals(1f, pace.expense.previous[29], 0.0001f)
    }

    @Test
    fun `expense and income share one pace axis`() {
        val expense = LongArray(31).also { it[0] = 200 }
        val income = LongArray(31).also { it[0] = 800 }
        val pace = buildPace(expense, LongArray(30), income, LongArray(30), 202608, ZONE, millis(2026, 10, 1))
        assertEquals(800L, pace.maxTotal)
        assertEquals(0.25f, pace.expense.current[0], 0.0001f)
        assertEquals(1f, pace.income.current[0], 0.0001f)
        assertEquals(200L, pace.expense.currentTotal)
        assertEquals(800L, pace.income.currentTotal)
    }

    // ------------------------------------------------------------------- movers sort

    @Test
    fun `movers sort by amount moved, not by percent`() {
        val current = listOf(stat(202608, 1, 1_000_000), stat(202608, 2, 300))
        val previous = listOf(stat(202607, 1, 900_000), stat(202607, 2, 10))
        val movers = (stats(current + previous, 202608) as Stage.Ready).data.expenseMovers
        // Category 2 moved +2900%, category 1 only +11% — but +100.000 beats +290.
        assertEquals(listOf(1L, 2L), movers.map { it.rootId })
        assertEquals(100_000L, movers[0].delta)
        assertEquals(1f, movers[0].fraction)
    }

    @Test
    fun `a category that dropped to zero still counts as a mover`() {
        val movers = (stats(listOf(stat(202608, 1, 100), stat(202607, 2, 5_000)), 202608) as Stage.Ready)
            .data.expenseMovers
        assertEquals(listOf(2L, 1L), movers.map { it.rootId })
        assertEquals(-5_000L, movers[0].delta)
    }

    @Test
    fun `movers are kept per kind and merged for All mode`() {
        val totals = listOf(
            stat(202608, 1, 1_000), stat(202607, 1, 100),
            stat(202608, 2, 9_000, CategoryKind.INCOME), stat(202607, 2, 100, CategoryKind.INCOME),
        )
        val data = (stats(totals, 202608) as Stage.Ready).data
        assertEquals(listOf(1L), data.expenseMovers.map { it.rootId })
        assertEquals(listOf(2L), data.incomeMovers.map { it.rootId })
        // All mode ranks both kinds together: the income category moved more.
        assertEquals(listOf(2L, 1L), data.allMovers.map { it.rootId })
        assertEquals(CategoryKind.INCOME, data.allMovers[0].kind)
        assertEquals(CategoryKind.EXPENSE, data.allMovers[1].kind)
    }

    // ---------------------------------------------------------------------- summary

    @Test
    fun `summary keeps both kinds and their net`() {
        val totals = listOf(
            stat(202608, 1, 1_000), stat(202607, 1, 500),
            stat(202608, 2, 3_000, CategoryKind.INCOME), stat(202607, 2, 4_000, CategoryKind.INCOME),
        )
        val summary = (stats(totals, 202608) as Stage.Ready).data.summary
        assertEquals(1_000L, summary.expense.total)
        assertEquals(500L, summary.expense.deltaAmount)
        assertEquals(100, summary.expense.deltaPercent)
        assertEquals(3_000L, summary.income.total)
        assertEquals(-25, summary.income.deltaPercent)
        assertEquals(2_000L, summary.net)
        assertEquals(3_500L, summary.prevNet)
        assertEquals(summary.expense, summary.of(AnalysisMode.EXPENSE))
        assertEquals(summary.income, summary.of(AnalysisMode.INCOME))
    }

    @Test
    fun `average per day divides by days elapsed in the live month`() {
        val stage = stats(listOf(stat(202609, 1, 1_000)), 202609, nowMillis = millis(2026, 9, 10))
        assertEquals(100L, (stage as Stage.Ready).data.summary.expense.perPeriod)
    }

    @Test
    fun `average per day divides by the whole month once it is over`() {
        val stage = stats(listOf(stat(202609, 1, 3_000)), 202609, nowMillis = millis(2026, 11, 1))
        assertEquals(100L, (stage as Stage.Ready).data.summary.expense.perPeriod)
    }

    @Test
    fun `percent change is null without a baseline`() {
        assertNull(percentChange(500, 0))
        assertEquals(-20, percentChange(400, 500))
    }

    // ------------------------------------------------------------------ slices, bars

    @Test
    fun `slices beyond the top six collapse into one Other slice`() {
        val totals = (1L..9L).map { stat(202608, it, it * 100) }
        val set = (stats(totals, 202608) as Stage.Ready).data.expense
        assertEquals(7, set.rows.size)
        assertNull(set.rows.last().rootId)
        // 9 categories, 100..900: the three smallest (100+200+300) are the Other row.
        assertEquals(600L, set.rows.last().amount)
        assertEquals(360f, set.donut.slices.sumOf { it.sweepAngle.toDouble() }.toFloat(), 0.01f)
        assertEquals(CategoryKind.EXPENSE, set.rows.first().kind)
    }

    @Test
    fun `bars carry both kinds on one shared scale`() {
        val totals = listOf(
            stat(202608, 1, 400), stat(202607, 1, 200),
            stat(202608, 2, 800, CategoryKind.INCOME),
        )
        val bars = (stats(totals, 202608) as Stage.Ready).data.bars
        assertEquals(6, bars.bars.size)
        val august = bars.bars.single { it.monthKey == 202608 }
        assertEquals(400L, august.expense)
        assertEquals(800L, august.income)
        // Scaled against 800, the biggest value of either kind.
        assertEquals(0.5f, august.expenseFraction, 0.0001f)
        assertEquals(1f, august.incomeFraction, 0.0001f)
        assertTrue(august.selected)
        assertFalse(bars.bars.single { it.monthKey == 202607 }.selected)
        assertEquals(600L / 6, bars.expenseAverage)
    }

    @Test
    fun `an empty month is Empty, not a zeroed Ready`() {
        assertEquals(Stage.Empty, stats(emptyList(), 202608))
        assertEquals(Stage.Empty, trades(emptyList(), 202608))
    }

    // ------------------------------------------------------------ trades, transfers

    @Test
    fun `heatmap starts the grid on Monday, scales to the busiest day and ignores income`() {
        val rows = listOf(
            trade(100, 2026, 8, 1),
            trade(9_999, 2026, 8, 2, type = TradeType.INCOME),
            trade(400, 2026, 8, 5),
        )
        val heatmap = (trades(rows, 202608) as Stage.Ready).data.heatmap
        // 1 Aug 2026 is a Saturday -> 5 blank cells before it.
        assertEquals(5, heatmap.leadingBlanks)
        assertEquals(31, heatmap.cells.size)
        assertEquals(400L, heatmap.maxAmount)
        assertEquals(0.25f, heatmap.cells[0].intensity, 0.0001f)
        assertEquals(1f, heatmap.cells[4].intensity, 0.0001f)
        assertEquals(0f, heatmap.cells[1].intensity, 0.0001f)
    }

    @Test
    fun `largest is kept per kind and merged for All mode`() {
        val rows = listOf(
            trade(10, 2026, 8, 1, id = 1),
            trade(30, 2026, 8, 2, id = 2),
            trade(20, 2026, 8, 3, id = 3, type = TradeType.INCOME),
            trade(90, 2026, 8, 4, id = 4, type = TradeType.INCOME),
        )
        val data = (trades(rows, 202608) as Stage.Ready).data
        assertEquals(listOf(30L, 10L), data.expenseLargest.map { it.amount })
        assertEquals(listOf(90L, 20L), data.incomeLargest.map { it.amount })
        assertEquals(listOf(90L, 30L, 20L, 10L), data.allLargest.map { it.amount })
        assertEquals(TradeType.INCOME, data.allLargest.first().type)
        assertEquals(data.expenseLargest, data.largestOf(AnalysisMode.EXPENSE))
        assertEquals(data.allLargest, data.largestOf(AnalysisMode.ALL))
    }

    @Test
    fun `largest never takes more than five`() {
        val rows = (1L..8L).map { trade(it * 10, 2026, 8, it.toInt(), id = it) }
        val largest = (trades(rows, 202608) as Stage.Ready).data.expenseLargest
        assertEquals(listOf(80L, 70L, 60L, 50L, 40L), largest.map { it.amount })
        assertEquals(8L, largest.first().tradeId)
    }

    @Test
    fun `trades from the previous month feed the pace line only`() {
        val rows = listOf(trade(100, 2026, 8, 1), trade(900, 2026, 7, 1))
        val data = (trades(rows, 202608) as Stage.Ready).data
        assertEquals(1, data.expenseLargest.size)
        assertEquals(100L, data.pace.expense.currentTotal)
        assertEquals(900L, data.pace.maxTotal)
    }

    @Test
    fun `transfers are summed per account pair, biggest first`() {
        val totals = listOf(
            TransferTotal(fromAccountId = 1, toAccountId = 2, count = 2, total = 500),
            TransferTotal(fromAccountId = 2, toAccountId = 3, count = 1, total = 1_500),
        )
        val ui = buildTransfers(totals, mapOf(1L to "Cash", 2L to "Bank", 3L to "Savings"))
        assertEquals(listOf("Bank", "Cash"), ui.pairs.map { it.fromName })
        assertEquals("Savings", ui.pairs.first().toName)
        assertEquals(2_000L, ui.total)
        assertEquals(3, ui.count)
        assertEquals(1f, ui.pairs.first().fraction, 0.0001f)
        assertEquals(500f / 1500f, ui.pairs.last().fraction, 0.0001f)
    }

    @Test
    fun `a month with nothing but transfers is still Ready`() {
        val totals = listOf(TransferTotal(1, 2, count = 1, total = 500))
        val stage = trades(emptyList(), 202608, transfers = totals)
        assertEquals(500L, (stage as Stage.Ready).data.transfers.total)
    }

    // ------------------------------------------------------------------- year page

    @Test
    fun `a past year counts all twelve months`() {
        val totals = (1..12).map { stat(202500 + it, 1, 1_000) }
        val data = (buildYearStats(totals, 2025, "Other", ZONE, millis(2026, 9, 1)) as Stage.Ready).data
        assertEquals(12, data.summary.monthsCounted)
        assertFalse(data.summary.partial)
        assertEquals(12_000L, data.summary.summary.expense.total)
        assertEquals(1_000L, data.summary.summary.expense.perPeriod)
        assertEquals(12, data.bars.bars.size)
    }

    @Test
    fun `the current year is year-to-date and compares against the same months last year`() {
        // Today is 1 Sep 2026: 9 months counted. Last year had 12, but only Jan–Sep may count.
        val thisYear = (1..9).map { stat(202600 + it, 1, 1_000) }
        val lastYear = (1..12).map { stat(202500 + it, 1, 2_000) }
        val data = (buildYearStats(thisYear + lastYear, 2026, "Other", ZONE, millis(2026, 9, 1)) as Stage.Ready).data
        assertTrue(data.summary.partial)
        assertEquals(9, data.summary.monthsCounted)
        assertEquals(9_000L, data.summary.summary.expense.total)
        // 9 * 2.000, not 12 * 2.000.
        assertEquals(18_000L, data.summary.summary.expense.prevTotal)
        assertEquals(-50, data.summary.summary.expense.deltaPercent)
    }

    @Test
    fun `the year summary names its biggest and smallest month, ignoring empty ones`() {
        val totals = listOf(stat(202503, 1, 5_000), stat(202507, 1, 1_000), stat(202511, 1, 3_000))
        val data = (buildYearStats(totals, 2025, "Other", ZONE, millis(2026, 9, 1)) as Stage.Ready).data
        assertEquals(202503, data.summary.biggestMonth)
        assertEquals(5_000L, data.summary.biggestAmount)
        assertEquals(202507, data.summary.smallestMonth)
        assertEquals(1_000L, data.summary.smallestAmount)
    }

    @Test
    fun `year categories are summed across the whole year before the top-six cut`() {
        val totals = (1..12).map { stat(202500 + it, 1, 100) } + (1..12).map { stat(202500 + it, 2, 50) }
        val set = (buildYearStats(totals, 2025, "Other", ZONE, millis(2026, 9, 1)) as Stage.Ready).data.expense
        assertEquals(2, set.rows.size)
        assertEquals(1_200L, set.rows.first().amount)
        assertEquals(600L, set.rows.last().amount)
    }

    @Test
    fun `year-over-year lines are cumulative and share one axis`() {
        val thisYear = (1..3).map { stat(202600 + it, 1, 1_000) }
        val lastYear = (1..12).map { stat(202500 + it, 1, 500) }
        val yoy = (buildYearStats(thisYear + lastYear, 2026, "Other", ZONE, millis(2026, 3, 31)) as Stage.Ready)
            .data.yoy
        assertEquals(3, yoy.expense.current.size)
        assertEquals(12, yoy.expense.previous.size)
        assertEquals(3_000L, yoy.expense.currentTotal)
        assertEquals(6_000L, yoy.expense.previousTotal)
        assertEquals(6_000L, yoy.maxTotal)
        assertEquals(0.5f, yoy.expense.current[2], 0.0001f)
        assertEquals(1f, yoy.expense.previous[11], 0.0001f)
    }

    @Test
    fun `a year with no data is Empty`() {
        assertEquals(Stage.Empty, buildYearStats(emptyList(), 2025, "Other", ZONE, millis(2026, 9, 1)))
        assertEquals(
            Stage.Empty,
            buildYearTrades(emptyList(), emptyList(), 2025, emptyMap(), emptyMap(), ZONE),
        )
    }

    @Test
    fun `year trades keep only rows inside the year`() {
        val rows = listOf(trade(500, 2025, 6, 1, id = 1), trade(900, 2026, 1, 1, id = 2))
        val data = (buildYearTrades(rows, emptyList(), 2025, emptyMap(), emptyMap(), ZONE) as Stage.Ready).data
        assertEquals(listOf(1L), data.expenseLargest.map { it.tradeId })
    }
}
