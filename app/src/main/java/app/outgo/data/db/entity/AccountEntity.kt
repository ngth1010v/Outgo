package app.outgo.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.outgo.domain.AccountType

/**
 * A place money is held (cash, a bank account, an e-wallet, a savings pot…).
 * [balance] is never written directly by the UI — it is only ever changed by
 * the SQL triggers on [TradeEntity] (see OutgoDatabase), so it always equals
 * the sum of that account's trades.
 *
 * A parent ([parentId] == null) or a subaccount. Like categories, trades only ever target a
 * subaccount: a parent groups them, its [balance] stays 0 and its shown balance is the sum of its
 * subaccounts'. Both levels have their own type and monthly savings targets.
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
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["parent_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("icon_id"), Index("parent_id")],
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "parent_id")
    val parentId: Long? = null,
    @ColumnInfo(name = "name")
    val name: String,
    /** Optional free text, null when empty. */
    @ColumnInfo(name = "description")
    val description: String? = null,
    @ColumnInfo(name = "icon_id")
    val iconId: Long?,
    @ColumnInfo(name = "balance")
    val balance: Long = 0,
    @ColumnInfo(name = "color")
    val color: Int = 0,
    /** [app.outgo.domain.AccountType]. */
    @ColumnInfo(name = "account_type", defaultValue = "0")
    val accountType: Int = 0,
    /** Leftover since schema 10 (monthly targets live in [SavingsMonthEntity]); only seeded it. */
    @ColumnInfo(name = "savings_target")
    val savingsTarget: Long? = null,
    /** Leftover since schema 10, like [savingsTarget]. */
    @ColumnInfo(name = "savings_from")
    val savingsFrom: Int? = null,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,
    @ColumnInfo(name = "archived")
    val archived: Boolean = false,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    /**
     * Savings subaccounts only: what comes into this subaccount is left out of its parent's
     * monthly savings progress. Balances, and this subaccount's own target, still count it.
     */
    @ColumnInfo(name = "parent_saving_exempt", defaultValue = "0")
    val parentSavingExempt: Boolean = false,
) {
    val isParent: Boolean get() = parentId == null

    /** A savings subaccount left out of its parent's savings progress; the flag means nothing elsewhere. */
    val isExempt: Boolean get() = parentId != null && accountType == AccountType.SAVINGS && parentSavingExempt
}
