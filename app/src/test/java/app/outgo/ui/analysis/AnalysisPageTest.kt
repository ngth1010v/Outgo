package app.outgo.ui.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Page order and caret rules — §3 of `docs/analysis-v2-plan.md`. */
class AnalysisPageTest {

    private val pages = buildPages(earliestMonth = 202511, currentMonth = 202603)

    @Test
    fun `a past year contributes all twelve months then its summary`() {
        // Data starts in November 2025, but the year still shows January onwards (D7).
        val of2025 = pages.filter { it.year == 2025 }
        assertEquals(13, of2025.size)
        assertEquals(AnalysisPage.Month(202501), of2025.first())
        assertEquals(AnalysisPage.Month(202512), of2025[11])
        assertEquals(AnalysisPage.Year(2025), of2025.last())
    }

    @Test
    fun `the current year stops at the current month, then its summary`() {
        val of2026 = pages.filter { it.year == 2026 }
        assertEquals(
            listOf(
                AnalysisPage.Month(202601),
                AnalysisPage.Month(202602),
                AnalysisPage.Month(202603),
                AnalysisPage.Year(2026),
            ),
            of2026,
        )
        assertEquals(AnalysisPage.Year(2026), pages.last())
    }

    @Test
    fun `swiping right off the last month of a year reaches that year's summary`() {
        val december = pages.indexOf(AnalysisPage.Month(202512))
        assertEquals(AnalysisPage.Year(2025), pages[december + 1])
    }

    @Test
    fun `swiping left off January reaches the previous year's summary`() {
        val january = pages.indexOf(AnalysisPage.Month(202601))
        assertEquals(AnalysisPage.Year(2025), pages[january - 1])
    }

    @Test
    fun `the first page has nothing to its left`() {
        assertEquals(AnalysisPage.Month(202501), pages.first())
    }

    @Test
    fun `a year caret keeps the month it was on`() {
        val february2026 = pages.indexOf(AnalysisPage.Month(202602))
        val target = yearStepTarget(pages, february2026, -1)
        assertEquals(AnalysisPage.Month(202502), pages[target!!])
    }

    @Test
    fun `a year caret moves summary to summary`() {
        val yearly2026 = pages.indexOf(AnalysisPage.Year(2026))
        val target = yearStepTarget(pages, yearly2026, -1)
        assertEquals(AnalysisPage.Year(2025), pages[target!!])
    }

    @Test
    fun `a year caret falls back to the last page of a year that has no such month`() {
        // November 2026 has not happened, so the forward caret lands on the 2026 summary.
        val november2025 = pages.indexOf(AnalysisPage.Month(202511))
        val target = yearStepTarget(pages, november2025, 1)
        assertEquals(AnalysisPage.Year(2026), pages[target!!])
    }

    @Test
    fun `a year caret is disabled when the target year has no pages`() {
        assertNull(yearStepTarget(pages, pages.indexOf(AnalysisPage.Month(202501)), -1))
        assertNull(yearStepTarget(pages, pages.indexOf(AnalysisPage.Year(2026)), 1))
    }

    @Test
    fun `page keys are distinct primitives, safe to put in a Bundle`() {
        // Pager and lazy-layout keys are written into a Bundle, so they must not be data classes.
        val keys = pages.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertEquals(202603, AnalysisPage.Month(202603).key)
        // A year page can never collide with a month: months run 01..12, never 00.
        assertEquals(202600, AnalysisPage.Year(2026).key)
    }

    @Test
    fun `the now button points at today's month`() {
        assertEquals(AnalysisPage.Month(202603), pages[currentMonthIndex(pages, 202603)])
    }

    @Test
    fun `a single-month history still has a year page`() {
        val only = buildPages(earliestMonth = 202603, currentMonth = 202603)
        assertEquals(
            listOf(AnalysisPage.Month(202601), AnalysisPage.Month(202602), AnalysisPage.Month(202603), AnalysisPage.Year(2026)),
            only,
        )
    }

    @Test
    fun `data newer than the current month never creates future pages`() {
        val guarded = buildPages(earliestMonth = 202612, currentMonth = 202603)
        assertEquals(AnalysisPage.Year(2026), guarded.last())
        assertEquals(4, guarded.size)
    }
}
