package com.kairo.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kairo.app.ui.navigation.BottomNavBar
import com.kairo.app.ui.navigation.Screen
import com.kairo.app.ui.screens.AiAssistantScreen
import com.kairo.app.ui.screens.AnalyticsScreen
import com.kairo.app.ui.screens.CalendarScreen
import com.kairo.app.ui.screens.HabitsScreen
import com.kairo.app.ui.screens.ListsScreen
import com.kairo.app.ui.screens.TasksScreen
import com.kairo.app.ui.theme.KairoBackground

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Tasks.route

    Scaffold(
        containerColor = KairoBackground,
        bottomBar = {
            if (currentRoute != Screen.AiAssistant.route) {
                BottomNavBar(
                    currentRoute = currentRoute,
                    onNavigateToRoute = { route ->
                        navController.navigate(route) {
                            popUpTo(Screen.Tasks.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Tasks.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(Screen.Tasks.route) {
                TasksScreen(
                    onNavigateToAi = {
                        navController.navigate(Screen.AiAssistant.route)
                    }
                )
            }
            composable(Screen.Lists.route) {
                ListsScreen()
            }
            composable(Screen.Habits.route) {
                HabitsScreen()
            }
            composable(Screen.Calendar.route) {
                CalendarScreen()
            }
            composable(Screen.Analytics.route) {
                AnalyticsScreen()
            }
            composable(Screen.AiAssistant.route) {
                AiAssistantScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
