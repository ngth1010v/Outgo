package app.outgo.domain

/** Values for `category.type` and the Trade screen's Expense/Income toggle. */
object CategoryKind {
    const val EXPENSE = 0
    const val INCOME = 1
}

/**
 * Values for `trade.type`. EXPENSE/INCOME are real user transactions and
 * always carry a category. ADJUST_IN/ADJUST_OUT are balance-adjustment
 * trades created when an account's "current balance" is edited directly;
 * they have no category and are excluded from charts and budgets.
 */
object TradeType {
    const val EXPENSE = 0
    const val INCOME = 1
    const val ADJUST_IN = 2
    const val ADJUST_OUT = 3

    /** True for types that increase the account balance. */
    fun isCredit(type: Int) = type == INCOME || type == ADJUST_IN
}

/** Values for `icon.kind`. */
object IconKind {
    const val BUILTIN = 0
    const val USER = 1
}

/** Values for `budget.kind`. */
object BudgetKind {
    const val LIMIT = 0
    const val SAVING = 1
}

/** Traffic-light color band for a budget's remaining amount. */
enum class BudgetLevel { OK, WARNING, OVER }

fun budgetLevel(spent: Long, limit: Long): BudgetLevel = when {
    limit <= 0 -> BudgetLevel.OK
    spent > limit -> BudgetLevel.OVER
    // spent / limit > 0.9 without floating point
    spent * 10 > limit * 9 -> BudgetLevel.WARNING
    else -> BudgetLevel.OK
}
