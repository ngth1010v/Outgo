package app.outgo.ui.analysis

import androidx.compose.runtime.Immutable
import app.outgo.data.db.dao.AccountBalance
import app.outgo.data.db.dao.BudgetMoveRow
import app.outgo.data.db.dao.BudgetWithProgress
import app.outgo.data.db.dao.CategoryMonthStatRow
import app.outgo.data.db.dao.SavingsTradeRow
import app.outgo.data.db.dao.TradeSlim
import app.outgo.data.db.dao.TransferTotal
import app.outgo.data.db.entity.AccountEntity
import app.outgo.data.db.entity.SavingsMonthEntity
import app.outgo.domain.AccountPrivacy
import app.outgo.domain.AccountType
import app.outgo.domain.CategoryKind
import app.outgo.domain.SavingProgressHide
import app.outgo.domain.TradeType
import app.outgo.util.MonthKey
import java.time.Instant
import java.time.ZoneId

/**
 * The Trades, Savings and Budgets groups of the month page: models and the pure aggregation behind
 * them. Like [AnalysisAggregate], no Android, no coroutines, no Compose: the ViewModel builds
 * [TradesInsightUi], [SavingsInsightUi] and [BudgetsInsightUi] on a background dispatcher, and the
 * per-mode helpers ([weekGrid], [sizeBuckets], [topTrades]…) run inside `remember` blocks.
 */

/** Months the month-by-month charts of these groups show, the page's own month last. */
const val INSIGHT_MONTH_COUNT = 6

/** Months the savings history grid shows. */
const val SAVINGS_HISTORY_MONTH_COUNT = 12

/** The group a chart's data comes from; each one is loaded only while the list holds one of its charts. */
enum class InsightGroup { TRADES, SAVINGS, BUDGETS }

/** Whose id a [ChartType.pick] chart keeps in [ChartCard.accountId]. */
enum class Pick { SAVINGS, SAVINGS_BALANCE, BUDGET }

/** Something a chart draws a row or a line for: an account, or a budget's category. */
@Immutable
data class InsightRef(val id: Long, val name: String, val iconId: Long?, val color: Int)

// ===================================================================== TRADES

/** One day of the month: what went out and came in, and how many trades of each. */
@Immutable
data class DayTotals(val expense: Long, val income: Long, val expenseCount: Int, val incomeCount: Int)

/** One month of every trade's count and total, by kind, from `category_month_stat`. */
@Immutable
data class MonthActivity(val month: Int, val expenseCount: Int, val expenseTotal: Long, val incomeCount: Int, val incomeTotal: Long)

@Immutable
data class TradesInsightUi(
    val month: Int,
    val daysInMonth: Int,
    /** Monday = 0: where day 1 sits in its week row. */
    val firstWeekday: Int,
    /** Index 0 == the 1st. */
    val days: List<DayTotals>,
    /** The month's expenses (a transfer taken from a budget counted as one) and incomes. */
    val trades: List<TradeSlim>,
    /** [INSIGHT_MONTH_COUNT] months, the page's own last. */
    val activity: List<MonthActivity>,
    val transfers: List<TransferTotal>,
)

/** Which kinds a [AnalysisMode] chip takes in. */
fun AnalysisMode.takes(type: Int): Boolean = when (this) {
    AnalysisMode.EXPENSE -> type == TradeType.EXPENSE
    AnalysisMode.INCOME -> type == TradeType.INCOME
    AnalysisMode.ALL -> type == TradeType.EXPENSE || type == TradeType.INCOME
}

