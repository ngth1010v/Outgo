package app.outgo.data.db.dao

/** One (month, parent category) bar segment for the Home stacked chart. */
data class MonthCategoryTotal(
    val monthKey: Int,
    val rootId: Long,
    val type: Int,
    val name: String,
    val color: Int,
    val total: Long,
)

/**
 * A budget joined with how much of it has been used this month. [spent] is
 * meaningful for [app.outgo.domain.BudgetKind.LIMIT], [saved] for
 * [app.outgo.domain.BudgetKind.SAVING].
 */
data class BudgetWithProgress(
    val id: Long,
    val kind: Int,
    val name: String?,
    val iconId: Long?,
    val categoryId: Long?,
    val limitAmount: Long?,
    val accountId: Long?,
    val targetAmount: Long?,
    val deadline: Long?,
    val sortOrder: Int,
    val spent: Long,
    val saved: Long,
    // Only set for kind == LIMIT (budget.name/icon_id are only meaningful for SAVING).
    val categoryName: String?,
    val categoryIconId: Long?,
) {
    val displayName: String get() = if (name != null) name else categoryName.orEmpty()
    val displayIconId: Long? get() = iconId ?: categoryIconId
}
