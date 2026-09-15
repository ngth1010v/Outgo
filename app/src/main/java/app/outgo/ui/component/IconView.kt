package app.outgo.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.outgo.R
import app.outgo.ui.LocalAppContainer

/**
 * Renders an icon by id, whether it's a builtin PNG in assets or a
 * user-imported PNG stored as a BLOB — callers never need to know which.
 */
@Composable
fun IconView(iconId: Long?, modifier: Modifier = Modifier, size: Dp = 28.dp) {
    val container = LocalAppContainer.current
    var bitmap by remember(iconId) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(iconId) {
        bitmap = container.iconStore.bitmapFor(iconId)
    }

    Box(
        modifier = modifier
            .size(size)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val current = bitmap
        if (current != null) {
            Image(bitmap = current, contentDescription = null, modifier = Modifier.padding(size * 0.16f))
        } else {
            Icon(
                painter = painterResource(R.drawable.ph_image),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(size * 0.28f),
            )
        }
    }
}

/** Same rendering, but by builtin asset key directly — used by the icon picker gallery before a DB row exists. */
@Composable
fun BuiltinIconImage(assetKey: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val container = LocalAppContainer.current
    var bitmap by remember(assetKey) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(assetKey) {
        bitmap = container.iconStore.bitmapForAsset(assetKey)
    }

    Box(
        modifier = modifier
            .size(size)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.padding(size * 0.16f)) }
    }
}
