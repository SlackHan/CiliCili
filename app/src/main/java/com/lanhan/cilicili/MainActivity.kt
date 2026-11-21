package com.lanhan.cilicili

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.lanhan.cilicili.ui.components.CiliIcons
import com.lanhan.cilicili.ui.search.SearchScreen
import com.lanhan.cilicili.ui.search.SearchViewModel
import com.lanhan.cilicili.ui.settings.SettingsScreen
import com.lanhan.cilicili.ui.settings.SettingsViewModel
import com.lanhan.cilicili.ui.sites.SitesScreen
import com.lanhan.cilicili.ui.sites.SitesViewModel
import com.lanhan.cilicili.ui.theme.CiliTheme

private enum class TopRoute(val route: String, val label: String) {
    SEARCH("search", "搜索"),
    SITES("sites", "站点"),
    SETTINGS("settings", "设置"),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as CiliApplication).container

        setContent {
            val settings by container.settingsRepository.settingsFlow
                .collectAsStateWithLifecycle(initialValue = AppSettings())

            CiliTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor,
            ) {
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
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentRoute == TopRoute.SEARCH.route,
                    onClick = { navigateTopLevel(TopRoute.SEARCH) },
                    icon = { Icon(Icons.Default.Search, contentDescription = null) },
                    label = { Text(TopRoute.SEARCH.label) },
                )
                NavigationBarItem(
                    selected = currentRoute == TopRoute.SITES.route,
                    onClick = { navigateTopLevel(TopRoute.SITES) },
                    icon = { Icon(CiliIcons.Globe, contentDescription = null) },
                    label = { Text(TopRoute.SITES.label) },
                )
                NavigationBarItem(
                    selected = currentRoute == TopRoute.SETTINGS.route,
                    onClick = { navigateTopLevel(TopRoute.SETTINGS) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(TopRoute.SETTINGS.label) },
                )
            }
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
