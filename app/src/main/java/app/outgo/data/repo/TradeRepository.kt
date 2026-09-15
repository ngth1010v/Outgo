package app.outgo.data.repo

import app.outgo.data.db.dao.TradeDao
import app.outgo.data.db.entity.TradeEntity
import app.outgo.domain.TradeDraft
import app.outgo.domain.TradeType
import app.outgo.util.MonthKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TradeRepository(private val tradeDao: TradeDao) {

    suspend fun insert(draft: TradeDraft): Long = withContext(Dispatchers.IO) {
        require(draft.amount > 0) { "amount must be > 0" }
        val now = System.currentTimeMillis()
        tradeDao.insert(
            TradeEntity(
                type = draft.type,
                amount = draft.amount,
                accountId = draft.accountId,
                categoryId = draft.categoryId,
                occurredAt = draft.occurredAt,
                monthKey = MonthKey.of(draft.occurredAt),
                note = draft.note?.takeIf { it.isNotBlank() },
                createdAt = now,
                updatedAt = now,
            ),
        )
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
                occurredAt = draft.occurredAt,
                monthKey = MonthKey.of(draft.occurredAt),
                note = draft.note?.takeIf { it.isNotBlank() },
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        tradeDao.findById(id)?.let { tradeDao.delete(it) }
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
}
