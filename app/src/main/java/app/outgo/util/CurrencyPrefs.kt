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
    private const val KEY_NUMBER_STYLE = "number_style"
    private const val KEY_DECIMALS = "decimals"

    fun get(context: Context): String = context
        .getSharedPreferences(LocalePrefs.PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_CURRENCY, null)
        .orEmpty()
        .ifBlank { Money.regionalSymbol() }

    fun set(context: Context, symbol: String) {
        context.getSharedPreferences(LocalePrefs.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_CURRENCY, symbol).apply()
    }

    /** Mirror of the number style, for the same first-frame reason as the symbol. */
    fun getNumberStyle(context: Context): NumberStyle = NumberStyle.fromKey(
        context.getSharedPreferences(LocalePrefs.PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_NUMBER_STYLE, null),
    )

    fun setNumberStyle(context: Context, style: NumberStyle) {
        context.getSharedPreferences(LocalePrefs.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_NUMBER_STYLE, style.key).apply()
    }

    /** Mirror of the decimal places, for the same first-frame reason as the symbol. */
    fun getDecimals(context: Context): Int = context
        .getSharedPreferences(LocalePrefs.PREFS_NAME, Context.MODE_PRIVATE)
        .getInt(KEY_DECIMALS, Money.DEFAULT_DECIMALS)
        .coerceIn(0, Money.MAX_DECIMALS)

    fun setDecimals(context: Context, decimals: Int) {
        context.getSharedPreferences(LocalePrefs.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_DECIMALS, decimals).apply()
    }
}
