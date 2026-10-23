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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import app.outgo.R
import app.outgo.data.repo.CategoryColorPalette

private const val COLUMNS = 15

/** The palette swatch closest to [color] (by RGB distance). */
private fun nearestSwatch(color: Int): Int = CategoryColorPalette.minBy { swatch ->
    val dr = ((swatch shr 16) and 0xFF) - ((color shr 16) and 0xFF)
    val dg = ((swatch shr 8) and 0xFF) - ((color shr 8) and 0xFF)
    val db = (swatch and 0xFF) - (color and 0xFF)
    dr * dr + dg * dg + db * db
}
private val SwatchShape = RoundedCornerShape(4.dp)

/**
 * 75 square swatches (see [CategoryColorPalette]) laid out as 5 rows of 15, for the
 * category/account color pickers. Each row fills the available width (matching the sheet's other inputs) with
 * swatches sized to divide it evenly, rather than a fixed dot size.
 */
@Composable
fun ColorPickerGrid(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    // A colour from before this palette (older starter data) marks the swatch nearest to it.
    val marked = remember(selected) { if (selected in CategoryColorPalette) selected else nearestSwatch(selected) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CategoryColorPalette.toList().chunked(COLUMNS).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { swatch ->
                    val isSelected = swatch == marked
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
                        contentAlignment = Alignment.Center,
                    ) {
                        // A check as well as the border, so the picked swatch is plain to see on any colour.
                        if (isSelected) {
                            Icon(
                                painterResource(R.drawable.ph_check),
                                contentDescription = null,
                                tint = if (Color(swatch).luminance() > 0.5f) Color.Black else Color.White,
                                modifier = Modifier.fillMaxSize(0.7f),
                            )
                        }
                    }
                }
            }
        }
    }
}
