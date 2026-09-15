package app.outgo.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.outgo.data.db.entity.SettingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingDao {
    @Upsert
    suspend fun set(setting: SettingEntity)

    @Query("SELECT value FROM setting WHERE key = :key")
    suspend fun get(key: String): String?

    @Query("SELECT value FROM setting WHERE key = :key")
    fun observe(key: String): Flow<String?>

    @Query("SELECT * FROM setting")
    suspend fun getAll(): List<SettingEntity>
}
