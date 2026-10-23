package app.outgo.data.draft

import app.outgo.domain.TradeDraft
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** One transaction in the draft list: not in the database (no balance or stat change) until pushed. */
data class DraftTrade(val id: Long, val trade: TradeDraft)

/**
 * The one draft list: transactions added on the Add screen to save later, all at once. Kept in
 * memory only, by the user's choice: it goes when the app's process ends.
 */
class DraftStore {
    private val _drafts = MutableStateFlow<List<DraftTrade>>(emptyList())

    /** In the order they were added. */
    val drafts: StateFlow<List<DraftTrade>> = _drafts.asStateFlow()

    private var nextId = 1L

    private val _pushed = MutableSharedFlow<Int>(extraBufferCapacity = 1)

    /** How many drafts each push saved: the Add screen says so once the user is back on it. */
    val pushed: SharedFlow<Int> = _pushed.asSharedFlow()

    fun find(id: Long): DraftTrade? = _drafts.value.find { it.id == id }

    @Synchronized
    fun add(trade: TradeDraft): Long {
        val id = nextId++
        _drafts.update { it + DraftTrade(id, trade) }
        return id
    }

    fun update(id: Long, trade: TradeDraft) {
        _drafts.update { list -> list.map { if (it.id == id) it.copy(trade = trade) else it } }
    }

    fun remove(id: Long) {
        _drafts.update { list -> list.filterNot { it.id == id } }
    }

    fun removeAll(ids: Set<Long>) {
        _drafts.update { list -> list.filterNot { it.id in ids } }
    }

    fun clear() {
        _drafts.value = emptyList()
    }

    fun notifyPushed(count: Int) {
        _pushed.tryEmit(count)
    }
}
