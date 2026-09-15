package app.outgo.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import app.outgo.data.db.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

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

    @Query(
        """
        SELECT * FROM category
        WHERE type = :type AND parent_id IS NOT NULL AND archived = 0 AND last_used_at IS NOT NULL
        ORDER BY last_used_at DESC LIMIT :limit
        """,
    )
    suspend fun mostRecent(type: Int, limit: Int): List<CategoryEntity>

    @Query(
        """
        SELECT * FROM category
        WHERE type = :type AND parent_id IS NOT NULL AND archived = 0 AND use_count > 0
        ORDER BY use_count DESC LIMIT :limit
        """,
    )
    suspend fun mostUsed(type: Int, limit: Int): List<CategoryEntity>

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
}
