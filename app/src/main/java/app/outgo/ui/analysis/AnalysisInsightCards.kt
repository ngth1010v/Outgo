package app.outgo.ui.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.outgo.R
import app.outgo.data.db.dao.TradeSlim
import app.outgo.domain.TradeType
import app.outgo.ui.component.IconView
import app.outgo.ui.home.compactAmount
import app.outgo.ui.nav.HistoryType
import app.outgo.ui.theme.BudgetWarningYellow
import app.outgo.ui.theme.ExpenseRed
import app.outgo.ui.theme.IncomeGreen
import app.outgo.util.Money
import app.outgo.util.MonthKey
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The cards of the Trades, Savings and Budgets groups. Like the other cards, each one's height is
 * fixed per chart ([insightContentHeight]) and never depends on the data: longer lists scroll
 * inside their box.
 */

private val GapDp = 8.dp
private val CalendarHeight = 168.dp
private val FlowHeight = 168.dp
private val TopRowHeight = 40.dp
private const val TOP_ROWS_SHOWN = 5
private const val BULLET_ROWS_SHOWN = 4
private const val GRID_ROWS_SHOWN = 5
private val WaterfallRowHeight = 24.dp
private const val SAVING_STEPS = 9
private const val BUDGET_STEPS = 6

@Composable
private fun lineOf(style: androidx.compose.ui.text.TextStyle): Dp = with(LocalDensity.current) { style.lineHeight.toDp() }

/** A bullet row: name and amounts, the bar, the caption. */
@Composable
private fun bulletRowHeight(): Dp = lineOf(MaterialTheme.typography.bodySmall) + lineOf(MaterialTheme.typography.labelSmall) + 18.dp

/** A grid row: a label line with room around it, never under 24dp. */
@Composable
private fun gridRowHeight(): Dp = maxOf(24.dp, lineOf(MaterialTheme.typography.labelSmall) + 8.dp)

/** The height under a Trades/Savings/Budgets card's title row; see [chartContentHeight]. */
@Composable
fun insightContentHeight(type: ChartType): Dp {
    val small = lineOf(MaterialTheme.typography.bodySmall)
    val label = lineOf(MaterialTheme.typography.labelSmall)
    return when (type) {
        ChartType.TRADE_CALENDAR, ChartType.TRADE_WEEK -> CalendarHeight + GapDp + small
        ChartType.TRADE_TRANSFERS -> FlowHeight + GapDp + small
        ChartType.TRADE_TOP -> TopRowHeight * TOP_ROWS_SHOWN
        ChartType.SAVING_PACE, ChartType.BUDGET_FORECAST -> bulletRowHeight() * BULLET_ROWS_SHOWN
        // The section spaces every child it holds: header, rows, detail line.
        ChartType.SAVING_HISTORY, ChartType.BUDGET_HISTORY -> label + GapDp + gridRowHeight() * GRID_ROWS_SHOWN + GapDp + small
        ChartType.BUDGET_REACHED -> label + GapDp + gridRowHeight() * GRID_ROWS_SHOWN
        ChartType.SAVING_SOURCES -> WaterfallRowHeight * SAVING_STEPS
        ChartType.BUDGET_WATERFALL -> WaterfallRowHeight * BUDGET_STEPS
        ChartType.BUDGET_COVERAGE -> BalanceHeight + GapDp + small + GapDp + small
        // Bars, burn-up and burn-down: the plot and a legend line.
        else -> BalanceHeight + GapDp + small
    }
}

/** The "+" sheet's and the card's title of a Trades/Savings/Budgets chart. */
@Composable
fun insightChartName(type: ChartType): String = stringResource(
    when (type) {
        ChartType.YEAR_TRADE_WEEK -> R.string.analysis_year_trade_week_title
        ChartType.YEAR_TRADE_SIZES -> R.string.analysis_year_trade_sizes_title
        ChartType.YEAR_TRADE_TOP -> R.string.analysis_year_trade_top_title
        ChartType.YEAR_TRADE_FREQUENCY -> R.string.analysis_year_trade_frequency_title
        ChartType.YEAR_TRADE_TRANSFERS -> R.string.analysis_year_trade_transfers_title
        ChartType.YEAR_SAVING_PACE -> R.string.analysis_year_saving_pace_title
        ChartType.YEAR_SAVING_HISTORY -> R.string.analysis_year_saving_history_title
        ChartType.YEAR_SAVING_RATE -> R.string.analysis_year_saving_rate_title
        ChartType.YEAR_SAVING_SOURCES -> R.string.analysis_year_saving_sources_title
        ChartType.YEAR_BUDGET_TOTALS -> R.string.analysis_year_budget_totals_title
        ChartType.YEAR_BUDGET_HISTORY -> R.string.analysis_year_budget_history_title
        ChartType.YEAR_BUDGET_COVERAGE -> R.string.analysis_year_budget_coverage_title
        ChartType.YEAR_BUDGET_REACHED -> R.string.analysis_year_budget_reached_title
        ChartType.TRADE_CALENDAR -> R.string.analysis_trade_calendar_title
        ChartType.TRADE_WEEK -> R.string.analysis_trade_week_title
        ChartType.TRADE_SIZES -> R.string.analysis_trade_sizes_title
        ChartType.TRADE_TOP -> R.string.analysis_trade_top_title
        ChartType.TRADE_FREQUENCY -> R.string.analysis_trade_frequency_title
        ChartType.TRADE_TRANSFERS -> R.string.analysis_trade_transfers_title
        ChartType.SAVING_PACE -> R.string.analysis_saving_pace_title
        ChartType.SAVING_BURNUP -> R.string.analysis_saving_burnup_title
        ChartType.SAVING_HISTORY -> R.string.analysis_saving_history_title
        ChartType.SAVING_RATE -> R.string.analysis_saving_rate_title
        ChartType.SAVING_SOURCES -> R.string.analysis_saving_sources_title
        ChartType.BUDGET_FORECAST -> R.string.analysis_budget_forecast_title
        ChartType.BUDGET_BURNDOWN -> R.string.analysis_budget_burndown_title
        ChartType.BUDGET_HISTORY -> R.string.analysis_budget_history_title
        ChartType.BUDGET_WATERFALL -> R.string.analysis_budget_waterfall_title
        ChartType.BUDGET_COVERAGE -> R.string.analysis_budget_coverage_title
        else -> R.string.analysis_budget_reached_title
    },
)

