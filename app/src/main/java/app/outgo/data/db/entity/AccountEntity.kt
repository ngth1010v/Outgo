package app.outgo.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A place money is held (cash, a bank account, an e-wallet, a savings pot…).
 * [balance] is never written directly by the UI — it is only ever changed by
 * the SQL triggers on [TradeEntity] (see OutgoDatabase), so it always equals
 * the sum of that account's trades.
 */
@Entity(
    tableName = "account",
    foreignKeys = [
        ForeignKey(
            entity = IconEntity::class,
            parentColumns = ["id"],
            childColumns = ["icon_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("icon_id")],
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "icon_id")
    val iconId: Long?,
    @ColumnInfo(name = "balance")
    val balance: Long = 0,
    @ColumnInfo(name = "color")
    val color: Int = 0,
    /** [app.outgo.domain.AccountType]. */
    @ColumnInfo(name = "account_type", defaultValue = "0")
    val accountType: Int = 0,
    /** Monthly savings target; only meaningful when [accountType] is SAVINGS. */
    @ColumnInfo(name = "savings_target")
    val savingsTarget: Long? = null,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,
    @ColumnInfo(name = "archived")
    val archived: Boolean = false,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
