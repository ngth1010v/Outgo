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
    val deviationPercent: Int,
)

data class AnalysisUiState(
    val chartTotals: List<MonthCategoryTotal> = emptyList(),
    val chartMonths: List<Int> = emptyList(),
    /** Fixed-size (TOP_INCOME_COUNT / TOP_EXPENSE_COUNT); null entries render as a placeholder row. */
    val topIncome: List<CategoryStat?> = List(TOP_INCOME_COUNT) { null },
    val topExpense: List<CategoryStat?> = List(TOP_EXPENSE_COUNT) { null },
)

const val TOP_INCOME_COUNT = 3
const val TOP_EXPENSE_COUNT = 6

class AnalysisViewModel(statDao: StatDao) : ViewModel() {

    private val currentMonth = MonthKey.current()
    private val previousMonth = MonthKey.minus(currentMonth, 1)
    private val months = listOf(previousMonth, currentMonth)

    val state: StateFlow<AnalysisUiState> = statDao.observeMonthlyTotals(previousMonth, currentMonth)
        .map { totals ->
            val byMonth = totals.groupBy { it.monthKey }
            val current = byMonth[currentMonth].orEmpty()
            val previous = byMonth[previousMonth].orEmpty()
            val previousByRoot = previous.associateBy { it.rootId }

            // Current month's top categories first; if there aren't enough, fall back to
            // categories that had spend/income last month but none this month (shown at 0,
            // -100%) so the section doesn't look emptier than it has to. Still-short lists are
            // padded with nulls -> placeholder rows.
            fun topOf(type: Int, count: Int): List<CategoryStat?> {
                val currentOfType = current.filter { it.type == type }.sortedByDescending { it.total }
                val currentIds = currentOfType.map { it.rootId }.toSet()
                val stats = currentOfType.take(count)
                    .map { buildStat(it.rootId, it.name, it.iconId, it.color, currentTotal = it.total, previousTotal = previousByRoot[it.rootId]?.total ?: 0L) }
                    .toMutableList<CategoryStat?>()

                if (stats.size < count) {
                    previous.filter { it.type == type && it.rootId !in currentIds }
                        .sortedByDescending { it.total }
                        .take(count - stats.size)
                        .forEach { stats += buildStat(it.rootId, it.name, it.iconId, it.color, currentTotal = 0L, previousTotal = it.total) }
                }
                while (stats.size < count) stats += null
                return stats
            }

            AnalysisUiState(
                chartTotals = totals,
                chartMonths = months,
                topIncome = topOf(CategoryKind.INCOME, TOP_INCOME_COUNT),
                topExpense = topOf(CategoryKind.EXPENSE, TOP_EXPENSE_COUNT),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalysisUiState(chartMonths = months))
}

private fun buildStat(rootId: Long, name: String, iconId: Long?, color: Int, currentTotal: Long, previousTotal: Long): CategoryStat {
    // ponytail: no previous-month baseline reads as "+100%" rather than undefined/infinite — good enough for a glance, revisit if it looks wrong in practice.
    val deviation = when {
        previousTotal == 0L && currentTotal == 0L -> 0
        previousTotal == 0L -> 100
        else -> ((currentTotal - previousTotal) * 100 / previousTotal).toInt()
    }
    return CategoryStat(rootId = rootId, name = name, iconId = iconId, color = color, total = currentTotal, deviationPercent = deviation)
}
