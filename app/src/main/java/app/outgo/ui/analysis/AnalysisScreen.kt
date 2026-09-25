package app.outgo.ui.analysis

import androidx.compose.foundation.background
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
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
import app.outgo.ui.component.reorderableItem
import app.outgo.ui.nav.placedIf
import app.outgo.ui.component.rememberReorderState
import app.outgo.ui.theme.ExpenseRed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
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
                    container.settingRepository,
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
    // One switch per screen: every page shows the same tab.
    var tab by rememberSaveable { mutableStateOf(AnalysisTab.CATEGORIES) }
    val snackbar = remember { SnackbarHostState() }
    val deletedLabel = stringResource(R.string.analysis_chart_deleted)
    val undoLabel = stringResource(R.string.trade_undo)
    val onDeleted: (LayoutSlot, Int, ChartCard) -> Unit = { slot, index, card ->
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            if (snackbar.showSnackbar(deletedLabel, actionLabel = undoLabel, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
                viewModel.restoreCard(slot, index, card)
            }
        }
    }

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
                tab = tab,
                onTab = { tab = it },
                onGoTo = { target -> scope.launch { pagerState.animateScrollToPage(target) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
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
                    tab = tab,
                    onDeleted = onDeleted,
                    onOpenTrade = onOpenTrade,
                    onOpenDay = onOpenDay,
                    onSelectMonth = { month -> goToPage(scope, pagerState, pages.indexOf(AnalysisPage.Month(month))) },
                )
                is AnalysisPage.Year -> YearPage(
                    page = page,
                    state = state,
                    viewModel = viewModel,
                    tab = tab,
                    onDeleted = onDeleted,
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
    tab: AnalysisTab,
    onTab: (AnalysisTab) -> Unit,
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
        TabSwitch(tab, onTab)
    }
}

private val TabSwitchHeight = 34.dp

@Composable
private fun TabSwitch(tab: AnalysisTab, onSelect: (AnalysisTab) -> Unit) {
    val labels = listOf(
        AnalysisTab.CATEGORIES to stringResource(R.string.analysis_tab_categories),
        AnalysisTab.ACCOUNTS to stringResource(R.string.analysis_tab_accounts),
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 4.dp, end = 8.dp, bottom = 4.dp)) {
        labels.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = tab == value,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = labels.size),
                // Below the 40dp default, overriding its minimum height.
                modifier = Modifier.height(TabSwitchHeight),
                // The default 18dp check and the text's padded slot don't fit 28dp: smaller check,
                // and the label keeps its own height, centred in the button.
                icon = {
                    if (tab == value) {
                        Icon(painterResource(R.drawable.ph_check), contentDescription = null, modifier = Modifier.size(14.dp))
                    }
                },
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.wrapContentHeight(unbounded = true))
            }
        }
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

/** Well under the 48dp default, so the two picker rows sit close together. */
private val HeaderRowHeight = 30.dp
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

// ------------------------------------------------------------------ chart list

/** Space between the screen edge and a card; the card's own padding brings its content to 16dp. */
private val PageGutter = 8.dp
private val CardPadding = 8.dp
private val CardShape = RoundedCornerShape(12.dp)

/** The delete zone opens once the finger is this close to the right edge… */
private val DeleteZoneReach = 120.dp

/** …and is this wide when open; dropping inside it deletes. */
private val DeleteZoneWidth = 45.dp

/** A thin hint of the zone while the finger is still far from it. */
private val DeleteZoneCollapsed = 6.dp

/** A shade off the page background, in both themes, so cards read as cards. */
@Composable
private fun cardColor(): Color =
    lerp(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.onBackground, 0.035f)

/**
 * One page's cards. A long press lifts a card (see [rememberReorderState]); dropping it with the
 * finger inside the red zone on the right edge deletes it instead of placing it.
 */
