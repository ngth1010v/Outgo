package app.outgo.ui.trade

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.outgo.data.db.entity.AccountEntity
import app.outgo.data.db.entity.CategoryEntity
import app.outgo.data.repo.AccountRepository
import app.outgo.data.repo.CategoryRepository
import app.outgo.data.repo.TradeRepository
import app.outgo.domain.CategoryKind
import app.outgo.domain.CategoryPicker
import app.outgo.domain.TradeDraft
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
    val occurredAt: Long = System.currentTimeMillis(),
    val note: String = "",
    val picker: CategoryPicker = CategoryPicker(emptyList(), emptyList()),
    val accounts: List<AccountEntity> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val editingTradeId: Long? = null,
) {
    val isEditing: Boolean get() = editingTradeId != null
    val isValid: Boolean get() = amount > 0 && selectedCategory != null && selectedAccountId != null
    val selectedAccount: AccountEntity? get() = accounts.find { it.id == selectedAccountId }
}

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
                    val fallbackAccount = s.selectedAccountId ?: accounts.firstOrNull()?.id
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
            loadPicker(_state.value.type)
            _state.update { it.copy(isLoading = false) }
        }
    }

    private suspend fun loadForEdit(tradeId: Long) {
        val trade = tradeRepository.findById(tradeId) ?: return
        val category = trade.categoryId?.let { categoryRepository.findById(it) }
        val parent = category?.parentId?.let { categoryRepository.findById(it) }
        _state.update {
            it.copy(
                type = trade.type,
                amount = trade.amount,
                selectedCategory = category,
                selectedParentCategory = parent,
                selectedAccountId = trade.accountId,
                occurredAt = trade.occurredAt,
                note = trade.note.orEmpty(),
            )
        }
    }

    private suspend fun loadPicker(type: Int) {
        val picker = categoryRepository.getPicker(type)
        _state.update { it.copy(picker = picker) }
    }

    fun onTypeChange(type: Int) {
        if (type == _state.value.type) return
        _state.update { it.copy(type = type, selectedCategory = null, selectedParentCategory = null) }
        viewModelScope.launch { loadPicker(type) }
    }

    fun onAmountChange(amount: Long) {
        _state.update { it.copy(amount = amount.coerceAtMost(999_999_999_999L)) }
    }

    fun onCategorySelected(category: CategoryEntity) {
        _state.update { it.copy(selectedCategory = category, selectedParentCategory = null) }
        viewModelScope.launch {
            val parent = category.parentId?.let { categoryRepository.findById(it) }
            _state.update { it.copy(selectedParentCategory = parent) }
        }
    }

    fun onAccountSelected(accountId: Long) {
        _state.update { it.copy(selectedAccountId = accountId) }
    }

    fun onDateChange(epochMillisAtMidnight: Long) {
        _state.update { s ->
            val old = java.time.Instant.ofEpochMilli(s.occurredAt).atZone(java.time.ZoneId.systemDefault())
            val newDate = java.time.Instant.ofEpochMilli(epochMillisAtMidnight).atZone(java.time.ZoneId.systemDefault())
            val combined = newDate.withHour(old.hour).withMinute(old.minute).withSecond(0).withNano(0)
            s.copy(occurredAt = combined.toInstant().toEpochMilli())
        }
    }

    fun onTimeChange(hour: Int, minute: Int) {
        _state.update { s ->
            val z = java.time.Instant.ofEpochMilli(s.occurredAt).atZone(java.time.ZoneId.systemDefault())
                .withHour(hour).withMinute(minute).withSecond(0).withNano(0)
            s.copy(occurredAt = z.toInstant().toEpochMilli())
        }
    }

    fun onNoteChange(note: String) {
        _state.update { it.copy(note = note.take(100)) }
    }

    fun save() {
        val s = _state.value
        if (!s.isValid || s.isSaving) return
        val draft = TradeDraft(
            type = s.type,
            amount = s.amount,
            accountId = s.selectedAccountId!!,
            categoryId = s.selectedCategory!!.id,
            occurredAt = s.occurredAt,
            note = s.note,
        )
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            try {
                if (s.isEditing) {
                    tradeRepository.update(s.editingTradeId!!, draft)
                    _events.emit(TradeEvent.SavedAndClose)
                } else {
                    val id = tradeRepository.insert(draft)
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
        viewModelScope.launch {
            tradeRepository.delete(id)
            _events.emit(TradeEvent.DeletedAndClose)
        }
    }

    fun undo(tradeId: Long) {
        viewModelScope.launch { tradeRepository.delete(tradeId) }
    }

    private fun resetForm() {
        _state.update {
            it.copy(
                amount = 0,
                selectedCategory = null,
                selectedParentCategory = null,
                occurredAt = System.currentTimeMillis(),
                note = "",
                type = CategoryKind.EXPENSE,
            )
        }
        viewModelScope.launch { loadPicker(CategoryKind.EXPENSE) }
    }
}
