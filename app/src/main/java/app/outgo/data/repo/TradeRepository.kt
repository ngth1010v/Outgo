package app.outgo.data.repo

import app.outgo.data.db.dao.AccountBalance
import app.outgo.data.db.dao.AccountFlow
import app.outgo.data.db.dao.TradeDao
import app.outgo.data.db.dao.TradeSlim
import app.outgo.data.db.dao.TransferTotal
import app.outgo.data.db.entity.TradeEntity
import app.outgo.domain.TradeDraft
import app.outgo.domain.TradeType
import app.outgo.util.MonthKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

class TradeRepository(private val tradeDao: TradeDao) {

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * Emits after every write. Screens whose trade data is a one-shot fetch rather than a
     * Flow (see [TradeDao]) use it to reload just what they are holding.
     */
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    private fun signalChange() {
        _changes.tryEmit(Unit)
    }

    suspend fun insert(draft: TradeDraft): Long = withContext(Dispatchers.IO) {
        require(draft.amount > 0) { "amount must be > 0" }
        val now = System.currentTimeMillis()
        tradeDao.insert(
            TradeEntity(
                type = draft.type,
                amount = draft.amount,
                accountId = draft.accountId,
                categoryId = draft.categoryId,
                toAccountId = draft.toAccountId,
                occurredAt = draft.occurredAt,
                monthKey = MonthKey.of(draft.occurredAt),
                note = draft.note?.takeIf { it.isNotBlank() },
                createdAt = now,
                updatedAt = now,
            ),
        ).also { signalChange() }
    }

    suspend fun update(id: Long, draft: TradeDraft) = withContext(Dispatchers.IO) {
        require(draft.amount > 0) { "amount must be > 0" }
        val existing = tradeDao.findById(id) ?: return@withContext
        tradeDao.update(
            existing.copy(
                type = draft.type,
                amount = draft.amount,
                accountId = draft.accountId,
                categoryId = draft.categoryId,
                toAccountId = draft.toAccountId,
                occurredAt = draft.occurredAt,
                monthKey = MonthKey.of(draft.occurredAt),
                note = draft.note?.takeIf { it.isNotBlank() },
                updatedAt = System.currentTimeMillis(),
            ),
        )
        signalChange()
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        tradeDao.findById(id)?.let { tradeDao.delete(it) }
        signalChange()
    }

    /** Records the delta between an account's old and new "current balance" as one adjustment trade. */
    suspend fun recordAdjustment(accountId: Long, delta: Long) = withContext(Dispatchers.IO) {
        if (delta == 0L) return@withContext
        val now = System.currentTimeMillis()
        tradeDao.insert(
            TradeEntity(
                type = if (delta > 0) TradeType.ADJUST_IN else TradeType.ADJUST_OUT,
                amount = kotlin.math.abs(delta),
                accountId = accountId,
                categoryId = null,
                occurredAt = now,
                monthKey = MonthKey.of(now),
                note = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
        signalChange()
    }

    suspend fun findById(id: Long): TradeEntity? = withContext(Dispatchers.IO) { tradeDao.findById(id) }

    suspend fun firstPage(type: Int, limit: Int = 50): List<TradeEntity> =
        withContext(Dispatchers.IO) { tradeDao.firstPage(type, limit) }

    suspend fun nextPage(type: Int, before: TradeEntity, limit: Int = 50): List<TradeEntity> =
        withContext(Dispatchers.IO) { tradeDao.nextPage(type, before.occurredAt, before.id, limit) }

    suspend fun firstPageForAccount(accountId: Long, limit: Int = 50): List<TradeEntity> =
        withContext(Dispatchers.IO) { tradeDao.firstPageForAccount(accountId, limit) }

    suspend fun nextPageForAccount(accountId: Long, before: TradeEntity, limit: Int = 50): List<TradeEntity> =
        withContext(Dispatchers.IO) { tradeDao.nextPageForAccount(accountId, before.occurredAt, before.id, limit) }

    suspend fun pageInRange(type: Int, fromMillis: Long, toMillis: Long): List<TradeEntity> =
        withContext(Dispatchers.IO) { tradeDao.pageInRange(type, fromMillis, toMillis) }

    suspend fun amountsAndTimesForMonths(monthKeys: List<Int>, types: List<Int>): List<TradeSlim> =
        withContext(Dispatchers.IO) { tradeDao.amountsAndTimesForMonths(monthKeys, types) }

    suspend fun transferTotalsForMonths(monthKeys: List<Int>): List<TransferTotal> =
        withContext(Dispatchers.IO) { tradeDao.transferTotalsForMonths(monthKeys) }

    suspend fun accountFlows(fromMonth: Int, toMonth: Int): List<AccountFlow> =
        withContext(Dispatchers.IO) { tradeDao.accountFlows(fromMonth, toMonth) }

    suspend fun balancesBefore(monthKey: Int): List<AccountBalance> =
        withContext(Dispatchers.IO) { tradeDao.balancesBefore(monthKey) }
}
