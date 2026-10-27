package app.outgo.ui.analysis

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.outgo.R
import app.outgo.ui.component.OutgoSegmentedButton
import app.outgo.ui.component.PickCell

/**
 * One icon cell of [ChartOptionsSheet]: an account, category, savings account or budget, or (with
 * [icon], a drawable instead of a stored icon) one of a chart's own switches.
 */
@Immutable
data class OptionItem(val id: Long, val name: String, val iconId: Long?, val color: Int, @DrawableRes val icon: Int? = null)

/**
 * What a chart's settings sheet offers: [pick] to choose one of ([pickedId] is ringed) under
 * [pickTitle], and [shown] to switch on and off. Either list may be empty, and is then left out.
 * [metric] (only for [ChartType.hasMetric]) adds the Expense / Income / All switch, which such a
 * chart has here instead of a title-row chip, and the amount / trade count switch.
 */
@Immutable
data class ChartOptions(val pickTitle: String, val pick: List<OptionItem>, val pickedId: Long?, val shown: List<OptionItem>, val metric: Boolean = false)

private const val CellsPerRow = 5

/**
 * A chart's settings as icon cells, like the Trade screen's pickers: tapping a [ChartOptions.pick]
 * cell moves the ring to it, tapping a [ChartOptions.shown] cell rings or unrings it. Changes apply
 * at once; the sheet stays open until dismissed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartOptionsSheet(title: String, card: ChartCard, options: ChartOptions, onChange: (ChartCard) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "title") {
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            if (options.metric) {
                item(key = "mode") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionTitle(stringResource(R.string.analysis_options_kind))
                        val modes = AnalysisMode.entries
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            modes.forEachIndexed { i, mode ->
                                OutgoSegmentedButton(
                                    selected = card.mode == mode,
                                    onClick = { if (card.mode != mode) onChange(card.copy(mode = mode)) },
                                    shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                                ) { Text(modeLabel(mode), maxLines = 1) }
                            }
                        }
                    }
                }
                item(key = "metric") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        SectionTitle(stringResource(R.string.analysis_options_show_by))
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            OutgoSegmentedButton(
                                selected = !card.byCount,
                                onClick = { if (card.byCount) onChange(card.copy(byCount = false)) },
                                shape = SegmentedButtonDefaults.itemShape(0, 2),
                            ) { Text(stringResource(R.string.analysis_show_by_amount), maxLines = 1) }
                            OutgoSegmentedButton(
                                selected = card.byCount,
                                onClick = { if (!card.byCount) onChange(card.copy(byCount = true)) },
                                shape = SegmentedButtonDefaults.itemShape(1, 2),
                            ) { Text(stringResource(R.string.analysis_legend_trades), maxLines = 1) }
                        }
                    }
                }
            }
            if (options.pick.isNotEmpty()) {
                item(key = "pickTitle") { SectionTitle(options.pickTitle) }
                cellRows("pick", options.pick, selected = { it == options.pickedId }) { id ->
                    if (id != card.accountId) onChange(card.copy(accountId = id))
                }
            }
            if (options.shown.isNotEmpty()) {
                val ids = options.shown.map { it.id }
                item(key = "shownTitle") {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        val count = ids.count { it !in card.hidden }
                        SectionTitle(stringResource(R.string.analysis_options_shown, count, ids.size), Modifier.weight(1f))
                        TextButton(onClick = { onChange(card.copy(hidden = card.hidden - ids.toSet())) }) {
                            Text(stringResource(R.string.analysis_accounts_all))
                        }
                        TextButton(onClick = { onChange(card.copy(hidden = (card.hidden + ids).distinct())) }) {
                            Text(stringResource(R.string.analysis_options_none))
                        }
                    }
                }
                cellRows("shown", options.shown, selected = { it !in card.hidden }) { id ->
                    onChange(card.copy(hidden = if (id in card.hidden) card.hidden - id else card.hidden + id))
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, modifier = modifier)
}

/** [items] as rows of [CellsPerRow] icon cells, a ring around each [selected] one. */
private fun LazyListScope.cellRows(key: String, items: List<OptionItem>, selected: (Long) -> Boolean, onClick: (Long) -> Unit) {
    items(items.chunked(CellsPerRow), key = { "$key-${it.first().id}" }) { row ->
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            row.forEach { item ->
                if (item.icon != null) {
                    SwitchCell(item.name, item.icon, Color(item.color), selected(item.id), { onClick(item.id) }, Modifier.weight(1f))
                } else {
                    PickCell(item.name, item.iconId, item.color, selected(item.id), { onClick(item.id) }, Modifier.weight(1f), wrap = true)
                }
            }
            repeat(CellsPerRow - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** A chart switch drawn like a [PickCell]: a drawable on a tinted disc, ringed while on. */
@Composable
private fun SwitchCell(name: String, @DrawableRes icon: Int, color: Color, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.clickable(onClick = onClick).padding(4.dp)) {
        val ring = MaterialTheme.colorScheme.primary
        Box(
            modifier = Modifier
                .size(48.dp)
                .then(
                    if (selected) {
                        Modifier.drawBehind {
                            val width = 2.dp.toPx()
                            drawCircle(ring, radius = (size.minDimension - width) / 2, style = Stroke(width))
                        }
                    } else {
                        Modifier
                    },
                )
                .padding(4.dp)
                .background(color.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        }
        Text(name, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
    }
}
