package app.outgo.ui.analysis

import app.outgo.data.db.dao.AccountBalance
import app.outgo.data.db.dao.AccountFlow
import app.outgo.data.db.dao.AccountMove
import app.outgo.data.db.dao.CategoryMonthTotal
import app.outgo.data.db.dao.FLOW_TRANSFER_IN
import app.outgo.data.db.dao.MonthCategoryTotal
import app.outgo.data.db.dao.TradeSlim
import app.outgo.domain.CategoryKind
import app.outgo.domain.TradeType
import app.outgo.util.MonthKey
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sign

/**
 * Pure aggregation for the Analysis screen: `category_month_stat` rows, raw trade rows and account
 * flows in, finished [SummaryUi]/[BalancesUi]/[CategoryChartsUi] out. No Android, no coroutines, no
 * Compose — the ViewModel calls these on [kotlinx.coroutines.Dispatchers.Default] and the unit tests
 * call them directly.
 *
 * Days are derived from `occurred_at` with the same zone [MonthKey] uses, so a trade never lands on
 * a day outside the `month_key` it was filed under.
 */

/** Neutral grey for a line whose account is unknown — readable in both themes. */
const val OTHER_COLOR = 0xFF9E9E9E.toInt()

fun monthKeyOf(epochMillis: Long, zone: ZoneId): Int {
    val d = Instant.ofEpochMilli(epochMillis).atZone(zone)
    return d.year * 100 + d.monthValue
}

fun yearMonthOf(monthKey: Int): YearMonth = YearMonth.of(monthKey / 100, monthKey % 100)

// ------------------------------------------------------------------- SUMMARY

/** The month summary for [month]. [totals] must cover at least the month before it; extra months are ignored. */
fun buildSummary(
    totals: List<MonthCategoryTotal>,
    month: Int,
    zone: ZoneId = ZoneId.systemDefault(),
    nowMillis: Long = System.currentTimeMillis(),
): Stage<SummaryUi> {
    val byMonth = totals.groupBy { it.monthKey }
    val current = byMonth[month].orEmpty()
    if (current.isEmpty()) return Stage.Empty

    val previous = byMonth[MonthKey.minus(month, 1)].orEmpty()
    val expense = kindSummary(
        current.filter { it.type == CategoryKind.EXPENSE }, previous.filter { it.type == CategoryKind.EXPENSE }, daysCounted(month, zone, nowMillis),
    )
    val income = kindSummary(
        current.filter { it.type == CategoryKind.INCOME }, previous.filter { it.type == CategoryKind.INCOME }, daysCounted(month, zone, nowMillis),
    )
    return Stage.Ready(SummaryUi(expense, income, net = income.total - expense.total, prevNet = income.prevTotal - expense.prevTotal))
}

/** Days the month's average divides by: elapsed so far for the live month, else its full length. */
internal fun daysCounted(month: Int, zone: ZoneId, nowMillis: Long): Int {
    val ym = yearMonthOf(month)
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    return if (YearMonth.from(today) == ym) today.dayOfMonth else ym.lengthOfMonth()
}

/**
 * Percent change against [previous]; null with no baseline (a change from 0 has no percent, and
 * showing one, like +100%, would be made up). Divides by |previous| so a net that was negative
 * still reads "+" when it grew.
 */
internal fun percentChange(current: Long, previous: Long): Int? =
    if (previous == 0L) null else ((current - previous) * 100 / abs(previous)).toInt()

