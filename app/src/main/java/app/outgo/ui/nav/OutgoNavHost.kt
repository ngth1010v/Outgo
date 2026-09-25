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
import androidx.compose.runtime.CompositionLocalProvider
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
import app.outgo.ui.balance.BalanceScreen
import app.outgo.ui.category.CategoryScreen
import app.outgo.ui.component.LocalSwipeParent
import app.outgo.ui.component.rememberSwipeLevel
import app.outgo.ui.component.swipeShift
import app.outgo.ui.component.swipeStep
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

    val tabIndex = bottomItems.indexOfFirst { it.route == tab }
    // Wraps around: Home's left neighbor is Setting and the reverse.
    fun tabAt(page: Int) = bottomItems[(tabIndex + page).mod(bottomItems.size)].route
    // Shared by all tabs and relative to the current one, whichever tab's box gets the touch.
    val tabSwipe = rememberSwipeLevel { next, _ -> tabAt(if (next) 1 else -1).let { route -> { selectTab(route) } } }
    val moving = tabSwipe.moving
    // A swipe before the prewarm below reached a neighbor composes it now.
    LaunchedEffect(moving) {
        if (moving) listOf(tabAt(-1), tabAt(1)).forEach { if (it !in composedTabs) composedTabs += it }
    }

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
                    // Neighbors are placed beside the current tab while it is dragged or settling.
                    val page = when (route) {
                        tab -> 0
                        tabAt(-1) -> -1
                        tabAt(1) -> 1
                        else -> null
                    }?.takeIf { it == 0 || moving }
                    Box(
                        Modifier.fillMaxSize().placedIf(onTabs && page != null).swipeStep(tabSwipe)
                            .swipeShift(tabSwipe, page ?: 0),
                    ) {
                        CompositionLocalProvider(LocalSwipeParent provides tabSwipe) { TabContent(route, shown, navController) }
                    }
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
                    route = Routes.HISTORY_PATTERN,
                    arguments = listOf(
                        navArgument("type") { type = NavType.StringType },
                        navArgument("day") { type = NavType.LongType; defaultValue = 0L },
                    ),
                ) { entry ->
                    val type = HistoryType.fromArg(entry.arguments?.getString("type"))
                    HistoryScreen(
                        type = type,
                        dayStartMillis = entry.arguments?.getLong("day")?.takeIf { it > 0L },
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
        Routes.ANALYSIS -> AnalysisScreen(
            onOpenTrade = { tradeId -> navController.navigate(Routes.tradeEdit(tradeId)) },
            onOpenDay = { dayStart -> navController.navigate(Routes.history(HistoryType.EXPENSE, dayStart)) },
        )
        Routes.SETTING -> SettingScreen()
    }
}

/**
 * Measures the content either way (so showing it needs no new layout pass) but places it only
 * when [shown]. Unplaced content is not drawn, gets no touches, and is not in the accessibility tree.
 */
internal fun Modifier.placedIf(shown: Boolean) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) { if (shown) placeable.place(0, 0) }
}
