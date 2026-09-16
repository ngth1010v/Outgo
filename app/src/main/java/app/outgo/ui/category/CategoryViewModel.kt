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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoryUiState(
    val type: Int = CategoryKind.EXPENSE,
    val parents: List<CategoryEntity> = emptyList(),
    val childrenByParent: Map<Long, List<CategoryEntity>> = emptyMap(),
    val budgetsByCategory: Map<Long, BudgetWithProgress> = emptyMap(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryViewModel(
    private val categoryRepository: CategoryRepository,
    private val budgetRepository: BudgetRepository,
) : ViewModel() {

    private val type = MutableStateFlow(CategoryKind.EXPENSE)

    val state: StateFlow<CategoryUiState> = type.flatMapLatest { t ->
        combine(
            categoryRepository.observeAllOfType(t),
            budgetRepository.observeWithProgress(MonthKey.current()),
        ) { all, budgets ->
            CategoryUiState(
                type = t,
                parents = all.filter { it.parentId == null },
                childrenByParent = all.filter { it.parentId != null }.groupBy { it.parentId!! },
                budgetsByCategory = budgets.filter { it.kind == BudgetKind.LIMIT && it.categoryId != null }
                    .associateBy { it.categoryId!! },
            )
        }
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
