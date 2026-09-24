package app.outgo.ui.analysis

import app.outgo.data.db.dao.MonthCategoryTotal
import app.outgo.data.db.dao.TradeSlim
import app.outgo.domain.CategoryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private val ZONE: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")

private fun millis(year: Int, month: Int, day: Int, hour: Int = 12): Long =
    LocalDate.of(year, month, day).atTime(LocalTime.of(hour, 0)).atZone(ZONE).toInstant().toEpochMilli()

private fun trade(amount: Long, year: Int, month: Int, day: Int, id: Long = 0, categoryId: Long? = 1L) =
    TradeSlim(id = id, amount = amount, occurredAt = millis(year, month, day), categoryId = categoryId, note = null)

private fun stat(monthKey: Int, rootId: Long, total: Long, type: Int = CategoryKind.EXPENSE) =
    MonthCategoryTotal(monthKey, rootId, type, "cat$rootId", 0, null, total)

class AnalysisAggregateTest {

    // -------------------------------------------------------------- weekday averages

    @Test
    fun `weekday average divides by calendar days, not by trade count`() {
        // June–August 2026. August 2026 has 5 Mondays (3, 10, 17, 24, 31);
        // June has 5 and July has 4 -> 14 Mondays in the range.
        val rows = listOf(
            trade(700, 2026, 8, 3),
            trade(300, 2026, 8, 10),
        )
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
    fun `weekday ignores trades outside the three-month range`() {
        val rows = listOf(trade(700, 2026, 5, 4), trade(700, 2026, 8, 3))
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
        val pace = buildPace(daily, LongArray(31), month = 202608, zone = ZONE, nowMillis = millis(2026, 8, 10))
        assertEquals(10, pace.current.size)
        assertEquals(1000L, pace.currentTotal)
        assertEquals(31, pace.daysInMonth)
        // Fractions of the max total: 100, 500, 1000 on days 1, 3, 10.
        assertEquals(0.1f, pace.current[0], 0.0001f)
        assertEquals(0.5f, pace.current[2], 0.0001f)
        assertEquals(1f, pace.current[9], 0.0001f)
    }

    @Test
    fun `a past month draws the whole month and shares the axis with the previous one`() {
        val daily = LongArray(31).also { it[0] = 100 }
        val prev = LongArray(30).also { it[0] = 400 }
        val pace = buildPace(daily, prev, month = 202608, zone = ZONE, nowMillis = millis(2026, 10, 1))
        assertEquals(31, pace.current.size)
        assertEquals(30, pace.previous.size)
        assertEquals(400L, pace.maxTotal)
        assertEquals(0.25f, pace.current[30], 0.0001f)
        assertEquals(1f, pace.previous[29], 0.0001f)
    }

    // ------------------------------------------------------------------- movers sort

    @Test
    fun `movers sort by amount moved, not by percent`() {
        val current = listOf(stat(202608, 1, 1_000_000), stat(202608, 2, 300))
        val previous = listOf(stat(202607, 1, 900_000), stat(202607, 2, 10))
        val stage = buildStats(
            totals = current + previous,
            month = 202608,
            otherName = "Other",
            zone = ZONE,
            nowMillis = millis(2026, 8, 31),
        )
        val movers = (stage as Stage.Ready).data.movers
        // Category 2 moved +2900%, category 1 only +11% — but +100.000 beats +290.
        assertEquals(listOf(1L, 2L), movers.map { it.rootId })
        assertEquals(100_000L, movers[0].delta)
        assertEquals(1f, movers[0].fraction)
    }

    @Test
    fun `a category that dropped to zero still counts as a mover`() {
        val stage = buildStats(
            totals = listOf(stat(202608, 1, 100), stat(202607, 2, 5_000)),
            month = 202608,
            otherName = "Other",
            zone = ZONE,
            nowMillis = millis(2026, 8, 31),
        )
        val movers = (stage as Stage.Ready).data.movers
        assertEquals(listOf(2L, 1L), movers.map { it.rootId })
        assertEquals(-5_000L, movers[0].delta)
    }

    // ---------------------------------------------------------------------- summary

    @Test
    fun `average per day divides by days elapsed in the live month`() {
        val stage = buildStats(
            totals = listOf(stat(202609, 1, 1_000)),
            month = 202609,
            otherName = "Other",
            zone = ZONE,
            nowMillis = millis(2026, 9, 10),
        )
        assertEquals(100L, (stage as Stage.Ready).data.summary.avgPerDay)
    }

    @Test
    fun `average per day divides by the whole month once it is over`() {
        val stage = buildStats(
            totals = listOf(stat(202609, 1, 3_000)),
            month = 202609,
            otherName = "Other",
            zone = ZONE,
            nowMillis = millis(2026, 11, 1),
        )
        assertEquals(100L, (stage as Stage.Ready).data.summary.avgPerDay)
    }

    @Test
    fun `percent change is null without a baseline`() {
        assertNull(percentChange(500, 0))
        assertEquals(-20, percentChange(400, 500))
    }

    // ------------------------------------------------------------------ slices, misc

    @Test
    fun `slices beyond the top six collapse into one Other slice`() {
        val totals = (1L..9L).map { stat(202608, it, it * 100) }
        val stage = buildStats(totals, 202608, "Other", ZONE, millis(2026, 8, 31))
        val set = (stage as Stage.Ready).data.expense
        assertEquals(7, set.rows.size)
        assertNull(set.rows.last().rootId)
        // 9 categories, 100..900: the three smallest (100+200+300) are the Other row.
        assertEquals(600L, set.rows.last().amount)
        assertEquals(360f, set.donut.slices.sumOf { it.sweepAngle.toDouble() }.toFloat(), 0.01f)
    }

    @Test
    fun `an empty month is Empty, not a zeroed Ready`() {
        assertEquals(Stage.Empty, buildStats(emptyList(), 202608, "Other", ZONE, millis(2026, 8, 31)))
        assertEquals(Stage.Empty, buildTrades(emptyList(), emptyList(), 202608, emptyMap(), ZONE, millis(2026, 8, 31)))
    }

    @Test
    fun `heatmap starts the grid on Monday and scales to the busiest day`() {
        val rows = listOf(trade(100, 2026, 8, 1), trade(400, 2026, 8, 5))
        val stage = buildTrades(rows, emptyList(), 202608, emptyMap(), ZONE, millis(2026, 9, 1))
        val heatmap = (stage as Stage.Ready).data.heatmap
        // 1 Aug 2026 is a Saturday -> 5 blank cells before it.
        assertEquals(5, heatmap.leadingBlanks)
        assertEquals(31, heatmap.cells.size)
        assertEquals(400L, heatmap.maxAmount)
        assertEquals(0.25f, heatmap.cells[0].intensity, 0.0001f)
        assertEquals(1f, heatmap.cells[4].intensity, 0.0001f)
        assertEquals(0f, heatmap.cells[1].intensity, 0.0001f)
    }

    @Test
    fun `largest purchases are the months top five, biggest first`() {
        val rows = (1L..8L).map { trade(it * 10, 2026, 8, it.toInt(), id = it) }
        val stage = buildTrades(rows, emptyList(), 202608, emptyMap(), ZONE, millis(2026, 9, 1))
        val largest = (stage as Stage.Ready).data.largest
        assertEquals(listOf(80L, 70L, 60L, 50L, 40L), largest.map { it.amount })
        assertEquals(8L, largest.first().tradeId)
    }

    @Test
    fun `trades from the previous month feed the pace line only`() {
        val rows = listOf(trade(100, 2026, 8, 1), trade(900, 2026, 7, 1))
        val stage = buildTrades(rows, emptyList(), 202608, emptyMap(), ZONE, millis(2026, 9, 1))
        val data = (stage as Stage.Ready).data
        assertEquals(1, data.largest.size)
        assertEquals(100L, data.pace.currentTotal)
        assertEquals(900L, data.pace.maxTotal)
    }
}
