package app.outgo.data.repo

import app.outgo.data.db.dao.AccountBalance
import app.outgo.data.db.dao.AccountFlow
import app.outgo.data.db.dao.AccountMove
import app.outgo.data.db.dao.TradeDao
import app.outgo.data.db.dao.TradeSlim
import app.outgo.data.db.dao.TransferTotal
import app.outgo.data.db.entity.TradeEntity
import app.outgo.domain.TradeDraft
import app.outgo.domain.TradeType
import app.outgo.util.MonthKey
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
                // Saved by hand with a real amount: no longer waiting on the offset.
                pendingAmount = null,
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

    val pendingCount: Flow<Int> get() = tradeDao.observePendingCount()

    /**
     * A budget offset's transfer, dated [occurredAt]. Moves [amount] when [fromAccountId] holds at
     * least that much, otherwise records a 0 transfer holding [amount] as pending (see [settle]).
     * Skipped when either account is gone.
     */
    suspend fun insertOffsetTransfer(
        fromAccountId: Long,
        toAccountId: Long,
        amount: Long,
        occurredAt: Long,
        note: String,
        categoryId: Long,
        month: Int,
    ) =
        withContext(Dispatchers.IO) {
            val balance = tradeDao.accountBalance(fromAccountId) ?: return@withContext
            tradeDao.accountBalance(toAccountId) ?: return@withContext
            val covered = balance >= amount
            val now = System.currentTimeMillis()
            tradeDao.insert(
                TradeEntity(
                    type = TradeType.TRANSFER,
                    amount = if (covered) amount else 0L,
                    accountId = fromAccountId,
                    categoryId = null,
                    toAccountId = toAccountId,
                    occurredAt = occurredAt,
                    monthKey = MonthKey.of(occurredAt),
                    note = note,
                    createdAt = now,
                    updatedAt = now,
                    pendingAmount = if (covered) null else amount,
                    offsetCategoryId = categoryId,
                    offsetMonth = month,
                ),
            )
            signalChange()
        }

    /**
     * Brings an automatic transfer in line with its budget month's settings: [amount] between these
     * accounts, or pending as in [insertOffsetTransfer] when the source can't cover it (counting
     * what this transfer already took from it). Left as it is when either account is gone.
     */
    suspend fun updateOffsetTransfer(trade: TradeEntity, fromAccountId: Long, toAccountId: Long, amount: Long, note: String) =
        withContext(Dispatchers.IO) {
            val balance = tradeDao.accountBalance(fromAccountId) ?: return@withContext
            tradeDao.accountBalance(toAccountId) ?: return@withContext
            val covered = balance + (if (trade.accountId == fromAccountId) trade.amount else 0L) >= amount
            tradeDao.update(
                trade.copy(
                    accountId = fromAccountId,
                    toAccountId = toAccountId,
                    amount = if (covered) amount else 0L,
                    pendingAmount = if (covered) null else amount,
                    note = note,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            signalChange()
        }

    suspend fun offsetTransfersFrom(fromMonth: Int): List<TradeEntity> =
        withContext(Dispatchers.IO) { tradeDao.offsetTransfersFrom(fromMonth) }

    suspend fun unlinkedOffsetTransfer(fromAccountId: Long, toAccountId: Long, monthText: String): TradeEntity? =
        withContext(Dispatchers.IO) { tradeDao.unlinkedOffsetTransfer(fromAccountId, toAccountId, monthText) }

    /**
     * Retries a pending offset transfer: moves the whole amount now, dated now, when the source
     * account can cover it. False (and nothing changes) when it still can't.
     */
    suspend fun settle(id: Long): Boolean = withContext(Dispatchers.IO) {
        val trade = tradeDao.findById(id) ?: return@withContext true
        val pending = trade.pendingAmount ?: return@withContext true
        if ((tradeDao.accountBalance(trade.accountId) ?: 0L) < pending) return@withContext false
        val now = System.currentTimeMillis()
        tradeDao.update(trade.copy(amount = pending, pendingAmount = null, occurredAt = now, monthKey = MonthKey.of(now), updatedAt = now))
        signalChange()
        true
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

    /**
     * Every account's balance at the start of [monthKey]. Sums whichever side of history is shorter:
     * the months before it, or (back from today's balances) the months since. Both give the same
     * figures, since the triggers keep `account.balance` equal to the sum of the account's trades.
     */
    suspend fun balancesBefore(monthKey: Int): List<AccountBalance> = withContext(Dispatchers.IO) {
        val earliest = tradeDao.earliestMonthKey() ?: return@withContext emptyList()
        if (monthIndex(monthKey) - monthIndex(earliest) <= monthIndex(MonthKey.current()) - monthIndex(monthKey)) {
            tradeDao.balancesBefore(monthKey)
        } else {
            tradeDao.balancesBeforeFromNow(monthKey)
        }
    }

    /** Months since year 0, so two month keys subtract to the months between them. */
    private fun monthIndex(monthKey: Int) = monthKey / 100 * 12 + monthKey % 100

    suspend fun accountMovesForMonth(monthKey: Int): List<AccountMove> =
        withContext(Dispatchers.IO) { tradeDao.accountMovesForMonth(monthKey) }

    /** Running spend of a budget's category for each day of [monthKey], day 1 to today (or the month's end). */
    suspend fun budgetDays(categoryId: Long, monthKey: Int = MonthKey.current()): List<Long> = withContext(Dispatchers.IO) {
        runningDays(tradeDao.budgetTradesForMonth(categoryId, monthKey).map { it.occurredAt to it.amount }, monthKey)
    }

    /** Running amount saved into an account for each day of this month, day 1 to today. */
    suspend fun savingDaysThisMonth(accountId: Long): List<Long> = withContext(Dispatchers.IO) {
        val month = MonthKey.current()
        runningDays(tradeDao.savingTradesForMonth(accountId, month).map { it.occurredAt to it.amount }, month)
    }

    /** Running balance change since [monthKey] began for each of [accountIds], day 1 to today (or the month's end). */
    suspend fun balanceChangeDays(accountIds: Collection<Long>, monthKey: Int): Map<Long, List<Long>> = withContext(Dispatchers.IO) {
        val moves = tradeDao.accountMovesForMonth(monthKey).groupBy { it.accountId }
        accountIds.associateWith { id -> runningDays(moves[id].orEmpty().map { it.occurredAt to it.delta }, monthKey) }
    }

    /** (occurredAt, amount) pairs summed per day and run up; the live month stops at today. */
    private fun runningDays(amounts: List<Pair<Long, Long>>, monthKey: Int): List<Long> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val length = YearMonth.of(monthKey / 100, monthKey % 100).lengthOfMonth()
        val days = if (monthKey == today.year * 100 + today.monthValue) today.dayOfMonth else length
        val daily = LongArray(days)
        amounts.forEach { (at, amount) ->
            val day = Instant.ofEpochMilli(at).atZone(zone).dayOfMonth
            // A trade dated later this month still counts, on today.
            daily[day.coerceAtMost(days) - 1] += amount
        }
        var running = 0L
        return daily.map { running += it; running }
    }
}
