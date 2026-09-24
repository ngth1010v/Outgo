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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HistoryUiState(
    val trades: List<TradeEntity> = emptyList(),
    /** [trades] grouped into day headers + rows, built off the main thread. */
    val items: List<HistoryListItem> = emptyList(),
    val categoriesById: Map<Long, CategoryEntity> = emptyMap(),
    val accountsById: Map<Long, AccountEntity> = emptyMap(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = true,
)

private const val PAGE_SIZE = 50
private const val DAY_MILLIS = 24L * 60 * 60 * 1000

class HistoryViewModel(
    private val tradeRepository: TradeRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    historyType: HistoryType,
    /** Start of a single day to show, or null for the whole (paged) history. */
    private val dayStartMillis: Long? = null,
) : ViewModel() {

    private val tradeType = when (historyType) {
        HistoryType.EXPENSE -> TradeType.EXPENSE
        HistoryType.INCOME -> TradeType.INCOME
        HistoryType.TRANSFER -> TradeType.TRANSFER
    }
    private val categoryKind = if (historyType == HistoryType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE

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
        // No refresh() here: both screens call it when they enter composition, which is also
        // what re-reads the list after an edit. Calling it here too ran the first page twice.
    }

    /** Re-runs the first page query. Call when the screen re-enters composition — the trade list is a one-shot fetch, not a Flow, so an edit made elsewhere (e.g. the trade-edit screen) isn't seen until this runs again. */
    fun refresh() {
        viewModelScope.launch {
            // A single day is small enough to come back in one query, so it never pages.
            val first = if (dayStartMillis == null) {
                tradeRepository.firstPage(tradeType, PAGE_SIZE)
            } else {
                tradeRepository.pageInRange(tradeType, dayStartMillis, dayStartMillis + DAY_MILLIS)
            }
            val items = withContext(Dispatchers.Default) { buildHistoryItems(first) }
            _state.update {
                it.copy(
                    trades = first,
                    items = items,
                    isLoading = false,
                    canLoadMore = dayStartMillis == null && first.size == PAGE_SIZE,
                )
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.isLoadingMore || !s.canLoadMore || s.trades.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = true) }
            val next = tradeRepository.nextPage(tradeType, s.trades.last(), PAGE_SIZE)
            val trades = s.trades + next
            val items = withContext(Dispatchers.Default) { buildHistoryItems(trades) }
            _state.update {
                it.copy(trades = trades, items = items, isLoadingMore = false, canLoadMore = next.size == PAGE_SIZE)
            }
        }
    }
}
