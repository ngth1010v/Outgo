package app.outgo.ui.analysis

import app.outgo.data.db.dao.AccountBalance
import app.outgo.data.db.dao.AccountFlow
import app.outgo.data.db.dao.CategoryMonthTotal
import app.outgo.data.db.dao.MonthCategoryTotal
import app.outgo.domain.CategoryKind
import app.outgo.domain.TradeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnalysisYearTest {

    @Test
    fun `months between cross a year end`() {
        assertEquals(listOf(202511, 202512, 202601, 202602), monthsBetween(202511, 202602))
        assertEquals(202610, lastMonthOf(2026, 202610))
        assertEquals(202512, lastMonthOf(2025, 202610))
    }

    @Test
    fun `year summary compares the same months of last year and averages per month`() {
        fun stat(month: Int, total: Long, type: Int = CategoryKind.EXPENSE) = MonthCategoryTotal(month, 1, type, "", 0, null, total)
        val totals = listOf(stat(202501, 100), stat(202512, 900), stat(202601, 300), stat(202603, 300), stat(202602, 50, CategoryKind.INCOME))
        val summary = buildYearSummary(totals, 2026, 202603)!!
        assertEquals(600L, summary.expense.total)
        // December of last year is outside Jan..Mar, so it does not count.
        assertEquals(100L, summary.expense.prevTotal)
        assertEquals(200L, summary.expense.perPeriod)
        assertEquals(-550L, summary.net)
        assertNull(buildYearSummary(totals, 2024, 202412))
    }

    @Test
    fun `year balances give month ends, the year-end pair and the last years`() {
        val opening = listOf(AccountBalance(1, 1_000))
        val flows = listOf(
            AccountFlow(202312, 1, TradeType.INCOME, 500),
            AccountFlow(202512, 1, TradeType.EXPENSE, 300),
            AccountFlow(202602, 1, TradeType.INCOME, 200),
        )
        val ui = buildYearBalances(flows, opening, 2026, 202603, mapOf(1L to Triple("A", null, 0)))!!
        assertEquals(listOf(202601, 202602, 202603), ui.months)
        assertEquals(listOf(1_200L, 1_400L, 1_400L), ui.monthly.single().values)
        assertEquals(listOf(1_200L, 1_400L), ui.share.single().values)
        assertEquals(listOf(2022, 2023, 2024, 2025, 2026), ui.years)
        assertEquals(listOf(1_000L, 1_500L, 1_500L, 1_200L, 1_400L), ui.yearly.single().values)
    }

    @Test
    fun `year categories run up through the year`() {
        val totals = listOf(
            CategoryMonthTotal(7, 202501, 40),
            CategoryMonthTotal(7, 202509, 999),
            CategoryMonthTotal(7, 202601, 10),
            CategoryMonthTotal(7, 202603, 5),
        )
        val ui = buildYearCategories(totals, 2026, 202603)!!
        assertEquals(listOf(10L, 10L, 15L), ui.monthly.single().values)
        // Last year's September is after March, so the pie compares with January..March only.
        assertEquals(listOf(40L, 15L), ui.share.single().values)
        assertEquals(listOf(0L, 0L, 0L, 1_039L, 15L), ui.yearly.single().values)
        assertNull(buildYearCategories(emptyList(), 2026, 202603))
    }

    @Test
    fun `year layout has every month section and at least four charts in each but Other`() {
        val year = LayoutSlot.YEAR.groups
        assertEquals(LayoutSlot.MONTH.groups.map { it.title }, year.map { it.title })
        assertEquals(LayoutSlot.MONTH.groups.map { g -> g.sub.map { it.title } }, year.map { g -> g.sub.map { it.title } })
        year.dropLast(1).forEach { group ->
            assert(group.allCharts().size >= 4)
            group.sub.forEach { assert(it.charts.size >= 4) }
        }
        assert(LayoutSlot.YEAR.offered.size >= 20)
        assertEquals(LayoutSlot.YEAR.offered, YearCharts.toSet())
        assertEquals(YearCharts.size, YearCharts.map { it.icon }.toSet().size)
        assert(YearCharts.all { it.base != null && it.base.base == null })
        assertEquals(YearCharts, decodeSlot(LayoutSlot.YEAR) { null }.map { it.type })
    }
}
