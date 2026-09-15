package app.outgo.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.outgo.data.db.entity.AccountEntity
import app.outgo.data.db.entity.CategoryEntity
import app.outgo.data.db.entity.TradeEntity
import app.outgo.data.repo.AccountRepository
import app.outgo.data.repo.CategoryRepository
import app.outgo.data.repo.TradeRepository
import app.outgo.domain.CategoryKind
import app.outgo.domain.TradeType
import app.outgo.ui.nav.HistoryType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryUiState(
    val trades: List<TradeEntity> = emptyList(),
    val categoriesById: Map<Long, CategoryEntity> = emptyMap(),
    val accountsById: Map<Long, AccountEntity> = emptyMap(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = true,
)

private const val PAGE_SIZE = 50

class HistoryViewModel(
    private val tradeRepository: TradeRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    historyType: HistoryType,
) : ViewModel() {

    private val tradeType = if (historyType == HistoryType.EXPENSE) TradeType.EXPENSE else TradeType.INCOME
    private val categoryKind = if (historyType == HistoryType.EXPENSE) CategoryKind.EXPENSE else CategoryKind.INCOME

    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            accountRepository.observeAll().collect { list ->
                _state.update { it.copy(accountsById = list.associateBy { a -> a.id }) }
            }
        }
        viewModelScope.launch {
            categoryRepository.observeAllOfType(categoryKind).collect { list ->
                _state.update { it.copy(categoriesById = list.associateBy { c -> c.id }) }
            }
        }
        viewModelScope.launch {
            val first = tradeRepository.firstPage(tradeType, PAGE_SIZE)
            _state.update { it.copy(trades = first, isLoading = false, canLoadMore = first.size == PAGE_SIZE) }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.isLoadingMore || !s.canLoadMore || s.trades.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = true) }
            val next = tradeRepository.nextPage(tradeType, s.trades.last(), PAGE_SIZE)
            _state.update {
                it.copy(trades = it.trades + next, isLoadingMore = false, canLoadMore = next.size == PAGE_SIZE)
            }
        }
    }
}
