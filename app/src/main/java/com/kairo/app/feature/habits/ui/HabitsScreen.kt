package com.kairo.app.feature.habits.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairo.app.feature.habits.data.HabitRepository
import com.kairo.app.feature.habits.model.HabitWithStats
import com.kairo.app.ui.components.AppHeader
import com.kairo.app.ui.components.SettingsDialog
import com.kairo.app.ui.theme.KairoBackground
import com.kairo.app.ui.theme.KairoError
import com.kairo.app.ui.theme.KairoPrimaryContainer
import java.time.LocalDate

@Composable
fun HabitsScreen(
    modifier: Modifier = Modifier
) {
    val habitsWithStats by HabitRepository.habitsWithStats.collectAsState()
    val disciplineOverview by HabitRepository.disciplineOverview.collectAsState()

    var selectedHabitForDetail by remember { mutableStateOf<HabitWithStats?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    // Search query state for Habits tab
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    // Filter habits according to search query
    val filteredHabits = remember(habitsWithStats, searchQuery) {
        if (searchQuery.isBlank()) {
            habitsWithStats
        } else {
            habitsWithStats.filter {
                it.habit.title.contains(searchQuery.trim(), ignoreCase = true)
            }
        }
    }

    // Back button handling
    BackHandler(enabled = isSearchActive) {
        isSearchActive = false
        searchQuery = ""
    }

    // If a habit is selected for detail analytics, show HabitDetailScreen
    if (selectedHabitForDetail != null) {
        val currentSelected = habitsWithStats.find { it.habit.id == selectedHabitForDetail?.habit?.id }
            ?: selectedHabitForDetail!!

        BackHandler {
            selectedHabitForDetail = null
        }

        HabitDetailScreen(
            habitWithStats = currentSelected,
            onBack = { selectedHabitForDetail = null },
            onCheckInToday = {
                HabitRepository.toggleHabitDay(currentSelected.habit.id, LocalDate.now())
            },
            onEditHabit = {
                // Edit mode
            },
            onDeleteHabit = { habitId ->
                HabitRepository.deleteHabit(habitId)
                selectedHabitForDetail = null
            }
        )
        return
    }

    // Main Habits List Screen
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(KairoBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Working Header from tasks (Anas + Synced cloud pill + Date + Search + 3 dots + Settings)
            AppHeader(
                searchQuery = searchQuery,
                isSearchActive = isSearchActive,
                onSearchQueryChange = { searchQuery = it },
                onToggleSearch = {
                    isSearchActive = !isSearchActive
                    if (!isSearchActive) searchQuery = ""
                },
                onCloseSearch = {
                    isSearchActive = false
                    searchQuery = ""
                },
                searchPlaceholder = "Search habits...",
                onSettingsClick = { showSettingsDialog = true },
                moreMenuContent = { dismissMenu ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Refresh Habits",
                                fontWeight = FontWeight.Medium
                            )
                        },
                        onClick = {
                            dismissMenu()
                            HabitRepository.fetchFromSupabase()
                        }
                    )
                }
            )

            // Scrollable Content
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Discipline Overview Card
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    DisciplineOverviewCard(stats = disciplineOverview)
                }

                // 2. Section Header: All Habits + Swipe dates
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (searchQuery.isBlank()) "All Habits" else "Search Results (${filteredHabits.size})",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Swipe,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Swipe dates",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                // 3. Habit Cards
                if (filteredHabits.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (searchQuery.isNotBlank()) "No habits matching \"$searchQuery\"" else "No habits yet. Tap + to add one!",
                                color = Color(0xFF94A3B8),
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    items(
                        items = filteredHabits,
                        key = { it.habit.id }
                    ) { item ->
                        HabitCard(
                            habitWithStats = item,
                            onTitleClick = {
                                selectedHabitForDetail = item
                            },
                            onDayClick = { date ->
                                HabitRepository.toggleHabitDay(item.habit.id, date)
                            }
                        )
                    }
                }

                // Bottom padding for FAB & BottomBar
                item {
                    Spacer(modifier = Modifier.height(84.dp))
                }
            }
        }

        // Floating Action Button (+)
        FloatingActionButton(
            onClick = { showCreateDialog = true },
            containerColor = KairoPrimaryContainer,
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp)
                .size(56.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Create Habit",
                modifier = Modifier.size(28.dp)
            )
        }
    }

    // Create Habit Dialog
    if (showCreateDialog) {
        CreateHabitDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { title, type, targetValue, targetUnit, repeatType, repeatDays, weeklyTargetCount, reminderEnabled, reminderTime, reminderToneUri, reminderToneTitle ->
                HabitRepository.addHabit(
                    title = title,
                    type = type,
                    targetValue = targetValue,
                    targetUnit = targetUnit,
                    repeatType = repeatType,
                    repeatDays = repeatDays,
                    weeklyTargetCount = weeklyTargetCount,
                    reminderEnabled = reminderEnabled,
                    reminderTime = reminderTime,
                    reminderToneUri = reminderToneUri,
                    reminderToneTitle = reminderToneTitle
                )
                showCreateDialog = false
            }
        )
    }

    // Common Settings Dialog
    if (showSettingsDialog) {
        SettingsDialog(onDismissRequest = { showSettingsDialog = false })
    }
}