fun buildTradesInsight(
    trades: List<TradeSlim>,
    stats: List<CategoryMonthStatRow>,
    transfers: List<TransferTotal>,
    month: Int,
    zone: ZoneId,
    /** The months whose trades the page covers: [month] alone, or a year's. [days] is [month]'s only. */
    period: List<Int> = listOf(month),
    activityMonths: List<Int> = MonthKey.lastN(month, INSIGHT_MONTH_COUNT),
): TradesInsightUi {
    val ym = yearMonthOf(month)
    val length = ym.lengthOfMonth()
    val expense = LongArray(length)
    val income = LongArray(length)
    val expenseCount = IntArray(length)
    val incomeCount = IntArray(length)
    val inPeriod = trades.filter { monthKeyOf(it.occurredAt, zone) in period }
    inPeriod.filter { monthKeyOf(it.occurredAt, zone) == month }.forEach { t ->
        val day = Instant.ofEpochMilli(t.occurredAt).atZone(zone).dayOfMonth - 1
        if (t.type == TradeType.INCOME) {
            income[day] += t.amount
            incomeCount[day]++
        } else {
            expense[day] += t.amount
            expenseCount[day]++
        }
    }
    val activity = activityMonths.map { m ->
        val rows = stats.filter { it.monthKey == m }
        val e = rows.filter { it.type == CategoryKind.EXPENSE }
        val i = rows.filter { it.type == CategoryKind.INCOME }
        MonthActivity(m, e.sumOf { it.tradeCount }, e.sumOf { it.total }, i.sumOf { it.tradeCount }, i.sumOf { it.total })
    }
    return TradesInsightUi(
        month = month,
        daysInMonth = length,
        firstWeekday = ym.atDay(1).dayOfWeek.value - 1,
        days = (0 until length).map { DayTotals(expense[it], income[it], expenseCount[it], incomeCount[it]) },
        trades = inPeriod.sortedByDescending { it.amount },
        activity = activity,
        transfers = transfers.filter { it.toAccountId != null && it.total > 0 },
    )
}

/** A day's value under [mode]: expense or income as a positive amount, or with All the net (income - expense). */
fun DayTotals.value(mode: AnalysisMode): Long = when (mode) {
    AnalysisMode.EXPENSE -> expense
    AnalysisMode.INCOME -> income
    AnalysisMode.ALL -> income - expense
}

fun DayTotals.count(mode: AnalysisMode): Int = when (mode) {
    AnalysisMode.EXPENSE -> expenseCount
    AnalysisMode.INCOME -> incomeCount
    AnalysisMode.ALL -> expenseCount + incomeCount
}

/** Four-hour slots of the day the weekday grid has, 0h-4h first. */
const val DAY_SLOTS = 6

/** [WEEK_DAYS] rows (Monday first) of [DAY_SLOTS] cells: the amount and the count of the trades in each. */
@Immutable
data class WeekGridUi(val amounts: List<Long>, val counts: List<Int>) {
    val max: Long get() = amounts.maxOrNull() ?: 0L
    fun amount(weekday: Int, slot: Int) = amounts[weekday * DAY_SLOTS + slot]
    fun count(weekday: Int, slot: Int) = counts[weekday * DAY_SLOTS + slot]
}

const val WEEK_DAYS = 7

/** The month's trades of [mode]'s kinds, summed by weekday and four-hour slot. With All, both kinds add up. */
fun weekGrid(trades: List<TradeSlim>, mode: AnalysisMode, zone: ZoneId): WeekGridUi {
    val amounts = LongArray(WEEK_DAYS * DAY_SLOTS)
    val counts = IntArray(WEEK_DAYS * DAY_SLOTS)
    trades.forEach { t ->
        if (!mode.takes(t.type)) return@forEach
        val at = Instant.ofEpochMilli(t.occurredAt).atZone(zone)
        val i = (at.dayOfWeek.value - 1) * DAY_SLOTS + at.hour / (24 / DAY_SLOTS)
        amounts[i] += t.amount
        counts[i]++
    }
    return WeekGridUi(amounts.asList(), counts.asList())
}

/**
 * Trades bucketed by size: [edges] split them into `edges.size + 1` buckets (below the first edge,
 * between each pair, from the last edge up). [countShare]/[sumShare] are each bucket's share of
 * the trades and of the money, so both fit one 0..1 axis.
 */
@Immutable
data class SizeBucketsUi(
    val edges: List<Long>,
    val counts: List<Int>,
    val sums: List<Long>,
    val countShare: List<Float>,
    val sumShare: List<Float>,
)

/** Edges the size buckets pick from: 1, 3, 10, 30… whole units, as minor units. */
private val SizeLadder: List<Long> = generateSequence(1L) { it * 10 }.take(13).flatMap { listOf(it, it * 3) }
    .map { it * app.outgo.util.Money.SCALE }.toList()

/** How many edges the size chart uses: five edges, six buckets. */
private const val SIZE_EDGES = 5

/**
 * [trades] of [mode]'s kinds in six buckets on a 1-3-10 ladder of edges. The ladder starts at the
 * first edge above the smallest tenth of the trades, so most of them spread over the buckets
 * instead of crowding into one. Null when there are no trades of those kinds.
 */
