package com.kairo.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.kairo.app.ui.navigation.BottomNavBar
import com.kairo.app.ui.navigation.Screen
import com.kairo.app.ui.navigation.bottomNavItems
import com.kairo.app.ui.screens.AnalyticsScreen
import com.kairo.app.feature.calendar.ui.CalendarScreen
import com.kairo.app.feature.habits.ui.HabitsScreen
import com.kairo.app.ui.screens.ListsScreen
import com.kairo.app.ui.screens.TasksScreen
import com.kairo.app.ui.theme.KairoBackground
import kotlinx.coroutines.launch

@Composable
fun MainScreen() {
    val pagerState = rememberPagerState(initialPage = 0) { bottomNavItems.size }
    val coroutineScope = rememberCoroutineScope()
    val currentRoute = bottomNavItems.getOrNull(pagerState.currentPage)?.route ?: Screen.Tasks.route

    var tasksResetTrigger by remember { mutableIntStateOf(0) }
    var listsResetTrigger by remember { mutableIntStateOf(0) }
    var habitsResetTrigger by remember { mutableIntStateOf(0) }
    var calendarResetTrigger by remember { mutableIntStateOf(0) }
    var analyticsResetTrigger by remember { mutableIntStateOf(0) }

    BackHandler(enabled = pagerState.currentPage != 0) {
        coroutineScope.launch {
            pagerState.animateScrollToPage(0)
        }
    }

    Scaffold(
        containerColor = KairoBackground,
        bottomBar = {
            BottomNavBar(
                currentRoute = currentRoute,
                onNavigateToRoute = { route ->
                    val targetIndex = bottomNavItems.indexOfFirst { it.route == route }
                    if (targetIndex != -1) {
                        if (targetIndex == pagerState.currentPage) {
                            when (route) {
                                Screen.Tasks.route -> tasksResetTrigger++
                                Screen.Lists.route -> listsResetTrigger++
                                Screen.Habits.route -> habitsResetTrigger++
                                Screen.Calendar.route -> calendarResetTrigger++
                                Screen.Analytics.route -> analyticsResetTrigger++
                            }
                        } else {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(targetIndex)
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            beyondViewportPageCount = 1
        ) { page ->
            when (page) {
                0 -> TasksScreen(resetTrigger = tasksResetTrigger)
                1 -> ListsScreen(resetTrigger = listsResetTrigger)
                2 -> HabitsScreen(resetTrigger = habitsResetTrigger)
                3 -> CalendarScreen(resetTrigger = calendarResetTrigger)
                4 -> AnalyticsScreen(resetTrigger = analyticsResetTrigger)
            }
        }
    }
}
