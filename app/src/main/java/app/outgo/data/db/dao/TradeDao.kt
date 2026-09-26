package app.outgo.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import app.outgo.data.db.entity.TradeEntity

/**
 * No [kotlinx.coroutines.flow.Flow] queries here on purpose: history lists
 * are paged manually (see HistoryViewModel) and simply reload their current
 * window when [app.outgo.data.repo.TradeRepository] signals a change, rather
 * than re-running a live query on every keystroke of a fast page scroll.
 */
@Dao
interface TradeDao {
    @Insert
    suspend fun insert(trade: TradeEntity): Long

    @Update
    suspend fun update(trade: TradeEntity)

    @Delete
    suspend fun delete(trade: TradeEntity)

    @Query("SELECT * FROM trade WHERE id = :id")
    suspend fun findById(id: Long): TradeEntity?

    @Query("SELECT * FROM trade WHERE type = :type ORDER BY occurred_at DESC, id DESC LIMIT :limit")
    suspend fun firstPage(type: Int, limit: Int): List<TradeEntity>

    @Query(
        """
        SELECT * FROM trade
        WHERE type = :type AND (occurred_at, id) < (:beforeOccurredAt, :beforeId)
        ORDER BY occurred_at DESC, id DESC
        LIMIT :limit
        """,
    )
    suspend fun nextPage(type: Int, beforeOccurredAt: Long, beforeId: Long, limit: Int): List<TradeEntity>

    /** One day of one type, for History opened from the Analysis heatmap. A day never pages. */
    @Query(
        """
        SELECT * FROM trade
        WHERE type = :type AND occurred_at >= :fromMillis AND occurred_at < :toMillis
        ORDER BY occurred_at DESC, id DESC
        """,
    )
    suspend fun pageInRange(type: Int, fromMillis: Long, toMillis: Long): List<TradeEntity>

    /**
     * Analysis: whole months of the given types, narrow columns. `type` rides along so one pass
     * over the result can split expense from income. Uses `index_trade_month_key_type`.
     */
    @Query(
        """
        SELECT id, type, amount, occurred_at AS occurredAt, category_id AS categoryId, note,
               account_id AS accountId
        FROM trade
        WHERE type IN (:types) AND month_key IN (:monthKeys)
        """,
    )
    suspend fun amountsAndTimesForMonths(monthKeys: List<Int>, types: List<Int>): List<TradeSlim>

    /** Analysis: transfers of the given months, summed per (from, to) account pair. */
    @Query(
        """
        SELECT account_id AS fromAccountId, to_account_id AS toAccountId,
               COUNT(*) AS count, SUM(amount) AS total
        FROM trade
        WHERE type = 4 AND month_key IN (:monthKeys)
        GROUP BY account_id, to_account_id
        ORDER BY total DESC
        """,
    )
    suspend fun transferTotalsForMonths(monthKeys: List<Int>): List<TransferTotal>

    /**
     * Analysis accounts: per month, per account, per type, the summed amount. A transfer's
     * receiving end comes back as [FLOW_TRANSFER_IN], so every row is one account's own movement.
     */
    @Query(
        """
        SELECT month_key AS monthKey, account_id AS accountId, type, SUM(amount) AS total
        FROM trade
        WHERE month_key BETWEEN :fromMonth AND :toMonth
        GROUP BY month_key, account_id, type
        UNION ALL
        SELECT month_key, to_account_id, 5, SUM(amount)
        FROM trade
        WHERE type = 4 AND to_account_id IS NOT NULL AND month_key BETWEEN :fromMonth AND :toMonth
        GROUP BY month_key, to_account_id
        """,
    )
    suspend fun accountFlows(fromMonth: Int, toMonth: Int): List<AccountFlow>

    /** Every account's balance at the start of [monthKey], summed the way the balance triggers do. */
    @Query(
        """
        SELECT accountId, SUM(delta) AS balance FROM (
            SELECT account_id AS accountId, CASE WHEN type IN (1,2) THEN amount ELSE -amount END AS delta
            FROM trade WHERE month_key < :monthKey
            UNION ALL
            SELECT to_account_id, amount FROM trade
            WHERE type = 4 AND to_account_id IS NOT NULL AND month_key < :monthKey
        )
        GROUP BY accountId
        """,
    )
    suspend fun balancesBefore(monthKey: Int): List<AccountBalance>

    /** Analysis daily balance: every balance change in [monthKey], signed the way the balance triggers do. */
    @Query(
        """
        SELECT account_id AS accountId, occurred_at AS occurredAt,
               CASE WHEN type IN (1,2) THEN amount ELSE -amount END AS delta
        FROM trade WHERE month_key = :monthKey
        UNION ALL
        SELECT to_account_id, occurred_at, amount FROM trade
        WHERE type = 4 AND to_account_id IS NOT NULL AND month_key = :monthKey
        """,
    )
    suspend fun accountMovesForMonth(monthKey: Int): List<AccountMove>

    @Query("SELECT * FROM trade WHERE account_id = :accountId ORDER BY occurred_at DESC, id DESC LIMIT :limit")
    suspend fun firstPageForAccount(accountId: Long, limit: Int): List<TradeEntity>

    @Query(
        """
        SELECT * FROM trade
        WHERE account_id = :accountId AND (occurred_at, id) < (:beforeOccurredAt, :beforeId)
        ORDER BY occurred_at DESC, id DESC
        LIMIT :limit
        """,
    )
    suspend fun nextPageForAccount(accountId: Long, beforeOccurredAt: Long, beforeId: Long, limit: Int): List<TradeEntity>
}