fun sizeBuckets(trades: List<TradeSlim>, mode: AnalysisMode): SizeBucketsUi? {
    val amounts = trades.filter { mode.takes(it.type) && it.amount > 0 }.map { it.amount }.sorted()
    if (amounts.isEmpty()) return null
    val low = amounts[(amounts.size / 10).coerceAtMost(amounts.lastIndex)]
    val from = SizeLadder.indexOfFirst { it > low }.coerceAtLeast(0).coerceAtMost(SizeLadder.size - SIZE_EDGES)
    val edges = SizeLadder.subList(from, from + SIZE_EDGES)
    val counts = IntArray(SIZE_EDGES + 1)
    val sums = LongArray(SIZE_EDGES + 1)
    amounts.forEach { a ->
        val i = edges.indexOfFirst { a < it }.let { if (it < 0) SIZE_EDGES else it }
        counts[i]++
        sums[i] += a
    }
    val total = sums.sum().coerceAtLeast(1L)
    return SizeBucketsUi(
        edges = edges,
        counts = counts.asList(),
        sums = sums.asList(),
        countShare = counts.map { it.toFloat() / amounts.size },
        sumShare = sums.map { it.toFloat() / total },
    )
}

/** How many trades the largest-trades list shows. */
const val TOP_TRADE_COUNT = 10

/** The [TOP_TRADE_COUNT] largest trades of [mode]'s kinds; [trades] is already largest first. */
fun topTrades(trades: List<TradeSlim>, mode: AnalysisMode): List<TradeSlim> =
    trades.asSequence().filter { mode.takes(it.type) }.take(TOP_TRADE_COUNT).toList()

/** One month's bar and line values for the count + average chart. */
fun MonthActivity.count(mode: AnalysisMode): Int = when (mode) {
    AnalysisMode.EXPENSE -> expenseCount
    AnalysisMode.INCOME -> incomeCount
    AnalysisMode.ALL -> expenseCount + incomeCount
}

fun MonthActivity.total(mode: AnalysisMode): Long = when (mode) {
    AnalysisMode.EXPENSE -> expenseTotal
    AnalysisMode.INCOME -> incomeTotal
    AnalysisMode.ALL -> expenseTotal + incomeTotal
}

/** Most nodes a side of the transfer flow chart draws; the smaller ones beyond it join an "other" node. */
const val FLOW_NODES = 5

/** Id of a flow node standing for the accounts beyond [FLOW_NODES]. */
const val OTHER_NODE = -1L

@Immutable
data class FlowNode(val id: Long, val total: Long)

@Immutable
data class FlowLink(val from: Long, val to: Long, val total: Long, val count: Int)

/** The month's transfers as a two-sided flow: sending accounts left, receiving right, largest first. */
@Immutable
data class TransferFlowUi(val sources: List<FlowNode>, val targets: List<FlowNode>, val links: List<FlowLink>, val total: Long, val count: Int)

fun transferFlow(transfers: List<TransferTotal>): TransferFlowUi? {
    if (transfers.isEmpty()) return null
    fun keep(totals: Map<Long, Long>): Set<Long> {
        val sorted = totals.entries.sortedByDescending { it.value }
        return if (sorted.size <= FLOW_NODES) sorted.map { it.key }.toSet() else sorted.take(FLOW_NODES - 1).map { it.key }.toSet()
    }
    val fromKeep = keep(transfers.groupBy { it.fromAccountId }.mapValues { (_, l) -> l.sumOf { it.total } })
    val toKeep = keep(transfers.groupBy { it.toAccountId!! }.mapValues { (_, l) -> l.sumOf { it.total } })
    val links = transfers.groupBy { (if (it.fromAccountId in fromKeep) it.fromAccountId else OTHER_NODE) to (if (it.toAccountId in toKeep) it.toAccountId!! else OTHER_NODE) }
        .map { (key, l) -> FlowLink(key.first, key.second, l.sumOf { it.total }, l.sumOf { it.count }) }
    fun nodes(side: (FlowLink) -> Long) = links.groupBy(side).map { (id, l) -> FlowNode(id, l.sumOf { it.total }) }
        // "Other" always goes last, whatever its size.
        .sortedWith(compareBy<FlowNode> { it.id == OTHER_NODE }.thenByDescending { it.total })
    val sources = nodes { it.from }
    val targets = nodes { it.to }
    val order = { list: List<FlowNode> -> list.withIndex().associate { (i, n) -> n.id to i } }
    val fromOrder = order(sources)
    val toOrder = order(targets)
    return TransferFlowUi(
        sources,
        targets,
        // Drawn in node order on both sides, so the bands leave and arrive without crossing inside a node.
        links.sortedWith(compareBy<FlowLink> { fromOrder[it.from] }.thenBy { toOrder[it.to] }),
        links.sumOf { it.total },
        links.sumOf { it.count },
    )
}

