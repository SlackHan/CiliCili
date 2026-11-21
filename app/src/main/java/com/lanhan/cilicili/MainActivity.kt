package com.lanhan.cilicili

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lanhan.cilicili.data.local.AppSettings
import com.lanhan.cilicili.domain.model.ThemeMode
import com.lanhan.cilicili.ui.components.DockItem
import com.lanhan.cilicili.ui.components.InstrumentDockBar
import com.lanhan.cilicili.ui.search.SearchScreen
import com.lanhan.cilicili.ui.search.SearchViewModel
import com.lanhan.cilicili.ui.settings.SettingsScreen
import com.lanhan.cilicili.ui.settings.SettingsViewModel
import com.lanhan.cilicili.ui.sites.SitesScreen
import com.lanhan.cilicili.ui.sites.SitesViewModel
import com.lanhan.cilicili.ui.theme.CiliTheme
import com.lanhan.cilicili.ui.theme.InstrumentTheme

private enum class TopRoute(val route: String, val index: String, val label: String) {
    SEARCH("search", "01", "检索"),
    SITES("sites", "02", "节点"),
    SETTINGS("settings", "03", "设置"),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as CiliApplication).container

        setContent {
            val settings by container.settingsRepository.settingsFlow
                .collectAsStateWithLifecycle(initialValue = AppSettings())

            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            SideEffect {
                val barStyle = if (darkTheme) {
                    SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                } else {
                    SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
            }

            CiliTheme(themeMode = settings.themeMode) {
                CiliMainApp(container = container)
            }
        }
    }
}

@Composable
private fun CiliMainApp(container: AppContainer) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val searchViewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory(container))
    val sitesViewModel: SitesViewModel = viewModel(factory = SitesViewModel.Factory(container))
    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(container))

    fun navigateTopLevel(target: TopRoute) {
        navController.navigate(target.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        containerColor = InstrumentTheme.colors.bg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            InstrumentDockBar(
                items = TopRoute.entries.map { route ->
                    DockItem(
                        index = route.index,
                        label = route.label,
                        selected = currentRoute == route.route,
                        onClick = { navigateTopLevel(route) },
                    )
                },
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopRoute.SEARCH.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(TopRoute.SEARCH.route) {
                SearchScreen(
                    viewModel = searchViewModel,
                    snackbarHostState = snackbarHostState,
                )
            }
            composable(TopRoute.SITES.route) {
                SitesScreen(
                    viewModel = sitesViewModel,
                    snackbarHostState = snackbarHostState,
                )
            }
            composable(TopRoute.SETTINGS.route) {
                SettingsScreen(
                    viewModel = settingsViewModel,
                    snackbarHostState = snackbarHostState,
                )
            }
        }
    }
}
