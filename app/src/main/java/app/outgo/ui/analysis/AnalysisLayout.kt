package app.outgo.ui.analysis

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import app.outgo.R

/**
 * The user-arranged chart lists. The month pages share one ordered list of [ChartCard]s and the
 * year pages another, each saved as one `setting` row so it travels with backups. Category and
 * account charts sit in the same list. A card knows its chart, its own Expense/Income/All choice
 * and, for the per-account charts, which account it follows.
 */

/** [icon] must be unique among the charts one [LayoutSlot] offers. */
enum class ChartType(
    @DrawableRes val icon: Int,
    val hasMode: Boolean = true,
    val hasAccount: Boolean = false,
    /** The account chip also offers "All"; a null account means all of them instead of the first. */
    val allAccounts: Boolean = false,
    /** A chart whose value axis can be pinned to reach 0; see [ChartCard.zero]. */
    val hasZero: Boolean = false,
) {
    // Categories, month
    SUMMARY(R.drawable.ph_receipt),
    /** Donut and breakdown list in one card. */
    DONUT(R.drawable.ph_chart_pie_slice),
    PACE(R.drawable.ph_chart_line_up, hasZero = true),
    TREND(R.drawable.ph_chart_bar, hasZero = true),
    MOVERS(R.drawable.ph_arrows_down_up),
    HEATMAP(R.drawable.ph_calendar_dots, hasMode = false),
    WEEKDAY(R.drawable.ph_chart_bar_horizontal, hasMode = false, hasZero = true),
    BUCKETS(R.drawable.ph_coins, hasMode = false),
    LARGEST(R.drawable.ph_sort_descending),

    // Categories, year
    YEAR_SUMMARY(R.drawable.ph_receipt),
    YEAR_BARS(R.drawable.ph_chart_bar, hasZero = true),
    YOY(R.drawable.ph_chart_line, hasZero = true),

    // Accounts
    ACCOUNT_DONUT(R.drawable.ph_chart_donut),
    NET_FLOW(R.drawable.ph_trend_up, hasMode = false),
    BALANCE_TREND(R.drawable.ph_wallet, hasMode = false, hasZero = true),
    /** Month pages only: a year has no single month to draw day by day. */
    DAILY_BALANCE(R.drawable.ph_chart_line, hasMode = false, hasAccount = true, allAccounts = true, hasZero = true),
    TRANSFERS(R.drawable.ph_arrows_left_right, hasMode = false),
    ACCOUNT_LARGEST(R.drawable.ph_ranking, hasAccount = true),
}

/** A heading in the "+" sheet and the charts under it. */
data class ChartGroup(@StringRes val title: Int, val charts: List<ChartType>)

/**
 * One saved list. [charts] is the first-run layout; [groups] is what the "+" sheet offers, and
 * holds the same charts. [legacy] are the rows of the older per-tab lists (Categories, then
 * Accounts) with their own first-run layouts, which a list never saved in this form is built from.
 */
enum class LayoutSlot(
    val settingKey: String,
    val charts: List<ChartType>,
    val groups: List<ChartGroup>,
    val legacy: List<Pair<String, List<ChartType>>>,
) {
    MONTH(
        "analysis_layout_month",
        MonthCategoryCharts + MonthAccountCharts,
        listOf(
            ChartGroup(
                R.string.analysis_group_overview,
                listOf(ChartType.SUMMARY, ChartType.DONUT, ChartType.ACCOUNT_DONUT, ChartType.NET_FLOW),
            ),
            ChartGroup(
                R.string.analysis_group_over_time,
                listOf(
                    ChartType.TREND, ChartType.PACE, ChartType.HEATMAP, ChartType.WEEKDAY,
                    ChartType.BALANCE_TREND, ChartType.DAILY_BALANCE,
                ),
            ),
            ChartGroup(
                R.string.analysis_group_details,
                listOf(ChartType.MOVERS, ChartType.BUCKETS, ChartType.LARGEST, ChartType.TRANSFERS, ChartType.ACCOUNT_LARGEST),
            ),
        ),
        listOf(
            "analysis_layout_month_categories" to MonthCategoryCharts,
            "analysis_layout_month_accounts" to MonthAccountCharts,
        ),
    ),
    YEAR(
        "analysis_layout_year",
        YearCategoryCharts + YearAccountCharts,
        listOf(
            ChartGroup(
                R.string.analysis_group_overview,
                listOf(ChartType.YEAR_SUMMARY, ChartType.DONUT, ChartType.ACCOUNT_DONUT, ChartType.NET_FLOW),
            ),
            ChartGroup(R.string.analysis_group_over_time, listOf(ChartType.YEAR_BARS, ChartType.YOY, ChartType.BALANCE_TREND)),
            ChartGroup(R.string.analysis_group_details, listOf(ChartType.LARGEST, ChartType.TRANSFERS, ChartType.ACCOUNT_LARGEST)),
        ),
        listOf(
            "analysis_layout_year_categories" to YearCategoryCharts,
            "analysis_layout_year_accounts" to YearAccountCharts,
        ),
    ),
    ;

    /** Every chart this list may hold, so a card of the other kind (hand-edited backup) is dropped. */
    val offered: Set<ChartType> get() = groups.flatMapTo(HashSet()) { it.charts }

    companion object {
        fun of(page: AnalysisPage): LayoutSlot = if (page is AnalysisPage.Month) MONTH else YEAR
    }
}

