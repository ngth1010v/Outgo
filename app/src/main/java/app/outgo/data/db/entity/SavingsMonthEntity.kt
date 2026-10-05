package app.outgo.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

/**
 * A savings account's monthly target from [monthKey] on, resolved like [BudgetMonthEntity]: the
 * newest row at or before a month applies. Only counts while the account is a savings account.
 */
@Entity(
    tableName = "savings_month",
    primaryKeys = ["account_id", "month_key"],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SavingsMonthEntity(
    @ColumnInfo(name = "account_id")
    val accountId: Long,
    /** yyyyMM. */
    @ColumnInfo(name = "month_key")
    val monthKey: Int,
    /** Off: no target this month; [target] is kept for turning it back on. */
    @ColumnInfo(name = "enabled")
    val enabled: Boolean,
    @ColumnInfo(name = "target")
    val target: Long,
)