// ==================================================================== SAVINGS

/** One savings account this month: what came in toward its target, the target, and where it should be by today. */
@Immutable
data class SavingPaceRow(
    val ref: InsightRef,
    val saved: Long,
    val target: Long,
    /** Where the saved amount should be by today, at an even pace (the whole target on a finished month). */
    val pace: Long,
    /** Where the month ends at the pace so far (the saved amount on a finished month). */
    val projected: Long,
)

/** A row of a month grid: one cell per month, null where there is nothing to count that month. */
@Immutable
data class GridCell(val value: Long, val goal: Long) {
    /** Share of the goal reached (saved / target, spent / limit); a goal of 0 counts any value as a full one. */
    val ratio: Float get() = if (goal > 0) value.toFloat() / goal else if (value > 0) 2f else 0f
}

@Immutable
data class GridRow(val ref: InsightRef, val cells: List<GridCell?>, val streak: Int, val misses: Int)

/** What a waterfall step is, which names it and decides its colour. */
enum class StepKind {
    OPENING, INCOME, TRANSFER_IN, LEFTOVER, NOT_COUNTED, SPENT, TRANSFER_OUT, ADJUST, CLOSING,
    LIMIT, CHILD_LIMITS, CARRY, FUNDED, EFFECTIVE, LEFT,
}

/** A [total] step is a bar from 0 to [amount]; any other step moves the running total by [amount]. */
@Immutable
data class WaterfallStep(val kind: StepKind, val amount: Long, val total: Boolean = false)

@Immutable
data class SavingsInsightUi(
    val month: Int,
    val daysInMonth: Int,
    /** Days of the month counted so far: today on the live month, else all of them. */
    val elapsed: Int,
    val pace: List<SavingPaceRow>,
    /** Running saved amount of each [pace] account per day, day 1 to [elapsed]. */
    val daily: Map<Long, List<Long>>,
    val historyMonths: List<Int>,
    val history: List<GridRow>,
    val rateMonths: List<Int>,
    /** Saved into every counted savings account per [rateMonths] month… */
    val saved: List<Long>,
    /** …and the month's income. */
    val income: List<Long>,
    /** Savings accounts whose balance can be shown, for the sources chart's picker. */
    val sourceAccounts: List<InsightRef>,
    val sources: Map<Long, List<WaterfallStep>>,
)

/** What the savings charts need of the accounts, built once per load. */
class SavingsBook(
    accounts: List<AccountEntity>,
    private val months: List<SavingsMonthEntity>,
) {
    private val byId = accounts.associateBy { it.id }
    private val children = accounts.groupBy { it.parentId }
    private val snapshots = months.groupBy { it.accountId }

    /** Every savings account, active or not: the trade query asks for all of their trades. */
    val savingsIds: List<Long> = accounts.filter { it.accountType == AccountType.SAVINGS }.map { it.id }

    /** The accounts whose income counts toward [id]'s target: itself and its subaccounts not left out. */
    fun countedGroup(id: Long): Set<Long> =
        (children[id].orEmpty().filter { !it.parentSavingExempt }.map { it.id } + id).toSet()

    /** The accounts [id]'s balance adds up: itself and every subaccount. */
    fun balanceGroup(id: Long): Set<Long> = (children[id].orEmpty().map { it.id } + id).toSet()

    private fun snapshot(id: Long, month: Int) = snapshots[id]?.lastOrNull { it.monthKey <= month }

    /** [id]'s savings target in [month], the way `AccountWithProgress.monthlyTarget` has it; null: none. */
    fun target(id: Long, month: Int): Long? {
        val account = byId[id] ?: return null
        if (account.accountType != AccountType.SAVINGS) return null
        val s = snapshot(id, month) ?: return null
        return when {
            s.enabled && s.sumChildren -> children[id].orEmpty()
                .filter { !it.archived && it.accountType == AccountType.SAVINGS && !it.parentSavingExempt }
                .sumOf { c -> snapshot(c.id, month)?.takeIf { it.enabled }?.target ?: 0L }
            s.enabled && s.target > 0 -> s.target
            else -> null
        }
    }

    fun account(id: Long): AccountEntity? = byId[id]
}

