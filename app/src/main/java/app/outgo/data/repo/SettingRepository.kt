package app.outgo.data.repo

import android.content.Context
import app.outgo.data.db.dao.SettingDao
import app.outgo.data.db.entity.SettingEntity
import app.outgo.data.db.entity.SettingKeys
import app.outgo.util.CurrencyPrefs
import app.outgo.util.LocalePrefs
import app.outgo.util.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingRepository(private val context: Context, private val settingDao: SettingDao) {

    /** BCP-47 language tag ("en", "vi"), or "" to follow the system locale. */
    fun observeLocale(): Flow<String> = settingDao.observe(SettingKeys.LOCALE).map { it.orEmpty() }

    /** Currency symbol, defaulting to [Money.DEFAULT_SYMBOL] — backups taken before this setting existed have no row. */
    fun observeCurrency(): Flow<String> =
        settingDao.observe(SettingKeys.CURRENCY).map { it?.takeIf(String::isNotBlank) ?: Money.DEFAULT_SYMBOL }

    suspend fun setCurrency(symbol: String) {
        settingDao.set(SettingEntity(SettingKeys.CURRENCY, symbol))
        // Mirrored so the next cold start paints the right symbol in its first frame.
        CurrencyPrefs.set(context, symbol)
    }

    suspend fun setLocale(languageTag: String) {
        settingDao.set(SettingEntity(SettingKeys.LOCALE, languageTag))
        // Mirrored to SharedPreferences so the next cold start can apply it in
        // attachBaseContext, before the database can safely be opened.
        LocalePrefs.set(context, languageTag)
    }
}
