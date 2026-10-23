package app.outgo.ui.analysis

import androidx.compose.runtime.Immutable

/**
 * Everything the Analysis sections draw, precomputed off the main thread by [buildSummary],
 * [buildBalances] and [buildCategoryCharts]. Composables only read these — no summing, sorting or
 * percentage maths happens inside a composition or a draw lambda.
 */

/** The summary card's kind chip. Transfers are never part of it — they get their own line. */
enum class AnalysisMode { EXPENSE, INCOME, ALL }

/** One section's data is either still loading, known-empty, or ready. */
sealed interface Stage<out T> {
    data object Loading : Stage<Nothing>
    data object Empty : Stage<Nothing>
    @Immutable
    data class Ready<T>(val data: T) : Stage<T>
}

val <T> Stage<T>.dataOrNull: T? get() = (this as? Stage.Ready<T>)?.data

// ------------------------------------------------------------------- summary

/** One kind's headline numbers. [perPeriod] is per day. */
@Immutable
data class KindSummary(
    val total: Long,
    val prevTotal: Long,
    val deltaAmount: Long,
    /** Null when the previous period had nothing, see [percentChange]. */
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

// ------------------------------------------------------------------- accounts

/**
 * An account (or category) for the account and category charts' pickers; a parent carries its
 * subaccounts (subcategories).
 */
@Immutable
data class AccountNode(val id: Long, val name: String, val iconId: Long?, val color: Int, val children: List<AccountNode> = emptyList())

/**
 * One account of the balance pie and its row: the month-end [amount], its change since the month
 * before, and its [share] of the positive balances (null at or below 0, which has no slice).
 */
@Immutable
data class ShareRow(
    val id: Long,
    val name: String,
    val iconId: Long?,
    val color: Int,
    val amount: Long,
    val delta: Long,
    /** Null when the month before had nothing, see [percentChange]. */
    val deltaPercent: Int?,
    val share: Float?,
    val startAngle: Float,
    val sweepAngle: Float,
)

/**
 * One month of the Categories group, both kinds, one line per category as stored (a subcategory,
 * or a parent that holds trades itself); a card adds them up per [AccountNode] with [sumLines].
 * [daily] runs up each one's month-to-date total, the live month stopping at today; [monthly]
 * holds each one's total in every month of [months], the page's own month last. [dailyCount] and
 * [monthlyCount] are the same for the number of trades instead of the money.
 */
@Immutable
data class CategoryChartsUi(
    val daysInMonth: Int,
    val daily: List<BalanceLine>,
    val months: List<Int>,
    val monthly: List<BalanceLine>,
    val dailyCount: List<BalanceLine> = emptyList(),
    val monthlyCount: List<BalanceLine> = emptyList(),
)

/** One account's balance at the end of each month of [BalanceTrendUi.months]. */
@Immutable
data class BalanceLine(
    val accountId: Long,
    val name: String,
    val color: Int,
    val values: List<Long>,
    val dashed: Boolean = false,
    /** Drawn in dots; a line can't be both [dashed] and dotted. */
    val dotted: Boolean = false,
    /** Shown in the legend, tinted with [color]; without one the legend falls back to a colour block. */
    val iconId: Long? = null,
    /** Index of the day (or month) [values]' first point sits on: a projection starts at today. */
    val start: Int = 0,
)

@Immutable
data class BalanceTrendUi(val months: List<Int>, val lines: List<BalanceLine>)

/**
 * Each account's balance at the end of every day of one month, index 0 == the 1st. The live
 * month's lines stop at today, so they can be shorter than [daysInMonth].
 */
@Immutable
data class DailyBalanceUi(val daysInMonth: Int, val lines: List<BalanceLine>)

/** The Accounts group's data for one month: month-end balances of the last months, and the month day by day. */
@Immutable
data class BalancesUi(val balance: BalanceTrendUi, val daily: DailyBalanceUi)
