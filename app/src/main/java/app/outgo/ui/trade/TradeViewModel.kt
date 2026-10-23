package app.outgo.ui.trade

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.outgo.data.db.entity.AccountEntity
import app.outgo.data.db.entity.CategoryEntity
import app.outgo.data.db.entity.TradeEntity
import app.outgo.data.db.dao.BudgetWithProgress
import app.outgo.data.draft.DraftStore
import app.outgo.data.repo.AccountRepository
import app.outgo.data.repo.BudgetRepository
import app.outgo.data.repo.CategoryRepository
import app.outgo.data.repo.SettingRepository
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
import kotlinx.coroutines.flow.combine
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
    /** Editing a transaction of the draft list (see [DraftStore]), not a saved one. */
    val editingDraftId: Long? = null,
    /** How many transactions the draft list holds: the Add screen's buttons follow it. */
    val draftCount: Int = 0,
    /** The edited trade as loaded: its balance moves are undone by the save, see [overdrawnBySave]. */
    val original: TradeEntity? = null,
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
    /** Fields the user tapped or set: not predicted again until the form is fresh. */
    val touched: Set<PredictField> = emptySet(),
    /** Fields that hold a prediction, not the user's input: drawn faded, saved as they are. */
    val predicted: Set<PredictField> = emptySet(),
    /** The predicted rest of the note being typed, shown faded after it. */
    val noteSuffix: String = "",
    /** The whole note once [noteSuffix] is taken. */
    val noteAccepted: String = "",
) {
    val isEditing: Boolean get() = editingTradeId != null || editingDraftId != null
    val isDraftEdit: Boolean get() = editingDraftId != null
    /** The Add screen with a draft list started: both its buttons add to the draft. */
    val drafting: Boolean get() = !isEditing && draftCount > 0
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
            // Going below 0 doesn't block the save: the form warns (see [overdrawnBySave]) and lets it through.
        }

    private val originalMoves: Map<Long, Long>
        get() = original?.let { balanceMoves(it.type, it.amount, it.accountId, it.toAccountId) }.orEmpty()

    /**
     * The account saving would take below 0 (in place of the edited trade, when editing); null when
     * none. Only a warning: a new account starts at 0, and its first expense must still be savable.
     */
    val overdrawnBySave: AccountEntity?
        get() {
            val from = selectedAccountId ?: return null
            if (amount <= 0) return null
            val moves = balanceMoves(type, amount, from, selectedToAccountId.takeIf { isTransfer })
            return overdrawn((moves.keys + originalMoves.keys).associateWith { (moves[it] ?: 0L) - (originalMoves[it] ?: 0L) })
        }

    /** The account deleting the edited trade takes below 0 (deleting an income, say); null when none. */
    val overdrawnByDelete: AccountEntity? get() = overdrawn(originalMoves.mapValues { -it.value })

    /**
     * The first account [change] (balance change by account id) takes below 0. One already below 0
     * may still gain: only a loss counts.
     */
    private fun overdrawn(change: Map<Long, Long>): AccountEntity? = change.entries.firstNotNullOfOrNull { (id, delta) ->
        accounts.find { it.id == id }?.takeIf { delta < 0 && it.balance + delta < 0 }
    }
    val isValid: Boolean get() = errors.isEmpty()

    /** What the user typed themselves (not a prediction): Back asks before dropping it. */
    val hasInput: Boolean
        get() = (amount > 0 && PredictField.AMOUNT !in predicted) || (note.isNotEmpty() && PredictField.NOTE !in predicted)

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

/** What a trade does to account balances, by account id: as the trade triggers apply it (see OutgoDatabase). */
internal fun balanceMoves(type: Int, amount: Long, accountId: Long, toAccountId: Long?): Map<Long, Long> = buildMap {
    put(accountId, if (TradeType.isCredit(type)) amount else -amount)
    if (type == TradeType.TRANSFER && toAccountId != null) put(toAccountId, (get(toAccountId) ?: 0L) + amount)
}

sealed interface TradeEvent {
    data class Saved(val tradeId: Long) : TradeEvent
    data class Drafted(val draftId: Long) : TradeEvent
    data class Pushed(val count: Int) : TradeEvent
    data object SavedAndClose : TradeEvent
    data object DeletedAndClose : TradeEvent
    data class Error(val message: String) : TradeEvent
}

