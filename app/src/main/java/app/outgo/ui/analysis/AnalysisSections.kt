package app.outgo.ui.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.fillMaxHeight
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.outgo.R
import app.outgo.domain.CategoryKind
import app.outgo.ui.component.IconView
import app.outgo.ui.theme.ExpenseRed
import app.outgo.ui.theme.IncomeGreen
import app.outgo.util.Money
import java.util.Locale

/**
 * The Analysis sections of the month page. Each one has a fixed height for its chart area (longer
 * lists scroll inside their box), so the skeleton, the empty state and the loaded state are all
 * exactly as tall — nothing on the page moves when data arrives, and every page of a pager lays its
 * cards out identically. The whole card's height is [chartHeight].
 */

// Heights are shared by the real content, the empty state and the skeleton.
val BalanceHeight = 160.dp

/**
 * The height under a card's title row. Depends only on the chart and its own chip, never on the
 * data, so a chart sits at the same place on every page and a pager switch keeps the scroll
 * position meaningful. Text lines go through the font scale.
 */
@Composable
fun chartContentHeight(type: ChartType): Dp {
    val typography = MaterialTheme.typography
    val density = LocalDensity.current
    fun line(style: androidx.compose.ui.text.TextStyle): Dp = with(density) { style.lineHeight.toDp() }
    val small = line(typography.bodySmall)
    fun summary(smallLines: Int) =
        line(typography.headlineMedium) + line(typography.bodyMedium) + small * smallLines + SummaryLineGap * (smallLines + 1)
    // A year chart is drawn like its month chart, at its height.
    return when (type.base ?: type) {
        ChartType.SUMMARY -> summary(MONTH_SUMMARY_SMALL_LINES)
        ChartType.ACCOUNT_DAILY, ChartType.SUBACCOUNT_DAILY, ChartType.ACCOUNT_MONTHS, ChartType.SUBACCOUNT_MONTHS,
        ChartType.EXPENSE_CATEGORY_DAILY, ChartType.EXPENSE_SUBCATEGORY_DAILY, ChartType.EXPENSE_CATEGORY_MONTHS,
        ChartType.EXPENSE_SUBCATEGORY_MONTHS, ChartType.INCOME_CATEGORY_DAILY, ChartType.INCOME_SUBCATEGORY_DAILY,
        ChartType.INCOME_CATEGORY_MONTHS, ChartType.INCOME_SUBCATEGORY_MONTHS,
        ChartType.EXPENSE_CATEGORY_COUNT_DAILY, ChartType.EXPENSE_SUBCATEGORY_COUNT_DAILY,
        ChartType.INCOME_CATEGORY_COUNT_DAILY, ChartType.INCOME_SUBCATEGORY_COUNT_DAILY ->
            BalanceHeight + SectionGap + small
        ChartType.ACCOUNT_SHARE, ChartType.SUBACCOUNT_SHARE, ChartType.EXPENSE_CATEGORY_SHARE, ChartType.EXPENSE_SUBCATEGORY_SHARE,
        ChartType.INCOME_CATEGORY_SHARE, ChartType.INCOME_SUBCATEGORY_SHARE,
        ChartType.EXPENSE_CATEGORY_COUNT_SHARE, ChartType.EXPENSE_SUBCATEGORY_COUNT_SHARE,
        ChartType.INCOME_CATEGORY_COUNT_SHARE, ChartType.INCOME_SUBCATEGORY_COUNT_SHARE -> ShareHeight
        else -> insightContentHeight(type.base ?: type)
    }
}

/** The whole card inside its padding: title row, gap, content. */
@Composable
fun chartHeight(type: ChartType): Dp = SectionTitleHeight + SectionGap + chartContentHeight(type)

private val SummaryLineGap = 2.dp
private const val MONTH_SUMMARY_SMALL_LINES = 2

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

/** The chart a section belongs to: its title row starts with an (i) opening [ChartInfoDialog]. */
val LocalSectionInfo = compositionLocalOf<ChartType?> { null }

/**
 * Opens the card's full-screen view, or (inside it, see [LocalFullscreenChart]) closes it: drawn
 * right after the (i) in the title row.
 */
val LocalSectionExpand = compositionLocalOf<(() -> Unit)?> { null }

