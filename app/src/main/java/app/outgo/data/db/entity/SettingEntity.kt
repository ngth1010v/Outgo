package app.outgo.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single app setting (theme, default account, currency symbol…), stored as
 * a key/value pair so it lives inside the same database file and is included
 * in every backup, instead of an OS-level SharedPreferences file.
 */
@Entity(tableName = "setting")
data class SettingEntity(
    @PrimaryKey
    @ColumnInfo(name = "key")
    val key: String,
    @ColumnInfo(name = "value")
    val value: String,
)

/** Well-known [SettingEntity.key] values. */
object SettingKeys {
    const val DEFAULT_ACCOUNT_ID = "default_account_id"

    /** BCP-47 language tag ("en", "vi"), or absent/empty to follow the system locale. */
    const val LOCALE = "locale"

    /** Currency symbol shown next to every amount, e.g. "$". Absent in pre-currency backups — see [app.outgo.util.Money.DEFAULT_SYMBOL]. */
    const val CURRENCY = "currency"
}
