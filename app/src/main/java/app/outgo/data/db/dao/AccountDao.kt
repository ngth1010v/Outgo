package app.outgo.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import app.outgo.data.db.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    /** Active accounts joined with this month's income (counts toward [AccountEntity.savingsTarget]). */
    @Query(
        """
        SELECT a.*, COALESCE((
            SELECT SUM(t.amount) FROM trade t
             WHERE t.month_key = :monthKey
               AND ((t.account_id = a.id AND t.type = 1) OR (t.to_account_id = a.id AND t.type = 4))
        ), 0) AS monthlyIncome,
        COALESCE((
            SELECT SUM(t.amount) FROM trade t
             WHERE t.month_key = :prevMonthKey
               AND ((t.account_id = a.id AND t.type = 1) OR (t.to_account_id = a.id AND t.type = 4))
        ), 0) AS prevMonthlyIncome
        FROM account a WHERE a.archived = 0 ORDER BY a.sort_order, a.id
        """,
    )
    fun observeActiveWithProgress(monthKey: Int, prevMonthKey: Int): Flow<List<AccountWithProgress>>
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

    @Query("SELECT * FROM account WHERE archived = 0 ORDER BY sort_order, id LIMIT 1")
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

    @Query("SELECT COALESCE(MAX(sort_order), -1) FROM account")
    suspend fun maxSortOrder(): Int

    @Query("UPDATE account SET sort_order = :order WHERE id = :id")
    suspend fun setSortOrder(id: Long, order: Int)
}
