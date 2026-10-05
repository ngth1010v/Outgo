package app.outgo.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.delay

/** How long an editor waits after the last change before saving, so typing "1500" saves once. */
private const val AutoSaveDelayMs = 500L

/**
 * Saves an editor's [value][rememberAutoSave] a moment after it last changed, and on leaving the
 * screen. Its save returns false to skip an invalid value (it is retried on the next change).
 */
class AutoSave<T> internal constructor(saved: T) {
    /** The value last saved (or loaded): what an invalid input goes back to. */
    var saved: T = saved
        private set

    internal var value: T = saved
    internal var save: (T) -> Boolean = { true }
    private var stopped = false

    /** Saves now if the value changed since the last save. */
    fun flush() {
        val v = value
        if (!stopped && v != saved && save(v)) saved = v
    }

    /** Takes [v] as already saved (it was just loaded), so it isn't written back. */
    fun reset(v: T) {
        saved = v
    }

    /** Saves nothing more, e.g. once the item is deleted. */
    fun stop() {
        stopped = true
    }
}

/**
 * Calls [onBlur] each time the field loses focus, e.g. to put an invalid value back to
 * [AutoSave.saved]. The first, unfocused report is skipped.
 */
@Composable
fun Modifier.onBlur(onBlur: () -> Unit): Modifier {
    var focused by remember { mutableStateOf(false) }
    return onFocusChanged {
        if (focused && !it.isFocused) onBlur()
        focused = it.isFocused
    }
}

@Composable
fun <T> rememberAutoSave(value: T, save: (T) -> Boolean): AutoSave<T> {
    val autoSave = remember { AutoSave(value) }
    autoSave.value = value
    autoSave.save = save
    LaunchedEffect(value) {
        delay(AutoSaveDelayMs)
        autoSave.flush()
    }
    DisposableEffect(autoSave) { onDispose { autoSave.flush() } }
    return autoSave
}