/** What a [ChartType.pick] card's settings sheet offers in [month]: savings accounts or budgets. */
fun pickOptions(type: ChartType, month: Int, state: AnalysisUiState): List<InsightRef> = when (type.pick) {
    Pick.SAVINGS -> state.monthSavingsInsights[month]?.dataOrNull?.pace?.map { it.ref }
    Pick.SAVINGS_BALANCE -> state.monthSavingsInsights[month]?.dataOrNull?.sourceAccounts
    Pick.BUDGET -> state.monthBudgetInsights[month]?.dataOrNull?.forecast?.map { it.ref }
    null -> null
}.orEmpty()

/** The card's picked id when [options] still has it, else the first option. */
private fun picked(card: ChartCard, options: List<Long>): Long? = card.accountId?.takeIf { it in options } ?: options.firstOrNull()

/** Navigation out of the cards: a trade's editor, or one day of History. */
@Stable
class InsightActions(val openTrade: (Long) -> Unit, val openDay: (HistoryType, Long) -> Unit)

@Composable
fun InsightCard(card: ChartCard, height: Dp, state: AnalysisUiState, month: Int, animate: Boolean, actions: InsightActions) {
    val title = insightChartName(card.type)
    // A year chart is its month chart drawn from the year's data; [month] is then the year's `yyyy00`.
    val type = card.type.base ?: card.type
    when (card.type.insight) {
        InsightGroup.TRADES -> StageSection(state.monthTradeInsights[month] ?: Stage.Loading, title, height) { data ->
            Section(title) {
                when (type) {
                    ChartType.TRADE_CALENDAR -> CalendarCard(card, data, state, actions, animate)
                    ChartType.TRADE_WEEK -> WeekCard(card, data, animate)
                    ChartType.TRADE_SIZES -> SizesCard(card, data, state, actions, animate)
                    ChartType.TRADE_TOP -> TopCard(card, data, state, actions)
                    ChartType.TRADE_FREQUENCY -> FrequencyCard(card, data, animate)
                    else -> TransfersCard(data, state, animate)
                }
            }
        }
        InsightGroup.SAVINGS -> StageSection(state.monthSavingsInsights[month] ?: Stage.Loading, title, height) { data ->
            Section(title) {
                when (type) {
                    ChartType.SAVING_PACE -> SavingPaceCard(data, animate)
                    ChartType.SAVING_BURNUP -> SavingBurnupCard(card, data, animate)
                    ChartType.SAVING_HISTORY -> SavingHistoryCard(card, data)
                    ChartType.SAVING_RATE -> SavingRateCard(data, animate)
                    else -> SavingSourcesCard(card, data, animate)
                }
            }
        }
        InsightGroup.BUDGETS -> StageSection(state.monthBudgetInsights[month] ?: Stage.Loading, title, height) { data ->
            Section(title) {
                when (type) {
                    ChartType.BUDGET_FORECAST -> BudgetForecastCard(data, animate)
                    ChartType.BUDGET_BURNDOWN -> BudgetBurndownCard(card, data, animate)
                    ChartType.BUDGET_HISTORY -> BudgetHistoryCard(card, data)
                    ChartType.BUDGET_WATERFALL -> BudgetWaterfallCard(card, data, animate)
                    ChartType.BUDGET_COVERAGE -> BudgetCoverageCard(data, animate, yearly = card.type.base != null)
                    else -> BudgetReachedCard(data)
                }
            }
        }
        null -> Unit
    }
}

// ------------------------------------------------------------------ shared

@Composable
private fun units() = Triple(stringResource(R.string.unit_thousand), stringResource(R.string.unit_million), stringResource(R.string.unit_billion))

@Composable
private fun compact(value: Long): String = compactAmount(value, units())

/** One line under a chart: muted text, and an optional action on the right. */
@Composable
private fun DetailLine(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (action != null && onAction != null) {
            Text(
                action,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = onAction).padding(horizontal = 4.dp),
            )
        }
    }
}

/** Legend entries without icons: a colour block and a name each. */
@Composable
private fun Legend(vararg items: Pair<Color, String>) {
    AccountLegend(items.mapIndexed { i, (color, name) -> BalanceLine(i.toLong(), name, color.toArgb(), emptyList()) })
}

