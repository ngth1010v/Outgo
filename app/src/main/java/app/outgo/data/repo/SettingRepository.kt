package app.outgo.data.repo

import android.content.Context
import app.outgo.data.db.dao.SettingDao
import app.outgo.data.db.entity.SettingEntity
import app.outgo.data.db.entity.SettingKeys
import app.outgo.util.CurrencyPrefs
import app.outgo.util.LocalePrefs
import app.outgo.util.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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

    /**
     * Whether Home masks the balance figure stored under [key] (one of the `*_BALANCE_HIDDEN`
     * [SettingKeys]). While [key] has no row, [legacyKey]'s value is used instead. No
     * SharedPreferences mirror: Home is not the start destination, and HomeViewModel reads this in
     * the same flow as the balances, so the figure and its mask always arrive together.
     */
    fun observeBalanceHidden(key: String, legacyKey: String? = null): Flow<Boolean> {
        val own = settingDao.observe(key)
        if (legacyKey == null) return own.map { it == "1" }
        return combine(own, settingDao.observe(legacyKey)) { value, legacy -> (value ?: legacy) == "1" }
    }

    suspend fun setBalanceHidden(key: String, hidden: Boolean) = setFlag(key, hidden)

    /** Home's balance row order as stored (see [SettingKeys.HOME_BALANCE_ORDER]); null until the user reorders. */
    fun observeHomeBalanceOrder(): Flow<String?> = settingDao.observe(SettingKeys.HOME_BALANCE_ORDER)

    suspend fun setHomeBalanceOrder(order: String) = settingDao.set(SettingEntity(SettingKeys.HOME_BALANCE_ORDER, order))

    private suspend fun setFlag(key: String, value: Boolean) {
        settingDao.set(SettingEntity(key, if (value) "1" else "0"))
    }

    suspend fun get(key: String): String? = settingDao.get(key)

    suspend fun set(key: String, value: String) = settingDao.set(SettingEntity(key, value))
}
