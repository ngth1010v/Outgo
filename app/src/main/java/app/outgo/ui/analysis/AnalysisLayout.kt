package app.outgo.ui.analysis

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import app.outgo.R
import app.outgo.domain.CategoryKind

/**
 * The user-arranged chart list. The month pages share one ordered list of [ChartCard]s, saved as one
 * `setting` row so it travels with backups. A card knows its chart, its own Expense/Income/All
 * choice and, for the account and category charts, which parent it follows and what it hides.
 */

/** [icon] must be unique among the charts one [LayoutSlot] offers. */
enum class ChartType(
    @DrawableRes val icon: Int,
    val hasMode: Boolean = true,
    /** A chart whose value axis can be pinned to reach 0; see [ChartCard.zero]. */
    val hasZero: Boolean = false,
    /** Picks one parent account ([ChartCard.accountId]) and draws its subaccounts. */
    val hasParent: Boolean = false,
    /** Each account (category) it draws can be switched off ([ChartCard.hidden]). */
    val hasToggles: Boolean = false,
    /** A Categories-group chart of this [CategoryKind]; its [hasParent] picks a parent category. */
    val categoryKind: Int? = null,
    /** A Trades, Savings or Budgets chart: the data it is drawn from. */
    val insight: InsightGroup? = null,
    /** Picks one savings account or budget ([ChartCard.accountId]) and draws only that one. */
    val pick: Pick? = null,
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

    // Trades, month pages only: how and when money moves, whatever its category.
    /** The month as a calendar, each day shaded by what went out (or came in, or the net). */
    TRADE_CALENDAR(R.drawable.ic_chart_calendar_heat, insight = InsightGroup.TRADES),
    /** Weekday x four-hour slot grid of the month's trades. */
    TRADE_WEEK(R.drawable.ic_chart_week_clock, insight = InsightGroup.TRADES),
    /** Trades by size: each bucket's share of the trades next to its share of the money. */
    TRADE_SIZES(R.drawable.ic_chart_sizes, insight = InsightGroup.TRADES),
    /** The month's largest trades; a tap opens one. */
    TRADE_TOP(R.drawable.ic_chart_top, insight = InsightGroup.TRADES),
    /** Trade count per month in bars, the average trade as a line. */
    TRADE_FREQUENCY(R.drawable.ic_chart_frequency, insight = InsightGroup.TRADES),
    /** Transfers as flows from the sending accounts to the receiving ones. */
    TRADE_TRANSFERS(R.drawable.ic_chart_flow, hasMode = false, insight = InsightGroup.TRADES),

    // Savings, month pages only. Progress an account hides stays hidden here unless kept in Analysis.
    /** Each target's progress against where an even pace would be by today. */
    SAVING_PACE(R.drawable.ic_chart_saving_pace, hasMode = false, insight = InsightGroup.SAVINGS),
    /** One account's month: saved so far, the even pace to its target, and where it is heading. */
    SAVING_BURNUP(R.drawable.ic_chart_burnup, hasMode = false, insight = InsightGroup.SAVINGS, pick = Pick.SAVINGS),
    /** The last twelve months' targets, hit or missed, with the current streak. */
    SAVING_HISTORY(R.drawable.ic_chart_saving_streak, hasMode = false, insight = InsightGroup.SAVINGS),
    /** Saved per month, and as a share of the month's income. */
    SAVING_RATE(R.drawable.ic_chart_saving_rate, hasMode = false, insight = InsightGroup.SAVINGS),
    /** One account's balance from the month's start to its end, step by step. */
    SAVING_SOURCES(R.drawable.ic_chart_saving_sources, hasMode = false, insight = InsightGroup.SAVINGS, pick = Pick.SAVINGS_BALANCE),

    // Budgets, month pages only.
    /** Each budget's spend, its even pace, and where the month ends at that pace; riskiest first. */
    BUDGET_FORECAST(R.drawable.ic_chart_budget_forecast, hasMode = false, insight = InsightGroup.BUDGETS),
    /** One budget's month: what is left day by day, top-ups stepping it up. */
    BUDGET_BURNDOWN(R.drawable.ic_chart_burndown, hasMode = false, insight = InsightGroup.BUDGETS, pick = Pick.BUDGET),
    /** The last months of every budget: share of the limit used, and the months over. */
    BUDGET_HISTORY(R.drawable.ic_chart_budget_grid, hasMode = false, insight = InsightGroup.BUDGETS),
    /** How one budget's limit this month comes about, and what is left of it. */
    BUDGET_WATERFALL(R.drawable.ic_chart_budget_waterfall, hasMode = false, insight = InsightGroup.BUDGETS, pick = Pick.BUDGET),
    /** Spend inside budgets, outside them, and left out of them, per month. */
    BUDGET_COVERAGE(R.drawable.ic_chart_coverage, hasMode = false, insight = InsightGroup.BUDGETS),
    /** The day each budget went over its limit, in each of the last months. */
    BUDGET_REACHED(R.drawable.ic_chart_reached, hasMode = false, insight = InsightGroup.BUDGETS),

    // Other
    /** Month totals, the change since last month, the daily average and the month's transfers. */
    SUMMARY(R.drawable.ph_receipt),
}

/**
 * A heading in the "+" sheet and the charts under it, then its [sub] groups: a sub-header each,
 * whose charts get the short two-line "<kind> / <by day | breakdown | 5 months>" names, see shortChartName.
 */
data class ChartGroup(@StringRes val title: Int, val charts: List<ChartType>, val sub: List<ChartGroup> = emptyList())

