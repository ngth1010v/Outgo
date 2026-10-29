package app.outgo.ui.history

import kotlinx.coroutines.flow.combine
import app.outgo.data.repo.HistoryQuery
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
 * The History tab's filter; null fields match everything. [type] narrows the list to one
 * [TradeType]. Expense/Income use [accountId], [categoryId] (a parent) and [subCategoryId] (one of
 * its children); every type at once matches [accountId] as either side of a transfer too. Transfer
 * uses [accountId] (source), [budgetId] (the budget's category), [toAccountId] and [automatic].
 * [fromDay] and [toDay] are the starts of the first and last day shown (both included).
 */
data class HistoryFilter(
    val type: Int? = null,
    /** A parent account: with no [subAccountId], all its subaccounts. */
    val accountId: Long? = null,
    /** One of [accountId]'s subaccounts. */
    val subAccountId: Long? = null,
    val categoryId: Long? = null,
    val subCategoryId: Long? = null,
    val budgetId: Long? = null,
    /** Transfers only, like [accountId]. */
    val toAccountId: Long? = null,
    val toSubAccountId: Long? = null,
    val automatic: Boolean? = null,
    val fromDay: Long? = null,
    val toDay: Long? = null,
    val note: String = "",
    val minAmount: Long? = null,
    val maxAmount: Long? = null,
) {
    val isEmpty: Boolean get() = this == HistoryFilter()
    val isTransfer: Boolean get() = type == TradeType.TRANSFER

    /** [type] [ADJUSTMENT]: balance adjustments, both ways. */
    val isAdjustment: Boolean get() = type == ADJUSTMENT

    /** The "From" account the query matches: the subaccount, else the parent (and so all its subaccounts). */
    val fromAccount: Long? get() = subAccountId ?: accountId

    /** The "To" account the query matches, transfers only; see [fromAccount]. */
    val toAccount: Long? get() = (toSubAccountId ?: toAccountId).takeIf { isTransfer }

    /** Expense or income: the types that have categories. */
    val hasCategories: Boolean get() = type == TradeType.EXPENSE || type == TradeType.INCOME

    fun toQuery() = HistoryQuery(
        // Every type at once includes the balance adjustments: the list then adds up to the balances.
        types = when (type) {
            null -> listOf(TradeType.EXPENSE, TradeType.INCOME, TradeType.TRANSFER, TradeType.ADJUST_IN, TradeType.ADJUST_OUT)
            ADJUSTMENT -> listOf(TradeType.ADJUST_IN, TradeType.ADJUST_OUT)
            else -> listOf(type)
        },
        accountId = fromAccount,
        eitherAccount = type == null,
        toAccountId = toAccount,
        categoryId = subCategoryId ?: budgetId.takeIf { isTransfer },
        parentId = categoryId.takeIf { subCategoryId == null && hasCategories },
        automatic = automatic.takeIf { isTransfer },
        fromMillis = fromDay,
        toMillis = toDay?.let(::nextDayStart),
        noteContains = note.takeIf { it.isNotBlank() },
        minAmount = minAmount,
        maxAmount = maxAmount,
    )

    companion object {
        /** The filter's [type] for balance adjustments, in and out alike (see [TradeType.ADJUST_IN]). */
        const val ADJUSTMENT = TradeType.ADJUST_IN
    }
}

/** Start of the day after the one [dayStart] falls in, in local time. */
private fun nextDayStart(dayStart: Long): Long {
    val zone = java.time.ZoneId.systemDefault()
    return java.time.Instant.ofEpochMilli(dayStart).atZone(zone).toLocalDate().plusDays(1)
        .atStartOfDay(zone).toInstant().toEpochMilli()
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
    /** Adjustments that are an account's opening balance (its first entry): labelled as such. */
    val openingIds: Set<Long> = emptySet(),
)

private const val PAGE_SIZE = 50
private const val DAY_MILLIS = 24L * 60 * 60 * 1000

class HistoryViewModel(
    private val tradeRepository: TradeRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    /** One type's history (opened from Home or Analysis), or null for the History tab's list of all. */
    historyType: HistoryType?,
    /** Start of a single day to show, or null for the whole (paged) history. */
    private val dayStartMillis: Long? = null,
) : ViewModel() {

    private val tradeType = when (historyType) {
        HistoryType.EXPENSE -> TradeType.EXPENSE
        HistoryType.INCOME -> TradeType.INCOME
        HistoryType.TRANSFER -> TradeType.TRANSFER
        null -> null
    }

    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            accountRepository.observeAll().collect { list ->
                _state.update { it.copy(accountsById = list.associateBy { a -> a.id }) }
            }
        }
        viewModelScope.launch {
            val categories = when (historyType) {
                HistoryType.INCOME -> categoryRepository.observeAllOfType(CategoryKind.INCOME)
                null -> combine(
                    categoryRepository.observeAllOfType(CategoryKind.EXPENSE),
                    categoryRepository.observeAllOfType(CategoryKind.INCOME),
                ) { expense, income -> expense + income }
                else -> categoryRepository.observeAllOfType(CategoryKind.EXPENSE)
            }
            categories.collect { list ->
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
        tradeType == null -> tradeRepository.historyPage(filter.toQuery(), before, PAGE_SIZE)
        !filter.isEmpty -> tradeRepository.filteredPage(
            tradeType, before,
            accountId = filter.fromAccount,
            toAccountId = filter.toAccount,
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
                tradeRepository.pageInRange(tradeType ?: TradeType.EXPENSE, dayStartMillis, dayStartMillis + DAY_MILLIS)
            }
            val items = withContext(Dispatchers.Default) { buildHistoryItems(first) }
            val openingIds = tradeRepository.openingAdjustmentIds()
            _state.update {
                it.copy(
                    trades = first,
                    items = items,
                    openingIds = openingIds,
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
