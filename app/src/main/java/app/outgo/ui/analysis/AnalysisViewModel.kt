package app.outgo.ui.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.outgo.data.db.dao.MonthCategoryTotal
import app.outgo.data.db.dao.StatDao
import app.outgo.domain.CategoryKind
import app.outgo.util.MonthKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** One category row in the Most income / Most expense sections. */
data class CategoryStat(
    val rootId: Long,
    val name: String,
    val iconId: Long?,
    val color: Int,
    val total: Long,
    /** vs. previous month; null when there's nothing to compare (both months zero). */
    val deviationPercent: Int?,
)

data class AnalysisUiState(
    val chartTotals: List<MonthCategoryTotal> = emptyList(),
    val chartMonths: List<Int> = emptyList(),
    val topIncome: List<CategoryStat> = emptyList(),
    val topExpense: List<CategoryStat> = emptyList(),
)

private const val TOP_INCOME_COUNT = 3
private const val TOP_EXPENSE_COUNT = 6

class AnalysisViewModel(statDao: StatDao) : ViewModel() {

    private val currentMonth = MonthKey.current()
    private val previousMonth = MonthKey.minus(currentMonth, 1)
    private val months = listOf(previousMonth, currentMonth)

    val state: StateFlow<AnalysisUiState> = statDao.observeMonthlyTotals(previousMonth, currentMonth)
        .map { totals ->
            val byMonth = totals.groupBy { it.monthKey }
            val current = byMonth[currentMonth].orEmpty()
            val previousByRoot = byMonth[previousMonth].orEmpty().associateBy { it.rootId }

            fun topOf(type: Int, count: Int) = current
                .filter { it.type == type }
                .sortedByDescending { it.total }
                .take(count)
                .map { it.toStat(previousByRoot[it.rootId]?.total ?: 0L) }

            AnalysisUiState(
                chartTotals = totals,
                chartMonths = months,
                topIncome = topOf(CategoryKind.INCOME, TOP_INCOME_COUNT),
                topExpense = topOf(CategoryKind.EXPENSE, TOP_EXPENSE_COUNT),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalysisUiState(chartMonths = months))
}

private fun MonthCategoryTotal.toStat(previousTotal: Long): CategoryStat {
    // ponytail: no previous-month baseline reads as "+100%" rather than undefined/infinite — good enough for a 3/6-row glance, revisit if it looks wrong in practice.
    val deviation = when {
        previousTotal == 0L && total == 0L -> 0
        previousTotal == 0L -> 100
        else -> ((total - previousTotal) * 100 / previousTotal).toInt()
    }
    return CategoryStat(rootId = rootId, name = name, iconId = iconId, color = color, total = total, deviationPercent = deviation)
}
