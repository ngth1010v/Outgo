package app.outgo.ui.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.outgo.data.db.dao.MonthCategoryTotal
import app.outgo.data.db.dao.StatDao
import app.outgo.data.repo.AccountRepository
import app.outgo.data.repo.BudgetRepository
import app.outgo.data.repo.resolvePrivacy
import app.outgo.domain.AccountPrivacy
import app.outgo.data.repo.CategoryRepository
import app.outgo.data.repo.SettingRepository
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    /** Oldest month with data … current month, plus one (coming soon) page per year. Oldest first. */
    val pages: List<AnalysisPage>,
    val selected: AnalysisPage,
    val monthSummary: Map<Int, Stage<SummaryUi>> = emptyMap(),
    /** The month's transfer total, for the summary's transfer line; from the raw-trade load. */
    val monthTransfers: Map<Int, Stage<Long>> = emptyMap(),
    /** The month's account balances, from the same load as [monthTransfers]. */
    val monthBalances: Map<Int, Stage<BalancesUi>> = emptyMap(),
    /** The month's Categories-group data, from the same load as [monthTransfers]. */
    val monthCategories: Map<Int, Stage<CategoryChartsUi>> = emptyMap(),
    /** Empty until the saved layouts are read, which draws a page with no charts for that moment. */
    val layouts: Map<LayoutSlot, List<ChartCard>> = emptyMap(),
    /** Active parent accounts with their active subaccounts, in the user's order. */
    val accountTree: List<AccountNode> = emptyList(),
    /**
     * Each active account's privacy settings as they apply, by id. Savings progress charts must skip
     * an account whose [AccountPrivacy.progressHiddenInAnalysis] is true, and never show a balance
     * whose [AccountPrivacy.hideBalance] is.
     */
    val accountPrivacy: Map<Long, AccountPrivacy> = emptyMap(),
    /** Active parent categories of each kind with their active subcategories, in the user's order. */
    val expenseTree: List<AccountNode> = emptyList(),
    val incomeTree: List<AccountNode> = emptyList(),
    /** The Trades, Savings and Budgets groups' data per month, loaded only while the list holds one of their charts. */
    val monthTradeInsights: Map<Int, Stage<TradesInsightUi>> = emptyMap(),
    val monthSavingsInsights: Map<Int, Stage<SavingsInsightUi>> = emptyMap(),
    val monthBudgetInsights: Map<Int, Stage<BudgetsInsightUi>> = emptyMap(),
    /** id -> (name, iconId, color) of every account, archived ones too, for the transfer flow chart. */
    val accountNames: Map<Long, Triple<String, Long?, Int>> = emptyMap(),
    /** id -> position on the Accounts screen, every account; the transfer flow chart's node order. */
    val accountOrder: Map<Long, Int> = emptyMap(),
    /** Each year page's summary, Accounts and Categories data, by year. Its insights sit in the month maps above, keyed `yyyy00`. */
    val years: Map<Int, Stage<YearUi>> = emptyMap(),
)

