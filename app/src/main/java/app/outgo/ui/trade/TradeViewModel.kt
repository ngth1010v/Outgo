package app.outgo.ui.trade

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.outgo.data.db.entity.AccountEntity
import app.outgo.data.db.entity.CategoryEntity
import app.outgo.data.db.dao.BudgetWithProgress
import app.outgo.data.repo.AccountRepository
import app.outgo.data.repo.BudgetRepository
import app.outgo.data.repo.CategoryRepository
import app.outgo.data.repo.TradeRepository
import app.outgo.domain.AccountType
import app.outgo.domain.CategoryKind
import app.outgo.domain.CategoryPicker
import app.outgo.domain.TradeDraft
import app.outgo.domain.TradeType
import app.outgo.util.MonthKey
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TradeUiState(
    val type: Int = CategoryKind.EXPENSE,
    val amount: Long = 0,
    val selectedCategory: CategoryEntity? = null,
    val selectedParentCategory: CategoryEntity? = null,
    val selectedAccountId: Long? = null,
    val selectedToAccountId: Long? = null,
    val occurredAt: Long = System.currentTimeMillis(),
    /** The user picked the date or time. Until then a new trade follows the clock: see [TradeViewModel.refreshTime]. */
    val occurredAtPicked: Boolean = false,
    val note: String = "",
    /** Per [CategoryKind], both loaded so a swipe can draw the other type's picker too. */
    val pickers: Map<Int, CategoryPicker> = emptyMap(),
    /** Active parents and subaccounts; a trade picks a subaccount. */
    val accounts: List<AccountEntity> = emptyList(),
    /** Every LIMIT budget with a category; [budgetOptions] narrows it to what the transfer form offers. */
    val budgets: List<BudgetWithProgress> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val editingTradeId: Long? = null,
    /** The edited trade is an automatic budget-offset transfer: it follows its budget, so it is read-only. */
    val locked: Boolean = false,
    /** As picked; saved only while its chip shows, see [showBudgetExempt] / [showSavingExempt]. */
    val budgetExempt: Boolean = false,
    val savingExempt: Boolean = false,
    /** A transfer's "To budget" category, as picked; saved only while [showToBudget]. */
    val toBudget: CategoryEntity? = null,
    /** Set by a save tapped with [errors]: from then on the form marks them, live, until it resets. */
    val showErrors: Boolean = false,
    /** Counts saves tapped with [errors]; each new value shakes the save button once. */
    val invalidSaveCount: Int = 0,
) {
    val isEditing: Boolean get() = editingTradeId != null
    val picker: CategoryPicker get() = pickers[type] ?: CategoryPicker(emptyList(), emptyList())
    val isTransfer: Boolean get() = type == TradeType.TRANSFER
    /** What keeps the trade from being saved. */
    val errors: Set<TradeError>
        get() = buildSet {
            if (amount <= 0) add(TradeError.AMOUNT)
            if (!isTransfer && selectedCategory == null) add(TradeError.CATEGORY)
            if (selectedAccountId == null) add(TradeError.ACCOUNT)
            if (isTransfer && selectedToAccountId == null) add(TradeError.TO_ACCOUNT)
            // Into the same account it only moves budget, so it needs a "From" or "To" budget.
            if (isSelfTransfer && selectedCategory == null && effectiveToBudget == null) add(TradeError.SELF_TRANSFER_BUDGET)
        }
    val isValid: Boolean get() = errors.isEmpty()

    /** [errors] as the form marks them: none until a save was tapped with some. */
    val shownErrors: Set<TradeError> get() = if (showErrors) errors else emptySet()
    val selectedAccount: AccountEntity? get() = accounts.find { it.id == selectedAccountId }
    val selectedToAccount: AccountEntity? get() = accounts.find { it.id == selectedToAccountId }

    /** A transfer into its own "From" account: no balance moves, only budget (see [isValid]). */
    val isSelfTransfer: Boolean get() = isTransfer && selectedToAccountId != null && selectedToAccountId == selectedAccountId

    /**
     * A transfer's "From budget" choices: the enabled budgets, plus the one an edited transfer
     * already draws from even if it was paused since. On a transfer, [selectedCategory] is that
     * budget's category: the transfer is saved with it and counts as that category's spending.
     */
    val budgetOptions: List<BudgetWithProgress>
        get() = budgets.filter { it.enabled || it.categoryId == selectedCategory?.id }

    /**
     * The category whose budgets an expense counts in. A budget exception is for expenses only: a
     * transfer's "From budget" always counts, so a transfer is always saved with it off.
     */
    val budgetCategory: CategoryEntity? get() = selectedCategory.takeIf { type == CategoryKind.EXPENSE }

    /** The account whose savings target the trade counts toward: an income's, or a transfer's "To". */
    val savingAccount: AccountEntity? get() = when (type) {
        CategoryKind.INCOME -> selectedAccount
        TradeType.TRANSFER -> selectedToAccount
        else -> null
    }

    /** A parent budget exception subcategory: its expenses are always budget exceptions. */
    val budgetExemptLocked: Boolean get() = budgetCategory?.isExempt == true

    /**
     * Always a saving exception: what comes into a parent saving exception subaccount, and a savings
     * account's transfer to itself (which only moves budget, and needs the exception for "To budget").
     */
    val savingExemptLocked: Boolean
        get() = savingAccount?.let { it.isExempt || (isSelfTransfer && it.accountType == AccountType.SAVINGS) } == true

    /** The budget exception switch can be on when the category or its parent has a budget (or it already is on). */
    val showBudgetExempt: Boolean
        get() = budgetCategory?.let { c -> budgetExempt || budgetExemptLocked || budgets.any { it.categoryId == c.id || it.categoryId == c.parentId } } ?: false

    /** The saving exception switch can be on when the account is a savings account (or it already is on). */
    val showSavingExempt: Boolean
        get() = savingAccount?.let { savingExempt || savingExemptLocked || it.accountType == AccountType.SAVINGS } ?: false

    /**
     * A transfer's "To budget" shows when what comes in doesn't count as savings: the "To" account
     * isn't a savings account, or the transfer is a saving exception.
     */
    val showToBudget: Boolean
        get() = isTransfer && selectedToAccount?.let { it.accountType != AccountType.SAVINGS || effectiveSavingExempt } == true

    /** "To budget" choices: like [budgetOptions], minus the "From budget" (a budget can't top itself up). */
    val toBudgetOptions: List<BudgetWithProgress>
        get() = budgets.filter { (it.enabled || it.categoryId == toBudget?.id) && it.categoryId != selectedCategory?.id }

    /** The "To budget" the transfer is saved with. */
    val effectiveToBudget: CategoryEntity? get() = toBudget?.takeIf { showToBudget && it.id != selectedCategory?.id }

    /** What the switches show and the trade is saved with. */
    val effectiveBudgetExempt: Boolean get() = showBudgetExempt && (budgetExempt || budgetExemptLocked)
    val effectiveSavingExempt: Boolean get() = showSavingExempt && (savingExempt || savingExemptLocked)
}

