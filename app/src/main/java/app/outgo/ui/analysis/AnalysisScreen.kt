package app.outgo.ui.analysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * One page per month, each a [LazyColumn] of ten sections. Only three pages are ever composed
 * (`beyondViewportPageCount = 1`) and the ViewModel keeps the last five months' results, so
 * swiping back to a month already seen renders straight from cache with no skeleton.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
                    otherName,
                )
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val months = state.months
    val pagerState = rememberPagerState(pageCount = { months.size })
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    // The month list starts as [current month] and grows backwards once the earliest month with
    // data is known; keep the selected month under the same page when that happens.
    LaunchedEffect(months) {
        val index = months.indexOf(state.selectedMonth)
        if (index >= 0 && index != pagerState.currentPage) pagerState.scrollToPage(index)
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.drop(1).collect { page ->
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            viewModel.state.value.months.getOrNull(page)?.let(viewModel::onMonthSettled)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        ArrowButton(
                            enabled = pagerState.currentPage > 0,
                            description = stringResource(R.string.analysis_previous_month),
                            rotation = 180f,
                            onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                        )
                        Text(
                            monthLabel(months.getOrElse(pagerState.currentPage) { state.selectedMonth }),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                        ArrowButton(
                            enabled = pagerState.currentPage < months.lastIndex,
                            description = stringResource(R.string.analysis_next_month),
                            rotation = 0f,
                            onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                        )
                    }
                },
            )
        },
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize().padding(padding),
            key = { months.getOrElse(it) { -it } },
        ) { page ->
            months.getOrNull(page)?.let { month ->
                MonthPage(
                    month = month,
                    state = state,
                    viewModel = viewModel,
                    onOpenTrade = onOpenTrade,
                    onOpenDay = onOpenDay,
                    onSelectMonth = { target ->
                        months.indexOf(target).takeIf { it >= 0 }?.let { index ->
                            scope.launch { pagerState.animateScrollToPage(index) }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ArrowButton(enabled: Boolean, description: String, rotation: Float, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.alpha(if (enabled) 1f else 0.3f)) {
        Icon(
            painter = painterResource(R.drawable.ph_caret_right),
            contentDescription = description,
            modifier = Modifier.rotate(rotation),
        )
    }
}

/** Index of the breakdown section, used to scroll a donut selection into view. */
private const val BREAKDOWN_ITEM = 2

@Composable
private fun MonthPage(
    month: Int,
    state: AnalysisUiState,
    viewModel: AnalysisViewModel,
    onOpenTrade: (Long) -> Unit,
    onOpenDay: (Long) -> Unit,
    onSelectMonth: (Int) -> Unit,
) {
    val stats = state.stats[month] ?: Stage.Loading
    val trades = state.trades[month] ?: Stage.Loading
    val selection = remember(month) { AnalysisSelection() }
    var showIncome by remember(month) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // Charts animate once per month; coming back to a cached month draws them finished.
    var animate by remember(month) { mutableStateOf(false) }
    val statsReady = stats is Stage.Ready
    LaunchedEffect(month, statsReady) {
        if (statsReady) animate = viewModel.consumeIntroAnimation(month)
    }

    // The raw-trade query starts only after this page has put a frame on screen.
    LaunchedEffect(month) {
        androidx.compose.runtime.withFrameNanos { }
        viewModel.loadTrades(month)
    }

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
        item(key = "summary", contentType = "summary") {
            StageSection(stats, stringResource(R.string.analysis_summary_title), 110.dp) {
                SummarySection(it.summary)
            }
        }
        item(key = "donut", contentType = "donut") {
            StageSection(stats, stringResource(R.string.analysis_by_category_title), 248.dp) { data ->
                DonutSection(
                    set = if (showIncome) data.income else data.expense,
                    showIncome = showIncome,
                    onToggle = { showIncome = it; selection.rootId = null },
                    selection = selection,
                    animate = animate,
                )
            }
        }
        item(key = "breakdown", contentType = "breakdown") {
            StageSection(stats, stringResource(R.string.analysis_breakdown_title), 308.dp) { data ->
                BreakdownSection(rows = (if (showIncome) data.income else data.expense).rows, selection = selection)
            }
        }
        item(key = "pace", contentType = "pace") {
            StageSection(trades, stringResource(R.string.analysis_pace_title), PaceHeight + 24.dp) {
                PaceSection(it.pace, animate)
            }
        }
        item(key = "trend", contentType = "trend") {
            StageSection(stats, stringResource(R.string.analysis_trend_title), TrendHeight + 24.dp) {
                TrendSection(it.trend, onSelectMonth, animate)
            }
        }
        item(key = "movers", contentType = "movers") {
            StageSection(stats, stringResource(R.string.analysis_movers_title), 180.dp) {
                MoversSection(it.movers, animate)
            }
        }
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
        item(key = "largest", contentType = "largest") {
            StageSection(trades, stringResource(R.string.analysis_largest_title), 220.dp) {
                LargestSection(it.largest, onOpenTrade)
            }
        }
    }
}
