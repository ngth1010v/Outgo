package app.outgo.data.repo

import android.content.Context
import app.outgo.data.db.dao.SettingDao
import app.outgo.data.db.entity.SettingEntity
import app.outgo.data.db.entity.SettingKeys
import app.outgo.util.LocalePrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingRepository(private val context: Context, private val settingDao: SettingDao) {

    /** BCP-47 language tag ("en", "vi"), or "" to follow the system locale. */
    fun observeLocale(): Flow<String> = settingDao.observe(SettingKeys.LOCALE).map { it.orEmpty() }

    suspend fun setLocale(languageTag: String) {
        settingDao.set(SettingEntity(SettingKeys.LOCALE, languageTag))
        // Mirrored to SharedPreferences so the next cold start can apply it in
        // attachBaseContext, before the database can safely be opened.
        LocalePrefs.set(context, languageTag)
    }
}
