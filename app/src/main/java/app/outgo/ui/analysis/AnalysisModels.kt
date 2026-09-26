package app.outgo.ui.analysis

import androidx.compose.runtime.Immutable

/**
 * Everything the Analysis sections draw, precomputed off the main thread by [buildStats],
 * [buildTrades], [buildYearStats] and [buildYearTrades]. Composables only read these — no summing,
 * sorting or percentage maths happens inside a composition or a draw lambda.
 *
 * Every model carries **both** kinds at once rather than being rebuilt per mode, so flipping a
 * card's Expense/Income/All chip picks a precomputed series instead of re-querying.
 */

/** A card's kind chip. Transfers are never part of it — they have their own chart. */
enum class AnalysisMode { EXPENSE, INCOME, ALL }

/** One section's data is either still loading, known-empty, or ready. */
sealed interface Stage<out T> {
    data object Loading : Stage<Nothing>
    data object Empty : Stage<Nothing>
    @Immutable
    data class Ready<T>(val data: T) : Stage<T>
}

val <T> Stage<T>.dataOrNull: T? get() = (this as? Stage.Ready<T>)?.data

// ---------------------------------------------------------------- section 1

/** One kind's headline numbers. [perPeriod] is per day on a month page, per month on a year page. */
@Immutable
data class KindSummary(
    val total: Long,
    val prevTotal: Long,
    val deltaAmount: Long,
    /** +100% when the previous period had nothing, see [percentChange]. */
    val deltaPercent: Int,
    val perPeriod: Long,
)

@Immutable
data class SummaryUi(
    val expense: KindSummary,
    val income: KindSummary,
    val net: Long,
    val prevNet: Long,
) {
    fun of(mode: AnalysisMode): KindSummary = if (mode == AnalysisMode.INCOME) income else expense
}

// ------------------------------------------------------------- sections 2, 3

/** [rootId] is null for the aggregated "Other" slice/row. */
@Immutable
data class DonutSlice(
    val rootId: Long?,
    val color: Int,
    val amount: Long,
    val fraction: Float,
    val startAngle: Float,
    val sweepAngle: Float,
)

@Immutable
data class DonutUi(val slices: List<DonutSlice>, val total: Long)

@Immutable
data class BreakdownRow(
    val rootId: Long?,
    /** [app.outgo.domain.CategoryKind]; drives the delta colour, which flips for income. */
    val kind: Int,
    val name: String,
    val iconId: Long?,
    val color: Int,
    val amount: Long,
    val sharePercent: Float,
    val deltaAmount: Long,
    val deltaPercent: Int,
)

/** The donut and the breakdown list are two views of one dataset. */
@Immutable
data class SliceSet(
    val donut: DonutUi,
    val rows: List<BreakdownRow>,
    val prevTotal: Long,
    val deltaAmount: Long,
    /** Change against the same period a month/year earlier. */
    val deltaPercent: Int,
)

// ---------------------------------------------------------------- section 4

/**
 * Running totals per day, index 0 == day 1. Kept as amounts rather than fractions so the chart
 * can rescale to whichever kind the mode switch is showing, and label its value axis.
 */
@Immutable
data class PaceSeries(
    val current: List<Long>,
    val previous: List<Long>,
    val currentTotal: Long,
    /** Biggest running total of this kind across both months. */
    val max: Long,
)

@Immutable
data class PaceUi(
    val expense: PaceSeries,
    val income: PaceSeries,
    val daysInMonth: Int,
    /** Shared axis for All mode, where both kinds are drawn together. */
    val combinedMax: Long,
) {
    fun of(mode: AnalysisMode): PaceSeries = if (mode == AnalysisMode.INCOME) income else expense

    /** A single kind scales to its own peak; All mode scales to both, so the two stay comparable. */
    fun maxOf(mode: AnalysisMode): Long = when (mode) {
        AnalysisMode.EXPENSE -> expense.max
        AnalysisMode.INCOME -> income.max
        AnalysisMode.ALL -> combinedMax
    }
}

// ------------------------------------------------------------- sections 5, Y2

