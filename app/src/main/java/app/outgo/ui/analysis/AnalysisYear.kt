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
 * - [average]: what the "average per month" pies divide, see [YearAverageUi]; categories only.
 */
@Immutable
data class YearLinesUi(
    val months: List<Int>,
    val monthly: List<BalanceLine>,
    val share: List<BalanceLine>,
    val years: List<Int>,
    val yearly: List<BalanceLine>,
    val average: YearAverageUi? = null,
)

/**
 * The year's category totals for the "average per month" pies, which a card divides by the months
 * that had any trade of its kind (see [averageLines]); the kind is only known from the card's tree.
 * - [lines]: three points per category: the whole year before, this year without [live], and [live].
 * - [masks]: per category, bit `m - 1` set for each month m with money: the year before, then this
 *   year without [live].
 * - [live]: the month still under way (it is left out unless it is the only month with data), or null.
 */
@Immutable
data class YearAverageUi(val lines: List<BalanceLine>, val masks: Map<Long, Pair<Int, Int>>, val live: Int?)

/**
 * [average]'s lines for the categories [ids] of one kind as two points, the year before and this
 * year, each its total over the months with data of that kind. With [tenths] the values are
 * tenths (a trade count averages 2.5), else as stored. [lines] must be [average]'s lines summed
 * the way the card draws them (see [sumLines] with 3 points).
 */
fun averageLines(average: YearAverageUi, lines: List<BalanceLine>, ids: Set<Long>, tenths: Boolean): List<BalanceLine> {
    var before = 0
    var current = 0
    var live = false
    average.masks.forEach { (id, masks) ->
        if (id in ids) {
            before = before or masks.first
            current = current or masks.second
        }
    }
    if (average.live != null) {
        live = current == 0 && average.lines.any { it.accountId in ids && it.values[2] != 0L }
    }
    val beforeMonths = Integer.bitCount(before)
    val currentMonths = if (live) 1 else Integer.bitCount(current)
    val scale = if (tenths) 10 else 1
    fun avg(total: Long, months: Int) = if (months == 0) 0L else Math.round(total.toDouble() * scale / months)
    return lines.map { line ->
        val now = if (live) line.values[2] else line.values[1]
        line.copy(values = listOf(avg(line.values[0], beforeMonths), avg(now, currentMonths)))
    }
}

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
fun buildYearSummary(totals: List<MonthCategoryTotal>, year: Int, lastMonth: Int, live: Boolean = false): SummaryUi? {
    val current = totals.filter { it.monthKey / 100 == year && it.monthKey <= lastMonth }
    if (current.isEmpty()) return null
    val previous = totals.filter { it.monthKey / 100 == year - 1 && it.monthKey % 100 <= lastMonth % 100 }
    val liveMonth = lastMonth.takeIf { live }
    /**
     * The months [rows] have money in, the live one left out unless it is the only one: an empty
     * month, or one only begun, would pull the average per month down.
     */
    fun monthsWithData(rows: List<MonthCategoryTotal>): Int {
        val months = rows.filter { it.total != 0L }.mapTo(HashSet()) { it.monthKey }
        val done = months - setOfNotNull(liveMonth)
        return if (done.isEmpty()) months.size else done.size
    }
    fun summary(kind: Int): KindSummary {
        val rows = current.filter { it.type == kind }
        val done = rows.filter { it.monthKey != liveMonth }
        val months = monthsWithData(rows)
        // The average counts the months it divides by: without the live month when that is left out.
        val averaged = if (done.any { it.total != 0L }) done else rows
        return kindSummary(rows, previous.filter { it.type == kind }, months).copy(
            perPeriod = if (months > 0) averaged.sumOf { it.total } / months else 0L,
        )
    }
    val expense = summary(CategoryKind.EXPENSE)
    val income = summary(CategoryKind.INCOME)
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
    /** [lastMonth] is still under way: the averages leave it out. */
    live: Boolean = false,
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
    val liveMonth = lastMonth.takeIf { live && it / 100 == year }
    val averageRows = byCategory.mapValues { (_, rows) -> rows.filter { it.monthKey / 100 == year - 1 || it.monthKey / 100 == year } }
    val average = YearAverageUi(
        lines = averageRows.map { (id, rows) ->
            line(
                id,
                listOf(
                    rows.filter { it.monthKey / 100 == year - 1 }.sumOf(value),
                    rows.filter { it.monthKey / 100 == year && it.monthKey != liveMonth }.sumOf(value),
                    rows.filter { it.monthKey == liveMonth }.sumOf(value),
                ),
            )
        },
        masks = averageRows.mapValues { (_, rows) ->
            fun mask(of: (CategoryMonthTotal) -> Boolean) = rows.filter(of).fold(0) { bits, it -> bits or (1 shl (it.monthKey % 100 - 1)) }
            mask { it.monthKey / 100 == year - 1 } to mask { it.monthKey / 100 == year && it.monthKey != liveMonth }
        },
        live = liveMonth,
    )
    return YearLinesUi(months, monthly, share, years, yearly, average)
}
