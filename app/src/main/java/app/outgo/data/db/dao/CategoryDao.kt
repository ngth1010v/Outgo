package app.outgo.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import app.outgo.data.db.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * A category's use: the date of its latest trade ([lastAt], the trade's own date, not when it was
 * saved), how many trades it has since a cut-off ([recentCount]) and in all ([totalCount]).
 */
data class CategoryUse(val id: Long, val lastAt: Long, val recentCount: Int, val totalCount: Int)

@Dao
interface CategoryDao {
    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("UPDATE category SET parent_budget_exempt = :exempt WHERE id = :id")
    suspend fun setParentBudgetExempt(id: Long, exempt: Boolean)

    @Delete
    suspend fun delete(category: CategoryEntity)

    @Query("SELECT * FROM category WHERE parent_id IS NULL AND type = :type AND archived = 0 ORDER BY sort_order, id")
    fun observeParents(type: Int): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category WHERE parent_id = :parentId AND archived = 0 ORDER BY sort_order, id")
    fun observeChildren(parentId: Long): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category WHERE type = :type AND archived = 0 ORDER BY sort_order, id")
    fun observeAllOfType(type: Int): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category WHERE id = :id")
    suspend fun findById(id: Long): CategoryEntity?

    @Query("SELECT * FROM category WHERE archived = 0")
    suspend fun allActive(): List<CategoryEntity>

    /** How each category was used by saved trades: see [CategoryUse]. */
    @Query(
        """
        SELECT category_id AS id, MAX(occurred_at) AS lastAt,
               SUM(occurred_at >= :since) AS recentCount, COUNT(*) AS totalCount
        FROM trade WHERE category_id IS NOT NULL
        GROUP BY category_id
        """,
    )
    suspend fun useStats(since: Long): List<CategoryUse>

    @Query("SELECT * FROM category WHERE type = :type AND parent_id IS NOT NULL AND archived = 0 ORDER BY sort_order, id")
    suspend fun allChildrenOnce(type: Int): List<CategoryEntity>

    @Query("SELECT COUNT(*) FROM category WHERE parent_id = :parentId")
    suspend fun countChildren(parentId: Long): Int

    @Query("SELECT COUNT(*) FROM trade WHERE category_id = :categoryId")
    suspend fun countTrades(categoryId: Long): Int

    @Query("SELECT id FROM category WHERE parent_id = :parentId")
    suspend fun childIds(parentId: Long): List<Long>

    @Query("SELECT COALESCE(MAX(sort_order), -1) FROM category WHERE parent_id IS :parentId")
    suspend fun maxSortOrder(parentId: Long?): Int

    @Query("UPDATE category SET parent_id = :parentId, sort_order = :order WHERE id = :id")
    suspend fun setPosition(id: Long, parentId: Long?, order: Int)
}
