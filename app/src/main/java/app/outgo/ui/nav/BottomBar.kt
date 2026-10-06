package app.outgo.ui.nav

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.outgo.R

internal data class BottomItem(
    val route: String,
    val labelRes: Int,
    val icon: Int,
    val iconSelected: Int,
)

internal val bottomItems = listOf(
    BottomItem(Routes.HOME, R.string.nav_home, R.drawable.ph_house, R.drawable.ph_house_fill),
    BottomItem(Routes.BALANCE, R.string.nav_balance, R.drawable.ph_wallet, R.drawable.ph_wallet_fill),
    BottomItem(Routes.CATEGORY, R.string.nav_category, R.drawable.ph_squares_four, R.drawable.ph_squares_four_fill),
    BottomItem(Routes.TRADE, R.string.nav_trade, R.drawable.ph_plus_circle, R.drawable.ph_plus_circle_fill),
    BottomItem(Routes.ANALYSIS, R.string.nav_analysis, R.drawable.ph_chart_pie_slice, R.drawable.ph_chart_pie_slice_fill),
    BottomItem(Routes.HISTORY, R.string.nav_history, R.drawable.ph_clock_counter_clockwise, R.drawable.ph_clock_counter_clockwise_fill),
    BottomItem(Routes.SETTING, R.string.nav_setting, R.drawable.ph_gear_six, R.drawable.ph_gear_six_fill),
)

@Composable
fun OutgoBottomBar(selectedRoute: String?, onSelect: (String) -> Unit) {
    // Own items, not NavigationBarItem: its 64x32dp indicator can't be resized.
    Surface(color = NavigationBarDefaults.containerColor, tonalElevation = NavigationBarDefaults.Elevation) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp)
                .windowInsetsPadding(NavigationBarDefaults.windowInsets)
                .padding(horizontal = 8.dp)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            bottomItems.forEach { item ->
                val selected = item.route == selectedRoute
                val indicator by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    label = "nav-indicator",
                )
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight().selectable(
                        selected = selected,
                        onClick = { onSelect(item.route) },
                        role = Role.Tab,
                        interactionSource = null,
                        indication = ripple(bounded = false, radius = 24.dp),
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    // 0.8x the Material indicator (as drawn at this bar's item width).
                    Box(
                        Modifier.size(width = 48.dp, height = 26.dp).background(indicator, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(if (selected) item.iconSelected else item.icon),
                            contentDescription = stringResource(item.labelRes),
                            tint = if (selected) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}
