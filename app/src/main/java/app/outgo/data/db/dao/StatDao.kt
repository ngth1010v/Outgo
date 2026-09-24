package app.outgo.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StatDao {
    /** One row per (month, parent category) in range, for the Home stacked bar chart. */
    @Query(
        """
        SELECT s.month_key AS monthKey, p.id AS rootId, p.type AS type, p.name AS name,
               p.color AS color, p.icon_id AS iconId, SUM(s.total) AS total
        FROM category_month_stat s
        JOIN category c ON c.id = s.category_id
        JOIN category p ON p.id = COALESCE(c.parent_id, c.id)
        WHERE s.month_key BETWEEN :fromMonth AND :toMonth
        GROUP BY s.month_key, p.id
        ORDER BY s.month_key, p.type, total DESC
        """,
    )
    fun observeMonthlyTotals(fromMonth: Int, toMonth: Int): Flow<List<MonthCategoryTotal>>

    /** Oldest month that has any data, i.e. the lower bound of the Analysis month picker. */
    @Query("SELECT MIN(month_key) FROM category_month_stat")
    suspend fun earliestMonth(): Int?

    @Query(
        """
        SELECT COALESCE(SUM(s.total), 0) FROM category_month_stat s JOIN category c ON c.id = s.category_id
        WHERE s.month_key = :monthKey AND (c.id = :categoryId OR c.parent_id = :categoryId)
        """,
    )
    fun observeSpentForCategory(categoryId: Long, monthKey: Int): Flow<Long>
}
