package app.outgo.ui.analysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.outgo.R
import app.outgo.ui.LocalAppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * One flat pager: every year's months, then that year's summary page. Only three pages are ever
 * composed (`beyondViewportPageCount = 1`) and the ViewModel keeps the last five pages' results,
 * so swiping back to a page already seen renders straight from cache with no skeleton.
 */
@Composable
fun AnalysisScreen(onOpenTrade: (Long) -> Unit, onOpenDay: (Long) -> Unit) {
    val container = LocalAppContainer.current
    val otherName = stringResource(R.string.analysis_other)
    val viewModel: AnalysisViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                AnalysisViewModel(
                    container.statDao,
                    container.tradeRepository,
                    container.categoryRepository,
                    container.accountRepository,
                    otherName,
                )
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pages = state.pages
    // Opens on the current month, not on the first page of the list.
    val pagerState = rememberPagerState(
        initialPage = pages.indexOf(state.selected).coerceAtLeast(0),
        pageCount = { pages.size },
    )
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    // One switch per screen: every mode-capable section on every page follows it.
    var mode by remember { mutableStateOf(AnalysisMode.EXPENSE) }

    // The page list starts as [this month, this year] and grows backwards once the earliest month
    // with data is known; keep the selected page under the same index when that happens.
    LaunchedEffect(pages) {
        val index = pages.indexOf(state.selected)
        if (index >= 0 && index != pagerState.currentPage) pagerState.scrollToPage(index)
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.drop(1).collect { index ->
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            viewModel.state.value.pages.getOrNull(index)?.let(viewModel::onPageSettled)
        }
    }

    val current = pages.getOrNull(pagerState.currentPage)
    Scaffold(
        topBar = {
            AnalysisHeader(
                pages = pages,
                index = pagerState.currentPage,
                current = current,
                currentMonth = viewModel.currentMonth,
                onGoTo = { target -> scope.launch { pagerState.animateScrollToPage(target) } },
            )
        },
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize().padding(padding),
            // Must be a Bundle-storable type: a data class key crashes the lazy layout.
            key = { pages.getOrNull(it)?.key ?: -it },
        ) { index ->
            when (val page = pages.getOrNull(index)) {
                is AnalysisPage.Month -> MonthPage(
                    page = page,
                    state = state,
                    viewModel = viewModel,
                    mode = mode,
                    onModeChange = { mode = it },
                    onOpenTrade = onOpenTrade,
                    onOpenDay = onOpenDay,
                    onSelectMonth = { month -> goToPage(scope, pagerState, pages.indexOf(AnalysisPage.Month(month))) },
                )
                is AnalysisPage.Year -> YearPage(
                    page = page,
                    state = state,
                    viewModel = viewModel,
                    mode = mode,
                    onModeChange = { mode = it },
                    onOpenTrade = onOpenTrade,
                    onSelectMonth = { month -> goToPage(scope, pagerState, pages.indexOf(AnalysisPage.Month(month))) },
                )
                null -> Unit
            }
        }
    }
}

private fun goToPage(
    scope: CoroutineScope,
    pagerState: androidx.compose.foundation.pager.PagerState,
    index: Int,
) {
    if (index >= 0) scope.launch { pagerState.animateScrollToPage(index) }
}

// --------------------------------------------------------------------- header

@Composable
private fun AnalysisHeader(
    pages: List<AnalysisPage>,
    index: Int,
    current: AnalysisPage?,
    currentMonth: Int,
    onGoTo: (Int) -> Unit,
) {
    val year = current?.year ?: (currentMonth / 100)
    val previousYear = yearStepTarget(pages, index, -1)
    val nextYear = yearStepTarget(pages, index, 1)
    val yearlyIndex = yearlyPageIndex(pages, year)
    val nowIndex = currentMonthIndex(pages, currentMonth)

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)) {
        HeaderRow(
            label = year.toString(),
            labelStyle = MaterialTheme.typography.titleMedium,
            previousIndex = previousYear,
            nextIndex = nextYear,
            previousDescription = stringResource(R.string.analysis_previous_year),
            nextDescription = stringResource(R.string.analysis_next_year),
            actionLabel = stringResource(R.string.analysis_year_page_title),
            actionIndex = yearlyIndex.takeIf { it >= 0 && current !is AnalysisPage.Year },
            onGoTo = onGoTo,
        )
        HeaderRow(
            label = when (current) {
                is AnalysisPage.Year, null -> stringResource(R.string.analysis_year_page_title)
                is AnalysisPage.Month -> stringResource(R.string.analysis_month_page_title, monthName(current.monthKey))
            },
            labelStyle = MaterialTheme.typography.titleSmall,
            // Plain flat order: left of January is the previous year's summary page.
            previousIndex = (index - 1).takeIf { it >= 0 },
            nextIndex = (index + 1).takeIf { it <= pages.lastIndex },
            previousDescription = stringResource(R.string.analysis_previous_month),
            nextDescription = stringResource(R.string.analysis_next_month),
            actionLabel = stringResource(R.string.analysis_go_now),
            actionIndex = nowIndex.takeIf { it >= 0 && it != index },
            onGoTo = onGoTo,
        )
    }
}

