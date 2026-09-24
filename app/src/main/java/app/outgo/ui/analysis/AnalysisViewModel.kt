package app.outgo.ui.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.outgo.data.db.dao.MonthCategoryTotal
import app.outgo.data.db.dao.StatDao
import app.outgo.data.repo.CategoryRepository
import app.outgo.data.repo.TradeRepository
import app.outgo.domain.CategoryKind
import app.outgo.domain.TradeType
import app.outgo.util.MonthKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId

/** Per-month results kept around so swiping back never shows a skeleton again. */
private const val CACHE_SIZE = 5

/** Grace period before a backgrounded screen's live query is dropped. */
private const val STOP_TIMEOUT_MS = 5_000L

data class AnalysisUiState(
    /** Oldest month with data … current month, oldest first. One pager page each. */
    val months: List<Int>,
    val selectedMonth: Int,
    val stats: Map<Int, Stage<StatsData>> = emptyMap(),
    val trades: Map<Int, Stage<TradesData>> = emptyMap(),
)

class AnalysisViewModel(
    private val statDao: StatDao,
    private val tradeRepository: TradeRepository,
    categoryRepository: CategoryRepository,
    private val otherName: String,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val currentMonth = MonthKey.current()

    private val selected = MutableStateFlow(currentMonth)
    private val _state = MutableStateFlow(AnalysisUiState(months = listOf(currentMonth), selectedMonth = currentMonth))
    val state: StateFlow<AnalysisUiState> = _state.asStateFlow()

    /** id -> (name, iconId, color); only used to label the 5 largest purchases. */
    private var categories: Map<Long, Triple<String, Long?, Int>> = emptyMap()

    private val statsCache = lru<Stage<StatsData>>()
    private val tradesCache = lru<Stage<TradesData>>()
    private val tradeJobs = HashMap<Int, Job>()
    /** Months whose charts have already played their intro animation. */
    private val animated = HashSet<Int>()

    init {
        viewModelScope.launch {
            val earliest = statDao.earliestMonth() ?: currentMonth
            _state.update { it.copy(months = monthsFrom(earliest)) }
        }
        viewModelScope.launch {
            categoryRepository.observeAllOfType(CategoryKind.EXPENSE).collect { list ->
                categories = list.associate { it.id to Triple(it.name, it.iconId, it.color) }
            }
        }
        viewModelScope.launch { observeStats() }
        viewModelScope.launch {
            // Trade rows are a one-shot fetch (see TradeDao), so a write elsewhere has to be
            // pulled in by hand — and only for the months still in the cache.
            tradeRepository.changes.collect {
                tradesCache.keys.toList().forEach { month -> loadTrades(month, force = true) }
            }
        }
    }

    /**
     * One observed range feeds the visible page and both its neighbours: each needs its own
     * 6-month trend window, and `selected - 6 … selected + 1` covers all three.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun observeStats() {
        // Same deal as SharingStarted.WhileSubscribed(5_000) elsewhere in the app: the query
        // stops 5s after the screen stops collecting (app backgrounded) and restarts on return.
        activeWhileSubscribed()
            .flatMapLatest { active -> if (active) statsTotals() else emptyFlow() }
            .collect { (month, totals) ->
                val window = listOf(MonthKey.minus(month, 1), month, MonthKey.minus(month, -1))
                    .filter { it in _state.value.months }
                val computed = withContext(Dispatchers.Default) {
                    window.associateWith { buildStats(totals, it, otherName, zone) }
                }
                computed.forEach { (m, stage) -> statsCache[m] = stage }
                _state.update { it.copy(stats = statsCache.toMap()) }
            }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun activeWhileSubscribed(): Flow<Boolean> = _state.subscriptionCount
        .map { it > 0 }
        .distinctUntilChanged()
        .transformLatest { active ->
            if (!active) delay(STOP_TIMEOUT_MS)
            emit(active)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun statsTotals(): Flow<Pair<Int, List<MonthCategoryTotal>>> =
        selected
            .flatMapLatest { month ->
                statDao.observeMonthlyTotals(MonthKey.minus(month, TREND_MONTH_COUNT.toLong()), MonthKey.minus(month, -1))
                    .map { month to it }
            }

    fun selectMonth(month: Int) {
        if (month == _state.value.selectedMonth) return
        selected.value = month
        _state.update { it.copy(selectedMonth = month) }
    }

    /**
     * Called once the page for [month] has drawn its first frame. Loading the raw-trade sections
     * any earlier would compete with that frame for the main thread.
     */
    fun loadTrades(month: Int, force: Boolean = false) {
        if (!force && (tradesCache[month] != null || tradeJobs[month] != null)) return
        tradeJobs.remove(month)?.cancel()
        if (tradesCache[month] == null) {
            tradesCache[month] = Stage.Loading
            _state.update { it.copy(trades = tradesCache.toMap()) }
        }
        tradeJobs[month] = viewModelScope.launch {
            // lastN(month, 3) already contains the previous month, so sections 4/7/9/10 and the
            // 3-month weekday average share a single query instead of two overlapping ones.
            val months = MonthKey.lastN(month, 3)
            val rows = tradeRepository.amountsAndTimesForMonths(months, TradeType.EXPENSE)
            val stage = withContext(Dispatchers.Default) {
                buildTrades(rows, rows, month, categories, zone)
            }
            tradesCache[month] = stage
            tradeJobs.remove(month)
            _state.update { it.copy(trades = tradesCache.toMap()) }
        }
    }

    /** The pager stopped on [month]: warm its neighbours, drop prefetches the user swiped past. */
    fun onMonthSettled(month: Int) {
        selectMonth(month)
        val keep = setOf(MonthKey.minus(month, 1), month, MonthKey.minus(month, -1))
        tradeJobs.keys.filter { it !in keep }.forEach { stale ->
            tradeJobs.remove(stale)?.cancel()
            // Drop the half-done entry too, or the month would stay stuck on its skeleton.
            if (tradesCache[stale] is Stage.Loading) tradesCache.remove(stale)
        }
        keep.filter { it in _state.value.months }.forEach { loadTrades(it) }
    }

    /** True the first time a month's charts are drawn, so a revisit doesn't re-animate. */
    fun consumeIntroAnimation(month: Int): Boolean = animated.add(month)

    private fun monthsFrom(earliest: Int): List<Int> {
        val months = ArrayList<Int>()
        var month = minOf(earliest, currentMonth)
        while (month <= currentMonth) {
            months += month
            month = MonthKey.minus(month, -1)
        }
        return months
    }

    private fun <T> lru() = object : LinkedHashMap<Int, T>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, T>): Boolean = size > CACHE_SIZE
    }
}