/**
 * Set inside a chart's full-screen view: [height] is all the room under its title row. Plots then
 * grow (see [plotHeight]), lists that scroll in a fixed box fill it (see [listHeight]), and a
 * chart's legend turns into one row per line with its value (see [AccountLegend]).
 */
@Immutable
class FullscreenChart(val height: Dp)

val LocalFullscreenChart = compositionLocalOf<FullscreenChart?> { null }

/** A plot's height: [normal] on the card, most of the screen in full screen, leaving room for its legend rows. */
@Composable
fun plotHeight(normal: Dp = BalanceHeight): Dp = LocalFullscreenChart.current?.let { maxOf(normal, it.height * FULLSCREEN_PLOT_SHARE) } ?: normal

/** A scrolling list's box: [normal] on the card; in full screen all the room but [reserve] for the lines around it. */
@Composable
fun listHeight(normal: Dp, reserve: Dp = 0.dp): Dp = LocalFullscreenChart.current?.let { maxOf(normal, it.height - reserve) } ?: normal

private const val FULLSCREEN_PLOT_SHARE = 0.6f
private const val FULLSCREEN_PIE_SHARE = 0.45f

@Composable
private fun SectionIcon(
    icon: Int,
    description: String,
    /** Taken off the box's start, so the glyph sits that much closer to the icon before it. */
    pullStart: Dp = 0.dp,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.CenterEnd,
        modifier = Modifier
            .size(width = SectionTitleHeight - pullStart, height = SectionTitleHeight)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .clickable(onClick = onClick),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            // The same margin on the end as a centred glyph has.
            modifier = Modifier.padding(end = (SectionTitleHeight - SectionIconSize) / 2).size(SectionIconSize),
        )
    }
}

private val SectionIconSize = 16.dp

/** Brings the expand glyph to 7dp from the (i)'s, from the 12dp two centred glyphs leave. */
private val ExpandIconPull = 5.dp

