package app.outgo.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.outgo.data.db.dao.AccountWithProgress
import app.outgo.data.db.dao.BudgetWithProgress
import app.outgo.data.repo.AccountRepository
import app.outgo.data.repo.BudgetRepository
import app.outgo.domain.AccountType
import app.outgo.util.MonthKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val availableBalance: Long = 0,
    val savingsBalance: Long = 0,
    val budgets: List<BudgetWithProgress> = emptyList(),
    val savingsAccounts: List<AccountWithProgress> = emptyList(),
) {
    val totalBalance: Long get() = availableBalance + savingsBalance
}

class HomeViewModel(
    accountRepository: AccountRepository,
    budgetRepository: BudgetRepository,
) : ViewModel() {

    val state: StateFlow<HomeUiState> = combine(
        budgetRepository.observeWithProgress(MonthKey.current()),
        accountRepository.observeActiveWithProgress(MonthKey.current()),
    ) { budgets, accounts ->
        val (savings, normal) = accounts.partition { it.account.accountType == AccountType.SAVINGS }
        HomeUiState(
            availableBalance = normal.sumOf { it.account.balance },
            savingsBalance = savings.sumOf { it.account.balance },
            budgets = budgets,
            savingsAccounts = savings,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
