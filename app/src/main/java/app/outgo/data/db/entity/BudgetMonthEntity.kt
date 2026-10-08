package app.outgo.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

/**
 * A budget's settings from [monthKey] on: a month uses the newest row at or before it, so a month
 * with no row of its own repeats the last one before it, and a month before the first row has no
 * budget. Written by the category editor for the month it shows (see BudgetRepository.setMonth).
 * [overTarget]/[underTarget]/[underFromAccount]/[underToAccount] are encoded as on [BudgetEntity].
 */
@Entity(
    tableName = "budget_month",
    primaryKeys = ["budget_id", "month_key"],
    foreignKeys = [
        ForeignKey(
            entity = BudgetEntity::class,
            parentColumns = ["id"],
            childColumns = ["budget_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class BudgetMonthEntity(
    @ColumnInfo(name = "budget_id")
    val budgetId: Long,
    /** yyyyMM. */
    @ColumnInfo(name = "month_key")
    val monthKey: Int,
    /** Off: no budget this month (no bar, no offsets); the other values are kept for turning it back on. */
    @ColumnInfo(name = "enabled")
    val enabled: Boolean,
    @ColumnInfo(name = "limit_amount")
    val limitAmount: Long,
    @ColumnInfo(name = "over_target")
    val overTarget: Long? = null,
    @ColumnInfo(name = "under_target")
    val underTarget: Long? = null,
    @ColumnInfo(name = "under_from_account")
    val underFromAccount: Long? = null,
    @ColumnInfo(name = "under_to_account")
    val underToAccount: Long? = null,
    /**
     * On (a parent category's budget only): the month's limit is the sum of its subcategories'
     * limits that month, [limitAmount] and the offsets above are kept but not used.
     */
    @ColumnInfo(name = "sum_children", defaultValue = "0")
    val sumChildren: Boolean = false,
)
