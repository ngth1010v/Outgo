package app.outgo.ui.analysis

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.outgo.R
import app.outgo.domain.CategoryKind

/**
 * What a chart is, what it tells, and how to read it, for the (i) on each card and a long press in
 * the "+" sheet. The account and category charts share one text per chart shape, filled in with
 * which accounts or categories it draws and, for categories, which kind.
 */
private class ChartTexts(
    @StringRes val about: Int,
    @StringRes val meaning: Int,
    @StringRes val how: Int,
    /** Shared by several charts and filled in with which accounts or categories they draw. */
    val templated: Boolean = true,
)

/** The account charts' and category charts' shared texts by shape: by day, breakdown, 5 months. */
private val AccountDaily = ChartTexts(R.string.analysis_info_account_daily_about, R.string.analysis_info_account_daily_meaning, R.string.analysis_info_account_daily_how)
private val AccountShare = ChartTexts(R.string.analysis_info_account_share_about, R.string.analysis_info_account_share_meaning, R.string.analysis_info_account_share_how)
private val AccountMonths = ChartTexts(R.string.analysis_info_account_months_about, R.string.analysis_info_account_months_meaning, R.string.analysis_info_account_months_how)
private val CategoryDaily = ChartTexts(R.string.analysis_info_category_daily_about, R.string.analysis_info_category_daily_meaning, R.string.analysis_info_category_daily_how)
private val CategoryShare = ChartTexts(R.string.analysis_info_category_share_about, R.string.analysis_info_category_share_meaning, R.string.analysis_info_category_share_how)
private val CategoryMonths = ChartTexts(R.string.analysis_info_category_months_about, R.string.analysis_info_category_months_meaning, R.string.analysis_info_category_months_how)

/** The trade-count category charts: by day and breakdown; the pie is read like the money one. */
private val CategoryCountDaily = ChartTexts(R.string.analysis_info_category_count_daily_about, R.string.analysis_info_category_count_daily_meaning, R.string.analysis_info_category_count_daily_how)
private val CategoryCountShare = ChartTexts(R.string.analysis_info_category_count_share_about, R.string.analysis_info_category_count_share_meaning, R.string.analysis_info_category_share_how)
private val YearCategoryCountMonthly = ChartTexts(R.string.analysis_info_year_category_count_monthly_about, R.string.analysis_info_year_category_count_monthly_meaning, R.string.analysis_info_year_category_count_monthly_how)
private val YearCategoryCountShare = ChartTexts(R.string.analysis_info_year_category_count_share_about, R.string.analysis_info_year_category_count_share_meaning, R.string.analysis_info_year_category_share_how)

/** The year page's account and category charts' shared texts by shape: by month, breakdown, 5 years. */
private val YearAccountMonthly = ChartTexts(R.string.analysis_info_year_account_monthly_about, R.string.analysis_info_year_account_monthly_meaning, R.string.analysis_info_year_account_monthly_how)
private val YearAccountShare = ChartTexts(R.string.analysis_info_year_account_share_about, R.string.analysis_info_year_account_share_meaning, R.string.analysis_info_year_account_share_how)
private val YearAccountYears = ChartTexts(R.string.analysis_info_year_account_years_about, R.string.analysis_info_year_account_years_meaning, R.string.analysis_info_year_account_years_how)
private val YearCategoryMonthly = ChartTexts(R.string.analysis_info_year_category_monthly_about, R.string.analysis_info_year_category_monthly_meaning, R.string.analysis_info_year_category_monthly_how)
private val YearCategoryShare = ChartTexts(R.string.analysis_info_year_category_share_about, R.string.analysis_info_year_category_share_meaning, R.string.analysis_info_year_category_share_how)
private val YearCategoryYears = ChartTexts(R.string.analysis_info_year_category_years_about, R.string.analysis_info_year_category_years_meaning, R.string.analysis_info_year_category_years_how)

/** A chart's own texts, named analysis_info_<key>_about / _meaning / _how. */
private fun own(about: Int, meaning: Int, how: Int) = ChartTexts(about, meaning, how, templated = false)