/** True when [trade] counts toward the target of the account whose counted group is [group]. */
private fun countsToward(trade: SavingsTradeRow, group: Set<Long>): Boolean = !trade.savingExempt && when (trade.type) {
    TradeType.INCOME -> trade.accountId in group
    TradeType.TRANSFER -> trade.toAccountId in group && trade.accountId !in group
    else -> false
}

/** Hidden from every multi-month savings chart: progress hidden anywhere, and not kept in Analysis. */
private fun AccountPrivacy?.hidesHistory(): Boolean = this != null && progressHide != SavingProgressHide.NEVER && !progressInAnalysis

/**
 * The Savings group for [month]. [trades] cover [SAVINGS_HISTORY_MONTH_COUNT] months up to it and
 * every account of [SavingsBook.savingsIds]; [opening] is every balance at the start of [month];
 * [incomeByMonth] the income total of each month. Only active accounts show; [privacy] decides
 * which progress may be shown, and a hidden balance keeps an account out of the sources chart.
 */
fun buildSavingsInsight(
    book: SavingsBook,
    active: List<AccountEntity>,
    privacy: Map<Long, AccountPrivacy>,
    trades: List<SavingsTradeRow>,
    opening: List<AccountBalance>,
    incomeByMonth: Map<Int, Long>,
    month: Int,
    zone: ZoneId,
    nowMillis: Long = System.currentTimeMillis(),
    /** The months the pace and sources charts add up: [month] alone, or a whole year. [opening] is at its start. */
    period: List<Int> = listOf(month),
    historyMonths: List<Int> = MonthKey.lastN(month, SAVINGS_HISTORY_MONTH_COUNT),
    rateMonths: List<Int> = MonthKey.lastN(month, INSIGHT_MONTH_COUNT),
): SavingsInsightUi {
    val now = MonthKey.of(nowMillis, zone)
    val length = period.sumOf { yearMonthOf(it).lengthOfMonth() }
    val elapsed = period.sumOf { m ->
        val days = yearMonthOf(m).lengthOfMonth()
        when {
            m < now -> days
            m == now -> daysCounted(m, zone, nowMillis).coerceAtMost(days)
            else -> 0
        }
    }
    val live = now in period
    val savings = active.filter { it.accountType == AccountType.SAVINGS }
    fun ref(a: AccountEntity) = InsightRef(a.id, a.name, a.iconId, a.color)
    val byMonth = trades.groupBy { it.monthKey }
    fun savedIn(id: Long, m: Int): Long {
        val group = book.countedGroup(id)
        return byMonth[m].orEmpty().filter { countsToward(it, group) }.sumOf { it.amount }
    }

    // This month's pace, one row per account with a target whose progress may show.
    val pace = ArrayList<SavingPaceRow>()
    val daily = HashMap<Long, List<Long>>()
    savings.forEach { a ->
        val targets = period.mapNotNull { book.target(a.id, it) }
        if (targets.isEmpty()) return@forEach
        val target = targets.sum()
        val saved = period.sumOf { savedIn(a.id, it) }
        if (privacy[a.id]?.progressHiddenInAnalysis(saved, target) == true) return@forEach
        pace += SavingPaceRow(
            ref(a),
            saved,
            target,
            pace = target * elapsed / length,
            projected = if (live && elapsed > 0) saved * length / elapsed else saved,
        )
        // Day by day only makes sense for one month (the burn-up chart).
        if (period.size != 1) return@forEach
        val group = book.countedGroup(a.id)
        val days = LongArray(elapsed.coerceAtLeast(1))
        byMonth[month].orEmpty().filter { countsToward(it, group) }.forEach { t ->
            val day = Instant.ofEpochMilli(t.occurredAt).atZone(zone).dayOfMonth
            days[day.coerceAtMost(days.size) - 1] += t.amount
        }
        var running = 0L
        daily[a.id] = days.map { running += it; running }
    }

    // Twelve months of target hits, for accounts that had a target in any of them.
    val history = savings.filter { !privacy[it.id].hidesHistory() }.mapNotNull { a ->
        val cells = historyMonths.map { m -> book.target(a.id, m)?.let { GridCell(savedIn(a.id, m), it) } }
        if (cells.all { it == null }) return@mapNotNull null
        GridRow(ref(a), cells, streak = streakOf(cells, live) { it.value >= it.goal }, misses = 0)
    }

    // The rate: every top savings level (no savings parent above it) counted once.
    val tops = savings.filter { a ->
        val parent = a.parentId?.let { book.account(it) }
        (parent == null || parent.accountType != AccountType.SAVINGS) && !privacy[a.id].hidesHistory()
    }
    val saved = rateMonths.map { m -> tops.sumOf { savedIn(it.id, m) } }
    val income = rateMonths.map { incomeByMonth[it] ?: 0L }

    // Where each shown balance's money came from and went this month.
    val start = opening.associate { it.accountId to it.balance }
    val sourceAccounts = savings.filter { privacy[it.id]?.hideBalance != true }
    val sources = sourceAccounts.associate { a ->
        val group = book.balanceGroup(a.id)
        val counted = book.countedGroup(a.id)
        val sums = LongArray(StepKind.entries.size)
        period.flatMap { byMonth[it].orEmpty() }.forEach { t ->
            val from = t.accountId in group
            val to = t.toAccountId in group
            if (!from && !to) return@forEach
            val kind = when (t.type) {
                TradeType.INCOME -> if (countsToward(t, counted)) StepKind.INCOME else StepKind.NOT_COUNTED
                TradeType.EXPENSE -> StepKind.SPENT
                TradeType.ADJUST_IN, TradeType.ADJUST_OUT -> StepKind.ADJUST
                TradeType.TRANSFER -> when {
                    from && to -> null
                    to && t.offsetMonth != null -> StepKind.LEFTOVER
                    to -> if (countsToward(t, counted)) StepKind.TRANSFER_IN else StepKind.NOT_COUNTED
                    else -> StepKind.TRANSFER_OUT
                }
                else -> null
            } ?: return@forEach
            val signed = when {
                kind == StepKind.ADJUST -> if (t.type == TradeType.ADJUST_IN) t.amount else -t.amount
                kind == StepKind.SPENT || kind == StepKind.TRANSFER_OUT -> -t.amount
                else -> t.amount
            }
            sums[kind.ordinal] += signed
        }
        val openingBalance = group.sumOf { start[it] ?: 0L }
        val moves = listOf(
            StepKind.INCOME, StepKind.TRANSFER_IN, StepKind.LEFTOVER, StepKind.NOT_COUNTED,
            StepKind.SPENT, StepKind.TRANSFER_OUT, StepKind.ADJUST,
        ).map { WaterfallStep(it, sums[it.ordinal]) }.filter { it.amount != 0L }
        a.id to (listOf(WaterfallStep(StepKind.OPENING, openingBalance, total = true)) + moves +
            WaterfallStep(StepKind.CLOSING, openingBalance + moves.sumOf { it.amount }, total = true))
    }

    return SavingsInsightUi(
        month, length, elapsed, pace, daily, historyMonths, history, rateMonths, saved, income,
        sourceAccounts.map(::ref), sources,
    )
}

