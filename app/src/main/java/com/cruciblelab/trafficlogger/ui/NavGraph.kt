package com.cruciblelab.trafficlogger.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cruciblelab.trafficlogger.MainViewModel
import com.cruciblelab.trafficlogger.data.RuleType

private object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val LIST = "list"
    const val DETAIL = "detail/{entryId}"
    const val SETTINGS = "settings"
    const val STATS = "stats"
    const val RULES = "rules"
    const val REPUTATION = "reputation"
    fun detail(id: Long) = "detail/$id"
}

@Composable
fun TrafficNavGraph(viewModel: MainViewModel, onToggleVpn: () -> Unit) {
    // Onboarding tamamlanana kadar DataStore'dan okuma bitmediği için null olabilir; bu sırada
    // NavHost'u hiç oluşturmuyoruz ki başlangıç rotası yanlış (örn. her zaman HOME) sabitlenmesin.
    val onboardingCompleted by viewModel.onboardingCompleted.collectAsState()
    val completed = onboardingCompleted

    if (completed == null) {
        Box(modifier = Modifier.fillMaxSize())
        return
    }

    val navController = rememberNavController()
    val entries by viewModel.entries.collectAsState()
    val vpnRunning by viewModel.vpnRunning.collectAsState()
    val retentionDays by viewModel.retentionDays.collectAsState()
    val dailyLimitMb by viewModel.dailyLimitMb.collectAsState()
    val ipInfoMap by viewModel.ipInfoMap.collectAsState()
    val rules by viewModel.rules.collectAsState()
    val homeSummary by viewModel.homeSummary.collectAsState()
    val reputationSources by viewModel.reputationSources.collectAsState()

    NavHost(
        navController = navController,
        startDestination = if (completed) Routes.HOME else Routes.ONBOARDING
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onRequestVpnPermission = onToggleVpn,
                onFinish = {
                    viewModel.setOnboardingCompleted(true)
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                summary = homeSummary,
                vpnRunning = vpnRunning,
                onToggleVpn = onToggleVpn,
                onOpenList = { navController.navigate(Routes.LIST) },
                onOpenStats = { navController.navigate(Routes.STATS) },
                onOpenRules = { navController.navigate(Routes.RULES) },
                onOpenReputation = { navController.navigate(Routes.REPUTATION) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.LIST) {
            TrafficListScreen(
                entries = entries,
                vpnRunning = vpnRunning,
                ipInfoMap = ipInfoMap,
                onRequestIpInfo = viewModel::requestIpInfo,
                onToggleVpn = onToggleVpn,
                onEntryClick = { entry -> navController.navigate(Routes.detail(entry.id)) },
                onHomeClick = { navController.popBackStack(Routes.HOME, inclusive = false) },
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
        composable(Routes.REPUTATION) {
            ReputationScreen(
                sources = reputationSources,
                presets = viewModel.availablePresets,
                onLoadPreset = { preset -> viewModel.loadPreset(preset) },
                onImportCustom = { name, json, onResult -> viewModel.importCustomReputation(name, json, onResult) },
                onSetEnabled = viewModel::setSourceEnabled,
                onSetAutoBlock = viewModel::setSourceAutoBlock,
                onDeleteSource = viewModel::deleteReputationSource,
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
