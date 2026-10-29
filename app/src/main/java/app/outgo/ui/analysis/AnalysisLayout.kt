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
    /**
     * Year page charts only: the month chart this one is drawn like. A year chart takes its chips
     * from it and is drawn by the same card, from the year's data.
     */
    val base: ChartType? = null,
    val hasMode: Boolean = base?.hasMode ?: true,
    /** Picks one parent account ([ChartCard.accountId]) and draws its subaccounts. */
    val hasParent: Boolean = base?.hasParent ?: false,
    /** Each account (category) it draws can be switched off ([ChartCard.hidden]). */
    val hasToggles: Boolean = base?.hasToggles ?: false,
    /** A Categories-group chart of this [CategoryKind]; its [hasParent] picks a parent category. */
    val categoryKind: Int? = base?.categoryKind,
    /** A Trades, Savings or Budgets chart: the data it is drawn from. */
    val insight: InsightGroup? = base?.insight,
    /** Picks one savings account or budget ([ChartCard.accountId]) and draws only that one. */
    val pick: Pick? = base?.pick,
    /** A Categories-group chart that counts trades instead of adding up their money. */
    val count: Boolean = base?.count ?: false,
) {
    // Accounts, month pages only. "Account" charts add each parent's subaccounts up; "subaccount"
    // charts draw the subaccounts of one picked parent.
    /** End-of-day balance, one line per account. */
    ACCOUNT_DAILY(R.drawable.ph_presentation_chart, hasMode = false, hasToggles = true),
    SUBACCOUNT_DAILY(R.drawable.ph_chart_scatter, hasMode = false, hasParent = true, hasToggles = true),
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
    EXPENSE_CATEGORY_DAILY(R.drawable.ph_shopping_cart, hasMode = false, hasToggles = true, categoryKind = CategoryKind.EXPENSE),
    EXPENSE_SUBCATEGORY_DAILY(R.drawable.ph_shopping_cart_simple, hasMode = false, hasParent = true, hasToggles = true, categoryKind = CategoryKind.EXPENSE),
    /** Pie of the month's totals, with each one's change since the month before. */
    EXPENSE_CATEGORY_SHARE(R.drawable.ph_shopping_bag, hasMode = false, hasToggles = true, categoryKind = CategoryKind.EXPENSE),
    EXPENSE_SUBCATEGORY_SHARE(R.drawable.ph_shopping_bag_open, hasMode = false, hasParent = true, hasToggles = true, categoryKind = CategoryKind.EXPENSE),
    /** Stacked totals of the last [BALANCE_MONTH_COUNT] months. */
    EXPENSE_CATEGORY_MONTHS(R.drawable.ph_basket, hasMode = false, hasToggles = true, categoryKind = CategoryKind.EXPENSE),
    EXPENSE_SUBCATEGORY_MONTHS(R.drawable.ph_storefront, hasMode = false, hasParent = true, hasToggles = true, categoryKind = CategoryKind.EXPENSE),
    INCOME_CATEGORY_DAILY(R.drawable.ph_hand_coins, hasMode = false, hasToggles = true, categoryKind = CategoryKind.INCOME),
    INCOME_SUBCATEGORY_DAILY(R.drawable.ph_hand_deposit, hasMode = false, hasParent = true, hasToggles = true, categoryKind = CategoryKind.INCOME),
    INCOME_CATEGORY_SHARE(R.drawable.ph_piggy_bank, hasMode = false, hasToggles = true, categoryKind = CategoryKind.INCOME),
    INCOME_SUBCATEGORY_SHARE(R.drawable.ph_vault, hasMode = false, hasParent = true, hasToggles = true, categoryKind = CategoryKind.INCOME),
    INCOME_CATEGORY_MONTHS(R.drawable.ph_money_wavy, hasMode = false, hasToggles = true, categoryKind = CategoryKind.INCOME),
    INCOME_SUBCATEGORY_MONTHS(R.drawable.ph_currency_circle_dollar, hasMode = false, hasParent = true, hasToggles = true, categoryKind = CategoryKind.INCOME),

    // Categories group, trade counts: the by-day and breakdown charts counting trades.
    /** Month-to-date trade count, one line per category. */
    EXPENSE_CATEGORY_COUNT_DAILY(R.drawable.ic_chart_count_line_expense, hasMode = false, hasToggles = true, categoryKind = CategoryKind.EXPENSE, count = true),
    EXPENSE_SUBCATEGORY_COUNT_DAILY(R.drawable.ic_chart_count_lines_expense, hasMode = false, hasParent = true, hasToggles = true, categoryKind = CategoryKind.EXPENSE, count = true),
    /** Pie of the month's trade counts, with each one's change since the month before. */
    EXPENSE_CATEGORY_COUNT_SHARE(R.drawable.ic_chart_count_pie_expense, hasMode = false, hasToggles = true, categoryKind = CategoryKind.EXPENSE, count = true),
    EXPENSE_SUBCATEGORY_COUNT_SHARE(R.drawable.ic_chart_count_slices_expense, hasMode = false, hasParent = true, hasToggles = true, categoryKind = CategoryKind.EXPENSE, count = true),
    INCOME_CATEGORY_COUNT_DAILY(R.drawable.ic_chart_count_line_income, hasMode = false, hasToggles = true, categoryKind = CategoryKind.INCOME, count = true),
    INCOME_SUBCATEGORY_COUNT_DAILY(R.drawable.ic_chart_count_lines_income, hasMode = false, hasParent = true, hasToggles = true, categoryKind = CategoryKind.INCOME, count = true),
    INCOME_CATEGORY_COUNT_SHARE(R.drawable.ic_chart_count_pie_income, hasMode = false, hasToggles = true, categoryKind = CategoryKind.INCOME, count = true),
    INCOME_SUBCATEGORY_COUNT_SHARE(R.drawable.ic_chart_count_slices_income, hasMode = false, hasParent = true, hasToggles = true, categoryKind = CategoryKind.INCOME, count = true),

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
    /**
     * Where the money came from, which account held it and where it went (a node chart). Its
     * settings ([CashFlowOptionsSheet]) pick the account and category levels; its filter holds the
     * switches of [cashFlowOptions], the accounts and the categories.
     */
    TRADE_CASH_FLOW(R.drawable.ic_chart_cash_flow, hasMode = false, hasToggles = true, insight = InsightGroup.TRADES),

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

    // Year page. Accounts: month-end balance through the year, the year-end pie (change since the
    // year before), and year-end balances of the last [YEAR_COUNT] years.
    YEAR_ACCOUNT_MONTHLY(R.drawable.ph_presentation_chart, ACCOUNT_DAILY),
    YEAR_SUBACCOUNT_MONTHLY(R.drawable.ph_chart_scatter, SUBACCOUNT_DAILY),
    YEAR_ACCOUNT_SHARE(R.drawable.ph_chart_pie, ACCOUNT_SHARE),
    YEAR_SUBACCOUNT_SHARE(R.drawable.ph_chart_polar, SUBACCOUNT_SHARE),
    YEAR_ACCOUNT_YEARS(R.drawable.ph_stack, ACCOUNT_MONTHS),
    YEAR_SUBACCOUNT_YEARS(R.drawable.ph_stack_simple, SUBACCOUNT_MONTHS),

    // Year page, Categories: year-to-date totals month by month, the year's pie (change since the
    // same months of the year before), and the totals of the last [YEAR_COUNT] years.
    YEAR_EXPENSE_CATEGORY_MONTHLY(R.drawable.ph_shopping_cart, EXPENSE_CATEGORY_DAILY),
    YEAR_EXPENSE_SUBCATEGORY_MONTHLY(R.drawable.ph_shopping_cart_simple, EXPENSE_SUBCATEGORY_DAILY),
    YEAR_EXPENSE_CATEGORY_SHARE(R.drawable.ph_shopping_bag, EXPENSE_CATEGORY_SHARE),
    YEAR_EXPENSE_SUBCATEGORY_SHARE(R.drawable.ph_shopping_bag_open, EXPENSE_SUBCATEGORY_SHARE),
    YEAR_EXPENSE_CATEGORY_YEARS(R.drawable.ph_basket, EXPENSE_CATEGORY_MONTHS),
    YEAR_EXPENSE_SUBCATEGORY_YEARS(R.drawable.ph_storefront, EXPENSE_SUBCATEGORY_MONTHS),
    YEAR_INCOME_CATEGORY_MONTHLY(R.drawable.ph_hand_coins, INCOME_CATEGORY_DAILY),
    YEAR_INCOME_SUBCATEGORY_MONTHLY(R.drawable.ph_hand_deposit, INCOME_SUBCATEGORY_DAILY),
    YEAR_INCOME_CATEGORY_SHARE(R.drawable.ph_piggy_bank, INCOME_CATEGORY_SHARE),
    YEAR_INCOME_SUBCATEGORY_SHARE(R.drawable.ph_vault, INCOME_SUBCATEGORY_SHARE),
    YEAR_INCOME_CATEGORY_YEARS(R.drawable.ph_money_wavy, INCOME_CATEGORY_MONTHS),
    YEAR_INCOME_SUBCATEGORY_YEARS(R.drawable.ph_currency_circle_dollar, INCOME_SUBCATEGORY_MONTHS),

    // Year page, Categories trade counts: year-to-date counts month by month, and the year's pie.
    YEAR_EXPENSE_CATEGORY_COUNT_MONTHLY(R.drawable.ic_chart_count_line_expense, EXPENSE_CATEGORY_COUNT_DAILY),
    YEAR_EXPENSE_SUBCATEGORY_COUNT_MONTHLY(R.drawable.ic_chart_count_lines_expense, EXPENSE_SUBCATEGORY_COUNT_DAILY),
    YEAR_EXPENSE_CATEGORY_COUNT_SHARE(R.drawable.ic_chart_count_pie_expense, EXPENSE_CATEGORY_COUNT_SHARE),
    YEAR_EXPENSE_SUBCATEGORY_COUNT_SHARE(R.drawable.ic_chart_count_slices_expense, EXPENSE_SUBCATEGORY_COUNT_SHARE),
    YEAR_INCOME_CATEGORY_COUNT_MONTHLY(R.drawable.ic_chart_count_line_income, INCOME_CATEGORY_COUNT_DAILY),
    YEAR_INCOME_SUBCATEGORY_COUNT_MONTHLY(R.drawable.ic_chart_count_lines_income, INCOME_SUBCATEGORY_COUNT_DAILY),
    YEAR_INCOME_CATEGORY_COUNT_SHARE(R.drawable.ic_chart_count_pie_income, INCOME_CATEGORY_COUNT_SHARE),
    YEAR_INCOME_SUBCATEGORY_COUNT_SHARE(R.drawable.ic_chart_count_slices_income, INCOME_SUBCATEGORY_COUNT_SHARE),

    // Year page, Trades: the month charts over the whole year (the frequency chart over its months).
    YEAR_TRADE_WEEK(R.drawable.ic_chart_week_clock, TRADE_WEEK),
    YEAR_TRADE_SIZES(R.drawable.ic_chart_sizes, TRADE_SIZES),
    YEAR_TRADE_TOP(R.drawable.ic_chart_top, TRADE_TOP),
    YEAR_TRADE_FREQUENCY(R.drawable.ic_chart_frequency, TRADE_FREQUENCY),
    YEAR_TRADE_TRANSFERS(R.drawable.ic_chart_flow, TRADE_TRANSFERS),
    YEAR_TRADE_CASH_FLOW(R.drawable.ic_chart_cash_flow, TRADE_CASH_FLOW),

    // Year page, Savings: the year's targets added up, and the year's months.
    YEAR_SAVING_PACE(R.drawable.ic_chart_saving_pace, SAVING_PACE),
    YEAR_SAVING_HISTORY(R.drawable.ic_chart_saving_streak, SAVING_HISTORY),
    YEAR_SAVING_RATE(R.drawable.ic_chart_saving_rate, SAVING_RATE),
    YEAR_SAVING_SOURCES(R.drawable.ic_chart_saving_sources, SAVING_SOURCES),

    // Year page, Budgets: the year's limits added up, and the year's months.
    YEAR_BUDGET_TOTALS(R.drawable.ic_chart_budget_forecast, BUDGET_FORECAST),
    YEAR_BUDGET_HISTORY(R.drawable.ic_chart_budget_grid, BUDGET_HISTORY),
    YEAR_BUDGET_COVERAGE(R.drawable.ic_chart_coverage, BUDGET_COVERAGE),
    YEAR_BUDGET_REACHED(R.drawable.ic_chart_reached, BUDGET_REACHED),

    // Year page, Other.
    YEAR_SUMMARY(R.drawable.ph_receipt, SUMMARY),
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
 * The year list is saved under a key of its own: the older year rows held charts it no longer has.
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
                            ChartType.EXPENSE_CATEGORY_COUNT_DAILY, ChartType.EXPENSE_CATEGORY_COUNT_SHARE,
                            ChartType.INCOME_CATEGORY_COUNT_DAILY, ChartType.INCOME_CATEGORY_COUNT_SHARE,
                        ),
                    ),
                    ChartGroup(
                        R.string.analysis_sub_subcategory,
                        listOf(
                            ChartType.EXPENSE_SUBCATEGORY_DAILY, ChartType.EXPENSE_SUBCATEGORY_SHARE, ChartType.EXPENSE_SUBCATEGORY_MONTHS,
                            ChartType.INCOME_SUBCATEGORY_DAILY, ChartType.INCOME_SUBCATEGORY_SHARE, ChartType.INCOME_SUBCATEGORY_MONTHS,
                            ChartType.EXPENSE_SUBCATEGORY_COUNT_DAILY, ChartType.EXPENSE_SUBCATEGORY_COUNT_SHARE,
                            ChartType.INCOME_SUBCATEGORY_COUNT_DAILY, ChartType.INCOME_SUBCATEGORY_COUNT_SHARE,
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
    YEAR(
        "analysis_layout_year_v2",
        YearCharts,
        listOf(
            ChartGroup(
                R.string.analysis_group_accounts,
                emptyList(),
                listOf(
                    ChartGroup(
                        R.string.analysis_sub_balance,
                        listOf(
                            ChartType.YEAR_ACCOUNT_MONTHLY, ChartType.YEAR_ACCOUNT_SHARE, ChartType.YEAR_ACCOUNT_YEARS,
                            ChartType.YEAR_SUBACCOUNT_MONTHLY, ChartType.YEAR_SUBACCOUNT_SHARE, ChartType.YEAR_SUBACCOUNT_YEARS,
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
                            ChartType.YEAR_EXPENSE_CATEGORY_MONTHLY, ChartType.YEAR_EXPENSE_CATEGORY_SHARE, ChartType.YEAR_EXPENSE_CATEGORY_YEARS,
                            ChartType.YEAR_INCOME_CATEGORY_MONTHLY, ChartType.YEAR_INCOME_CATEGORY_SHARE, ChartType.YEAR_INCOME_CATEGORY_YEARS,
                            ChartType.YEAR_EXPENSE_CATEGORY_COUNT_MONTHLY, ChartType.YEAR_EXPENSE_CATEGORY_COUNT_SHARE,
                            ChartType.YEAR_INCOME_CATEGORY_COUNT_MONTHLY, ChartType.YEAR_INCOME_CATEGORY_COUNT_SHARE,
                        ),
                    ),
                    ChartGroup(
                        R.string.analysis_sub_subcategory,
                        listOf(
                            ChartType.YEAR_EXPENSE_SUBCATEGORY_MONTHLY, ChartType.YEAR_EXPENSE_SUBCATEGORY_SHARE, ChartType.YEAR_EXPENSE_SUBCATEGORY_YEARS,
                            ChartType.YEAR_INCOME_SUBCATEGORY_MONTHLY, ChartType.YEAR_INCOME_SUBCATEGORY_SHARE, ChartType.YEAR_INCOME_SUBCATEGORY_YEARS,
                            ChartType.YEAR_EXPENSE_SUBCATEGORY_COUNT_MONTHLY, ChartType.YEAR_EXPENSE_SUBCATEGORY_COUNT_SHARE,
                            ChartType.YEAR_INCOME_SUBCATEGORY_COUNT_MONTHLY, ChartType.YEAR_INCOME_SUBCATEGORY_COUNT_SHARE,
                        ),
                    ),
                ),
            ),
            ChartGroup(R.string.analysis_group_trades, YearTradeCharts),
            ChartGroup(R.string.analysis_group_savings, YearSavingCharts),
            ChartGroup(R.string.analysis_group_budgets, YearBudgetCharts),
            ChartGroup(R.string.analysis_group_other, listOf(ChartType.YEAR_SUMMARY)),
        ),
        emptyList(),
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
        ChartType.TRADE_TOP, ChartType.TRADE_FREQUENCY, ChartType.TRADE_TRANSFERS, ChartType.TRADE_CASH_FLOW,
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

val YearTradeCharts
    get() = listOf(
        ChartType.YEAR_TRADE_WEEK, ChartType.YEAR_TRADE_SIZES, ChartType.YEAR_TRADE_TOP,
        ChartType.YEAR_TRADE_FREQUENCY, ChartType.YEAR_TRADE_TRANSFERS, ChartType.YEAR_TRADE_CASH_FLOW,
    )

val YearSavingCharts
    get() = listOf(ChartType.YEAR_SAVING_PACE, ChartType.YEAR_SAVING_HISTORY, ChartType.YEAR_SAVING_RATE, ChartType.YEAR_SAVING_SOURCES)

val YearBudgetCharts
    get() = listOf(ChartType.YEAR_BUDGET_TOTALS, ChartType.YEAR_BUDGET_HISTORY, ChartType.YEAR_BUDGET_COVERAGE, ChartType.YEAR_BUDGET_REACHED)

/** The year list's first-run layout: every year chart, in the "+" sheet's group order. */
val YearCharts
    get() = listOf(
        ChartType.YEAR_ACCOUNT_MONTHLY, ChartType.YEAR_SUBACCOUNT_MONTHLY, ChartType.YEAR_ACCOUNT_SHARE,
        ChartType.YEAR_SUBACCOUNT_SHARE, ChartType.YEAR_ACCOUNT_YEARS, ChartType.YEAR_SUBACCOUNT_YEARS,
        ChartType.YEAR_EXPENSE_CATEGORY_MONTHLY, ChartType.YEAR_EXPENSE_SUBCATEGORY_MONTHLY, ChartType.YEAR_EXPENSE_CATEGORY_SHARE,
        ChartType.YEAR_EXPENSE_SUBCATEGORY_SHARE, ChartType.YEAR_EXPENSE_CATEGORY_YEARS, ChartType.YEAR_EXPENSE_SUBCATEGORY_YEARS,
        ChartType.YEAR_INCOME_CATEGORY_MONTHLY, ChartType.YEAR_INCOME_SUBCATEGORY_MONTHLY, ChartType.YEAR_INCOME_CATEGORY_SHARE,
        ChartType.YEAR_INCOME_SUBCATEGORY_SHARE, ChartType.YEAR_INCOME_CATEGORY_YEARS, ChartType.YEAR_INCOME_SUBCATEGORY_YEARS,
    ) + YearTradeCharts + YearSavingCharts + YearBudgetCharts + ChartType.YEAR_SUMMARY

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

/**
 * The Categories group's trade-count charts. Not in any first-run layout: the "+" sheet adds them.
 * Drawn by the same card as [MonthCategoryGroupCharts].
 */
val MonthCategoryCountCharts
    get() = listOf(
        ChartType.EXPENSE_CATEGORY_COUNT_DAILY, ChartType.EXPENSE_SUBCATEGORY_COUNT_DAILY, ChartType.EXPENSE_CATEGORY_COUNT_SHARE,
        ChartType.EXPENSE_SUBCATEGORY_COUNT_SHARE, ChartType.INCOME_CATEGORY_COUNT_DAILY, ChartType.INCOME_SUBCATEGORY_COUNT_DAILY,
        ChartType.INCOME_CATEGORY_COUNT_SHARE, ChartType.INCOME_SUBCATEGORY_COUNT_SHARE,
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
    /** Only for [ChartType.hasToggles]: the accounts (categories) switched off, so one added later shows. */
    val hidden: List<Long> = emptyList(),
    /** Only for [hasMetric]: shades and ranks by trade count instead of amount. */
    val byCount: Boolean = false,
    /** Only for the cash flow: the categories switched off (parents, or sub-categories of [expandedCategories]). */
    val hiddenCategories: List<Long> = emptyList(),
    /** Only for the cash flow: full screen squeezes the chart into the screen instead of scrolling it. */
    val fitScreen: Boolean = false,
    /** Only for the cash flow: the parent accounts drawn as their subaccounts. */
    val expandedAccounts: List<Long> = emptyList(),
    /** Only for the cash flow: the parent categories drawn as their sub-categories. */
    val expandedCategories: List<Long> = emptyList(),
    /** Only for the cash flow: accounts show their name and net change, not just their icon. */
    val accountDetail: Boolean = false,
)

/**
 * The day calendar and the weekday-and-time grid (month or year): shade by amount or by trade count
 * ([ChartCard.byCount]), set in the gear sheet along with their kind.
 */
val ChartType.hasMetric: Boolean get() = (base ?: this).let { it == ChartType.TRADE_WEEK || it == ChartType.TRADE_CALENDAR }

/**
 * `TYPE.MODE.accountId.zero.ids.month.hidden.metric.flags.hiddenCategories.expandedAccounts.expandedCategories`
 * per card, `;`-separated; the lists are `,`-separated, metric is `C` for [ChartCard.byCount] or empty,
 * flags holds `F` for [ChartCard.fitScreen] and `D` for [ChartCard.accountDetail]. zero
 * belonged to the removed "Auto / With 0" axis chip (every axis now takes in 0), ids and month to
 * the removed budget and savings charts: all three are always written empty, so hidden keeps its
 * place. Older versions read the first parts and ignore the rest.
 */
fun encodeLayout(cards: List<ChartCard>): String = cards.joinToString(";") {
    "${it.type.name}.${it.mode.name}.${it.accountId ?: ""}...." + it.hidden.joinToString(",") + "." + (if (it.byCount) "C" else "") +
        "." + (if (it.fitScreen) "F" else "") + (if (it.accountDetail) "D" else "") + "." + it.hiddenCategories.joinToString(",") +
        "." + it.expandedAccounts.joinToString(",") + "." + it.expandedCategories.joinToString(",")
}

/**
 * Null [value] (never saved) gives [defaults]. Unknown charts — from a newer version's backup — and
 * charts not in [offered] are skipped rather than failing the whole list. Ids are 0; see [withIds].
 */
private fun ids(part: String?): List<Long> = part?.split(',')?.mapNotNull { it.toLongOrNull() }.orEmpty()

fun decodeLayout(value: String?, defaults: List<ChartType>, offered: Set<ChartType>): List<ChartCard> {
    if (value == null) return defaults.map { ChartCard(0, it) }
    return value.split(';').filter { it.isNotEmpty() }.mapNotNull { entry ->
        val parts = entry.split('.')
        val type = ChartType.entries.firstOrNull { it.name == parts[0] && it in offered } ?: return@mapNotNull null
        val mode = AnalysisMode.entries.firstOrNull { it.name == parts.getOrNull(1) } ?: AnalysisMode.ALL
        ChartCard(
            0, type, mode, parts.getOrNull(2)?.toLongOrNull(),
            parts.getOrNull(6)?.split(',')?.mapNotNull { it.toLongOrNull() }.orEmpty(),
            byCount = parts.getOrNull(7) == "C",
            fitScreen = parts.getOrNull(8).orEmpty().contains('F'),
            accountDetail = parts.getOrNull(8).orEmpty().contains('D'),
            hiddenCategories = ids(parts.getOrNull(9)),
            expandedAccounts = ids(parts.getOrNull(10)),
            expandedCategories = ids(parts.getOrNull(11)),
        )
    }
}

/**
 * The slot's list from its own row, or — when that was never written — the older Categories and
 * Accounts lists joined in that order, each falling back to its own first-run layout.
 */
inline fun decodeSlot(slot: LayoutSlot, read: (String) -> String?): List<ChartCard> {
    val own = read(slot.settingKey)
    if (own != null || slot.legacy.isEmpty()) return decodeLayout(own, slot.charts, slot.offered)
    return slot.legacy.flatMap { (key, defaults) -> decodeLayout(read(key), defaults, slot.offered) }
}

/** Session ids from [firstId] up, in list order. */
fun List<ChartCard>.withIds(firstId: Long): List<ChartCard> = mapIndexed { i, card -> card.copy(id = firstId + i) }
