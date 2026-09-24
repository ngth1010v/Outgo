package app.outgo.ui.analysis

import app.outgo.data.db.dao.MonthCategoryTotal
import app.outgo.data.db.dao.TradeSlim
import app.outgo.domain.CategoryKind
import app.outgo.util.MonthKey
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs

/**
 * Pure aggregation for the Analysis screen: `category_month_stat` rows and raw trade rows in,
 * finished [StatsData]/[TradesData] out. No Android, no coroutines, no Compose — the ViewModel
 * calls these on [kotlinx.coroutines.Dispatchers.Default] and the unit tests call them directly.
 *
 * Days and weekdays are derived from `occurred_at` with the same zone [MonthKey] uses, so a
 * trade never lands on a day outside the `month_key` it was filed under.
 */

/** Slices past this count are summed into one "Other" row (plus the "Other" row itself). */
const val TOP_SLICE_COUNT = 6
const val TREND_MONTH_COUNT = 6
const val MOVER_COUNT = 5
const val LARGEST_COUNT = 5

/** Neutral grey for the "Other" slice — not a category color, and readable in both themes. */
const val OTHER_COLOR = 0xFF9E9E9E.toInt()

fun monthKeyOf(epochMillis: Long, zone: ZoneId): Int {
    val d = Instant.ofEpochMilli(epochMillis).atZone(zone)
    return d.year * 100 + d.monthValue
}

fun yearMonthOf(monthKey: Int): YearMonth = YearMonth.of(monthKey / 100, monthKey % 100)

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

    val prevMonth = MonthKey.minus(month, 1)
    val previous = byMonth[prevMonth].orEmpty()

    fun ofKind(rows: List<MonthCategoryTotal>, kind: Int) = rows.filter { it.type == kind }
    val curExpense = ofKind(current, CategoryKind.EXPENSE)
    val prevExpense = ofKind(previous, CategoryKind.EXPENSE)
    val curIncome = ofKind(current, CategoryKind.INCOME)
    val prevIncome = ofKind(previous, CategoryKind.INCOME)

    val expenseTotal = curExpense.sumOf { it.total }
    val prevExpenseTotal = prevExpense.sumOf { it.total }
    val incomeTotal = curIncome.sumOf { it.total }

    val days = daysCounted(month, zone, nowMillis)
    val summary = SummaryUi(
        total = expenseTotal,
        prevTotal = prevExpenseTotal,
        deltaAmount = expenseTotal - prevExpenseTotal,
        deltaPercent = percentChange(expenseTotal, prevExpenseTotal),
        avgPerDay = if (days > 0) expenseTotal / days else 0L,
        income = incomeTotal,
        net = incomeTotal - expenseTotal,
    )

    return Stage.Ready(
        StatsData(
            summary = summary,
            expense = buildSliceSet(curExpense, prevExpense, otherName),
            income = buildSliceSet(curIncome, prevIncome, otherName),
            trend = buildTrend(byMonth, month),
            movers = buildMovers(curExpense, prevExpense),
        ),
    )
}

/** Days the month's average-per-day divides by: elapsed so far for the live month, else its full length. */
internal fun daysCounted(month: Int, zone: ZoneId, nowMillis: Long): Int {
    val ym = yearMonthOf(month)
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    return if (YearMonth.from(today) == ym) today.dayOfMonth else ym.lengthOfMonth()
}

/** Percent change, or null when there is no baseline to compare against. */
internal fun percentChange(current: Long, previous: Long): Int? =
    if (previous == 0L) null else ((current - previous) * 100 / previous).toInt()

