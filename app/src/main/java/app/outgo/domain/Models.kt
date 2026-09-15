package app.outgo.domain

/** What the Trade screen collects before it is turned into a [app.outgo.data.db.entity.TradeEntity]. */
data class TradeDraft(
    val type: Int, // TradeType.EXPENSE or TradeType.INCOME
    val amount: Long,
    val accountId: Long,
    val categoryId: Long,
    val occurredAt: Long,
    val note: String?,
)

/** The two rows of quick-pick categories shown on the Trade screen. */
data class CategoryPicker(
    val recent: List<app.outgo.data.db.entity.CategoryEntity>,
    val top: List<app.outgo.data.db.entity.CategoryEntity>,
) {
    val allIconIds: List<Long> get() = (recent + top).mapNotNull { it.iconId }
}
