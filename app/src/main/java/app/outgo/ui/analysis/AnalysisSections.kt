package app.outgo.ui.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.outgo.R
import app.outgo.domain.CategoryKind
import app.outgo.domain.TradeType
import app.outgo.ui.component.IconView
import app.outgo.ui.theme.ExpenseRed
import app.outgo.ui.theme.IncomeGreen
import app.outgo.ui.theme.TransferBlue
import app.outgo.util.Money
import app.outgo.util.formatDate
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * The Analysis sections, shared by the month page and the year page. Each one has a fixed height
 * for its chart area and a fixed row count for its lists (longer lists scroll inside their box), so
 * the skeleton, the empty state and the loaded state are all exactly as tall — nothing on the page
 * moves when data arrives, and every page of a pager lays its cards out identically. The whole
 * card's height is [chartHeight].
 *
 * Sections that read [AnalysisMode] get it from their own card's chip; the heatmap, weekday and
 * size-mix sections are expense-only and have no chip.
 */

// Heights are shared by the real content, the empty state and the skeleton.
val DonutHeight = 200.dp
val PaceHeight = 140.dp
val BarsHeight = 140.dp
val WeekdayHeight = 140.dp
val YoyHeight = 140.dp
val BalanceHeight = 160.dp
private val RowHeight = 44.dp
private val MoverRowHeight = 36.dp
private val MoverLabelHeight = 22.dp

/**
 * The full row budget, plus both half labels in All mode. Constant for a given mode, so the
 * skeleton, the empty state and the loaded section are the same height.
 */
fun moversContentHeight(mode: AnalysisMode): Dp =
    MoverRowHeight * MOVER_COUNT + if (mode == AnalysisMode.ALL) MoverLabelHeight * 2 else 0.dp
private val BucketRowHeight = 52.dp

/** The donut's breakdown list and the net-flow list show this many rows and scroll for the rest. */
private const val LIST_VISIBLE_ROWS = 5

/**
 * The height under a card's title row. Depends only on the chart and its own chip, never on the
 * data, so a chart sits at the same place on every page and a pager switch keeps the scroll
 * position meaningful. Text lines go through the font scale.
 */
@Composable
fun chartContentHeight(type: ChartType, mode: AnalysisMode): Dp {
    val typography = MaterialTheme.typography
    val density = LocalDensity.current
    fun line(style: androidx.compose.ui.text.TextStyle): Dp = with(density) { style.lineHeight.toDp() }
    val small = line(typography.bodySmall)
    fun summary(smallLines: Int) =
        line(typography.headlineMedium) + line(typography.bodyMedium) + small * smallLines + SummaryLineGap * (smallLines + 1)
    return when (type) {
        ChartType.SUMMARY -> summary(MONTH_SUMMARY_SMALL_LINES)
        ChartType.YEAR_SUMMARY -> summary(YEAR_SUMMARY_SMALL_LINES)
        ChartType.DONUT, ChartType.ACCOUNT_DONUT -> DonutHeight + SectionGap + RowHeight * LIST_VISIBLE_ROWS
        ChartType.PACE -> PaceHeight + SectionGap + small
        ChartType.YOY -> YoyHeight + SectionGap + small
        ChartType.TREND, ChartType.YEAR_BARS -> small + SectionGap + BarsHeight
        ChartType.MOVERS -> moversContentHeight(mode)
        ChartType.HEATMAP -> line(typography.labelSmall) + SectionGap + HeatmapCellSize * HEATMAP_ROWS
        ChartType.WEEKDAY -> WeekdayHeight
        ChartType.BUCKETS -> small + (SectionGap + BucketRowHeight) * BUCKET_COUNT
        ChartType.LARGEST, ChartType.ACCOUNT_LARGEST -> RowHeight * LARGEST_COUNT
        ChartType.TRANSFERS -> small + SectionGap + RowHeight * TRANSFER_PAIR_COUNT
        ChartType.NET_FLOW -> MoverRowHeight * LIST_VISIBLE_ROWS
        ChartType.BALANCE_TREND, ChartType.DAILY_BALANCE -> BalanceHeight + SectionGap + small
    }
}

