package app.outgo.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import app.outgo.R

/**
 * The editor on screen that has unsaved changes, if any: a way out of it other than its own
 * buttons (a bottom-bar tab) hands it what to do once the user agrees to drop them.
 */
class LeaveGuard {
    /** Asks the dirty editor first, then runs the action passed in if the user discards. */
    var ask: ((() -> Unit) -> Unit)? by mutableStateOf(null)
        internal set

    /** Runs [action] now, or once the dirty editor's changes are discarded. */
    fun leave(action: () -> Unit) {
        val guard = ask
        if (guard == null) action() else guard(action)
    }
}

val LocalLeaveGuard = staticCompositionLocalOf { LeaveGuard() }

/**
 * Leaving an editor with unsaved changes asks first. Returns what the editor's Cancel buttons
 * call; the system Back and a bottom-bar tab (through [LocalLeaveGuard]) are caught too, but only
 * while [dirty] (a clean editor just closes).
 */
@Composable
fun rememberDiscardGuard(dirty: Boolean, onLeave: () -> Unit): () -> Unit {
    val leave by rememberUpdatedState(onLeave)
    // What a "Discard" runs: leaving by Cancel/Back, or the tab the user tapped.
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val guard = LocalLeaveGuard.current
    val ask: (() -> Unit) -> Unit = remember { { action -> pending = action } }
    DisposableEffect(dirty, guard) {
        if (dirty) guard.ask = ask
        onDispose { if (guard.ask === ask) guard.ask = null }
    }
    BackHandler(enabled = dirty) { pending = { leave() } }
    pending?.let { action ->
        ConfirmDialog(
            title = stringResource(R.string.discard_title),
            message = stringResource(R.string.discard_message),
            confirmLabel = stringResource(R.string.discard_confirm),
            dismissLabel = stringResource(R.string.discard_keep_editing),
            onConfirm = {
                pending = null
                action()
            },
            onDismiss = { pending = null },
        )
    }
    return { if (dirty) pending = { leave() } else leave() }
}
