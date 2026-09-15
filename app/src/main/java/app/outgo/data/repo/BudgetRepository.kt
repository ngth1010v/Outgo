package app.outgo.data.repo

import app.outgo.data.db.dao.BudgetDao
import app.outgo.data.db.dao.BudgetWithProgress
import app.outgo.data.db.entity.BudgetEntity
import app.outgo.domain.BudgetKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class BudgetRepository(private val budgetDao: BudgetDao) {

    fun observeWithProgress(monthKey: Int): Flow<List<BudgetWithProgress>> =
        budgetDao.observeBudgetsWithProgress(monthKey)

    suspend fun findByCategory(categoryId: Long): BudgetEntity? =
        withContext(Dispatchers.IO) { budgetDao.findByCategory(categoryId) }

    suspend fun findById(id: Long): BudgetEntity? = withContext(Dispatchers.IO) { budgetDao.findById(id) }

    suspend fun delete(budget: BudgetEntity) = withContext(Dispatchers.IO) { budgetDao.delete(budget) }

    /** Creates/updates/removes the single LIMIT budget for a category. Pass null or <= 0 to clear it. */
    suspend fun setLimitForCategory(categoryId: Long, limitAmount: Long?) = withContext(Dispatchers.IO) {
        val existing = budgetDao.findByCategory(categoryId)
        when {
            limitAmount == null || limitAmount <= 0 -> existing?.let { budgetDao.delete(it) }
            existing != null -> budgetDao.update(existing.copy(limitAmount = limitAmount))
            else -> {
                val order = budgetDao.maxSortOrder(BudgetKind.LIMIT) + 1
                budgetDao.insert(
                    BudgetEntity(
                        kind = BudgetKind.LIMIT,
                        categoryId = categoryId,
                        limitAmount = limitAmount,
                        sortOrder = order,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    suspend fun createSavingGoal(name: String, iconId: Long?, accountId: Long, targetAmount: Long, deadline: Long?): Long =
        withContext(Dispatchers.IO) {
            val order = budgetDao.maxSortOrder(BudgetKind.SAVING) + 1
            budgetDao.insert(
                BudgetEntity(
                    kind = BudgetKind.SAVING,
                    name = name,
                    iconId = iconId,
                    accountId = accountId,
                    targetAmount = targetAmount,
                    deadline = deadline,
                    sortOrder = order,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }

    suspend fun updateSavingGoal(
        budget: BudgetEntity,
        name: String,
        iconId: Long?,
        accountId: Long,
        targetAmount: Long,
        deadline: Long?,
    ) = withContext(Dispatchers.IO) {
        budgetDao.update(
            budget.copy(name = name, iconId = iconId, accountId = accountId, targetAmount = targetAmount, deadline = deadline),
        )
    }
}
