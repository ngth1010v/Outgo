package app.outgo.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import app.outgo.R

/**
 * Leaving an editor with unsaved changes asks first. Returns what the editor's Cancel buttons
 * call; the system Back is caught too, but only while [dirty] (a clean editor just closes).
 */
@Composable
fun rememberDiscardGuard(dirty: Boolean, onLeave: () -> Unit): () -> Unit {
    var asking by remember { mutableStateOf(false) }
    BackHandler(enabled = dirty) { asking = true }
    if (asking) {
        ConfirmDialog(
            title = stringResource(R.string.discard_title),
            message = stringResource(R.string.discard_message),
            confirmLabel = stringResource(R.string.discard_confirm),
            dismissLabel = stringResource(R.string.discard_keep_editing),
            onConfirm = {
                asking = false
                onLeave()
            },
            onDismiss = { asking = false },
        )
    }
    return { if (dirty) asking = true else onLeave() }
}
