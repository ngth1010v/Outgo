package app.outgo.ui.draft

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.outgo.data.db.entity.AccountEntity
import app.outgo.data.db.entity.CategoryEntity
import app.outgo.data.db.entity.TradeEntity
import app.outgo.data.draft.DraftStore
import app.outgo.data.draft.DraftTrade
import app.outgo.data.repo.AccountRepository
import app.outgo.data.repo.CategoryRepository
import app.outgo.data.repo.TradeRepository
import app.outgo.domain.CategoryKind
import app.outgo.domain.TradeType
import app.outgo.ui.history.HistoryListItem
import app.outgo.ui.history.buildHistoryItems
import app.outgo.ui.trade.balanceMoves
import app.outgo.util.MonthKey
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why a draft can't be saved any more: what it names was archived or deleted after it was added. */
enum class DraftProblem { ACCOUNT, TO_ACCOUNT, CATEGORY, BUDGET }

data class DraftListUiState(
    val drafts: List<DraftTrade> = emptyList(),
    /** The drafts as History draws them, newest first, grouped by day. */
    val items: List<HistoryListItem> = emptyList(),
    val categoriesById: Map<Long, CategoryEntity> = emptyMap(),
    val accountsById: Map<Long, AccountEntity> = emptyMap(),
    /** By draft id; a draft not in here can be pushed. */
    val problems: Map<Long, DraftProblem> = emptyMap(),
    /**
     * By draft id: the account this draft takes below 0, counting the drafts before it. Only a
     * warning, like on the Add screen: it doesn't keep the draft from being saved.
     */
    val overdrawn: Map<Long, AccountEntity> = emptyMap(),
    val isLoading: Boolean = true,
    val isPushing: Boolean = false,
) {
    val validCount: Int get() = drafts.count { it.id !in problems }
    val invalidCount: Int get() = problems.size
}

sealed interface DraftListEvent {
    /** Some were saved; the invalid ones stay. */
    data class Pushed(val count: Int) : DraftListEvent
    data class Error(val message: String) : DraftListEvent
}

class DraftListViewModel(
    private val draftStore: DraftStore,
    private val tradeRepository: TradeRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DraftListUiState())
    val state: StateFlow<DraftListUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<DraftListEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<DraftListEvent> = _events

    init {
        viewModelScope.launch {
            combine(
                draftStore.drafts,
                accountRepository.observeAll(),
                categoryRepository.observeAllOfType(CategoryKind.EXPENSE),
                categoryRepository.observeAllOfType(CategoryKind.INCOME),
            ) { drafts, accounts, expense, income ->
                val accountsById = accounts.associateBy { it.id }
                val categoriesById = (expense + income).associateBy { it.id }
                val problems = drafts.mapNotNull { d -> problemOf(d, accountsById, categoriesById)?.let { d.id to it } }.toMap()
                val overdrawn = overdrawnBy(drafts.filter { it.id !in problems }, accountsById)
                val trades = drafts.map { it.asEntity() }.sortedWith(compareByDescending<TradeEntity> { it.occurredAt }.thenByDescending { it.id })
                DraftListUiState(
                    drafts = drafts,
                    items = buildHistoryItems(trades),
                    categoriesById = categoriesById,
                    accountsById = accountsById,
                    problems = problems,
                    overdrawn = overdrawn,
                    isLoading = false,
                )
            }.collect { new -> _state.update { new.copy(isPushing = it.isPushing) } }
        }
    }

    fun remove(draftId: Long) = draftStore.remove(draftId)

    fun clear() {
        // Empty now: the screen goes back to the Add screen on its own.
        draftStore.clear()
    }

    /**
     * Saves the drafts that can be saved, in one transaction; the others stay in the list. The
     * Add screen then says how many were saved.
     */
    fun push() {
        val s = _state.value
        if (s.isPushing) return
        val valid = s.drafts.filter { it.id !in s.problems }
        if (valid.isEmpty()) return
        _state.update { it.copy(isPushing = true) }
        viewModelScope.launch {
            try {
                tradeRepository.insertAll(valid.map { it.trade })
                draftStore.removeAll(valid.map { it.id }.toSet())
                draftStore.notifyPushed(valid.size)
                // Drafts left (the invalid ones): the user stays here, so it is said here too.
                if (draftStore.drafts.value.isNotEmpty()) _events.emit(DraftListEvent.Pushed(valid.size))
            } catch (t: Throwable) {
                _events.emit(DraftListEvent.Error(t.message ?: "Error"))
            } finally {
                _state.update { it.copy(isPushing = false) }
            }
        }
    }

    /**
     * Saves the drafts in date order, on paper: a draft is marked when it takes an account below
     * 0 (the Add screen's rule: an account that loses money and ends below 0).
     */
    private fun overdrawnBy(drafts: List<DraftTrade>, accounts: Map<Long, AccountEntity>): Map<Long, AccountEntity> {
        val balances = accounts.mapValues { it.value.balance }.toMutableMap()
        val out = mutableMapOf<Long, AccountEntity>()
        for (d in drafts.sortedWith(compareBy<DraftTrade> { it.trade.occurredAt }.thenBy { it.id })) {
            val t = d.trade
            val moves = balanceMoves(t.type, t.amount, t.accountId, t.toAccountId.takeIf { t.type == TradeType.TRANSFER })
            moves.forEach { (id, delta) ->
                val after = (balances[id] ?: 0L) + delta
                balances[id] = after
                if (delta < 0 && after < 0 && d.id !in out) accounts[id]?.let { out[d.id] = it }
            }
        }
        return out
    }

    private fun problemOf(
        draft: DraftTrade,
        accounts: Map<Long, AccountEntity>,
        categories: Map<Long, CategoryEntity>,
    ): DraftProblem? {
        val t = draft.trade
        // What the Add screen offers: an active subaccount, an active category.
        fun usable(id: Long?) = accounts[id]?.let { !it.archived && !it.isParent } == true
        fun usableCategory(id: Long?) = categories[id]?.let { !it.archived } == true
        return when {
            !usable(t.accountId) -> DraftProblem.ACCOUNT
            t.type == TradeType.TRANSFER && !usable(t.toAccountId) -> DraftProblem.TO_ACCOUNT
            t.type != TradeType.TRANSFER && !usableCategory(t.categoryId) -> DraftProblem.CATEGORY
            t.type == TradeType.TRANSFER && listOfNotNull(t.categoryId, t.toCategoryId).any { !usableCategory(it) } -> DraftProblem.BUDGET
            else -> null
        }
    }
}

/** A draft drawn as a History row: its id is the draft's, not a saved trade's. */
private fun DraftTrade.asEntity() = TradeEntity(
    id = id,
    type = trade.type,
    amount = trade.amount,
    accountId = trade.accountId,
    categoryId = trade.categoryId,
    toAccountId = trade.toAccountId,
    occurredAt = trade.occurredAt,
    monthKey = MonthKey.of(trade.occurredAt),
    note = trade.note,
    createdAt = 0,
    updatedAt = 0,
    budgetExempt = trade.budgetExempt,
    savingExempt = trade.savingExempt,
    toCategoryId = trade.toCategoryId,
)
