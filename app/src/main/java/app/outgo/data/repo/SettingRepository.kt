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

    /** Whether Home masks its available-balance figure. No SharedPreferences mirror: Home is not
     *  the start destination, and HomeViewModel reads this in the same flow as the balances, so the
     *  figure and its mask always arrive together. */
    fun observeAvailableBalanceHidden(): Flow<Boolean> = observeFlag(SettingKeys.AVAILABLE_BALANCE_HIDDEN)

    /** Whether Home masks its savings and total balance figures. */
    fun observeOtherBalancesHidden(): Flow<Boolean> = observeFlag(SettingKeys.OTHER_BALANCES_HIDDEN)

    suspend fun setAvailableBalanceHidden(hidden: Boolean) = setFlag(SettingKeys.AVAILABLE_BALANCE_HIDDEN, hidden)

    suspend fun setOtherBalancesHidden(hidden: Boolean) = setFlag(SettingKeys.OTHER_BALANCES_HIDDEN, hidden)

    private fun observeFlag(key: String): Flow<Boolean> = settingDao.observe(key).map { it == "1" }

    private suspend fun setFlag(key: String, value: Boolean) {
        settingDao.set(SettingEntity(key, if (value) "1" else "0"))
    }

    suspend fun get(key: String): String? = settingDao.get(key)

    suspend fun set(key: String, value: String) = settingDao.set(SettingEntity(key, value))
}