private fun buildSliceSet(
    current: List<MonthCategoryTotal>,
    previous: List<MonthCategoryTotal>,
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

private fun buildTrend(byMonth: Map<Int, List<MonthCategoryTotal>>, month: Int): TrendUi {
    val months = MonthKey.lastN(month, TREND_MONTH_COUNT)
    val amounts = months.map { m ->
        byMonth[m].orEmpty().filter { it.type == CategoryKind.EXPENSE }.sumOf { it.total }
    }
    val max = amounts.max().coerceAtLeast(1L)
    val average = amounts.sum() / months.size
    return TrendUi(
        bars = months.mapIndexed { i, m ->
            TrendBar(m, amounts[i], amounts[i].toFloat() / max, selected = m == month)
        },
        average = average,
        averageFraction = average.toFloat() / max,
    )
}

private fun buildMovers(current: List<MonthCategoryTotal>, previous: List<MonthCategoryTotal>): List<MoverRow> {
    val prevByRoot = previous.associateBy { it.rootId }
    val curByRoot = current.associateBy { it.rootId }
    // Categories that had spend last month and none this month are a full decrease, so the
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
        MoverRow(rootId, row.name, row.iconId, row.color, delta, abs(delta).toFloat() / max)
    }
}

// -------------------------------------------------------------------- TRADES

/**
 * Sections 4, 7, 9 and 10 in a single pass over [rows], which must be the EXPENSE trades of
 * [month] and the month before it (one query, see `amountsAndTimesForMonths`).
 * [weekdayRows] is the separate 3-month fetch for section 8.
 */
fun buildTrades(
    rows: List<TradeSlim>,
    weekdayRows: List<TradeSlim>,
    month: Int,
    categoryNames: Map<Long, Triple<String, Long?, Int>>,
    zone: ZoneId = ZoneId.systemDefault(),
    nowMillis: Long = System.currentTimeMillis(),
): Stage<TradesData> {
    val ym = yearMonthOf(month)
    val prevYm = ym.minusMonths(1)
    val daysInMonth = ym.lengthOfMonth()
    val daily = LongArray(daysInMonth)
    val prevDaily = LongArray(prevYm.lengthOfMonth())
    val currentAmounts = ArrayList<Long>(rows.size)
    var largest = ArrayList<TradeSlim>(rows.size)

    for (row in rows) {
        val date = Instant.ofEpochMilli(row.occurredAt).atZone(zone).toLocalDate()
        when (YearMonth.from(date)) {
            ym -> {
                daily[date.dayOfMonth - 1] += row.amount
                currentAmounts += row.amount
                largest += row
            }
            prevYm -> prevDaily[date.dayOfMonth - 1] += row.amount
            else -> Unit // a trade whose month_key disagrees with its timestamp; ignore it
        }
    }

    if (currentAmounts.isEmpty()) return Stage.Empty

    largest = ArrayList(largest.sortedByDescending { it.amount }.take(LARGEST_COUNT))

    return Stage.Ready(
        TradesData(
            pace = buildPace(daily, prevDaily, month, zone, nowMillis),
            heatmap = buildHeatmap(daily, ym, zone),
            weekday = buildWeekday(weekdayRows, month, zone, nowMillis),
            buckets = buildBuckets(currentAmounts),
            largest = largest.map { row ->
                val category = row.categoryId?.let { categoryNames[it] }
                LargestItem(
                    tradeId = row.id,
                    name = category?.first.orEmpty(),
                    iconId = category?.second,
                    color = category?.third ?: OTHER_COLOR,
                    amount = row.amount,
                    occurredAt = row.occurredAt,
                    note = row.note,
                )
            },
        ),
    )
}

internal fun buildPace(
    daily: LongArray,
    prevDaily: LongArray,
    month: Int,
    zone: ZoneId,
    nowMillis: Long,
): PaceUi {
    // The live month's line stops at today instead of flat-lining to the end of the month.
    val lastDay = daysCounted(month, zone, nowMillis).coerceAtMost(daily.size)
    val current = ArrayList<Float>(lastDay)
    val previous = ArrayList<Float>(prevDaily.size)
    var running = 0L
    val currentTotals = LongArray(lastDay)
    for (i in 0 until lastDay) {
        running += daily[i]
        currentTotals[i] = running
    }
    val currentTotal = running
    running = 0L
    val prevTotals = LongArray(prevDaily.size)
    for (i in prevDaily.indices) {
        running += prevDaily[i]
        prevTotals[i] = running
    }
    val max = maxOf(currentTotal, running).coerceAtLeast(1L)
    currentTotals.forEach { current += it.toFloat() / max }
    prevTotals.forEach { previous += it.toFloat() / max }
    return PaceUi(current, previous, daily.size, max, currentTotal)
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
