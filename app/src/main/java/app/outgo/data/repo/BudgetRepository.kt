package app.outgo.data.repo

import androidx.room.withTransaction
import app.outgo.data.db.OutgoDatabase
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

/**
 * What the category editor saves for a LIMIT budget. [overTarget]/[underTarget]: null = no offset,
 * [BudgetOffset.SELF] = this budget, otherwise the target budget's category id. [underFromAccount]
 * and [underToAccount] replace [underTarget] when the unspent amount moves as real money.
 * Not [enabled]: an existing budget is paused with its settings kept, and the rest is ignored.
 */
data class BudgetSetting(
    val enabled: Boolean,
    val limit: Long?,
    /** yyyyMM; months before it have no budget. */
    val applyFrom: Int,
    val overTarget: Long? = null,
    val underTarget: Long? = null,
    val underFromAccount: Long? = null,
    val underToAccount: Long? = null,
)

class BudgetRepository(
    private val db: OutgoDatabase,
    private val budgetDao: BudgetDao,
    private val tradeRepository: TradeRepository,
) {

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

    /**
     * Creates, updates or pauses the single LIMIT budget for a category. A null [setting] or a
     * limit <= 0 while enabled changes nothing (the editor doesn't allow saving that).
     */
    suspend fun setLimitForCategory(categoryId: Long, setting: BudgetSetting?) = withContext(Dispatchers.IO) {
        if (setting == null) return@withContext
        val existing = budgetDao.findByCategory(categoryId)
        if (!setting.enabled) {
            existing?.takeIf { it.enabled }?.let { budgetDao.update(it.copy(enabled = false)) }
            return@withContext
        }
        val limit = setting.limit?.takeIf { it > 0 } ?: return@withContext
        // Account transfers only move money for months that finish after this: a newly set, changed
        // or resumed account offset starts clean from last month.
        val lastMonth = MonthKey.minus(MonthKey.current(), 1)
        if (existing != null) {
            val accountChanged = existing.underFromAccount != setting.underFromAccount ||
                existing.underToAccount != setting.underToAccount || !existing.enabled
            budgetDao.update(
                existing.copy(
                    limitAmount = limit,
                    overTarget = setting.overTarget,
                    underTarget = setting.underTarget,
                    underFromAccount = setting.underFromAccount,
                    underToAccount = setting.underToAccount,
                    carryFrom = setting.applyFrom,
                    settledMonth = if (accountChanged) lastMonth else existing.settledMonth,
                    enabled = true,
                ),
            )
        } else {
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
                    underFromAccount = setting.underFromAccount,
                    underToAccount = setting.underToAccount,
                    carryFrom = setting.applyFrom,
                    settledMonth = lastMonth,
                ),
            )
        }
    }

    /**
     * Makes the transfer of every finished month an account offset hasn't moved yet, dated the
     * 1st of the month after. Called on each app start; months already made are skipped via
     * settled_month, so this is a no-op after the first start of a month.
     * ponytail: runs on app start only, so an app left open across midnight on the 1st makes the
     * transfer on its next start; add a date-change receiver if that ever matters.
     */
    suspend fun settleAccountOffsets() = withContext(Dispatchers.IO) {
        val month = MonthKey.current()
        val budgets = budgetDao.observeBudgetsWithProgress(month, MonthKey.minus(month, 1)).first()
        if (budgets.none { it.underFromAccount != null }) return@withContext
        val spend = budgetDao.observeCarrySpend(month).first()
        val moves = ArrayList<Triple<BudgetWithProgress, Int, Long>>()
        budgetCarry(budgets, spend, month) { budget, m, left ->
            if (m > (budget.settledMonth ?: Int.MIN_VALUE)) moves += Triple(budget, m, left)
        }
        db.withTransaction {
            // Oldest first, so each month's balance check sees the earlier months' transfers.
            moves.sortedBy { it.second }.forEach { (budget, m, left) ->
                val firstOfNext = YearMonth.of(m / 100, m % 100).plusMonths(1).atDay(1)
                    .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                tradeRepository.insertOffsetTransfer(
                    budget.underFromAccount!!, budget.underToAccount!!, left, firstOfNext,
                    note = budget.displayName + " · " + String.format(Locale.US, "%02d/%d", m % 100, m / 100),
                )
            }
            budgetDao.markAccountOffsetsSettled(MonthKey.minus(month, 1))
        }
    }
}

/**
 * Offset each budget (by category id) carries into [monthKey]. Walks month by month from the
 * earliest apply-from (carry_from): a month's limit is the base limit plus what was carried in, and
 * whatever is over (negative) or under (positive) that goes to the chosen target the month after.
 * So an offset a later month absorbs is not counted twice. Paused budgets take no part, and a target
 * that is paused, gone, or not started yet in the month the offset lands in drops it.
 * A budget with an account offset hands its unspent amount to [onAccountOffset] instead of a budget.
 * Uses today's base limit for past months too, since limit history isn't kept.
 */
internal fun budgetCarry(
    budgets: List<BudgetWithProgress>,
    spend: List<BudgetMonthSpend>,
    monthKey: Int,
    onAccountOffset: (budget: BudgetWithProgress, month: Int, left: Long) -> Unit = { _, _, _ -> },
): Map<Long, Long> {
    val rules = budgets.filter {
        it.enabled && it.categoryId != null && it.carryFrom != null &&
            (it.overTarget != null || it.underTarget != null || (it.underFromAccount != null && it.underToAccount != null))
    }
    val start = rules.minOfOrNull { it.carryFrom!! } ?: return emptyMap()
    // Target -> its apply-from (MIN_VALUE when it has none).
    val known = budgets.filter { it.enabled && it.categoryId != null }.associate { it.categoryId!! to (it.carryFrom ?: Int.MIN_VALUE) }
    val spent = spend.associate { (it.categoryId to it.monthKey) to it.spent }
    var carry = emptyMap<Long, Long>()
    var month = start
    while (month < monthKey) {
        val next = HashMap<Long, Long>()
        for (b in rules) {
            val id = b.categoryId!!
            if (month < b.carryFrom!!) continue
            val left = (b.limitAmount ?: 0L) + (carry[id] ?: 0L) - (spent[id to month] ?: 0L)
            if (left > 0 && b.underFromAccount != null && b.underToAccount != null) {
                onAccountOffset(b, month, left)
                continue
            }
            val target = when {
                left < 0 -> b.overTarget
                left > 0 -> b.underTarget
                else -> null
            }?.let { if (it == BudgetOffset.SELF) id else it } ?: continue
            if ((known[target] ?: continue) <= MonthKey.minus(month, -1)) next[target] = (next[target] ?: 0L) + left
        }
        carry = next
        month = MonthKey.minus(month, -1)
    }
    return carry
}
