package app.outgo.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.outgo.data.db.entity.BudgetEntity
import app.outgo.data.db.entity.BudgetMonthEntity
import kotlinx.coroutines.flow.Flow

/**
 * The summed limit of a parent category's subcategories in :monthKey: each active subcategory's
 * budget whose settings in effect then are on. Their own sums are never used (a subcategory can't sum).
 */
private const val CHILD_LIMIT_SUM = """COALESCE((
                   SELECT SUM(cm.limit_amount) FROM budget cb
                   JOIN category cc ON cc.id = cb.category_id
                   JOIN budget_month cm ON cm.budget_id = cb.id AND cm.month_key =
                       (SELECT MAX(month_key) FROM budget_month WHERE budget_id = cb.id AND month_key <= :monthKey)
                   WHERE cb.kind = 0 AND cc.parent_id = b.category_id AND cc.archived = 0 AND cm.enabled = 1
               ), 0)"""

private const val BUDGETS_WITH_PROGRESS = """
        SELECT b.id, b.kind, b.name, b.icon_id AS iconId, b.category_id AS categoryId,
               cat.parent_id AS parentCategoryId,
               CASE WHEN m.sum_children = 1 THEN """ + CHILD_LIMIT_SUM + """ ELSE m.limit_amount END AS limitAmount,
               COALESCE(m.sum_children, 0) AS sumChildren,
               b.sort_order AS sortOrder,
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
               m.over_target AS overTarget, m.under_target AS underTarget,
               m.under_from_account AS underFromAccount, m.under_to_account AS underToAccount,
               b.settled_month AS settledMonth, COALESCE(m.enabled, 0) AS enabled,
               0 AS carry
        FROM budget b
        LEFT JOIN budget_month m ON m.budget_id = b.id AND m.month_key =
            (SELECT MAX(month_key) FROM budget_month WHERE budget_id = b.id AND month_key <= :monthKey)
        LEFT JOIN category cat ON cat.id = b.category_id
        LEFT JOIN category parent ON parent.id = cat.parent_id
        WHERE b.kind = 0
        ORDER BY b.category_id IS NULL,
                 COALESCE(parent.sort_order, cat.sort_order), COALESCE(parent.id, cat.id),
                 cat.parent_id IS NOT NULL, cat.sort_order, b.id
        """

private const val CARRY_SPEND = """
        SELECT b.category_id AS categoryId, s.month_key AS monthKey, SUM(s.total) AS spent
        FROM budget b
        JOIN category c ON c.id = b.category_id OR c.parent_id = b.category_id
        JOIN category_month_stat s ON s.category_id = c.id
        WHERE b.kind = 0 AND s.month_key < :monthKey
          AND s.month_key >= (SELECT MIN(month_key) FROM budget_month WHERE budget_id = b.id)
        GROUP BY b.category_id, s.month_key
        """

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
     * LIMIT budgets with the settings in effect in [monthKey] (newest snapshot at or before it;
     * none: off, null limit and offsets), joined with that month's progress. A LIMIT budget on a
     * parent category also counts its children's spend. (Budgets used to
     * also have a SAVING kind tied to an account; that's been replaced by
     * savings accounts, see [app.outgo.domain.AccountType.SAVINGS].)
     * Listed in the Category screen's order: by parent, a parent's own budget before its children's.
     */
    @Query(BUDGETS_WITH_PROGRESS)
    fun observeBudgetsWithProgress(monthKey: Int, prevMonthKey: Int): Flow<List<BudgetWithProgress>>

    /** One-shot [observeBudgetsWithProgress], e.g. inside a transaction (a Flow reads outside it). */
    @Query(BUDGETS_WITH_PROGRESS)
    suspend fun budgetsWithProgress(monthKey: Int, prevMonthKey: Int): List<BudgetWithProgress>

    /** Monthly spend of every budget, from its first snapshot's month up to before [monthKey]. */
    @Query(CARRY_SPEND)
    fun observeCarrySpend(monthKey: Int): Flow<List<BudgetMonthSpend>>

    @Query(CARRY_SPEND)
    suspend fun carrySpend(monthKey: Int): List<BudgetMonthSpend>

    /** Every budget's monthly snapshots, oldest first within a budget. */
    @Query("SELECT * FROM budget_month ORDER BY budget_id, month_key")
    fun observeMonths(): Flow<List<BudgetMonthEntity>>

    @Query("SELECT * FROM budget_month ORDER BY budget_id, month_key")
    suspend fun months(): List<BudgetMonthEntity>

    /** The snapshot in effect in [monthKey]: the newest at or before it. */
    @Query("SELECT * FROM budget_month WHERE budget_id = :budgetId AND month_key <= :monthKey ORDER BY month_key DESC LIMIT 1")
    suspend fun monthAt(budgetId: Long, monthKey: Int): BudgetMonthEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM budget_month WHERE budget_id = :budgetId AND month_key = :monthKey)")
    suspend fun hasMonth(budgetId: Long, monthKey: Int): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMonth(month: BudgetMonthEntity)

    /** Budgets whose account transfers aren't made up to [monthKey] yet. */
    @Query("SELECT COUNT(*) FROM budget WHERE settled_month IS NULL OR settled_month < :monthKey")
    suspend fun countUnsettled(monthKey: Int): Int

    @Query("UPDATE budget SET settled_month = :monthKey")
    suspend fun markAccountOffsetsSettled(monthKey: Int)

    /** What [parentCategoryId]'s budget sums in [monthKey] when it sums its subcategories. */
    @Query("""
        SELECT COALESCE(SUM(cm.limit_amount), 0) FROM budget cb
        JOIN category cc ON cc.id = cb.category_id
        JOIN budget_month cm ON cm.budget_id = cb.id AND cm.month_key =
            (SELECT MAX(month_key) FROM budget_month WHERE budget_id = cb.id AND month_key <= :monthKey)
        WHERE cb.kind = 0 AND cc.parent_id = :parentCategoryId AND cc.archived = 0 AND cm.enabled = 1
        """)
    suspend fun childLimitSum(parentCategoryId: Long, monthKey: Int): Long

    @Query("SELECT COALESCE(MAX(sort_order), -1) FROM budget WHERE kind = :kind")
    suspend fun maxSortOrder(kind: Int): Int
}
