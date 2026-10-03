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

    /**
     * "1" while Home's available-balance figure is masked. Stored as "hidden" rather than
     * "visible" so an absent row — every pre-existing database and backup — means shown.
     */
    const val AVAILABLE_BALANCE_HIDDEN = "available_balance_hidden"

    /**
     * Legacy: "1" while Home's savings *and* total balance figures were masked by one shared toggle.
     * No longer written; read only as the fallback for [SAVINGS_BALANCE_HIDDEN] and
     * [TOTAL_BALANCE_HIDDEN] while their own rows are absent, so an older choice carries over.
     */
    const val OTHER_BALANCES_HIDDEN = "other_balances_hidden"

    /** "1" while Home's free-balance figure is masked. Absent row means shown. */
    const val FREE_BALANCE_HIDDEN = "free_balance_hidden"

    /** "1" while Home's budgets-balance figure is masked. Absent row means shown. */
    const val BUDGETS_BALANCE_HIDDEN = "budgets_balance_hidden"

    /** "1" while Home's savings-balance figure is masked. Absent row falls back to [OTHER_BALANCES_HIDDEN]. */
    const val SAVINGS_BALANCE_HIDDEN = "savings_balance_hidden"

    /** "1" while Home's total-balance figure is masked. Absent row falls back to [OTHER_BALANCES_HIDDEN]. */
    const val TOTAL_BALANCE_HIDDEN = "total_balance_hidden"

    /**
     * Comma-separated order of Home's balance rows, by name (e.g. "FREE,BUDGETS,SAVINGS,AVAILABLE,TOTAL").
     * The first is drawn large. Absent row means the default order; unknown names are skipped and
     * missing ones appended, so a renamed or added balance never breaks an older value.
     */
    const val HOME_BALANCE_ORDER = "home_balance_order"
}