/** The whole card inside its padding: title row, gap, content. */
@Composable
fun chartHeight(type: ChartType, mode: AnalysisMode): Dp = SectionTitleHeight + SectionGap + chartContentHeight(type, mode)

private val SummaryLineGap = 2.dp
private const val MONTH_SUMMARY_SMALL_LINES = 2
private const val YEAR_SUMMARY_SMALL_LINES = 4
private const val BUCKET_COUNT = 4

/** Which slice/row the user tapped; read through `derivedStateOf` at the call sites. */
@Stable
class AnalysisSelection {
    var rootId by mutableStateOf<Long?>(null)
    fun toggle(id: Long?) {
        rootId = if (id == null || id == rootId) null else id
    }
}

@Composable
fun monthName(monthKey: Int): String = stringArrayResource(R.array.month_full)[(monthKey % 100) - 1]

/**
 * Section titles that name a kind. A title must not say "spending" while the switch is on Income,
 * and the skeleton has to use the same title as the loaded section, so both go through here.
 */
@Composable
fun paceTitle(mode: AnalysisMode): String = stringResource(
    when (mode) {
        AnalysisMode.EXPENSE -> R.string.analysis_pace_title
        AnalysisMode.INCOME -> R.string.analysis_pace_title_income
        AnalysisMode.ALL -> R.string.analysis_pace_title_all
    },
)

@Composable
fun categoryTitle(mode: AnalysisMode): String = stringResource(
    when (mode) {
        AnalysisMode.EXPENSE -> R.string.analysis_by_category_title
        AnalysisMode.INCOME -> R.string.analysis_by_category_income_title
        AnalysisMode.ALL -> R.string.analysis_by_category_all_title
    },
)

@Composable
fun largestTitle(mode: AnalysisMode): String = stringResource(
    when (mode) {
        AnalysisMode.EXPENSE -> R.string.analysis_largest_title
        AnalysisMode.INCOME -> R.string.analysis_largest_income_title
        AnalysisMode.ALL -> R.string.analysis_largest_all_title
    },
)

/** More of a kind is red for expense and green for income; less is the other way round. */
@Composable
internal fun deltaColor(delta: Long, kind: Int = CategoryKind.EXPENSE): Color {
    val up = if (kind == CategoryKind.INCOME) IncomeGreen else ExpenseRed
    val down = if (kind == CategoryKind.INCOME) ExpenseRed else IncomeGreen
    return when {
        delta > 0 -> up
        delta < 0 -> down
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/**
 * Drawn at the end of a section's title row: the card's chips. Provided by the chart list around
 * each card, so the skeleton, the empty state and the loaded section all carry it.
 */
val LocalSectionAction = compositionLocalOf<(@Composable () -> Unit)?> { null }

@Composable
fun Section(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val action = LocalSectionAction.current
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SectionGap)) {
        Row(modifier = Modifier.fillMaxWidth().height(SectionTitleHeight), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            action?.invoke()
        }
        content()
    }
}

/** Fits a chip, so a card with one is as tall as a card without. */
private val SectionTitleHeight = 28.dp
private val SectionGap = 8.dp

/** A compact dropdown in a section's title row: the current choice and a caret. */
@Composable
fun <T> ChoiceChip(label: String, options: List<Pair<T, String>>, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { open = true }
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 120.dp),
            )
            Icon(
                painter = painterResource(R.drawable.ph_caret_down),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 2.dp).size(12.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        open = false
                        onSelect(value)
                    },
                )
            }
        }
    }
}

@Composable
fun modeLabel(mode: AnalysisMode): String = stringResource(
    when (mode) {
        AnalysisMode.EXPENSE -> R.string.trade_expense
        AnalysisMode.INCOME -> R.string.trade_income
        AnalysisMode.ALL -> R.string.analysis_mode_all
    },
)

/** Same height as the loaded section, with a neutral block instead of the chart. */
@Composable
fun SectionSkeleton(title: String, height: Dp, modifier: Modifier = Modifier) {
    Section(title, modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        )
    }
}

@Composable
fun SectionEmpty(title: String, height: Dp, modifier: Modifier = Modifier) {
    Section(title, modifier) { EmptyBox(height) }
}

