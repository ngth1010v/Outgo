package app.outgo.ui.analysis

import app.outgo.data.db.dao.MonthCategoryTotal
import app.outgo.data.db.dao.TradeSlim
import app.outgo.data.db.dao.TransferTotal
import app.outgo.domain.CategoryKind
import app.outgo.domain.TradeType
import app.outgo.util.MonthKey
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs

/**
 * Pure aggregation for the Analysis screen: `category_month_stat` rows, raw trade rows and transfer
 * totals in, finished [StatsData]/[TradesData]/[YearStatsData]/[YearTradesData] out. No Android, no
 * coroutines, no Compose — the ViewModel calls these on [kotlinx.coroutines.Dispatchers.Default]
 * and the unit tests call them directly.
 *
 * Days and weekdays are derived from `occurred_at` with the same zone [MonthKey] uses, so a trade
 * never lands on a day outside the `month_key` it was filed under.
 */

/** Slices past this count are summed into one "Other" row (plus the "Other" row itself). */
const val TOP_SLICE_COUNT = 6
const val TREND_MONTH_COUNT = 6
const val MOVER_COUNT = 5
const val LARGEST_COUNT = 5
const val TRANSFER_PAIR_COUNT = 5

/** Neutral grey for the "Other" slice — not a category color, and readable in both themes. */
const val OTHER_COLOR = 0xFF9E9E9E.toInt()

fun monthKeyOf(epochMillis: Long, zone: ZoneId): Int {
    val d = Instant.ofEpochMilli(epochMillis).atZone(zone)
    return d.year * 100 + d.monthValue
}

fun yearMonthOf(monthKey: Int): YearMonth = YearMonth.of(monthKey / 100, monthKey % 100)

fun monthsOfYear(year: Int): List<Int> = (1..12).map { year * 100 + it }

// --------------------------------------------------------------------- STATS

/**
 * Sections 1, 2, 3, 5 and 6 for [month]. [totals] must cover at least
 * `month - (TREND_MONTH_COUNT - 1) … month`; extra months are ignored.
 */
fun buildStats(
    totals: List<MonthCategoryTotal>,
    month: Int,
    otherName: String,
    zone: ZoneId = ZoneId.systemDefault(),
    nowMillis: Long = System.currentTimeMillis(),
): Stage<StatsData> {
    val byMonth = totals.groupBy { it.monthKey }
    val current = byMonth[month].orEmpty()
    if (current.isEmpty()) return Stage.Empty

    val previous = byMonth[MonthKey.minus(month, 1)].orEmpty()
    val curExpense = current.filter { it.type == CategoryKind.EXPENSE }
    val prevExpense = previous.filter { it.type == CategoryKind.EXPENSE }
    val curIncome = current.filter { it.type == CategoryKind.INCOME }
    val prevIncome = previous.filter { it.type == CategoryKind.INCOME }

    val days = daysCounted(month, zone, nowMillis)
    return Stage.Ready(
        StatsData(
            summary = summaryOf(curExpense, prevExpense, curIncome, prevIncome, days),
            expense = buildSliceSet(curExpense, prevExpense, CategoryKind.EXPENSE, otherName),
            income = buildSliceSet(curIncome, prevIncome, CategoryKind.INCOME, otherName),
            bars = buildBars(byMonth, MonthKey.lastN(month, TREND_MONTH_COUNT), month),
            expenseMovers = buildMovers(curExpense, prevExpense, CategoryKind.EXPENSE),
            incomeMovers = buildMovers(curIncome, prevIncome, CategoryKind.INCOME),
            allMovers = buildMovers(current, previous, null),
        ),
    )
}

