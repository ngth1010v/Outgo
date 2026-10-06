package app.outgo.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
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

    /**
     * The History tab's filtered list, newest first, after the (occurred_at, id) cursor. Each
     * null filter matches everything. [categoryId] matches a trade's category or, on a transfer,
     * its budget (manual: category_id, automatic: offset_category_id); [parentId] matches a parent
     * category and all its children; [automatic] matches automatic budget transfers or the rest.
     */
    @Query(
        """
        SELECT * FROM trade
        WHERE type = :type AND (occurred_at, id) < (:beforeOccurredAt, :beforeId)
            AND (:accountId IS NULL OR account_id = :accountId)
            AND (:toAccountId IS NULL OR to_account_id = :toAccountId)
            AND (:categoryId IS NULL OR COALESCE(category_id, offset_category_id) = :categoryId)
            AND (:parentId IS NULL OR category_id = :parentId
                OR category_id IN (SELECT id FROM category WHERE parent_id = :parentId))
            AND (:automatic IS NULL OR (offset_month IS NOT NULL) = :automatic)
        ORDER BY occurred_at DESC, id DESC
        LIMIT :limit
        """,
    )
    suspend fun filteredPage(
        type: Int,
        beforeOccurredAt: Long,
        beforeId: Long,
        accountId: Long?,
        toAccountId: Long?,
        categoryId: Long?,
        parentId: Long?,
        automatic: Boolean?,
        limit: Int,
    ): List<TradeEntity>

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
     * A transfer taken from a budget (one with a category) comes back as an EXPENSE when EXPENSE
     * is asked for, matching how the triggers count it into `category_month_stat`.
     */
    @Query(
        """
        SELECT id, CASE WHEN type = 4 THEN 0 ELSE type END AS type, amount, occurred_at AS occurredAt,
               category_id AS categoryId, note, account_id AS accountId
        FROM trade
        WHERE month_key IN (:monthKeys)
          AND (type IN (:types) OR (type = 4 AND category_id IS NOT NULL AND 0 IN (:types)))
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

    /**
     * The same balances as [balancesBefore], worked back from today's: `account.balance` minus every
     * move from [monthKey] on. Reads only the months after [monthKey], so it is the cheap way in for
     * a recent month. Also lists accounts with no trade before [monthKey], at 0.
     */
    @Query(
        """
        SELECT accountId, SUM(delta) AS balance FROM (
            SELECT id AS accountId, balance AS delta FROM account
            UNION ALL
            SELECT account_id, CASE WHEN type IN (1,2) THEN -amount ELSE amount END
            FROM trade WHERE month_key >= :monthKey
            UNION ALL
            SELECT to_account_id, -amount FROM trade
            WHERE type = 4 AND to_account_id IS NOT NULL AND month_key >= :monthKey
        )
        GROUP BY accountId
        """,
    )
    suspend fun balancesBeforeFromNow(monthKey: Int): List<AccountBalance>

    @Query("SELECT MIN(month_key) FROM trade")
    suspend fun earliestMonthKey(): Int?

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

    /** A budget's month: its category's trades plus its children's, the same set `BudgetWithProgress.spent` sums. */
    @Query(
        """
        SELECT t.id, t.type, t.amount, t.occurred_at AS occurredAt, t.category_id AS categoryId, t.note, t.account_id AS accountId
        FROM trade t JOIN category c ON c.id = t.category_id
        WHERE t.month_key = :monthKey AND (c.id = :categoryId OR c.parent_id = :categoryId)
        """,
    )
    suspend fun budgetTradesForMonth(categoryId: Long, monthKey: Int): List<TradeSlim>

    /** A savings account's month: the income and transfers-in `AccountWithProgress.monthlyIncome` sums. */
    @Query(
        """
        SELECT id, type, amount, occurred_at AS occurredAt, category_id AS categoryId, note, account_id AS accountId
        FROM trade
        WHERE month_key = :monthKey
          AND ((type = 1 AND account_id IN (SELECT id FROM account WHERE id = :accountId OR parent_id = :accountId))
            OR (type = 4 AND to_account_id IN (SELECT id FROM account WHERE id = :accountId OR parent_id = :accountId)
                AND account_id NOT IN (SELECT id FROM account WHERE id = :accountId OR parent_id = :accountId)))
        """,
    )
    suspend fun savingTradesForMonth(accountId: Long, monthKey: Int): List<TradeSlim>

    /** Budget-offset transfers still waiting for money; see [app.outgo.data.db.entity.TradeEntity.pendingAmount]. */
    @Query("SELECT COUNT(*) FROM trade WHERE pending_amount IS NOT NULL")
    fun observePendingCount(): Flow<Int>

    /** Automatic budget-offset transfers for months from [fromMonth] on. */
    @Query("SELECT * FROM trade WHERE offset_month >= :fromMonth")
    suspend fun offsetTransfersFrom(fromMonth: Int): List<TradeEntity>

    /**
     * An automatic transfer made before transfers were linked whose note no longer matched its
     * budget's name: same accounts, note ending in " · [monthText]" (MM/yyyy).
     */
    @Query(
        """
        SELECT * FROM trade
        WHERE type = 4 AND category_id IS NULL AND offset_month IS NULL
          AND account_id = :fromAccountId AND to_account_id = :toAccountId AND note LIKE '% · ' || :monthText
        LIMIT 1
        """,
    )
    suspend fun unlinkedOffsetTransfer(fromAccountId: Long, toAccountId: Long, monthText: String): TradeEntity?

    @Query("SELECT balance FROM account WHERE id = :accountId")
    suspend fun accountBalance(accountId: Long): Long?
}
