package app.outgo.ui.analysis

import androidx.compose.runtime.Immutable

/**
 * The Analysis pager is one flat list of pages: every year contributes its months, then its own
 * year page.
 *
 * ```
 * [2025 Jan] … [2025 Dec] [2025 Yearly] | [2026 Jan] … [2026 Sep] [2026 Yearly]
 * ```
 *
 * So swiping right off a year's last month lands on that year's summary, and swiping left off
 * January lands on the previous year's summary. Every function here is pure, so the page order and
 * the caret rules are unit-testable without a device.
 */
@Immutable
sealed interface AnalysisPage {
    @Immutable
    data class Month(val monthKey: Int) : AnalysisPage

    @Immutable
    data class Year(val year: Int) : AnalysisPage
}

/**
 * Stable primitive id for this page, for pager/lazy-layout keys — those are written into a
 * Bundle, so a data class is rejected at runtime. `yyyyMM` for a month; `yyyy00` for a year page,
 * which no month can collide with because months run 01..12.
 */
val AnalysisPage.key: Int
    get() = when (this) {
        is AnalysisPage.Month -> monthKey
        is AnalysisPage.Year -> year * 100
    }

val AnalysisPage.year: Int
    get() = when (this) {
        is AnalysisPage.Month -> monthKey / 100
        is AnalysisPage.Year -> year
    }

/**
 * All pages from [earliestMonth] to [currentMonth], oldest first. Every year in range gets all 12
 * month pages (empty months show their empty state), except the current year, which stops at the
 * current month — there are no future pages.
 */
fun buildPages(earliestMonth: Int, currentMonth: Int): List<AnalysisPage> {
    val firstYear = minOf(earliestMonth, currentMonth) / 100
    val currentYear = currentMonth / 100
    val pages = ArrayList<AnalysisPage>((currentYear - firstYear + 1) * 13)
    for (year in firstYear..currentYear) {
        val lastMonth = if (year == currentYear) currentMonth % 100 else 12
        for (month in 1..lastMonth) pages += AnalysisPage.Month(year * 100 + month)
        pages += AnalysisPage.Year(year)
    }
    return pages
}

/**
 * Where a year caret lands: the same month one year away, or that year's summary when the current
 * page is itself a summary. Falls back to the last page of the target year when the same month
 * does not exist there (e.g. November of the current year has not happened yet). Null when the
 * target year has no pages at all, which is when the caret is disabled.
 */
fun yearStepTarget(pages: List<AnalysisPage>, currentIndex: Int, yearDelta: Int): Int? {
    val page = pages.getOrNull(currentIndex) ?: return null
    val targetYear = page.year + yearDelta
    if (pages.none { it.year == targetYear }) return null
    return when (page) {
        is AnalysisPage.Year -> pages.indexOf(AnalysisPage.Year(targetYear))
        is AnalysisPage.Month -> {
            val sameMonth = pages.indexOf(AnalysisPage.Month(targetYear * 100 + page.monthKey % 100))
            if (sameMonth >= 0) sameMonth else pages.indexOfLast { it.year == targetYear }
        }
    }
}

/** Index of the year summary page for [year], or -1. */
fun yearlyPageIndex(pages: List<AnalysisPage>, year: Int): Int = pages.indexOf(AnalysisPage.Year(year))

/** Index of today's month page, or -1 — the target of the "Now" button. */
fun currentMonthIndex(pages: List<AnalysisPage>, currentMonth: Int): Int =
    pages.indexOf(AnalysisPage.Month(currentMonth))