/** Sections Y1–Y4 for [year]. [totals] must cover `year-1 Jan … year Dec`. */
fun buildYearStats(
    totals: List<MonthCategoryTotal>,
    year: Int,
    otherName: String,
    zone: ZoneId = ZoneId.systemDefault(),
    nowMillis: Long = System.currentTimeMillis(),
): Stage<YearStatsData> {
    val byMonth = totals.groupBy { it.monthKey }
    val months = monthsOfYear(year)
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val partial = today.year == year
    // A partial year is compared against the same months of last year, never against 12.
    val monthsCounted = if (partial) today.monthValue else 12
    val counted = months.take(monthsCounted)
    val previousCounted = monthsOfYear(year - 1).take(monthsCounted)

    val current = counted.flatMap { byMonth[it].orEmpty() }
    if (current.isEmpty()) return Stage.Empty
    val previous = previousCounted.flatMap { byMonth[it].orEmpty() }

    val curExpense = current.filter { it.type == CategoryKind.EXPENSE }
    val prevExpense = previous.filter { it.type == CategoryKind.EXPENSE }
    val curIncome = current.filter { it.type == CategoryKind.INCOME }
    val prevIncome = previous.filter { it.type == CategoryKind.INCOME }

    // Categories are summed across the year before the top-6 cut, so one row per category.
    fun merge(rows: List<MonthCategoryTotal>): List<MonthCategoryTotal> =
        rows.groupBy { it.rootId }.map { (_, group) -> group.first().copy(total = group.sumOf { it.total }) }

    val expenseByMonth = counted.map { m -> m to byMonth[m].orEmpty().filter { it.type == CategoryKind.EXPENSE }.sumOf { it.total } }
    val nonEmpty = expenseByMonth.filter { it.second > 0L }

    return Stage.Ready(
        YearStatsData(
            summary = YearSummaryUi(
                summary = summaryOf(curExpense, prevExpense, curIncome, prevIncome, monthsCounted),
                monthsCounted = monthsCounted,
                partial = partial,
                biggestMonth = nonEmpty.maxByOrNull { it.second }?.first,
                biggestAmount = nonEmpty.maxOfOrNull { it.second } ?: 0L,
                smallestMonth = nonEmpty.minByOrNull { it.second }?.first,
                smallestAmount = nonEmpty.minOfOrNull { it.second } ?: 0L,
            ),
            expense = buildSliceSet(merge(curExpense), merge(prevExpense), CategoryKind.EXPENSE, otherName),
            income = buildSliceSet(merge(curIncome), merge(prevIncome), CategoryKind.INCOME, otherName),
            bars = buildBars(byMonth, months, selected = null),
            yoy = buildYoy(byMonth, year, monthsCounted),
        ),
    )
}

/** Days the month's average divides by: elapsed so far for the live month, else its full length. */
internal fun daysCounted(month: Int, zone: ZoneId, nowMillis: Long): Int {
    val ym = yearMonthOf(month)
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    return if (YearMonth.from(today) == ym) today.dayOfMonth else ym.lengthOfMonth()
}

/** Percent change, or null when there is no baseline to compare against. */
internal fun percentChange(current: Long, previous: Long): Int? =
    if (previous == 0L) null else ((current - previous) * 100 / previous).toInt()

private fun kindSummary(current: List<MonthCategoryTotal>, previous: List<MonthCategoryTotal>, periods: Int): KindSummary {
    val total = current.sumOf { it.total }
    val prev = previous.sumOf { it.total }
    return KindSummary(
        total = total,
        prevTotal = prev,
        deltaAmount = total - prev,
        deltaPercent = percentChange(total, prev),
        perPeriod = if (periods > 0) total / periods else 0L,
    )
}

private fun summaryOf(
    curExpense: List<MonthCategoryTotal>,
    prevExpense: List<MonthCategoryTotal>,
    curIncome: List<MonthCategoryTotal>,
    prevIncome: List<MonthCategoryTotal>,
    periods: Int,
): SummaryUi {
    val expense = kindSummary(curExpense, prevExpense, periods)
    val income = kindSummary(curIncome, prevIncome, periods)
    return SummaryUi(
        expense = expense,
        income = income,
        net = income.total - expense.total,
        prevNet = income.prevTotal - expense.prevTotal,
    )
}