@Composable
private fun HeaderRow(
    label: String,
    labelStyle: androidx.compose.ui.text.TextStyle,
    previousIndex: Int?,
    nextIndex: Int?,
    previousDescription: String,
    nextDescription: String,
    actionLabel: String,
    actionIndex: Int?,
    onGoTo: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(HeaderRowHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            ArrowButton(previousIndex, previousDescription, rotation = 180f, onGoTo = onGoTo)
            Text(label, style = labelStyle, fontWeight = FontWeight.Bold)
            ArrowButton(nextIndex, nextDescription, rotation = 0f, onGoTo = onGoTo)
        }
        TextButton(
            onClick = { actionIndex?.let(onGoTo) },
            enabled = actionIndex != null,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            modifier = Modifier.height(HeaderRowHeight).alpha(if (actionIndex != null) 1f else 0.3f),
        ) {
            Text(actionLabel, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** 0.7x the 48dp default, so the two picker rows cost one normal app bar between them. */
private val HeaderRowHeight = 34.dp
private val HeaderIconSize = 20.dp

@Composable
private fun ArrowButton(target: Int?, description: String, rotation: Float, onGoTo: (Int) -> Unit) {
    IconButton(
        onClick = { target?.let(onGoTo) },
        enabled = target != null,
        modifier = Modifier.size(HeaderRowHeight).alpha(if (target != null) 1f else 0.3f),
    ) {
        Icon(
            painter = painterResource(R.drawable.ph_caret_right),
            contentDescription = description,
            modifier = Modifier.size(HeaderIconSize).rotate(rotation),
        )
    }
}

// ----------------------------------------------------------------- month page

/** Index of the breakdown section, used to scroll a donut selection into view. */
private const val BREAKDOWN_ITEM = 3

@Composable
private fun MonthPage(
    page: AnalysisPage.Month,
    state: AnalysisUiState,
    viewModel: AnalysisViewModel,
    mode: AnalysisMode,
    onModeChange: (AnalysisMode) -> Unit,
    onOpenTrade: (Long) -> Unit,
    onOpenDay: (Long) -> Unit,
    onSelectMonth: (Int) -> Unit,
) {
    val month = page.monthKey
    val stats = state.monthStats[month] ?: Stage.Loading
    val trades = state.monthTrades[month] ?: Stage.Loading
    val selection = remember(month) { AnalysisSelection() }
    val listState = rememberLazyListState()
    val animate = rememberIntroAnimation(page, stats, viewModel)
    // Expense-only sections have nothing to say about income and are dropped in that mode.
    val showExpenseOnly = mode != AnalysisMode.INCOME

    LoadTradesAfterFirstFrame(page, viewModel)

    LaunchedEffect(selection.rootId) {
        if (selection.rootId != null && listState.firstVisibleItemIndex > BREAKDOWN_ITEM) {
            listState.animateScrollToItem(BREAKDOWN_ITEM)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(key = "mode", contentType = "mode") { ModeSwitch(mode, onModeChange) }

        item(key = "summary", contentType = "summary") {
            StageSection(stats, stringResource(R.string.analysis_summary_title), 112.dp) {
                SummarySection(it.summary, mode, trades.dataOrNull?.transfers)
            }
        }
        item(key = "donut", contentType = "donut") {
            StageSection(stats, categoryTitle(mode), DonutHeight) {
                DonutSection(it.expense, it.income, mode, selection, animate)
            }
        }
        item(key = "breakdown", contentType = "breakdown") {
            StageSection(stats, stringResource(R.string.analysis_breakdown_title), 308.dp) {
                BreakdownSection(it.expense, it.income, mode, selection)
            }
        }
        item(key = "pace", contentType = "pace") {
            StageSection(trades, paceTitle(mode), PaceHeight + 24.dp) {
                PaceSection(it.pace, mode, animate)
            }
        }
        item(key = "bars", contentType = "bars") {
            StageSection(stats, stringResource(R.string.analysis_trend_title), BarsHeight + 24.dp) {
                BarsSection(it.bars, mode, stringResource(R.string.analysis_trend_title), onSelectMonth, animate)
            }
        }
        item(key = "movers", contentType = "movers") {
            StageSection(stats, stringResource(R.string.analysis_movers_title), moversContentHeight(mode)) {
                MoversSection(it.movers, mode, animate)
            }
        }
        if (showExpenseOnly) {
            item(key = "heatmap", contentType = "heatmap") {
                StageSection(trades, stringResource(R.string.analysis_heatmap_title), 288.dp) {
                    HeatmapSection(it.heatmap, onOpenDay)
                }
            }
            item(key = "weekday", contentType = "weekday") {
                StageSection(trades, stringResource(R.string.analysis_weekday_title), WeekdayHeight) {
                    WeekdaySection(it.weekday, animate)
                }
            }
            item(key = "buckets", contentType = "buckets") {
                StageSection(trades, stringResource(R.string.analysis_buckets_title), 232.dp) {
                    BucketsSection(it.buckets, animate)
                }
            }
        }
        item(key = "largest", contentType = "largest") {
            StageSection(trades, largestTitle(mode), 220.dp) {
                LargestSection(it.largestOf(mode), mode, onOpenTrade)
            }
        }
        // Transfers move money without spending or earning it, so they only belong to All.
        if (mode == AnalysisMode.ALL) {
            item(key = "transfers", contentType = "transfers") {
                StageSection(trades, stringResource(R.string.analysis_transfers_title), 244.dp) {
                    TransfersSection(it.transfers, animate)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ year page

@Composable
private fun YearPage(
    page: AnalysisPage.Year,
    state: AnalysisUiState,
    viewModel: AnalysisViewModel,
    mode: AnalysisMode,
    onModeChange: (AnalysisMode) -> Unit,
    onOpenTrade: (Long) -> Unit,
    onSelectMonth: (Int) -> Unit,
) {
    val stats = state.yearStats[page.year] ?: Stage.Loading
    val trades = state.yearTrades[page.year] ?: Stage.Loading
    val selection = remember(page) { AnalysisSelection() }
    val animate = rememberIntroAnimation(page, stats, viewModel)

    LoadTradesAfterFirstFrame(page, viewModel)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(key = "mode", contentType = "mode") { ModeSwitch(mode, onModeChange) }

        item(key = "year-summary", contentType = "summary") {
            StageSection(stats, stringResource(R.string.analysis_year_summary_title), 132.dp) {
                YearSummarySection(it.summary, mode, trades.dataOrNull?.transfers)
            }
        }
        item(key = "year-bars", contentType = "bars") {
            StageSection(stats, stringResource(R.string.analysis_year_bars_title), BarsHeight + 24.dp) {
                BarsSection(it.bars, mode, stringResource(R.string.analysis_year_bars_title), onSelectMonth, animate)
            }
        }
        item(key = "year-donut", contentType = "donut") {
            StageSection(stats, categoryTitle(mode), DonutHeight) {
                DonutSection(it.expense, it.income, mode, selection, animate)
            }
        }
        item(key = "year-breakdown", contentType = "breakdown") {
            StageSection(stats, stringResource(R.string.analysis_breakdown_title), 308.dp) {
                BreakdownSection(it.expense, it.income, mode, selection)
            }
        }
        item(key = "year-yoy", contentType = "yoy") {
            StageSection(stats, stringResource(R.string.analysis_year_yoy_title), YoyHeight + 24.dp) {
                YoySection(it.yoy, page.year, mode, animate)
            }
        }
        item(key = "year-largest", contentType = "largest") {
            StageSection(trades, largestTitle(mode), 220.dp) {
                LargestSection(it.largestOf(mode), mode, onOpenTrade)
            }
        }
        item(key = "year-transfers", contentType = "transfers") {
            StageSection(trades, stringResource(R.string.analysis_transfers_title), 244.dp) {
                TransfersSection(it.transfers, animate)
            }
        }
    }
}

// ---------------------------------------------------------------------- shared

/** The raw-trade query starts only after this page has put a frame on screen. */
@Composable
private fun LoadTradesAfterFirstFrame(page: AnalysisPage, viewModel: AnalysisViewModel) {
    LaunchedEffect(page) {
        withFrameNanos { }
        viewModel.loadTrades(page)
    }
}

/** Charts animate once per page; coming back to a cached page draws them finished. */
@Composable
private fun rememberIntroAnimation(page: AnalysisPage, stats: Stage<*>, viewModel: AnalysisViewModel): Boolean {
    var animate by remember(page) { mutableStateOf(false) }
    val ready = stats is Stage.Ready
    LaunchedEffect(page, ready) {
        if (ready) animate = viewModel.consumeIntroAnimation(page)
    }
    return animate
}
