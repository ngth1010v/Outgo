package app.outgo.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.outgo.data.db.dao.AccountWithProgress
import app.outgo.data.db.dao.BudgetWithProgress
import app.outgo.data.db.entity.SettingKeys
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

/** The five figures at the top of Home, in default display order, each with its own persisted mask. */
enum class HomeBalance(val hiddenKey: String, val legacyHiddenKey: String? = null) {
    FREE(SettingKeys.FREE_BALANCE_HIDDEN),
    BUDGETS(SettingKeys.BUDGETS_BALANCE_HIDDEN),
    SAVINGS(SettingKeys.SAVINGS_BALANCE_HIDDEN, SettingKeys.OTHER_BALANCES_HIDDEN),
    AVAILABLE(SettingKeys.AVAILABLE_BALANCE_HIDDEN),
    TOTAL(SettingKeys.TOTAL_BALANCE_HIDDEN, SettingKeys.OTHER_BALANCES_HIDDEN),
}

data class HomeUiState(
    val availableBalance: Long = 0,
    val savingsBalance: Long = 0,
    val budgets: List<BudgetWithProgress> = emptyList(),
    val savingsAccounts: List<AccountWithProgress> = emptyList(),
    val hidden: Set<HomeBalance> = emptySet(),
    /**
     * The balance rows in the user's order; the first is drawn large. Empty until the saved order
     * has loaded, so Home never draws a default order that then reshuffles and morphs on open.
     */
    val order: List<HomeBalance> = emptyList(),
) {
    val totalBalance: Long get() = availableBalance + savingsBalance

    /** What the active budgets still have left this month. An overspent budget counts 0, not
     *  negative, so it does not eat into what the other budgets still need. A parent summing its
     *  subcategories' limits already stands for them, so they don't count again. */
    val budgetRemaining: Long get() {
        val summing = budgets.filter { it.sumChildren }.mapNotNullTo(HashSet()) { it.categoryId }
        return budgets.filter { it.parentCategoryId !in summing }.sumOf { (it.effectiveLimit - it.spent).coerceAtLeast(0) }
    }

    /** The part of the available money set aside for the remaining budgets. */
    val budgetsBalance: Long get() = minOf(budgetRemaining, availableBalance).coerceAtLeast(0)

    /** How much the available money falls short of covering the remaining budgets; 0 when it covers them. */
    val budgetShortfall: Long get() = (budgetRemaining - availableBalance.coerceAtLeast(0)).coerceAtLeast(0)

    /** Total - savings - budgets, i.e. available money no budget has a claim on. */
    val freeBalance: Long get() = (totalBalance - savingsBalance - budgetsBalance).coerceAtLeast(0)

    fun amount(balance: HomeBalance): Long = when (balance) {
        HomeBalance.FREE -> freeBalance
        HomeBalance.BUDGETS -> budgetsBalance
        HomeBalance.SAVINGS -> savingsBalance
        HomeBalance.AVAILABLE -> availableBalance
        HomeBalance.TOTAL -> totalBalance
    }
}

class HomeViewModel(
    accountRepository: AccountRepository,
    budgetRepository: BudgetRepository,
    private val settingRepository: SettingRepository,
) : ViewModel() {

    private val hidden = combine(
        HomeBalance.entries.map { balance ->
            settingRepository.observeBalanceHidden(balance.hiddenKey, balance.legacyHiddenKey)
        },
    ) { flags -> HomeBalance.entries.filterIndexedTo(mutableSetOf()) { i, _ -> flags[i] } }

    // The mask flags ride in the same combine as the balances on purpose: the first emission that
    // carries real balances carries the real flags too, so a figure the user chose to hide never
    // flashes while the setting rows are still loading.
    val state: StateFlow<HomeUiState> = combine(
        budgetRepository.observeWithProgress(MonthKey.current()),
        accountRepository.observeActiveWithProgress(MonthKey.current()),
        hidden,
        settingRepository.observeHomeBalanceOrder(),
    ) { budgets, accounts, hidden, order ->
        val (savings, normal) = accounts.partition { it.account.accountType == AccountType.SAVINGS }
        HomeUiState(
            availableBalance = normal.sumOf { it.account.balance },
            savingsBalance = savings.sumOf { it.account.balance },
            // Paused budgets and ones whose apply-from is still ahead have no bar this month.
            budgets = budgets.filter { it.enabled },
            // A savings parent stands for its subaccounts, unless one has a target of its own.
            savingsAccounts = savings.filter { row ->
                row.account.isParent || row.monthlyTarget != null ||
                    savings.none { it.account.id == row.account.parentId }
            },
            hidden = hidden,
            order = parseOrder(order),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setOrder(order: List<HomeBalance>) {
        viewModelScope.launch { settingRepository.setHomeBalanceOrder(order.joinToString(",") { it.name }) }
    }

    fun toggleHidden(balance: HomeBalance) {
        val hidden = balance in state.value.hidden
        viewModelScope.launch { settingRepository.setBalanceHidden(balance.hiddenKey, !hidden) }
    }
}

/** Stored names in order, skipping unknown ones, then any balance the stored value lacks. */
private fun parseOrder(stored: String?): List<HomeBalance> {
    if (stored.isNullOrBlank()) return HomeBalance.entries
    val listed = stored.split(',').mapNotNull { name -> HomeBalance.entries.firstOrNull { it.name == name } }.distinct()
    return listed + HomeBalance.entries.filter { it !in listed }
}