@Composable
fun Section(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val action = LocalSectionAction.current
    val info = LocalSectionInfo.current
    val expand = LocalSectionExpand.current
    val fullscreen = LocalFullscreenChart.current != null
    var showInfo by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SectionGap)) {
        Row(modifier = Modifier.fillMaxWidth().height(SectionTitleHeight), verticalAlignment = Alignment.CenterVertically) {
            if (info != null) {
                SectionIcon(R.drawable.ph_info, stringResource(R.string.analysis_info_open, title)) { showInfo = true }
            }
            if (expand != null) {
                val pull = if (info != null) ExpandIconPull else 0.dp
                if (fullscreen) {
                    SectionIcon(R.drawable.ph_arrows_in_simple, stringResource(R.string.analysis_fullscreen_close), pull, expand)
                } else {
                    SectionIcon(R.drawable.ph_arrows_out_simple, stringResource(R.string.analysis_fullscreen_open, title), pull, expand)
                }
            }
            if (info != null || expand != null) Spacer(Modifier.width(2.dp))
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
    if (showInfo && info != null) ChartInfoDialog(info, title) { showInfo = false }
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

// -------------------------------------------------------------------- summary

@Composable
fun SummarySection(
    summary: SummaryUi,
    mode: AnalysisMode,
    /** The month's transfer total; null until the raw-trade load is in. */
    transfers: Long?,
    modifier: Modifier = Modifier,
    /** The year page's: against last year, averaged per month. */
    yearly: Boolean = false,
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

    val title = stringResource(if (yearly) R.string.analysis_year_summary_title else R.string.analysis_summary_title)
    Section(title, modifier.semantics { contentDescription = description }) {
        Column(verticalArrangement = Arrangement.spacedBy(SummaryLineGap)) {
            Text(
                Money.formatSigned(headline),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = deltaColor(headline, CategoryKind.INCOME),
            )
            // All mode compares the net (what is left), like its headline; the others their own kind.
            val delta = if (mode == AnalysisMode.ALL) summary.net - summary.prevNet else kind.deltaAmount
            val percent = if (mode == AnalysisMode.ALL) percentChange(summary.net, summary.prevNet) else kind.deltaPercent
            Text(
                comparisonText(mode, delta, percent, yearly),
                style = MaterialTheme.typography.bodyMedium,
                color = deltaColor(delta, if (mode == AnalysisMode.ALL) CategoryKind.INCOME else kindType),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            SummaryLine(stringResource(if (yearly) R.string.analysis_avg_per_month else R.string.analysis_avg_per_day, Money.formatSigned(perPeriod)))
            // Transfers arrive with the raw-trade stage; the line keeps its height until then.
            SummaryLine(stringResource(R.string.analysis_transfer_line, transfers?.let { Money.format(it) } ?: "—"))
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

/** "+25%"; "—" with no baseline to compare with (see [percentChange]). */
private fun signedPercent(percent: Int?): String = when {
    percent == null -> "—"
    percent > 0 -> "+$percent%"
    else -> "$percent%"
}

/**
 * The summary's comparison, in words so its sign can't be misread against the headline's: "Spent
 * 50$ more than last month (+25%)". The percent is left out when the period before had nothing.
 */
@Composable
private fun comparisonText(mode: AnalysisMode, delta: Long, percent: Int?, yearly: Boolean): String {
    val period = stringResource(if (yearly) R.string.analysis_cmp_last_year else R.string.analysis_cmp_last_month)
    if (delta == 0L) return stringResource(R.string.analysis_cmp_same, period)
    val more = delta > 0
    val res = when (mode) {
        AnalysisMode.EXPENSE -> if (more) R.string.analysis_cmp_spent_more else R.string.analysis_cmp_spent_less
        AnalysisMode.INCOME -> if (more) R.string.analysis_cmp_earned_more else R.string.analysis_cmp_earned_less
        AnalysisMode.ALL -> if (more) R.string.analysis_cmp_net_more else R.string.analysis_cmp_net_less
    }
    val text = stringResource(res, Money.format(kotlin.math.abs(delta)), period)
    return if (percent == null) text else "$text (${signedPercent(percent)})"
}

// ------------------------------------------------------------- daily lines

@Composable
private fun DailyLines(
    title: String,
    description: String,
    lines: List<BalanceLine>,
    daysInMonth: Int,
    animate: Boolean,
    modifier: Modifier,
    xLabels: List<String>?,
    counts: Boolean,
) {
    Section(title, modifier.semantics { contentDescription = description }) {
        if (lines.isEmpty()) {
            EmptyBox(listHeight(BalanceHeight))
        } else {
            // One focused line per chart, shared by the lines and their legend; a tap off both clears it.
            val focus = remember { AnalysisSelection() }
            DailyBalanceChart(
                lines,
                daysInMonth,
                zero = true,
                introProgress(animate),
                Modifier.fillMaxWidth().height(plotHeight()),
                focusedId = focus.rootId,
                onFocus = focus::toggle,
                xLabels = xLabels,
                counts = counts,
            )
            AccountLegend(lines, focus.rootId, focus::toggle, counts = counts)
        }
    }
}

/**
 * A small icon in the line's colour (a colour block for a line without an icon) and name per line, on a single row that scrolls sideways when there are more
 * accounts than fit — wrapping would make the card's height depend on the account count.
 * With [onFocus], tapping a name hands its line's id over and tapping between names hands null;
 * while [focusedId] names a line, the other names fade.
 */
@Composable
internal fun AccountLegend(
    lines: List<BalanceLine>,
    focusedId: Long? = null,
    onFocus: ((Long?) -> Unit)? = null,
    /** A short stroke in each line's own style (solid, dashed, dotted) in place of the colour block. */
    strokeSamples: Boolean = false,
    /** The values are trade counts, not money: only the full-screen rows show them. */
    counts: Boolean = false,
) {
    val anyFocused = focusedId != null && lines.any { it.accountId == focusedId }
    // Full screen has the room for a row per line with its latest value; a key without values
    // (a colour per kind of bar) stays a single row.
    if (LocalFullscreenChart.current != null && lines.any { it.values.isNotEmpty() }) {
        LegendRows(lines, focusedId, anyFocused, onFocus, strokeSamples, counts)
        return
    }
    val clear = if (onFocus == null) Modifier else Modifier.pointerInput(Unit) { detectTapGestures { onFocus(null) } }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().then(clear)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).widthIn(min = maxWidth),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            lines.forEach { line ->
                val alpha by animateFloatAsState(if (!anyFocused || line.accountId == focusedId) 1f else 0.35f, label = "legendFocus")
                val item = if (onFocus == null) {
                    Modifier
                } else {
                    Modifier.clip(RoundedCornerShape(4.dp)).clickable { onFocus(line.accountId) }.padding(horizontal = 2.dp)
                }
                Row(
                    modifier = item.alpha(alpha),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (line.iconId != null) {
                        // Takes a text line's height so the fixed-height card doesn't clip; the
                        // glyph has a transparent margin, so it is drawn larger, past that slot.
                        IconView(
                            iconId = line.iconId,
                            modifier = Modifier.size(16.dp).wrapContentSize(unbounded = true),
                            size = 18.dp,
                            color = line.color,
                            circle = false,
                        )
                    } else if (strokeSamples) {
                        LegendStroke(line)
                    } else {
                        Box(Modifier.size(10.dp).background(Color(line.color), RoundedCornerShape(2.dp)))
                    }
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

/**
 * The full-screen legend: one row per line, with its icon, name and latest value (the month's end,
 * or today's for a running month), filling the room under the plot and scrolling past it.
 */
@Composable
private fun LegendRows(
    lines: List<BalanceLine>,
    focusedId: Long?,
    anyFocused: Boolean,
    onFocus: ((Long?) -> Unit)?,
    strokeSamples: Boolean,
    counts: Boolean,
) {
    // Under a plot: all the room but the plot and the gap above this list.
    val height = listHeight(0.dp, reserve = plotHeight() + SectionGap)
    Column(modifier = Modifier.fillMaxWidth().height(height).verticalScroll(rememberScrollState())) {
        lines.forEach { line ->
            val alpha by animateFloatAsState(if (!anyFocused || line.accountId == focusedId) 1f else 0.35f, label = "legendFocus")
            val selected = anyFocused && line.accountId == focusedId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(LegendRowHeight)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                    .then(if (onFocus == null) Modifier else Modifier.clickable { onFocus(line.accountId) })
                    .padding(horizontal = 6.dp)
                    .alpha(alpha),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when {
                    line.iconId != null -> IconView(iconId = line.iconId, size = 24.dp, color = line.color)
                    strokeSamples -> LegendStroke(line)
                    else -> Box(Modifier.size(12.dp).background(Color(line.color), RoundedCornerShape(3.dp)))
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    line.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                val value = line.values.lastOrNull()
                if (value != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (counts) stringResource(R.string.analysis_trades_count, value.toInt()) else Money.format(value),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private val LegendRowHeight = 44.dp

// ------------------------------------------------------------ account balances

/**
 * The account balance charts' lines, one per account; all of them may be switched off. The value
 * axis always takes in 0. With [counts] the values are trade counts, not money.
 */
@Composable
fun BalanceDaysSection(
    title: String,
    lines: List<BalanceLine>,
    daysInMonth: Int,
    animate: Boolean,
    modifier: Modifier = Modifier,
    /** A name per point, for a line per month of a year; null labels the days. */
    xLabels: List<String>? = null,
    counts: Boolean = false,
) {
    val description = stringResource(R.string.analysis_cd_daily_lines, title, lines.size)
    DailyLines(title, description, lines, daysInMonth, animate, modifier, xLabels, counts)
}

/** Month-end balances of the last months, stacked per month in the accounts' own order. */
@Composable
fun BalanceMonthsSection(
    title: String,
    lines: List<BalanceLine>,
    months: List<Int>,
    animate: Boolean,
    modifier: Modifier = Modifier,
    /** A bar's name in place of its month's, for the bars of the last years. */
    barLabels: List<String>? = null,
) {
    val names = stringArrayResource(R.array.month_abbrev)
    val labels = barLabels ?: remember(months, names) { months.map { names[(it % 100) - 1] } }
    val description = stringResource(R.string.analysis_cd_daily_lines, title, lines.size)
    Section(title, modifier.semantics { contentDescription = description }) {
        if (lines.isEmpty()) {
            EmptyBox(listHeight(BalanceHeight))
        } else {
            StackedBalanceBars(lines, labels, introProgress(animate), Modifier.fillMaxWidth().height(plotHeight()))
            AccountLegend(lines)
        }
    }
}

/**
 * The balance pie on the left and its rows on the right, in a fixed box that scrolls inside. A
 * slice and its row share one selection: tapping either picks both out, and a slice tapped on the
 * pie scrolls its row into view.
 */
@Composable
fun BalanceShareSection(
    title: String,
    rows: List<ShareRow>,
    selection: AnalysisSelection,
    animate: Boolean,
    /** Which way a change counts as good: a balance or income going up is, an expense going up isn't. */
    kind: Int = CategoryKind.INCOME,
    modifier: Modifier = Modifier,
    /** The rows are trade counts, not money. */
    counts: Boolean = false,
) {
    val description = stringResource(R.string.analysis_cd_daily_lines, title, rows.size)
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val rowPx = with(LocalDensity.current) { ShareRowHeight.toPx() }
    Section(title, modifier.semantics { contentDescription = description }) {
        if (rows.isEmpty()) {
            EmptyBox(listHeight(ShareHeight))
        } else {
            val onSelect: (Long?) -> Unit = { id ->
                selection.toggle(id)
                val index = rows.indexOfFirst { it.id == id }
                if (index >= 0 && selection.rootId == id) scope.launch { scroll.animateScrollTo((index * rowPx).toInt()) }
            }
            val full = LocalFullscreenChart.current
            if (full != null) {
                // Full screen: a big pie on top, its rows filling the room under it.
                val pie = full.height * FULLSCREEN_PIE_SHARE
                Column(modifier = Modifier.fillMaxWidth().height(full.height), verticalArrangement = Arrangement.spacedBy(SectionGap)) {
                    PieChart(rows, { selection.rootId }, onSelect, introProgress(animate), Modifier.fillMaxWidth().height(pie))
                    Column(modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(scroll)) {
                        rows.forEach { ShareRowItem(it, selection, kind, counts) }
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth().height(ShareHeight), verticalAlignment = Alignment.CenterVertically) {
                    PieChart(
                        rows = rows,
                        selectedId = { selection.rootId },
                        onSelect = onSelect,
                        progress = introProgress(animate),
                        // 40% of the row; the pie fits the smaller side and centres in it.
                        modifier = Modifier.fillMaxWidth(0.4f).fillMaxHeight(),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(scroll)) {
                        rows.forEach { ShareRowItem(it, selection, kind, counts) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareRowItem(row: ShareRow, selection: AnalysisSelection, kind: Int, counts: Boolean) {
    // Only the row whose selected-ness actually flips recomposes on a tap.
    val selected by remember(row.id) { derivedStateOf { selection.rootId == row.id } }
    val background by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0f),
        label = "shareRow",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ShareRowHeight)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable { selection.toggle(row.id) }
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconView(iconId = row.iconId, size = 24.dp, color = row.color)
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(row.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                // No share at or below 0: such a balance has no slice.
                row.share?.let { String.format(Locale.US, "%.2f%%", it * 100f) } ?: "—",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(4.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (counts) stringResource(R.string.analysis_trades_count, row.amount.toInt()) else Money.format(row.amount),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            val delta = if (counts) (if (row.delta > 0) "+${row.delta}" else "${row.delta}") else Money.formatSignedNoCurrency(row.delta)
            Text(
                "$delta (${signedPercent(row.deltaPercent)})",
                style = MaterialTheme.typography.labelSmall,
                color = deltaColor(row.delta, kind),
                maxLines = 1,
            )
        }
    }
}

val ShareRowHeight = 44.dp
/** The pie card shows four rows at once; more scroll inside. */
val ShareHeight = ShareRowHeight * 4

/** A legend's stroke sample: the line's colour and style, so lines of one colour still tell apart. */
@Composable
private fun LegendStroke(line: BalanceLine) {
    val color = Color(line.color)
    androidx.compose.foundation.Canvas(Modifier.size(width = 18.dp, height = 10.dp)) {
        val y = size.height / 2f
        val effect = when {
            line.dotted -> androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(0.1f, 3.dp.toPx()))
            line.dashed -> androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))
            else -> null
        }
        drawLine(
            color,
            androidx.compose.ui.geometry.Offset(1.dp.toPx(), y),
            androidx.compose.ui.geometry.Offset(size.width - 1.dp.toPx(), y),
            strokeWidth = 2.dp.toPx(),
            pathEffect = effect,
            cap = if (line.dotted) androidx.compose.ui.graphics.StrokeCap.Round else androidx.compose.ui.graphics.StrokeCap.Butt,
        )
    }
}
