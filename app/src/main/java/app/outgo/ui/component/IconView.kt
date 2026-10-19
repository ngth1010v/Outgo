package app.outgo.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.outgo.R
import app.outgo.ui.LocalAppContainer

/**
 * Renders an icon by id, whether it's a builtin PNG in assets or a
 * user-imported PNG stored as a BLOB — callers never need to know which.
 *
 * When [color] (a packed ARGB int, e.g. a category/account's `color` column)
 * is given, the icon glyph is tinted with it and its circular background is
 * a lightened ("blanker") tint of the same color, instead of the neutral
 * default. Built-in icons are monochrome silhouettes, so a flat tint reads
 * correctly; imported icons fall back untouched via the null branch.
 * Without [circle] only the glyph is drawn, filling [size] (e.g. a chart legend).
 */
@Composable
fun IconView(iconId: Long?, modifier: Modifier = Modifier, size: Dp = 28.dp, color: Int? = null, circle: Boolean = true) {
    val container = LocalAppContainer.current
    // Seeded from the cache so an already-decoded icon paints in the first frame, instead of a
    // placeholder frame then a swap in the middle of a screen's slide-in.
    var bitmap by remember(iconId) { mutableStateOf(container.iconStore.cached(iconId)) }

    LaunchedEffect(iconId) {
        if (bitmap == null) bitmap = container.iconStore.bitmapFor(iconId)
    }

    val tint = color?.let { Color(it) }
    val background = tint?.lighten() ?: MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = modifier
            .size(size)
            .then(if (circle) Modifier.background(background, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        val current = bitmap
        if (current != null) {
            Image(
                bitmap = current,
                contentDescription = null,
                colorFilter = tint?.let { ColorFilter.tint(it) },
                modifier = if (circle) Modifier.padding(size * 0.16f) else Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ph_image),
                contentDescription = null,
                tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(size * 0.28f),
            )
        }
    }
}

/**
 * The "parent budget exception" badge on a sub-category's icon of [iconSize], in the Box around
 * that icon: hung off its bottom-right corner like the budget badge on a History transfer.
 */
@Composable
fun BoxScope.ExceptionBadge(iconSize: Dp) {
    val size = iconSize * 0.616f
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .offset(x = iconSize * 0.125f, y = iconSize * 0.205f)
            .shadow(2.dp, CircleShape)
            .size(size)
            .background(MaterialTheme.colorScheme.surface, CircleShape),
    ) {
        Icon(
            painter = painterResource(R.drawable.ph_prohibit),
            contentDescription = stringResource(R.string.category_parent_budget_exception),
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(size * 0.8f),
        )
    }
}

/** Lightens a color toward white — used for the "blanker" background behind a tinted icon. */
internal fun Color.lighten(fraction: Float = 0.75f): Color = lerp(this, Color.White, fraction)

/** Same rendering, but by builtin asset key directly — used by the icon picker gallery before a DB row exists. */
@Composable
fun BuiltinIconImage(assetKey: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val container = LocalAppContainer.current
    var bitmap by remember(assetKey) { mutableStateOf(container.iconStore.cachedAsset(assetKey)) }

    LaunchedEffect(assetKey) {
        if (bitmap == null) bitmap = container.iconStore.bitmapForAsset(assetKey)
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
