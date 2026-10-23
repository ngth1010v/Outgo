package app.outgo.ui.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * An on/off input that sits in a form among text fields: [label] on the left, a switch on the
 * right, inside the same outlined box and minimum height as an OutlinedTextField. The whole box
 * toggles it.
 */
@Composable
fun SwitchField(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val shape = OutlinedTextFieldDefaults.shape
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = OutlinedTextFieldDefaults.MinHeight)
            .clip(shape)
            .border(OutlinedTextFieldDefaults.UnfocusedBorderThickness, MaterialTheme.colorScheme.outline, shape)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(start = 16.dp, end = 12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        // The box handles the toggle and its semantics; the switch only shows the state.
        SectionSwitch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/**
 * The editors' on/off switch, drawn at 75% (39x24dp) so a section header isn't taller than its
 * title row; [SwitchField] uses it too, so every switch in an editor is the same size. With an
 * [onCheckedChange], Compose still widens its touch area to the 48dp minimum.
 */
@Composable
fun SectionSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier.size(39.dp, 24.dp).scale(0.75f),
    )
}
