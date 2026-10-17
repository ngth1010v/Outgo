package app.outgo.domain

/** What the Trade screen collects before it is turned into a [app.outgo.data.db.entity.TradeEntity]. */
data class TradeDraft(
    val type: Int, // TradeType.EXPENSE, INCOME or TRANSFER
    val amount: Long,
    val accountId: Long,
    val categoryId: Long?, // on a TradeType.TRANSFER, the optional "From budget"'s category
    val toAccountId: Long? = null, // set for TradeType.TRANSFER
    val occurredAt: Long,
    val note: String?,
    val budgetExempt: Boolean = false,
    val savingExempt: Boolean = false,
    val toCategoryId: Long? = null, // on a TradeType.TRANSFER, the optional "To budget"'s category
)

/** The two rows of quick-pick categories shown on the Trade screen. */
data class CategoryPicker(
    val recent: List<app.outgo.data.db.entity.CategoryEntity>,
    val top: List<app.outgo.data.db.entity.CategoryEntity>,
) {
    val allIconIds: List<Long> get() = (recent + top).mapNotNull { it.iconId }
}
