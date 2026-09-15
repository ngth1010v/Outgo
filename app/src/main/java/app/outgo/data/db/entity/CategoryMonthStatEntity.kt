package app.outgo.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Per-(category, month) running total, maintained exclusively by triggers on
 * [TradeEntity] — never written to directly. This is what keeps the Home
 * chart and budget progress bars fast regardless of how many trades exist:
 * they read this small table instead of aggregating the full trade history.
 */
@Entity(
    tableName = "category_month_stat",
    primaryKeys = ["category_id", "month_key"],
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("month_key")],
)
data class CategoryMonthStatEntity(
    @ColumnInfo(name = "category_id")
    val categoryId: Long,
    @ColumnInfo(name = "month_key")
    val monthKey: Int,
    @ColumnInfo(name = "total")
    val total: Long = 0,
    @ColumnInfo(name = "trade_count")
    val tradeCount: Int = 0,
)
