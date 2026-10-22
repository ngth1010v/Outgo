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
        ChartType.INCOME_CATEGORY_MONTHS, ChartType.INCOME_SUBCATEGORY_MONTHS ->
            BalanceHeight + SectionGap + small
        ChartType.ACCOUNT_SHARE, ChartType.SUBACCOUNT_SHARE, ChartType.EXPENSE_CATEGORY_SHARE, ChartType.EXPENSE_SUBCATEGORY_SHARE,
        ChartType.INCOME_CATEGORY_SHARE, ChartType.INCOME_SUBCATEGORY_SHARE -> ShareHeight
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

@Composable
fun Section(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val action = LocalSectionAction.current
    val info = LocalSectionInfo.current
    var showInfo by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SectionGap)) {
        Row(modifier = Modifier.fillMaxWidth().height(SectionTitleHeight), verticalAlignment = Alignment.CenterVertically) {
            if (info != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(SectionTitleHeight).clip(androidx.compose.foundation.shape.CircleShape).clickable { showInfo = true },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ph_info),
                        contentDescription = stringResource(R.string.analysis_info_open, title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(Modifier.width(2.dp))
            }
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

/**
 * A compact dropdown in a section's title row: the current choice and a caret. With [checked] it is
 * a multi-pick: each option shows a tick and the menu stays open while options are toggled.
 */
@Composable
fun <T> ChoiceChip(label: String, options: List<Pair<T, String>>, checked: ((T) -> Boolean)? = null, onSelect: (T) -> Unit) {
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
                        if (checked == null) open = false
                        onSelect(value)
                    },
                    leadingIcon = checked?.let { isChecked ->
                        {
                            Icon(
                                painter = painterResource(R.drawable.ph_check),
                                contentDescription = null,
                                tint = if (isChecked(value)) MaterialTheme.colorScheme.primary else Color.Transparent,
                                modifier = Modifier.size(16.dp),
                            )
                        }
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
            Text(
                stringResource(
                    if (yearly) R.string.analysis_vs_last_year_percent else R.string.analysis_vs_last_month_percent,
                    Money.formatSigned(kind.deltaAmount),
                    kind.deltaPercent,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = deltaColor(kind.deltaAmount, kindType),
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

private fun signedPercent(percent: Int): String = (if (percent > 0) "+" else "") + "$percent%"

// ------------------------------------------------------------- daily lines

@Composable
private fun DailyLines(
    title: String,
    description: String,
    lines: List<BalanceLine>,
    daysInMonth: Int,
    zero: Boolean,
    animate: Boolean,
    modifier: Modifier,
    xLabels: List<String>?,
) {
    Section(title, modifier.semantics { contentDescription = description }) {
        if (lines.isEmpty()) {
            EmptyBox(BalanceHeight)
        } else {
            // One focused line per chart, shared by the lines and their legend; a tap off both clears it.
            val focus = remember { AnalysisSelection() }
            DailyBalanceChart(
                lines,
                daysInMonth,
                zero,
                introProgress(animate),
                Modifier.fillMaxWidth().height(BalanceHeight),
                focusedId = focus.rootId,
                onFocus = focus::toggle,
                xLabels = xLabels,
            )
            AccountLegend(lines, focus.rootId, focus::toggle)
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
internal fun AccountLegend(lines: List<BalanceLine>, focusedId: Long? = null, onFocus: ((Long?) -> Unit)? = null) {
    val anyFocused = focusedId != null && lines.any { it.accountId == focusedId }
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

// ------------------------------------------------------------ account balances

/** The account balance charts' lines, one per account; all of them may be switched off. */
@Composable
fun BalanceDaysSection(
    title: String,
    lines: List<BalanceLine>,
    daysInMonth: Int,
    zero: Boolean,
    animate: Boolean,
    modifier: Modifier = Modifier,
    /** A name per point, for a line per month of a year; null labels the days. */
    xLabels: List<String>? = null,
) {
    val description = stringResource(R.string.analysis_cd_daily_lines, title, lines.size)
    DailyLines(title, description, lines, daysInMonth, zero, animate, modifier, xLabels)
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
            EmptyBox(BalanceHeight)
        } else {
            StackedBalanceBars(lines, labels, introProgress(animate), Modifier.fillMaxWidth().height(BalanceHeight))
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
) {
    val description = stringResource(R.string.analysis_cd_daily_lines, title, rows.size)
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val rowPx = with(LocalDensity.current) { ShareRowHeight.toPx() }
    Section(title, modifier.semantics { contentDescription = description }) {
        if (rows.isEmpty()) {
            EmptyBox(ShareHeight)
        } else {
            Row(modifier = Modifier.fillMaxWidth().height(ShareHeight), verticalAlignment = Alignment.CenterVertically) {
                PieChart(
                    rows = rows,
                    selectedId = { selection.rootId },
                    onSelect = { id ->
                        selection.toggle(id)
                        val index = rows.indexOfFirst { it.id == id }
                        if (index >= 0 && selection.rootId == id) scope.launch { scroll.animateScrollTo((index * rowPx).toInt()) }
                    },
                    progress = introProgress(animate),
                    // 40% of the row; the pie fits the smaller side and centres in it.
                    modifier = Modifier.fillMaxWidth(0.4f).fillMaxHeight(),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(scroll)) {
                    rows.forEach { ShareRowItem(it, selection, kind) }
                }
            }
        }
    }
}

@Composable
private fun ShareRowItem(row: ShareRow, selection: AnalysisSelection, kind: Int) {
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
            Text(Money.format(row.amount), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(
                "${Money.formatSignedNoCurrency(row.delta)} (${signedPercent(row.deltaPercent)})",
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
