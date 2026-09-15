package app.outgo.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import app.outgo.data.db.entity.IconEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IconDao {
    @Insert
    suspend fun insert(icon: IconEntity): Long

    @Query("SELECT * FROM icon WHERE asset_key = :assetKey LIMIT 1")
    suspend fun findByAssetKey(assetKey: String): IconEntity?

    @Query("SELECT * FROM icon WHERE sha256 = :sha256 LIMIT 1")
    suspend fun findBySha256(sha256: String): IconEntity?

    @Query("SELECT * FROM icon WHERE id = :id")
    suspend fun findById(id: Long): IconEntity?

    @Query("SELECT * FROM icon WHERE kind = 1 ORDER BY created_at DESC")
    fun observeUserIcons(): Flow<List<IconEntity>>

    /** User-imported icons not referenced by any account, category or budget — safe to delete. */
    @Query(
        """
        SELECT * FROM icon
        WHERE kind = 1
          AND id NOT IN (SELECT icon_id FROM account WHERE icon_id IS NOT NULL)
          AND id NOT IN (SELECT icon_id FROM category WHERE icon_id IS NOT NULL)
          AND id NOT IN (SELECT icon_id FROM budget WHERE icon_id IS NOT NULL)
        ORDER BY created_at DESC
        """,
    )
    suspend fun findUnusedUserIcons(): List<IconEntity>

    @Delete
    suspend fun delete(icon: IconEntity)
}