/**
 * One saved list. [charts] is the first-run layout; [groups] is what the "+" sheet offers, and
 * holds the same charts. [legacy] are the rows of the older per-tab lists (Categories, then
 * Accounts) with their own first-run layouts, which a list never saved in this form is built from.
 * The year page has no list of its own yet ("Coming soon"), so its old rows are no longer read.
 */
enum class LayoutSlot(
    val settingKey: String,
    val charts: List<ChartType>,
    val groups: List<ChartGroup>,
    val legacy: List<Pair<String, List<ChartType>>>,
) {
    MONTH(
        "analysis_layout_month",
        MonthBalanceCharts + MonthCategoryGroupCharts + ChartType.SUMMARY,
        listOf(
            ChartGroup(
                R.string.analysis_group_accounts,
                emptyList(),
                listOf(
                    ChartGroup(
                        R.string.analysis_sub_balance,
                        listOf(
                            ChartType.ACCOUNT_DAILY, ChartType.ACCOUNT_SHARE, ChartType.ACCOUNT_MONTHS,
                            ChartType.SUBACCOUNT_DAILY, ChartType.SUBACCOUNT_SHARE, ChartType.SUBACCOUNT_MONTHS,
                        ),
                    ),
                ),
            ),
            ChartGroup(
                R.string.analysis_group_categories,
                emptyList(),
                listOf(
                    ChartGroup(
                        R.string.analysis_sub_category,
                        listOf(
                            ChartType.EXPENSE_CATEGORY_DAILY, ChartType.EXPENSE_CATEGORY_SHARE, ChartType.EXPENSE_CATEGORY_MONTHS,
                            ChartType.INCOME_CATEGORY_DAILY, ChartType.INCOME_CATEGORY_SHARE, ChartType.INCOME_CATEGORY_MONTHS,
                        ),
                    ),
                    ChartGroup(
                        R.string.analysis_sub_subcategory,
                        listOf(
                            ChartType.EXPENSE_SUBCATEGORY_DAILY, ChartType.EXPENSE_SUBCATEGORY_SHARE, ChartType.EXPENSE_SUBCATEGORY_MONTHS,
                            ChartType.INCOME_SUBCATEGORY_DAILY, ChartType.INCOME_SUBCATEGORY_SHARE, ChartType.INCOME_SUBCATEGORY_MONTHS,
                        ),
                    ),
                ),
            ),
            ChartGroup(R.string.analysis_group_trades, TradeCharts),
            ChartGroup(R.string.analysis_group_savings, SavingCharts),
            ChartGroup(R.string.analysis_group_budgets, BudgetCharts),
            ChartGroup(R.string.analysis_group_other, listOf(ChartType.SUMMARY)),
        ),
        listOf(
            "analysis_layout_month_categories" to listOf(ChartType.SUMMARY),
            "analysis_layout_month_accounts" to emptyList(),
        ),
    ),
    ;

    /**
     * Every chart this list may hold, sub-groups included, so a removed chart (or one from a
     * hand-edited backup) is dropped when the list is read.
     */
    val offered: Set<ChartType> get() = groups.flatMapTo(HashSet()) { it.allCharts() }
}

val TradeCharts
    get() = listOf(
        ChartType.TRADE_CALENDAR, ChartType.TRADE_WEEK, ChartType.TRADE_SIZES,
        ChartType.TRADE_TOP, ChartType.TRADE_FREQUENCY, ChartType.TRADE_TRANSFERS,
    )

val SavingCharts
    get() = listOf(
        ChartType.SAVING_PACE, ChartType.SAVING_BURNUP, ChartType.SAVING_HISTORY,
        ChartType.SAVING_RATE, ChartType.SAVING_SOURCES,
    )

val BudgetCharts
    get() = listOf(
        ChartType.BUDGET_FORECAST, ChartType.BUDGET_BURNDOWN, ChartType.BUDGET_HISTORY,
        ChartType.BUDGET_WATERFALL, ChartType.BUDGET_COVERAGE, ChartType.BUDGET_REACHED,
    )

/** This group's charts and those of its sub-groups, at any depth. */
fun ChartGroup.allCharts(): List<ChartType> = charts + sub.flatMap { it.allCharts() }

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

/** [id] only lives for the session: it keys the lazy list, it is not saved. */
@Immutable
data class ChartCard(
    val id: Long,
    val type: ChartType,
    val mode: AnalysisMode = AnalysisMode.ALL,
    /**
     * Only for [ChartType.hasParent]: the parent account, or for a [ChartType.categoryKind] the
     * parent category; null follows the first one.
     */
    val accountId: Long? = null,
    /** Only for [ChartType.hasZero]: the value axis always takes in 0, instead of fitting the data. */
    val zero: Boolean = false,
    /** Only for [ChartType.hasToggles]: the accounts (categories) switched off, so one added later shows. */
    val hidden: List<Long> = emptyList(),
)

/**
 * `TYPE.MODE.accountId.zero.ids.month.hidden` per card, `;`-separated; zero is `0` when on, empty when
 * off, hidden is `,`-separated. ids and month belonged to the removed budget and savings charts and
 * are always written empty, so hidden keeps its place. Older versions read the first parts and
 * ignore the rest.
 */
fun encodeLayout(cards: List<ChartCard>): String = cards.joinToString(";") {
    "${it.type.name}.${it.mode.name}.${it.accountId ?: ""}.${if (it.zero) "0" else ""}..." + it.hidden.joinToString(",")
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
