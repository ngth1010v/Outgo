package app.outgo.ui.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.outgo.R
import app.outgo.ui.LocalAppContainer
import app.outgo.ui.component.IconView
import app.outgo.ui.home.StackedDivergingBarChart
import app.outgo.ui.nav.AnalysisKind
import app.outgo.ui.theme.ExpenseRed
import app.outgo.ui.theme.IncomeGreen
import app.outgo.ui.theme.TransferBlue
import app.outgo.util.Money

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(onOpenSub: (AnalysisKind) -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: AnalysisViewModel = viewModel(
        factory = viewModelFactory { initializer { AnalysisViewModel(container.statDao) } },
    )
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(R.string.nav_analysis)) }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    StackedDivergingBarChart(
                        totals = state.chartTotals,
                        months = state.chartMonths,
                        showMonthLabels = true,
                        showLegend = false,
                        chartHeight = null,
                        showYAxis = true,
                        // Wider than the 0.3/0.7 split elsewhere: the axis gutter takes its width
                        // from the chart's own share plus half as much again from the stats column.
                        modifier = Modifier.weight(0.36f).fillMaxHeight(),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(0.64f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        CategoryStatSection(
                            title = stringResource(R.string.analysis_most_income),
                            stats = state.topIncome,
                            reverseColor = false,
                        )
                        CategoryStatSection(
                            title = stringResource(R.string.analysis_most_expense),
                            stats = state.topExpense,
                            reverseColor = true,
                        )
                    }
                }
            }

            item { AnalysisRoutingGrid(onOpenSub) }
        }
    }
}

@Composable
private fun AnalysisRoutingGrid(onOpenSub: (AnalysisKind) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            AnalysisKindButton(
                label = stringResource(R.string.analysis_general),
                iconRes = R.drawable.ph_chart_pie_slice_fill,
                color = MaterialTheme.colorScheme.onSurface,
                onClick = { onOpenSub(AnalysisKind.GENERAL) },
                modifier = Modifier.weight(1f),
            )
            AnalysisKindButton(
                label = stringResource(R.string.trade_income),
                iconRes = R.drawable.ph_trend_up,
                color = IncomeGreen,
                onClick = { onOpenSub(AnalysisKind.INCOME) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            AnalysisKindButton(
                label = stringResource(R.string.trade_expense),
                iconRes = R.drawable.ph_trend_down,
                color = ExpenseRed,
                onClick = { onOpenSub(AnalysisKind.EXPENSE) },
                modifier = Modifier.weight(1f),
            )
            AnalysisKindButton(
                label = stringResource(R.string.trade_transfer),
                iconRes = R.drawable.ph_swap_horizontal,
                color = TransferBlue,
                onClick = { onOpenSub(AnalysisKind.TRANSFER) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CategoryStatSection(title: String, stats: List<CategoryStat?>, reverseColor: Boolean) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        stats.forEach { stat ->
            if (stat != null) CategoryStatRow(stat, reverseColor) else PlaceholderStatRow()
        }
    }
}

private val StatRowHeight = 32.dp

/** Same content/spacing as [CategoryStatRow] (made invisible) so the row is exactly as tall, with a centered "-" on top. */
@Composable
private fun PlaceholderStatRow() {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).alpha(0f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.size(StatRowHeight))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("-", style = MaterialTheme.typography.bodyLarge)
                Text("-", style = MaterialTheme.typography.bodySmall)
            }
            Text("-", fontWeight = FontWeight.Bold)
        }
        Text("-", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CategoryStatRow(stat: CategoryStat, reverseColor: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconView(iconId = stat.iconId, size = StatRowHeight, color = stat.color)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(stat.name, style = MaterialTheme.typography.bodyLarge)
            Text(Money.format(stat.total), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val pct = stat.deviationPercent
        val positiveColor = if (reverseColor) ExpenseRed else IncomeGreen
        val negativeColor = if (reverseColor) IncomeGreen else ExpenseRed
        Text(
            (if (pct > 0) "+" else "") + "$pct%",
            fontWeight = FontWeight.Bold,
            color = when {
                pct > 0 -> positiveColor
                pct < 0 -> negativeColor
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun AnalysisKindButton(
    label: String,
    iconRes: Int,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .aspectRatio(1.4f / (0.75f * 0.7f * 0.9f))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
            .padding(start = 6.dp, end = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .background(color.copy(alpha = 0.15f), androidx.compose.foundation.shape.CircleShape)
                .padding(8.dp),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(label, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisSubScreen(kind: AnalysisKind, onBack: () -> Unit) {
    val title = when (kind) {
        AnalysisKind.GENERAL -> stringResource(R.string.analysis_general)
        AnalysisKind.INCOME -> stringResource(R.string.trade_income)
        AnalysisKind.EXPENSE -> stringResource(R.string.trade_expense)
        AnalysisKind.TRANSFER -> stringResource(R.string.trade_transfer)
    }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ph_caret_right), contentDescription = null, modifier = Modifier.rotate(180f))
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.analysis_placeholder), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