/**
 * Months in a row that [hit], counted back from the last one. The live month only counts once it
 * is hit; a miss there doesn't break the streak, since the month isn't over.
 */
private fun streakOf(cells: List<GridCell?>, live: Boolean, hit: (GridCell) -> Boolean): Int {
    var streak = 0
    for (i in cells.indices.reversed()) {
        val cell = cells[i]
        val ok = cell != null && hit(cell)
        if (i == cells.lastIndex && live && !ok) continue
        if (!ok) break
        streak++
    }
    return streak
}

// ==================================================================== BUDGETS

/** One budget this month: what it spent, its limit after offsets and top-ups, and where it is heading. */
@Immutable
data class BudgetForecastRow(
    val ref: InsightRef,
    val spent: Long,
    val limit: Long,
    /** Where spending should be by today, at an even pace (the whole limit on a finished month). */
    val pace: Long,
    /** Where the month ends at the pace so far (the spent amount on a finished month). */
    val projected: Long,
)

/** One budget's month, day by day: what is left at the end of each day, day 1 to elapsed. */
@Immutable
data class BudgetDaysUi(val left: List<Long>, val limit: Long)

/** The days a budget went over its limit in each month of [BudgetsInsightUi.historyMonths]; null where it didn't. */
@Immutable
data class ReachedRow(val ref: InsightRef, val days: List<Int?>, val average: Int?)

