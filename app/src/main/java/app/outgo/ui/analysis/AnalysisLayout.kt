package app.outgo.ui.analysis

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import app.outgo.R
import app.outgo.domain.CategoryKind

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
    /** Picks any number of budgets or savings accounts ([ChartCard.ids]) and its own month ([ChartCard.month]). */
    val hasPicks: Boolean = false,
    /** Picks one parent account ([ChartCard.accountId]) and draws its subaccounts. */
    val hasParent: Boolean = false,
    /** Each account (category) it draws can be switched off ([ChartCard.hidden]). */
    val hasToggles: Boolean = false,
    /** A Categories-group chart of this [CategoryKind]; its [hasParent] picks a parent category. */
    val categoryKind: Int? = null,
) {
    // Accounts, month pages only. "Account" charts add each parent's subaccounts up; "subaccount"
    // charts draw the subaccounts of one picked parent.
    /** End-of-day balance, one line per account. */
    ACCOUNT_DAILY(R.drawable.ph_presentation_chart, hasMode = false, hasZero = true, hasToggles = true),
    SUBACCOUNT_DAILY(R.drawable.ph_chart_scatter, hasMode = false, hasZero = true, hasParent = true, hasToggles = true),
    /** Pie of the balances at the end of the month, with each one's change since the month before. */
    ACCOUNT_SHARE(R.drawable.ph_chart_pie, hasMode = false, hasToggles = true),
    SUBACCOUNT_SHARE(R.drawable.ph_chart_polar, hasMode = false, hasParent = true, hasToggles = true),
    /** Stacked month-end balances of the last [BALANCE_MONTH_COUNT] months. */
    ACCOUNT_MONTHS(R.drawable.ph_stack, hasMode = false, hasToggles = true),
    SUBACCOUNT_MONTHS(R.drawable.ph_stack_simple, hasMode = false, hasParent = true, hasToggles = true),

    // Categories group, month pages only: the same three charts as the Accounts group, for each
    // kind. "Category" charts add each parent's subcategories up; "subcategory" charts draw the
    // subcategories of one picked parent.
    /** Month-to-date total, one line per category. */
    EXPENSE_CATEGORY_DAILY(R.drawable.ph_shopping_cart, hasMode = false, hasZero = true, hasToggles = true, categoryKind = CategoryKind.EXPENSE),
    EXPENSE_SUBCATEGORY_DAILY(R.drawable.ph_shopping_cart_simple, hasMode = false, hasZero = true, hasParent = true, hasToggles = true, categoryKind = CategoryKind.EXPENSE),
    /** Pie of the month's totals, with each one's change since the month before. */
    EXPENSE_CATEGORY_SHARE(R.drawable.ph_shopping_bag, hasMode = false, hasToggles = true, categoryKind = CategoryKind.EXPENSE),
    EXPENSE_SUBCATEGORY_SHARE(R.drawable.ph_shopping_bag_open, hasMode = false, hasParent = true, hasToggles = true, categoryKind = CategoryKind.EXPENSE),
    /** Stacked totals of the last [BALANCE_MONTH_COUNT] months. */
    EXPENSE_CATEGORY_MONTHS(R.drawable.ph_basket, hasMode = false, hasToggles = true, categoryKind = CategoryKind.EXPENSE),
    EXPENSE_SUBCATEGORY_MONTHS(R.drawable.ph_storefront, hasMode = false, hasParent = true, hasToggles = true, categoryKind = CategoryKind.EXPENSE),
    INCOME_CATEGORY_DAILY(R.drawable.ph_hand_coins, hasMode = false, hasZero = true, hasToggles = true, categoryKind = CategoryKind.INCOME),
    INCOME_SUBCATEGORY_DAILY(R.drawable.ph_hand_deposit, hasMode = false, hasZero = true, hasParent = true, hasToggles = true, categoryKind = CategoryKind.INCOME),
    INCOME_CATEGORY_SHARE(R.drawable.ph_piggy_bank, hasMode = false, hasToggles = true, categoryKind = CategoryKind.INCOME),
    INCOME_SUBCATEGORY_SHARE(R.drawable.ph_vault, hasMode = false, hasParent = true, hasToggles = true, categoryKind = CategoryKind.INCOME),
    INCOME_CATEGORY_MONTHS(R.drawable.ph_money_wavy, hasMode = false, hasToggles = true, categoryKind = CategoryKind.INCOME),
    INCOME_SUBCATEGORY_MONTHS(R.drawable.ph_currency_circle_dollar, hasMode = false, hasParent = true, hasToggles = true, categoryKind = CategoryKind.INCOME),

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

    // Budgets and savings, month pages only
    /** Running spend of each picked budget, day by day. */
    BUDGET_DAILY(R.drawable.ph_trend_down, hasMode = false, hasPicks = true),
    /** Running balance change of each picked savings account since the month began. */
    SAVING_DAILY(R.drawable.ph_wallet_fill, hasMode = false, hasPicks = true),
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
        MonthBalanceCharts + MonthCategoryGroupCharts + MonthCategoryCharts + MonthAccountCharts + listOf(ChartType.BUDGET_DAILY, ChartType.SAVING_DAILY),
        listOf(
            ChartGroup(R.string.analysis_group_accounts, MonthBalanceCharts),
            ChartGroup(R.string.analysis_group_categories, MonthCategoryGroupCharts),
            // ponytail: every chart from before the Accounts group, parked here until each finds its own group.
            ChartGroup(
                R.string.analysis_group_other,
                listOf(
                    ChartType.SUMMARY, ChartType.DONUT, ChartType.ACCOUNT_DONUT, ChartType.NET_FLOW,
                    ChartType.TREND, ChartType.PACE, ChartType.HEATMAP, ChartType.WEEKDAY,
                    ChartType.BALANCE_TREND, ChartType.DAILY_BALANCE, ChartType.BUDGET_DAILY, ChartType.SAVING_DAILY,
                    ChartType.MOVERS, ChartType.BUCKETS, ChartType.LARGEST, ChartType.TRANSFERS, ChartType.ACCOUNT_LARGEST,
                ),
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

/** The Accounts group, in its first-run order; added on top of an older saved list once, see [MONTH_BALANCE_ADDED_KEY]. */
val MonthBalanceCharts
    get() = listOf(
        ChartType.ACCOUNT_DAILY, ChartType.SUBACCOUNT_DAILY, ChartType.ACCOUNT_SHARE, ChartType.SUBACCOUNT_SHARE,
        ChartType.ACCOUNT_MONTHS, ChartType.SUBACCOUNT_MONTHS,
    )

/** Set once [MonthBalanceCharts] were put on top of the saved month list (or a fresh one started with them). */
const val MONTH_BALANCE_ADDED_KEY = "analysis_layout_month_balance_added"

/**
 * The Categories group, in its first-run order; added after the Accounts group's charts of an older
 * saved list once, see [MONTH_CATEGORY_ADDED_KEY].
 */
val MonthCategoryGroupCharts
    get() = listOf(
        ChartType.EXPENSE_CATEGORY_DAILY, ChartType.EXPENSE_SUBCATEGORY_DAILY, ChartType.EXPENSE_CATEGORY_SHARE,
        ChartType.EXPENSE_SUBCATEGORY_SHARE, ChartType.EXPENSE_CATEGORY_MONTHS, ChartType.EXPENSE_SUBCATEGORY_MONTHS,
        ChartType.INCOME_CATEGORY_DAILY, ChartType.INCOME_SUBCATEGORY_DAILY, ChartType.INCOME_CATEGORY_SHARE,
        ChartType.INCOME_SUBCATEGORY_SHARE, ChartType.INCOME_CATEGORY_MONTHS, ChartType.INCOME_SUBCATEGORY_MONTHS,
    )

/** Set once [MonthCategoryGroupCharts] were put into the saved month list (or a fresh one started with them). */
const val MONTH_CATEGORY_ADDED_KEY = "analysis_layout_month_category_added"

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
    /**
     * Only for [ChartType.hasAccount]; null follows the first account. A [ChartType.hasParent] chart
     * keeps its parent account, or for a [ChartType.categoryKind] its parent category, here.
     */
    val accountId: Long? = null,
    /** Only for [ChartType.hasZero]: the value axis always takes in 0, instead of fitting the data. */
    val zero: Boolean = false,
    /** Only for [ChartType.hasPicks]: the budgets' category ids or the savings account ids; empty means all. */
    val ids: List<Long> = emptyList(),
    /** Only for [ChartType.hasPicks]: a fixed `yyyyMM`; null follows the page's month. */
    val month: Int? = null,
    /** Only for [ChartType.hasToggles]: the accounts (categories) switched off, so one added later shows. */
    val hidden: List<Long> = emptyList(),
)

/**
 * `TYPE.MODE.accountId.zero.ids.month.hidden` per card, `;`-separated; zero is `0` when on, empty when
 * off, ids and hidden are `,`-separated. Older versions read the first parts and ignore the rest.
 */
fun encodeLayout(cards: List<ChartCard>): String = cards.joinToString(";") {
    "${it.type.name}.${it.mode.name}.${it.accountId ?: ""}.${if (it.zero) "0" else ""}.${it.ids.joinToString(",")}.${it.month ?: ""}" +
        ".${it.hidden.joinToString(",")}"
}

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
        ChartCard(
            0, type, mode, parts.getOrNull(2)?.toLongOrNull(), parts.getOrNull(3) == "0",
            parts.getOrNull(4)?.split(',')?.mapNotNull { it.toLongOrNull() }.orEmpty(),
            parts.getOrNull(5)?.toIntOrNull(),
            parts.getOrNull(6)?.split(',')?.mapNotNull { it.toLongOrNull() }.orEmpty(),
        )
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
