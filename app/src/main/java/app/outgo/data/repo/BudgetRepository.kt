package app.outgo.data.repo

import app.outgo.data.db.dao.BudgetDao
import app.outgo.data.db.dao.BudgetMonthSpend
import app.outgo.data.db.dao.BudgetWithProgress
import app.outgo.data.db.entity.BudgetEntity
import app.outgo.domain.BudgetKind
import app.outgo.domain.BudgetOffset
import app.outgo.util.MonthKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

/**
 * What the category sheet saves for a LIMIT budget. [overTarget]/[underTarget]: null = no offset,
 * [BudgetOffset.SELF] = this budget, otherwise the target budget's category id.
 */
data class BudgetSetting(val limit: Long?, val overTarget: Long? = null, val underTarget: Long? = null)

class BudgetRepository(private val budgetDao: BudgetDao) {

    fun observeWithProgress(monthKey: Int): Flow<List<BudgetWithProgress>> = combine(
        budgetDao.observeBudgetsWithProgress(monthKey, MonthKey.minus(monthKey, 1)),
        budgetDao.observeCarrySpend(monthKey),
    ) { budgets, spend ->
        val carry = budgetCarry(budgets, spend, monthKey)
        budgets.map { b -> carry[b.categoryId]?.let { b.copy(carry = it) } ?: b }
    }

    suspend fun findByCategory(categoryId: Long): BudgetEntity? =
        withContext(Dispatchers.IO) { budgetDao.findByCategory(categoryId) }

    suspend fun findById(id: Long): BudgetEntity? = withContext(Dispatchers.IO) { budgetDao.findById(id) }

    suspend fun delete(budget: BudgetEntity) = withContext(Dispatchers.IO) { budgetDao.delete(budget) }

    /** Creates/updates/removes the single LIMIT budget for a category. A null or <= 0 limit clears it. */
    suspend fun setLimitForCategory(categoryId: Long, setting: BudgetSetting?) = withContext(Dispatchers.IO) {
        val existing = budgetDao.findByCategory(categoryId)
        val limit = setting?.limit
        // Offsets start from last month's result, so a change shows on this month's bar right away.
        val carryFrom = MonthKey.minus(MonthKey.current(), 1)
        when {
            limit == null || limit <= 0 -> existing?.let { budgetDao.delete(it) }
            existing != null -> {
                val targetsChanged = existing.overTarget != setting.overTarget || existing.underTarget != setting.underTarget
                budgetDao.update(
                    existing.copy(
                        limitAmount = limit,
                        overTarget = setting.overTarget,
                        underTarget = setting.underTarget,
                        carryFrom = if (targetsChanged) carryFrom else existing.carryFrom,
                    ),
                )
            }
            else -> {
                val order = budgetDao.maxSortOrder(BudgetKind.LIMIT) + 1
                budgetDao.insert(
                    BudgetEntity(
                        kind = BudgetKind.LIMIT,
                        categoryId = categoryId,
                        limitAmount = limit,
                        sortOrder = order,
                        createdAt = System.currentTimeMillis(),
                        overTarget = setting.overTarget,
                        underTarget = setting.underTarget,
                        carryFrom = carryFrom,
                    ),
                )
            }
        }
    }
}

/**
 * Offset each budget (by category id) carries into [monthKey]. Walks month by month from the
 * earliest carry_from: a month's limit is the base limit plus what was carried in, and whatever is
 * over (negative) or under (positive) that goes to the chosen target the month after. So an offset
 * a later month absorbs is not counted twice. Targets without a budget drop the offset.
 * Uses today's base limit for past months too, since limit history isn't kept.
 */
internal fun budgetCarry(budgets: List<BudgetWithProgress>, spend: List<BudgetMonthSpend>, monthKey: Int): Map<Long, Long> {
    val rules = budgets.filter { it.categoryId != null && it.carryFrom != null && (it.overTarget != null || it.underTarget != null) }
    val start = rules.minOfOrNull { it.carryFrom!! } ?: return emptyMap()
    val known = budgets.mapNotNullTo(HashSet()) { it.categoryId }
    val spent = spend.associate { (it.categoryId to it.monthKey) to it.spent }
    var carry = emptyMap<Long, Long>()
    var month = start
    while (month < monthKey) {
        val next = HashMap<Long, Long>()
        for (b in rules) {
            val id = b.categoryId!!
            if (month < b.carryFrom!!) continue
            val left = (b.limitAmount ?: 0L) + (carry[id] ?: 0L) - (spent[id to month] ?: 0L)
            val target = when {
                left < 0 -> b.overTarget
                left > 0 -> b.underTarget
                else -> null
            }?.let { if (it == BudgetOffset.SELF) id else it } ?: continue
            if (target in known) next[target] = (next[target] ?: 0L) + left
        }
        carry = next
        month = MonthKey.minus(month, -1)
    }
    return carry
}
