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

    /** Analysis: whole months of one type, narrow columns. Uses `index_trade_month_key_type`. */
    @Query(
        """
        SELECT id, amount, occurred_at AS occurredAt, category_id AS categoryId, note
        FROM trade
        WHERE type = :type AND month_key IN (:monthKeys)
        """,
    )
    suspend fun amountsAndTimesForMonths(monthKeys: List<Int>, type: Int): List<TradeSlim>

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