internal fun kindSummary(current: List<MonthCategoryTotal>, previous: List<MonthCategoryTotal>, periods: Int): KindSummary {
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

// ------------------------------------------------------------------ ACCOUNTS

/** What a flow row does to its account's balance — the same signs as the balance triggers. */
private fun signed(flow: AccountFlow): Long = when (flow.type) {
    TradeType.INCOME, TradeType.ADJUST_IN, FLOW_TRANSFER_IN -> flow.total
    else -> -flow.total
}

/**
 * Each account's balance at the end of every month of [trend]: [opening] is every balance at the
 * start of `trend.first()`, and [flows] must cover [trend]. Accounts that sit at 0 all along are
 * left out; [accounts] is in the user's account order.
 */
fun buildBalanceTrend(
    flows: List<AccountFlow>,
    opening: List<AccountBalance>,
    trend: List<Int>,
    accounts: Map<Long, Triple<String, Long?, Int>>,
): BalanceTrendUi {
    val start = opening.associate { it.accountId to it.balance }
    val monthly = flows.groupBy { it.accountId }
        .mapValues { (_, group) -> group.groupBy { it.monthKey }.mapValues { (_, m) -> m.sumOf(::signed) } }
    val order = accounts.keys.withIndex().associate { (i, id) -> id to i }
    val lines = (start.keys + monthly.keys).sortedBy { order[it] ?: Int.MAX_VALUE }.mapNotNull { id ->
        var running = start[id] ?: 0L
        val values = trend.map { month ->
            running += monthly[id]?.get(month) ?: 0L
            running
        }
        val account = accounts[id]
        if (values.all { it == 0L }) null else BalanceLine(id, account?.first.orEmpty(), account?.third ?: OTHER_COLOR, values, iconId = account?.second)
    }
    return BalanceTrendUi(trend, lines)
}

/**
 * Every account's end-of-day balance through [month]. [opening] is every balance at the start of
 * some earlier month, and [flows] must cover the months from there up to (not including) [month];
 * [moves] are [month]'s own balance changes. Accounts that sit at 0 all month are left out.
 *
 * An account with nothing before [month] (none of its money, no history) didn't exist yet: its
 * line starts on its first move's day (see [BalanceLine.start]), so an opening balance entered then
 * is where the line begins, not a jump up from a made-up 0.
 */
fun buildDailyBalance(
    opening: List<AccountBalance>,
    flows: List<AccountFlow>,
    moves: List<AccountMove>,
    month: Int,
    accounts: Map<Long, Triple<String, Long?, Int>>,
    zone: ZoneId,
    nowMillis: Long = System.currentTimeMillis(),
): DailyBalanceUi {
    val daysInMonth = yearMonthOf(month).lengthOfMonth()
    val lastDay = daysCounted(month, zone, nowMillis).coerceAtMost(daysInMonth)
    val start = HashMap<Long, Long>()
    opening.forEach { start[it.accountId] = it.balance }
    // Accounts with any history before this month: they existed, even at 0.
    val before = HashSet<Long>()
    flows.filter { it.monthKey < month }.forEach {
        start[it.accountId] = (start[it.accountId] ?: 0L) + signed(it)
        before += it.accountId
    }
    val perDay = HashMap<Long, LongArray>()
    val firstDay = HashMap<Long, Int>()
    moves.forEach { move ->
        val day = Instant.ofEpochMilli(move.occurredAt).atZone(zone).dayOfMonth
        perDay.getOrPut(move.accountId) { LongArray(daysInMonth) }[day - 1] += move.delta
        firstDay[move.accountId] = minOf(firstDay[move.accountId] ?: day, day)
    }
    val order = accounts.keys.withIndex().associate { (i, id) -> id to i }
    val lines = (start.keys + perDay.keys).sortedBy { order[it] ?: Int.MAX_VALUE }.mapNotNull { id ->
        var running = start[id] ?: 0L
        val days = perDay[id]
        val isNew = running == 0L && id !in before
        val from = if (isNew) ((firstDay[id] ?: 1) - 1).coerceAtMost(lastDay) else 0
        val values = (0 until lastDay).map { day ->
            running += days?.get(day) ?: 0L
            running
        }.drop(from)
        val account = accounts[id]
        if (values.all { it == 0L }) null else BalanceLine(id, account?.first.orEmpty(), account?.third ?: OTHER_COLOR, values, iconId = account?.second, start = from)
    }
    return DailyBalanceUi(daysInMonth, lines)
}

/** The [low]..[high] a value axis spans, and the tick values inside it. */
data class ValueAxis(val low: Double, val high: Double, val ticks: List<Long>)

/**
 * Value axis for data spanning [min]..[max] on a plot [heightPx] tall. The range is the data
 * padded by a tenth of its span each side; with [zero] it always takes in 0, and an end that sits
 * on 0 is not padded past it. Ticks sit on the smallest 1/2/5 x 10^k step that keeps them at
 * least [minGapPx] apart. No ticks until the plot has a height. Without [pad] the range is the data exactly,
 * unless the data is flat.
 */
fun valueAxis(min: Long, max: Long, heightPx: Float, minGapPx: Float, zero: Boolean, pad: Boolean = true): ValueAxis {
    val dataMin = (if (zero) minOf(min, 0L) else min).toDouble()
    val dataMax = (if (zero) maxOf(max, 0L) else max).toDouble()
    val delta = (dataMax - dataMin).takeIf { it > 0.0 } ?: abs(dataMax).takeIf { it > 0.0 } ?: 1.0
    // A flat line still needs a span, so it is padded either way.
    val margin = if (!pad && dataMax > dataMin) 0.0 else delta / 10
    val low = if (zero && dataMin == 0.0) 0.0 else dataMin - margin
    val high = if (zero && dataMax == 0.0 && dataMin < 0.0) 0.0 else dataMax + margin
    if (heightPx <= 0f) return ValueAxis(low, high, emptyList())
    val span = high - low
    // Amounts are whole minor units, so the step never goes below 1.
    var scale = 10.0.pow(floor(log10(span)) - 3).toLong().coerceAtLeast(1L)
    var index = 0
    var step = scale
    // Long overflows past ~9.2e18; no real amount reaches it, but stop rather than wrap.
    while (step / span * heightPx < minGapPx && scale < Long.MAX_VALUE / 100) {
        index++
        if (index == NiceBases.size) {
            index = 0
            scale *= 10
        }
        step = NiceBases[index] * scale
    }
    val first = ceil(low / step).toLong() * step
    return ValueAxis(low, high, generateSequence(first) { it + step }.takeWhile { it <= high }.toList())
}

private val NiceBases = longArrayOf(1, 2, 5)

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

// ------------------------------------------------------------ account balances

/** Months the last-months balance bars show, the page's own month last. */
const val BALANCE_MONTH_COUNT = 5

/**
 * What an account balance chart draws: each parent account adding up itself and its subaccounts,
 * or with [subaccounts] the subaccounts of [parentId] (the first parent when it is null or gone).
 * Leaves that are archived are not in [tree], so like the Accounts tab they don't count.
 */
fun balanceGroups(tree: List<AccountNode>, parentId: Long?, subaccounts: Boolean): List<Pair<AccountNode, List<Long>>> =
    if (!subaccounts) {
        tree.map { parent -> parent to (parent.children.map { it.id } + parent.id) }
    } else {
        (tree.firstOrNull { it.id == parentId } ?: tree.firstOrNull())?.children.orEmpty().map { it to listOf(it.id) }
    }

/**
 * One line per group of [groups], [length] points long: the sum of its leaves' lines in [lines].
 * A leaf without a line sat at 0 all along (its line was dropped for that), and a line that stops
 * short (the live month stops at today) counts nothing after its end. A line that starts late (see
 * [BalanceLine.start]) counts from its start, and the group's line starts with its earliest leaf.
 */
fun sumLines(lines: List<BalanceLine>, groups: List<Pair<AccountNode, List<Long>>>, length: Int): List<BalanceLine> {
    val byId = lines.associateBy { it.accountId }
    return groups.map { (node, leaves) ->
        val values = LongArray(length)
        val own = leaves.mapNotNull { byId[it] }
        own.forEach { line -> line.values.forEachIndexed { i, v -> if (line.start + i < length) values[line.start + i] += v } }
        val from = (own.minOfOrNull { it.start } ?: 0).coerceIn(0, length)
        BalanceLine(node.id, node.name, node.color, values.asList().drop(from), iconId = node.iconId, start = from)
    }
}

/**
 * The pie's rows from month-end [lines] (as [sumLines] makes them, over [months]): each account's
 * balance at the end of the last month and its change since the end of the month before, largest
 * first. Slices share the positive balances; an account at or below 0 is a row without a slice.
 */
fun buildShare(lines: List<BalanceLine>, nodes: Map<Long, AccountNode>): List<ShareRow> {
    val rows = lines.map { line ->
        val amount = line.values.lastOrNull() ?: 0L
        val previous = line.values.getOrNull(line.values.size - 2) ?: 0L
        line to (amount to previous)
    }.sortedByDescending { it.second.first }
    val total = rows.sumOf { it.second.first.coerceAtLeast(0L) }
    var angle = -90f
    return rows.map { (line, values) ->
        val (amount, previous) = values
        val share = if (amount > 0 && total > 0) amount.toFloat() / total else 0f
        val row = ShareRow(
            id = line.accountId,
            name = line.name,
            iconId = nodes[line.accountId]?.iconId,
            color = line.color,
            amount = amount,
            delta = amount - previous,
            deltaPercent = percentChange(amount, previous),
            share = if (amount > 0) share else null,
            startAngle = angle,
            sweepAngle = share * 360f,
        )
        angle += row.sweepAngle
        row
    }
}

// ------------------------------------------------------------------ categories

/**
 * The Categories group's data for [month]: [rows] are the trades (expense and income, a transfer
 * from a budget counted as its category's expense) of at least that month, [totals] the stored
 * category totals of the last [BALANCE_MONTH_COUNT] months. Only categories with money show up.
 */
fun buildCategoryCharts(
    rows: List<TradeSlim>,
    totals: List<CategoryMonthTotal>,
    month: Int,
    zone: ZoneId,
    nowMillis: Long = System.currentTimeMillis(),
): CategoryChartsUi {
    val daysInMonth = yearMonthOf(month).lengthOfMonth()
    val lastDay = daysCounted(month, zone, nowMillis).coerceAtMost(daysInMonth)
    val perDay = HashMap<Long, LongArray>()
    val perDayCount = HashMap<Long, LongArray>()
    rows.forEach { row ->
        val id = row.categoryId ?: return@forEach
        val at = Instant.ofEpochMilli(row.occurredAt).atZone(zone)
        if (at.year * 100 + at.monthValue != month) return@forEach
        perDay.getOrPut(id) { LongArray(daysInMonth) }[at.dayOfMonth - 1] += row.amount
        perDayCount.getOrPut(id) { LongArray(daysInMonth) }[at.dayOfMonth - 1] += 1
    }
    fun runningLines(perId: Map<Long, LongArray>) = perId.map { (id, days) ->
        var running = 0L
        BalanceLine(id, "", OTHER_COLOR, (0 until lastDay).map { day -> running += days[day]; running })
    }
    val months = MonthKey.lastN(month, BALANCE_MONTH_COUNT)
    val index = months.withIndex().associate { (i, m) -> m to i }
    val perMonth = HashMap<Long, LongArray>()
    val perMonthCount = HashMap<Long, LongArray>()
    totals.forEach { total ->
        val i = index[total.monthKey] ?: return@forEach
        perMonth.getOrPut(total.categoryId) { LongArray(months.size) }[i] += total.total
        perMonthCount.getOrPut(total.categoryId) { LongArray(months.size) }[i] += total.tradeCount.toLong()
    }
    fun monthLines(perId: Map<Long, LongArray>) = perId.map { (id, values) -> BalanceLine(id, "", OTHER_COLOR, values.asList()) }
    return CategoryChartsUi(daysInMonth, runningLines(perDay), months, monthLines(perMonth), runningLines(perDayCount), monthLines(perMonthCount))
}
