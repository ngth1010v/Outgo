package app.outgo.ui.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.outgo.data.db.dao.BudgetWithProgress
import app.outgo.data.db.entity.AccountEntity
import app.outgo.data.db.entity.CategoryEntity
import app.outgo.data.repo.AccountRepository
import app.outgo.data.repo.BudgetRepository
import app.outgo.data.repo.BudgetSetting
import app.outgo.data.repo.CategoryRepository
import app.outgo.domain.BudgetKind
import app.outgo.domain.CategoryKind
import app.outgo.util.MonthKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class CategoryUiState(
    val type: Int = CategoryKind.EXPENSE,
    val parents: List<CategoryEntity> = emptyList(),
    val childrenByParent: Map<Long, List<CategoryEntity>> = emptyMap(),
    val budgetsByCategory: Map<Long, BudgetWithProgress> = emptyMap(),
    /** The active accounts, for a budget's account offset. */
    val accounts: List<AccountEntity> = emptyList(),
    /** The other type's lists, for the swipe preview; null inside it. */
    val other: CategoryUiState? = null,
)

class CategoryViewModel(
    private val categoryRepository: CategoryRepository,
    private val budgetRepository: BudgetRepository,
    accountRepository: AccountRepository,
) : ViewModel() {

    private val type = MutableStateFlow(CategoryKind.EXPENSE)

    // Both types observed at once, so a swipe can draw the other type's list too.
    val state: StateFlow<CategoryUiState> = combine(
        type,
        categoryRepository.observeAllOfType(CategoryKind.EXPENSE),
        categoryRepository.observeAllOfType(CategoryKind.INCOME),
        budgetRepository.observeWithProgress(MonthKey.current()),
        accountRepository.observeAll(),
    ) { t, expense, income, budgets, allAccounts ->
        val accounts = allAccounts.filter { !it.archived }
        val budgetsByCategory = budgets.filter { it.kind == BudgetKind.LIMIT && it.categoryId != null }
            .associateBy { it.categoryId!! }
        fun ofType(kind: Int, all: List<CategoryEntity>) = CategoryUiState(
            type = kind,
            parents = all.filter { it.parentId == null },
            childrenByParent = all.filter { it.parentId != null }.groupBy { it.parentId!! },
            budgetsByCategory = budgetsByCategory,
            accounts = accounts,
        )
        val expenseState = ofType(CategoryKind.EXPENSE, expense)
        val incomeState = ofType(CategoryKind.INCOME, income)
        if (t == CategoryKind.EXPENSE) expenseState.copy(other = incomeState) else incomeState.copy(other = expenseState)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryUiState())

    fun setType(newType: Int) {
        type.value = newType
    }

    suspend fun childCount(parentId: Long): Int = categoryRepository.childCount(parentId)
    suspend fun hasTrades(categoryId: Long): Boolean = categoryRepository.hasTrades(categoryId)

    fun createParent(name: String, iconId: Long?, color: Int, budget: BudgetSetting?, monthKey: Int, defaultChildName: String) {
        write { categoryRepository.createParent(type.value, name, iconId, color, budget, monthKey, defaultChildName) }
    }

    fun createChild(parentId: Long, name: String, iconId: Long?, color: Int, budget: BudgetSetting?, monthKey: Int) {
        write { categoryRepository.createChild(parentId, name, iconId, color, budget, monthKey) }
    }

    fun update(category: CategoryEntity, name: String, iconId: Long?, color: Int) {
        write { categoryRepository.update(category, name, iconId, color) }
    }

    suspend fun budgetAt(categoryId: Long, monthKey: Int): BudgetSetting? = 
        // After any queued save, so a month just left reads back what was saved.
        writes.withLock { budgetRepository.settingAt(categoryId, monthKey) }

    fun setBudgetMonth(categoryId: Long, monthKey: Int, setting: BudgetSetting) {
        write { budgetRepository.setMonth(categoryId, monthKey, setting) }
    }

    /** Runs editor saves one after another, in the order they were asked for. */
    private val writes = Mutex()
    private fun write(block: suspend () -> Unit) {
        viewModelScope.launch { writes.withLock { block() } }
    }

    fun reorder(lists: Map<Long?, List<Long>>) {
        viewModelScope.launch { categoryRepository.reorder(lists) }
    }

    fun deleteOrArchive(category: CategoryEntity) {
        write { categoryRepository.deleteOrArchive(category) }
    }
}
