package app.outgo.ui.balance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.outgo.data.db.dao.AccountWithProgress
import app.outgo.data.db.entity.AccountEntity
import app.outgo.data.repo.AccountRepository
import app.outgo.data.repo.SavingsSetting
import app.outgo.util.MonthKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BalanceViewModel(private val accountRepository: AccountRepository) : ViewModel() {

    val accounts: StateFlow<List<AccountWithProgress>> = accountRepository.observeActiveWithProgress(MonthKey.current())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun hasTrades(accountId: Long): Boolean = accountRepository.hasTrades(accountId)

    fun create(name: String, iconId: Long?, color: Int, balance: Long, accountType: Int, savings: SavingsSetting?, monthKey: Int, description: String?) {
        write { accountRepository.create(name, iconId, color, balance, accountType, savings, monthKey, description) }
    }

    fun update(accountId: Long, name: String, iconId: Long?, color: Int, balance: Long, accountType: Int, description: String?) {
        write { accountRepository.update(accountId, name, iconId, color, balance, accountType, description) }
    }

    suspend fun savingsAt(accountId: Long, monthKey: Int): SavingsSetting? = 
        // After any queued save, so a month just left reads back what was saved.
        writes.withLock { accountRepository.savingsAt(accountId, monthKey) }

    fun setSavingsMonth(accountId: Long, monthKey: Int, setting: SavingsSetting) {
        write { accountRepository.setSavingsMonth(accountId, monthKey, setting) }
    }

    /** Runs editor saves one after another, in the order they were asked for. */
    private val writes = Mutex()
    private fun write(block: suspend () -> Unit) {
        viewModelScope.launch { writes.withLock { block() } }
    }

    fun reorder(ids: List<Long>) {
        viewModelScope.launch { accountRepository.reorder(ids) }
    }

    fun deleteOrArchive(account: AccountEntity) {
        write { accountRepository.deleteOrArchive(account) }
    }
}