class TradeViewModel(
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val tradeRepository: TradeRepository,
    private val budgetRepository: BudgetRepository,
    private val settingRepository: SettingRepository,
    private val draftStore: DraftStore,
    editingTradeId: Long? = null,
    editingDraftId: Long? = null,
) : ViewModel() {

    /** The form as the user filled it; [state] is it with the predictions filled in. */
    private var raw = TradeUiState(editingTradeId = editingTradeId, editingDraftId = editingDraftId)
    private val _state = MutableStateFlow(raw)
    val state: StateFlow<TradeUiState> = _state.asStateFlow()

    /** Past trades the predictions learn from, newest first, and the categories they can name. */
    private var history: List<TradeEntity> = emptyList()
    private var categories: Map<Long, CategoryEntity> = emptyMap()
    private var predictEnabled = false
    private var minConfidence = SettingRepository.DEFAULT_PREDICT_MIN_CONFIDENCE

    /** Every change goes through here, so [state] follows [raw] in the same call (a text field needs that). */
    private inline fun edit(change: (TradeUiState) -> TradeUiState) {
        raw = change(raw)
        _state.value = withPrediction(raw)
    }

    private val _events = MutableSharedFlow<TradeEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<TradeEvent> = _events

    init {
        viewModelScope.launch {
            // A new trade starts with no account: the user picks it, or a prediction fills it.
            accountRepository.observeActive().collect { accounts -> edit { it.copy(accounts = accounts) } }
        }

        if (editingTradeId == null && editingDraftId == null) {
            viewModelScope.launch {
                draftStore.drafts.collect { list -> edit { it.copy(draftCount = list.size) } }
            }
            viewModelScope.launch {
                draftStore.pushed.collect { count ->
                    // The pushed trades count in the quick picks and predictions now.
                    loadPickers()
                    loadPredictionData()
                    _events.emit(TradeEvent.Pushed(count))
                }
            }
        }

        viewModelScope.launch {
            if (editingTradeId != null) loadForEdit(editingTradeId)
            if (editingDraftId != null) loadDraftForEdit(editingDraftId)
            loadPickers()
            edit { it.copy(isLoading = false) }
            if (editingTradeId == null && editingDraftId == null) {
                loadPredictionData()
                launch {
                    combine(settingRepository.observePredictEnabled(), settingRepository.observePredictMinConfidence(), ::Pair)
                        .collect { (on, min) ->
                            predictEnabled = on
                            minConfidence = min
                            edit { it }
                        }
                }
            }
            // Budgets only feed the transfer form's "From budget": started after the pickers, so on
            // a cold start their queries and carry walk don't compete with the first screen's data.
            budgetRepository.observeWithProgress(MonthKey.current()).collect { budgets ->
                edit { it.copy(budgets = budgets.filter { b -> b.categoryId != null }) }
            }
        }
    }

    private suspend fun loadForEdit(tradeId: Long) {
        val trade = tradeRepository.findById(tradeId) ?: return
        // An automatic transfer shows the budget it moves the unspent amount of (read-only).
        val category = (trade.categoryId ?: trade.offsetCategoryId)?.let { categoryRepository.findById(it) }
        val parent = category?.parentId?.let { categoryRepository.findById(it) }
        edit {
            it.copy(
                original = trade,
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

    /** A draft has no "original": nothing of it is in the balances yet. */
    private suspend fun loadDraftForEdit(draftId: Long) {
        val trade = draftStore.find(draftId)?.trade ?: return
        val category = trade.categoryId?.let { categoryRepository.findById(it) }
        val parent = category?.parentId?.let { categoryRepository.findById(it) }
        edit {
            it.copy(
                type = trade.type,
                amount = trade.amount,
                selectedCategory = category,
                selectedParentCategory = parent,
                selectedAccountId = trade.accountId,
                selectedToAccountId = trade.toAccountId,
                occurredAt = trade.occurredAt,
                note = trade.note.orEmpty(),
                budgetExempt = trade.budgetExempt,
                savingExempt = trade.savingExempt,
                toBudget = trade.toCategoryId?.let { id -> categoryRepository.findById(id) },
            )
        }
    }

    private suspend fun loadPredictionData() {
        if (raw.isEditing) return
        history = tradeRepository.recentForPrediction()
        categories = categoryRepository.allActive().associateBy { it.id }
        edit { it }
    }

    /**
     * [s] with the predictions in the fields the user has neither filled nor touched. Only the
     * new-trade form predicts; a prediction naming an account, category or budget no longer
     * offered is dropped.
     */
    private fun withPrediction(form: TradeUiState): TradeUiState {
        if (form.isEditing) return form
        val s = withAccountDefault(form)
        if (!predictEnabled || history.isEmpty()) return withLastAccount(s)
        val transfer = s.isTransfer
        val exempt = if (s.type == CategoryKind.EXPENSE) s.budgetExempt else s.savingExempt
        val known = TradeFields(
            categoryId = s.selectedCategory?.id,
            accountId = s.selectedAccountId,
            toAccountId = s.selectedToAccountId.takeIf { transfer },
            toCategoryId = s.toBudget?.id.takeIf { transfer },
            exempt = exempt.takeIf { PredictField.EXEMPT in s.touched },
        )
        val p = TradePredictor.predict(history, s.type, s.amount, known, s.note, s.occurredAt, minConfidence / 100.0)
        val f = p.fields
        fun open(field: PredictField, current: Any?) = current == null && field !in s.touched
        fun account(id: Long?) = id?.takeIf { s.accounts.any { a -> a.id == id && !a.isParent } }
        fun budget(id: Long?) = id?.takeIf { s.budgets.any { b -> b.enabled && b.categoryId == id } }?.let(categories::get)
        val category = (if (transfer) budget(f.categoryId) else f.categoryId?.let(categories::get))
            ?.takeIf { open(PredictField.CATEGORY, s.selectedCategory) }
        val accountId = account(f.accountId)?.takeIf { open(PredictField.ACCOUNT, s.selectedAccountId) }
        val toAccountId = account(f.toAccountId)?.takeIf { transfer && open(PredictField.TO_ACCOUNT, s.selectedToAccountId) }
        val toBudget = budget(f.toCategoryId)?.takeIf { transfer && open(PredictField.TO_BUDGET, s.toBudget) }
        val predictExempt = f.exempt == true && PredictField.EXEMPT !in s.touched
        val note = f.note?.takeIf { s.note.isEmpty() && PredictField.NOTE !in s.touched }
        val amount = f.amount?.takeIf { s.amount <= 0 }
        return s.copy(
            amount = amount ?: s.amount,
            selectedCategory = category ?: s.selectedCategory,
            selectedParentCategory = if (category != null) category.parentId?.let(categories::get) else s.selectedParentCategory,
            selectedAccountId = accountId ?: s.selectedAccountId,
            selectedToAccountId = toAccountId ?: s.selectedToAccountId,
            toBudget = toBudget ?: s.toBudget,
            budgetExempt = s.budgetExempt || (predictExempt && s.type == CategoryKind.EXPENSE),
            savingExempt = s.savingExempt || (predictExempt && s.type != CategoryKind.EXPENSE),
            note = note ?: s.note,
            predicted = buildSet {
                if (amount != null) add(PredictField.AMOUNT)
                if (category != null) add(PredictField.CATEGORY)
                if (accountId != null) add(PredictField.ACCOUNT)
                if (toAccountId != null) add(PredictField.TO_ACCOUNT)
                if (toBudget != null) add(PredictField.TO_BUDGET)
                if (predictExempt) add(PredictField.EXEMPT)
                if (note != null) add(PredictField.NOTE)
            },
            noteSuffix = p.noteSuffix,
            noteAccepted = p.noteAccepted,
        ).let(::withLastAccount)
    }

    /** The accounts a trade can be saved to: subaccounts, not their parents. */
    private fun TradeUiState.pickable() = accounts.filter { !it.isParent }

    /** With only one account to pick from, it is picked: there is no choice to make. */
    private fun withAccountDefault(s: TradeUiState): TradeUiState {
        if (s.selectedAccountId != null) return s
        val only = s.pickable().singleOrNull() ?: return s
        return s.copy(selectedAccountId = only.id)
    }

    /**
     * Still no account: the one the last trade of this type used, shown faded like a prediction
     * (and saved as shown, see [PredictField.ACCOUNT]). The fallback when nothing better is
     * predicted, and it works with predictions turned off too.
     */
    private fun withLastAccount(s: TradeUiState): TradeUiState {
        if (s.selectedAccountId != null || PredictField.ACCOUNT in s.touched) return s
        val last = history.firstOrNull { it.type == s.type }?.accountId
            ?.takeIf { id -> s.pickable().any { it.id == id } } ?: return s
        return s.copy(selectedAccountId = last, predicted = s.predicted + PredictField.ACCOUNT)
    }

    /** The user tapped [field]: its prediction goes, and it stays empty until they fill it. */
    fun dismissPrediction(field: PredictField) {
        if (field in raw.touched) return
        edit { it.copy(touched = it.touched + field) }
    }

    /** Takes the faded rest of the note as typed. */
    fun acceptNoteSuffix() {
        val s = _state.value
        if (s.noteSuffix.isNotEmpty()) onNoteChange(s.noteAccepted)
    }

    private suspend fun loadPicker(type: Int) {
        val picker = categoryRepository.getPicker(type)
        edit { it.copy(pickers = it.pickers + (type to picker)) }
    }

    /** Re-reads the quick picks, e.g. when the tab is shown again after a category was edited. */
    fun refreshPickers() {
        viewModelScope.launch {
            loadPickers()
            loadPredictionData()
        }
    }

    private suspend fun loadPickers() {
        loadPicker(CategoryKind.EXPENSE)
        loadPicker(CategoryKind.INCOME)
    }

    fun onTypeChange(type: Int) {
        if (type == raw.type) return
        // Another type is a fresh form: its errors wait for its own save, its fields can be predicted again.
        edit { it.copy(type = type, selectedCategory = null, selectedParentCategory = null, toBudget = null, showErrors = false, touched = emptySet()) }
        if (type != TradeType.TRANSFER) {
            viewModelScope.launch { loadPicker(type) }
        }
    }

    /** The form was left (its tab hidden): the errors marked by a save tapped earlier go. */
    fun clearErrors() {
        if (raw.showErrors) edit { it.copy(showErrors = false) }
    }

    /** Drops what was typed (Back on the form, confirmed): the amount and note clear, like after a save. */
    fun discardInput() {
        edit { it.copy(amount = 0, note = "", showErrors = false) }
    }

    fun onAmountChange(amount: Long) {
        // 999,999,999,999 (amounts are in hundredths).
        edit { it.copy(amount = amount.coerceAtMost(99_999_999_999_900L)) }
    }

    fun onCategorySelected(category: CategoryEntity) {
        edit { it.copy(selectedCategory = category, selectedParentCategory = null, touched = it.touched + PredictField.CATEGORY) }
        viewModelScope.launch {
            val parent = category.parentId?.let { categoryRepository.findById(it) }
            edit { it.copy(selectedParentCategory = parent) }
        }
    }

    /** A transfer's "From budget": the budget's category id, or null for none. */
    fun onBudgetSelected(categoryId: Long?) {
        if (categoryId == null) {
            edit { it.copy(selectedCategory = null, selectedParentCategory = null, touched = it.touched + PredictField.CATEGORY) }
            return
        }
        viewModelScope.launch {
            categoryRepository.findById(categoryId)?.let { category -> edit { it.copy(selectedCategory = category) } }
        }
    }

    /** A transfer's "To budget": the budget's category id, or null for none. */
    fun onToBudgetSelected(categoryId: Long?) {
        if (categoryId == null) {
            edit { it.copy(toBudget = null, touched = it.touched + PredictField.TO_BUDGET) }
            return
        }
        viewModelScope.launch {
            categoryRepository.findById(categoryId)?.let { category -> edit { it.copy(toBudget = category) } }
        }
    }

    fun onAccountSelected(accountId: Long) {
        edit { it.copy(selectedAccountId = accountId) }
    }

    fun onToAccountSelected(accountId: Long) {
        edit { it.copy(selectedToAccountId = accountId) }
    }

    fun onDateChange(epochMillisAtMidnight: Long) {
        edit { s ->
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
        edit { s -> if (s.isEditing || s.occurredAtPicked) s else s.copy(occurredAt = System.currentTimeMillis()) }
    }

    fun onTimeChange(hour: Int, minute: Int) {
        edit { s ->
            val z = java.time.Instant.ofEpochMilli(s.occurredAt).atZone(java.time.ZoneId.systemDefault())
                .withHour(hour).withMinute(minute).withSecond(0).withNano(0)
            s.copy(occurredAt = z.toInstant().toEpochMilli(), occurredAtPicked = true)
        }
    }

    fun onBudgetExemptChange(on: Boolean) {
        edit { it.copy(budgetExempt = on, touched = it.touched + PredictField.EXEMPT) }
    }

    fun onSavingExemptChange(on: Boolean) {
        edit { it.copy(savingExempt = on, touched = it.touched + PredictField.EXEMPT) }
    }

    fun onNoteChange(note: String) {
        edit { it.copy(note = note.take(100)) }
    }

    /** The form as a trade to save, or null (with its errors marked) when it can't be saved. */
    private fun validDraft(s: TradeUiState): TradeDraft? {
        if (s.isSaving || s.locked) return null
        if (!s.isValid) {
            edit { it.copy(showErrors = true, invalidSaveCount = it.invalidSaveCount + 1) }
            return null
        }
        return TradeDraft(
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
    }

    /** The Add screen's "Add to draft": the form goes to the draft list, not the database. */
    fun addToDraft() {
        val s = _state.value
        if (s.isEditing) return
        val draft = validDraft(s) ?: return
        val id = draftStore.add(draft)
        lastDrafted = id to s
        resetForm()
        _events.tryEmit(TradeEvent.Drafted(id))
    }

    fun save() {
        val s = _state.value
        // A started draft list takes every new trade: Save never writes to the database by accident.
        if (s.drafting) return addToDraft()
        val draft = validDraft(s) ?: return
        if (s.isDraftEdit) {
            draftStore.update(s.editingDraftId!!, draft)
            _events.tryEmit(TradeEvent.SavedAndClose)
            return
        }
        viewModelScope.launch {
            edit { it.copy(isSaving = true) }
            try {
                if (s.isEditing) {
                    tradeRepository.update(s.editingTradeId!!, draft)
                    _events.emit(TradeEvent.SavedAndClose)
                } else {
                    val id = tradeRepository.insert(draft)
                    lastSaved = id to s
                    resetForm()
                    loadPredictionData()
                    _events.emit(TradeEvent.Saved(id))
                }
            } catch (t: Throwable) {
                _events.emit(TradeEvent.Error(t.message ?: "Error"))
            } finally {
                edit { it.copy(isSaving = false) }
            }
        }
    }

    fun deleteEditingTrade() {
        _state.value.editingDraftId?.let { draftId ->
            draftStore.remove(draftId)
            _events.tryEmit(TradeEvent.DeletedAndClose)
            return
        }
        val id = _state.value.editingTradeId ?: return
        if (_state.value.locked) return
        viewModelScope.launch {
            tradeRepository.delete(id)
            _events.emit(TradeEvent.DeletedAndClose)
        }
    }

    /** The trade saved last and the form as it was when saved, for [undo]. */
    private var lastSaved: Pair<Long, TradeUiState>? = null

    /** The draft added last and the form as it was then, for [undoDraft]. */
    private var lastDrafted: Pair<Long, TradeUiState>? = null

    /** Takes a draft just added out of the list and puts it back into the form. */
    fun undoDraft(draftId: Long) {
        val saved = lastDrafted?.takeIf { it.first == draftId }?.second
        lastDrafted = null
        draftStore.remove(draftId)
        if (saved != null) restoreForm(saved)
    }

    /** Deletes a trade just saved and puts what it was saved with back into the form, to fix and save again. */
    fun undo(tradeId: Long) {
        val saved = lastSaved?.takeIf { it.first == tradeId }?.second
        lastSaved = null
        if (saved != null) restoreForm(saved)
        viewModelScope.launch {
            tradeRepository.delete(tradeId)
            // Its category's use count and last use went back with it.
            loadPickers()
            loadPredictionData()
        }
    }

    private fun restoreForm(saved: TradeUiState) {
        edit {
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
                // What was saved, predictions included, is the user's input now.
                touched = saved.touched + saved.predicted,
            )
        }
    }

    /**
     * After a save: only the amount and note clear. The type, category, accounts and a picked
     * date/time stay for the next entry (e.g. several of yesterday's); the view model lives as long
     * as the process, so a fresh start of the app begins from a blank form.
     */
    private fun resetForm() {
        edit { it.copy(amount = 0, note = "", showErrors = false, touched = it.touched - PredictField.NOTE) }
        viewModelScope.launch { loadPickers() }
    }
}
