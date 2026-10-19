package app.outgo.ui.analysis

import app.outgo.data.db.dao.MonthCategoryTotal
import app.outgo.domain.CategoryKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private val ZONE: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")

private fun millis(year: Int, month: Int, day: Int, hour: Int = 12): Long =
    LocalDate.of(year, month, day).atTime(LocalTime.of(hour, 0)).atZone(ZONE).toInstant().toEpochMilli()

private fun stat(monthKey: Int, rootId: Long, total: Long, type: Int = CategoryKind.EXPENSE) =
    MonthCategoryTotal(monthKey, rootId, type, "cat$rootId", 0, null, total)

private fun summary(totals: List<MonthCategoryTotal>, month: Int, nowMillis: Long = millis(2026, 8, 31)) =
    buildSummary(totals, month, ZONE, nowMillis)

class AnalysisAggregateTest {

    // ------------------------------------------------------------------- chart axes

    @Test
    fun `day labels include the first and last day and are evenly spread`() {
        assertEquals(listOf(1, 9, 16, 24, 31), axisDays(31))
        assertEquals(listOf(1, 8, 16, 23, 30), axisDays(30))
        assertEquals(listOf(1, 8, 15, 22, 29), axisDays(29))
        assertEquals(listOf(1, 8, 15, 21, 28), axisDays(28))
        // Always exactly `count` labels, always starting at 1 and ending on the last day.
        listOf(28, 29, 30, 31).forEach { days ->
            val labels = axisDays(days)
            assertEquals(5, labels.size)
            assertEquals(1, labels.first())
            assertEquals(days, labels.last())
        }
    }

    // ---------------------------------------------------------------------- summary

    @Test
    fun `summary keeps both kinds and their net`() {
        val totals = listOf(
            stat(202608, 1, 1_000), stat(202607, 1, 500),
            stat(202608, 2, 3_000, CategoryKind.INCOME), stat(202607, 2, 4_000, CategoryKind.INCOME),
        )
        val summary = (summary(totals, 202608) as Stage.Ready).data
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
        val stage = summary(listOf(stat(202609, 1, 1_000)), 202609, nowMillis = millis(2026, 9, 10))
        assertEquals(100L, (stage as Stage.Ready).data.expense.perPeriod)
    }

    @Test
    fun `average per day divides by the whole month once it is over`() {
        val stage = summary(listOf(stat(202609, 1, 3_000)), 202609, nowMillis = millis(2026, 11, 1))
        assertEquals(100L, (stage as Stage.Ready).data.expense.perPeriod)
    }

    @Test
    fun `percent change is 100 percent without a baseline`() {
        assertEquals(100, percentChange(500, 0))
        assertEquals(-100, percentChange(-500, 0))
        assertEquals(0, percentChange(0, 0))
        assertEquals(-20, percentChange(400, 500))
        assertEquals(50, percentChange(-500, -1_000))
    }

    @Test
    fun `an empty month is Empty, not a zeroed Ready`() {
        assertEquals(Stage.Empty, summary(emptyList(), 202608))
        // Only last month has data: this month is still empty.
        assertEquals(Stage.Empty, summary(listOf(stat(202607, 1, 500)), 202608))
    }
}
