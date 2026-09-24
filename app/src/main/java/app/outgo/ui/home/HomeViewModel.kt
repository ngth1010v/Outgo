package app.outgo.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.outgo.data.db.dao.AccountWithProgress
import app.outgo.data.db.dao.BudgetWithProgress
import app.outgo.data.repo.AccountRepository
import app.outgo.data.repo.BudgetRepository
import app.outgo.data.repo.SettingRepository
import app.outgo.domain.AccountType
import app.outgo.util.MonthKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val availableBalance: Long = 0,
    val savingsBalance: Long = 0,
    val budgets: List<BudgetWithProgress> = emptyList(),
    val savingsAccounts: List<AccountWithProgress> = emptyList(),
    val availableBalanceHidden: Boolean = false,
    val otherBalancesHidden: Boolean = false,
) {
    val totalBalance: Long get() = availableBalance + savingsBalance
}

class HomeViewModel(
    accountRepository: AccountRepository,
    budgetRepository: BudgetRepository,
    private val settingRepository: SettingRepository,
) : ViewModel() {

    // The mask flags ride in the same combine as the balances on purpose: the first emission that
    // carries real balances carries the real flags too, so a figure the user chose to hide never
    // flashes while the setting row is still loading.
    val state: StateFlow<HomeUiState> = combine(
        budgetRepository.observeWithProgress(MonthKey.current()),
        accountRepository.observeActiveWithProgress(MonthKey.current()),
        settingRepository.observeAvailableBalanceHidden(),
        settingRepository.observeOtherBalancesHidden(),
    ) { budgets, accounts, availableHidden, otherHidden ->
        val (savings, normal) = accounts.partition { it.account.accountType == AccountType.SAVINGS }
        HomeUiState(
            availableBalance = normal.sumOf { it.account.balance },
            savingsBalance = savings.sumOf { it.account.balance },
            budgets = budgets,
            savingsAccounts = savings,
            availableBalanceHidden = availableHidden,
            otherBalancesHidden = otherHidden,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun toggleAvailableBalance() {
        val hidden = state.value.availableBalanceHidden
        viewModelScope.launch { settingRepository.setAvailableBalanceHidden(!hidden) }
    }

    fun toggleOtherBalances() {
        val hidden = state.value.otherBalancesHidden
        viewModelScope.launch { settingRepository.setOtherBalancesHidden(!hidden) }
    }
}
