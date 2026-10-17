package com.kairo.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
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
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(targetIndex)
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
                0 -> TasksScreen()
                1 -> ListsScreen()
                2 -> HabitsScreen()
                3 -> CalendarScreen()
                4 -> AnalyticsScreen()
            }
        }
    }
}
