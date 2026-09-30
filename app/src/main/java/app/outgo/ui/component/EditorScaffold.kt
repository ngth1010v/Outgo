package app.outgo.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.outgo.R

private val CloseButtonSize = 44.dp

/**
 * The full-screen editor shared by the category and account editors: a title, the fields, then
 * Cancel / Save / Delete at the bottom, and a round cancel button fixed at the top right that
 * stays put while the fields scroll under it. [onDelete] null hides Delete (creating, not editing).
 * While [dirty], leaving by Cancel or the system Back asks first; see [rememberDiscardGuard].
 */
@Composable
fun EditorScaffold(
    title: String,
    onCancel: () -> Unit,
    dirty: Boolean,
    onSave: () -> Unit,
    saveEnabled: Boolean,
    onDelete: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cancel = rememberDiscardGuard(dirty, onCancel)
    Scaffold { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            // Scrollable so a field stays reachable above the keyboard.
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp)) {
                // Level with the close button, and clear of it.
                Box(Modifier.fillMaxWidth().height(CloseButtonSize).padding(end = CloseButtonSize + 12.dp), contentAlignment = Alignment.CenterStart) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(16.dp))

                content()

                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = cancel, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.common_cancel)) }
                Spacer(Modifier.height(8.dp))
                Button(onClick = onSave, enabled = saveEnabled, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.common_save))
                }
                if (onDelete != null) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onDelete,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
                }
            }

            Surface(
                onClick = cancel,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(CloseButtonSize),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ph_caret_right),
                        contentDescription = stringResource(R.string.common_cancel),
                        modifier = Modifier.size(20.dp).rotate(180f),
                    )
                }
            }
        }
    }
}
