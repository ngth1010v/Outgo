package app.outgo.data.repo

import app.outgo.data.db.dao.SettingDao
import app.outgo.data.db.entity.SettingEntity
import app.outgo.data.db.entity.SettingKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

enum class ThemeMode(val storageValue: String) {
    SYSTEM("system"), LIGHT("light"), DARK("dark");

    companion object {
        fun fromStorage(value: String?): ThemeMode = entries.find { it.storageValue == value } ?: SYSTEM
    }
}

class SettingRepository(private val settingDao: SettingDao) {

    fun observeThemeMode(): Flow<ThemeMode> = settingDao.observe(SettingKeys.THEME).map { ThemeMode.fromStorage(it) }

    suspend fun setThemeMode(mode: ThemeMode) = withContext(Dispatchers.IO) {
        settingDao.set(SettingEntity(SettingKeys.THEME, mode.storageValue))
    }
}
