package app.outgo.ui.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.outgo.data.db.dao.MonthCategoryTotal
import app.outgo.data.db.dao.StatDao
import app.outgo.data.repo.AccountRepository
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

/** Per-page results kept around so swiping back never shows a skeleton again. */
private const val CACHE_SIZE = 5

/** Grace period before a backgrounded screen's live query is dropped. */
private const val STOP_TIMEOUT_MS = 5_000L

/** Both kinds come back in one query and are split in Kotlin; transfers are aggregated separately. */
private val TRADE_TYPES = listOf(TradeType.EXPENSE, TradeType.INCOME)

data class AnalysisUiState(
    /** Oldest month with data … current month, plus one summary page per year. Oldest first. */
    val pages: List<AnalysisPage>,
    val selected: AnalysisPage,
    val monthStats: Map<Int, Stage<StatsData>> = emptyMap(),
    val monthTrades: Map<Int, Stage<TradesData>> = emptyMap(),
    val yearStats: Map<Int, Stage<YearStatsData>> = emptyMap(),
    val yearTrades: Map<Int, Stage<YearTradesData>> = emptyMap(),
)

class AnalysisViewModel(
    private val statDao: StatDao,
    private val tradeRepository: TradeRepository,
    categoryRepository: CategoryRepository,
    accountRepository: AccountRepository,
    private val otherName: String,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()
    val currentMonth = MonthKey.current()

    private val selected = MutableStateFlow<AnalysisPage>(AnalysisPage.Month(currentMonth))
    private val _state = MutableStateFlow(
        AnalysisUiState(pages = buildPages(currentMonth, currentMonth), selected = AnalysisPage.Month(currentMonth)),
    )
    val state: StateFlow<AnalysisUiState> = _state.asStateFlow()

    /** id -> (name, iconId, color); labels the largest-movement rows. */
    private var categories: Map<Long, Triple<String, Long?, Int>> = emptyMap()

    /** id -> name; labels the transfer pairs. */
    private var accountNames: Map<Long, String> = emptyMap()

    private val monthStatsCache = lru<Stage<StatsData>>()
    private val monthTradesCache = lru<Stage<TradesData>>()
    private val yearStatsCache = lru<Stage<YearStatsData>>()
    private val yearTradesCache = lru<Stage<YearTradesData>>()
    private val tradeJobs = HashMap<AnalysisPage, Job>()

    /** Pages whose charts have already played their intro animation. */
    private val animated = HashSet<AnalysisPage>()

    init {
        viewModelScope.launch {
            val earliest = statDao.earliestMonth() ?: currentMonth
            _state.update { it.copy(pages = buildPages(earliest, currentMonth)) }
        }
        // Both kinds: an income trade needs its category name in the largest-movements list.
        viewModelScope.launch {
            categoryRepository.observeAllOfType(CategoryKind.EXPENSE).collect { list ->
                categories = categories + list.associate { it.id to Triple(it.name, it.iconId, it.color) }
            }
        }
        viewModelScope.launch {
            categoryRepository.observeAllOfType(CategoryKind.INCOME).collect { list ->
                categories = categories + list.associate { it.id to Triple(it.name, it.iconId, it.color) }
            }
        }
        viewModelScope.launch {
            accountRepository.observeAll().collect { list -> accountNames = list.associate { it.id to it.name } }
        }
        viewModelScope.launch { observeStats() }
        viewModelScope.launch {
            // Trade rows are a one-shot fetch (see TradeDao), so a write elsewhere has to be
            // pulled in by hand — and only for the pages still in the cache.
            tradeRepository.changes.collect {
                val cached = monthTradesCache.keys.map { AnalysisPage.Month(it) } +
                    yearTradesCache.keys.map { AnalysisPage.Year(it) }
                cached.forEach { page -> loadTrades(page, force = true) }
            }
        }
    }

    // ------------------------------------------------------------------- STATS

    /**
     * One observed range feeds the visible page and both its neighbours. A month page needs its
     * own 6-month trend window plus the neighbours'; a year page needs last year for the
     * year-over-year comparison, plus January of the next year for its right-hand neighbour.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun observeStats() {
        // Same deal as SharingStarted.WhileSubscribed(5_000) elsewhere in the app: the query
        // stops 5s after the screen stops collecting (app backgrounded) and restarts on return.
        activeWhileSubscribed()
            .flatMapLatest { active -> if (active) statsTotals() else emptyFlow() }
            .collect { (page, totals) -> publishStats(page, totals) }
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
    private fun statsTotals(): Flow<Pair<AnalysisPage, List<MonthCategoryTotal>>> =
        selected.flatMapLatest { page ->
            val range = statsRange(page)
            statDao.observeMonthlyTotals(range.first, range.second).map { page to it }
        }

    /**
     * Wide enough for the visible page **and both neighbours**: December's neighbour is a year
     * page, and a year summary built from half a year of rows would be quietly wrong rather than
     * merely late.
     */
    private fun statsRange(page: AnalysisPage): Pair<Int, Int> {
        val ranges = window(page).map { pageRange(it) }
        return ranges.minOf { it.first } to ranges.maxOf { it.second }
    }

    private fun pageRange(page: AnalysisPage): Pair<Int, Int> = when (page) {
        is AnalysisPage.Month ->
            MonthKey.minus(page.monthKey, TREND_MONTH_COUNT.toLong()) to MonthKey.minus(page.monthKey, -1)
        // Last year for the year-over-year comparison, plus January of the year after.
        is AnalysisPage.Year -> (page.year - 1) * 100 + 1 to (page.year + 1) * 100 + 1
    }

    /** The visible page and whichever neighbours exist — exactly what the pager composes. */
    private fun window(page: AnalysisPage): List<AnalysisPage> {
        val pages = _state.value.pages
        val index = pages.indexOf(page)
        return listOfNotNull(pages.getOrNull(index - 1), page, pages.getOrNull(index + 1))
    }

    private suspend fun publishStats(page: AnalysisPage, totals: List<MonthCategoryTotal>) {
        val window = window(page)
        val computed = withContext(Dispatchers.Default) {
            val months = window.filterIsInstance<AnalysisPage.Month>()
                .associate { it.monthKey to buildStats(totals, it.monthKey, otherName, zone) }
            val years = window.filterIsInstance<AnalysisPage.Year>()
                .associate { it.year to buildYearStats(totals, it.year, otherName, zone) }
            months to years
        }
        computed.first.forEach { (month, stage) -> monthStatsCache[month] = stage }
        computed.second.forEach { (year, stage) -> yearStatsCache[year] = stage }
        _state.update { it.copy(monthStats = monthStatsCache.toMap(), yearStats = yearStatsCache.toMap()) }
    }

    // ------------------------------------------------------------------ TRADES

    /**
     * Called once the page has drawn its first frame. Loading the raw-trade sections any earlier
     * would compete with that frame for the main thread.
     */
    fun loadTrades(page: AnalysisPage, force: Boolean = false) {
        if (!force && (cachedTrades(page) != null || tradeJobs[page] != null)) return
        tradeJobs.remove(page)?.cancel()
        if (cachedTrades(page) == null) putTrades(page, Stage.Loading)
        tradeJobs[page] = viewModelScope.launch {
            when (page) {
                is AnalysisPage.Month -> {
                    // lastN(month, 3) already contains the previous month, so the pace line and
                    // the 3-month weekday average share a single query.
                    val months = MonthKey.lastN(page.monthKey, 3)
                    val rows = tradeRepository.amountsAndTimesForMonths(months, TRADE_TYPES)
                    val transfers = tradeRepository.transferTotalsForMonths(listOf(page.monthKey))
                    val stage = withContext(Dispatchers.Default) {
                        buildTrades(rows, rows, transfers, page.monthKey, categories, accountNames, zone)
                    }
                    monthTradesCache[page.monthKey] = stage
                }
                is AnalysisPage.Year -> {
                    val months = monthsOfYear(page.year)
                    val rows = tradeRepository.amountsAndTimesForMonths(months, TRADE_TYPES)
                    val transfers = tradeRepository.transferTotalsForMonths(months)
                    val stage = withContext(Dispatchers.Default) {
                        buildYearTrades(rows, transfers, page.year, categories, accountNames, zone)
                    }
                    yearTradesCache[page.year] = stage
                }
            }
            tradeJobs.remove(page)
            publishTrades()
        }
    }

    private fun cachedTrades(page: AnalysisPage): Any? = when (page) {
        is AnalysisPage.Month -> monthTradesCache[page.monthKey]
        is AnalysisPage.Year -> yearTradesCache[page.year]
    }

    private fun putTrades(page: AnalysisPage, stage: Stage<Nothing>) {
        when (page) {
            is AnalysisPage.Month -> monthTradesCache[page.monthKey] = stage
            is AnalysisPage.Year -> yearTradesCache[page.year] = stage
        }
        publishTrades()
    }

    private fun publishTrades() {
        _state.update { it.copy(monthTrades = monthTradesCache.toMap(), yearTrades = yearTradesCache.toMap()) }
    }

    // -------------------------------------------------------------- navigation

    fun selectPage(page: AnalysisPage) {
        if (page == _state.value.selected) return
        selected.value = page
        _state.update { it.copy(selected = page) }
    }

    /** The pager stopped on [page]: warm its neighbours, drop prefetches the user swiped past. */
    fun onPageSettled(page: AnalysisPage) {
        selectPage(page)
        val pages = _state.value.pages
        val index = pages.indexOf(page)
        val keep = listOfNotNull(pages.getOrNull(index - 1), page, pages.getOrNull(index + 1))
        tradeJobs.keys.filter { it !in keep }.forEach { stale ->
            tradeJobs.remove(stale)?.cancel()
            // Drop the half-done entry too, or the page would stay stuck on its skeleton.
            if (cachedTrades(stale) is Stage.Loading) {
                when (stale) {
                    is AnalysisPage.Month -> monthTradesCache.remove(stale.monthKey)
                    is AnalysisPage.Year -> yearTradesCache.remove(stale.year)
                }
            }
        }
        keep.forEach { loadTrades(it) }
    }

    /** True the first time a page's charts are drawn, so a revisit doesn't re-animate. */
    fun consumeIntroAnimation(page: AnalysisPage): Boolean = animated.add(page)

    private fun <T> lru() = object : LinkedHashMap<Int, T>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, T>): Boolean = size > CACHE_SIZE
    }
}
