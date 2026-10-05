package app.outgo.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single ledger entry. [amount] is always positive; the sign is implied by
 * [type] (see [app.outgo.domain.TradeType]). Every write to this table is
 * mirrored by SQL triggers into `account.balance`, `category_month_stat` and
 * `category.use_count/last_used_at` — see OutgoDatabase for the trigger SQL.
 */
@Entity(
    tableName = "trade",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["type", "occurred_at", "id"]),
        Index(value = ["account_id", "occurred_at"]),
        Index(value = ["category_id", "created_at"]),
        // Monthly sums (account income, budget spend) filter by month first.
        Index(value = ["month_key", "type"]),
    ],
)
data class TradeEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "type")
    val type: Int,
    @ColumnInfo(name = "amount")
    val amount: Long,
    @ColumnInfo(name = "account_id")
    val accountId: Long,
    /**
     * On a [app.outgo.domain.TradeType.TRANSFER], the category of the budget it was taken from
     * ("From budget"), or null: the triggers then count it as that category's spending.
     */
    @ColumnInfo(name = "category_id")
    val categoryId: Long?,
    /** Destination account for [app.outgo.domain.TradeType.TRANSFER]; null otherwise. */
    @ColumnInfo(name = "to_account_id")
    val toAccountId: Long? = null,
    @ColumnInfo(name = "occurred_at")
    val occurredAt: Long,
    @ColumnInfo(name = "month_key")
    val monthKey: Int,
    @ColumnInfo(name = "note")
    val note: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    /**
     * Set on a budget-offset transfer the source account could not cover: [amount] stays 0 and
     * this holds what still has to move. Null for every other trade.
     */
    @ColumnInfo(name = "pending_amount")
    val pendingAmount: Long? = null,
    /**
     * On an automatic budget-offset transfer: the category of the budget whose month [offsetMonth]
     * (yyyyMM) left the unspent amount it moves. Such a transfer follows that budget's settings (see
     * BudgetRepository.setMonth), shows that budget as its source, and can't be edited by hand.
     * Not [categoryId]: that would count the transfer as the category's spending. Null on every
     * other trade.
     */
    @ColumnInfo(name = "offset_category_id")
    val offsetCategoryId: Long? = null,
    @ColumnInfo(name = "offset_month")
    val offsetMonth: Int? = null,
)
