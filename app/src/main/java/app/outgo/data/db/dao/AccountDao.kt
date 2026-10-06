package app.outgo.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.outgo.data.db.entity.AccountEntity
import app.outgo.data.db.entity.SavingsMonthEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    /**
     * Active accounts (parents and subaccounts) joined with this month's income and, for a savings
     * account, the target in effect this month (newest snapshot at or before it, when that one is on).
     * A parent's income and balance are its active subaccounts' together; a transfer between two of
     * them moves nothing into the parent, so it isn't income there.
     */
    @Query(
        """
        SELECT a.*, COALESCE((
            SELECT SUM(t.amount) FROM trade t
             WHERE t.month_key = :monthKey
               AND ((t.type = 1 AND t.account_id IN (SELECT g.id FROM account g WHERE g.id = a.id OR g.parent_id = a.id))
                 OR (t.type = 4 AND t.to_account_id IN (SELECT g.id FROM account g WHERE g.id = a.id OR g.parent_id = a.id)
                     AND t.account_id NOT IN (SELECT g.id FROM account g WHERE g.id = a.id OR g.parent_id = a.id)))
        ), 0) AS monthlyIncome,
        COALESCE((
            SELECT SUM(t.amount) FROM trade t
             WHERE t.month_key = :prevMonthKey
               AND ((t.type = 1 AND t.account_id IN (SELECT g.id FROM account g WHERE g.id = a.id OR g.parent_id = a.id))
                 OR (t.type = 4 AND t.to_account_id IN (SELECT g.id FROM account g WHERE g.id = a.id OR g.parent_id = a.id)
                     AND t.account_id NOT IN (SELECT g.id FROM account g WHERE g.id = a.id OR g.parent_id = a.id)))
        ), 0) AS prevMonthlyIncome,
        CASE WHEN a.account_type = 1 THEN (
            SELECT CASE WHEN m.enabled = 1 AND m.target > 0 THEN m.target END FROM savings_month m
             WHERE m.account_id = a.id AND m.month_key <= :monthKey
             ORDER BY m.month_key DESC LIMIT 1
        ) END AS monthlyTarget,
        (SELECT SUM(g.balance) FROM account g WHERE g.id = a.id OR (g.parent_id = a.id AND g.archived = 0)) AS totalBalance
        FROM account a WHERE a.archived = 0 ORDER BY a.sort_order, a.id
        """,
    )
    fun observeActiveWithProgress(monthKey: Int, prevMonthKey: Int): Flow<List<AccountWithProgress>>

    /** The savings snapshot in effect in [monthKey]: the newest at or before it. */
    @Query("SELECT * FROM savings_month WHERE account_id = :accountId AND month_key <= :monthKey ORDER BY month_key DESC LIMIT 1")
    suspend fun savingsAt(accountId: Long, monthKey: Int): SavingsMonthEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM savings_month WHERE account_id = :accountId AND month_key = :monthKey)")
    suspend fun hasSavingsMonth(accountId: Long, monthKey: Int): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSavingsMonth(month: SavingsMonthEntity)
    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Delete
    suspend fun delete(account: AccountEntity)

    @Query("SELECT * FROM account WHERE archived = 0 ORDER BY sort_order, id")
    fun observeActive(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM account ORDER BY sort_order, id")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM account WHERE id = :id")
    suspend fun findById(id: Long): AccountEntity?

    @Query("SELECT COUNT(*) FROM trade WHERE account_id = :accountId")
    suspend fun countTrades(accountId: Long): Int

    @Query("SELECT * FROM account WHERE archived = 0 AND parent_id IS NOT NULL ORDER BY sort_order, id LIMIT 1")
    suspend fun firstActiveOrNull(): AccountEntity?

    @Query(
        """
        SELECT a.* FROM account a
        JOIN trade t ON t.account_id = a.id
        WHERE a.archived = 0
        ORDER BY t.occurred_at DESC, t.id DESC
        LIMIT 1
        """,
    )
    suspend fun lastUsedAccount(): AccountEntity?

    @Query("SELECT COALESCE(MAX(sort_order), -1) FROM account WHERE parent_id IS :parentId")
    suspend fun maxSortOrder(parentId: Long?): Int

    @Query("UPDATE account SET parent_id = :parentId, sort_order = :order WHERE id = :id")
    suspend fun setPosition(id: Long, parentId: Long?, order: Int)

    @Query("UPDATE account SET account_type = :type WHERE id IN (:ids) OR parent_id IN (:ids)")
    suspend fun setTypeWithChildren(ids: List<Long>, type: Int)

    @Query("SELECT id FROM account WHERE parent_id = :parentId")
    suspend fun childIds(parentId: Long): List<Long>
}
