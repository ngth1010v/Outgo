package app.outgo.ui.component

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * A page drawn over the whole app, bottom bar included, in the activity's own window: unlike a
 * Dialog it covers the system bars and keeps the app's status bar look. One at a time; the screen
 * that shows it keeps owning its state and its Back handling (see [ShowOverlay]).
 */
@Stable
class OverlayHost {
    internal var content by mutableStateOf<(@Composable () -> Unit)?>(null)

    /** Who handed [content] over: a caller leaving clears only its own page. */
    internal var owner: Any? = null
}

val LocalOverlayHost = staticCompositionLocalOf<OverlayHost?> { null }

/** Draws the host's page, if any, over everything before it; call last in the root's Box. */
@Composable
fun OverlayHostContent(host: OverlayHost) {
    val content = host.content ?: return
    // A Surface, so text inside takes the theme's colour like on any screen.
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxSize()
            // Takes every touch the page itself doesn't, so nothing underneath reacts.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
    ) { content() }
}

/**
 * Shows [content] in the root's overlay while this call stays in composition. [content] is
 * re-handed over on every recomposition here, so it always draws the caller's current state.
 */
@Composable
fun ShowOverlay(content: @Composable () -> Unit) {
    val host = LocalOverlayHost.current ?: return
    val owner = remember { Any() }
    SideEffect {
        host.content = content
        host.owner = owner
    }
    DisposableEffect(host) {
        onDispose { if (host.owner === owner) host.content = null }
    }
}

