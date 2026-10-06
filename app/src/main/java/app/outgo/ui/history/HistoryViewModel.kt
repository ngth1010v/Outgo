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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The History tab's filter for one type; null fields match everything. Expense/Income use
 * [accountId], [categoryId] (a parent) and [subCategoryId] (one of its children). Transfer uses
 * [accountId] (source), [budgetId] (the budget's category), [toAccountId] and [automatic].
 */
data class HistoryFilter(
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val subCategoryId: Long? = null,
    val budgetId: Long? = null,
    val toAccountId: Long? = null,
    val automatic: Boolean? = null,
) {
    val isEmpty: Boolean get() = this == HistoryFilter()
}

data class HistoryUiState(
    val trades: List<TradeEntity> = emptyList(),
    /** [trades] grouped into day headers + rows, built off the main thread. */
    val items: List<HistoryListItem> = emptyList(),
    val categoriesById: Map<Long, CategoryEntity> = emptyMap(),
    val accountsById: Map<Long, AccountEntity> = emptyMap(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = true,
    val filter: HistoryFilter = HistoryFilter(),
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

    /** Loads in flight; a new filter or refresh cancels them so an older page can't land after it. */
    private var loadJob: Job? = null

    fun setFilter(filter: HistoryFilter) {
        if (filter == _state.value.filter) return
        // Emptied so a loadMore can't page the old filter's rows under the new one.
        _state.update { it.copy(filter = filter, trades = emptyList(), items = emptyList(), isLoading = true) }
        refresh()
    }

    /** A page after [before] (null: the first), through the filtered query when a filter is set. */
    private suspend fun page(filter: HistoryFilter, before: TradeEntity?): List<TradeEntity> = when {
        !filter.isEmpty -> tradeRepository.filteredPage(
            tradeType, before,
            accountId = filter.accountId,
            toAccountId = filter.toAccountId,
            categoryId = filter.subCategoryId ?: filter.budgetId,
            parentId = filter.categoryId.takeIf { filter.subCategoryId == null },
            automatic = filter.automatic,
            limit = PAGE_SIZE,
        )
        before == null -> tradeRepository.firstPage(tradeType, PAGE_SIZE)
        else -> tradeRepository.nextPage(tradeType, before, PAGE_SIZE)
    }

    /** Re-runs the first page query. Call when the screen re-enters composition — the trade list is a one-shot fetch, not a Flow, so an edit made elsewhere (e.g. the trade-edit screen) isn't seen until this runs again. */
    fun refresh() {
        loadJob?.cancel()
        _state.update { it.copy(isLoadingMore = false) }
        loadJob = viewModelScope.launch {
            // A single day is small enough to come back in one query, so it never pages.
            val first = if (dayStartMillis == null) {
                page(_state.value.filter, null)
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
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = true) }
            val next = page(s.filter, s.trades.last())
            val trades = s.trades + next
            val items = withContext(Dispatchers.Default) { buildHistoryItems(trades) }
            _state.update {
                it.copy(trades = trades, items = items, isLoadingMore = false, canLoadMore = next.size == PAGE_SIZE)
            }
        }
    }
}