@Composable
private fun ChartList(
    slot: LayoutSlot,
    cards: List<ChartCard>,
    accounts: List<Pair<Long, String>>,
    listState: LazyListState,
    viewModel: AnalysisViewModel,
    onDeleted: (LayoutSlot, Int, ChartCard) -> Unit,
    chart: @Composable (ChartCard) -> Unit,
) {
    val reorder = rememberReorderState(listState)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var pageRight by remember { mutableFloatStateOf(0f) }
    // Both in root space, read on demand so a drag step recomposes the zone alone.
    fun distanceToEdge() = pageRight - reorder.fingerX
    val isNear = { distanceToEdge() < with(density) { DeleteZoneReach.toPx() } }
    val isActive = { distanceToEdge() < with(density) { DeleteZoneWidth.toPx() } }
    var showAdd by remember { mutableStateOf(false) }
    reorder.update(
        keys = cards.map { it.id },
        canDrag = { true },
        isSlot = { _, _ -> true },
        onMove = { key, to -> viewModel.moveCard(slot, key as Long, to) },
        onDrop = { key ->
            val card = cards.firstOrNull { it.id == key }
            if (card != null && isActive()) {
                onDeleted(slot, viewModel.removeCard(slot, card.id), card)
            } else {
                viewModel.saveLayout(slot)
            }
        },
    )

    Box(modifier = Modifier.fillMaxSize().onGloballyPositioned { pageRight = it.boundsInRoot().right }) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = PageGutter, end = PageGutter, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(cards, key = { it.id }, contentType = { it.type }) { card ->
                val action: (@Composable () -> Unit)? = if (card.type.hasMode || card.type.hasAccount) {
                    { CardChips(card, accounts) { viewModel.updateCard(slot, it) } }
                } else {
                    null
                }
                Box(
                    modifier = reorderableItem(reorder, card.id)
                        // Opaque, so a lifted card hides what it passes over; its shadow comes from the lift.
                        .background(cardColor(), CardShape)
                        .padding(CardPadding),
                ) {
                    CompositionLocalProvider(LocalSectionAction provides action) { chart(card) }
                }
            }
            item(key = "add", contentType = "add") {
                FilledTonalButton(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Icon(painterResource(R.drawable.ph_plus), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.analysis_add_chart))
                }
            }
        }
        if (reorder.draggingKey != null) DeleteZone(isNear, isActive, Modifier.align(Alignment.CenterEnd))
    }

    if (showAdd) {
        AddChartSheet(
            slot = slot,
            onSelect = { type ->
                showAdd = false
                viewModel.addCard(slot, type)
                scope.launch { listState.animateScrollToItem(cards.size) }
            },
            onDismiss = { showAdd = false },
        )
    }
}

@Composable
private fun CardChips(card: ChartCard, accounts: List<Pair<Long, String>>, onChange: (ChartCard) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (card.type.hasAccount) {
            val id = card.accountId ?: accounts.firstOrNull()?.first
            ChoiceChip(accounts.firstOrNull { it.first == id }?.second ?: "—", accounts) { onChange(card.copy(accountId = it)) }
        }
        if (card.type.hasMode) {
            ChoiceChip(modeLabel(card.mode), AnalysisMode.entries.map { it to modeLabel(it) }) { onChange(card.copy(mode = it)) }
        }
    }
}

/** A thin red edge while dragging; it opens as the finger nears it and turns solid when a drop would delete. */
@Composable
private fun DeleteZone(isNear: () -> Boolean, isActive: () -> Boolean, modifier: Modifier = Modifier) {
    val near = isNear()
    val active = isActive()
    val width by animateDpAsState(if (near) DeleteZoneWidth else DeleteZoneCollapsed, label = "deleteZone")
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(width)
            .background(ExpenseRed.copy(alpha = if (active) 0.5f else 0.2f)),
        contentAlignment = Alignment.Center,
    ) {
        if (near) {
            Icon(
                painter = painterResource(R.drawable.ph_trash),
                contentDescription = stringResource(R.string.analysis_delete_chart),
                tint = if (active) Color.White else ExpenseRed,
            )
        }
    }
}

/** Every chart this page offers, grouped, as icon cells — the same shape as the Trade screen's category sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddChartSheet(slot: LayoutSlot, onSelect: (ChartType) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(key = "title") {
                Text(stringResource(R.string.analysis_add_chart), style = MaterialTheme.typography.titleMedium)
            }
            items(slot.groups, key = { it.title }) { group ->
                Column {
                    Text(stringResource(group.title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    group.charts.chunked(4).forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            row.forEach { type -> ChartCell(type, { onSelect(type) }, Modifier.weight(1f)) }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartCell(type: ChartType, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        Box(
            modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(type.icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            chartName(type),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The "+" sheet's name for a chart: never names a kind, since the card's own chip picks that. */
@Composable
private fun chartName(type: ChartType): String = stringResource(
    when (type) {
        ChartType.SUMMARY -> R.string.analysis_summary_title
        ChartType.DONUT -> R.string.analysis_by_category_all_title
        ChartType.PACE -> R.string.analysis_chart_pace
        ChartType.TREND -> R.string.analysis_trend_title
        ChartType.MOVERS -> R.string.analysis_movers_title
        ChartType.HEATMAP -> R.string.analysis_heatmap_title
        ChartType.WEEKDAY -> R.string.analysis_weekday_title
        ChartType.BUCKETS -> R.string.analysis_buckets_title
        ChartType.LARGEST -> R.string.analysis_largest_all_title
        ChartType.YEAR_SUMMARY -> R.string.analysis_year_summary_title
        ChartType.YEAR_BARS -> R.string.analysis_year_bars_title
        ChartType.YOY -> R.string.analysis_year_yoy_title
        ChartType.ACCOUNT_DONUT -> R.string.analysis_by_account_all_title
        ChartType.NET_FLOW -> R.string.analysis_net_flow_title
        ChartType.BALANCE_TREND -> R.string.analysis_balance_title
        ChartType.TRANSFERS -> R.string.analysis_transfers_title
        ChartType.ACCOUNT_LARGEST -> R.string.analysis_chart_account_largest
    },
)