/** One month's bar. Used by the 6-month trend and by the year page's 12-month chart. */
@Immutable
data class MonthBar(
    val monthKey: Int,
    val expense: Long,
    val income: Long,
    val selected: Boolean,
)

@Immutable
data class BarsUi(
    val bars: List<MonthBar>,
    val expenseAverage: Long,
    val incomeAverage: Long,
) {
    fun averageOf(mode: AnalysisMode): Long = if (mode == AnalysisMode.INCOME) incomeAverage else expenseAverage
}

// ---------------------------------------------------------------- section 6

/** The two halves All mode shows, ranked together and split — see [buildMovers]. */
@Immutable
data class MoverSplit(val expense: List<MoverRow>, val income: List<MoverRow>) {
    /** Counting the placeholder row an empty half still draws. */
    val rowCount: Int get() = maxOf(expense.size, 1) + maxOf(income.size, 1)
}

/**
 * A single-kind mode shows that kind's own top movers, scaled to its own biggest move; All mode
 * shows [all], where both kinds are ranked together and share one scale.
 */
@Immutable
data class MoversUi(
    val expense: List<MoverRow>,
    val income: List<MoverRow>,
    val all: MoverSplit,
) {
    fun of(mode: AnalysisMode): List<MoverRow> = if (mode == AnalysisMode.INCOME) income else expense
}

@Immutable
data class MoverRow(
    val rootId: Long,
    val kind: Int,
    val name: String,
    val iconId: Long?,
    val color: Int,
    /** Signed: positive means the category's total went up. */
    val delta: Long,
    /** |delta| over the biggest |delta| in the list. */
    val fraction: Float,
)

// ---------------------------------------------------------------- section 7

@Immutable
data class HeatCell(
    val dayOfMonth: Int,
    val startMillis: Long,
    val amount: Long,
    /** 0f for a day with no spend, up to 1f on the month's heaviest day. */
    val intensity: Float,
)

@Immutable
data class HeatmapUi(val cells: List<HeatCell>, val leadingBlanks: Int, val maxAmount: Long)

// ---------------------------------------------------------------- section 8

@Immutable
data class WeekdayBar(
    /** 1 = Monday … 7 = Sunday, matching [java.time.DayOfWeek.getValue]. */
    val weekday: Int,
    val average: Long,
)

// ---------------------------------------------------------------- section 9

@Immutable
data class SizeBucket(
    /** Inclusive lower bound; null on the first bucket. */
    val from: Long?,
    /** Exclusive upper bound; null on the last bucket. */
    val until: Long?,
    val count: Int,
    val amount: Long,
    val countPercent: Float,
    val moneyPercent: Float,
)

@Immutable
data class BucketsUi(val buckets: List<SizeBucket>, val median: Long)

// --------------------------------------------------------------- section 10

@Immutable
data class LargestItem(
    val tradeId: Long,
    /** [app.outgo.domain.TradeType]; colours the amount in All mode. */
    val type: Int,
    val name: String,
    val iconId: Long?,
    val color: Int,
    val amount: Long,
    val occurredAt: Long,
    val note: String?,
)

// --------------------------------------------------------------- section 11

/** One account pair. Both ends carry their own icon and colour, so the row can show the move. */
@Immutable
data class TransferPair(
    val fromName: String,
    val fromIconId: Long?,
    val fromColor: Int,
    val toName: String,
    val toIconId: Long?,
    val toColor: Int,
    val total: Long,
    val count: Int,
    val fraction: Float,
)

@Immutable
data class TransfersUi(val pairs: List<TransferPair>, val total: Long, val count: Int)

// ------------------------------------------------------------------ year page

@Immutable
data class YearSummaryUi(
    val summary: SummaryUi,
    /** Months counted so far: 12 for a past year, the elapsed count for the current one. */
    val monthsCounted: Int,
    val partial: Boolean,
    val biggestMonth: Int?,
    val biggestAmount: Long,
    val smallestMonth: Int?,
    val smallestAmount: Long,
)

