package app.outgo.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A monthly spending LIMIT on one category. See [app.outgo.domain.BudgetKind].
 *
 * account_id/target_amount/deadline are leftover columns from a removed
 * SAVING budget kind (replaced by savings accounts, [app.outgo.domain.AccountType.SAVINGS])
 * and are no longer written or read by app code.
 */
@Entity(
    tableName = "budget",
    foreignKeys = [
        ForeignKey(
            entity = IconEntity::class,
            parentColumns = ["id"],
            childColumns = ["icon_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["category_id"], unique = true),
        Index("account_id"),
    ],
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "kind")
    val kind: Int,
    @ColumnInfo(name = "name")
    val name: String? = null,
    @ColumnInfo(name = "icon_id")
    val iconId: Long? = null,
    @ColumnInfo(name = "category_id")
    val categoryId: Long? = null,
    @ColumnInfo(name = "limit_amount")
    val limitAmount: Long? = null,
    @ColumnInfo(name = "account_id")
    val accountId: Long? = null,
    @ColumnInfo(name = "target_amount")
    val targetAmount: Long? = null,
    @ColumnInfo(name = "deadline")
    val deadline: Long? = null,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