private fun textsOf(type: ChartType): ChartTexts = when (type) {
    ChartType.ACCOUNT_DAILY, ChartType.SUBACCOUNT_DAILY -> AccountDaily
    ChartType.ACCOUNT_SHARE, ChartType.SUBACCOUNT_SHARE -> AccountShare
    ChartType.ACCOUNT_MONTHS, ChartType.SUBACCOUNT_MONTHS -> AccountMonths
    ChartType.EXPENSE_CATEGORY_DAILY, ChartType.EXPENSE_SUBCATEGORY_DAILY,
    ChartType.INCOME_CATEGORY_DAILY, ChartType.INCOME_SUBCATEGORY_DAILY -> CategoryDaily
    ChartType.EXPENSE_CATEGORY_SHARE, ChartType.EXPENSE_SUBCATEGORY_SHARE,
    ChartType.INCOME_CATEGORY_SHARE, ChartType.INCOME_SUBCATEGORY_SHARE -> CategoryShare
    ChartType.EXPENSE_CATEGORY_MONTHS, ChartType.EXPENSE_SUBCATEGORY_MONTHS,
    ChartType.INCOME_CATEGORY_MONTHS, ChartType.INCOME_SUBCATEGORY_MONTHS -> CategoryMonths
    ChartType.EXPENSE_CATEGORY_COUNT_DAILY, ChartType.EXPENSE_SUBCATEGORY_COUNT_DAILY,
    ChartType.INCOME_CATEGORY_COUNT_DAILY, ChartType.INCOME_SUBCATEGORY_COUNT_DAILY -> CategoryCountDaily
    ChartType.EXPENSE_CATEGORY_COUNT_SHARE, ChartType.EXPENSE_SUBCATEGORY_COUNT_SHARE,
    ChartType.INCOME_CATEGORY_COUNT_SHARE, ChartType.INCOME_SUBCATEGORY_COUNT_SHARE -> CategoryCountShare
    ChartType.YEAR_EXPENSE_CATEGORY_COUNT_MONTHLY, ChartType.YEAR_EXPENSE_SUBCATEGORY_COUNT_MONTHLY,
    ChartType.YEAR_INCOME_CATEGORY_COUNT_MONTHLY, ChartType.YEAR_INCOME_SUBCATEGORY_COUNT_MONTHLY -> YearCategoryCountMonthly
    ChartType.YEAR_EXPENSE_CATEGORY_COUNT_SHARE, ChartType.YEAR_EXPENSE_SUBCATEGORY_COUNT_SHARE,
    ChartType.YEAR_INCOME_CATEGORY_COUNT_SHARE, ChartType.YEAR_INCOME_SUBCATEGORY_COUNT_SHARE -> YearCategoryCountShare
    ChartType.SUMMARY -> own(R.string.analysis_info_summary_about, R.string.analysis_info_summary_meaning, R.string.analysis_info_summary_how)
    ChartType.TRADE_CALENDAR -> own(R.string.analysis_info_trade_calendar_about, R.string.analysis_info_trade_calendar_meaning, R.string.analysis_info_trade_calendar_how)
    ChartType.TRADE_WEEK -> own(R.string.analysis_info_trade_week_about, R.string.analysis_info_trade_week_meaning, R.string.analysis_info_trade_week_how)
    ChartType.TRADE_SIZES -> own(R.string.analysis_info_trade_sizes_about, R.string.analysis_info_trade_sizes_meaning, R.string.analysis_info_trade_sizes_how)
    ChartType.TRADE_TOP -> own(R.string.analysis_info_trade_top_about, R.string.analysis_info_trade_top_meaning, R.string.analysis_info_trade_top_how)
    ChartType.TRADE_FREQUENCY -> own(R.string.analysis_info_trade_frequency_about, R.string.analysis_info_trade_frequency_meaning, R.string.analysis_info_trade_frequency_how)
    ChartType.TRADE_TRANSFERS -> own(R.string.analysis_info_trade_transfers_about, R.string.analysis_info_trade_transfers_meaning, R.string.analysis_info_trade_transfers_how)
    ChartType.TRADE_CASH_FLOW -> own(R.string.analysis_info_trade_cash_flow_about, R.string.analysis_info_trade_cash_flow_meaning, R.string.analysis_info_trade_cash_flow_how)
    ChartType.SAVING_PACE -> own(R.string.analysis_info_saving_pace_about, R.string.analysis_info_saving_pace_meaning, R.string.analysis_info_saving_pace_how)
    ChartType.SAVING_BURNUP -> own(R.string.analysis_info_saving_burnup_about, R.string.analysis_info_saving_burnup_meaning, R.string.analysis_info_saving_burnup_how)
    ChartType.SAVING_HISTORY -> own(R.string.analysis_info_saving_history_about, R.string.analysis_info_saving_history_meaning, R.string.analysis_info_saving_history_how)
    ChartType.SAVING_RATE -> own(R.string.analysis_info_saving_rate_about, R.string.analysis_info_saving_rate_meaning, R.string.analysis_info_saving_rate_how)
    ChartType.SAVING_SOURCES -> own(R.string.analysis_info_saving_sources_about, R.string.analysis_info_saving_sources_meaning, R.string.analysis_info_saving_sources_how)
    ChartType.BUDGET_FORECAST -> own(R.string.analysis_info_budget_forecast_about, R.string.analysis_info_budget_forecast_meaning, R.string.analysis_info_budget_forecast_how)
    ChartType.BUDGET_BURNDOWN -> own(R.string.analysis_info_budget_burndown_about, R.string.analysis_info_budget_burndown_meaning, R.string.analysis_info_budget_burndown_how)
    ChartType.BUDGET_HISTORY -> own(R.string.analysis_info_budget_history_about, R.string.analysis_info_budget_history_meaning, R.string.analysis_info_budget_history_how)
    ChartType.BUDGET_WATERFALL -> own(R.string.analysis_info_budget_waterfall_about, R.string.analysis_info_budget_waterfall_meaning, R.string.analysis_info_budget_waterfall_how)
    ChartType.BUDGET_COVERAGE -> own(R.string.analysis_info_budget_coverage_about, R.string.analysis_info_budget_coverage_meaning, R.string.analysis_info_budget_coverage_how)
    ChartType.BUDGET_REACHED -> own(R.string.analysis_info_budget_reached_about, R.string.analysis_info_budget_reached_meaning, R.string.analysis_info_budget_reached_how)
    ChartType.YEAR_ACCOUNT_MONTHLY, ChartType.YEAR_SUBACCOUNT_MONTHLY -> YearAccountMonthly
    ChartType.YEAR_ACCOUNT_SHARE, ChartType.YEAR_SUBACCOUNT_SHARE -> YearAccountShare
    ChartType.YEAR_ACCOUNT_YEARS, ChartType.YEAR_SUBACCOUNT_YEARS -> YearAccountYears
    ChartType.YEAR_EXPENSE_CATEGORY_MONTHLY, ChartType.YEAR_EXPENSE_SUBCATEGORY_MONTHLY,
    ChartType.YEAR_INCOME_CATEGORY_MONTHLY, ChartType.YEAR_INCOME_SUBCATEGORY_MONTHLY -> YearCategoryMonthly
    ChartType.YEAR_EXPENSE_CATEGORY_SHARE, ChartType.YEAR_EXPENSE_SUBCATEGORY_SHARE,
    ChartType.YEAR_INCOME_CATEGORY_SHARE, ChartType.YEAR_INCOME_SUBCATEGORY_SHARE -> YearCategoryShare
    ChartType.YEAR_EXPENSE_CATEGORY_YEARS, ChartType.YEAR_EXPENSE_SUBCATEGORY_YEARS,
    ChartType.YEAR_INCOME_CATEGORY_YEARS, ChartType.YEAR_INCOME_SUBCATEGORY_YEARS -> YearCategoryYears
    ChartType.YEAR_SUMMARY -> own(R.string.analysis_info_year_summary_about, R.string.analysis_info_year_summary_meaning, R.string.analysis_info_year_summary_how)
    ChartType.YEAR_TRADE_WEEK -> own(R.string.analysis_info_year_trade_week_about, R.string.analysis_info_trade_week_meaning, R.string.analysis_info_trade_week_how)
    ChartType.YEAR_TRADE_SIZES -> own(R.string.analysis_info_year_trade_sizes_about, R.string.analysis_info_trade_sizes_meaning, R.string.analysis_info_trade_sizes_how)
    ChartType.YEAR_TRADE_TOP -> own(R.string.analysis_info_year_trade_top_about, R.string.analysis_info_year_trade_top_meaning, R.string.analysis_info_trade_top_how)
    ChartType.YEAR_TRADE_FREQUENCY -> own(R.string.analysis_info_year_trade_frequency_about, R.string.analysis_info_trade_frequency_meaning, R.string.analysis_info_trade_frequency_how)
    ChartType.YEAR_TRADE_TRANSFERS -> own(R.string.analysis_info_year_trade_transfers_about, R.string.analysis_info_trade_transfers_meaning, R.string.analysis_info_trade_transfers_how)
    ChartType.YEAR_TRADE_CASH_FLOW -> own(R.string.analysis_info_year_trade_cash_flow_about, R.string.analysis_info_trade_cash_flow_meaning, R.string.analysis_info_trade_cash_flow_how)
    ChartType.YEAR_SAVING_PACE -> own(R.string.analysis_info_year_saving_pace_about, R.string.analysis_info_year_saving_pace_meaning, R.string.analysis_info_saving_pace_how)
    ChartType.YEAR_SAVING_HISTORY -> own(R.string.analysis_info_year_saving_history_about, R.string.analysis_info_saving_history_meaning, R.string.analysis_info_saving_history_how)
    ChartType.YEAR_SAVING_RATE -> own(R.string.analysis_info_year_saving_rate_about, R.string.analysis_info_saving_rate_meaning, R.string.analysis_info_saving_rate_how)
    ChartType.YEAR_SAVING_SOURCES -> own(R.string.analysis_info_year_saving_sources_about, R.string.analysis_info_saving_sources_meaning, R.string.analysis_info_saving_sources_how)
    ChartType.YEAR_BUDGET_TOTALS -> own(R.string.analysis_info_year_budget_totals_about, R.string.analysis_info_year_budget_totals_meaning, R.string.analysis_info_year_budget_totals_how)
    ChartType.YEAR_BUDGET_HISTORY -> own(R.string.analysis_info_year_budget_history_about, R.string.analysis_info_budget_history_meaning, R.string.analysis_info_budget_history_how)
    ChartType.YEAR_BUDGET_COVERAGE -> own(R.string.analysis_info_year_budget_coverage_about, R.string.analysis_info_year_budget_coverage_meaning, R.string.analysis_info_budget_coverage_how)
    ChartType.YEAR_BUDGET_REACHED -> own(R.string.analysis_info_year_budget_reached_about, R.string.analysis_info_budget_reached_meaning, R.string.analysis_info_budget_reached_how)
}