@Composable
private fun EmptyBox(height: Dp) {
    Box(modifier = Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.analysis_no_data),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Renders [stage]: skeleton while loading, empty state when there is nothing, else [content]. */
@Composable
fun <T> StageSection(
    stage: Stage<T>,
    title: String,
    height: Dp,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    when (stage) {
        is Stage.Loading -> SectionSkeleton(title, height, modifier)
        is Stage.Empty -> SectionEmpty(title, height, modifier)
        is Stage.Ready -> content(stage.data)
    }
}

// ------------------------------------------------------------------ section 1

@Composable
fun SummarySection(
    summary: SummaryUi,
    mode: AnalysisMode,
    transfers: TransfersUi?,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.analysis_summary_title),
    perPeriodFormat: Int = R.string.analysis_avg_per_day,
    /** Extra small lines under the transfer line; always drawn, even blank, so the card keeps its height. */
    footer: List<String> = emptyList(),
) {
    val kind = summary.of(mode)
    val kindType = if (mode == AnalysisMode.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
    // Signed as money flows: expense out (-), income in (+), All mode the net of both.
    val headline = when (mode) {
        AnalysisMode.EXPENSE -> -kind.total
        AnalysisMode.INCOME -> kind.total
        AnalysisMode.ALL -> summary.net
    }
    val perPeriod = when (mode) {
        AnalysisMode.EXPENSE -> -kind.perPeriod
        AnalysisMode.INCOME -> kind.perPeriod
        AnalysisMode.ALL -> summary.income.perPeriod - summary.expense.perPeriod
    }
    val description = summaryDescription(kind, mode, summary)

    Section(title, modifier.semantics { contentDescription = description }) {
        Column(verticalArrangement = Arrangement.spacedBy(SummaryLineGap)) {
            Text(
                Money.formatSigned(headline),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = deltaColor(headline, CategoryKind.INCOME),
            )
            Text(
                changeLabel(kind.deltaAmount, kind.deltaPercent, perPeriodFormat == R.string.analysis_per_month),
                style = MaterialTheme.typography.bodyMedium,
                color = deltaColor(kind.deltaAmount, kindType),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            SummaryLine(stringResource(perPeriodFormat, Money.formatSigned(perPeriod)))
            // Transfers arrive with the raw-trade stage; the line keeps its height until then.
            SummaryLine(stringResource(R.string.analysis_transfer_line, transfers?.total?.let { Money.format(it) } ?: "—"))
            footer.forEach { SummaryLine(it) }
        }
    }
}

@Composable
private fun SummaryLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun summaryDescription(kind: KindSummary, mode: AnalysisMode, summary: SummaryUi): String {
    val total = if (mode == AnalysisMode.ALL) summary.net else kind.total
    if (kind.prevTotal == 0L) {
        return stringResource(R.string.analysis_cd_summary_no_baseline, Money.format(total))
    }
    val direction = if (kind.deltaAmount >= 0) {
        stringResource(R.string.analysis_cd_more, Money.format(kotlin.math.abs(kind.deltaAmount)))
    } else {
        stringResource(R.string.analysis_cd_less, Money.format(kotlin.math.abs(kind.deltaAmount)))
    }
    return stringResource(R.string.analysis_cd_summary, Money.format(total), direction)
}

@Composable
private fun changeLabel(delta: Long, percent: Int, yearly: Boolean): String =
    if (yearly) {
        stringResource(R.string.analysis_vs_last_year, Money.formatSigned(delta))
    } else {
        stringResource(R.string.analysis_vs_last_month_percent, Money.formatSigned(delta), percent)
    }

private fun signedPercent(percent: Int): String = (if (percent > 0) "+" else "") + "$percent%"

// -------------------------------------------------------------- sections 2, 3

/** The donut and its breakdown list in one card: a slice and its row share one selection. */
@Composable
fun DonutSection(
    expense: SliceSet,
    income: SliceSet,
    mode: AnalysisMode,
    selection: AnalysisSelection,
    animate: Boolean,
    modifier: Modifier = Modifier,
    title: String = categoryTitle(mode),
    /** Row amounts in the default text colour rather than green for income, e.g. for balances. */
    plainAmounts: Boolean = false,
) {
    val shown = if (mode == AnalysisMode.INCOME) income else expense
    // All mode's centre shows the net of both rings, signed and coloured like the summary.
    val net = income.donut.total - expense.donut.total
    val prevNet = income.prevTotal - expense.prevTotal
    val centreLabel = if (mode == AnalysisMode.ALL) Money.formatSigned(net) else Money.format(shown.donut.total)
    val centreLabelColor =
        if (mode == AnalysisMode.ALL) deltaColor(net, CategoryKind.INCOME) else MaterialTheme.colorScheme.onSurface
    val centreDelta = if (mode == AnalysisMode.ALL) net - prevNet else shown.deltaAmount
    val centrePercent = if (mode == AnalysisMode.ALL) percentChange(net, prevNet) else shown.deltaPercent
    val centreKind = if (mode == AnalysisMode.EXPENSE) CategoryKind.EXPENSE else CategoryKind.INCOME
    val description = stringResource(
        R.string.analysis_cd_donut,
        centreLabel,
        shown.rows.size,
        Money.format(shown.rows.firstOrNull()?.amount ?: 0L),
    )
    Section(title, modifier.semantics { contentDescription = description }) {
        if (expense.rows.isEmpty() && income.rows.isEmpty()) {
            EmptyBox(DonutHeight)
        } else {
            DonutChart(
                expense = expense.donut,
                income = income.donut,
                mode = mode,
                selectedRootId = { selection.rootId },
                centerLabel = centreLabel,
                centerLabelColor = centreLabelColor,
                centerDelta = signedPercent(centrePercent),
                centerDeltaColor = deltaColor(centreDelta, centreKind),
                onSelect = selection::toggle,
                progress = introProgress(animate),
                modifier = Modifier.fillMaxWidth().height(DonutHeight),
            )
            // All mode lists expense first, then income — the stated priority, not interleaved by amount.
            val rows = when (mode) {
                AnalysisMode.EXPENSE -> expense.rows
                AnalysisMode.INCOME -> income.rows
                AnalysisMode.ALL -> expense.rows + income.rows
            }
            // A fixed box, so the card is as tall with 2 rows as with 14; the rest scroll inside.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(RowHeight * LIST_VISIBLE_ROWS)
                    .verticalScroll(remember(mode) { ScrollState(0) }),
            ) {
                rows.forEach { BreakdownRowItem(it, selection, plainAmounts) }
                NoMoreRows(LIST_VISIBLE_ROWS - rows.size, RowHeight)
            }
        }
    }
}

@Composable
private fun BreakdownRowItem(row: BreakdownRow, selection: AnalysisSelection, plainAmount: Boolean) {
    // Only the row whose selected-ness actually flips recomposes on a tap.
    val selected by remember(row.rootId) {
        derivedStateOf { row.rootId != null && selection.rootId == row.rootId }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .clickable { selection.toggle(row.rootId) }
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (row.rootId == null) {
            ColorDot(Color(row.color), size = 28.dp)
        } else {
            IconView(iconId = row.iconId, size = 28.dp, color = row.color)
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(row.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                // Two decimals: a small category rounded to "0%" said nothing.
                String.format(Locale.US, "%.2f%%", row.sharePercent),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                Money.format(row.amount),
                style = MaterialTheme.typography.bodyMedium,
                color = if (row.kind == CategoryKind.INCOME && !plainAmount) IncomeGreen else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "${Money.formatSignedNoCurrency(row.deltaAmount)} (${signedPercent(row.deltaPercent)})",
                style = MaterialTheme.typography.bodySmall,
                color = deltaColor(row.deltaAmount, row.kind),
            )
        }
    }
}

// ------------------------------------------------------------------ section 4

@Composable
fun PaceSection(pace: PaceUi, mode: AnalysisMode, zero: Boolean, animate: Boolean, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.analysis_cd_pace, Money.format(pace.of(mode).currentTotal))
    Section(paceTitle(mode), modifier.semantics { contentDescription = description }) {
        PaceChart(pace, mode, zero, introProgress(animate), Modifier.fillMaxWidth().height(PaceHeight))
        ChartLegend(
            listOf(
                stringResource(R.string.analysis_this_month) to
                    if (mode == AnalysisMode.ALL) listOf(ExpenseRed, IncomeGreen) else listOf(colorOf(mode)),
                stringResource(R.string.analysis_last_month) to listOf(previousLineColor()),
            ),
            Modifier.fillMaxWidth(),
        )
    }
}