@Composable
private fun Empty(text: String? = null, height: Dp) {
    Box(modifier = Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
        Text(
            text ?: stringResource(R.string.analysis_no_data),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** The colour a kind chip draws in: red out, green in, the accent for both. */
@Composable
private fun modeColor(mode: AnalysisMode): Color = when (mode) {
    AnalysisMode.EXPENSE -> ExpenseRed
    AnalysisMode.INCOME -> IncomeGreen
    AnalysisMode.ALL -> MaterialTheme.colorScheme.primary
}

private fun percent(part: Long, whole: Long): Int = if (whole > 0) Math.round(part * 100.0 / whole).toInt() else 0

@Composable
private fun trades(count: Int) = stringResource(R.string.analysis_trades_count, count)

/** A grid cell picked by a tap: its row id and month index. */
@Stable
private class CellPick {
    var row by mutableStateOf<Long?>(null)
    var column by mutableIntStateOf(-1)
    fun toggle(r: Long, c: Int) {
        if (row == r && column == c) {
            row = null
            column = -1
        } else {
            row = r
            column = c
        }
    }
}

// ================================================================== TRADES

@Composable
private fun CalendarCard(card: ChartCard, data: TradesInsightUi, state: AnalysisUiState, actions: InsightActions, animate: Boolean) {
    val full = LocalFullscreenChart.current
    var selected by remember(data.month, card.id) { mutableStateOf<Int?>(null) }
    val values = remember(data, card.mode, card.byCount) {
        data.days.map { if (card.byCount) it.count(card.mode).toLong() else it.value(card.mode) }
    }
    // Full screen: square cells, then the picked day's trades in the room left.
    Column(modifier = fullscreenBox(full), verticalArrangement = Arrangement.spacedBy(GapDp)) {
        CalendarHeatmap(
            data.daysInMonth,
            data.firstWeekday,
            values,
            modeColor(card.mode),
            // Counts have no sign: All then shades in the accent like the other kinds.
            diverging = card.mode == AnalysisMode.ALL && !card.byCount,
            selected = selected,
            onSelect = { selected = if (it == selected) null else it },
            progress = introProgress(animate),
            modifier = if (full != null) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().height(CalendarHeight),
            today = remember(data.month) { LocalDate.now().takeIf { it.year * 100 + it.monthValue == data.month }?.dayOfMonth },
            square = full != null,
        )
        val signed: (Long) -> String = { if (card.mode == AnalysisMode.EXPENSE) Money.formatSigned(-it) else Money.formatSigned(it) }
        val day = selected
        if (day != null) {
            val totals = data.days[day - 1]
            val date = LocalDate.of(data.month / 100, data.month % 100, day)
            val label = stringArrayResource(R.array.weekday_abbrev)[date.dayOfWeek.value - 1] + " " + day
            // All has no single kind: History opens on the one that moved more that day.
            val type = when {
                card.mode == AnalysisMode.INCOME -> HistoryType.INCOME
                card.mode == AnalysisMode.EXPENSE -> HistoryType.EXPENSE
                totals.income > totals.expense -> HistoryType.INCOME
                else -> HistoryType.EXPENSE
            }
            val zone = ZoneId.systemDefault()
            val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
            DetailLine(
                stringResource(R.string.analysis_day_detail, label, signed(totals.value(card.mode)), trades(totals.count(card.mode))),
                stringResource(R.string.analysis_open),
            ) { actions.openDay(type, start) }
            if (full != null) {
                val dayTrades = remember(data, card.mode, day) {
                    data.trades.filter { card.mode.takes(it.type) && Instant.ofEpochMilli(it.occurredAt).atZone(zone).toLocalDate() == date }.sortedBy { it.occurredAt }
                }
                TradeList(dayTrades, state, actions, TimeOnly, ranked = false, modifier = Modifier.weight(1f))
            }
        } else {
            val busiest = values.indices.maxByOrNull { kotlin.math.abs(values[it]) }?.takeIf { values[it] != 0L }
            DetailLine(
                if (busiest == null) stringResource(R.string.analysis_tap_hint)
                else stringResource(
                    R.string.analysis_busiest,
                    stringResource(R.string.analysis_day_number, busiest + 1) + " (" + (if (card.byCount) trades(values[busiest].toInt()) else signed(values[busiest])) + ")",
                ),
            )
        }
    }
}

/** On the card, the content's own height; in full screen all of [full]'s, so a list can take the rest with a weight. */
private fun fullscreenBox(full: FullscreenChart?): Modifier = if (full != null) Modifier.fillMaxWidth().height(full.height) else Modifier.fillMaxWidth()

/** A chart's height in full screen when a list shares the screen with it. */
private fun FullscreenChart.chartWithList(normal: Dp): Dp = maxOf(normal, height * 0.4f)

@Composable
private fun WeekCard(card: ChartCard, data: TradesInsightUi, animate: Boolean) {
    val full = LocalFullscreenChart.current
    var selected by remember(data.month, card.id) { mutableStateOf<Int?>(null) }
    val grid = remember(data, card.mode) { weekGrid(data.trades, card.mode, ZoneId.systemDefault()) }
    val values = remember(grid, card.byCount) { if (card.byCount) grid.counts.map { it.toLong() } else grid.amounts }
    val weekdays = stringArrayResource(R.array.weekday_abbrev)
    fun slotName(i: Int): String {
        val hours = 24 / DAY_SLOTS
        val slot = i % DAY_SLOTS
        return "${weekdays[i / DAY_SLOTS]} ${slot * hours}h–${(slot + 1) * hours}h"
    }
    Column(modifier = fullscreenBox(full), verticalArrangement = Arrangement.spacedBy(GapDp)) {
        WeekHeatmap(
            values,
            modeColor(card.mode),
            selected,
            onSelect = { selected = if (it == selected) null else it },
            progress = introProgress(animate),
            modifier = Modifier.fillMaxWidth().height(full?.chartWithList(CalendarHeight) ?: CalendarHeight),
            // A weekday and hour, not a date: marked on every page.
            now = remember { LocalDateTime.now().let { (it.dayOfWeek.value - 1) * DAY_SLOTS + it.hour / (24 / DAY_SLOTS) } },
        )
        val cell = selected
        if (cell != null) {
            DetailLine(stringResource(R.string.analysis_day_detail, slotName(cell), Money.format(grid.amounts[cell]), trades(grid.counts[cell])))
        } else {
            val busiest = values.indices.maxByOrNull { values[it] }?.takeIf { values[it] > 0 }
            DetailLine(if (busiest == null) stringResource(R.string.analysis_no_data) else stringResource(R.string.analysis_busiest, slotName(busiest)))
        }
        if (full != null) {
            // Every slot with trades, ranked by what the card shades by, the other value breaking ties.
            val ranked = remember(grid, card.byCount) {
                grid.counts.indices.filter { grid.counts[it] > 0 }.sortedWith(
                    if (card.byCount) compareByDescending<Int> { grid.counts[it] }.thenByDescending { grid.amounts[it] }
                    else compareByDescending<Int> { grid.amounts[it] }.thenByDescending { grid.counts[it] },
                )
            }
            val largest = (ranked.firstOrNull()?.let { values[it] } ?: 0L).coerceAtLeast(1L)
            val color = modeColor(card.mode)
            Column(modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                ranked.forEachIndexed { rank, i ->
                    val amount = Money.format(grid.amounts[i])
                    val count = trades(grid.counts[i])
                    BarRow(rank + 1, values[i].toFloat() / largest, color, selected == i, onClick = { selected = if (selected == i) null else i }) {
                        Text(slotName(i), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.End) {
                            // The value the list is ranked by on top.
                            Text(if (card.byCount) count else amount, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1)
                            Text(if (card.byCount) amount else count, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** A list row of [TopRowHeight]: its [rank], then [content], over a faint bar of [share] (0..1) in [color]; tinted when [selected]. */
@Composable
private fun BarRow(
    rank: Int?,
    share: Float,
    color: Color,
    selected: Boolean = false,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(TopRowHeight)
            .clip(RoundedCornerShape(8.dp))
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) else Modifier)
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(share.coerceIn(0f, 1f)).background(color.copy(alpha = 0.08f)))
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (rank != null) {
                Text("$rank", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(24.dp))
            }
            content()
        }
    }
}

private val TimeOnly = DateTimeFormatter.ofPattern("HH:mm")

/**
 * [list] as rows that open the trade's editor: category, [time] (and note), signed amount, over a
 * bar of its share of the largest. Scrolls inside [modifier]'s box.
 */
@Composable
private fun TradeList(list: List<TradeSlim>, state: AnalysisUiState, actions: InsightActions, time: DateTimeFormatter, ranked: Boolean, modifier: Modifier) {
    val categories = remember(state.expenseTree, state.incomeTree) {
        (state.expenseTree + state.incomeTree).flatMap { listOf(it) + it.children }.associateBy { it.id }
    }
    val largest = (list.maxOfOrNull { it.amount } ?: 0L).coerceAtLeast(1L)
    val zone = ZoneId.systemDefault()
    Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        list.forEachIndexed { i, t ->
            val category = t.categoryId?.let { categories[it] }
            val income = t.type == TradeType.INCOME
            val color = if (income) IncomeGreen else ExpenseRed
            BarRow(if (ranked) i + 1 else null, t.amount.toFloat() / largest, color, onClick = { actions.openTrade(t.id) }) {
                IconView(iconId = category?.iconId, size = 24.dp, color = category?.color)
                Spacer(Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(category?.name.orEmpty(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(Instant.ofEpochMilli(t.occurredAt).atZone(zone).format(time), t.note?.takeIf { it.isNotBlank() }).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(Money.formatSigned(if (income) t.amount else -t.amount), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = color, maxLines = 1)
            }
        }
    }
}

@Composable
private fun SizesCard(card: ChartCard, data: TradesInsightUi, state: AnalysisUiState, actions: InsightActions, animate: Boolean) {
    val buckets = remember(data, card.mode) { sizeBuckets(data.trades, card.mode) }
    if (buckets == null) return Empty(height = listHeight(BalanceHeight))
    val full = LocalFullscreenChart.current
    var selected by remember(data.month, card.id, card.mode) { mutableStateOf<Int?>(null) }
    val unitNames = units()
    val labels = remember(buckets, unitNames) {
        List(buckets.counts.size) { i -> if (i == 0) "<" + compactAmount(buckets.edges[0], unitNames) else compactAmount(buckets.edges[i - 1], unitNames) + "+" }
    }
    val tradesShare = MaterialTheme.colorScheme.primary
    val moneyShare = modeColor(if (card.mode == AnalysisMode.ALL) AnalysisMode.EXPENSE else card.mode).copy(alpha = 0.75f)
    val tradesFormat = LocalTrades.current
    Column(modifier = fullscreenBox(full), verticalArrangement = Arrangement.spacedBy(GapDp)) {
        PairedShareBars(
            labels,
            buckets.countShare,
            buckets.sumShare,
            tradesShare,
            moneyShare,
            describe = { i ->
                listOf(
                    labels[i],
                    tradesFormat(buckets.counts[i]) + " (${Math.round(buckets.countShare[i] * 100)}%)",
                    Money.format(buckets.sums[i]) + " (${Math.round(buckets.sumShare[i] * 100)}%)",
                )
            },
            progress = introProgress(animate),
            modifier = Modifier.fillMaxWidth().height(full?.chartWithList(BalanceHeight) ?: BalanceHeight),
            // Full screen: a tap picks a bucket and lists its trades.
            selected = selected.takeIf { full != null },
            onSelect = if (full != null) { i -> selected = if (selected == i) null else i } else null,
        )
        Legend(tradesShare to stringResource(R.string.analysis_share_of_trades), moneyShare to stringResource(R.string.analysis_share_of_money))
        if (full != null) {
            val bucket = selected
            if (bucket == null) {
                DetailLine(stringResource(R.string.analysis_tap_bucket_hint))
            } else {
                DetailLine(stringResource(R.string.analysis_day_detail, labels[bucket], Money.format(buckets.sums[bucket]), trades(buckets.counts[bucket])))
                // data.trades is already largest first.
                val list = remember(data, card.mode, buckets, bucket) { data.trades.filter { card.mode.takes(it.type) && it.amount > 0 && buckets.bucketOf(it.amount) == bucket } }
                TradeList(list, state, actions, TopDate, ranked = true, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** "N trades", usable outside composition (inside a chart's describe lambda). */
private val LocalTrades = androidx.compose.runtime.staticCompositionLocalOf<(Int) -> String> { { "$it" } }

@Composable
fun ProvideInsightFormats(content: @Composable () -> Unit) {
    val pattern = stringResource(R.string.analysis_trades_count)
    val format: (Int) -> String = remember(pattern) { { n -> String.format(pattern, n) } }
    androidx.compose.runtime.CompositionLocalProvider(LocalTrades provides format, content = content)
}

private val TopDate = DateTimeFormatter.ofPattern("dd/MM HH:mm")

@Composable
private fun TopCard(card: ChartCard, data: TradesInsightUi, state: AnalysisUiState, actions: InsightActions) {
    val top = remember(data, card.mode) { topTrades(data.trades, card.mode) }
    val height = listHeight(TopRowHeight * TOP_ROWS_SHOWN)
    if (top.isEmpty()) return Empty(height = height)
    TradeList(top, state, actions, TopDate, ranked = true, modifier = Modifier.height(height))
}

private fun Modifier.fillMaxSize() = fillMaxWidth().fillMaxHeight()

@Composable
private fun monthLabels(months: List<Int>): List<String> {
    val names = stringArrayResource(R.array.month_abbrev)
    return remember(months, names.toList()) { months.map { names[(it % 100) - 1] } }
}

@Composable
private fun FrequencyCard(card: ChartCard, data: TradesInsightUi, animate: Boolean) {
    val counts = remember(data, card.mode) { data.activity.map { it.count(card.mode).toLong() } }
    if (counts.all { it == 0L }) return Empty(height = listHeight(BalanceHeight))
    val full = LocalFullscreenChart.current
    val averages = remember(data, card.mode) { data.activity.map { a -> a.count(card.mode).let { if (it > 0) a.total(card.mode) / it else 0L } } }
    val labels = monthLabels(data.activity.map { it.month })
    val unitNames = units()
    val tradesFormat = LocalTrades.current
    val averageLabel = stringResource(R.string.analysis_legend_average)
    val barColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    val lineColor = modeColor(if (card.mode == AnalysisMode.ALL) AnalysisMode.EXPENSE else card.mode)
    Column(modifier = fullscreenBox(full), verticalArrangement = Arrangement.spacedBy(GapDp)) {
        BarLineChart(
            labels,
            counts,
            averages,
            barColor,
            lineColor,
            barText = { it.toString() },
            describe = { i -> listOf(labels[i] + ": " + tradesFormat(counts[i].toInt()), averageLabel + ": " + compactAmount(averages[i], unitNames)) },
            progress = introProgress(animate),
            modifier = Modifier.fillMaxWidth().height(full?.chartWithList(BalanceHeight) ?: BalanceHeight),
        )
        Legend(barColor to stringResource(R.string.analysis_legend_trades), lineColor to averageLabel)
        if (full != null) FrequencyTable(data.activity, card.mode, Modifier.weight(1f))
    }
}

/** One row per month, newest first: its trade count, total and average per trade. */
@Composable
private fun FrequencyTable(activity: List<MonthActivity>, mode: AnalysisMode, modifier: Modifier) {
    val label = MaterialTheme.typography.labelSmall
    Column(modifier = modifier.fillMaxWidth()) {
        TableRow(
            listOf(
                stringResource(R.string.analysis_mode_month),
                stringResource(R.string.analysis_legend_trades),
                stringResource(R.string.analysis_col_total),
                stringResource(R.string.analysis_col_average),
            ),
            header = true,
            modifier = Modifier.height(lineOf(label) + 4.dp),
        )
        Column(modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
            activity.asReversed().forEach { a ->
                val count = a.count(mode)
                val total = a.total(mode)
                TableRow(
                    listOf(monthYear(a.month), "$count", Money.format(total), if (count > 0) Money.format(total / count) else "—"),
                    header = false,
                    modifier = Modifier.height(TopRowHeight),
                )
            }
        }
    }
}

private val TableWeights = floatArrayOf(1.1f, 0.9f, 1.3f, 1.3f)

/** A [FrequencyTable] line: the month left, the numbers right-aligned. */
@Composable
private fun TableRow(cells: List<String>, header: Boolean, modifier: Modifier) {
    val style = if (header) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall
    val color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        cells.forEachIndexed { i, text ->
            Text(
                text,
                style = style,
                color = color,
                fontWeight = if (!header && i == 0) FontWeight.Medium else null,
                textAlign = if (i == 0) TextAlign.Start else TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(TableWeights[i]),
            )
        }
    }
}

@Composable
private fun TransfersCard(data: TradesInsightUi, state: AnalysisUiState, animate: Boolean) {
    // The card merges accounts whose bar is shorter than their two-line label (gaps between the
    // bars take about 24dp); full screen shows every account.
    val full = LocalFullscreenChart.current != null
    val minShare = if (full) 0.0 else (lineOf(MaterialTheme.typography.labelSmall) * 2 / (FlowHeight - 24.dp)).toDouble()
    val flow = remember(data, minShare) { transferFlow(data.transfers, minShare) }
    if (flow == null) return Empty(height = listHeight(FlowHeight + GapDp))
    TransferFlowChart(
        flow,
        state.accountNames,
        stringResource(R.string.analysis_flow_other),
        introProgress(animate),
        // Full screen: all the room but the total line under it.
        Modifier.fillMaxWidth().height(listHeight(FlowHeight, reserve = GapDp + lineOf(MaterialTheme.typography.bodySmall))),
    )
    DetailLine(stringResource(R.string.analysis_flow_total, flow.count, Money.format(flow.total)))
}

// ================================================================= SAVINGS

/** A bullet row: icon, name, amount against goal, the bar, and a caption in [captionColor]. */
@Composable
private fun BulletRowItem(
    ref: InsightRef,
    value: Long,
    goal: Long,
    pace: Long,
    projected: Long,
    fill: Color,
    caption: String,
    captionColor: Color,
    animate: Boolean,
) {
    Row(modifier = Modifier.fillMaxWidth().height(bulletRowHeight()).padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        IconView(iconId = ref.iconId, size = 28.dp, color = ref.color)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ref.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.analysis_amount_of, compact(value), compact(goal)), style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            BulletBar(value, goal, pace, projected, fill, introProgress(animate), Modifier.fillMaxWidth().height(10.dp))
            Text(caption, style = MaterialTheme.typography.labelSmall, color = captionColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SavingPaceCard(data: SavingsInsightUi, animate: Boolean) {
    val height = listHeight(bulletRowHeight() * BULLET_ROWS_SHOWN)
    if (data.pace.isEmpty()) return Empty(stringResource(R.string.analysis_no_targets), height)
    val live = data.elapsed < data.daysInMonth
    Column(modifier = Modifier.fillMaxWidth().height(height).verticalScroll(rememberScrollState())) {
        data.pace.forEach { row ->
            val (caption, color) = when {
                row.saved >= row.target -> stringResource(R.string.analysis_target_reached) to IncomeGreen
                !live -> stringResource(R.string.analysis_missed, compact(row.target - row.saved)) to ExpenseRed
                row.saved >= row.pace -> stringResource(R.string.analysis_ahead, compact(row.saved - row.pace)) + " · " +
                    stringResource(R.string.analysis_heading, compact(row.projected)) to IncomeGreen
                else -> stringResource(R.string.analysis_behind, compact(row.pace - row.saved)) + " · " +
                    stringResource(R.string.analysis_heading, compact(row.projected)) to ExpenseRed
            }
            BulletRowItem(row.ref, row.saved, row.target, row.pace, row.projected, Color(row.ref.color), caption, color, animate)
        }
    }
}

/** The actual line, the even-pace line to [goalAtEnd] (or from it, falling), and a projection from today. */
@Composable
private fun pacedLines(
    ref: InsightRef,
    actual: List<Long>,
    daysInMonth: Int,
    live: Boolean,
    pace: (day: Int) -> Long,
    projectedEnd: Long,
): List<BalanceLine> {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val paceName = stringResource(R.string.analysis_line_pace)
    val projectedName = stringResource(R.string.analysis_line_projected)
    return remember(ref, actual, daysInMonth, live, projectedEnd, muted, paceName, projectedName) {
        val lines = ArrayList<BalanceLine>()
        lines += BalanceLine(-2, paceName, muted, (1..daysInMonth).map(pace), dashed = true)
        lines += BalanceLine(ref.id, ref.name, ref.color, actual, iconId = ref.iconId)
        val today = actual.size
        if (live && today in 1 until daysInMonth) {
            val from = actual.last()
            val steps = daysInMonth - today
            lines += BalanceLine(-3, projectedName, ref.color, (0..steps).map { k -> from + (projectedEnd - from) * k / steps }, dashed = true, start = today - 1)
        }
        lines
    }
}

@Composable
private fun SavingBurnupCard(card: ChartCard, data: SavingsInsightUi, animate: Boolean) {
    val id = picked(card, data.pace.map { it.ref.id })
    val row = data.pace.firstOrNull { it.ref.id == id } ?: return Empty(stringResource(R.string.analysis_no_targets), listHeight(BalanceHeight))
    val live = data.elapsed < data.daysInMonth
    val lines = pacedLines(row.ref, data.daily[row.ref.id].orEmpty(), data.daysInMonth, live, { d -> row.target * d / data.daysInMonth }, row.projected)
    val focus = remember { AnalysisSelection() }
    DailyBalanceChart(lines, data.daysInMonth, zero = true, introProgress(animate), Modifier.fillMaxWidth().height(plotHeight()), focusedId = focus.rootId, onFocus = focus::toggle)
    AccountLegend(lines, focus.rootId, focus::toggle)
}

/**
 * Rows of [rows] with a cell per month of [months]: tapping one names it in the line below.
 * [tail] is the column after the cells ([tailTitle] over it).
 */
@Composable
private fun MonthGrid(
    rows: List<GridRow>,
    months: List<Int>,
    cellColor: (GridCell) -> Color,
    cellText: ((GridCell) -> String)?,
    tailTitle: String,
    tail: (GridRow) -> String,
    detail: (GridRow, Int, GridCell?) -> String,
    pick: CellPick,
) {
    // Month numbers, so six and twelve columns read alike.
    val labels = remember(months) { months.map { (it % 100).toString() } }
    val current = remember { MonthKey.current() }
    val nowBorder = MaterialTheme.colorScheme.onSurface
    val nameWidth = 84.dp
    val tailWidth = 40.dp
    val rowHeight = gridRowHeight()
    val empty = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
    val headerHeight = lineOf(MaterialTheme.typography.labelSmall)
    // Full screen shows more rows: all the room but the header, the detail line and their gaps.
    val rowsBox = listHeight(rowHeight * GRID_ROWS_SHOWN, reserve = headerHeight + GapDp + GapDp + lineOf(MaterialTheme.typography.bodySmall))
    // The header and the rows in one box, so the current month's cells get one border over the column.
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(GapDp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(nameWidth))
                labels.forEach {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(tailTitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.width(tailWidth))
            }
            Column(modifier = Modifier.fillMaxWidth().height(rowsBox).verticalScroll(rememberScrollState())) {
                rows.forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth().height(rowHeight), verticalAlignment = Alignment.CenterVertically) {
                        Row(modifier = Modifier.width(nameWidth), verticalAlignment = Alignment.CenterVertically) {
                            IconView(iconId = row.ref.iconId, size = 18.dp, color = row.ref.color, circle = false)
                            Spacer(Modifier.width(4.dp))
                            Text(row.ref.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 4.dp))
                        }
                        row.cells.forEachIndexed { i, cell ->
                            val selected = pick.row == row.ref.id && pick.column == i
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(1.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (cell == null) empty else cellColor(cell))
                                    .then(if (selected) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)) else Modifier)
                                    .clickable { pick.toggle(row.ref.id, i) },
                                contentAlignment = Alignment.Center,
                            ) {
                                // Twelve columns leave no room for a percentage either: the colour tells it.
                                if (cell != null && cellText != null && months.size <= 6) {
                                    Text(cellText(cell), style = MaterialTheme.typography.labelSmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                        Text(tail(row), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.width(tailWidth))
                    }
                }
            }
        }
        val column = months.indexOf(current)
        if (column >= 0 && rows.isNotEmpty()) {
            // The cells only, not the month number over them; down to the last row shown, not the
            // empty space under a short list.
            val height = minOf(rowHeight * rows.size, rowsBox)
            Row(modifier = Modifier.fillMaxWidth().padding(top = headerHeight + GapDp).height(height)) {
                Spacer(Modifier.width(nameWidth))
                months.indices.forEach { i ->
                    val edge = if (i == column) Modifier.border(NowStroke, nowBorder, RoundedCornerShape(5.dp)) else Modifier
                    Box(Modifier.weight(1f).fillMaxHeight().then(edge))
                }
                Spacer(Modifier.width(tailWidth))
            }
        }
    }
    val row = rows.firstOrNull { it.ref.id == pick.row }
    DetailLine(if (row != null && pick.column in row.cells.indices) detail(row, pick.column, row.cells[pick.column]) else stringResource(R.string.analysis_tap_hint))
}

/** "Sep 2026" for a grid's detail line. */
@Composable
private fun monthYear(month: Int): String = stringArrayResource(R.array.month_abbrev)[(month % 100) - 1] + " " + (month / 100)

@Composable
private fun SavingHistoryCard(card: ChartCard, data: SavingsInsightUi) {
    if (data.history.isEmpty()) return Empty(stringResource(R.string.analysis_no_targets), listHeight(insightContentHeight(ChartType.SAVING_HISTORY)))
    val pick = remember(data.month, card.id) { CellPick() }
    val noTarget = stringResource(R.string.analysis_no_target_month)
    val names = stringArrayResource(R.array.month_abbrev)
    val gridDetail = stringResource(R.string.analysis_grid_detail)
    val unitNames = units()
    MonthGrid(
        data.history,
        data.historyMonths,
        cellColor = { c -> if (c.ratio >= 1f) IncomeGreen.copy(alpha = 0.85f) else IncomeGreen.copy(alpha = 0.1f + 0.5f * c.ratio) },
        cellText = null,
        tailTitle = stringResource(R.string.analysis_streak),
        tail = { "${it.streak}" },
        detail = { row, i, cell ->
            val m = data.historyMonths[i]
            val month = names[(m % 100) - 1] + " " + (m / 100)
            if (cell == null) String.format(noTarget, month, row.ref.name)
            else String.format(gridDetail, month, row.ref.name, compactAmount(cell.value, unitNames), compactAmount(cell.goal, unitNames), Math.round(cell.ratio * 100))
        },
        pick = pick,
    )
}

@Composable
private fun SavingRateCard(data: SavingsInsightUi, animate: Boolean) {
    if (data.saved.all { it == 0L }) return Empty(height = listHeight(BalanceHeight))
    // In basis points, so the line keeps two decimals of a percent as a Long.
    val rates = remember(data) { data.saved.zip(data.income) { s, i -> if (i > 0) s * 10_000 / i else 0L } }
    val labels = monthLabels(data.rateMonths)
    val unitNames = units()
    val rateDetail = stringResource(R.string.analysis_rate_detail)
    val barColor = IncomeGreen.copy(alpha = 0.6f)
    val lineColor = MaterialTheme.colorScheme.primary
    BarLineChart(
        labels,
        data.saved,
        rates,
        barColor,
        lineColor,
        barText = { if (it == 0L) "" else compactAmount(it, unitNames) },
        describe = { i ->
            listOf(
                labels[i] + ": " + String.format(rateDetail, compactAmount(data.saved[i], unitNames), compactAmount(data.income[i], unitNames)),
                if (data.income[i] > 0) "${Math.round(rates[i] / 100.0)}%" else "—",
            )
        },
        progress = introProgress(animate),
        modifier = Modifier.fillMaxWidth().height(plotHeight()),
    )
    Legend(barColor to stringResource(R.string.analysis_legend_saved), lineColor to stringResource(R.string.analysis_legend_rate))
}

@Composable
private fun stepLabel(kind: StepKind): String = stringResource(
    when (kind) {
        StepKind.OPENING -> R.string.analysis_step_opening
        StepKind.INCOME -> R.string.analysis_step_income
        StepKind.TRANSFER_IN -> R.string.analysis_step_transfer_in
        StepKind.LEFTOVER -> R.string.analysis_step_leftover
        StepKind.NOT_COUNTED -> R.string.analysis_step_not_counted
        StepKind.SPENT -> R.string.analysis_step_spent
        StepKind.TRANSFER_OUT -> R.string.analysis_step_transfer_out
        StepKind.ADJUST -> R.string.analysis_step_adjust
        StepKind.CLOSING -> R.string.analysis_step_closing
        StepKind.LIMIT -> R.string.analysis_step_limit
        StepKind.CHILD_LIMITS -> R.string.analysis_step_child_limits
        StepKind.CARRY -> R.string.analysis_step_carry
        StepKind.FUNDED -> R.string.analysis_step_funded
        StepKind.EFFECTIVE -> R.string.analysis_step_effective
        StepKind.LEFT -> R.string.analysis_step_left
    },
)

/** Every step's name, read up front: the chart measures them outside composition. */
@Composable
private fun stepLabels(): Map<StepKind, String> = StepKind.entries.associateWith { stepLabel(it) }

@Composable
private fun Waterfall(steps: List<WaterfallStep>, color: Int, rows: Int, animate: Boolean, yearly: Boolean = false) {
    val names = stepLabels().let { labels ->
        if (!yearly) labels
        else labels + mapOf(StepKind.OPENING to stringResource(R.string.analysis_step_year_opening), StepKind.CLOSING to stringResource(R.string.analysis_step_year_closing))
    }
    WaterfallChart(
        steps,
        label = { names.getValue(it) },
        amount = { if (it.total) Money.groupThousands(it.amount) else Money.formatSignedNoCurrency(it.amount) },
        totalColor = Color(color),
        progress = introProgress(animate),
        modifier = Modifier.fillMaxWidth().height(WaterfallRowHeight * rows),
    )
}

@Composable
private fun SavingSourcesCard(card: ChartCard, data: SavingsInsightUi, animate: Boolean) {
    val id = picked(card, data.sourceAccounts.map { it.id })
    val ref = data.sourceAccounts.firstOrNull { it.id == id }
    val steps = ref?.let { data.sources[it.id] } ?: return Empty(stringResource(R.string.analysis_no_savings), listHeight(WaterfallRowHeight * SAVING_STEPS))
    // Nothing in, nothing out, nothing held: no story to tell.
    if (steps.all { it.amount == 0L }) return Empty(height = listHeight(WaterfallRowHeight * SAVING_STEPS))
    Waterfall(steps, ref.color, SAVING_STEPS, animate, yearly = card.type.base != null)
}

// ================================================================= BUDGETS

@Composable
private fun BudgetForecastCard(data: BudgetsInsightUi, animate: Boolean) {
    val height = listHeight(bulletRowHeight() * BULLET_ROWS_SHOWN)
    if (data.forecast.isEmpty()) return Empty(stringResource(R.string.analysis_no_budgets), height)
    Column(modifier = Modifier.fillMaxWidth().height(height).verticalScroll(rememberScrollState())) {
        data.forecast.forEach { row ->
            val over = row.spent > row.limit
            val heading = data.live && row.projected > row.limit
            val color = when {
                over -> ExpenseRed
                heading -> BudgetWarningYellow
                else -> IncomeGreen
            }
            val caption = when {
                over -> stringResource(R.string.analysis_over_by, compact(row.spent - row.limit))
                heading -> stringResource(R.string.analysis_heading_over, compact(row.projected), compact(row.projected - row.limit))
                data.live -> stringResource(R.string.analysis_on_track, compact(row.limit - row.spent))
                else -> stringResource(R.string.analysis_left, compact(row.limit - row.spent))
            }
            BulletRowItem(row.ref, row.spent, row.limit, if (data.live) row.pace else 0L, row.projected, color, caption, color, animate)
        }
    }
}

@Composable
private fun BudgetBurndownCard(card: ChartCard, data: BudgetsInsightUi, animate: Boolean) {
    val id = picked(card, data.forecast.map { it.ref.id })
    val row = data.forecast.firstOrNull { it.ref.id == id } ?: return Empty(stringResource(R.string.analysis_no_budgets), listHeight(BalanceHeight))
    val days = data.daily[row.ref.id] ?: return Empty(height = listHeight(BalanceHeight))
    val left = days.left
    val rate = if (data.elapsed > 0) row.spent / data.elapsed else 0L
    val end = (left.lastOrNull() ?: 0L) - rate * (data.daysInMonth - left.size)
    val lines = pacedLines(row.ref, left, data.daysInMonth, data.live, { d -> days.limit * (data.daysInMonth - d) / data.daysInMonth }, end)
    val focus = remember { AnalysisSelection() }
    DailyBalanceChart(lines, data.daysInMonth, zero = true, introProgress(animate), Modifier.fillMaxWidth().height(plotHeight()), focusedId = focus.rootId, onFocus = focus::toggle)
    AccountLegend(lines, focus.rootId, focus::toggle)
}

@Composable
private fun BudgetHistoryCard(card: ChartCard, data: BudgetsInsightUi) {
    if (data.history.isEmpty()) return Empty(stringResource(R.string.analysis_no_budgets), listHeight(insightContentHeight(ChartType.BUDGET_HISTORY)))
    val pick = remember(data.month, card.id) { CellPick() }
    val names = stringArrayResource(R.array.month_abbrev)
    val gridDetail = stringResource(R.string.analysis_grid_detail)
    val off = stringResource(R.string.analysis_budget_off_month)
    val unitNames = units()
    MonthGrid(
        data.history,
        data.historyMonths,
        cellColor = { c ->
            when {
                c.ratio > 1f -> ExpenseRed.copy(alpha = 0.55f)
                c.ratio > 0.8f -> BudgetWarningYellow.copy(alpha = 0.55f)
                else -> IncomeGreen.copy(alpha = 0.15f + 0.4f * c.ratio)
            }
        },
        cellText = { "${Math.round(it.ratio.coerceAtMost(9.99f) * 100)}%" },
        tailTitle = stringResource(R.string.analysis_over_count),
        tail = { "${it.misses}" },
        detail = { row, i, cell ->
            val m = data.historyMonths[i]
            val month = names[(m % 100) - 1] + " " + (m / 100)
            if (cell == null) String.format(off, month, row.ref.name)
            else String.format(gridDetail, month, row.ref.name, compactAmount(cell.value, unitNames), compactAmount(cell.goal, unitNames), Math.round(cell.ratio * 100))
        },
        pick = pick,
    )
}

@Composable
private fun BudgetWaterfallCard(card: ChartCard, data: BudgetsInsightUi, animate: Boolean) {
    val id = picked(card, data.forecast.map { it.ref.id })
    val row = data.forecast.firstOrNull { it.ref.id == id }
    val steps = row?.let { data.waterfall[it.ref.id] } ?: return Empty(stringResource(R.string.analysis_no_budgets), listHeight(WaterfallRowHeight * BUDGET_STEPS))
    Waterfall(steps, row.ref.color, BUDGET_STEPS, animate)
}

@Composable
private fun BudgetCoverageCard(data: BudgetsInsightUi, animate: Boolean, yearly: Boolean) {
    if (data.coverage.all { line -> line.values.all { it == 0L } }) return Empty(height = listHeight(BalanceHeight))
    val inside = MaterialTheme.colorScheme.primary
    val outside = BudgetWarningYellow
    val exempt = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val names = listOf(
        stringResource(R.string.analysis_cover_in),
        stringResource(R.string.analysis_cover_out),
        stringResource(R.string.analysis_cover_exempt),
    )
    val colors = listOf(inside, outside, exempt).map { it.toArgb() }
    val lines = remember(data.coverage, names, colors) { data.coverage.mapIndexed { i, l -> l.copy(name = names[i], color = colors[i]) } }
    StackedBalanceBars(lines, monthLabels(data.historyMonths), introProgress(animate), Modifier.fillMaxWidth().height(plotHeight()))
    AccountLegend(lines)
    // The year page's line covers the whole year, the month page's the month.
    val inBudget = if (yearly) lines[0].values.sum() else lines[0].values.last()
    val all = lines.sumOf { if (yearly) it.values.sum() else it.values.last() }
    DetailLine(stringResource(if (yearly) R.string.analysis_cover_share_year else R.string.analysis_cover_share, percent(inBudget, all)))
}

@Composable
private fun BudgetReachedCard(data: BudgetsInsightUi) {
    val height = listHeight(insightContentHeight(ChartType.BUDGET_REACHED))
    if (data.reached.isEmpty()) return Empty(stringResource(R.string.analysis_no_budgets), height)
    val nameWidth = 84.dp
    val tailWidth = 40.dp
    val rowHeight = gridRowHeight()
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(nameWidth))
        DayDotAxis(31, Modifier.weight(1f).height(lineOf(MaterialTheme.typography.labelSmall)))
        Text(stringResource(R.string.analysis_reached_average), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.width(tailWidth))
    }
    Column(modifier = Modifier.fillMaxWidth().height(listHeight(rowHeight * GRID_ROWS_SHOWN, reserve = lineOf(MaterialTheme.typography.labelSmall) + GapDp)).verticalScroll(rememberScrollState())) {
        data.reached.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth().height(rowHeight), verticalAlignment = Alignment.CenterVertically) {
                Row(modifier = Modifier.width(nameWidth), verticalAlignment = Alignment.CenterVertically) {
                    IconView(iconId = row.ref.iconId, size = 18.dp, color = row.ref.color, circle = false)
                    Spacer(Modifier.width(4.dp))
                    Text(row.ref.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 4.dp))
                }
                DayDotStrip(row.days, 31, ExpenseRed, Modifier.weight(1f).fillMaxHeight())
                Text(
                    row.average?.let { stringResource(R.string.analysis_reached_day, it) } ?: "—",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.width(tailWidth),
                )
            }
        }
    }
}