/** Which accounts or categories an account/category chart draws, as its texts' first argument. */
@Composable
private fun scopeOf(type: ChartType): String = stringResource(
    when {
        type.categoryKind != null && type.hasParent -> R.string.analysis_info_scope_subcategory
        type.categoryKind != null -> R.string.analysis_info_scope_category
        type.hasParent -> R.string.analysis_info_scope_subaccount
        else -> R.string.analysis_info_scope_account
    },
)

/** The three paragraphs of [type]'s description, filled in. */
@Composable
fun chartInfo(type: ChartType): Triple<String, String, String> {
    val texts = textsOf(type)
    if (!texts.templated) return Triple(stringResource(texts.about), stringResource(texts.meaning), stringResource(texts.how))
    // Category texts take the kind first, then the scope; account texts take the scope only.
    val args: Array<Any> = when (type.categoryKind) {
        null -> arrayOf(scopeOf(type))
        else -> arrayOf(
            stringResource(if (type.categoryKind == CategoryKind.INCOME) R.string.analysis_info_kind_income else R.string.analysis_info_kind_expense),
            scopeOf(type),
        )
    }
    return Triple(stringResource(texts.about, *args), stringResource(texts.meaning, *args), stringResource(texts.how, *args))
}

/** [type]'s description in a dialog titled [title]: what it is, what it means, how to read it. */
@Composable
fun ChartInfoDialog(type: ChartType, title: String, onDismiss: () -> Unit) {
    val (about, meaning, how) = chartInfo(type)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoPart(stringResource(R.string.analysis_info_about), about)
                InfoPart(stringResource(R.string.analysis_info_meaning), meaning)
                InfoPart(stringResource(R.string.analysis_info_how), how)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_ok)) } },
    )
}

@Composable
private fun InfoPart(heading: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(heading, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
