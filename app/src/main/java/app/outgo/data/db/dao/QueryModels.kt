package app.outgo.data.db.dao

import androidx.room.Embedded
import app.outgo.data.db.entity.AccountEntity

/** An account joined with how much income it's received this month, toward [monthlyTarget]. */
data class AccountWithProgress(
    @Embedded val account: AccountEntity,
    val monthlyIncome: Long,
    val prevMonthlyIncome: Long,
    /** This month's savings target: a savings account whose target in effect is on, else null. */
    val monthlyTarget: Long?,
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
    val limitAmount: Long?,
    val sortOrder: Int,
    val spent: Long,
    val prevSpent: Long,
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
    /** This month's limit after the carried offset. */
    val effectiveLimit: Long get() = (limitAmount ?: 0L) + carry
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