/** The colour the charts draw the previous period's dashed line in. */
@Composable
private fun previousLineColor(): Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)

/** Centred row of legend entries: each label follows its colour boxes. */
@Composable
private fun ChartLegend(items: List<Pair<String, List<Color>>>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { (label, colors) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                colors.forEach { Box(Modifier.size(10.dp).background(it, RoundedCornerShape(2.dp))) }
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

// -------------------------------------------------------------- sections 5, Y2

@Composable
fun BarsSection(
    bars: BarsUi,
    mode: AnalysisMode,
    zero: Boolean,
    title: String,
    onSelectMonth: (Int) -> Unit,
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    val months = stringArrayResource(R.array.month_abbrev)
    val labels = remember(bars.bars, months) { bars.bars.map { months[(it.monthKey % 100) - 1] } }
    val description = stringResource(R.string.analysis_cd_trend, Money.format(bars.averageOf(mode)))
    Section(title, modifier.semantics { contentDescription = description }) {
        Text(
            stringResource(R.string.analysis_average, Money.format(bars.averageOf(mode))),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        MonthBars(
            bars = bars.bars,
            labels = labels,
            mode = mode,
            average = bars.averageOf(mode),
            zero = zero,
            onSelect = onSelectMonth,
            progress = introProgress(animate),
            modifier = Modifier.fillMaxWidth().height(BarsHeight),
        )
    }
}

// ------------------------------------------------------------------ section 6

/**
 * A single-kind mode lists that kind's own top movers; All mode splits the ranking into an expense
 * half and an income half, each half keeping a placeholder row when it has nothing.
 */
@Composable
fun MoversSection(movers: MoversUi, mode: AnalysisMode, animate: Boolean, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.analysis_cd_movers)
    val deltaWidth = rememberDeltaWidth(
        remember(movers, mode) { if (mode == AnalysisMode.ALL) movers.all.expense + movers.all.income else movers.of(mode) },
    )
    Section(stringResource(R.string.analysis_movers_title), modifier.semantics { contentDescription = description }) {
        Column(modifier = Modifier.fillMaxWidth().height(moversContentHeight(mode))) {
            if (mode == AnalysisMode.ALL) {
                MoverHalf(stringResource(R.string.trade_expense), movers.all.expense, deltaWidth, animate)
                MoverHalf(stringResource(R.string.trade_income), movers.all.income, deltaWidth, animate)
                // An empty half already shows its one "no data" row.
                NoMoreRows(MOVER_COUNT - maxOf(movers.all.expense.size, 1) - maxOf(movers.all.income.size, 1), MoverRowHeight)
            } else {
                val rows = movers.of(mode)
                if (rows.isEmpty()) {
                    EmptyBox(MoverRowHeight * MOVER_COUNT)
                } else {
                    rows.forEach { mover -> MoverRowItem(mover, deltaWidth, animate) }
                    NoMoreRows(MOVER_COUNT - rows.size, MoverRowHeight)
                }
            }
        }
    }
}

/** The delta column is as wide as its widest label in every row, so all bars share one zero line. */
@Composable
private fun rememberDeltaWidth(rows: List<MoverRow>): Dp {
    val style = MaterialTheme.typography.bodySmall
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(rows, style, density) {
        with(density) {
            (rows.maxOfOrNull { measurer.measure(Money.formatSignedNoCurrency(it.delta), style).size.width } ?: 0).toDp()
        }
    }
}

@Composable
private fun MoverHalf(label: String, rows: List<MoverRow>, deltaWidth: Dp, animate: Boolean) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.height(MoverLabelHeight),
    )
    if (rows.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(MoverRowHeight),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.analysis_no_data),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        rows.forEach { mover -> MoverRowItem(mover, deltaWidth, animate) }
    }
}

