package app.outgo.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.outgo.R

/**
 * The full-screen editor shared by the category and account editors: a back arrow and the title
 * at the top left, the fields, then the buttons every editor shares (see [EditorButtons]):
 * Save, Cancel, and Delete set apart at the end. [onDelete] null hides Delete (creating, not
 * editing). Nothing is written until Save; while [dirty], leaving by the arrow, Cancel, the system
 * Back or a bottom-bar tab asks first (see [rememberDiscardGuard]).
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
            // A tap outside the fields drops their focus, so a field's blur check runs.
            val focusManager = LocalFocusManager.current
            Column(
                modifier = Modifier.fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } }
                    .verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
            ) {
                EditorTitle(title, onBack = cancel)
                Spacer(Modifier.height(16.dp))

                content()

                Spacer(Modifier.height(16.dp))
                EditorButtons(onSave = onSave, saveEnabled = saveEnabled, onCancel = cancel, onDelete = onDelete)
            }
        }
    }
}

/**
 * An editor's top row: a back arrow at the left (leaving, like Cancel) and the title. Scrolls with
 * the fields, so it never covers one.
 */
@Composable
fun EditorTitle(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
            Icon(
                painter = painterResource(R.drawable.ph_caret_right),
                contentDescription = stringResource(R.string.common_back),
                modifier = Modifier.size(20.dp).rotate(180f),
            )
        }
        Spacer(Modifier.width(8.dp))
        val style = MaterialTheme.typography.titleMedium
        Text(title, style = style.copy(fontSize = style.fontSize * 1.2f, lineHeight = style.lineHeight * 1.2f))
    }
}

/** Every editor's buttons, in one order: Save (the main one), Cancel, then Delete apart at the end. */
@Composable
fun EditorButtons(onSave: () -> Unit, saveEnabled: Boolean, onCancel: () -> Unit, onDelete: (() -> Unit)?) {
    Button(onClick = onSave, enabled = saveEnabled, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.common_save))
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.common_cancel))
    }
    if (onDelete != null) DeleteButton(onDelete)
}

/** The red Delete, with its bin icon, set apart below an editor's other buttons. */
@Composable
fun DeleteButton(onClick: () -> Unit) {
    Spacer(Modifier.height(24.dp))
    OutlinedButton(
        onClick = onClick,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(painterResource(R.drawable.ph_trash), contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
    }
}
