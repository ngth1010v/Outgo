package app.outgo.ui.analysis

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import app.outgo.R

/**
 * The user-arranged chart lists. Each of the four pages (month/year x Categories/Accounts) keeps
 * its own ordered list of [ChartCard]s, saved as one `setting` row per page so it travels with
 * backups. A card knows its chart, its own Expense/Income/All choice and, for the per-account
 * list, which account it follows.
 */

/** The two halves of the screen, switched from the header. */
enum class AnalysisTab { CATEGORIES, ACCOUNTS }

/** [icon] must be unique among the charts one page offers; see AnalysisLayoutTest. */
enum class ChartType(@DrawableRes val icon: Int, val hasMode: Boolean = true, val hasAccount: Boolean = false) {
    // Categories, month
    SUMMARY(R.drawable.ph_receipt),
    /** Donut and breakdown list in one card. */
    DONUT(R.drawable.ph_chart_pie_slice),
    PACE(R.drawable.ph_chart_line_up),
    TREND(R.drawable.ph_chart_bar),
    MOVERS(R.drawable.ph_arrows_down_up),
    HEATMAP(R.drawable.ph_calendar_dots, hasMode = false),
    WEEKDAY(R.drawable.ph_chart_bar_horizontal, hasMode = false),
    BUCKETS(R.drawable.ph_coins, hasMode = false),
    LARGEST(R.drawable.ph_sort_descending),

    // Categories, year
    YEAR_SUMMARY(R.drawable.ph_receipt),
    YEAR_BARS(R.drawable.ph_chart_bar),
    YOY(R.drawable.ph_chart_line),

    // Accounts
    ACCOUNT_DONUT(R.drawable.ph_chart_donut),
    NET_FLOW(R.drawable.ph_arrows_down_up, hasMode = false),
    BALANCE_TREND(R.drawable.ph_chart_line_up, hasMode = false),
    TRANSFERS(R.drawable.ph_arrows_left_right, hasMode = false),
    ACCOUNT_LARGEST(R.drawable.ph_sort_descending, hasAccount = true),
}

/** A heading in the "+" sheet and the charts under it. */
data class ChartGroup(@StringRes val title: Int, val charts: List<ChartType>)

/**
 * One saved list. [charts] is the first-run layout; [groups] is what the "+" sheet offers, and
 * holds the same charts.
 */
enum class LayoutSlot(val settingKey: String, val charts: List<ChartType>, val groups: List<ChartGroup>) {
    MONTH_CATEGORIES(
        "analysis_layout_month_categories",
        listOf(
            ChartType.SUMMARY, ChartType.DONUT, ChartType.PACE, ChartType.TREND, ChartType.MOVERS,
            ChartType.HEATMAP, ChartType.WEEKDAY, ChartType.BUCKETS, ChartType.LARGEST,
        ),
        listOf(
            ChartGroup(R.string.analysis_group_overview, listOf(ChartType.SUMMARY, ChartType.DONUT)),
            ChartGroup(
                R.string.analysis_group_over_time,
                listOf(ChartType.TREND, ChartType.PACE, ChartType.HEATMAP, ChartType.WEEKDAY),
            ),
            ChartGroup(R.string.analysis_group_details, listOf(ChartType.MOVERS, ChartType.BUCKETS, ChartType.LARGEST)),
        ),
    ),
    MONTH_ACCOUNTS("analysis_layout_month_accounts", AccountCharts, AccountGroups),
    YEAR_CATEGORIES(
        "analysis_layout_year_categories",
        listOf(ChartType.YEAR_SUMMARY, ChartType.YEAR_BARS, ChartType.DONUT, ChartType.YOY, ChartType.LARGEST),
        listOf(
            ChartGroup(R.string.analysis_group_overview, listOf(ChartType.YEAR_SUMMARY, ChartType.DONUT)),
            ChartGroup(R.string.analysis_group_over_time, listOf(ChartType.YEAR_BARS, ChartType.YOY)),
            ChartGroup(R.string.analysis_group_details, listOf(ChartType.LARGEST)),
        ),
    ),
    YEAR_ACCOUNTS("analysis_layout_year_accounts", AccountCharts, AccountGroups),
    ;

    companion object {
        fun of(page: AnalysisPage, tab: AnalysisTab): LayoutSlot = when (page) {
            is AnalysisPage.Month -> if (tab == AnalysisTab.CATEGORIES) MONTH_CATEGORIES else MONTH_ACCOUNTS
            is AnalysisPage.Year -> if (tab == AnalysisTab.CATEGORIES) YEAR_CATEGORIES else YEAR_ACCOUNTS
        }
    }
}

private val AccountCharts
    get() = listOf(
        ChartType.ACCOUNT_DONUT, ChartType.NET_FLOW, ChartType.BALANCE_TREND, ChartType.TRANSFERS, ChartType.ACCOUNT_LARGEST,
    )

private val AccountGroups
    get() = listOf(
        ChartGroup(R.string.analysis_group_overview, listOf(ChartType.ACCOUNT_DONUT, ChartType.NET_FLOW)),
        ChartGroup(R.string.analysis_group_over_time, listOf(ChartType.BALANCE_TREND)),
        ChartGroup(R.string.analysis_group_details, listOf(ChartType.TRANSFERS, ChartType.ACCOUNT_LARGEST)),
    )

/** [id] only lives for the session: it keys the lazy list, it is not saved. */
@Immutable
data class ChartCard(
    val id: Long,
    val type: ChartType,
    val mode: AnalysisMode = AnalysisMode.ALL,
    /** Only for [ChartType.hasAccount]; null follows the first account. */
    val accountId: Long? = null,
)

/** `TYPE.MODE.accountId` per card, `;`-separated. */
fun encodeLayout(cards: List<ChartCard>): String =
    cards.joinToString(";") { "${it.type.name}.${it.mode.name}.${it.accountId ?: ""}" }

/**
 * Null [value] (never saved) gives the slot's default list. Unknown charts — from a newer
 * version's backup — are skipped rather than failing the whole list. Ids start at [firstId].
 */
fun decodeLayout(value: String?, slot: LayoutSlot, firstId: Long): List<ChartCard> {
    if (value == null) return slot.charts.mapIndexed { i, type -> ChartCard(firstId + i, type) }
    return value.split(';').filter { it.isNotEmpty() }.mapNotNull { entry ->
        val parts = entry.split('.')
        val type = ChartType.entries.firstOrNull { it.name == parts[0] } ?: return@mapNotNull null
        val mode = AnalysisMode.entries.firstOrNull { it.name == parts.getOrNull(1) } ?: AnalysisMode.ALL
        ChartCard(0, type, mode, parts.getOrNull(2)?.toLongOrNull())
    }.mapIndexed { i, card -> card.copy(id = firstId + i) }
}