private fun buildSliceSet(
    current: List<MonthCategoryTotal>,
    previous: List<MonthCategoryTotal>,
    kind: Int,
    otherName: String,
): SliceSet {
    val total = current.sumOf { it.total }
    if (total == 0L) return SliceSet(DonutUi(emptyList(), 0L), emptyList())

    val prevByRoot = previous.associate { it.rootId to it.total }
    val sorted = current.sortedByDescending { it.total }
    val top = sorted.take(TOP_SLICE_COUNT)
    val rest = sorted.drop(TOP_SLICE_COUNT)

    val rows = ArrayList<BreakdownRow>(top.size + 1)
    top.forEach { row ->
        val prev = prevByRoot[row.rootId] ?: 0L
        rows += BreakdownRow(
            rootId = row.rootId,
            kind = kind,
            name = row.name,
            iconId = row.iconId,
            color = row.color,
            amount = row.total,
            sharePercent = row.total * 100f / total,
            deltaAmount = row.total - prev,
            deltaPercent = percentChange(row.total, prev),
        )
    }
    if (rest.isNotEmpty()) {
        val amount = rest.sumOf { it.total }
        val prev = rest.sumOf { prevByRoot[it.rootId] ?: 0L }
        rows += BreakdownRow(
            rootId = null,
            kind = kind,
            name = otherName,
            iconId = null,
            color = OTHER_COLOR,
            amount = amount,
            sharePercent = amount * 100f / total,
            deltaAmount = amount - prev,
            deltaPercent = percentChange(amount, prev),
        )
    }

    // Angles accumulate here, once, rather than in the draw lambda.
    var start = -90f
    val slices = rows.map { row ->
        val fraction = row.amount.toFloat() / total
        val sweep = fraction * 360f
        DonutSlice(row.rootId, row.color, row.amount, fraction, start, sweep).also { start += sweep }
    }
    return SliceSet(DonutUi(slices, total), rows)
}

/**
 * Bars for [months]. Expense and income share one scale so the diverging All-mode chart is
 * readable; [selected] highlights one bar, or null on the year page.
 */
private fun buildBars(byMonth: Map<Int, List<MonthCategoryTotal>>, months: List<Int>, selected: Int?): BarsUi {
    fun sum(month: Int, kind: Int) = byMonth[month].orEmpty().filter { it.type == kind }.sumOf { it.total }
    val expense = months.map { sum(it, CategoryKind.EXPENSE) }
    val income = months.map { sum(it, CategoryKind.INCOME) }
    val max = (expense + income).max().coerceAtLeast(1L)
    val expenseAverage = expense.sum() / months.size
    val incomeAverage = income.sum() / months.size
    return BarsUi(
        bars = months.mapIndexed { i, m ->
            MonthBar(
                monthKey = m,
                expense = expense[i],
                income = income[i],
                expenseFraction = expense[i].toFloat() / max,
                incomeFraction = income[i].toFloat() / max,
                selected = m == selected,
            )
        },
        expenseAverage = expenseAverage,
        incomeAverage = incomeAverage,
        expenseAverageFraction = expenseAverage.toFloat() / max,
        incomeAverageFraction = incomeAverage.toFloat() / max,
    )
}

/** Cumulative month-by-month totals for [year] against the same months of the year before. */
private fun buildYoy(byMonth: Map<Int, List<MonthCategoryTotal>>, year: Int, monthsCounted: Int): YoyUi {
    val expense = rawYoy(byMonth, year, monthsCounted, CategoryKind.EXPENSE)
    val income = rawYoy(byMonth, year, monthsCounted, CategoryKind.INCOME)
    // Both kinds and both years share one axis.
    val max = maxOf(expense.max(), income.max()).coerceAtLeast(1L)
    return YoyUi(expense = expense.toSeries(max), income = income.toSeries(max), maxTotal = max)
}

private class YoySeriesRaw(
    val current: List<Long>,
    val previous: List<Long>,
    val currentTotal: Long,
    val previousTotal: Long,
) {
    fun max(): Long = maxOf(currentTotal, previousTotal)
    fun toSeries(max: Long): YoySeries = YoySeries(
        current = current.map { it.toFloat() / max },
        previous = previous.map { it.toFloat() / max },
        currentTotal = currentTotal,
        previousTotal = previousTotal,
    )
}

private fun rawYoy(byMonth: Map<Int, List<MonthCategoryTotal>>, year: Int, monthsCounted: Int, kind: Int): YoySeriesRaw {
    fun cumulative(months: List<Int>): List<Long> {
        var running = 0L
        return months.map { m ->
            running += byMonth[m].orEmpty().filter { it.type == kind }.sumOf { it.total }
            running
        }
    }
    val current = cumulative(monthsOfYear(year).take(monthsCounted))
    val previous = cumulative(monthsOfYear(year - 1))
    return YoySeriesRaw(current, previous, current.lastOrNull() ?: 0L, previous.lastOrNull() ?: 0L)
}

