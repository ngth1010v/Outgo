package app.outgo.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.outgo.data.db.dao.BudgetWithProgress
import app.outgo.data.db.dao.MonthCategoryTotal
import app.outgo.data.db.dao.StatDao
import app.outgo.data.repo.AccountRepository
import app.outgo.data.repo.BudgetRepository
import app.outgo.util.MonthKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val totalBalance: Long = 0,
    val budgets: List<BudgetWithProgress> = emptyList(),
    val monthlyTotals: List<MonthCategoryTotal> = emptyList(),
    val months: List<Int> = emptyList(),
)

class HomeViewModel(
    accountRepository: AccountRepository,
    budgetRepository: BudgetRepository,
    statDao: StatDao,
) : ViewModel() {

    private val months = MonthKey.lastN(MonthKey.current(), 5)

    val state: StateFlow<HomeUiState> = combine(
        accountRepository.observeTotalBalance(),
        budgetRepository.observeWithProgress(MonthKey.current()),
        statDao.observeMonthlyTotals(months.first(), months.last()),
    ) { total, budgets, totals ->
        HomeUiState(totalBalance = total, budgets = budgets, monthlyTotals = totals, months = months)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(months = months))
}
