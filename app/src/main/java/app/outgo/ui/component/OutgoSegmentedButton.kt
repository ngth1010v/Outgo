package app.outgo.ui.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * A [SegmentedButton] whose check sits 4dp from its label instead of Material's fixed 8dp. The
 * check's own glyph padding and that nudge leave a selected button's content looking right of
 * centre, so it is drawn 3dp to the left while selected.
 */
@Composable
fun SingleChoiceSegmentedButtonRowScope.OutgoSegmentedButton(
    selected: Boolean,
    onClick: () -> Unit,
    shape: Shape,
    enabled: Boolean = true,
    label: @Composable () -> Unit,
) {
    // Read in the offset lambdas (layout), so the 3dp slide doesn't recompose the button every frame.
    val shift by animateDpAsState(if (selected) (-3).dp else 0.dp, label = "segmentShift")
    SegmentedButton(
        selected = selected,
        onClick = onClick,
        shape = shape,
        enabled = enabled,
        // Disabled borders keep the enabled outline: the selected segment is drawn over its
        // neighbours' edges, and Material's faint disabled border left a gap in the frame there.
        colors = SegmentedButtonDefaults.colors(
            disabledActiveBorderColor = MaterialTheme.colorScheme.outline,
            disabledInactiveBorderColor = MaterialTheme.colorScheme.outline,
        ),
        icon = {
            SegmentedButtonDefaults.Icon(
                active = selected,
                activeContent = { Box(Modifier.offset { IntOffset((4.dp + shift).roundToPx(), 0) }) { SegmentedButtonDefaults.ActiveIcon() } },
            )
        },
        label = { Box(Modifier.offset { IntOffset(shift.roundToPx(), 0) }) { label() } },
    )
}
