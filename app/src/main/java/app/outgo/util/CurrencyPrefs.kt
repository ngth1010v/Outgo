package app.outgo.util

import android.content.Context

/**
 * SharedPreferences mirror of the chosen currency symbol (source of truth is the `setting`
 * table, see [app.outgo.data.db.entity.SettingKeys.CURRENCY]) — the Trade screen is the start
 * destination and shows the symbol next to its amount field, so the first frame must paint the
 * right one instead of flashing the default while the database opens. Same rationale as
 * [LocalePrefs]; see CLAUDE.md's SharedPreferences note.
 */
object CurrencyPrefs {
    private const val KEY_CURRENCY = "currency"

    fun get(context: Context): String = context
        .getSharedPreferences(LocalePrefs.PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_CURRENCY, Money.DEFAULT_SYMBOL)
        .orEmpty()
        .ifBlank { Money.DEFAULT_SYMBOL }

    fun set(context: Context, symbol: String) {
        context.getSharedPreferences(LocalePrefs.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_CURRENCY, symbol).apply()
    }
}
