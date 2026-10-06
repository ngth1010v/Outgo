package app.outgo.data.repo

import androidx.room.withTransaction
import app.outgo.data.db.OutgoDatabase
import app.outgo.data.db.dao.AccountDao
import app.outgo.data.db.dao.AccountWithProgress
import app.outgo.data.db.entity.AccountEntity
import app.outgo.data.db.entity.SavingsMonthEntity
import app.outgo.domain.AccountType
import app.outgo.util.MonthKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext

/** A savings account's target for one month, as the account editor shows and saves it. */
data class SavingsSetting(val enabled: Boolean, val target: Long)

/**
 * `account.balance` is never assigned directly here — editing "current
 * balance" in the UI is translated into an adjustment [TradeRepository]
 * trade, so the balance always stays equal to the sum of that account's
 * trades (see architecture.md §7.3).
 */
class AccountRepository(
    private val db: OutgoDatabase,
    private val accountDao: AccountDao,
    private val tradeRepository: TradeRepository,
) {
    fun observeActive(): Flow<List<AccountEntity>> = accountDao.observeActive()
    /** Shared by Home and Accounts: one live query per month however many screens watch it. */
    fun observeActiveWithProgress(monthKey: Int): Flow<List<AccountWithProgress>> = synchronized(progress) {
        progress.getOrPut(monthKey) {
            accountDao.observeActiveWithProgress(monthKey, MonthKey.minus(monthKey, 1))
                .shareIn(shareScope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), replay = 1)
        }
    }

    private val progress = HashMap<Int, Flow<List<AccountWithProgress>>>()
    private val shareScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    fun observeAll(): Flow<List<AccountEntity>> = accountDao.observeAll()

    suspend fun findById(id: Long): AccountEntity? = withContext(Dispatchers.IO) { accountDao.findById(id) }

    /** Last account used in a trade, else the first active account, else null (no accounts yet). */
    suspend fun defaultAccountId(): Long? = withContext(Dispatchers.IO) {
        (accountDao.lastUsedAccount() ?: accountDao.firstActiveOrNull())?.id
    }

    /**
     * Creates a subaccount of [parentId], or with null a parent plus its default subaccount (same
     * name, icon, color, description and type): trades only ever target a subaccount. The
     * [initialBalance] goes to the subaccount, [savings] to the account created at that level.
     * A savings parent's subaccounts are always savings accounts too.
     */
    suspend fun create(
        name: String,
        iconId: Long?,
        color: Int,
        initialBalance: Long,
        accountType: Int = AccountType.NORMAL,
        savings: SavingsSetting? = null,
        monthKey: Int = MonthKey.current(),
        description: String? = null,
        parentId: Long? = null,
    ): Long = withContext(Dispatchers.IO) {
        db.withTransaction {
            val now = System.currentTimeMillis()
            val type = if (parentId != null && accountDao.findById(parentId)?.accountType == AccountType.SAVINGS) AccountType.SAVINGS else accountType
            fun row(parent: Long?, order: Int) = AccountEntity(
                parentId = parent,
                name = name,
                description = description,
                iconId = iconId,
                balance = 0,
                color = color,
                accountType = type,
                sortOrder = order,
                createdAt = now,
                updatedAt = now,
            )
            val id = accountDao.insert(row(parentId, accountDao.maxSortOrder(parentId) + 1))
            val subId = if (parentId == null) accountDao.insert(row(id, 0)) else id
            if (initialBalance != 0L) tradeRepository.recordAdjustment(subId, initialBalance)
            if (savings != null && type == AccountType.SAVINGS) setSavingsMonth(id, monthKey, savings)
            id
        }
    }

    /**
     * [newBalance] is null for a parent: its balance is its subaccounts', not set by hand. A parent
     * made a savings account makes its subaccounts savings ones too (the editor asks first).
     */
    suspend fun update(
        accountId: Long,
        name: String,
        iconId: Long?,
        color: Int,
        newBalance: Long?,
        accountType: Int,
        description: String?,
    ) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val existing = accountDao.findById(accountId) ?: return@withTransaction
            accountDao.update(
                existing.copy(
                    name = name,
                    description = description,
                    iconId = iconId,
                    color = color,
                    accountType = accountType,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            if (existing.isParent && accountType == AccountType.SAVINGS) accountDao.setTypeWithChildren(listOf(accountId), AccountType.SAVINGS)
            val delta = (newBalance ?: existing.balance) - existing.balance
            if (delta != 0L) tradeRepository.recordAdjustment(accountId, delta)
        }
    }

    /** [accountId]'s savings target in effect in [monthKey], or null when it has none by then. */
    suspend fun savingsAt(accountId: Long, monthKey: Int): SavingsSetting? = withContext(Dispatchers.IO) {
        accountDao.savingsAt(accountId, monthKey)?.let { SavingsSetting(it.enabled, it.target) }
    }

    /**
     * Saves [setting] as [accountId]'s savings target from [monthKey] on. Like
     * [BudgetRepository.setMonth], a past month's edit first gives the month after a snapshot of
     * what it had, so it changes only that month.
     */
    suspend fun setSavingsMonth(accountId: Long, monthKey: Int, setting: SavingsSetting) = withContext(Dispatchers.IO) {
        db.withTransaction {
            if (monthKey < MonthKey.current()) {
                val next = MonthKey.minus(monthKey, -1)
                if (!accountDao.hasSavingsMonth(accountId, next)) {
                    accountDao.putSavingsMonth(
                        accountDao.savingsAt(accountId, next)?.copy(monthKey = next)
                            ?: SavingsMonthEntity(accountId, next, enabled = false, target = 0),
                    )
                }
            }
            accountDao.putSavingsMonth(SavingsMonthEntity(accountId, monthKey, setting.enabled, setting.target))
        }
    }

    suspend fun hasTrades(accountId: Long): Boolean = withContext(Dispatchers.IO) { accountDao.countTrades(accountId) > 0 }
    suspend fun childCount(parentId: Long): Int = withContext(Dispatchers.IO) { accountDao.childIds(parentId).size }

    /**
     * Saves each list's order as its rows' sort_order, under that list's parent (null: the top
     * level). A subaccount listed under a new parent moves there, its history with it; one moved
     * into a savings parent becomes a savings account (the list asks first).
     */
    suspend fun reorder(lists: Map<Long?, List<Long>>) = withContext(Dispatchers.IO) {
        db.withTransaction {
            lists.forEach { (parentId, ids) -> ids.forEachIndexed { index, id -> accountDao.setPosition(id, parentId, index) } }
            val savingsParents = lists.keys.filterNotNull().filter { accountDao.findById(it)?.accountType == AccountType.SAVINGS }
            if (savingsParents.isNotEmpty()) accountDao.setTypeWithChildren(savingsParents, AccountType.SAVINGS)
        }
    }

    /**
     * Hard-deletes an account with no history, otherwise archives it (hidden, history kept). A
     * parent first applies that to each subaccount, and is archived while an archived one remains.
     */
    suspend fun deleteOrArchive(account: AccountEntity) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val ids = (if (account.isParent) accountDao.childIds(account.id) else emptyList()) + account.id
            for (id in ids) {
                val row = accountDao.findById(id) ?: continue
                if (accountDao.countTrades(id) > 0 || accountDao.childIds(id).isNotEmpty()) {
                    accountDao.update(row.copy(archived = true))
                } else {
                    accountDao.delete(row)
                }
            }
        }
    }
}
