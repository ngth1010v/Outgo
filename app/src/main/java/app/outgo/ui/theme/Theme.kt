package app.outgo.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private val LightColors = lightColorScheme(
    primary = md_light_primary,
    onPrimary = md_light_onPrimary,
    primaryContainer = md_light_primaryContainer,
    onPrimaryContainer = md_light_onPrimaryContainer,
    secondary = md_light_secondary,
    background = md_light_background,
    surface = md_light_surface,
    onSurface = md_light_onSurface,
    surfaceVariant = md_light_surfaceVariant,
    error = md_light_error,
)

@Composable
fun OutgoTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colorScheme = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicLightColorScheme(context) else LightColors
    }
    MaterialTheme(colorScheme = colorScheme, typography = OutgoTypography) {
        // Text stops growing at 130%: past that the fixed-width rows (toggles, charts, headers)
        // can't fit it. Up to 130% the system's own (non-linear, API 34+) scaling is kept.
        val density = LocalDensity.current
        if (density.fontScale <= MaxFontScale) content()
        else CompositionLocalProvider(LocalDensity provides Density(density.density, MaxFontScale), content = content)
    }
}

private const val MaxFontScale = 1.3f