/** Cumulative totals per month; index 0 == January. */
@Immutable
data class YoySeries(val current: List<Long>, val previous: List<Long>, val currentTotal: Long, val previousTotal: Long)

@Immutable
data class YoyUi(val expense: YoySeries, val income: YoySeries) {
    fun of(mode: AnalysisMode): YoySeries = if (mode == AnalysisMode.INCOME) income else expense
}

// ------------------------------------------------------------------- accounts

/** One account's balance at the end of each month of [BalanceTrendUi.months]. */
@Immutable
data class BalanceLine(val accountId: Long, val name: String, val color: Int, val values: List<Long>)

@Immutable
data class BalanceTrendUi(val months: List<Int>, val lines: List<BalanceLine>)

/**
 * Each account's balance at the end of every day of one month, index 0 == the 1st. The live
 * month's lines stop at today, so they can be shorter than [daysInMonth].
 */
@Immutable
data class DailyBalanceUi(val daysInMonth: Int, val lines: List<BalanceLine>)

@Immutable
data class LargestSet(val expense: List<LargestItem>, val income: List<LargestItem>, val all: List<LargestItem>) {
    fun of(mode: AnalysisMode): List<LargestItem> = when (mode) {
        AnalysisMode.EXPENSE -> expense
        AnalysisMode.INCOME -> income
        AnalysisMode.ALL -> all
    }
}

/**
 * The Accounts page. [expense]/[income] reuse the category donut's shape with an account id as
 * `rootId`; [netFlow] reuses the movers' row, signed like income (up is green).
 */
@Immutable
data class AccountsUi(
    val expense: SliceSet,
    val income: SliceSet,
    val netFlow: List<MoverRow>,
    val balance: BalanceTrendUi,
    val largest: Map<Long, LargestSet>,
    /**
     * Each account's share of the money held at the end of the period, against the end of the
     * period before; the All-mode donut. Accounts at or below 0 cannot be a slice and are left out.
     */
    val balanceShare: SliceSet,
    /** Month pages only; a year page leaves it empty. */
    val daily: DailyBalanceUi = DailyBalanceUi(0, emptyList()),
)

// ------------------------------------------------------------------- stages

@Immutable
data class StatsData(
    val summary: SummaryUi,
    val expense: SliceSet,
    val income: SliceSet,
    val bars: BarsUi,
    /** Always both kinds: the section shows an expense half and an income half. */
    val movers: MoversUi,
) {
    fun slicesOf(mode: AnalysisMode): SliceSet = if (mode == AnalysisMode.INCOME) income else expense
}

@Immutable
data class TradesData(
    val pace: PaceUi,
    val heatmap: HeatmapUi,
    val weekday: List<WeekdayBar>,
    val buckets: BucketsUi,
    val expenseLargest: List<LargestItem>,
    val incomeLargest: List<LargestItem>,
    val allLargest: List<LargestItem>,
    val transfers: TransfersUi,
    val accounts: AccountsUi,
) {
    fun largestOf(mode: AnalysisMode): List<LargestItem> = when (mode) {
        AnalysisMode.EXPENSE -> expenseLargest
        AnalysisMode.INCOME -> incomeLargest
        AnalysisMode.ALL -> allLargest
    }
}

@Immutable
data class YearStatsData(
    val summary: YearSummaryUi,
    val expense: SliceSet,
    val income: SliceSet,
    val bars: BarsUi,
    val yoy: YoyUi,
) {
    fun slicesOf(mode: AnalysisMode): SliceSet = if (mode == AnalysisMode.INCOME) income else expense
}

@Immutable
data class YearTradesData(
    val transfers: TransfersUi,
    val accounts: AccountsUi,
    val expenseLargest: List<LargestItem>,
    val incomeLargest: List<LargestItem>,
    val allLargest: List<LargestItem>,
) {
    fun largestOf(mode: AnalysisMode): List<LargestItem> = when (mode) {
        AnalysisMode.EXPENSE -> expenseLargest
        AnalysisMode.INCOME -> incomeLargest
        AnalysisMode.ALL -> allLargest
    }
}
