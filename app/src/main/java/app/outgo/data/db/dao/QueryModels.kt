package app.outgo.data.db.dao

import androidx.room.Embedded
import app.outgo.data.db.entity.AccountEntity

/** An account joined with how much income it's received this month, toward [monthlyTarget]. */
data class AccountWithProgress(
    @Embedded val account: AccountEntity,
    val monthlyIncome: Long,
    val prevMonthlyIncome: Long,
    /**
     * This month's savings target: a savings account whose target in effect is on, else null. A
     * parent summing its subaccounts' targets has their sum, which can be 0.
     */
    val monthlyTarget: Long?,
    /** The balance to show: a parent's is its active subaccounts' together, a subaccount's its own. */
    val totalBalance: Long,
)

/** One (month, parent category) bar segment for the Home stacked chart. */
data class MonthCategoryTotal(
    val monthKey: Int,
    val rootId: Long,
    val type: Int,
    val name: String,
    val color: Int,
    val iconId: Long?,
    val total: Long,
)

/** One category's total and trade count in one month, straight from `category_month_stat`. */
data class CategoryMonthTotal(val categoryId: Long, val monthKey: Int, val total: Long, val tradeCount: Int = 0)

/** Spend of one LIMIT budget (its category plus children) in one month. */
data class BudgetMonthSpend(
    val categoryId: Long,
    val monthKey: Int,
    val spent: Long,
)

/** A LIMIT budget with the settings in effect in one month, joined with how much it spent then. */
data class BudgetWithProgress(
    val id: Long,
    val kind: Int,
    val name: String?,
    val iconId: Long?,
    val categoryId: Long?,
    /** The budget's category's parent, null for a parent category's budget. */
    val parentCategoryId: Long?,
    /** That month's limit; with [sumChildren], the sum of its subcategories' limits. */
    val limitAmount: Long?,
    /** That month the limit is its subcategories' limits summed, and its own offsets don't apply. */
    val sumChildren: Boolean,
    val sortOrder: Int,
    val spent: Long,
    val prevSpent: Long,
    /** That month's top-ups ("To budget" transfers), its subcategories' included; added to the limit. */
    val funded: Long,
    val categoryName: String?,
    val categoryIconId: Long?,
    val categoryColor: Int?,
    val overTarget: Long?,
    val underTarget: Long?,
    val underFromAccount: Long?,
    val underToAccount: Long?,
    val settledMonth: Int?,
    /**
     * On in the month asked for (its [app.outgo.data.db.entity.BudgetMonthEntity] in effect then is
     * on): only then does it show a bar. The limit and offsets above are that month's.
     */
    val enabled: Boolean,
    /** Offset carried in from earlier months (negative: overspend taken off). Filled by BudgetRepository. */
    val carry: Long,
) {
    /** This month's limit after the carried offset and the top-ups. */
    val effectiveLimit: Long get() = (limitAmount ?: 0L) + carry + funded
    val displayName: String get() = if (name != null) name else categoryName.orEmpty()
    val displayIconId: Long? get() = iconId ?: categoryIconId
}

/**
 * The few trade columns the Analysis screen needs. Deliberately not `SELECT *`:
 * these rows are fetched a whole month at a time and every extra column is copied
 * for nothing.
 */
data class TradeSlim(
    val id: Long,
    val type: Int,
    val amount: Long,
    val occurredAt: Long,
    val categoryId: Long?,
    val note: String?,
    val accountId: Long = 0,
)

/**
 * Transfers summed per account pair. They carry no category, so nothing about them reaches
 * `category_month_stat` and the Analysis transfer section has to aggregate them directly.
 */
data class TransferTotal(
    val fromAccountId: Long,
    val toAccountId: Long?,
    val count: Int,
    val total: Long,
)

/** [type] is a [app.outgo.domain.TradeType], or [FLOW_TRANSFER_IN] for a transfer's receiving end. */
data class AccountFlow(
    val monthKey: Int,
    val accountId: Long,
    val type: Int,
    val total: Long,
)

/**
 * Analysis cash flow: trades summed per (type, account, receiving account, category), with
 * subaccounts folded into their parent account and subcategories into their parent category.
 * [toAccountId] is set only on a transfer, and [categoryId] (with its name, icon and colour) only on
 * an expense or income.
 */
data class CashFlowRow(
    val type: Int,
    val accountId: Long,
    val toAccountId: Long?,
    val categoryId: Long?,
    val categoryName: String?,
    val categoryIconId: Long?,
    val categoryColor: Int?,
    val total: Long,
)

/** Pseudo trade type: the receiving account's side of a transfer. */
const val FLOW_TRANSFER_IN = 5

data class AccountBalance(
    val accountId: Long,
    val balance: Long,
)

/** One trade's effect on one account's balance; a transfer comes back as two moves. */
data class AccountMove(
    val accountId: Long,
    val occurredAt: Long,
    val delta: Long,
)

/**
 * Analysis savings charts: one trade touching a savings account, with what decides whether it
 * counts toward a savings target ([savingExempt]) and whether it is an automatic budget leftover
 * ([offsetMonth] set). [toAccountId] is a transfer's receiving account, else null.
 */
data class SavingsTradeRow(
    val monthKey: Int,
    val type: Int,
    val amount: Long,
    val occurredAt: Long,
    val accountId: Long,
    val toAccountId: Long?,
    val savingExempt: Boolean,
    val offsetMonth: Int?,
)

/**
 * Analysis budget charts: a trade's effect on the budgets of its category. [funding] false is spend
 * (counted, not exempt); true is a "To budget" top-up of [categoryId]. [parentId]/[parentExempt] are
 * the category's, so a subcategory's money also reaches its parent's budget unless it is exempt.
 */
data class BudgetMoveRow(
    val monthKey: Int,
    val occurredAt: Long,
    val amount: Long,
    val categoryId: Long,
    val parentId: Long?,
    val parentExempt: Boolean,
    val funding: Boolean,
)

/** One category's stored month totals, with its kind and parent, for the budget coverage and trade frequency charts. */
data class CategoryMonthStatRow(
    val categoryId: Long,
    val parentId: Long?,
    val parentExempt: Boolean,
    val type: Int,
    val monthKey: Int,
    val total: Long,
    val exemptTotal: Long,
    val tradeCount: Int,
)
