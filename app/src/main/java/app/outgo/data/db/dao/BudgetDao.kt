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
     * LIMIT budgets joined with this month's progress. A LIMIT budget on a
     * parent category also counts its children's spend. (Budgets used to
     * also have a SAVING kind tied to an account; that's been replaced by
     * savings accounts, see [app.outgo.domain.AccountType.SAVINGS].)
     * Listed in the Category screen's order: by parent, a parent's own budget before its children's.
     */
    @Query(
        """
        SELECT b.id, b.kind, b.name, b.icon_id AS iconId, b.category_id AS categoryId,
               b.limit_amount AS limitAmount, b.sort_order AS sortOrder,
               COALESCE((
                   SELECT SUM(s.total) FROM category_month_stat s JOIN category c ON c.id = s.category_id
                   WHERE s.month_key = :monthKey AND (c.id = b.category_id OR c.parent_id = b.category_id)
               ), 0) AS spent,
               COALESCE((
                   SELECT SUM(s.total) FROM category_month_stat s JOIN category c ON c.id = s.category_id
                   WHERE s.month_key = :prevMonthKey AND (c.id = b.category_id OR c.parent_id = b.category_id)
               ), 0) AS prevSpent,
               cat.name AS categoryName,
               cat.icon_id AS categoryIconId,
               cat.color AS categoryColor,
               b.over_target AS overTarget, b.under_target AS underTarget, b.carry_from AS carryFrom,
               b.under_from_account AS underFromAccount, b.under_to_account AS underToAccount,
               b.settled_month AS settledMonth,
               0 AS carry
        FROM budget b
        LEFT JOIN category cat ON cat.id = b.category_id
        LEFT JOIN category parent ON parent.id = cat.parent_id
        WHERE b.kind = 0
        ORDER BY b.category_id IS NULL,
                 COALESCE(parent.sort_order, cat.sort_order), COALESCE(parent.id, cat.id),
                 cat.parent_id IS NOT NULL, cat.sort_order, b.id
        """,
    )
    fun observeBudgetsWithProgress(monthKey: Int, prevMonthKey: Int): Flow<List<BudgetWithProgress>>

    /** Monthly spend of budgets that carry an offset, from each one's carry_from up to before [monthKey]. */
    @Query(
        """
        SELECT b.category_id AS categoryId, s.month_key AS monthKey, SUM(s.total) AS spent
        FROM budget b
        JOIN category c ON c.id = b.category_id OR c.parent_id = b.category_id
        JOIN category_month_stat s ON s.category_id = c.id
        WHERE b.kind = 0 AND (b.over_target IS NOT NULL OR b.under_target IS NOT NULL OR b.under_from_account IS NOT NULL)
          AND s.month_key >= b.carry_from AND s.month_key < :monthKey
        GROUP BY b.category_id, s.month_key
        """,
    )
    fun observeCarrySpend(monthKey: Int): Flow<List<BudgetMonthSpend>>

    @Query("UPDATE budget SET settled_month = :monthKey WHERE under_from_account IS NOT NULL")
    suspend fun markAccountOffsetsSettled(monthKey: Int)

    @Query("SELECT COALESCE(MAX(sort_order), -1) FROM budget WHERE kind = :kind")
    suspend fun maxSortOrder(kind: Int): Int
}
