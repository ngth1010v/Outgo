package app.outgo.ui.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.outgo.ui.analysis.AnalysisScreen
import app.outgo.ui.analysis.AnalysisSubScreen
import app.outgo.ui.balance.BalanceScreen
import app.outgo.ui.category.CategoryScreen
import app.outgo.ui.component.SLIDE_MS
import app.outgo.ui.history.HistoryScreen
import app.outgo.ui.home.HomeScreen
import app.outgo.ui.setting.SettingScreen
import app.outgo.ui.trade.TradeScreen

@Composable
fun OutgoRoot() {
    val navController = rememberNavController()

    Scaffold(bottomBar = { OutgoBottomBar(navController) }) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.TRADE,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            enterTransition = { slideIntoContainer(slideDirection(pop = false), slideSpec) },
            exitTransition = { slideOutOfContainer(slideDirection(pop = false), slideSpec) },
            popEnterTransition = { slideIntoContainer(slideDirection(pop = true), slideSpec) },
            popExitTransition = { slideOutOfContainer(slideDirection(pop = true), slideSpec) },
        ) {
            composable(Routes.TRADE) {
                TradeScreen(editingTradeId = null, onClose = {})
            }
            composable(Routes.HOME) {
                HomeScreen(onOpenTrade = { tradeId -> navController.navigate(Routes.tradeEdit(tradeId)) })
            }
            composable(Routes.BALANCE) { BalanceScreen() }
            composable(Routes.CATEGORY) { CategoryScreen() }
            composable(Routes.ANALYSIS) {
                AnalysisScreen(onOpenSub = { kind -> navController.navigate(Routes.analysisSub(kind)) })
            }
            composable(Routes.SETTING) { SettingScreen() }

            composable(
                route = Routes.ANALYSIS_SUB_PATTERN,
                arguments = listOf(navArgument("kind") { type = NavType.StringType }),
            ) { entry ->
                val kind = AnalysisKind.fromArg(entry.arguments?.getString("kind"))
                AnalysisSubScreen(kind = kind, onBack = { navController.popBackStack() })
            }

            composable(
                route = Routes.HISTORY_PATTERN,
                arguments = listOf(navArgument("type") { type = NavType.StringType }),
            ) { entry ->
                val type = HistoryType.fromArg(entry.arguments?.getString("type"))
                HistoryScreen(
                    type = type,
                    onBack = { navController.popBackStack() },
                    onOpenTrade = { tradeId -> navController.navigate(Routes.tradeEdit(tradeId)) },
                )
            }

            composable(
                route = Routes.TRADE_EDIT_PATTERN,
                arguments = listOf(navArgument("tradeId") { type = NavType.LongType }),
            ) { entry ->
                val tradeId = entry.arguments?.getLong("tradeId") ?: return@composable
                TradeScreen(editingTradeId = tradeId, onClose = { navController.popBackStack() })
            }
        }
    }
}

private val slideSpec = tween<IntOffset>(SLIDE_MS, easing = EaseInOut)

/** Bottom-bar order; pushed screens (history, trade edit, analysis sub) sit "right of" all tabs. */
private fun navIndex(entry: NavBackStackEntry): Int =
    bottomItems.indexOfFirst { it.route == entry.destination.route }.let { if (it < 0) Int.MAX_VALUE else it }

/** Moving to a tab further right slides content left, and vice versa; ties push forward, pop back. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.slideDirection(pop: Boolean): SlideDirection {
    val delta = navIndex(targetState).compareTo(navIndex(initialState))
    return if (delta > 0 || (delta == 0 && !pop)) SlideDirection.Start else SlideDirection.End
}
