package app.outgo.ui.nav

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import app.outgo.R

internal data class BottomItem(
    val route: String,
    val labelRes: Int,
    val icon: Int,
    val iconSelected: Int,
)

internal val bottomItems = listOf(
    BottomItem(Routes.HOME, R.string.nav_home, R.drawable.ph_house, R.drawable.ph_house_fill),
    BottomItem(Routes.TRADE, R.string.nav_trade, R.drawable.ph_plus_circle, R.drawable.ph_plus_circle_fill),
    BottomItem(Routes.BALANCE, R.string.nav_balance, R.drawable.ph_wallet, R.drawable.ph_wallet_fill),
    BottomItem(Routes.CATEGORY, R.string.nav_category, R.drawable.ph_squares_four, R.drawable.ph_squares_four_fill),
    BottomItem(Routes.ANALYSIS, R.string.nav_analysis, R.drawable.ph_chart_pie_slice, R.drawable.ph_chart_pie_slice_fill),
    BottomItem(Routes.SETTING, R.string.nav_setting, R.drawable.ph_gear_six, R.drawable.ph_gear_six_fill),
)

@Composable
fun OutgoBottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    NavigationBar(modifier = Modifier.height(64.dp)) {
        bottomItems.forEach { item ->
            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        painter = painterResource(if (selected) item.iconSelected else item.icon),
                        contentDescription = stringResource(item.labelRes),
                        modifier = Modifier.size(20.dp),
                    )
                },
                label = null,
            )
        }
    }
}
