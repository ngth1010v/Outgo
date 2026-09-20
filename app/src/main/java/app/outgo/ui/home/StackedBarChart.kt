package app.outgo.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.drawText
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.outgo.R
import app.outgo.data.db.dao.MonthCategoryTotal
import app.outgo.domain.CategoryKind
import app.outgo.util.MonthKey

/**
 * Vertical stacked bar chart of the last N months: income categories stack
 * upward from a shared baseline, expense categories stack downward — see
 * architecture.md §7.2. Only parent categories are ever passed in here
 * (the DAO query already sums children into their parent).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StackedDivergingBarChart(
    totals: List<MonthCategoryTotal>,
    months: List<Int>,
    modifier: Modifier = Modifier,
    showMonthLabels: Boolean = true,
    showLegend: Boolean = true,
    /** Fixed chart height, or null to fill whatever vertical space the parent gives it (e.g. a Row sized by IntrinsicSize.Min). */
    chartHeight: androidx.compose.ui.unit.Dp? = 160.dp,
    /** Draws a compact value axis in a left gutter, eating [YAxisWidth] of the chart's width. */
    showYAxis: Boolean = false,
) {
    val byMonth = remember(totals, months) { totals.groupBy { it.monthKey } }

    val rootOrder = remember(totals) {
        totals.groupBy { it.rootId }
            .mapValues { (_, rows) -> rows.sumOf { it.total } }
            .entries.sortedByDescending { it.value }
            .map { it.key }
    }

    val maxIncome = remember(byMonth, months) {
        months.maxOfOrNull { m -> byMonth[m].orEmpty().filter { it.type == CategoryKind.INCOME }.sumOf { it.total } } ?: 0L
    }
    val maxExpense = remember(byMonth, months) {
        months.maxOfOrNull { m -> byMonth[m].orEmpty().filter { it.type == CategoryKind.EXPENSE }.sumOf { it.total } } ?: 0L
    }

    val onSurfaceVariant = MaterialTheme.colorScheme.outlineVariant
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val axisStyle = MaterialTheme.typography.labelSmall
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
    val units = Triple(
        stringResource(R.string.unit_thousand),
        stringResource(R.string.unit_million),
        stringResource(R.string.unit_billion),
    )
    Column(modifier = modifier.fillMaxWidth()) {
        val chartAreaModifier = if (chartHeight != null) Modifier.height(chartHeight) else Modifier.weight(1f)
        if (maxIncome == 0L && maxExpense == 0L) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxWidth().then(chartAreaModifier),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                if (showMonthLabels || showLegend) {
                    Text(
                        stringResource(R.string.home_no_data),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        } else {
            Canvas(modifier = Modifier.fillMaxWidth().then(chartAreaModifier).padding(horizontal = 4.dp)) {
                val axisWidth = if (showYAxis) YAxisWidth.toPx() else 0f
                val plotWidth = size.width - axisWidth
                val barWidthTotal = plotWidth / months.size
                val barPad = barWidthTotal * 0.18f
                val total = (maxIncome + maxExpense).coerceAtLeast(1)
                val baselineY = size.height * (maxIncome.toFloat() / total.toFloat())
                val incomeAreaHeight = baselineY
                val expenseAreaHeight = size.height - baselineY
                val pxPerUnitAbove = if (maxIncome > 0) incomeAreaHeight / maxIncome else 0f
                val pxPerUnitBelow = if (maxExpense > 0) expenseAreaHeight / maxExpense else 0f

                drawLine(
                    color = onSurfaceVariant,
                    start = Offset(0f, baselineY),
                    end = Offset(plotWidth, baselineY),
                    strokeWidth = 1.5f,
                )

                if (showYAxis) {
                    // Both halves share one pixels-per-unit scale, so one step keeps every gap
                    // the same height whichever side of the baseline it falls on.
                    val minPadding = MinTickPadding.toPx()
                    val step = niceStep(maxOf(pxPerUnitAbove, pxPerUnitBelow), minPadding)
                    // 0 (the baseline), the income peak and the expense peak are always labelled;
                    // nice-step ticks fill the gaps, minus any that would crowd a peak label.
                    val ticks = buildList {
                        add(0L to baselineY)
                        if (maxIncome > 0) add(maxIncome to baselineY - maxIncome * pxPerUnitAbove)
                        if (maxExpense > 0) add(maxExpense to baselineY + maxExpense * pxPerUnitBelow)
                        if (step > 0) {
                            ticksUpTo(maxIncome, step)
                                .filter { (maxIncome - it) * pxPerUnitAbove >= minPadding }
                                .forEach { add(it to baselineY - it * pxPerUnitAbove) }
                            ticksUpTo(maxExpense, step)
                                .filter { (maxExpense - it) * pxPerUnitBelow >= minPadding }
                                .forEach { add(it to baselineY + it * pxPerUnitBelow) }
                        }
                    }
                    for ((value, y) in ticks) {
                        val layout = textMeasurer.measure(compactAmount(value, units), axisStyle)
                        drawText(
                            textLayoutResult = layout,
                            color = axisColor,
                            topLeft = Offset(plotWidth + 4.dp.toPx(), y - layout.size.height / 2f),
                        )
                        drawLine(
                            color = onSurfaceVariant,
                            start = Offset(0f, y),
                            end = Offset(plotWidth, y),
                            strokeWidth = 1f,
                        )
                    }
                }

                months.forEachIndexed { index, month ->
                    val colStart = index * barWidthTotal + barPad
                    val colEnd = (index + 1) * barWidthTotal - barPad
                    val entries = byMonth[month].orEmpty()

                    val income = entries.filter { it.type == CategoryKind.INCOME }
                        .sortedBy { rootOrder.indexOf(it.rootId) }
                    var top = baselineY
                    for (e in income) {
                        val h = e.total * pxPerUnitAbove
                        drawRect(
                            color = Color(e.color),
                            topLeft = Offset(colStart, top - h),
                            size = androidx.compose.ui.geometry.Size(colEnd - colStart, h),
                        )
                        top -= h
                    }

                    val expense = entries.filter { it.type == CategoryKind.EXPENSE }
                        .sortedBy { rootOrder.indexOf(it.rootId) }
                    var bottom = baselineY
                    for (e in expense) {
                        val h = e.total * pxPerUnitBelow
                        drawRect(
                            color = Color(e.color),
                            topLeft = Offset(colStart, bottom),
                            size = androidx.compose.ui.geometry.Size(colEnd - colStart, h),
                        )
                        bottom += h
                    }
                }
            }
        }

        if (showMonthLabels) {
            val monthAbbrev = stringArrayResource(R.array.month_abbrev)
            Row(modifier = Modifier.fillMaxWidth().padding(end = if (showYAxis) YAxisWidth else 0.dp)) {
                months.forEach { month ->
                    Text(
                        monthAbbrev.getOrElse((month % 100) - 1) { MonthKey.label(month) },
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (!showLegend) return@Column

        val legend = remember(totals, rootOrder) {
            val byRoot = totals.associateBy { it.rootId }
            rootOrder.mapNotNull { byRoot[it] }
        }
        if (legend.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
            ) {
                legend.forEach { entry ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier.size(10.dp).background(Color(entry.color), RoundedCornerShape(2.dp)),
                        )
                        Text(
                            entry.name,
                            modifier = Modifier.padding(start = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Width of the value-axis gutter on the chart's right: five labelSmall characters plus a 4dp gap. */
private val YAxisWidth = 34.dp

/** Smallest vertical gap between two axis labels, so they never overlap. */
private val MinTickPadding = 48.dp

/** step, 2*step, ... up to [max]. The zero tick sits on the shared baseline and is added separately. */
private fun ticksUpTo(max: Long, step: Long): List<Long> =
    generateSequence(step) { it + step }.takeWhile { it <= max }.toList()

/**
 * Walks the 1/2/5 x 10^k ladder and returns the first step whose on-screen height
 * ([pxPerUnit] pixels per money unit) is at least [minPaddingPx]. 0 when there is no scale.
 */
private fun niceStep(pxPerUnit: Float, minPaddingPx: Float): Long {
    if (pxPerUnit <= 0f) return 0
    val base = longArrayOf(1, 2, 5)
    var step = 1L
    var index = 0
    var power = 0
    while (step * pxPerUnit < minPaddingPx) {
        index++
        if (index == base.size) {
            index = 0
            power++
        }
        // Long overflows past ~9.2e18; no real amount reaches it, but stop rather than wrap.
        if (power > 18) return step
        step = base[index] * pow10(power)
    }
    return step
}

private fun pow10(power: Int): Long {
    var out = 1L
    repeat(power) { out *= 10 }
    return out
}

/** "950", "2.5M", "12M" — never more than 5 characters, always a '.' decimal point, no currency. */
private fun compactAmount(value: Long, units: Triple<String, String, String>): String {
    val (divisor, suffix) = when {
        value >= 1_000_000_000L -> 1_000_000_000.0 to units.third
        value >= 1_000_000L -> 1_000_000.0 to units.second
        value >= 1_000L -> 1_000.0 to units.first
        else -> 1.0 to ""
    }
    val scaled = value / divisor
    // One decimal only while it still fits: "2.5M" is fine, "12.5M" would round to "13M".
    val number = if (scaled < 10.0 && scaled % 1.0 != 0.0) {
        String.format(java.util.Locale.US, "%.1f", scaled)
    } else {
        Math.round(scaled).toString()
    }
    return number + suffix
}
