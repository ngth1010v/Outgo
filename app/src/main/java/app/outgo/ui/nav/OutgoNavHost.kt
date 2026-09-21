package app.outgo.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.outgo.ui.analysis.AnalysisScreen
import app.outgo.ui.analysis.AnalysisSubScreen
import app.outgo.ui.balance.BalanceScreen
import app.outgo.ui.category.CategoryScreen
import app.outgo.ui.history.HistoryScreen
import app.outgo.ui.home.HomeScreen
import app.outgo.ui.setting.SettingScreen
import app.outgo.ui.trade.TradeScreen
import kotlinx.coroutines.delay

/** Wait after launch before composing the not-yet-visited tabs, so cold start stays untouched. */
private const val PREWARM_DELAY_MS = 800L

@Composable
fun OutgoRoot(openSetting: Boolean = false) {
    val navController = rememberNavController()
    // Relaunched after a backup restore: land back on Setting (Back still returns to Trade).
    var tab by rememberSaveable { mutableStateOf(if (openSetting) Routes.SETTING else Routes.TRADE) }
    // Tabs stay composed once visited, so a tap only changes which one is placed: no screen is
    // rebuilt on switch. The cost is memory and the hidden tabs' Flows staying live.
    val composedTabs = remember { mutableStateListOf(tab) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val onTabs = backStackEntry?.destination?.route.let { it == null || it == Routes.TABS }
    val focusManager = LocalFocusManager.current

    fun selectTab(route: String) {
        focusManager.clearFocus() // a focused field in a hidden tab would keep the keyboard up
        if (route !in composedTabs) composedTabs += route
        tab = route
        if (!onTabs) navController.popBackStack(Routes.TABS, inclusive = false)
    }

    BackHandler(enabled = onTabs && tab != Routes.TRADE) { selectTab(Routes.TRADE) }

    LaunchedEffect(Unit) {
        delay(PREWARM_DELAY_MS)
        // One tab at a time so no single frame composes several screens.
        for (item in bottomItems) {
            if (item.route !in composedTabs) {
                composedTabs += item.route
                delay(100)
            }
        }
    }

    Scaffold(bottomBar = { OutgoBottomBar(selectedRoute = if (onTabs) tab else null, onSelect = ::selectTab) }) { padding ->
        Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
            composedTabs.forEach { route ->
                key(route) {
                    val shown = onTabs && route == tab
                    Box(Modifier.fillMaxSize().placedIf(shown)) { TabContent(route, shown, navController) }
                }
            }

            // Only the screens pushed on top of the tabs; the empty start destination lets the
            // tabs underneath show through.
            NavHost(
                navController = navController,
                startDestination = Routes.TABS,
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
            ) {
                composable(Routes.TABS) {}

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
}

@Composable
private fun TabContent(route: String, shown: Boolean, navController: NavHostController) {
    when (route) {
        Routes.TRADE -> TradeScreen(editingTradeId = null, onClose = {})
        Routes.HOME -> HomeScreen(
            visible = shown,
            onOpenTrade = { tradeId -> navController.navigate(Routes.tradeEdit(tradeId)) },
        )
        Routes.BALANCE -> BalanceScreen()
        Routes.CATEGORY -> CategoryScreen()
        Routes.ANALYSIS -> AnalysisScreen(onOpenSub = { kind -> navController.navigate(Routes.analysisSub(kind)) })
        Routes.SETTING -> SettingScreen()
    }
}

/**
 * Measures the content either way (so showing it needs no new layout pass) but places it only
 * when [shown]. Unplaced content is not drawn, gets no touches, and is not in the accessibility tree.
 */
private fun Modifier.placedIf(shown: Boolean) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) { if (shown) placeable.place(0, 0) }
}