/** A reason the trade can't be saved, marked on its field. */
enum class TradeError { AMOUNT, CATEGORY, ACCOUNT, TO_ACCOUNT, SELF_TRANSFER_BUDGET }

sealed interface TradeEvent {
    data class Saved(val tradeId: Long) : TradeEvent
    data object SavedAndClose : TradeEvent
    data object DeletedAndClose : TradeEvent
    data class Error(val message: String) : TradeEvent
}

class TradeViewModel(
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val tradeRepository: TradeRepository,
    private val budgetRepository: BudgetRepository,
    editingTradeId: Long? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(TradeUiState(editingTradeId = editingTradeId))
    val state: StateFlow<TradeUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<TradeEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<TradeEvent> = _events

    init {
        viewModelScope.launch {
            accountRepository.observeActive().collect { accounts ->
                _state.update { s ->
                    // While loading, the default (last used) account is still on its way: the first
                    // account would beat it. After that, this covers an account created from none.
                    val fallbackAccount = if (s.isLoading) null else accounts.firstOrNull { !it.isParent }?.id
                    s.copy(accounts = accounts, selectedAccountId = s.selectedAccountId ?: fallbackAccount)
                }
            }
        }

        viewModelScope.launch {
            if (editingTradeId != null) {
                loadForEdit(editingTradeId)
            } else {
                val defaultAccount = accountRepository.defaultAccountId()
                _state.update { it.copy(selectedAccountId = it.selectedAccountId ?: defaultAccount) }
            }
            loadPickers()
            _state.update { it.copy(isLoading = false) }
            // Budgets only feed the transfer form's "From budget": started after the pickers, so on
            // a cold start their queries and carry walk don't compete with the first screen's data.
            budgetRepository.observeWithProgress(MonthKey.current()).collect { budgets ->
                _state.update { it.copy(budgets = budgets.filter { b -> b.categoryId != null }) }
            }
        }
    }

    private suspend fun loadForEdit(tradeId: Long) {
        val trade = tradeRepository.findById(tradeId) ?: return
        // An automatic transfer shows the budget it moves the unspent amount of (read-only).
        val category = (trade.categoryId ?: trade.offsetCategoryId)?.let { categoryRepository.findById(it) }
        val parent = category?.parentId?.let { categoryRepository.findById(it) }
        _state.update {
            it.copy(
                type = trade.type,
                amount = trade.amount,
                selectedCategory = category,
                selectedParentCategory = parent,
                selectedAccountId = trade.accountId,
                selectedToAccountId = trade.toAccountId,
                occurredAt = trade.occurredAt,
                note = trade.note.orEmpty(),
                locked = trade.offsetMonth != null,
                budgetExempt = trade.budgetExempt,
                savingExempt = trade.savingExempt,
                toBudget = trade.toCategoryId?.let { id -> categoryRepository.findById(id) },
            )
        }
    }

    private suspend fun loadPicker(type: Int) {
        val picker = categoryRepository.getPicker(type)
        _state.update { it.copy(pickers = it.pickers + (type to picker)) }
    }

    /** Re-reads the quick picks, e.g. when the tab is shown again after a category was edited. */
    fun refreshPickers() {
        viewModelScope.launch { loadPickers() }
    }

    private suspend fun loadPickers() {
        loadPicker(CategoryKind.EXPENSE)
        loadPicker(CategoryKind.INCOME)
    }

    fun onTypeChange(type: Int) {
        if (type == _state.value.type) return
        // Another type is a fresh form: its errors wait for its own save.
        _state.update { it.copy(type = type, selectedCategory = null, selectedParentCategory = null, toBudget = null, showErrors = false) }
        if (type != TradeType.TRANSFER) {
            viewModelScope.launch { loadPicker(type) }
        }
    }

    fun onAmountChange(amount: Long) {
        // 999,999,999,999 (amounts are in hundredths).
        _state.update { it.copy(amount = amount.coerceAtMost(99_999_999_999_900L)) }
    }

    fun onCategorySelected(category: CategoryEntity) {
        _state.update { it.copy(selectedCategory = category, selectedParentCategory = null) }
        viewModelScope.launch {
            val parent = category.parentId?.let { categoryRepository.findById(it) }
            _state.update { it.copy(selectedParentCategory = parent) }
        }
    }

    /** A transfer's "From budget": the budget's category id, or null for none. */
    fun onBudgetSelected(categoryId: Long?) {
        if (categoryId == null) {
            _state.update { it.copy(selectedCategory = null, selectedParentCategory = null) }
            return
        }
        viewModelScope.launch {
            categoryRepository.findById(categoryId)?.let { category -> _state.update { it.copy(selectedCategory = category) } }
        }
    }

    /** A transfer's "To budget": the budget's category id, or null for none. */
    fun onToBudgetSelected(categoryId: Long?) {
        if (categoryId == null) {
            _state.update { it.copy(toBudget = null) }
            return
        }
        viewModelScope.launch {
            categoryRepository.findById(categoryId)?.let { category -> _state.update { it.copy(toBudget = category) } }
        }
    }

    fun onAccountSelected(accountId: Long) {
        _state.update { it.copy(selectedAccountId = accountId) }
    }

    fun onToAccountSelected(accountId: Long) {
        _state.update { it.copy(selectedToAccountId = accountId) }
    }

    fun onDateChange(epochMillisAtMidnight: Long) {
        _state.update { s ->
            val old = java.time.Instant.ofEpochMilli(s.occurredAt).atZone(java.time.ZoneId.systemDefault())
            val newDate = java.time.Instant.ofEpochMilli(epochMillisAtMidnight).atZone(java.time.ZoneId.systemDefault())
            val combined = newDate.withHour(old.hour).withMinute(old.minute).withSecond(0).withNano(0)
            s.copy(occurredAt = combined.toInstant().toEpochMilli(), occurredAtPicked = true)
        }
    }

    /**
     * Moves a new trade's date and time to now, unless the user picked them. Called when the form is
     * shown again: the tab stays composed, so the time it was first shown at goes stale.
     */
    fun refreshTime() {
        _state.update { s -> if (s.isEditing || s.occurredAtPicked) s else s.copy(occurredAt = System.currentTimeMillis()) }
    }

    fun onTimeChange(hour: Int, minute: Int) {
        _state.update { s ->
            val z = java.time.Instant.ofEpochMilli(s.occurredAt).atZone(java.time.ZoneId.systemDefault())
                .withHour(hour).withMinute(minute).withSecond(0).withNano(0)
            s.copy(occurredAt = z.toInstant().toEpochMilli(), occurredAtPicked = true)
        }
    }

    fun onBudgetExemptChange(on: Boolean) {
        _state.update { it.copy(budgetExempt = on) }
    }

    fun onSavingExemptChange(on: Boolean) {
        _state.update { it.copy(savingExempt = on) }
    }

    fun onNoteChange(note: String) {
        _state.update { it.copy(note = note.take(100)) }
    }

    fun save() {
        val s = _state.value
        if (s.isSaving || s.locked) return
        if (!s.isValid) {
            _state.update { it.copy(showErrors = true, invalidSaveCount = it.invalidSaveCount + 1) }
            return
        }
        val draft = TradeDraft(
            type = s.type,
            amount = s.amount,
            accountId = s.selectedAccountId!!,
            // A transfer's category is its optional "From budget".
            categoryId = if (s.isTransfer) s.selectedCategory?.id else s.selectedCategory!!.id,
            toAccountId = if (s.isTransfer) s.selectedToAccountId else null,
            // Not picked: the moment of saving, not of opening the form.
            occurredAt = if (s.isEditing || s.occurredAtPicked) s.occurredAt else System.currentTimeMillis(),
            note = s.note,
            budgetExempt = s.effectiveBudgetExempt,
            savingExempt = s.effectiveSavingExempt,
            toCategoryId = s.effectiveToBudget?.id,
        )
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            try {
                if (s.isEditing) {
                    tradeRepository.update(s.editingTradeId!!, draft)
                    _events.emit(TradeEvent.SavedAndClose)
                } else {
                    val id = tradeRepository.insert(draft)
                    lastSaved = id to s
                    resetForm()
                    _events.emit(TradeEvent.Saved(id))
                }
            } catch (t: Throwable) {
                _events.emit(TradeEvent.Error(t.message ?: "Error"))
            } finally {
                _state.update { it.copy(isSaving = false) }
            }
        }
    }

    fun deleteEditingTrade() {
        val id = _state.value.editingTradeId ?: return
        if (_state.value.locked) return
        viewModelScope.launch {
            tradeRepository.delete(id)
            _events.emit(TradeEvent.DeletedAndClose)
        }
    }

    /** The trade saved last and the form as it was when saved, for [undo]. */
    private var lastSaved: Pair<Long, TradeUiState>? = null

    /** Deletes a trade just saved and puts what it was saved with back into the form, to fix and save again. */
    fun undo(tradeId: Long) {
        val saved = lastSaved?.takeIf { it.first == tradeId }?.second
        lastSaved = null
        if (saved != null) {
            _state.update {
                it.copy(
                    type = saved.type,
                    amount = saved.amount,
                    selectedCategory = saved.selectedCategory,
                    selectedParentCategory = saved.selectedParentCategory,
                    selectedAccountId = saved.selectedAccountId,
                    selectedToAccountId = saved.selectedToAccountId,
                    occurredAt = saved.occurredAt,
                    occurredAtPicked = saved.occurredAtPicked,
                    note = saved.note,
                    budgetExempt = saved.budgetExempt,
                    savingExempt = saved.savingExempt,
                    toBudget = saved.toBudget,
                    showErrors = false,
                )
            }
        }
        viewModelScope.launch {
            tradeRepository.delete(tradeId)
            // Its category's use count and last use went back with it.
            loadPickers()
        }
    }

    /**
     * After a save: only the amount and note clear. The type, category, accounts and a picked
     * date/time stay for the next entry (e.g. several of yesterday's); the view model lives as long
     * as the process, so a fresh start of the app begins from a blank form.
     */
    private fun resetForm() {
        _state.update { it.copy(amount = 0, note = "", showErrors = false) }
        viewModelScope.launch { loadPickers() }
    }
}
