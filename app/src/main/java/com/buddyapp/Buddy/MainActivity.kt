package com.buddyapp.Buddy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.buddyapp.Buddy.ui.CommandsScreen
import com.buddyapp.Buddy.ui.DashboardScreen
import com.buddyapp.Buddy.ui.HistoryScreen
import com.buddyapp.Buddy.ui.KeysScreen
import com.buddyapp.Buddy.ui.ApiUsageScreen
import com.buddyapp.Buddy.ui.SettingsScreen
import com.buddyapp.Buddy.ui.theme.BuddyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BuddyTheme {
                BuddyMainScreen()
            }
        }
    }
}

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Outlined.Home)
    object Keys : Screen("keys", "Keys", Icons.Outlined.Key)
    object Commands : Screen("commands", "Commands", Icons.AutoMirrored.Outlined.List)
    object History : Screen("history", "History", Icons.Outlined.History)
    object Settings : Screen("settings", "Settings", Icons.Outlined.Settings)
}

@Composable
fun BuddyMainScreen() {
    val navController = rememberNavController()
    val items = listOf(Screen.Dashboard, Screen.Keys, Screen.Commands, Screen.History, Screen.Settings)
    val haptic = LocalHapticFeedback.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.background
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentRoute == screen.route,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) { DashboardScreen() }
            composable(Screen.Keys.route) { KeysScreen(navController = navController) }
            composable("api_usage/{keyIndex}") { backStackEntry ->
                val keyIndex = backStackEntry.arguments?.getString("keyIndex")?.toIntOrNull() ?: 0
                ApiUsageScreen(navController = navController, keyIndex = keyIndex)
            }
            composable(Screen.Commands.route) { CommandsScreen() }
            composable(Screen.History.route) { HistoryScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}