@Immutable
data class BudgetsInsightUi(
    val month: Int,
    val daysInMonth: Int,
    val elapsed: Int,
    /** True while [month] is the live one: the forecast and burn-down project to its end. */
    val live: Boolean,
    /** Riskiest first: the furthest over its limit by the projection. */
    val forecast: List<BudgetForecastRow>,
    val daily: Map<Long, BudgetDaysUi>,
    val waterfall: Map<Long, List<WaterfallStep>>,
    val historyMonths: List<Int>,
    val history: List<GridRow>,
    /** Budgeted, unbudgeted and exempt spend over [historyMonths], as stacked lines. */
    val coverage: List<BalanceLine>,
    val reached: List<ReachedRow>,
)

/** Budgeted, unbudgeted and exempt spend: the coverage lines' ids, which name and colour them. */
const val COVER_IN = 1L
const val COVER_OUT = 2L
const val COVER_EXEMPT = 3L

private fun ref(b: BudgetWithProgress) = InsightRef(b.categoryId!!, b.displayName, b.displayIconId, b.categoryColor ?: OTHER_COLOR)

/**
 * Each budget's spend and limit over [months] added up, the furthest over first: a row per budget on
 * in any of them. The limit is each month's limit plus top-ups; what was carried over is left out,
 * since the month before already counted that money.
 */
fun budgetTotals(progress: Map<Int, List<BudgetWithProgress>>, months: List<Int>): List<BudgetForecastRow> {
    val rows = LinkedHashMap<Long, BudgetForecastRow>()
    months.reversed().forEach { m ->
        progress[m].orEmpty().filter { it.enabled && it.categoryId != null }.forEach { b ->
            val limit = b.effectiveLimit - b.carry
            val row = rows[b.categoryId!!]
            rows[b.categoryId] = if (row == null) {
                BudgetForecastRow(ref(b), b.spent, limit, 0L, b.spent)
            } else {
                row.copy(spent = row.spent + b.spent, limit = row.limit + limit, projected = row.projected + b.spent)
            }
        }
    }
    return rows.values.sortedByDescending { if (it.limit > 0) it.spent.toDouble() / it.limit else if (it.spent > 0) Double.MAX_VALUE else 0.0 }
}

/** Budgets that count a category's money: its own and, unless it is left out, its parent's. */
private fun budgetsOf(categoryId: Long, parentId: Long?, parentExempt: Boolean): List<Long> =
    if (parentId != null && !parentExempt) listOf(categoryId, parentId) else listOf(categoryId)

/**
 * The Budgets group for [month]. [progress] has every month of [INSIGHT_MONTH_COUNT] up to it,
 * [moves] their spend and top-ups, [stats] their category totals.
 */
