package app.outgo.data.db.dao

import androidx.room.Embedded
import app.outgo.data.db.entity.AccountEntity

/** An account joined with how much income it's received this month, toward [AccountEntity.savingsTarget]. */
data class AccountWithProgress(
    @Embedded val account: AccountEntity,
    val monthlyIncome: Long,
)

/** One (month, parent category) bar segment for the Home stacked chart. */
data class MonthCategoryTotal(
    val monthKey: Int,
    val rootId: Long,
    val type: Int,
    val name: String,
    val color: Int,
    val total: Long,
)

/** A LIMIT budget joined with how much of it has been spent this month. */
data class BudgetWithProgress(
    val id: Long,
    val kind: Int,
    val name: String?,
    val iconId: Long?,
    val categoryId: Long?,
    val limitAmount: Long?,
    val sortOrder: Int,
    val spent: Long,
    val categoryName: String?,
    val categoryIconId: Long?,
    val categoryColor: Int?,
) {
    val displayName: String get() = if (name != null) name else categoryName.orEmpty()
    val displayIconId: Long? get() = iconId ?: categoryIconId
}
