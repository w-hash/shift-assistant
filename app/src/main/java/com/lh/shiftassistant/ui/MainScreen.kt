package com.lh.shiftassistant.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lh.shiftassistant.AppContainer
import com.lh.shiftassistant.R
import com.lh.shiftassistant.ui.calendar.CalendarScreen
import com.lh.shiftassistant.ui.location.LocationScreen
import com.lh.shiftassistant.ui.settings.SettingsScreen
import com.lh.shiftassistant.ui.shift.ShiftManagementScreen

object Routes {
    const val CALENDAR = "calendar"
    const val SHIFTS = "shifts"
    const val LOCATIONS = "locations"
    const val SETTINGS = "settings"
}

private data class NavItem(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector
)

private val navItems = listOf(
    NavItem(Routes.CALENDAR, R.string.nav_calendar, Icons.Filled.CalendarMonth),
    NavItem(Routes.SHIFTS, R.string.nav_shift, Icons.Filled.Schedule),
    NavItem(Routes.LOCATIONS, R.string.nav_location, Icons.Filled.Place),
    NavItem(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings)
)

@Composable
fun MainScreen(
    container: AppContainer,
    viewModel: AppViewModel,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                navItems.forEach { item ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(stringResource(item.labelRes)) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.CALENDAR,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.CALENDAR) { CalendarScreen(container, viewModel, darkTheme, onToggleTheme) }
            composable(Routes.SHIFTS) { ShiftManagementScreen(container, viewModel) }
            composable(Routes.LOCATIONS) { LocationScreen(container, viewModel) }
            composable(Routes.SETTINGS) { SettingsScreen(container) }
        }
    }
}
