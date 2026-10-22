package app.outgo.ui.analysis

import androidx.compose.foundation.background
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.Stable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.Hyphens
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.outgo.R
import app.outgo.domain.CategoryKind
import app.outgo.ui.LocalAppContainer
import app.outgo.ui.component.reorderableItem
import app.outgo.ui.component.rememberReorderState
import app.outgo.ui.nav.HistoryType
import app.outgo.ui.theme.ExpenseRed
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * One flat pager: every year's months, then that year's page. Only
 * three pages are ever composed (`beyondViewportPageCount = 1`) and the ViewModel keeps the last
 * five months' results, so swiping back to a page already seen renders straight from cache with no
 * skeleton.
 *
 * Every month page shows the same chart list, each card at a height that does not depend on the
 * data (see [chartHeight]); the year pages share a list of their own. The month pages follow one
 * scroll position ([ScrollAnchor]) and the year pages another: a swipe or a caret lands on the next
 * month with the same chart in the same place, and stepping over a year page keeps that position.
 */
@Composable
fun AnalysisScreen(
    visible: Boolean = true,
    onOpenTrade: (Long) -> Unit = {},
    onOpenDay: (HistoryType, Long) -> Unit = { _, _ -> },
) {
    val container = LocalAppContainer.current
    val viewModel: AnalysisViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                AnalysisViewModel(
                    container.statDao,
                    container.tradeRepository,
                    container.categoryRepository,
                    container.accountRepository,
                    container.budgetRepository,
                    container.settingRepository,
                )
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pages = state.pages
    // Shown again after another tab: budget and savings settings may have changed there.
    var wasVisible by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) {
        if (visible && !wasVisible) viewModel.onShown()
        wasVisible = visible
    }
    val actions = remember(onOpenTrade, onOpenDay) { InsightActions(onOpenTrade, onOpenDay) }
    // Opens on the current month, not on the first page of the list.
    val pagerState = rememberPagerState(
        initialPage = pages.indexOf(state.selected).coerceAtLeast(0),
        pageCount = { pages.size },
    )
    val monthAnchor = remember { ScrollAnchor() }
    val yearAnchor = remember { ScrollAnchor() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
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

    Scaffold(
        topBar = {
            AnalysisHeader(
                pages = pages,
                index = pagerState.currentPage,
                currentMonth = viewModel.currentMonth,
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
            when (val page = pages.getOrNull(index) ?: return@HorizontalPager) {
                is AnalysisPage.Month -> {
                    // By page rather than index: the index shifts when the list grows backwards.
                    val listState = rememberSyncedListState(monthAnchor) { pages.getOrNull(pagerState.settledPage) == page }
                    ProvideInsightFormats { MonthPage(page, listState, state, viewModel, onDeleted, actions) }
                }
                is AnalysisPage.Year -> {
                    val listState = rememberSyncedListState(yearAnchor) { pages.getOrNull(pagerState.settledPage) == page }
                    ProvideInsightFormats { YearPage(page, listState, state, viewModel, onDeleted, actions) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- scroll sync

/**
 * The one scroll position every month page shares. The settled page writes it as it scrolls; every
 * other composed month page — the neighbours a swipe reveals, a page a caret jumps to — reads it.
 * The cards are the same list at the same heights on every month page, so the same item index and
 * offset put the same chart at the same place.
 */
@Stable
private class ScrollAnchor {
    var index by mutableIntStateOf(0)
    var offset by mutableIntStateOf(0)
}

@Composable
private fun rememberSyncedListState(anchor: ScrollAnchor, isLeader: () -> Boolean): LazyListState {
    val listState = rememberLazyListState(anchor.index, anchor.offset)
    val leader by rememberUpdatedState(isLeader)
    LaunchedEffect(listState, anchor) {
        snapshotFlow {
            SyncPoint(leader(), listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, anchor.index, anchor.offset)
        }.collect { point ->
            if (point.leader) {
                anchor.index = point.index
                anchor.offset = point.offset
            } else if (point.index != point.anchorIndex || point.offset != point.anchorOffset) {
                listState.scrollToItem(point.anchorIndex, point.anchorOffset)
            }
        }
    }
    return listState
}

private data class SyncPoint(val leader: Boolean, val index: Int, val offset: Int, val anchorIndex: Int, val anchorOffset: Int)

// --------------------------------------------------------------------- header

@Composable
private fun AnalysisHeader(
    pages: List<AnalysisPage>,
    index: Int,
    currentMonth: Int,
    onGoTo: (Int) -> Unit,
) {
    val current = pages.getOrNull(index)
    val year = current?.year ?: (currentMonth / 100)
    val yearlyIndex = yearlyPageIndex(pages, year)
    val nowIndex = currentMonthIndex(pages, currentMonth)

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)) {
        HeaderRow(
            label = year.toString(),
            labelStyle = MaterialTheme.typography.titleMedium,
            previousIndex = yearStepTarget(pages, index, -1),
            nextIndex = yearStepTarget(pages, index, 1),
            previousDescription = stringResource(R.string.analysis_previous_year),
            nextDescription = stringResource(R.string.analysis_next_year),
            actionLabel = stringResource(R.string.analysis_year_page_title),
            action = if (yearlyIndex >= 0 && current !is AnalysisPage.Year) ({ onGoTo(yearlyIndex) }) else null,
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
            action = if (nowIndex >= 0 && nowIndex != index) ({ onGoTo(nowIndex) }) else null,
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
    /** Null greys the button out. */
    action: (() -> Unit)?,
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
            onClick = { action?.invoke() },
            enabled = action != null,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            modifier = Modifier.height(HeaderRowHeight).alpha(if (action != null) 1f else 0.3f),
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
    month: Int,
    slot: LayoutSlot,
    cards: List<ChartCard>,
    state: AnalysisUiState,
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
                val action: (@Composable () -> Unit)? = if (card.type.hasMode || card.type.hasZero || card.type.hasParent || card.type.hasToggles || card.type.pick != null) {
                    { CardChips(card, state, month) { viewModel.updateCard(slot, it) } }
                } else {
                    null
                }
                Box(
                    modifier = reorderableItem(reorder, card.id)
                        // Opaque, so a lifted card hides what it passes over; its shadow comes from the lift.
                        .background(cardColor(), CardShape)
                        .padding(CardPadding)
                        // Fixed per chart and chip, never per data: every page lays out alike.
                        .height(chartHeight(card.type)),
                ) {
                    CompositionLocalProvider(LocalSectionAction provides action, LocalSectionInfo provides card.type) { chart(card) }
                }
            }
            item(key = "add", contentType = "add") {
                FilledTonalButton(
                    onClick = { showAdd = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = cardColor()),
                ) {
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
private fun CardChips(card: ChartCard, state: AnalysisUiState, month: Int, onChange: (ChartCard) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (card.type.pick != null) {
            val options = pickOptions(card.type, month, state)
            val id = card.accountId?.takeIf { picked -> options.any { it.first == picked } } ?: options.firstOrNull()?.first
            ChoiceChip(options.firstOrNull { it.first == id }?.second ?: "—", options) { onChange(card.copy(accountId = it)) }
        }
        val tree = treeOf(card.type, state)
        if (card.type.hasParent) {
            val parents = tree.map { it.id to it.name }
            val id = card.accountId?.takeIf { picked -> parents.any { it.first == picked } } ?: parents.firstOrNull()?.first
            ChoiceChip(parents.firstOrNull { it.first == id }?.second ?: "—", parents) { onChange(card.copy(accountId = it)) }
        }
        if (card.type.hasToggles) {
            val options = balanceGroups(tree, card.accountId, card.type.hasParent).map { it.first.id to it.first.name }
            val shown = options.count { it.first !in card.hidden }
            val label = if (shown == options.size) stringResource(R.string.analysis_accounts_all) else stringResource(R.string.analysis_shown_count, shown, options.size)
            ChoiceChip(label, options, checked = { it !in card.hidden }) { id ->
                onChange(card.copy(hidden = if (id in card.hidden) card.hidden - id else card.hidden + id))
            }
        }
        if (card.type.hasMode) {
            ChoiceChip(modeLabel(card.mode), AnalysisMode.entries.map { it to modeLabel(it) }) { onChange(card.copy(mode = it)) }
        }
        if (card.type.hasZero) {
            val options = listOf(false to stringResource(R.string.analysis_axis_fit), true to stringResource(R.string.analysis_axis_zero))
            ChoiceChip(options.first { it.first == card.zero }.second, options) { onChange(card.copy(zero = it)) }
        }
    }
}

/** What a [ChartType.hasParent]/[ChartType.hasToggles] chart picks from: categories of its kind, or accounts. */
private fun treeOf(type: ChartType, state: AnalysisUiState): List<AccountNode> = when (type.categoryKind) {
    CategoryKind.EXPENSE -> state.expenseTree
    CategoryKind.INCOME -> state.incomeTree
    else -> state.accountTree
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
    // The chart whose description a long press opened.
    var info by remember { mutableStateOf<ChartType?>(null) }
    val onInfo: (ChartType) -> Unit = { info = it }
    info?.let { ChartInfoDialog(it, chartName(it)) { info = null } }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(key = "title") {
                Column {
                    Text(stringResource(R.string.analysis_add_chart), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.analysis_add_chart_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(slot.groups, key = { it.title }) { group ->
                Column {
                    Text(stringResource(group.title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    ChartCells(group.charts, short = false, onSelect, onInfo)
                    group.sub.forEach { sub ->
                        Text(
                            stringResource(sub.title),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        ChartCells(sub.charts, short = true, onSelect, onInfo)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartCells(charts: List<ChartType>, short: Boolean, onSelect: (ChartType) -> Unit, onInfo: (ChartType) -> Unit) {
    charts.chunked(6).forEach { row ->
        Row(modifier = Modifier.fillMaxWidth()) {
            row.forEach { type -> ChartCell(type, short, { onSelect(type) }, { onInfo(type) }, Modifier.weight(1f)) }
            repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ChartCell(type: ChartType, short: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClickLabel = stringResource(R.string.analysis_info_open, chartName(type)),
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            )
            .padding(vertical = 8.dp, horizontal = 2.dp),
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(type.icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (short) shortChartName(type) else chartName(type),
            // Six cells a row leave ~55dp each: no extra tracking, and long words hyphenate instead of splitting anywhere.
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp, hyphens = Hyphens.Auto),
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A sub-grouped chart's name under its sub-header (Accounts and Categories groups): which account
 * level or category kind on the first line, which of the three charts on the second.
 */
@Composable
private fun shortChartName(type: ChartType): String {
    val name = type.name.removePrefix("YEAR_")
    val kind = when {
        name.startsWith("ACCOUNT_") -> R.string.analysis_short_account
        name.startsWith("SUBACCOUNT_") -> R.string.analysis_short_subaccount
        name.startsWith("EXPENSE_") -> R.string.analysis_short_expense
        else -> R.string.analysis_short_income
    }
    val chart = when {
        name.endsWith("_DAILY") -> R.string.analysis_short_daily
        name.endsWith("_MONTHLY") -> R.string.analysis_short_monthly
        name.endsWith("_SHARE") -> R.string.analysis_short_share
        name.endsWith("_YEARS") -> R.string.analysis_short_years
        else -> R.string.analysis_short_months
    }
    return stringResource(kind) + "\n" + stringResource(chart)
}

/** The "+" sheet's name for a chart: never names a kind, since the card's own chip picks that. */
@Composable
private fun chartName(type: ChartType): String = if (type.insight != null) insightChartName(type) else stringResource(
    when (type) {
        ChartType.SUMMARY -> R.string.analysis_summary_title
        ChartType.ACCOUNT_DAILY -> R.string.analysis_account_daily_title
        ChartType.SUBACCOUNT_DAILY -> R.string.analysis_subaccount_daily_title
        ChartType.ACCOUNT_SHARE -> R.string.analysis_account_share_title
        ChartType.SUBACCOUNT_SHARE -> R.string.analysis_subaccount_share_title
        ChartType.ACCOUNT_MONTHS -> R.string.analysis_account_months_title
        ChartType.SUBACCOUNT_MONTHS -> R.string.analysis_subaccount_months_title
        ChartType.EXPENSE_CATEGORY_DAILY -> R.string.analysis_expense_category_daily_title
        ChartType.EXPENSE_SUBCATEGORY_DAILY -> R.string.analysis_expense_subcategory_daily_title
        ChartType.EXPENSE_CATEGORY_SHARE -> R.string.analysis_expense_category_share_title
        ChartType.EXPENSE_SUBCATEGORY_SHARE -> R.string.analysis_expense_subcategory_share_title
        ChartType.EXPENSE_CATEGORY_MONTHS -> R.string.analysis_expense_category_months_title
        ChartType.EXPENSE_SUBCATEGORY_MONTHS -> R.string.analysis_expense_subcategory_months_title
        ChartType.INCOME_CATEGORY_DAILY -> R.string.analysis_income_category_daily_title
        ChartType.INCOME_SUBCATEGORY_DAILY -> R.string.analysis_income_subcategory_daily_title
        ChartType.INCOME_CATEGORY_SHARE -> R.string.analysis_income_category_share_title
        ChartType.INCOME_SUBCATEGORY_SHARE -> R.string.analysis_income_subcategory_share_title
        ChartType.INCOME_CATEGORY_MONTHS -> R.string.analysis_income_category_months_title
        ChartType.INCOME_SUBCATEGORY_MONTHS -> R.string.analysis_income_subcategory_months_title
        ChartType.YEAR_SUMMARY -> R.string.analysis_year_summary_title
        ChartType.YEAR_ACCOUNT_MONTHLY -> R.string.analysis_year_account_monthly_title
        ChartType.YEAR_SUBACCOUNT_MONTHLY -> R.string.analysis_year_subaccount_monthly_title
        ChartType.YEAR_ACCOUNT_SHARE -> R.string.analysis_year_account_share_title
        ChartType.YEAR_SUBACCOUNT_SHARE -> R.string.analysis_year_subaccount_share_title
        ChartType.YEAR_ACCOUNT_YEARS -> R.string.analysis_year_account_years_title
        ChartType.YEAR_SUBACCOUNT_YEARS -> R.string.analysis_year_subaccount_years_title
        ChartType.YEAR_EXPENSE_CATEGORY_MONTHLY -> R.string.analysis_year_expense_category_monthly_title
        ChartType.YEAR_EXPENSE_SUBCATEGORY_MONTHLY -> R.string.analysis_year_expense_subcategory_monthly_title
        ChartType.YEAR_EXPENSE_CATEGORY_SHARE -> R.string.analysis_year_expense_category_share_title
        ChartType.YEAR_EXPENSE_SUBCATEGORY_SHARE -> R.string.analysis_year_expense_subcategory_share_title
        ChartType.YEAR_EXPENSE_CATEGORY_YEARS -> R.string.analysis_year_expense_category_years_title
        ChartType.YEAR_EXPENSE_SUBCATEGORY_YEARS -> R.string.analysis_year_expense_subcategory_years_title
        ChartType.YEAR_INCOME_CATEGORY_MONTHLY -> R.string.analysis_year_income_category_monthly_title
        ChartType.YEAR_INCOME_SUBCATEGORY_MONTHLY -> R.string.analysis_year_income_subcategory_monthly_title
        ChartType.YEAR_INCOME_CATEGORY_SHARE -> R.string.analysis_year_income_category_share_title
        ChartType.YEAR_INCOME_SUBCATEGORY_SHARE -> R.string.analysis_year_income_subcategory_share_title
        ChartType.YEAR_INCOME_CATEGORY_YEARS -> R.string.analysis_year_income_category_years_title
        ChartType.YEAR_INCOME_SUBCATEGORY_YEARS -> R.string.analysis_year_income_subcategory_years_title
        else -> 0
    },
)

// ----------------------------------------------------------------- month page

@Composable
private fun MonthPage(
    page: AnalysisPage.Month,
    listState: LazyListState,
    state: AnalysisUiState,
    viewModel: AnalysisViewModel,
    onDeleted: (LayoutSlot, Int, ChartCard) -> Unit,
    actions: InsightActions,
) {
    val month = page.monthKey
    val summary = state.monthSummary[month] ?: Stage.Loading
    val animate = rememberIntroAnimation(page, summary, viewModel)

    LoadTradesAfterFirstFrame(page, viewModel)

    val cards = state.layouts[LayoutSlot.MONTH] ?: return
    ChartList(month, LayoutSlot.MONTH, cards, state, listState, viewModel, onDeleted) { card ->
        val height = chartContentHeight(card.type)
        when (card.type) {
            ChartType.SUMMARY -> StageSection(summary, stringResource(R.string.analysis_summary_title), height) {
                SummarySection(it, card.mode, state.monthTransfers[month]?.dataOrNull)
            }
            in MonthBalanceCharts ->
                BalanceCard(card, height, state.monthBalances[month] ?: Stage.Loading, state.accountTree, month, animate)
            in MonthCategoryGroupCharts ->
                CategoryCard(card, height, state.monthCategories[month] ?: Stage.Loading, treeOf(card.type, state), month, animate)
            // Every chart a list can hold is one of the above; decodeLayout drops the rest.
            else -> if (card.type.insight != null) InsightCard(card, height, state, month, animate, actions)
        }
    }
}

/**
 * An Accounts-group chart: each parent account (its subaccounts added up), or one parent's
 * subaccounts, without the ones the card switched off.
 */
@Composable
private fun BalanceCard(card: ChartCard, height: Dp, accounts: Stage<BalancesUi>, tree: List<AccountNode>, month: Int, animate: Boolean) {
    val sub = card.type.hasParent
    val title = chartName(card.type)
    val groups = remember(tree, card.accountId, sub, card.hidden) {
        balanceGroups(tree, card.accountId, sub).filter { it.first.id !in card.hidden }
    }
    StageSection(accounts, title, height) { data ->
        when (card.type) {
            ChartType.ACCOUNT_DAILY, ChartType.SUBACCOUNT_DAILY -> {
                val lines = remember(data.daily, groups) {
                    val length = data.daily.lines.maxOfOrNull { it.values.size } ?: 0
                    if (length == 0) emptyList() else sumLines(data.daily.lines, groups, length)
                }
                BalanceDaysSection(title, lines, data.daily.daysInMonth, card.zero, animate)
            }
            ChartType.ACCOUNT_SHARE, ChartType.SUBACCOUNT_SHARE -> {
                val selection = remember(month, card.id) { AnalysisSelection() }
                val rows = remember(data.balance, groups) {
                    buildShare(sumLines(data.balance.lines, groups, data.balance.months.size), groups.associate { it.first.id to it.first })
                }
                BalanceShareSection(title, rows, selection, animate)
            }
            else -> {
                val months = data.balance.months.takeLast(BALANCE_MONTH_COUNT)
                val lines = remember(data.balance, groups) {
                    sumLines(data.balance.lines, groups, data.balance.months.size).map { it.copy(values = it.values.takeLast(BALANCE_MONTH_COUNT)) }
                }
                BalanceMonthsSection(title, lines, months, animate)
            }
        }
    }
}

/**
 * A Categories-group chart: each parent category of the card's kind (its subcategories added up),
 * or one parent's subcategories, without the ones switched off or without money in the window.
 */
@Composable
private fun CategoryCard(card: ChartCard, height: Dp, data: Stage<CategoryChartsUi>, tree: List<AccountNode>, month: Int, animate: Boolean) {
    val title = chartName(card.type)
    val kind = card.type.categoryKind ?: CategoryKind.EXPENSE
    val groups = remember(tree, card.accountId, card.type, card.hidden) {
        balanceGroups(tree, card.accountId, card.type.hasParent).filter { it.first.id !in card.hidden }
    }
    StageSection(data, title, height) { charts ->
        when (card.type) {
            ChartType.EXPENSE_CATEGORY_DAILY, ChartType.EXPENSE_SUBCATEGORY_DAILY,
            ChartType.INCOME_CATEGORY_DAILY, ChartType.INCOME_SUBCATEGORY_DAILY -> {
                val lines = remember(charts, groups) {
                    val length = charts.daily.maxOfOrNull { it.values.size } ?: 0
                    if (length == 0) emptyList() else sumLines(charts.daily, groups, length).filter { line -> line.values.any { it != 0L } }
                }
                BalanceDaysSection(title, lines, charts.daysInMonth, card.zero, animate)
            }
            ChartType.EXPENSE_CATEGORY_SHARE, ChartType.EXPENSE_SUBCATEGORY_SHARE,
            ChartType.INCOME_CATEGORY_SHARE, ChartType.INCOME_SUBCATEGORY_SHARE -> {
                val selection = remember(month, card.id) { AnalysisSelection() }
                val rows = remember(charts, groups) {
                    // This month and the one before; a category at 0 in both has nothing to show.
                    val lines = sumLines(charts.monthly, groups, charts.months.size).map { it.copy(values = it.values.takeLast(2)) }
                    buildShare(lines.filter { line -> line.values.any { it != 0L } }, groups.associate { it.first.id to it.first })
                }
                BalanceShareSection(title, rows, selection, animate, kind)
            }
            else -> {
                val lines = remember(charts, groups) {
                    sumLines(charts.monthly, groups, charts.months.size).filter { line -> line.values.any { it != 0L } }
                }
                BalanceMonthsSection(title, lines, charts.months, animate)
            }
        }
    }
}

// ------------------------------------------------------------------ year page

/**
 * The year's own chart list. Its cards are the month cards drawn from the year's data: Accounts
 * and Categories from [YearUi], Trades, Savings and Budgets from the insight maps under the year's
 * page key.
 */
@Composable
private fun YearPage(
    page: AnalysisPage.Year,
    listState: LazyListState,
    state: AnalysisUiState,
    viewModel: AnalysisViewModel,
    onDeleted: (LayoutSlot, Int, ChartCard) -> Unit,
    actions: InsightActions,
) {
    val data = state.years[page.year] ?: Stage.Loading
    val animate = rememberIntroAnimation(page, data, viewModel)

    LoadTradesAfterFirstFrame(page, viewModel)

    val cards = state.layouts[LayoutSlot.YEAR] ?: return
    ChartList(page.key, LayoutSlot.YEAR, cards, state, listState, viewModel, onDeleted) { card ->
        val height = chartContentHeight(card.type)
        val base = card.type.base ?: card.type
        when {
            base == ChartType.SUMMARY -> StageSection(data.map { it.summary }, chartName(card.type), height) {
                SummarySection(it, card.mode, data.dataOrNull?.transfers, yearly = true)
            }
            base in MonthBalanceCharts -> YearLinesCard(card, height, data.map { it.balances }, state.accountTree, page.key, animate)
            base in MonthCategoryGroupCharts -> YearLinesCard(card, height, data.map { it.categories }, treeOf(card.type, state), page.key, animate)
            card.type.insight != null -> InsightCard(card, height, state, page.key, animate, actions)
        }
    }
}

/** The inner data of a loaded stage, or [Stage.Empty] where it is null. */
private inline fun <T, R> Stage<T>.map(inner: (T) -> R?): Stage<R> = when (this) {
    is Stage.Loading -> Stage.Loading
    is Stage.Empty -> Stage.Empty
    is Stage.Ready -> inner(data)?.let { Stage.Ready(it) } ?: Stage.Empty
}

/**
 * A year Accounts or Categories chart, drawn by its month chart's section: a line per month of
 * the year, the year's pie, or a bar per year. Categories without money in the window are left out.
 */
@Composable
private fun YearLinesCard(card: ChartCard, height: Dp, data: Stage<YearLinesUi>, tree: List<AccountNode>, key: Int, animate: Boolean) {
    val title = chartName(card.type)
    val category = card.type.categoryKind != null
    val groups = remember(tree, card.accountId, card.type, card.hidden) {
        balanceGroups(tree, card.accountId, card.type.hasParent).filter { it.first.id !in card.hidden }
    }
    fun List<BalanceLine>.kept() = if (category) filter { line -> line.values.any { it != 0L } } else this
    val months = stringArrayResource(R.array.month_abbrev).let { names -> remember(*names) { names.toList() } }
    StageSection(data, title, height) { year ->
        val name = card.type.name
        when {
            name.endsWith("_MONTHLY") -> {
                val lines = remember(year, groups) {
                    if (year.months.isEmpty()) emptyList() else sumLines(year.monthly, groups, year.months.size).kept()
                }
                BalanceDaysSection(title, lines, MONTHS_IN_YEAR, card.zero, animate, xLabels = months)
            }
            name.endsWith("_SHARE") -> {
                val selection = remember(key, card.id) { AnalysisSelection() }
                val rows = remember(year, groups) {
                    buildShare(sumLines(year.share, groups, 2).kept(), groups.associate { it.first.id to it.first })
                }
                BalanceShareSection(title, rows, selection, animate, card.type.categoryKind ?: CategoryKind.INCOME)
            }
            else -> {
                val lines = remember(year, groups) { sumLines(year.yearly, groups, year.years.size).kept() }
                BalanceMonthsSection(title, lines, emptyList(), animate, barLabels = year.years.map { it.toString() })
            }
        }
    }
}

private const val MONTHS_IN_YEAR = 12

// ---------------------------------------------------------------------- shared

/** The raw-trade query starts only after this page has put a frame on screen. */
@Composable
private fun LoadTradesAfterFirstFrame(page: AnalysisPage, viewModel: AnalysisViewModel) {
    LaunchedEffect(page) {
        withFrameNanos { }
        viewModel.loadTrades(page)
        viewModel.loadInsights(page)
    }
}

/** Charts animate once per page; coming back to a cached page draws them finished. */
@Composable
private fun rememberIntroAnimation(page: AnalysisPage, stats: Stage<*>, viewModel: AnalysisViewModel): Boolean {
    var animate by remember(page) { mutableStateOf(false) }
    val ready = stats !is Stage.Loading
    LaunchedEffect(page, ready) {
        if (ready) animate = viewModel.consumeIntroAnimation(page)
    }
    return animate
}
