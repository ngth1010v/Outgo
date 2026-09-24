package app.outgo.ui.analysis

import androidx.compose.runtime.Immutable

/**
 * Everything the Analysis sections draw, precomputed off the main thread by [buildStats],
 * [buildTrades], [buildYearStats] and [buildYearTrades]. Composables only read these — no summing,
 * sorting or percentage maths happens inside a composition or a draw lambda.
 *
 * Every model carries **both** kinds at once rather than being rebuilt per mode, so flipping the
 * Expense/Income/All switch picks a precomputed series instead of re-querying.
 */

/** The page-wide switch. Transfers are never part of it — they have their own section. */
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
    /** null when the previous period had nothing to compare against. */
    val deltaPercent: Int?,
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
    val deltaPercent: Int?,
)

/** The donut and the breakdown list are two views of one dataset. */
@Immutable
data class SliceSet(val donut: DonutUi, val rows: List<BreakdownRow>)

// ---------------------------------------------------------------- section 4

/** Running totals per day, as fractions of the shared [PaceUi.maxTotal]; index 0 == day 1. */
@Immutable
data class PaceSeries(val current: List<Float>, val previous: List<Float>, val currentTotal: Long)

@Immutable
data class PaceUi(
    val expense: PaceSeries,
    val income: PaceSeries,
    val daysInMonth: Int,
    /** Both kinds and both months share one axis, so the lines are comparable. */
    val maxTotal: Long,
) {
    fun of(mode: AnalysisMode): PaceSeries = if (mode == AnalysisMode.INCOME) income else expense
}

// ------------------------------------------------------------- sections 5, Y2

/** One month's bar. Used by the 6-month trend and by the year page's 12-month chart. */
@Immutable
data class MonthBar(
    val monthKey: Int,
    val expense: Long,
    val income: Long,
    val expenseFraction: Float,
    val incomeFraction: Float,
    val selected: Boolean,
)

@Immutable
data class BarsUi(
    val bars: List<MonthBar>,
    val expenseAverage: Long,
    val incomeAverage: Long,
    val expenseAverageFraction: Float,
    val incomeAverageFraction: Float,
) {
    fun averageOf(mode: AnalysisMode): Long = if (mode == AnalysisMode.INCOME) incomeAverage else expenseAverage
    fun averageFractionOf(mode: AnalysisMode): Float =
        if (mode == AnalysisMode.INCOME) incomeAverageFraction else expenseAverageFraction
}

// ---------------------------------------------------------------- section 6

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
    val fraction: Float,
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

@Immutable
data class TransferPair(
    val fromName: String,
    val toName: String,
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

/** Cumulative totals per month, as fractions of the shared [YoyUi.maxTotal]; index 0 == January. */
@Immutable
data class YoySeries(val current: List<Float>, val previous: List<Float>, val currentTotal: Long, val previousTotal: Long)

@Immutable
data class YoyUi(val expense: YoySeries, val income: YoySeries, val maxTotal: Long) {
    fun of(mode: AnalysisMode): YoySeries = if (mode == AnalysisMode.INCOME) income else expense
}

// ------------------------------------------------------------------- stages

@Immutable
data class StatsData(
    val summary: SummaryUi,
    val expense: SliceSet,
    val income: SliceSet,
    val bars: BarsUi,
    val expenseMovers: List<MoverRow>,
    val incomeMovers: List<MoverRow>,
    val allMovers: List<MoverRow>,
) {
    fun slicesOf(mode: AnalysisMode): SliceSet = if (mode == AnalysisMode.INCOME) income else expense
    fun moversOf(mode: AnalysisMode): List<MoverRow> = when (mode) {
        AnalysisMode.EXPENSE -> expenseMovers
        AnalysisMode.INCOME -> incomeMovers
        AnalysisMode.ALL -> allMovers
    }
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
