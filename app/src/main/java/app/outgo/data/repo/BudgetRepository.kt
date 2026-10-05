package app.outgo.data.repo

import androidx.room.withTransaction
import app.outgo.data.db.OutgoDatabase
import app.outgo.data.db.dao.BudgetDao
import app.outgo.data.db.dao.BudgetMonthSpend
import app.outgo.data.db.dao.BudgetWithProgress
import app.outgo.data.db.entity.BudgetEntity
import app.outgo.data.db.entity.BudgetMonthEntity
import app.outgo.domain.BudgetKind
import app.outgo.domain.BudgetOffset
import app.outgo.util.MonthKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

/**
 * A LIMIT budget's settings for one month, as the category editor shows and saves them.
 * [overTarget]/[underTarget]: null = no offset, [BudgetOffset.SELF] = this budget, otherwise the
 * target budget's category id. [underFromAccount] and [underToAccount] replace [underTarget] when
 * the unspent amount moves as real money. Not [enabled]: no budget that month, the rest is kept.
 */
data class BudgetSetting(
    val enabled: Boolean,
    val limit: Long,
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
        budgetDao.observeMonths(),
        budgetDao.observeCarrySpend(monthKey),
    ) { budgets, months, spend ->
        val carry = budgetCarry(budgets, months, spend, monthKey)
        budgets.map { b -> carry[b.categoryId]?.let { b.copy(carry = it) } ?: b }
    }

    suspend fun findByCategory(categoryId: Long): BudgetEntity? =
        withContext(Dispatchers.IO) { budgetDao.findByCategory(categoryId) }

    suspend fun findById(id: Long): BudgetEntity? = withContext(Dispatchers.IO) { budgetDao.findById(id) }

    suspend fun delete(budget: BudgetEntity) = withContext(Dispatchers.IO) { budgetDao.delete(budget) }

    /** [categoryId]'s budget settings in effect in [monthKey], or null when it has none by then. */
    suspend fun settingAt(categoryId: Long, monthKey: Int): BudgetSetting? = withContext(Dispatchers.IO) {
        val budget = budgetDao.findByCategory(categoryId) ?: return@withContext null
        budgetDao.monthAt(budget.id, monthKey)?.let {
            BudgetSetting(it.enabled, it.limitAmount, it.overTarget, it.underTarget, it.underFromAccount, it.underToAccount)
        }
    }

    /**
     * Saves [setting] as [categoryId]'s budget from [monthKey] on, creating the budget on first use.
     * Later months without a snapshot of their own follow it, except after a past month: there the
     * month after first gets a snapshot of what it had, so a past edit changes only that month.
     * A month whose account transfer was already made gets it redone, see [resyncAccountOffsets].
     */
    suspend fun setMonth(categoryId: Long, monthKey: Int, setting: BudgetSetting) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val budget = budgetDao.findByCategory(categoryId) ?: budgetDao.insert(
                BudgetEntity(
                    kind = BudgetKind.LIMIT,
                    categoryId = categoryId,
                    sortOrder = budgetDao.maxSortOrder(BudgetKind.LIMIT) + 1,
                    createdAt = System.currentTimeMillis(),
                    // Account transfers only move money for months that finish after this.
                    settledMonth = MonthKey.minus(MonthKey.current(), 1),
                ),
            ).let { budgetDao.findById(it)!! }
            if (monthKey < MonthKey.current()) {
                val next = MonthKey.minus(monthKey, -1)
                if (!budgetDao.hasMonth(budget.id, next)) {
                    budgetDao.putMonth(
                        budgetDao.monthAt(budget.id, next)?.copy(monthKey = next)
                            ?: BudgetMonthEntity(budget.id, next, enabled = false, limitAmount = 0),
                    )
                }
            }
            budgetDao.putMonth(
                BudgetMonthEntity(
                    budgetId = budget.id,
                    monthKey = monthKey,
                    enabled = setting.enabled,
                    limitAmount = setting.limit,
                    overTarget = setting.overTarget,
                    underTarget = setting.underTarget,
                    underFromAccount = setting.underFromAccount,
                    underToAccount = setting.underToAccount,
                ),
            )
            if (monthKey <= (budget.settledMonth ?: Int.MIN_VALUE)) resyncAccountOffsets(monthKey)
        }
    }

    /**
     * Makes the automatic transfers already made for months from [fromMonth] on match what the
     * budgets' monthly settings give now: the amount follows, a month that no longer leaves money
     * to move loses its transfer, and one that now does gets it. Offsets carry across budgets and
     * months, so every budget is checked, not only the edited one. A transfer whose budget is gone
     * is left alone.
     */
    private suspend fun resyncAccountOffsets(fromMonth: Int) {
        val month = MonthKey.current()
        val budgets = budgetDao.budgetsWithProgress(month, MonthKey.minus(month, 1))
        val months = budgetDao.months()
        val spend = budgetDao.carrySpend(month)
        val due = ArrayList<OffsetMove>()
        budgetCarry(budgets, months, spend, month) { budget, setting, m, left ->
            if (m >= fromMonth && m <= (budget.settledMonth ?: Int.MIN_VALUE)) due += OffsetMove(budget, setting, m, left)
        }
        val ids = budgets.mapNotNull { it.categoryId }.toSet()
        val linked = tradeRepository.offsetTransfersFrom(fromMonth).filter { it.offsetCategoryId in ids }
            .groupBy { it.offsetCategoryId!! to it.offsetMonth!! }
        val made = linked.mapValues { it.value.first() }
        // Removals first: they give money back to source accounts the updates may need. A month
        // only ever has one transfer, so any second one for it goes too.
        val kept = due.map { it.budget.categoryId!! to it.month }.toSet()
        linked.forEach { (key, trades) ->
            (if (key in kept) trades.drop(1) else trades).forEach { tradeRepository.delete(it.id) }
        }
        due.sortedBy { it.month }.forEach { move ->
            val from = move.setting.underFromAccount!!
            val to = move.setting.underToAccount!!
            val note = offsetNote(move.budget.displayName, move.month)
            val trade = made[move.budget.categoryId!! to move.month]
                ?: tradeRepository.unlinkedOffsetTransfer(from, to, monthText(move.month))
                    ?.copy(offsetCategoryId = move.budget.categoryId, offsetMonth = move.month)
            if (trade != null) {
                tradeRepository.updateOffsetTransfer(trade, from, to, move.left, note)
            } else {
                tradeRepository.insertOffsetTransfer(from, to, move.left, firstOfNext(move.month), note, move.budget.categoryId!!, move.month)
            }
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
        val months = budgetDao.months()
        if (months.none { it.underFromAccount != null }) return@withContext
        val budgets = budgetDao.budgetsWithProgress(month, MonthKey.minus(month, 1))
        val spend = budgetDao.carrySpend(month)
        val moves = ArrayList<OffsetMove>()
        budgetCarry(budgets, months, spend, month) { budget, setting, m, left ->
            if (m > (budget.settledMonth ?: Int.MIN_VALUE)) moves += OffsetMove(budget, setting, m, left)
        }
        db.withTransaction {
            // Oldest first, so each month's balance check sees the earlier months' transfers.
            moves.sortedBy { it.month }.forEach { move ->
                tradeRepository.insertOffsetTransfer(
                    move.setting.underFromAccount!!, move.setting.underToAccount!!, move.left, firstOfNext(move.month),
                    note = offsetNote(move.budget.displayName, move.month),
                    categoryId = move.budget.categoryId!!,
                    month = move.month,
                )
            }
            budgetDao.markAccountOffsetsSettled(MonthKey.minus(month, 1))
        }
    }
}

