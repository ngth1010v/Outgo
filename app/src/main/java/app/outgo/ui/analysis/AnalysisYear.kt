package app.outgo.ui.analysis

import androidx.compose.runtime.Immutable
import app.outgo.data.db.dao.AccountBalance
import app.outgo.data.db.dao.AccountFlow
import app.outgo.data.db.dao.CategoryMonthTotal
import app.outgo.data.db.dao.MonthCategoryTotal
import app.outgo.domain.CategoryKind

/**
 * The year page's own data: the summary and the Accounts and Categories groups. Pure, like
 * [AnalysisAggregate]. The Trades, Savings and Budgets groups reuse the month builders over the
 * year's months (see `AnalysisViewModel.buildInsight`).
 */

/** Years the "last years" bars show, the page's own year last. */
const val YEAR_COUNT = 5

/** Every month from [from] to [to], both included. */
fun monthsBetween(from: Int, to: Int): List<Int> {
    val months = ArrayList<Int>()
    var m = from
    while (m <= to) {
        months += m
        m = if (m % 100 == 12) (m / 100 + 1) * 100 + 1 else m + 1
    }
    return months
}

/** The last month of [year] that has happened by [currentMonth]: December, or the current month. */
fun lastMonthOf(year: Int, currentMonth: Int): Int = minOf(year * 100 + 12, currentMonth)

/**
 * One Accounts or Categories group of a year page, one line per account (category) as stored; a
 * card adds them up per [AccountNode] with [sumLines].
 * - [monthly]: a point per month of [months] (the year so far).
 * - [share]: two points, the year before and this year, for [buildShare].
 * - [yearly]: a point per year of [years], this one last.
 */
@Immutable
data class YearLinesUi(
    val months: List<Int>,
    val monthly: List<BalanceLine>,
    val share: List<BalanceLine>,
    val years: List<Int>,
    val yearly: List<BalanceLine>,
)

/** A year page's own data; a null group had nothing to show. */
@Immutable
data class YearUi(
    val summary: SummaryUi?,
    val transfers: Long,
    val balances: YearLinesUi?,
    val categories: YearLinesUi?,
    /** [categories] counting trades instead of money. */
    val categoryCounts: YearLinesUi? = null,
)

/**
 * The year's totals against the same months of the year before (the whole year once it is over),
 * averaged per month so far. [totals] must cover both years. Null when the year has nothing.
 */
fun buildYearSummary(totals: List<MonthCategoryTotal>, year: Int, lastMonth: Int): SummaryUi? {
    val current = totals.filter { it.monthKey / 100 == year && it.monthKey <= lastMonth }
    if (current.isEmpty()) return null
    val previous = totals.filter { it.monthKey / 100 == year - 1 && it.monthKey % 100 <= lastMonth % 100 }
    val months = lastMonth % 100
    val expense = kindSummary(current.filter { it.type == CategoryKind.EXPENSE }, previous.filter { it.type == CategoryKind.EXPENSE }, months)
    val income = kindSummary(current.filter { it.type == CategoryKind.INCOME }, previous.filter { it.type == CategoryKind.INCOME }, months)
    return SummaryUi(expense, income, net = income.total - expense.total, prevNet = income.prevTotal - expense.prevTotal)
}

/**
 * Month-end balances for [year] up to [lastMonth]. [opening] is every balance at the start of the
 * first of the [YEAR_COUNT] years, and [flows] cover them up to [lastMonth]. Null with no balances.
 */
fun buildYearBalances(
    flows: List<AccountFlow>,
    opening: List<AccountBalance>,
    year: Int,
    lastMonth: Int,
    accounts: Map<Long, Triple<String, Long?, Int>>,
): YearLinesUi? {
    val trend = monthsBetween((year - YEAR_COUNT + 1) * 100 + 1, lastMonth)
    val lines = buildBalanceTrend(flows, opening, trend, accounts).lines
    if (lines.isEmpty()) return null
    val index = trend.withIndex().associate { (i, m) -> m to i }
    // Before the first month of the trend every balance is its opening one.
    val start = opening.associate { it.accountId to it.balance }
    fun BalanceLine.at(month: Int): Long = index[month]?.let { values[it] } ?: (start[accountId] ?: 0L)
    val months = monthsBetween(year * 100 + 1, lastMonth)
    val years = (year - YEAR_COUNT + 1..year).toList()
    return YearLinesUi(
        months,
        lines.map { line -> line.copy(values = months.map { line.at(it) }) },
        lines.map { line -> line.copy(values = listOf(line.at((year - 1) * 100 + 12), line.at(lastMonth))) },
        years,
        lines.map { line -> line.copy(values = years.map { line.at(minOf(it * 100 + 12, lastMonth)) }) },
    )
}

/**
 * Category totals for [year] up to [lastMonth]: year-to-date month by month, this year against the
 * same months of the year before, and the last [YEAR_COUNT] years. [totals] must cover those years.
 * Null when no category has money in them. [value] picks what is added up: the money, or the trade count.
 */
fun buildYearCategories(
    totals: List<CategoryMonthTotal>,
    year: Int,
    lastMonth: Int,
    value: (CategoryMonthTotal) -> Long = { it.total },
): YearLinesUi? {
    val inRange = totals.filter { it.monthKey <= lastMonth && value(it) != 0L }
    if (inRange.isEmpty()) return null
    val months = monthsBetween(year * 100 + 1, lastMonth)
    val years = (year - YEAR_COUNT + 1..year).toList()
    val byCategory = inRange.groupBy { it.categoryId }
    fun line(id: Long, values: List<Long>) = BalanceLine(id, "", OTHER_COLOR, values)
    val monthly = byCategory.map { (id, rows) ->
        val perMonth = rows.groupBy { it.monthKey }.mapValues { (_, r) -> r.sumOf(value) }
        var running = 0L
        line(id, months.map { m -> running += perMonth[m] ?: 0L; running })
    }
    val share = byCategory.map { (id, rows) ->
        val previous = rows.filter { it.monthKey / 100 == year - 1 && it.monthKey % 100 <= lastMonth % 100 }.sumOf(value)
        line(id, listOf(previous, rows.filter { it.monthKey / 100 == year }.sumOf(value)))
    }
    val yearly = byCategory.map { (id, rows) ->
        val perYear = rows.groupBy { it.monthKey / 100 }.mapValues { (_, r) -> r.sumOf(value) }
        line(id, years.map { perYear[it] ?: 0L })
    }
    return YearLinesUi(months, monthly, share, years, yearly)
}