/** Top movers by amount moved. [kind] null means both kinds compete in one list. */
private fun buildMovers(
    current: List<MonthCategoryTotal>,
    previous: List<MonthCategoryTotal>,
    kind: Int?,
): List<MoverRow> {
    val prevByRoot = previous.associateBy { it.rootId }
    val curByRoot = current.associateBy { it.rootId }
    // Categories that had a total last month and none this month are a full decrease, so the
    // union of both months is the candidate set — not just this month's.
    val deltas = (curByRoot.keys + prevByRoot.keys).mapNotNull { rootId ->
        val row = curByRoot[rootId] ?: prevByRoot.getValue(rootId)
        val delta = (curByRoot[rootId]?.total ?: 0L) - (prevByRoot[rootId]?.total ?: 0L)
        if (delta == 0L) null else Triple(row, rootId, delta)
    }
    // Sorted by amount moved, never by percent: a 300% jump on a tiny category is noise.
    val top = deltas.sortedByDescending { abs(it.third) }.take(MOVER_COUNT)
    val max = top.maxOfOrNull { abs(it.third) }?.coerceAtLeast(1L) ?: return emptyList()
    return top.map { (row, rootId, delta) ->
        MoverRow(rootId, kind ?: row.type, row.name, row.iconId, row.color, delta, abs(delta).toFloat() / max)
    }
}

// -------------------------------------------------------------------- TRADES

/**
 * Sections 4, 7, 9, 10 and 11 in a single pass over [rows], which must be the EXPENSE and INCOME
 * trades of [month] and the month before it (one query, see `amountsAndTimesForMonths`).
 * [weekdayRows] is the 3-month window for section 8; [transfers] is the separate transfer
 * aggregate for section 11.
 */
fun buildTrades(
    rows: List<TradeSlim>,
    weekdayRows: List<TradeSlim>,
    transfers: List<TransferTotal>,
    month: Int,
    categories: Map<Long, Triple<String, Long?, Int>>,
    accountNames: Map<Long, String>,
    zone: ZoneId = ZoneId.systemDefault(),
    nowMillis: Long = System.currentTimeMillis(),
): Stage<TradesData> {
    val ym = yearMonthOf(month)
    val prevYm = ym.minusMonths(1)
    val daily = LongArray(ym.lengthOfMonth())
    val prevDaily = LongArray(prevYm.lengthOfMonth())
    val incomeDaily = LongArray(ym.lengthOfMonth())
    val incomePrevDaily = LongArray(prevYm.lengthOfMonth())
    val expenseAmounts = ArrayList<Long>(rows.size)
    val currentRows = ArrayList<TradeSlim>(rows.size)

    for (row in rows) {
        val date = Instant.ofEpochMilli(row.occurredAt).atZone(zone).toLocalDate()
        val expense = row.type == TradeType.EXPENSE
        when (YearMonth.from(date)) {
            ym -> {
                if (expense) {
                    daily[date.dayOfMonth - 1] += row.amount
                    expenseAmounts += row.amount
                } else {
                    incomeDaily[date.dayOfMonth - 1] += row.amount
                }
                currentRows += row
            }
            // Only the pace line looks at the previous month.
            prevYm -> if (expense) prevDaily[date.dayOfMonth - 1] += row.amount else incomePrevDaily[date.dayOfMonth - 1] += row.amount
            else -> Unit // a trade whose month_key disagrees with its timestamp; ignore it
        }
    }

    val transfersUi = buildTransfers(transfers, accountNames)
    if (currentRows.isEmpty() && transfersUi.count == 0) return Stage.Empty

    return Stage.Ready(
        TradesData(
            pace = buildPace(daily, prevDaily, incomeDaily, incomePrevDaily, month, zone, nowMillis),
            heatmap = buildHeatmap(daily, ym, zone),
            weekday = buildWeekday(weekdayRows, month, zone, nowMillis),
            buckets = buildBuckets(expenseAmounts),
            expenseLargest = largestOf(currentRows, TradeType.EXPENSE, categories),
            incomeLargest = largestOf(currentRows, TradeType.INCOME, categories),
            allLargest = largestOf(currentRows, null, categories),
            transfers = transfersUi,
        ),
    )
}