fun buildBudgetsInsight(
    progress: Map<Int, List<BudgetWithProgress>>,
    moves: List<BudgetMoveRow>,
    stats: List<CategoryMonthStatRow>,
    month: Int,
    zone: ZoneId,
    nowMillis: Long = System.currentTimeMillis(),
    /** The month-by-month charts' months, [month] last; [progress], [moves] and [stats] must cover them. */
    historyMonths: List<Int> = MonthKey.lastN(month, INSIGHT_MONTH_COUNT),
): BudgetsInsightUi {
    val length = yearMonthOf(month).lengthOfMonth()
    val elapsed = daysCounted(month, zone, nowMillis).coerceAtMost(length)
    val live = MonthKey.of(nowMillis, zone) == month
    fun shown(m: Int) = progress[m].orEmpty().filter { it.enabled && it.categoryId != null }
    val current = shown(month)

    // Each budget's spend and top-ups per (month, day).
    val spentDays = HashMap<Pair<Long, Int>, LongArray>()
    val fundedDays = HashMap<Pair<Long, Int>, LongArray>()
    moves.forEach { row ->
        val days = yearMonthOf(row.monthKey).lengthOfMonth()
        val day = Instant.ofEpochMilli(row.occurredAt).atZone(zone).dayOfMonth.coerceAtMost(days)
        val into = if (row.funding) fundedDays else spentDays
        budgetsOf(row.categoryId, row.parentId, row.parentExempt).forEach { id ->
            into.getOrPut(id to row.monthKey) { LongArray(days) }[day - 1] += row.amount
        }
    }

    val forecast = current.map { b ->
        val limit = b.effectiveLimit
        BudgetForecastRow(
            ref(b),
            b.spent,
            limit,
            pace = limit.coerceAtLeast(0L) * elapsed / length,
            projected = if (live && elapsed > 0) b.spent * length / elapsed else b.spent,
        )
    }.sortedByDescending { if (it.limit > 0) it.projected.toDouble() / it.limit else if (it.projected > 0) Double.MAX_VALUE else 0.0 }

    val daily = current.associate { b ->
        val id = b.categoryId!!
        val spent = spentDays[id to month]
        val funded = fundedDays[id to month]
        var left = (b.limitAmount ?: 0L) + b.carry
        b.categoryId to BudgetDaysUi(
            (0 until elapsed.coerceAtLeast(1)).map { d ->
                left += (funded?.getOrNull(d) ?: 0L) - (spent?.getOrNull(d) ?: 0L)
                left
            },
            b.effectiveLimit,
        )
    }

    val waterfall = current.associate { b ->
        val base = b.limitAmount ?: 0L
        b.categoryId!! to listOfNotNull(
            WaterfallStep(if (b.sumChildren) StepKind.CHILD_LIMITS else StepKind.LIMIT, base, total = true),
            WaterfallStep(StepKind.CARRY, b.carry).takeIf { b.carry != 0L },
            WaterfallStep(StepKind.FUNDED, b.funded).takeIf { b.funded != 0L },
            WaterfallStep(StepKind.EFFECTIVE, b.effectiveLimit, total = true).takeIf { b.carry != 0L || b.funded != 0L },
            WaterfallStep(StepKind.SPENT, -b.spent),
            WaterfallStep(StepKind.LEFT, b.effectiveLimit - b.spent, total = true),
        )
    }

    // Every budget on in any of the months, in the order of the page's own month (then the rest).
    val seen = LinkedHashMap<Long, BudgetWithProgress>()
    (listOf(month) + historyMonths.reversed()).forEach { m -> shown(m).forEach { seen.putIfAbsent(it.categoryId!!, it) } }
    val byMonth = historyMonths.associateWith { m -> shown(m).associateBy { it.categoryId!! } }
    val history = seen.values.map { b ->
        val cells = historyMonths.map { m -> byMonth[m]?.get(b.categoryId!!)?.let { GridCell(it.spent, it.effectiveLimit) } }
        GridRow(
            ref(b),
            cells,
            streak = streakOf(cells, live) { it.value <= it.goal },
            misses = cells.count { it != null && it.value > it.goal },
        )
    }

    val reached = seen.values.map { b ->
        val id = b.categoryId!!
        val days = historyMonths.map { m ->
            val budget = byMonth[m]?.get(id) ?: return@map null
            val spent = spentDays[id to m] ?: return@map null
            val limit = budget.effectiveLimit
            var running = 0L
            spent.indices.firstOrNull { d -> running += spent[d]; running > limit }?.plus(1)
        }
        val hit = days.filterNotNull()
        ReachedRow(ref(b), days, if (hit.isEmpty()) null else Math.round(hit.average()).toInt())
    }

    // Spend inside budgets, outside them, and left out of them, month by month.
    val inside = LongArray(historyMonths.size)
    val outside = LongArray(historyMonths.size)
    val exempt = LongArray(historyMonths.size)
    val index = historyMonths.withIndex().associate { (i, m) -> m to i }
    stats.forEach { s ->
        if (s.type != CategoryKind.EXPENSE) return@forEach
        val i = index[s.monthKey] ?: return@forEach
        val on = byMonth[s.monthKey].orEmpty()
        val covered = budgetsOf(s.categoryId, s.parentId, s.parentExempt).any { it in on }
        exempt[i] += s.exemptTotal
        if (covered) inside[i] += s.total - s.exemptTotal else outside[i] += s.total - s.exemptTotal
    }
    val coverage = listOf(
        BalanceLine(COVER_IN, "", 0, inside.asList()),
        BalanceLine(COVER_OUT, "", 0, outside.asList()),
        BalanceLine(COVER_EXEMPT, "", 0, exempt.asList()),
    )

    return BudgetsInsightUi(month, length, elapsed, live, forecast, daily, waterfall, historyMonths, history, coverage, reached)
}
