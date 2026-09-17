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
                val barWidthTotal = size.width / months.size
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
                    end = Offset(size.width, baselineY),
                    strokeWidth = 1.5f,
                )

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
            Row(modifier = Modifier.fillMaxWidth()) {
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
