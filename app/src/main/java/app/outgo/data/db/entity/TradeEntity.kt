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
    @ColumnInfo(name = "category_id")
    val categoryId: Long?,
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
)
