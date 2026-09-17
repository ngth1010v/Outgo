package app.outgo.data.repo

import androidx.room.withTransaction
import app.outgo.data.db.OutgoDatabase
import app.outgo.data.db.dao.AccountDao
import app.outgo.data.db.dao.AccountWithProgress
import app.outgo.data.db.entity.AccountEntity
import app.outgo.domain.AccountType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

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
    fun observeActiveWithProgress(monthKey: Int): Flow<List<AccountWithProgress>> = accountDao.observeActiveWithProgress(monthKey)
    fun observeAll(): Flow<List<AccountEntity>> = accountDao.observeAll()
    fun observeTotalBalance(): Flow<Long> = accountDao.observeTotalBalance()

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
        savingsTarget: Long? = null,
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
                    savingsTarget = savingsTarget.takeIf { accountType == AccountType.SAVINGS },
                    sortOrder = order,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            if (initialBalance != 0L) tradeRepository.recordAdjustment(id, initialBalance)
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
        savingsTarget: Long?,
    ) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val existing = accountDao.findById(accountId) ?: return@withTransaction
            accountDao.update(
                existing.copy(
                    name = name,
                    iconId = iconId,
                    color = color,
                    accountType = accountType,
                    savingsTarget = savingsTarget.takeIf { accountType == AccountType.SAVINGS },
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            val delta = newBalance - existing.balance
            if (delta != 0L) tradeRepository.recordAdjustment(accountId, delta)
        }
    }

    suspend fun hasTrades(accountId: Long): Boolean = withContext(Dispatchers.IO) { accountDao.countTrades(accountId) > 0 }

    /** Hard-deletes if the account has no history, otherwise archives it (hidden, history kept). */
    suspend fun deleteOrArchive(account: AccountEntity) = withContext(Dispatchers.IO) {
        if (accountDao.countTrades(account.id) > 0) {
            accountDao.update(account.copy(archived = true))
        } else {
            accountDao.delete(account)
        }
    }
}
