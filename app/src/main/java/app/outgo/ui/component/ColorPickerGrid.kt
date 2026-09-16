package app.outgo.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.outgo.data.repo.CategoryColorPalette

private const val COLUMNS = 11
private val SwatchShape = RoundedCornerShape(6.dp)

/**
 * 33 swatches (see [CategoryColorPalette]) laid out as 3 rows of 11, for the category/account
 * color pickers. Each row fills the available width (matching the sheet's other inputs) with
 * swatches sized to divide it evenly, rather than a fixed dot size.
 */
@Composable
fun ColorPickerGrid(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CategoryColorPalette.toList().chunked(COLUMNS).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { swatch ->
                    val isSelected = swatch == selected
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .background(Color(swatch), SwatchShape)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                                shape = SwatchShape,
                            )
                            .clickable { onSelect(swatch) },
                    )
                }
            }
        }
    }
}
