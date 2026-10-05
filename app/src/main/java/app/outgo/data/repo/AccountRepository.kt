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

    suspend fun create(
        name: String,
        iconId: Long?,
        color: Int,
        initialBalance: Long,
        accountType: Int = AccountType.NORMAL,
        savings: SavingsSetting? = null,
        monthKey: Int = MonthKey.current(),
    ): Long = withContext(Dispatchers.IO) {
        db.withTransaction {
            val now = System.currentTimeMillis()
            val order = accountDao.maxSortOrder() + 1
            val id = accountDao.insert(
                AccountEntity(
                    name = name,
                    iconId = iconId,
                    balance = 0,
                    color = color,
                    accountType = accountType,
                    sortOrder = order,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            if (initialBalance != 0L) tradeRepository.recordAdjustment(id, initialBalance)
            if (savings != null && accountType == AccountType.SAVINGS) setSavingsMonth(id, monthKey, savings)
            id
        }
    }

    suspend fun update(
        accountId: Long,
        name: String,
        iconId: Long?,
        color: Int,
        newBalance: Long,
        accountType: Int,
    ) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val existing = accountDao.findById(accountId) ?: return@withTransaction
            accountDao.update(
                existing.copy(
                    name = name,
                    iconId = iconId,
                    color = color,
                    accountType = accountType,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            val delta = newBalance - existing.balance
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

    /** Hard-deletes if the account has no history, otherwise archives it (hidden, history kept). */
    /** Saves [ids]' order as their sort_order. */
    suspend fun reorder(ids: List<Long>) = withContext(Dispatchers.IO) {
        db.withTransaction { ids.forEachIndexed { index, id -> accountDao.setSortOrder(id, index) } }
    }

    suspend fun deleteOrArchive(account: AccountEntity) = withContext(Dispatchers.IO) {
        if (accountDao.countTrades(account.id) > 0) {
            accountDao.update(account.copy(archived = true))
        } else {
            accountDao.delete(account)
        }
    }
}
