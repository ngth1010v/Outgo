package app.outgo.ui.draft

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.outgo.data.db.dao.AccountWithProgress
import app.outgo.data.db.dao.BudgetWithProgress
import app.outgo.data.db.entity.AccountEntity
import app.outgo.data.db.entity.CategoryEntity
import app.outgo.data.db.entity.TradeEntity
import app.outgo.data.draft.DraftStore
import app.outgo.data.draft.DraftTrade
import app.outgo.data.repo.AccountRepository
import app.outgo.data.repo.BudgetRepository
import app.outgo.data.repo.resolvePrivacy
import app.outgo.data.repo.CategoryRepository
import app.outgo.data.repo.TradeRepository
import app.outgo.domain.AccountType
import app.outgo.domain.CategoryKind
import app.outgo.domain.SavingProgressHide
import app.outgo.domain.TradeDraft
import app.outgo.domain.TradeType
import app.outgo.ui.history.HistoryListItem
import app.outgo.ui.history.buildHistoryItems
import app.outgo.ui.trade.balanceMoves
import app.outgo.util.MonthKey
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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

/** One budget this month, before and after the drafts are saved. */
data class BudgetImpact(
    val budget: BudgetWithProgress,
    val spentAfter: Long,
    /** The limit after the drafts' top-ups ("To budget" transfers). */
    val limitAfter: Long,
    /** The drafts' spend run up by day of this month, day 1 to today; see [runningByDay]. */
    val addedByDay: List<Long>,
) {
    val spentBefore: Long get() = budget.spent
    val limitBefore: Long get() = budget.effectiveLimit
}

/** One savings account's monthly target, before and after the drafts are saved. */
data class SavingImpact(
    val row: AccountWithProgress,
    val savedAfter: Long,
    /** The drafts' savings run up by day of this month, day 1 to today; see [runningByDay]. */
    val addedByDay: List<Long>,
) {
    val savedBefore: Long get() = row.monthlyIncome

    /** Only rows with a target are kept. */
    val target: Long get() = row.monthlyTarget ?: 0L
}

/** What saving the drafts would do to this month's budgets and savings targets. */
data class DraftImpact(
    val budgets: List<BudgetImpact> = emptyList(),
    val savings: List<SavingImpact> = emptyList(),
    /** Drafts that can be saved but fall in another month: not counted above. */
    val otherMonthCount: Int = 0,
    val isLoading: Boolean = true,
)

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
    budgetRepository: BudgetRepository,
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

    /**
     * This month's budgets and savings targets the valid drafts change, before and after. Only
     * collected while the Impact tab is shown.
     */
    val impact: StateFlow<DraftImpact> = run {
        val month = MonthKey.current()
        combine(
            _state,
            budgetRepository.observeWithProgress(month),
            accountRepository.observeActiveWithProgress(month),
        ) { s, budgets, accounts ->
            if (s.isLoading) return@combine DraftImpact()
            val valid = s.drafts.filter { it.id !in s.problems }.map { it.trade }
            val (thisMonth, other) = valid.partition { MonthKey.of(it.occurredAt) == month }
            DraftImpact(
                budgets = budgetImpacts(thisMonth, budgets, s.categoriesById),
                savings = savingImpacts(thisMonth, accounts, s.accountsById),
                otherMonthCount = other.size,
                isLoading = false,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DraftImpact())

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

    /**
     * As BudgetDao counts them: a trade with the budget's category, or a subcategory that isn't a
     * parent budget exception, is spend (minus a budget exception); a transfer "To budget" such a
     * category tops the limit up. Only the budgets the drafts change.
     */
    private fun budgetImpacts(
        drafts: List<TradeDraft>,
        budgets: List<BudgetWithProgress>,
        categories: Map<Long, CategoryEntity>,
    ): List<BudgetImpact> = budgets.filter { it.enabled && it.categoryId != null }.mapNotNull { b ->
        fun countsIn(id: Long?) = categories[id]?.let { c -> c.id == b.categoryId || (c.parentId == b.categoryId && !c.parentBudgetExempt) } == true
        val spending = drafts.filter { countsIn(it.categoryId) && !it.budgetExempt }
        val spent = spending.sumOf { it.amount }
        val funded = drafts.filter { it.type == TradeType.TRANSFER && countsIn(it.toCategoryId) }.sumOf { it.amount }
        if (spent == 0L && funded == 0L) null else BudgetImpact(b, b.spent + spent, b.effectiveLimit + funded, runningByDay(spending))
    }

    /**
     * As AccountDao counts a month's savings: income into the account (or a subaccount that isn't
     * left out of it), or a transfer into it from outside. Savings accounts with a target this
     * month whose progress isn't always hidden, and only the ones the drafts change.
     */
    private fun savingImpacts(
        drafts: List<TradeDraft>,
        rows: List<AccountWithProgress>,
        accounts: Map<Long, AccountEntity>,
    ): List<SavingImpact> {
        val privacy = resolvePrivacy(rows.map { it.account })
        return rows.filter { it.account.accountType == AccountType.SAVINGS && it.monthlyTarget != null }.mapNotNull { row ->
            if (privacy[row.account.id]?.progressHide == SavingProgressHide.ALWAYS) return@mapNotNull null
            val a = row.account
            val group = accounts.values.filter { it.id == a.id || (it.parentId == a.id && !it.parentSavingExempt) }.mapTo(HashSet()) { it.id }
            val saving = drafts.filter { t ->
                !t.savingExempt && (
                    (t.type == TradeType.INCOME && t.accountId in group) ||
                        (t.type == TradeType.TRANSFER && t.toAccountId in group && t.accountId !in group)
                    )
            }
            val saved = saving.sumOf { it.amount }
            if (saved == 0L) null else SavingImpact(row, row.monthlyIncome + saved, runningByDay(saving))
        }
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

/**
 * [drafts] (all this month) summed per day and run up, day 1 to today: the same days as
 * TradeRepository's budget and savings lines, and a draft dated later this month counts on today
 * like a saved one does there.
 */
private fun runningByDay(drafts: List<TradeDraft>): List<Long> {
    val zone = ZoneId.systemDefault()
    val days = LocalDate.now(zone).dayOfMonth
    val daily = LongArray(days)
    drafts.forEach { t ->
        val day = Instant.ofEpochMilli(t.occurredAt).atZone(zone).dayOfMonth
        daily[day.coerceAtMost(days) - 1] += t.amount
    }
    var running = 0L
    return daily.map { running += it; running }
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
