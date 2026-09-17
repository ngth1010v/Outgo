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

/** Values for `account.account_type`. */
object AccountType {
    const val NORMAL = 0
    const val SAVINGS = 1
}

/** Values for `icon.kind`. */
object IconKind {
    const val BUILTIN = 0
    const val USER = 1
}

/**
 * Values for `budget.kind`. Only LIMIT (a monthly spending cap on a
 * category) remains — the old SAVING kind was replaced by savings accounts
 * (see [AccountType.SAVINGS]).
 */
object BudgetKind {
    const val LIMIT = 0
}
