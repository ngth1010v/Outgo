package app.outgo.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
            modifier = Modifier.padding(padding),
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