/** Sections Y5 and Y6: the year's transfers and its biggest movements. */
fun buildYearTrades(
    rows: List<TradeSlim>,
    transfers: List<TransferTotal>,
    year: Int,
    categories: Map<Long, Triple<String, Long?, Int>>,
    accountNames: Map<Long, String>,
    zone: ZoneId = ZoneId.systemDefault(),
): Stage<YearTradesData> {
    val months = monthsOfYear(year).toSet()
    val inYear = rows.filter { monthKeyOf(it.occurredAt, zone) in months }
    val transfersUi = buildTransfers(transfers, accountNames)
    if (inYear.isEmpty() && transfersUi.count == 0) return Stage.Empty
    return Stage.Ready(
        YearTradesData(
            transfers = transfersUi,
            expenseLargest = largestOf(inYear, TradeType.EXPENSE, categories),
            incomeLargest = largestOf(inYear, TradeType.INCOME, categories),
            allLargest = largestOf(inYear, null, categories),
        ),
    )
}

/** Biggest [LARGEST_COUNT] movements; [type] null means expense and income compete together. */
internal fun largestOf(
    rows: List<TradeSlim>,
    type: Int?,
    categories: Map<Long, Triple<String, Long?, Int>>,
): List<LargestItem> = rows
    .filter { type == null || it.type == type }
    .sortedByDescending { it.amount }
    .take(LARGEST_COUNT)
    .map { row ->
        val category = row.categoryId?.let { categories[it] }
        LargestItem(
            tradeId = row.id,
            type = row.type,
            name = category?.first.orEmpty(),
            iconId = category?.second,
            color = category?.third ?: OTHER_COLOR,
            amount = row.amount,
            occurredAt = row.occurredAt,
            note = row.note,
        )
    }

internal fun buildTransfers(totals: List<TransferTotal>, accountNames: Map<Long, String>): TransfersUi {
    val total = totals.sumOf { it.total }
    val max = totals.maxOfOrNull { it.total }?.coerceAtLeast(1L) ?: 1L
    return TransfersUi(
        pairs = totals.sortedByDescending { it.total }.take(TRANSFER_PAIR_COUNT).map { row ->
            TransferPair(
                fromName = accountNames[row.fromAccountId].orEmpty(),
                toName = row.toAccountId?.let { accountNames[it] }.orEmpty(),
                total = row.total,
                count = row.count,
                fraction = row.total.toFloat() / max,
            )
        },
        total = total,
        count = totals.sumOf { it.count },
    )
}

internal fun buildPace(
    daily: LongArray,
    prevDaily: LongArray,
    incomeDaily: LongArray,
    incomePrevDaily: LongArray,
    month: Int,
    zone: ZoneId,
    nowMillis: Long,
): PaceUi {
    // The live month's line stops at today instead of flat-lining to the end of the month.
    val lastDay = daysCounted(month, zone, nowMillis).coerceAtMost(daily.size)
    val expenseCurrent = runningTotals(daily, lastDay)
    val expensePrevious = runningTotals(prevDaily, prevDaily.size)
    val incomeCurrent = runningTotals(incomeDaily, lastDay)
    val incomePrevious = runningTotals(incomePrevDaily, incomePrevDaily.size)

    // Each kind keeps its own peak so a single-kind view fills the chart; All mode uses both.
    fun series(current: List<Long>, previous: List<Long>) = PaceSeries(
        current = current,
        previous = previous,
        currentTotal = current.lastOrNull() ?: 0L,
        max = maxOf(current.lastOrNull() ?: 0L, previous.lastOrNull() ?: 0L).coerceAtLeast(1L),
    )

    val expense = series(expenseCurrent, expensePrevious)
    val income = series(incomeCurrent, incomePrevious)
    return PaceUi(
        expense = expense,
        income = income,
        daysInMonth = daily.size,
        combinedMax = maxOf(expense.max, income.max),
    )
}

/**
 * Value-axis ticks for [max]: 0 and every multiple of a 1/2/5 x 10^k step, aiming for about
 * [targetSteps] gaps. Pure so the chart draws a list rather than deriving one per frame.
 */
fun valueTicks(max: Long, targetSteps: Int = 4): List<Long> {
    if (max <= 0L) return listOf(0L)
    val step = niceValueStep(max, targetSteps)
    return generateSequence(0L) { it + step }.takeWhile { it <= max }.toList()
}

/** Smallest 1/2/5 x 10^k step that splits [max] into at most [targetSteps] gaps. */
internal fun niceValueStep(max: Long, targetSteps: Int): Long {
    val base = longArrayOf(1, 2, 5)
    var step = 1L
    var index = 0
    var power = 0
    while (max / step > targetSteps) {
        index++
        if (index == base.size) {
            index = 0
            power++
        }
        // Long overflows past ~9.2e18; no real amount reaches it, but stop rather than wrap.
        if (power > 18) return step
        var scale = 1L
        repeat(power) { scale *= 10 }
        step = base[index] * scale
    }
    return step
}

