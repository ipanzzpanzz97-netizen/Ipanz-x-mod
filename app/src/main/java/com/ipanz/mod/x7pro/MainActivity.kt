package com.ipanz.mod.x7pro

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ipanz.mod.x7pro.ui.components.StatusPillBadge
import com.ipanz.mod.x7pro.ui.screens.DeepCleanerScreen
import com.ipanz.mod.x7pro.ui.screens.GatekeeperLockedScreen
import com.ipanz.mod.x7pro.ui.screens.HyperGameScreen
import com.ipanz.mod.x7pro.ui.screens.SystemDashboardScreen
import com.ipanz.mod.x7pro.ui.screens.VoiceStudioScreen
import com.ipanz.mod.x7pro.ui.theme.CarbonElevated
import com.ipanz.mod.x7pro.ui.theme.CarbonSurface
import com.ipanz.mod.x7pro.ui.theme.IPanzModTheme
import com.ipanz.mod.x7pro.ui.theme.NeonCyan
import com.ipanz.mod.x7pro.ui.theme.PitchBlack
import com.ipanz.mod.x7pro.ui.theme.PocoElectricYellow
import com.ipanz.mod.x7pro.ui.theme.PocoYellowContainer
import com.ipanz.mod.x7pro.ui.theme.TextSecondary
import kotlinx.serialization.Serializable

@Serializable object SystemRoute
@Serializable object CleanerRoute
@Serializable object HyperGameRoute
@Serializable object VoiceRoute

private data class NavTabItem(
    val route: Any,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {

    private val viewModel: IPanzViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )

        setContent {
            IPanzModTheme {
                val hardwareCheck by viewModel.hardwareCheck.collectAsStateWithLifecycle()

                if (!hardwareCheck.isAccessAllowed) {
                    GatekeeperLockedScreen(
                        hardwareCheck = hardwareCheck,
                        onExitApp = { finishAffinity() }
                    )
                } else {
                    IPanzMainAppShell(viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissions()
    }
}

@Composable
private fun IPanzMainAppShell(viewModel: IPanzViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = navBackStackEntry?.destination

    val hardwareCheck by viewModel.hardwareCheck.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val cleanerState by viewModel.cleanerState.collectAsStateWithLifecycle()
    val gameState by viewModel.gameState.collectAsStateWithLifecycle()
    val hudState by viewModel.hudState.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()

    val tabs = listOf(
        NavTabItem(SystemRoute, "Sistem", Icons.Filled.Dashboard, Icons.Outlined.Dashboard, "nav_system"),
        NavTabItem(CleanerRoute, "Cleaner", Icons.Filled.CleaningServices, Icons.Outlined.CleaningServices, "nav_cleaner"),
        NavTabItem(HyperGameRoute, "Game", Icons.Filled.SportsEsports, Icons.Outlined.SportsEsports, "nav_game"),
        NavTabItem(VoiceRoute, "Voice AI", Icons.Filled.GraphicEq, Icons.Outlined.GraphicEq, "nav_voice")
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(PitchBlack)) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = PitchBlack,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CarbonSurface)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "iPANZ MOD",
                            style = MaterialTheme.typography.headlineSmall,
                            color = PocoElectricYellow,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "POCO X7 PRO • DIMENSITY 8400-ULTRA",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonCyan
                        )
                    }
                    StatusPillBadge(
                        text = "${String.format("%.1f", telemetry.batteryTempCelsius)}°C • ${telemetry.ramAvailableMb}MB",
                        color = PocoElectricYellow
                    )
                }
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = CarbonSurface,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        tabs.forEach { tab ->
                            val selected = currentDest?.hasRoute(tab.route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                modifier = Modifier.testTag(tab.testTag),
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label,
                                        modifier = Modifier.size(22.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = PocoElectricYellow,
                                    selectedTextColor = PocoElectricYellow,
                                    indicatorColor = PocoYellowContainer,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = CarbonElevated,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        tabs.forEach { tab ->
                            val selected = currentDest?.hasRoute(tab.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                modifier = Modifier.testTag(tab.testTag),
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = PocoElectricYellow,
                                    selectedTextColor = PocoElectricYellow,
                                    indicatorColor = PocoYellowContainer,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                )
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = SystemRoute,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) {
                    composable<SystemRoute> {
                        SystemDashboardScreen(
                            hardwareCheck = hardwareCheck,
                            telemetry = telemetry,
                            permissions = permissions,
                            useShizuku = cleanerState.useShizukuElevation,
                            onToggleShizuku = { viewModel.setUseShizuku(it) },
                            onRefreshPermissions = { viewModel.refreshPermissions() }
                        )
                    }
                    composable<CleanerRoute> {
                        DeepCleanerScreen(
                            cleanerState = cleanerState,
                            telemetry = telemetry,
                            permissions = permissions,
                            onStartScan = { viewModel.startDeepStorageScan() },
                            onCancelScan = { viewModel.cancelDeepScan() },
                            onToggleJunk = { viewModel.toggleJunkSelection(it) },
                            onCleanSelectedJunk = { viewModel.cleanSelectedJunk() },
                            onToggleDuplicate = { sha, path -> viewModel.toggleDuplicateSelection(sha, path) },
                            onCleanSelectedDuplicates = { viewModel.cleanSelectedDuplicates() },
                            onToggleHeavyFile = { viewModel.toggleHeavyFileSelection(it) },
                            onCleanSelectedHeavyFiles = { viewModel.cleanSelectedHeavyFiles() },
                            onBoostRam = { viewModel.boostRamNow() },
                            onOpenAppSettings = { viewModel.openAppStorageSettings(it) },
                            onRefreshApps = { viewModel.loadGamesAndCacheApps() }
                        )
                    }
                    composable<HyperGameRoute> {
                        HyperGameScreen(
                            gameState = gameState,
                            hudState = hudState,
                            permissions = permissions,
                            onToggleAutoDnd = { viewModel.setAutoDnd(it) },
                            onToggleLockBrightness = { viewModel.setLockBrightness(it) },
                            onActivateOneTapBoost = { viewModel.activateOneTapGameBoost(it) },
                            onRestoreEnvironment = { viewModel.restorePostGameSettings() },
                            onRefreshGames = { viewModel.loadGamesAndCacheApps() }
                        )
                    }
                    composable<VoiceRoute> {
                        VoiceStudioScreen(
                            voiceState = voiceState,
                            voiceEngine = viewModel.voiceEngine,
                            permissions = permissions,
                            onRefreshPermissions = { viewModel.refreshPermissions() }
                        )
                    }
                }
            }
        }
    }
}
