package app.outgo.ui.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.outgo.data.db.dao.BudgetWithProgress
import app.outgo.data.db.entity.CategoryEntity
import app.outgo.data.repo.BudgetRepository
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

data class CategoryUiState(
    val type: Int = CategoryKind.EXPENSE,
    val parents: List<CategoryEntity> = emptyList(),
    val childrenByParent: Map<Long, List<CategoryEntity>> = emptyMap(),
    val budgetsByCategory: Map<Long, BudgetWithProgress> = emptyMap(),
    /** The other type's lists, for the swipe preview; null inside it. */
    val other: CategoryUiState? = null,
)

class CategoryViewModel(
    private val categoryRepository: CategoryRepository,
    private val budgetRepository: BudgetRepository,
) : ViewModel() {

    private val type = MutableStateFlow(CategoryKind.EXPENSE)

    // Both types observed at once, so a swipe can draw the other type's list too.
    val state: StateFlow<CategoryUiState> = combine(
        type,
        categoryRepository.observeAllOfType(CategoryKind.EXPENSE),
        categoryRepository.observeAllOfType(CategoryKind.INCOME),
        budgetRepository.observeWithProgress(MonthKey.current()),
    ) { t, expense, income, budgets ->
        val budgetsByCategory = budgets.filter { it.kind == BudgetKind.LIMIT && it.categoryId != null }
            .associateBy { it.categoryId!! }
        fun ofType(kind: Int, all: List<CategoryEntity>) = CategoryUiState(
            type = kind,
            parents = all.filter { it.parentId == null },
            childrenByParent = all.filter { it.parentId != null }.groupBy { it.parentId!! },
            budgetsByCategory = budgetsByCategory,
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

    fun createParent(name: String, iconId: Long?, color: Int, budget: Long?, defaultChildName: String) {
        viewModelScope.launch { categoryRepository.createParent(type.value, name, iconId, color, budget, defaultChildName) }
    }

    fun createChild(parentId: Long, name: String, iconId: Long?, color: Int, budget: Long?) {
        viewModelScope.launch { categoryRepository.createChild(parentId, name, iconId, color, budget) }
    }

    fun update(category: CategoryEntity, name: String, iconId: Long?, color: Int, budget: Long?) {
        viewModelScope.launch { categoryRepository.update(category, name, iconId, color, budget) }
    }

    fun deleteOrArchive(category: CategoryEntity) {
        viewModelScope.launch { categoryRepository.deleteOrArchive(category) }
    }
}