@Composable
/** Fills the [count] rows a short list is missing with one "no data remains" line, so the card keeps its height. */
private fun NoMoreRows(count: Int, rowHeight: Dp) {
    if (count <= 0) return
    Box(modifier = Modifier.fillMaxWidth().height(rowHeight * count), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.analysis_no_more_data),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MoverRowItem(mover: MoverRow, deltaWidth: Dp, animate: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().height(MoverRowHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconView(iconId = mover.iconId, size = 22.dp, color = mover.color)
        Spacer(Modifier.width(8.dp))
        Text(
            mover.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(80.dp),
        )
        MoverBar(
            fraction = mover.fraction,
            increase = mover.delta > 0,
            kindColor = deltaColor(mover.delta, mover.kind),
            progress = introProgress(animate),
            modifier = Modifier.weight(1f).height(MoverRowHeight),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            Money.formatSignedNoCurrency(mover.delta),
            style = MaterialTheme.typography.bodySmall,
            color = deltaColor(mover.delta, mover.kind),
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(deltaWidth),
        )
    }
}

// ------------------------------------------------------------------ section 7

@Composable
fun HeatmapSection(heatmap: HeatmapUi, onDayClick: (Long) -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.analysis_cd_heatmap, Money.format(heatmap.maxAmount))
    Section(stringResource(R.string.analysis_heatmap_title), modifier.semantics { contentDescription = description }) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Monday-first header, localized by java.time rather than by a string array.
            DayOfWeek.entries.forEach { day ->
                Text(
                    day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
            }
        }
        // 6 rows always, so a 5-row month doesn't make the page shorter.
        HeatmapGrid(
            heatmap = heatmap,
            onDayClick = onDayClick,
            modifier = Modifier.fillMaxWidth().height(HeatmapCellSize * HEATMAP_ROWS),
        )
    }
}

