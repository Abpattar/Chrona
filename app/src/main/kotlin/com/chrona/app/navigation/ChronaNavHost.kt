package com.chrona.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.chrona.app.di.AppContainer
import com.chrona.app.ui.board.BoardRoute
import com.chrona.app.ui.history.HistoryScreen
import com.chrona.app.ui.schedule.ScheduleRoute
import com.chrona.app.ui.settings.SettingsScreen
import com.chrona.app.ui.today.TodayRoute

/** Navigation shell: five top-level destinations, all peers (UI-001). */
@Composable
fun ChronaNavHost(
    container: AppContainer,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar(modifier = Modifier.semantics { testTag = "chrona_bottom_bar" }) {
                ChronaDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = { navController.navigateToTopLevel(destination) },
                        icon = { Icon(imageVector = destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.labelRes)) },
                        modifier = Modifier.semantics { testTag = "nav_${destination.route}" },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ChronaDestination.TODAY.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(ChronaDestination.TODAY.route) {
                TodayRoute(container)
            }
            composable(ChronaDestination.SCHEDULE.route) { ScheduleRoute(container) }
            composable(ChronaDestination.BOARD.route) { BoardRoute(container) }
            composable(ChronaDestination.HISTORY.route) { HistoryScreen() }
            composable(ChronaDestination.SETTINGS.route) { SettingsScreen() }
        }
    }
}

/**
 * Top-level destinations keep a single entry on the back stack, so switching tabs never grows
 * an unbounded history the user has to back out of.
 */
private fun NavHostController.navigateToTopLevel(destination: ChronaDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}