/**
 * [count] day labels spread evenly across a month of [daysInMonth], always including day 1 and
 * the last day. The gaps are equal on screen; the labelled day is the one under each position.
 */
fun axisDays(daysInMonth: Int, count: Int = 5): List<Int> {
    if (daysInMonth <= 1 || count <= 1) return listOf(1)
    return (0 until count).map { i ->
        1 + Math.round((daysInMonth - 1).toFloat() * i / (count - 1))
    }
}

private fun runningTotals(daily: LongArray, days: Int): List<Long> {
    var running = 0L
    return (0 until days).map { i ->
        running += daily[i]
        running
    }
}

internal fun buildHeatmap(daily: LongArray, ym: YearMonth, zone: ZoneId): HeatmapUi {
    val max = daily.max()
    val first = ym.atDay(1)
    return HeatmapUi(
        cells = daily.mapIndexed { i, amount ->
            HeatCell(
                dayOfMonth = i + 1,
                startMillis = first.plusDays(i.toLong()).atStartOfDay(zone).toInstant().toEpochMilli(),
                amount = amount,
                intensity = if (max == 0L) 0f else amount.toFloat() / max,
            )
        },
        // Monday-first grid: a month starting on Thursday leaves 3 blank cells.
        leadingBlanks = first.dayOfWeek.value - 1,
        maxAmount = max,
    )
}

/**
 * Average expense per weekday over the 3 months ending at [month]. The divisor is how many of
 * that weekday actually occurred in the range (capped at today), NOT the number of trades — a
 * Monday with no spending still drags the Monday average down, which is the whole point.
 */
internal fun buildWeekday(rows: List<TradeSlim>, month: Int, zone: ZoneId, nowMillis: Long): List<WeekdayBar> {
    val months = MonthKey.lastN(month, 3)
    val first = yearMonthOf(months.first()).atDay(1)
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val last = minOf(yearMonthOf(months.last()).atEndOfMonth(), today)

    val totals = LongArray(8)
    val inRange = months.toSet()
    for (row in rows) {
        if (row.type != TradeType.EXPENSE) continue
        val date = Instant.ofEpochMilli(row.occurredAt).atZone(zone).toLocalDate()
        if (monthKeyOf(row.occurredAt, zone) in inRange && !date.isAfter(last)) {
            totals[date.dayOfWeek.value] += row.amount
        }
    }

    val counts = IntArray(8)
    var day: LocalDate = first
    while (!day.isAfter(last)) {
        counts[day.dayOfWeek.value]++
        day = day.plusDays(1)
    }

    val averages = (1..7).map { weekday -> if (counts[weekday] == 0) 0L else totals[weekday] / counts[weekday] }
    val max = averages.max().coerceAtLeast(1L)
    return (1..7).map { weekday -> WeekdayBar(weekday, averages[weekday - 1], averages[weekday - 1].toFloat() / max) }
}

/** Buckets relative to the month's median purchase: <0.5x, 0.5–2x, 2–5x, >5x. */
internal fun buildBuckets(amounts: List<Long>): BucketsUi {
    val sorted = amounts.sorted()
    val median = if (sorted.isEmpty()) {
        0L
    } else if (sorted.size % 2 == 1) {
        sorted[sorted.size / 2]
    } else {
        (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
    }
    val edges = longArrayOf(median / 2, median * 2, median * 5)
    val counts = IntArray(4)
    val sums = LongArray(4)
    for (amount in sorted) {
        val index = when {
            amount < edges[0] -> 0
            amount < edges[1] -> 1
            amount < edges[2] -> 2
            else -> 3
        }
        counts[index]++
        sums[index] += amount
    }
    val totalCount = sorted.size.coerceAtLeast(1)
    val totalAmount = sorted.sum().coerceAtLeast(1L)
    return BucketsUi(
        buckets = (0..3).map { i ->
            SizeBucket(
                from = if (i == 0) null else edges[i - 1],
                until = if (i == 3) null else edges[i],
                count = counts[i],
                amount = sums[i],
                countPercent = counts[i] * 100f / totalCount,
                moneyPercent = sums[i] * 100f / totalAmount,
            )
        },
        median = median,
    )
}
