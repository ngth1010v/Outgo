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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.outgo.R
import app.outgo.ui.component.IconView
import app.outgo.ui.theme.ExpenseRed
import app.outgo.ui.theme.IncomeGreen
import app.outgo.util.Money
import app.outgo.util.formatDate
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * The ten Analysis sections. Each one has a fixed height for its chart area and a fixed row
 * count for its lists, so the skeleton, the empty state and the loaded state are all exactly
 * as tall — nothing on the page moves when data arrives.
 */

// Heights are shared by the real content, the empty state and the skeleton.
val DonutHeight = 200.dp
val PaceHeight = 140.dp
val TrendHeight = 140.dp
val WeekdayHeight = 140.dp
private val RowHeight = 44.dp
private val MoverRowHeight = 36.dp
private val BucketRowHeight = 52.dp

/** Which slice/row the user tapped; read through `derivedStateOf` at the call sites. */
@Stable
class AnalysisSelection {
    var rootId by mutableStateOf<Long?>(null)
    fun toggle(id: Long?) {
        rootId = if (id == null || id == rootId) null else id
    }
}

@Composable
fun monthLabel(monthKey: Int): String {
    val months = stringArrayResource(R.array.month_abbrev)
    return stringResource(R.string.analysis_month_label, months[(monthKey % 100) - 1], monthKey / 100)
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
}

@Composable
fun Section(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(title)
        content()
    }
}

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
    Section(title, modifier) {
        Box(modifier = Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.analysis_no_data),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
fun SummarySection(summary: SummaryUi, modifier: Modifier = Modifier) {
    val changeText = changeLabel(summary.deltaAmount, summary.deltaPercent)
    val description = if (summary.deltaPercent == null && summary.prevTotal == 0L) {
        stringResource(R.string.analysis_cd_summary_no_baseline, Money.format(summary.total))
    } else {
        val direction = if (summary.deltaAmount >= 0) {
            stringResource(R.string.analysis_cd_more, Money.format(kotlin.math.abs(summary.deltaAmount)))
        } else {
            stringResource(R.string.analysis_cd_less, Money.format(kotlin.math.abs(summary.deltaAmount)))
        }
        stringResource(R.string.analysis_cd_summary, Money.format(summary.total), direction)
    }

    Section(stringResource(R.string.analysis_summary_title), modifier.semantics { contentDescription = description }) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(Money.format(summary.total), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                changeText,
                style = MaterialTheme.typography.bodyMedium,
                color = deltaColor(summary.deltaAmount),
            )
            Text(
                stringResource(R.string.analysis_avg_per_day, Money.format(summary.avgPerDay)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(
                    R.string.analysis_income_and_net,
                    Money.format(summary.income),
                    Money.formatSigned(summary.net),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun changeLabel(delta: Long, percent: Int?): String =
    if (percent == null) {
        stringResource(R.string.analysis_vs_last_month, Money.formatSigned(delta))
    } else {
        stringResource(R.string.analysis_vs_last_month_percent, Money.formatSigned(delta), percent)
    }

/** More spending is red, less is green — the opposite of a plain +/- colouring. */
@Composable
private fun deltaColor(delta: Long): Color = when {
    delta > 0 -> ExpenseRed
    delta < 0 -> IncomeGreen
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

// ------------------------------------------------------------------ section 2

@Composable
fun DonutSection(
    set: SliceSet,
    showIncome: Boolean,
    onToggle: (Boolean) -> Unit,
    selection: AnalysisSelection,
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(
        if (showIncome) R.string.analysis_by_category_income_title else R.string.analysis_by_category_title,
    )
    val largest = set.rows.firstOrNull()?.amount ?: 0L
    val description = stringResource(
        R.string.analysis_cd_donut,
        Money.format(set.donut.total),
        set.rows.size,
        Money.format(largest),
    )
    Section(title, modifier.semantics { contentDescription = description }) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !showIncome,
                onClick = { onToggle(false) },
                label = { Text(stringResource(R.string.trade_expense)) },
            )
            FilterChip(
                selected = showIncome,
                onClick = { onToggle(true) },
                label = { Text(stringResource(R.string.trade_income)) },
            )
        }
        if (set.rows.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(DonutHeight), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.analysis_no_data),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            DonutChart(
                donut = set.donut,
                selectedRootId = { selection.rootId },
                centerLabel = Money.format(set.donut.total),
                onSelect = selection::toggle,
                progress = introProgress(animate),
                modifier = Modifier.fillMaxWidth().height(DonutHeight),
            )
        }
    }
}

// ------------------------------------------------------------------ section 3

@Composable
fun BreakdownSection(
    rows: List<BreakdownRow>,
    selection: AnalysisSelection,
    modifier: Modifier = Modifier,
) {
    Section(stringResource(R.string.analysis_breakdown_title), modifier) {
        if (rows.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(RowHeight), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.analysis_no_data),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            rows.forEach { row -> BreakdownRowItem(row, selection) }
        }
    }
}

@Composable
private fun BreakdownRowItem(row: BreakdownRow, selection: AnalysisSelection) {
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
            ColorDot(Color(row.color), size = 28.dp, modifier = Modifier)
        } else {
            IconView(iconId = row.iconId, size = 28.dp, color = row.color)
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(row.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${row.sharePercent.toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Money.format(row.amount), style = MaterialTheme.typography.bodyMedium)
            Text(
                if (row.deltaPercent == null) {
                    Money.formatSignedNoCurrency(row.deltaAmount)
                } else {
                    "${if (row.deltaPercent > 0) "+" else ""}${row.deltaPercent}%"
                },
                style = MaterialTheme.typography.bodySmall,
                color = deltaColor(row.deltaAmount),
            )
        }
    }
}

