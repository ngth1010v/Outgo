package app.outgo.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import app.outgo.data.db.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Insert
    suspend fun insert(budget: BudgetEntity): Long

    @Update
    suspend fun update(budget: BudgetEntity)

    @Delete
    suspend fun delete(budget: BudgetEntity)

    @Query("SELECT * FROM budget WHERE category_id = :categoryId LIMIT 1")
    suspend fun findByCategory(categoryId: Long): BudgetEntity?

    @Query("SELECT * FROM budget WHERE id = :id")
    suspend fun findById(id: Long): BudgetEntity?

    /**
     * All budgets (LIMIT then SAVING) joined with this month's progress.
     * A LIMIT budget on a parent category also counts its children's spend.
     */
    @Query(
        """
        SELECT b.id, b.kind, b.name, b.icon_id AS iconId, b.category_id AS categoryId,
               b.limit_amount AS limitAmount, b.account_id AS accountId,
               b.target_amount AS targetAmount, b.deadline, b.sort_order AS sortOrder,
               COALESCE((
                   SELECT SUM(s.total) FROM category_month_stat s JOIN category c ON c.id = s.category_id
                   WHERE s.month_key = :monthKey AND (c.id = b.category_id OR c.parent_id = b.category_id)
               ), 0) AS spent,
               COALESCE(a.balance, 0) AS saved,
               cat.name AS categoryName,
               cat.icon_id AS categoryIconId
        FROM budget b
        LEFT JOIN account a ON a.id = b.account_id
        LEFT JOIN category cat ON cat.id = b.category_id
        ORDER BY b.kind, b.sort_order, b.id
        """,
    )
    fun observeBudgetsWithProgress(monthKey: Int): Flow<List<BudgetWithProgress>>

    @Query("SELECT COALESCE(MAX(sort_order), -1) FROM budget WHERE kind = :kind")
    suspend fun maxSortOrder(kind: Int): Int
}
