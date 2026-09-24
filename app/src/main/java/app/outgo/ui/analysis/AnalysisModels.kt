package app.outgo.ui.analysis

import androidx.compose.runtime.Immutable

/**
 * Everything the Analysis sections draw, precomputed off the main thread by
 * [buildStats], [buildTrades] and [buildWeekday]. Composables only read these —
 * no summing, sorting or percentage maths happens inside a composition or a
 * draw lambda.
 */

/** One section's data is either still loading, known-empty, or ready. */
sealed interface Stage<out T> {
    data object Loading : Stage<Nothing>
    data object Empty : Stage<Nothing>
    @Immutable
    data class Ready<T>(val data: T) : Stage<T>
}

val <T> Stage<T>.dataOrNull: T? get() = (this as? Stage.Ready<T>)?.data

// ---------------------------------------------------------------- section 1

@Immutable
data class SummaryUi(
    val total: Long,
    val prevTotal: Long,
    val deltaAmount: Long,
    /** null when last month had nothing to compare against. */
    val deltaPercent: Int?,
    val avgPerDay: Long,
    val income: Long,
    val net: Long,
)

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
    val name: String,
    val iconId: Long?,
    val color: Int,
    val amount: Long,
    val sharePercent: Float,
    val deltaAmount: Long,
    val deltaPercent: Int?,
)

/** The donut and the breakdown list are two views of one dataset, toggled together. */
@Immutable
data class SliceSet(val donut: DonutUi, val rows: List<BreakdownRow>)

// ---------------------------------------------------------------- section 4

@Immutable
data class PaceUi(
    /** Running expense total per day, as a fraction of [maxTotal]; index 0 == day 1. */
    val current: List<Float>,
    val previous: List<Float>,
    val daysInMonth: Int,
    val maxTotal: Long,
    val currentTotal: Long,
)

// ---------------------------------------------------------------- section 5

@Immutable
data class TrendBar(
    val monthKey: Int,
    val amount: Long,
    val fraction: Float,
    val selected: Boolean,
)

@Immutable
data class TrendUi(val bars: List<TrendBar>, val average: Long, val averageFraction: Float)

// ---------------------------------------------------------------- section 6

@Immutable
data class MoverRow(
    val rootId: Long,
    val name: String,
    val iconId: Long?,
    val color: Int,
    /** Signed: positive means spending went up. */
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
    val name: String,
    val iconId: Long?,
    val color: Int,
    val amount: Long,
    val occurredAt: Long,
    val note: String?,
)

// ------------------------------------------------------------------- stages

@Immutable
data class StatsData(
    val summary: SummaryUi,
    val expense: SliceSet,
    val income: SliceSet,
    val trend: TrendUi,
    val movers: List<MoverRow>,
)

@Immutable
data class TradesData(
    val pace: PaceUi,
    val heatmap: HeatmapUi,
    val weekday: List<WeekdayBar>,
    val buckets: BucketsUi,
    val largest: List<LargestItem>,
)
