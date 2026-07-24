package com.cruciblelab.trafficlogger.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cruciblelab.trafficlogger.MainViewModel
import com.cruciblelab.trafficlogger.data.RuleType

private object Routes {
    const val LIST = "list"
    const val DETAIL = "detail/{entryId}"
    const val SETTINGS = "settings"
    const val STATS = "stats"
    const val RULES = "rules"
    fun detail(id: Long) = "detail/$id"
}

@Composable
fun TrafficNavGraph(viewModel: MainViewModel, onToggleVpn: () -> Unit) {
    val navController = rememberNavController()
    val entries by viewModel.entries.collectAsState()
    val vpnRunning by viewModel.vpnRunning.collectAsState()
    val retentionDays by viewModel.retentionDays.collectAsState()
    val dailyLimitMb by viewModel.dailyLimitMb.collectAsState()
    val ipInfoMap by viewModel.ipInfoMap.collectAsState()
    val rules by viewModel.rules.collectAsState()

    NavHost(navController = navController, startDestination = Routes.LIST) {
        composable(Routes.LIST) {
            TrafficListScreen(
                entries = entries,
                vpnRunning = vpnRunning,
                ipInfoMap = ipInfoMap,
                onRequestIpInfo = viewModel::requestIpInfo,
                onToggleVpn = onToggleVpn,
                onEntryClick = { entry -> navController.navigate(Routes.detail(entry.id)) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                onStatsClick = { navController.navigate(Routes.STATS) },
                onRulesClick = { navController.navigate(Routes.RULES) },
                onBlockEntry = viewModel::blockEntry,
                onWhitelistEntry = viewModel::whitelistEntry
            )
        }
        composable(Routes.STATS) {
            StatsScreen(entries = entries, onBack = { navController.popBackStack() })
        }
        composable(Routes.RULES) {
            RulesScreen(
                rules = rules,
                onAddRule = { type, value -> viewModel.addRule(type, null, null, value) },
                onDeleteRule = viewModel::deleteRule,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            Routes.DETAIL,
            arguments = listOf(navArgument("entryId") { type = NavType.LongType })
        ) { backStackEntry ->
            val entryId = backStackEntry.arguments?.getLong("entryId") ?: return@composable
            val entry = entries.find { it.id == entryId } ?: return@composable
            val history by viewModel.connectionHistory(entry).collectAsState(initial = emptyList())
            TrafficDetailScreen(
                entry = entry,
                history = history,
                ipInfo = ipInfoMap[entry.destIp],
                onRequestIpInfo = viewModel::requestIpInfo,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                retentionDays = retentionDays,
                onRetentionChange = viewModel::setRetentionDays,
                dailyLimitMb = dailyLimitMb,
                onDailyLimitChange = viewModel::setDailyLimitMb,
                onClearHistory = viewModel::clearHistory,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