private val MonthCategoryCharts
    get() = listOf(
        ChartType.SUMMARY, ChartType.DONUT, ChartType.PACE, ChartType.TREND, ChartType.MOVERS,
        ChartType.HEATMAP, ChartType.WEEKDAY, ChartType.BUCKETS, ChartType.LARGEST,
    )

private val MonthAccountCharts
    get() = listOf(
        ChartType.ACCOUNT_DONUT, ChartType.NET_FLOW, ChartType.BALANCE_TREND, ChartType.DAILY_BALANCE,
        ChartType.TRANSFERS, ChartType.ACCOUNT_LARGEST,
    )

private val YearCategoryCharts
    get() = listOf(ChartType.YEAR_SUMMARY, ChartType.YEAR_BARS, ChartType.DONUT, ChartType.YOY, ChartType.LARGEST)

private val YearAccountCharts
    get() = listOf(
        ChartType.ACCOUNT_DONUT, ChartType.NET_FLOW, ChartType.BALANCE_TREND, ChartType.TRANSFERS, ChartType.ACCOUNT_LARGEST,
    )

/** [id] only lives for the session: it keys the lazy list, it is not saved. */
@Immutable
data class ChartCard(
    val id: Long,
    val type: ChartType,
    val mode: AnalysisMode = AnalysisMode.ALL,
    /** Only for [ChartType.hasAccount]; null follows the first account. */
    val accountId: Long? = null,
    /** Only for [ChartType.hasZero]: the value axis always takes in 0, instead of fitting the data. */
    val zero: Boolean = false,
)

/** `TYPE.MODE.accountId.zero` per card, `;`-separated; zero is `0` when on, empty when off. */
fun encodeLayout(cards: List<ChartCard>): String =
    cards.joinToString(";") { "${it.type.name}.${it.mode.name}.${it.accountId ?: ""}.${if (it.zero) "0" else ""}" }

/**
 * Null [value] (never saved) gives [defaults]. Unknown charts — from a newer version's backup — and
 * charts not in [offered] are skipped rather than failing the whole list. Ids are 0; see [withIds].
 */
fun decodeLayout(value: String?, defaults: List<ChartType>, offered: Set<ChartType>): List<ChartCard> {
    if (value == null) return defaults.map { ChartCard(0, it) }
    return value.split(';').filter { it.isNotEmpty() }.mapNotNull { entry ->
        val parts = entry.split('.')
        val type = ChartType.entries.firstOrNull { it.name == parts[0] && it in offered } ?: return@mapNotNull null
        val mode = AnalysisMode.entries.firstOrNull { it.name == parts.getOrNull(1) } ?: AnalysisMode.ALL
        ChartCard(0, type, mode, parts.getOrNull(2)?.toLongOrNull(), parts.getOrNull(3) == "0")
    }
}

/**
 * The slot's list from its own row, or — when that was never written — the older Categories and
 * Accounts lists joined in that order, each falling back to its own first-run layout.
 */
inline fun decodeSlot(slot: LayoutSlot, read: (String) -> String?): List<ChartCard> {
    read(slot.settingKey)?.let { return decodeLayout(it, slot.charts, slot.offered) }
    return slot.legacy.flatMap { (key, defaults) -> decodeLayout(read(key), defaults, slot.offered) }
}

/** Session ids from [firstId] up, in list order. */
fun List<ChartCard>.withIds(firstId: Long): List<ChartCard> = mapIndexed { i, card -> card.copy(id = firstId + i) }
