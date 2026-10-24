package app.outgo.ui.analysis

import androidx.compose.foundation.layout.Arrangement
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
import app.outgo.ui.component.PickCell

/** One icon cell of [ChartOptionsSheet]: an account, category, savings account or budget. */
@Immutable
data class OptionItem(val id: Long, val name: String, val iconId: Long?, val color: Int)

/**
 * What a chart's settings sheet offers: [pick] to choose one of ([pickedId] is ringed) under
 * [pickTitle], and [shown] to switch on and off. Either list may be empty, and is then left out.
 */
@Immutable
data class ChartOptions(val pickTitle: String, val pick: List<OptionItem>, val pickedId: Long?, val shown: List<OptionItem>)

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
                PickCell(item.name, item.iconId, item.color, selected(item.id), { onClick(item.id) }, Modifier.weight(1f), wrap = true)
            }
            repeat(CellsPerRow - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}