class AnalysisViewModel(
    private val statDao: StatDao,
    private val tradeRepository: TradeRepository,
    categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val budgetRepository: BudgetRepository,
    private val settingRepository: SettingRepository,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()
    val currentMonth = MonthKey.current()

    private val selected = MutableStateFlow<AnalysisPage>(AnalysisPage.Month(currentMonth))
    private val _state = MutableStateFlow(
        AnalysisUiState(pages = buildPages(currentMonth, currentMonth), selected = AnalysisPage.Month(currentMonth)),
    )
    val state: StateFlow<AnalysisUiState> = _state.asStateFlow()

    /** id -> (name, iconId, color), in the user's account order; names and colours the balance lines. */
    private var accounts: Map<Long, Triple<String, Long?, Int>> = emptyMap()

    private val summaryCache = lru<Stage<SummaryUi>>()
    private val transfersCache = lru<Stage<Long>>()
    private val balancesCache = lru<Stage<BalancesUi>>()
    private val categoriesCache = lru<Stage<CategoryChartsUi>>()
    private val tradeJobs = HashMap<Int, Job>()
    private val insightCaches = InsightGroup.entries.associateWith { lru<Stage<*>>() }
    private val insightJobs = HashMap<Pair<InsightGroup, Int>, Job>()
    private val yearCache = lru<Stage<YearUi>>()
    private val yearJobs = HashMap<Int, Job>()

    /** Pages whose charts have already played their intro animation. */
    private val animated = HashSet<AnalysisPage>()

    private var nextCardId = 0L

    /** Layout writes chain onto the previous one, so the last edit is always the one saved. */
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            val earliest = statDao.earliestMonth() ?: currentMonth
            _state.update { it.copy(pages = buildPages(earliest, currentMonth)) }
        }
        viewModelScope.launch {
            categoryRepository.observeAllOfType(CategoryKind.EXPENSE).collect { list ->
                val tree = treeOf(list.filter { it.isParent }, list.filter { !it.isParent }, link = { it.id to it.parentId }) {
                    AccountNode(it.id, it.name, it.iconId, it.color)
                }
                _state.update { it.copy(expenseTree = tree) }
            }
        }
        viewModelScope.launch {
            categoryRepository.observeAllOfType(CategoryKind.INCOME).collect { list ->
                val tree = treeOf(list.filter { it.isParent }, list.filter { !it.isParent }, link = { it.id to it.parentId }) {
                    AccountNode(it.id, it.name, it.iconId, it.color)
                }
                _state.update { it.copy(incomeTree = tree) }
            }
        }
        viewModelScope.launch {
            accountRepository.observeAll().collect { list ->
                accounts = list.associate { it.id to Triple(it.name, it.iconId, it.color) }
                val active = list.filter { !it.archived }
                val privacy = resolvePrivacy(active)
                // An account whose balance is hidden is left out of every account chart, and so is a
                // parent left with no subaccounts by that.
                val shown = active.filter { privacy[it.id]?.hideBalance != true }
                val hadChildren = active.mapNotNullTo(HashSet()) { it.parentId }
                val tree = treeOf(shown.filter { it.isParent }, shown.filter { !it.isParent }, link = { it.id to it.parentId }) {
                    AccountNode(it.id, it.name, it.iconId, it.color)
                }.filter { it.children.isNotEmpty() || it.id !in hadChildren }
                // The Accounts screen's order (each parent, then its subaccounts), archived ones too:
                // old transfers can involve them.
                val order = HashMap<Long, Int>()
                list.filter { it.isParent }.forEach { parent ->
                    order[parent.id] = order.size
                    list.filter { it.parentId == parent.id }.forEach { order[it.id] = order.size }
                }
                _state.update { it.copy(accountTree = tree, accountPrivacy = privacy, accountNames = accounts, accountOrder = order) }
            }
        }
        viewModelScope.launch {
            val decoded = LayoutSlot.entries.associateWith { slot -> decodeSlot(slot) { settingRepository.get(it) } }.toMutableMap()
            // Once: the Accounts group's charts go on top of the month list saved before they existed.
            // A fresh install takes this path too, so its list starts Accounts, then Categories.
            if (settingRepository.get(MONTH_BALANCE_ADDED_KEY) == null) {
                val month = MonthBalanceCharts.map { ChartCard(0, it) } +
                    decoded.getValue(LayoutSlot.MONTH).filter { it.type !in MonthBalanceCharts }
                decoded[LayoutSlot.MONTH] = month
                settingRepository.set(LayoutSlot.MONTH.settingKey, encodeLayout(month))
                settingRepository.set(MONTH_BALANCE_ADDED_KEY, "1")
            }
            // Once, likewise: the Categories group's charts go right after the Accounts group's.
            if (settingRepository.get(MONTH_CATEGORY_ADDED_KEY) == null) {
                val rest = decoded.getValue(LayoutSlot.MONTH).filter { it.type !in MonthCategoryGroupCharts }
                val at = rest.indexOfLast { it.type in MonthBalanceCharts } + 1
                val month = rest.take(at) + MonthCategoryGroupCharts.map { ChartCard(0, it) } + rest.drop(at)
                decoded[LayoutSlot.MONTH] = month
                settingRepository.set(LayoutSlot.MONTH.settingKey, encodeLayout(month))
                settingRepository.set(MONTH_CATEGORY_ADDED_KEY, "1")
            }
            val layouts = decoded.mapValues { (_, cards) -> cards.withIds(nextCardId).also { nextCardId += it.size } }
            _state.update { it.copy(layouts = layouts) }
            window(selected.value).forEach { loadInsights(it) }
        }
        viewModelScope.launch { observeSummary() }
        viewModelScope.launch {
            // Trade rows are a one-shot fetch (see TradeDao), so a write elsewhere has to be
            // pulled in by hand — and only for the months still in the cache.
            tradeRepository.changes.collect {
                transfersCache.keys.toList().forEach { loadTrades(AnalysisPage.Month(it), force = true) }
                yearCache.keys.toList().forEach { loadTrades(AnalysisPage.Year(it), force = true) }
                reloadInsights()
            }
        }
    }

    /** Parents in the user's order, each with its children; [link] gives a row's (id, parentId). */
    private fun <T> treeOf(parents: List<T>, children: List<T>, link: (T) -> Pair<Long, Long?>, node: (T) -> AccountNode): List<AccountNode> {
        val byParent = children.groupBy { link(it).second }
        return parents.map { parent -> node(parent).copy(children = byParent[link(parent).first].orEmpty().map(node)) }
    }

    // ----------------------------------------------------------------- SUMMARY

    /**
     * One observed range feeds the visible page and both its neighbours: each month page needs
     * itself and the month before it. Year pages draw nothing yet.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun observeSummary() {
        // Same deal as SharingStarted.WhileSubscribed(5_000) elsewhere in the app: the query
        // stops 5s after the screen stops collecting (app backgrounded) and restarts on return.
        activeWhileSubscribed()
            .flatMapLatest { active -> if (active) monthTotals() else emptyFlow() }
            .collect { (months, totals) -> publishSummary(months, totals) }
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
    private fun monthTotals(): Flow<Pair<List<Int>, List<MonthCategoryTotal>>> =
        selected.flatMapLatest { page ->
            val months = window(page).filterIsInstance<AnalysisPage.Month>().map { it.monthKey }
            if (months.isEmpty()) {
                emptyFlow()
            } else {
                statDao.observeMonthlyTotals(MonthKey.minus(months.min(), 1), months.max()).map { months to it }
            }
        }

    /** The visible page and whichever neighbours exist — exactly what the pager composes. */
    private fun window(page: AnalysisPage): List<AnalysisPage> {
        val pages = _state.value.pages
        val index = pages.indexOf(page)
        return listOfNotNull(pages.getOrNull(index - 1), page, pages.getOrNull(index + 1))
    }

    private suspend fun publishSummary(months: List<Int>, totals: List<MonthCategoryTotal>) {
        val computed = withContext(Dispatchers.Default) { months.associateWith { buildSummary(totals, it, zone) } }
        computed.forEach { (month, stage) -> summaryCache[month] = stage }
        _state.update { it.copy(monthSummary = summaryCache.toMap()) }
    }

    // ------------------------------------------------------------------ TRADES

    /**
     * Called once a month page has drawn its first frame. Loading the raw-trade charts any earlier
     * would compete with that frame for the main thread. A year page has nothing to load.
     */
    fun loadTrades(page: AnalysisPage, force: Boolean = false) {
        if (page is AnalysisPage.Year) return loadYear(page.year, force)
        val month = (page as? AnalysisPage.Month)?.monthKey ?: return
        if (!force && (transfersCache[month] != null || tradeJobs[month] != null)) return
        tradeJobs.remove(month)?.cancel()
        if (transfersCache[month] == null) {
            transfersCache[month] = Stage.Loading
            balancesCache[month] = Stage.Loading
            categoriesCache[month] = Stage.Loading
            publishTrades()
        }
        tradeJobs[month] = viewModelScope.launch {
            val trend = MonthKey.lastN(month, BALANCE_MONTH_COUNT)
            // Independent reads: run side by side (WAL lets them share the database).
            val rowsAsync = async { tradeRepository.amountsAndTimesForMonths(listOf(month), TRADE_TYPES) }
            val transfersAsync = async { tradeRepository.transferTotalsForMonths(listOf(month)) }
            val flowsAsync = async { tradeRepository.accountFlows(trend.first(), month) }
            val openingAsync = async { tradeRepository.balancesBefore(trend.first()) }
            val movesAsync = async { tradeRepository.accountMovesForMonth(month) }
            val categoryTotalsAsync = async { statDao.categoryMonthTotals(trend.first(), month) }
            val rows = rowsAsync.await()
            val transfers = transfersAsync.await()
            val flows = flowsAsync.await()
            val opening = openingAsync.await()
            val moves = movesAsync.await()
            val categoryTotals = categoryTotalsAsync.await()
            val names = accounts
            val (balances, charts) = withContext(Dispatchers.Default) {
                val balances = BalancesUi(
                    buildBalanceTrend(flows, opening, trend, names),
                    buildDailyBalance(opening, flows, moves, month, names, zone),
                )
                balances to buildCategoryCharts(rows, categoryTotals, month, zone)
            }
            transfersCache[month] = Stage.Ready(transfers.sumOf { it.total })
            // Lines that sit at 0 all along are dropped, so none left means no money anywhere.
            balancesCache[month] = if (balances.balance.lines.isEmpty() && balances.daily.lines.isEmpty()) Stage.Empty else Stage.Ready(balances)
            categoriesCache[month] = if (charts.daily.isEmpty() && charts.monthly.isEmpty()) Stage.Empty else Stage.Ready(charts)
            tradeJobs.remove(month)
            publishTrades()
        }
    }

    /** A year page's summary, Accounts and Categories data, read once like a month's trades. */
    private fun loadYear(year: Int, force: Boolean) {
        if (!force && (yearCache[year] != null || yearJobs[year] != null)) return
        yearJobs.remove(year)?.cancel()
        if (yearCache[year] == null) {
            yearCache[year] = Stage.Loading
            publishYears()
        }
        yearJobs[year] = viewModelScope.launch {
            val last = lastMonthOf(year, currentMonth)
            val first = (year - YEAR_COUNT + 1) * 100 + 1
            val totalsAsync = async { statDao.observeMonthlyTotals((year - 1) * 100 + 1, last).first() }
            val transfersAsync = async { tradeRepository.transferTotalsForMonths(monthsBetween(year * 100 + 1, last)) }
            val flowsAsync = async { tradeRepository.accountFlows(first, last) }
            val openingAsync = async { tradeRepository.balancesBefore(first) }
            val categoryTotalsAsync = async { statDao.categoryMonthTotals(first, last) }
            val totals = totalsAsync.await()
            val transfers = transfersAsync.await()
            val flows = flowsAsync.await()
            val opening = openingAsync.await()
            val categoryTotals = categoryTotalsAsync.await()
            val names = accounts
            val ui = withContext(Dispatchers.Default) {
                YearUi(
                    buildYearSummary(totals, year, last),
                    transfers.sumOf { it.total },
                    buildYearBalances(flows, opening, year, last, names),
                    buildYearCategories(categoryTotals, year, last),
                    buildYearCategories(categoryTotals, year, last) { it.tradeCount.toLong() },
                )
            }
            yearCache[year] = if (ui.summary == null && ui.balances == null && ui.categories == null) Stage.Empty else Stage.Ready(ui)
            yearJobs.remove(year)
            publishYears()
        }
    }

    private fun publishYears() {
        _state.update { it.copy(years = yearCache.toMap()) }
    }

    private fun publishTrades() {
        _state.update {
            it.copy(
                monthTransfers = transfersCache.toMap(),
                monthBalances = balancesCache.toMap(),
                monthCategories = categoriesCache.toMap(),
            )
        }
    }

    // ---------------------------------------------------------------- INSIGHTS

    /** The groups the page's saved list draws from; the others are not loaded at all. */
    private fun neededGroups(page: AnalysisPage): Set<InsightGroup> =
        _state.value.layouts[if (page is AnalysisPage.Year) LayoutSlot.YEAR else LayoutSlot.MONTH].orEmpty()
            .mapNotNullTo(HashSet()) { it.type.insight }

    /**
     * Loads the Trades/Savings/Budgets data [page] needs and doesn't have yet (or again, with
     * [force]). Cached under the page's [key]: a year's `yyyy00` never meets a month's.
     */
    fun loadInsights(page: AnalysisPage, force: Boolean = false) {
        val month = page.key
        neededGroups(page).forEach { group ->
            val cache = insightCaches.getValue(group)
            val key = group to month
            if (!force && (cache[month] != null || insightJobs[key] != null)) return@forEach
            insightJobs.remove(key)?.cancel()
            if (cache[month] == null) {
                cache[month] = Stage.Loading
                publishInsights()
            }
            insightJobs[key] = viewModelScope.launch {
                val stage: Stage<Any> = Stage.Ready(if (page is AnalysisPage.Year) buildYearInsight(group, page.year) else buildInsight(group, month))
                cache[month] = stage
                insightJobs.remove(key)
                publishInsights()
            }
        }
    }

    /** Every month already loaded, again: a trade, a budget or a savings target may have changed. */
    private fun reloadInsights() {
        val months = insightCaches.values.flatMapTo(HashSet()) { it.keys }
        months.forEach { loadInsights(if (it % 100 == 0) AnalysisPage.Year(it / 100) else AnalysisPage.Month(it), force = true) }
    }

    /**
     * The tab is shown again. Budget and savings settings change on other screens without a trade
     * write, so the months already loaded are read again.
     */
    fun onShown() = reloadInsights()

    private suspend fun buildInsight(group: InsightGroup, month: Int): Any = coroutineScope {
        when (group) {
            InsightGroup.TRADES -> {
                val months = MonthKey.lastN(month, INSIGHT_MONTH_COUNT)
                val trades = async { tradeRepository.amountsAndTimesForMonths(listOf(month), TRADE_TYPES) }
                val stats = async { statDao.categoryMonthStats(months.first(), month) }
                val transfers = async { tradeRepository.transferTotalsForMonths(listOf(month)) }
                val cash = async { tradeRepository.cashFlowForMonths(listOf(month)) }
                val t = trades.await()
                val st = stats.await()
                val tr = transfers.await()
                val cf = cash.await()
                withContext(Dispatchers.Default) { buildTradesInsight(t, st, tr, month, zone, cashFlow = cf) }
            }
            InsightGroup.SAVINGS -> {
                val months = MonthKey.lastN(month, SAVINGS_HISTORY_MONTH_COUNT)
                val all = accountRepository.observeAll().first()
                val book = SavingsBook(all, accountRepository.allSavingsMonths())
                val trades = async { tradeRepository.savingsTrades(months.first(), month, book.savingsIds) }
                val opening = async { tradeRepository.balancesBefore(month) }
                val stats = async { statDao.categoryMonthStats(MonthKey.lastN(month, INSIGHT_MONTH_COUNT).first(), month) }
                val active = all.filter { !it.archived }
                val privacy = resolvePrivacy(active)
                val income = stats.await().filter { it.type == CategoryKind.INCOME }
                    .groupBy { it.monthKey }.mapValues { (_, rows) -> rows.sumOf { it.total } }
                val t = trades.await()
                val o = opening.await()
                withContext(Dispatchers.Default) { buildSavingsInsight(book, active, privacy, t, o, income, month, zone) }
            }
            InsightGroup.BUDGETS -> {
                val months = MonthKey.lastN(month, INSIGHT_MONTH_COUNT)
                val progress = async { budgetRepository.progressFor(months) }
                val moves = async { tradeRepository.budgetMoves(months.first(), month) }
                val stats = async { statDao.categoryMonthStats(months.first(), month) }
                val p = progress.await()
                val m = moves.await()
                val st = stats.await()
                withContext(Dispatchers.Default) { buildBudgetsInsight(p, m, st, month, zone) }
            }
        }
    }

    /** The month builders over [year]'s months so far: the frequency, rate and grid charts get a column per month. */
    private suspend fun buildYearInsight(group: InsightGroup, year: Int): Any = coroutineScope {
        val last = lastMonthOf(year, currentMonth)
        val first = year * 100 + 1
        val months = monthsBetween(first, last)
        when (group) {
            InsightGroup.TRADES -> {
                val trades = async { tradeRepository.amountsAndTimesForMonths(months, TRADE_TYPES) }
                val stats = async { statDao.categoryMonthStats(first, last) }
                val transfers = async { tradeRepository.transferTotalsForMonths(months) }
                val cash = async { tradeRepository.cashFlowForMonths(months) }
                val t = trades.await()
                val st = stats.await()
                val tr = transfers.await()
                val cf = cash.await()
                withContext(Dispatchers.Default) {
                    buildTradesInsight(t, st, tr, last, zone, period = months, activityMonths = months, cashFlow = cf)
                }
            }
            InsightGroup.SAVINGS -> {
                val all = accountRepository.observeAll().first()
                val book = SavingsBook(all, accountRepository.allSavingsMonths())
                val trades = async { tradeRepository.savingsTrades(first, last, book.savingsIds) }
                val opening = async { tradeRepository.balancesBefore(first) }
                val stats = async { statDao.categoryMonthStats(first, last) }
                val active = all.filter { !it.archived }
                val privacy = resolvePrivacy(active)
                val income = stats.await().filter { it.type == CategoryKind.INCOME }
                    .groupBy { it.monthKey }.mapValues { (_, rows) -> rows.sumOf { it.total } }
                val t = trades.await()
                val o = opening.await()
                withContext(Dispatchers.Default) {
                    // The pace runs over the whole year, so its target is the year's.
                    buildSavingsInsight(
                        book, active, privacy, t, o, income, last, zone,
                        period = monthsBetween(first, year * 100 + 12), historyMonths = months, rateMonths = months,
                    )
                }
            }
            InsightGroup.BUDGETS -> {
                val progress = async { budgetRepository.progressFor(months) }
                val moves = async { tradeRepository.budgetMoves(first, last) }
                val stats = async { statDao.categoryMonthStats(first, last) }
                val p = progress.await()
                val m = moves.await()
                val st = stats.await()
                withContext(Dispatchers.Default) {
                    // A finished total, not a forecast: the bars read "left" or "over by".
                    buildBudgetsInsight(p, m, st, last, zone, historyMonths = months).copy(forecast = budgetTotals(p, months), live = false)
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun publishInsights() {
        _state.update {
            it.copy(
                monthTradeInsights = insightCaches.getValue(InsightGroup.TRADES).toMap() as Map<Int, Stage<TradesInsightUi>>,
                monthSavingsInsights = insightCaches.getValue(InsightGroup.SAVINGS).toMap() as Map<Int, Stage<SavingsInsightUi>>,
                monthBudgetInsights = insightCaches.getValue(InsightGroup.BUDGETS).toMap() as Map<Int, Stage<BudgetsInsightUi>>,
            )
        }
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
        val keep = window(page)
        val keepMonths = keep.filterIsInstance<AnalysisPage.Month>().map { it.monthKey }
        val keepYears = keep.filterIsInstance<AnalysisPage.Year>().map { it.year }
        yearJobs.keys.filter { it !in keepYears }.forEach { stale ->
            yearJobs.remove(stale)?.cancel()
            if (yearCache[stale] is Stage.Loading) yearCache.remove(stale)
        }
        tradeJobs.keys.filter { it !in keepMonths }.forEach { stale ->
            tradeJobs.remove(stale)?.cancel()
            // Drop the half-done entry too, or the page would stay stuck on its skeleton.
            if (transfersCache[stale] is Stage.Loading) {
                transfersCache.remove(stale)
                balancesCache.remove(stale)
                categoriesCache.remove(stale)
            }
        }
        keep.forEach {
            loadTrades(it)
            loadInsights(it)
        }
    }

    // ------------------------------------------------------------------ layout

    /** A drag step: in memory only, [saveLayout] writes it once the card is dropped. */
    fun moveCard(slot: LayoutSlot, id: Long, to: Int) = editLayout(slot, save = false) { cards ->
        val card = cards.first { it.id == id }
        (cards - card).toMutableList().apply { add(to.coerceIn(0, size), card) }
    }

    fun saveLayout(slot: LayoutSlot) = editLayout(slot) { it }

    fun addCard(slot: LayoutSlot, type: ChartType) {
        editLayout(slot) { it + ChartCard(nextCardId++, type) }
        // A chart of a group not loaded yet brings its data in.
        if (type.insight != null) window(selected.value).forEach { loadInsights(it) }
    }

    fun updateCard(slot: LayoutSlot, card: ChartCard) =
        editLayout(slot) { cards -> cards.map { if (it.id == card.id) card else it } }

    /** Returns the index the card had, for [restoreCard]. */
    fun removeCard(slot: LayoutSlot, id: Long): Int {
        val index = _state.value.layouts[slot]?.indexOfFirst { it.id == id } ?: -1
        editLayout(slot) { cards -> cards.filter { it.id != id } }
        return index
    }

    fun restoreCard(slot: LayoutSlot, index: Int, card: ChartCard) =
        editLayout(slot) { it.toMutableList().apply { add(index.coerceIn(0, size), card) } }

    private fun editLayout(slot: LayoutSlot, save: Boolean = true, edit: (List<ChartCard>) -> List<ChartCard>) {
        val cards = edit(_state.value.layouts[slot] ?: return)
        _state.update { it.copy(layouts = it.layouts + (slot to cards)) }
        if (!save) return
        val previous = saveJob
        saveJob = viewModelScope.launch {
            previous?.join()
            settingRepository.set(slot.settingKey, encodeLayout(cards))
        }
    }

    /** True the first time a page's charts are drawn, so a revisit doesn't re-animate. */
    fun consumeIntroAnimation(page: AnalysisPage): Boolean = animated.add(page)

    private fun <T> lru() = object : LinkedHashMap<Int, T>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, T>): Boolean = size > CACHE_SIZE
    }
}
