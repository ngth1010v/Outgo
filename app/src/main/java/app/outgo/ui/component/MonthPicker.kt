package app.outgo.ui.component

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.outgo.R
import app.outgo.util.MonthKey
import java.util.Locale

/**
 * A borderless "10/2026 ▾" that opens a month grid with a year stepper, for a section header's
 * row: which month an editor's monthly settings are shown and saved for. Any year can be reached.
 */
@Composable
fun MonthPicker(monthKey: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val description = stringResource(R.string.month_picker_label)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClickLabel = description) { open = true }
            .heightIn(min = 28.dp)
            .padding(horizontal = 6.dp),
    ) {
        Text(
            String.format(Locale.US, "%02d/%d", monthKey % 100, monthKey / 100),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Icon(
            painter = painterResource(R.drawable.ph_caret_down),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp).size(12.dp),
        )
    }
    if (open) {
        MonthGridDialog(monthKey, onDismiss = { open = false }) {
            open = false
            onSelect(it)
        }
    }
}

@Composable
private fun MonthGridDialog(selected: Int, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    var year by remember { mutableIntStateOf(selected / 100) }
    val current = MonthKey.current()
    // App strings, not java.time: the device locale can differ from the language chosen in Settings.
    val monthNames = stringArrayResource(R.array.month_abbrev)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { year-- }) {
                    Icon(painterResource(R.drawable.ph_caret_right), stringResource(R.string.month_picker_prev_year), Modifier.size(18.dp).rotate(180f))
                }
                Text(year.toString(), textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                IconButton(onClick = { year++ }) {
                    Icon(painterResource(R.drawable.ph_caret_right), stringResource(R.string.month_picker_next_year), Modifier.size(18.dp))
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..12).chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { m ->
                            val key = year * 100 + m
                            val isSelected = key == selected
                            Surface(
                                onClick = { onSelect(key) },
                                shape = CircleShape,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                // This month is ringed, so it's easy to find again.
                                modifier = Modifier.weight(1f).heightIn(min = 40.dp).let {
                                    if (key == current && !isSelected) it.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape) else it
                                },
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(monthNames[m - 1], style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSelect(current) }) { Text(stringResource(R.string.month_picker_this_month)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