/** One month's unspent amount a budget moves to an account. */
private class OffsetMove(val budget: BudgetWithProgress, val setting: BudgetMonthEntity, val month: Int, val left: Long)

/** `09/2026`. */
private fun monthText(month: Int) = String.format(Locale.US, "%02d/%d", month % 100, month / 100)

/** An automatic transfer's note, e.g. "Food & Drink · 09/2026"; the 10 -> 11 migration matched on it. */
private fun offsetNote(budgetName: String, month: Int) = budgetName + " · " + monthText(month)

/** A month's automatic transfer is dated the 1st of the month after, local time. */
private fun firstOfNext(month: Int): Long = YearMonth.of(month / 100, month % 100).plusMonths(1).atDay(1)
    .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

/**
 * Offset each budget (by category id) carries into [monthKey]. Walks month by month from the
 * earliest snapshot with an offset: a month's limit is that month's limit (from [months], the
 * newest snapshot at or before it) plus what was carried in, and whatever is over (negative) or
 * under (positive) that goes to that month's chosen target the month after. So an offset a later
 * month absorbs is not counted twice. A budget that is off in a month takes no part then, and a
 * target that is off (or has no budget yet) in the month the offset lands in drops it.
 * A month with an account offset hands its unspent amount to [onAccountOffset] instead of a budget.
 */
internal fun budgetCarry(
    budgets: List<BudgetWithProgress>,
    months: List<BudgetMonthEntity>,
    spend: List<BudgetMonthSpend>,
    monthKey: Int,
    onAccountOffset: (budget: BudgetWithProgress, setting: BudgetMonthEntity, month: Int, left: Long) -> Unit = { _, _, _, _ -> },
): Map<Long, Long> {
    fun BudgetMonthEntity.hasOffset() =
        overTarget != null || underTarget != null || (underFromAccount != null && underToAccount != null)
    val start = months.filter { it.hasOffset() }.minOfOrNull { it.monthKey } ?: return emptyMap()
    val byBudget = months.groupBy { it.budgetId }
    val byCategory = budgets.filter { it.categoryId != null }.associateBy { it.categoryId!! }
    /** The snapshot [b] has on in [month], or null. [months] is oldest first. */
    fun at(b: BudgetWithProgress, month: Int) = byBudget[b.id]?.lastOrNull { it.monthKey <= month }?.takeIf { it.enabled }
    val spent = spend.associate { (it.categoryId to it.monthKey) to it.spent }
    var carry = emptyMap<Long, Long>()
    var month = start
    while (month < monthKey) {
        val nextMonth = MonthKey.minus(month, -1)
        val next = HashMap<Long, Long>()
        for ((id, b) in byCategory) {
            val setting = at(b, month)?.takeIf { it.hasOffset() } ?: continue
            val left = setting.limitAmount + (carry[id] ?: 0L) - (spent[id to month] ?: 0L)
            if (left > 0 && setting.underFromAccount != null && setting.underToAccount != null) {
                onAccountOffset(b, setting, month, left)
                continue
            }
            val target = when {
                left < 0 -> setting.overTarget
                left > 0 -> setting.underTarget
                else -> null
            }?.let { if (it == BudgetOffset.SELF) id else it } ?: continue
            if (byCategory[target]?.let { at(it, nextMonth) } != null) next[target] = (next[target] ?: 0L) + left
        }
        carry = next
        month = nextMonth
    }
    return carry
}
