package app.outgo.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * SharedPreferences mirror of the user's chosen app language (source of truth is the `setting`
 * table, see [app.outgo.data.db.entity.SettingKeys.LOCALE]) — read synchronously from
 * `attachBaseContext`, before the database can safely be touched, so the very first frame
 * renders in the right language. See CLAUDE.md's "why things are lazy" / SharedPreferences note.
 */
object LocalePrefs {
    internal const val PREFS_NAME = "outgo_prefs"
    private const val KEY_LOCALE = "locale"

    /** BCP-47 language tag ("en", "vi"), or "" to follow the system locale. */
    fun get(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_LOCALE, "").orEmpty()

    fun set(context: Context, languageTag: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_LOCALE, languageTag).apply()
    }

    /** Wraps [context] with the stored locale applied, for `attachBaseContext`. No-op when following the system locale. */
    fun wrap(context: Context): Context {
        val tag = get(context)
        if (tag.isEmpty()) return context
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration).apply { setLocale(locale) }
        return context.createConfigurationContext(config)
    }
}