private val HeatmapCellSize = 44.dp
private const val HEATMAP_ROWS = 6

// ------------------------------------------------------------------ section 8

@Composable
fun WeekdaySection(bars: List<WeekdayBar>, zero: Boolean, animate: Boolean, modifier: Modifier = Modifier) {
    val labels = remember(bars) {
        bars.map { DayOfWeek.of(it.weekday).getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
    }
    val description = stringResource(R.string.analysis_cd_weekday)
    Section(stringResource(R.string.analysis_weekday_title), modifier.semantics { contentDescription = description }) {
        WeekdayBars(bars, labels, zero, introProgress(animate), Modifier.fillMaxWidth().height(WeekdayHeight))
    }
}

// ------------------------------------------------------------------ section 9

@Composable
fun BucketsSection(buckets: BucketsUi, animate: Boolean, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.analysis_cd_buckets, Money.format(buckets.median))
    Section(stringResource(R.string.analysis_buckets_title), modifier.semantics { contentDescription = description }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.analysis_buckets_median, Money.format(buckets.median)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            ChartLegend(
                listOf(
                    stringResource(R.string.analysis_buckets_count) to listOf(MaterialTheme.colorScheme.primary),
                    stringResource(R.string.analysis_buckets_money) to listOf(ExpenseRed),
                ),
            )
        }
        buckets.buckets.forEach { bucket ->
            Row(
                modifier = Modifier.fillMaxWidth().height(BucketRowHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    bucketLabel(bucket),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(120.dp),
                )
                Spacer(Modifier.width(8.dp))
                BucketBars(bucket, introProgress(animate), Modifier.weight(1f).height(BucketRowHeight * 0.6f))
                Spacer(Modifier.width(8.dp))
                Text(
                    "${bucket.countPercent.toInt()}% / ${bucket.moneyPercent.toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun bucketLabel(bucket: SizeBucket): String = when {
    bucket.from == null -> stringResource(R.string.analysis_bucket_under, Money.format(bucket.until ?: 0L))
    bucket.until == null -> stringResource(R.string.analysis_bucket_over, Money.format(bucket.from))
    else -> stringResource(R.string.analysis_bucket_range, Money.format(bucket.from), Money.format(bucket.until))
}

// ----------------------------------------------------------------- section 10

@Composable
fun LargestSection(
    items: List<LargestItem>,
    mode: AnalysisMode,
    onOpenTrade: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Section(largestTitle(mode), modifier) {
        Box(modifier = Modifier.fillMaxWidth().height(RowHeight * LARGEST_COUNT)) {
            if (items.isEmpty()) {
                EmptyBox(RowHeight * LARGEST_COUNT)
            } else {
                Column {
                    items.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(RowHeight)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onOpenTrade(item.tradeId) }
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconView(iconId = item.iconId, size = 28.dp, color = item.color)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    item.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    listOfNotNull(formatDate(item.occurredAt), item.note).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                Money.format(item.amount),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (item.type == TradeType.INCOME) IncomeGreen else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    NoMoreRows(LARGEST_COUNT - items.size, RowHeight)
                }
            }
        }
    }
}

// ----------------------------------------------------------------- section 11

/** Transfers never join the mode switch: they move money without spending or earning it. */
@Composable
fun TransfersSection(transfers: TransfersUi, animate: Boolean, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.analysis_cd_transfers, Money.format(transfers.total), transfers.count)
    Section(stringResource(R.string.analysis_transfers_title), modifier.semantics { contentDescription = description }) {
        Text(
            stringResource(R.string.analysis_transfers_count, transfers.count, Money.format(transfers.total)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Box(modifier = Modifier.fillMaxWidth().height(RowHeight * TRANSFER_PAIR_COUNT)) {
            if (transfers.pairs.isEmpty()) {
                EmptyBox(RowHeight * TRANSFER_PAIR_COUNT)
            } else {
                Column {
                    transfers.pairs.forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth().height(RowHeight).padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TransferEnds(pair)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.analysis_transfer_pair, pair.fromName, pair.toName),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                TransferBar(
                                    fraction = pair.fraction,
                                    progress = introProgress(animate),
                                    modifier = Modifier.fillMaxWidth().height(6.dp),
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(Money.format(pair.total), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    NoMoreRows(TRANSFER_PAIR_COUNT - transfers.pairs.size, RowHeight)
                }
            }
        }
    }
}

/** Both accounts of a transfer, source on the left, target on the right, arrow between them. */
@Composable
private fun TransferEnds(pair: TransferPair) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconView(iconId = pair.fromIconId, size = TransferIconSize, color = pair.fromColor)
        Icon(
            painter = painterResource(R.drawable.ph_arrow_right),
            contentDescription = null,
            tint = TransferBlue,
            modifier = Modifier.padding(horizontal = 2.dp).size(12.dp),
        )
        IconView(iconId = pair.toIconId, size = TransferIconSize, color = pair.toColor)
    }
}

private val TransferIconSize = 24.dp

// ----------------------------------------------------------------- section Y4

@Composable
fun YoySection(yoy: YoyUi, year: Int, mode: AnalysisMode, zero: Boolean, animate: Boolean, modifier: Modifier = Modifier) {
    val series = yoy.of(mode)
    val description = stringResource(
        R.string.analysis_cd_yoy,
        Money.format(series.currentTotal),
        Money.format(series.previousTotal),
    )
    Section(stringResource(R.string.analysis_year_yoy_title), modifier.semantics { contentDescription = description }) {
        YoyChart(series, year, mode, zero, introProgress(animate), Modifier.fillMaxWidth().height(YoyHeight))
        ChartLegend(
            listOf(
                "$year ${Money.format(series.currentTotal)}" to listOf(colorOf(mode)),
                "${year - 1} ${Money.format(series.previousTotal)}" to listOf(previousLineColor()),
            ),
            Modifier.fillMaxWidth(),
        )
    }
}

// ----------------------------------------------------------------- section Y1

@Composable
fun YearSummarySection(
    year: YearSummaryUi,
    mode: AnalysisMode,
    transfers: TransfersUi?,
    modifier: Modifier = Modifier,
) {
    val months = stringArrayResource(R.array.month_abbrev)
    fun label(monthKey: Int?) = monthKey?.let { months[(it % 100) - 1] }.orEmpty()
    SummarySection(
        summary = year.summary,
        mode = mode,
        transfers = transfers,
        modifier = modifier,
        title = stringResource(R.string.analysis_year_summary_title),
        perPeriodFormat = R.string.analysis_per_month,
        // Both lines are always there, blank when they do not apply, so every year is as tall.
        footer = listOf(
            if (year.biggestMonth != null) {
                stringResource(R.string.analysis_year_biggest, label(year.biggestMonth), Money.format(year.biggestAmount)) +
                    " · " +
                    stringResource(R.string.analysis_year_smallest, label(year.smallestMonth), Money.format(year.smallestAmount))
            } else {
                ""
            },
            if (year.partial) stringResource(R.string.analysis_year_to_date, year.monthsCounted) else "",
        ),
    )
}

// ------------------------------------------------------------------- accounts

@Composable
fun accountTitle(mode: AnalysisMode): String = stringResource(
    when (mode) {
        AnalysisMode.EXPENSE -> R.string.analysis_by_account_title
        AnalysisMode.INCOME -> R.string.analysis_by_account_income_title
        AnalysisMode.ALL -> R.string.analysis_by_account_all_title
    },
)

/** Each account's balance change over the period, transfers and adjustments included. */
@Composable
fun NetFlowSection(rows: List<MoverRow>, animate: Boolean, modifier: Modifier = Modifier) {
    val deltaWidth = rememberDeltaWidth(rows)
    val description = stringResource(R.string.analysis_cd_net_flow)
    Section(stringResource(R.string.analysis_net_flow_title), modifier.semantics { contentDescription = description }) {
        if (rows.isEmpty()) {
            EmptyBox(MoverRowHeight * LIST_VISIBLE_ROWS)
        } else {
            // One row per account: a fixed box that scrolls inside, like the donut's list.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MoverRowHeight * LIST_VISIBLE_ROWS)
                    .verticalScroll(rememberScrollState()),
            ) {
                rows.forEach { MoverRowItem(it, deltaWidth, animate) }
                NoMoreRows(LIST_VISIBLE_ROWS - rows.size, MoverRowHeight)
            }
        }
    }
}

@Composable
fun BalanceTrendSection(balance: BalanceTrendUi, zero: Boolean, animate: Boolean, modifier: Modifier = Modifier) {
    val months = stringArrayResource(R.array.month_abbrev)
    val labels = remember(balance.months, months) { balance.months.map { months[(it % 100) - 1] } }
    val description = stringResource(R.string.analysis_cd_balance, balance.lines.size)
    Section(stringResource(R.string.analysis_balance_title), modifier.semantics { contentDescription = description }) {
        if (balance.lines.isEmpty()) {
            EmptyBox(BalanceHeight)
        } else {
            BalanceChart(balance, labels, zero, introProgress(animate), Modifier.fillMaxWidth().height(BalanceHeight))
            AccountLegend(balance.lines)
        }
    }
}

/** [accountId] picks one account's line; null draws them all. */
@Composable
fun DailyBalanceSection(daily: DailyBalanceUi, accountId: Long?, zero: Boolean, animate: Boolean, modifier: Modifier = Modifier) {
    val lines = remember(daily, accountId) { if (accountId == null) daily.lines else daily.lines.filter { it.accountId == accountId } }
    val description = stringResource(R.string.analysis_cd_daily_balance, lines.size)
    Section(stringResource(R.string.analysis_daily_balance_title), modifier.semantics { contentDescription = description }) {
        if (lines.isEmpty()) {
            EmptyBox(BalanceHeight)
        } else {
            DailyBalanceChart(lines, daily.daysInMonth, zero, introProgress(animate), Modifier.fillMaxWidth().height(BalanceHeight))
            AccountLegend(lines)
        }
    }
}

/**
 * A colour block and name per line, on a single row that scrolls sideways when there are more
 * accounts than fit — wrapping would make the card's height depend on the account count.
 */
@Composable
private fun AccountLegend(lines: List<BalanceLine>) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).widthIn(min = maxWidth),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            lines.forEach { line ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(10.dp).background(Color(line.color), RoundedCornerShape(2.dp)))
                    Text(
                        line.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