// ------------------------------------------------------------------ section 4

@Composable
fun PaceSection(pace: PaceUi, animate: Boolean, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.analysis_cd_pace, Money.format(pace.currentTotal))
    Section(stringResource(R.string.analysis_pace_title), modifier.semantics { contentDescription = description }) {
        Text(
            stringResource(R.string.analysis_pace_legend),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PaceChart(pace, introProgress(animate), Modifier.fillMaxWidth().height(PaceHeight))
    }
}

// ------------------------------------------------------------------ section 5

@Composable
fun TrendSection(trend: TrendUi, onSelectMonth: (Int) -> Unit, animate: Boolean, modifier: Modifier = Modifier) {
    val months = stringArrayResource(R.array.month_abbrev)
    val labels = remember(trend.bars, months) { trend.bars.map { months[(it.monthKey % 100) - 1] } }
    val description = stringResource(R.string.analysis_cd_trend, Money.format(trend.average))
    Section(stringResource(R.string.analysis_trend_title), modifier.semantics { contentDescription = description }) {
        Text(
            stringResource(R.string.analysis_average, Money.format(trend.average)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TrendBars(
            bars = trend.bars,
            labels = labels,
            averageFraction = trend.averageFraction,
            onSelect = onSelectMonth,
            progress = introProgress(animate),
            modifier = Modifier.fillMaxWidth().height(TrendHeight),
        )
    }
}

// ------------------------------------------------------------------ section 6

@Composable
fun MoversSection(movers: List<MoverRow>, animate: Boolean, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.analysis_cd_movers)
    Section(stringResource(R.string.analysis_movers_title), modifier.semantics { contentDescription = description }) {
        Box(modifier = Modifier.fillMaxWidth().height(MoverRowHeight * MOVER_COUNT)) {
            if (movers.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.analysis_no_data),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Column {
                    movers.forEach { mover ->
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
                                progress = introProgress(animate),
                                modifier = Modifier.weight(1f).height(MoverRowHeight),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                Money.formatSignedNoCurrency(mover.delta),
                                style = MaterialTheme.typography.bodySmall,
                                color = deltaColor(mover.delta),
                            )
                        }
                    }
                }
            }
        }
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
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
fun WeekdaySection(bars: List<WeekdayBar>, animate: Boolean, modifier: Modifier = Modifier) {
    val labels = remember(bars) {
        bars.map { DayOfWeek.of(it.weekday).getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
    }
    val description = stringResource(R.string.analysis_cd_weekday)
    Section(stringResource(R.string.analysis_weekday_title), modifier.semantics { contentDescription = description }) {
        WeekdayBars(bars, labels, introProgress(animate), Modifier.fillMaxWidth().height(WeekdayHeight))
    }
}

// ------------------------------------------------------------------ section 9

@Composable
fun BucketsSection(buckets: BucketsUi, animate: Boolean, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.analysis_cd_buckets, Money.format(buckets.median))
    Section(stringResource(R.string.analysis_buckets_title), modifier.semantics { contentDescription = description }) {
        Text(
            stringResource(R.string.analysis_buckets_median, Money.format(buckets.median)) +
                " · " + stringResource(R.string.analysis_buckets_legend),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
fun LargestSection(items: List<LargestItem>, onOpenTrade: (Long) -> Unit, modifier: Modifier = Modifier) {
    Section(stringResource(R.string.analysis_largest_title), modifier) {
        Box(modifier = Modifier.fillMaxWidth().height(RowHeight * LARGEST_COUNT)) {
            if (items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.analysis_no_data),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                            Text(Money.format(item.amount), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