/** The page's list for the current tab, each tab with its own scroll position. */
@Composable
private fun PageCharts(
    page: AnalysisPage,
    tab: AnalysisTab,
    state: AnalysisUiState,
    viewModel: AnalysisViewModel,
    onDeleted: (LayoutSlot, Int, ChartCard) -> Unit,
    chart: @Composable (ChartCard) -> Unit,
) {
    // The other tab is built once this page has drawn, then kept: a tab switch only changes which
    // list is placed, instead of composing every chart of the other list on the tap.
    var prewarmed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        delay(OTHER_TAB_PREWARM_MS)
        prewarmed = true
    }
    // Nothing until the saved layouts are read: showing the defaults first would visibly reshuffle.
    if (state.layouts.isEmpty()) return
    Box(modifier = Modifier.fillMaxSize()) {
        AnalysisTab.entries.forEach { shown ->
            if (shown == tab || prewarmed) {
                val slot = LayoutSlot.of(page, shown)
                key(slot) {
                    Box(modifier = Modifier.fillMaxSize().placedIf(shown == tab)) {
                        val listState = rememberLazyListState()
                        ChartList(slot, state.layouts[slot].orEmpty(), state.accounts, listState, viewModel, onDeleted, chart)
                    }
                }
            }
        }
    }
}

/** After a page's first frame, so building the hidden tab never delays the visible one. */
private const val OTHER_TAB_PREWARM_MS = 300L

// ----------------------------------------------------------------- month page

@Composable
private fun MonthPage(
    page: AnalysisPage.Month,
    tab: AnalysisTab,
    state: AnalysisUiState,
    viewModel: AnalysisViewModel,
    onDeleted: (LayoutSlot, Int, ChartCard) -> Unit,
    onOpenTrade: (Long) -> Unit,
    onOpenDay: (Long) -> Unit,
    onSelectMonth: (Int) -> Unit,
) {
    val month = page.monthKey
    val stats = state.monthStats[month] ?: Stage.Loading
    val trades = state.monthTrades[month] ?: Stage.Loading
    val selection = remember(month) { AnalysisSelection() }
    val accountSelection = remember(month) { AnalysisSelection() }
    val animate = rememberIntroAnimation(page, stats, viewModel)
    val accounts = remember(trades) { trades.map { it.accounts } }
    val transfers = remember(trades) { trades.map { it.transfers } }

    LoadTradesAfterFirstFrame(page, viewModel)

    PageCharts(page, tab, state, viewModel, onDeleted) { card ->
        val mode = card.mode
        when (card.type) {
            ChartType.SUMMARY -> StageSection(stats, stringResource(R.string.analysis_summary_title), 112.dp) {
                SummarySection(it.summary, mode, trades.dataOrNull?.transfers)
            }
            ChartType.DONUT -> StageSection(stats, categoryTitle(mode), DonutCardSkeletonHeight) {
                DonutSection(it.expense, it.income, mode, selection, animate)
            }
            ChartType.PACE -> StageSection(trades, paceTitle(mode), PaceHeight + 24.dp) {
                PaceSection(it.pace, mode, animate)
            }
            ChartType.TREND -> StageSection(stats, stringResource(R.string.analysis_trend_title), BarsHeight + 24.dp) {
                BarsSection(it.bars, mode, stringResource(R.string.analysis_trend_title), onSelectMonth, animate)
            }
            ChartType.MOVERS -> StageSection(stats, stringResource(R.string.analysis_movers_title), moversContentHeight(mode)) {
                MoversSection(it.movers, mode, animate)
            }
            ChartType.HEATMAP -> StageSection(trades, stringResource(R.string.analysis_heatmap_title), 288.dp) {
                HeatmapSection(it.heatmap, onOpenDay)
            }
            ChartType.WEEKDAY -> StageSection(trades, stringResource(R.string.analysis_weekday_title), WeekdayHeight) {
                WeekdaySection(it.weekday, animate)
            }
            ChartType.BUCKETS -> StageSection(trades, stringResource(R.string.analysis_buckets_title), 232.dp) {
                BucketsSection(it.buckets, animate)
            }
            ChartType.LARGEST -> StageSection(trades, largestTitle(mode), 220.dp) {
                LargestSection(it.largestOf(mode), mode, onOpenTrade)
            }
            else -> AccountChart(card, accounts, transfers, state.accounts, accountSelection, animate, onOpenTrade)
        }
    }
}

