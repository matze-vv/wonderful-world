package de.wonderfulworld.app.ui

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.wonderfulworld.app.ui.screens.AlarmsScreen
import de.wonderfulworld.app.ui.screens.ArchiveScreen
import de.wonderfulworld.app.ui.screens.DashboardScreen
import de.wonderfulworld.app.ui.screens.EntryScreen
import de.wonderfulworld.app.ui.screens.PlanningScreen
import de.wonderfulworld.app.ui.screens.SettingsScreen
import de.wonderfulworld.app.ui.screens.JournalViewScreen
import de.wonderfulworld.app.ui.screens.ManageScreen
import de.wonderfulworld.app.ui.screens.MissionEditScreen
import de.wonderfulworld.app.ui.screens.RitualsTodayScreen
import de.wonderfulworld.app.ui.screens.TagMissionsScreen
import de.wonderfulworld.app.ui.components.BackTopBar
import androidx.compose.foundation.layout.Column
import kotlinx.coroutines.flow.MutableStateFlow

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("dashboard", "Woche", Icons.Outlined.GridView),
    Tab("archive", "Archiv", Icons.Outlined.CalendarMonth),
    Tab("alarms", "Wecker", Icons.Outlined.Alarm),
    Tab("more", "Mehr", Icons.Outlined.MoreHoriz),
)

@Composable
fun AppNavigation(pendingDest: MutableStateFlow<String?>) {
    val nav = rememberNavController()
    val dest by pendingDest.collectAsState()
    LaunchedEffect(dest) {
        val d = dest ?: return@LaunchedEffect
        when (d) {
            "planning" -> nav.navigate("planning") { launchSingleTop = true }
            else -> nav.switchTab(d)
        }
        pendingDest.value = null
    }

    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = tabs.any { it.route == route }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route,
                            onClick = { nav.switchTab(t.route) },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "dashboard", modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
            composable("dashboard") {
                DashboardScreen(
                    onPlan = { nav.navigate("planning") },
                    onNewEntry = { nav.navigate("entry?id=0") },
                    onOpenEntry = { nav.navigate("journal/$it") },
                    onOpenRituals = { nav.navigate("rituals") },
                    onOpenTag = { nav.navigate("tag/$it") },
                    onOpenMission = { nav.navigate("mission?id=$it&tag=0") },
                    onNewMission = { nav.navigate("mission?id=0&tag=0") },
                )
            }
            composable("archive") { ArchiveScreen(onOpenEntry = { nav.navigate("journal/$it") }) }
            composable("alarms") { AlarmsScreen() }
            composable("more") { ManageScreen(onOpenSettings = { nav.navigate("settings") }) }
            composable("settings") {
                Column {
                    BackTopBar("Einstellungen & Backup", onBack = { nav.popBackStack() })
                    SettingsScreen()
                }
            }
            composable("planning") {
                PlanningScreen(
                    onDone = { nav.popBackStack() },
                    onEditMission = { nav.navigate("mission?id=$it&tag=0") },
                    onNewMission = { nav.navigate("mission?id=0&tag=0") },
                )
            }
            composable("rituals") { RitualsTodayScreen(onBack = { nav.popBackStack() }) }
            composable("tag/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { back ->
                TagMissionsScreen(
                    tagId = back.arguments?.getLong("id") ?: 0L,
                    onBack = { nav.popBackStack() },
                    onEditMission = { nav.navigate("mission?id=$it&tag=0") },
                    onNewMission = { nav.navigate("mission?id=0&tag=$it") },
                )
            }
            composable("journal/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { back ->
                JournalViewScreen(
                    id = back.arguments?.getLong("id") ?: 0L,
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate("entry?id=$it") },
                )
            }
            composable(
                "entry?id={id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = 0L }),
            ) { back ->
                EntryScreen(id = back.arguments?.getLong("id") ?: 0L, onDone = { nav.popBackStack() })
            }
            composable(
                "mission?id={id}&tag={tag}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("tag") { type = NavType.LongType; defaultValue = 0L },
                ),
            ) { back ->
                MissionEditScreen(
                    id = back.arguments?.getLong("id") ?: 0L,
                    presetTag = back.arguments?.getLong("tag") ?: 0L,
                    onDone = { nav.popBackStack() },
                )
            }
        }
    }
}

private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
