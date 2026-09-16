package app.outgo.ui.balance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.outgo.data.db.entity.AccountEntity
import app.outgo.data.repo.AccountRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BalanceViewModel(private val accountRepository: AccountRepository) : ViewModel() {

    val accounts: StateFlow<List<AccountEntity>> = accountRepository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun hasTrades(accountId: Long): Boolean = accountRepository.hasTrades(accountId)

    fun create(name: String, iconId: Long?, color: Int, balance: Long) {
        viewModelScope.launch { accountRepository.create(name, iconId, color, balance) }
    }

    fun update(accountId: Long, name: String, iconId: Long?, color: Int, balance: Long) {
        viewModelScope.launch { accountRepository.update(accountId, name, iconId, color, balance) }
    }

    fun deleteOrArchive(account: AccountEntity) {
        viewModelScope.launch { accountRepository.deleteOrArchive(account) }
    }
}