// ------------------------------------------------------------------ year page

@Composable
private fun YearPage(
    page: AnalysisPage.Year,
    tab: AnalysisTab,
    state: AnalysisUiState,
    viewModel: AnalysisViewModel,
    onDeleted: (LayoutSlot, Int, ChartCard) -> Unit,
    onOpenTrade: (Long) -> Unit,
    onSelectMonth: (Int) -> Unit,
) {
    val stats = state.yearStats[page.year] ?: Stage.Loading
    val trades = state.yearTrades[page.year] ?: Stage.Loading
    val selection = remember(page) { AnalysisSelection() }
    val accountSelection = remember(page) { AnalysisSelection() }
    val animate = rememberIntroAnimation(page, stats, viewModel)
    val accounts = remember(trades) { trades.map { it.accounts } }
    val transfers = remember(trades) { trades.map { it.transfers } }

    LoadTradesAfterFirstFrame(page, viewModel)

    PageCharts(page, tab, state, viewModel, onDeleted) { card ->
        val mode = card.mode
        when (card.type) {
            ChartType.YEAR_SUMMARY -> StageSection(stats, stringResource(R.string.analysis_year_summary_title), 132.dp) {
                YearSummarySection(it.summary, mode, trades.dataOrNull?.transfers)
            }
            ChartType.YEAR_BARS -> StageSection(stats, stringResource(R.string.analysis_year_bars_title), BarsHeight + 24.dp) {
                BarsSection(it.bars, mode, stringResource(R.string.analysis_year_bars_title), onSelectMonth, animate)
            }
            ChartType.DONUT -> StageSection(stats, categoryTitle(mode), DonutCardSkeletonHeight) {
                DonutSection(it.expense, it.income, mode, selection, animate)
            }
            ChartType.YOY -> StageSection(stats, stringResource(R.string.analysis_year_yoy_title), YoyHeight + 24.dp) {
                YoySection(it.yoy, page.year, mode, animate)
            }
            ChartType.LARGEST -> StageSection(trades, largestTitle(mode), 220.dp) {
                LargestSection(it.largestOf(mode), mode, onOpenTrade)
            }
            else -> AccountChart(card, accounts, transfers, state.accounts, accountSelection, animate, onOpenTrade)
        }
    }
}

// --------------------------------------------------------------- account charts

/** The Accounts tab's charts, the same on a month page and a year page. */
@Composable
private fun AccountChart(
    card: ChartCard,
    accounts: Stage<AccountsUi>,
    transfers: Stage<TransfersUi>,
    accountList: List<Pair<Long, String>>,
    selection: AnalysisSelection,
    animate: Boolean,
    onOpenTrade: (Long) -> Unit,
) {
    val mode = card.mode
    when (card.type) {
        ChartType.ACCOUNT_DONUT -> StageSection(accounts, accountTitle(mode), DonutCardSkeletonHeight) {
            DonutSection(it.expense, it.income, mode, selection, animate, title = accountTitle(mode))
        }
        ChartType.NET_FLOW -> StageSection(accounts, stringResource(R.string.analysis_net_flow_title), NetFlowSkeletonHeight) {
            NetFlowSection(it.netFlow, animate)
        }
        ChartType.BALANCE_TREND -> StageSection(accounts, stringResource(R.string.analysis_balance_title), BalanceHeight + 24.dp) {
            BalanceTrendSection(it.balance, animate)
        }
        ChartType.TRANSFERS -> StageSection(transfers, stringResource(R.string.analysis_transfers_title), 244.dp) {
            TransfersSection(it, animate)
        }
        ChartType.ACCOUNT_LARGEST -> StageSection(accounts, largestTitle(mode), 220.dp) {
            val id = card.accountId ?: accountList.firstOrNull()?.first
            LargestSection(it.largest[id]?.of(mode).orEmpty(), mode, onOpenTrade)
        }
        // A chart of the other page kind, e.g. from a hand-edited backup: nothing to draw.
        else -> Unit
    }
}

private fun <T, R> Stage<T>.map(transform: (T) -> R): Stage<R> = when (this) {
    is Stage.Loading -> Stage.Loading
    is Stage.Empty -> Stage.Empty
    is Stage.Ready -> Stage.Ready(transform(data))
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
