package com.kairo.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kairo.app.ui.components.CreateTaskDialog
import com.kairo.app.ui.components.FilterSortBar
import com.kairo.app.ui.components.SectionHeader
import com.kairo.app.ui.components.SectionType
import com.kairo.app.ui.components.TaskCard
import com.kairo.app.ui.theme.KairoBackground
import com.kairo.app.ui.theme.KairoOutlineVariant
import com.kairo.app.ui.theme.KairoPrimary
import com.kairo.app.ui.theme.KairoPrimaryContainer
import com.kairo.app.ui.theme.KairoSurfaceContainerHigh
import com.kairo.app.ui.viewmodel.TaskViewModel

@Composable
fun TasksScreen(
    onNavigateToAi: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TaskViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = KairoBackground,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(KairoBackground)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Logo and Title
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(KairoPrimaryContainer, KairoPrimary)
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.TaskAlt,
                                contentDescription = "Logo",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Text(
                            text = "Tasks",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Action Icons
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(onClick = { viewModel.toggleSearchBar() }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = if (uiState.isSearchActive) KairoPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(onClick = onNavigateToAi) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "AI Assistant",
                                tint = KairoPrimary
                            )
                        }

                        IconButton(onClick = {
                            Toast.makeText(context, "Settings coming soon", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Expandable Search Bar
                AnimatedVisibility(visible = uiState.isSearchActive) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.updateSearch(it) },
                        placeholder = { Text("Search tasks...") },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateSearch("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = KairoPrimary,
                            unfocusedBorderColor = KairoOutlineVariant.copy(alpha = 0.4f),
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Filter & Sort Control Bar
                FilterSortBar(
                    activeFilter = uiState.activeFilter,
                    activeSort = uiState.activeSort,
                    onFilterSelected = { viewModel.setFilter(it) },
                    onSortSelected = { viewModel.setSort(it) },
                    onExportClicked = {
                        Toast.makeText(context, "Exporting tasks...", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.setShowCreateDialog(true) },
                shape = RoundedCornerShape(16.dp),
                containerColor = KairoPrimaryContainer,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Create Task",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. OVERDUE SECTION
            if (uiState.overdueTasks.isNotEmpty()) {
                item(key = "section_overdue_header") {
                    SectionHeader(
                        title = "Overdue",
                        countText = "${uiState.overdueTasks.size} overdue",
                        sectionType = SectionType.OVERDUE,
                        isExpanded = uiState.overdueExpanded,
                        onToggle = { viewModel.toggleOverdue() }
                    )
                }

                if (uiState.overdueExpanded) {
                    items(uiState.overdueTasks, key = { it.id }) { task ->
                        TaskCard(
                            task = task,
                            onToggleCompletion = { viewModel.toggleTask(task.id) }
                        )
                    }
                }
            }

            // 2. TODAY SECTION
            if (uiState.todayTasks.isNotEmpty()) {
                item(key = "section_today_header") {
                    SectionHeader(
                        title = "Today",
                        countText = "${uiState.todayTasks.size} tasks",
                        sectionType = SectionType.TODAY,
                        isExpanded = uiState.todayExpanded,
                        onToggle = { viewModel.toggleToday() }
                    )
                }

                if (uiState.todayExpanded) {
                    items(uiState.todayTasks, key = { it.id }) { task ->
                        TaskCard(
                            task = task,
                            onToggleCompletion = { viewModel.toggleTask(task.id) }
                        )
                    }
                }
            }

            // 3. UPCOMING SECTION
            if (uiState.upcomingTasks.isNotEmpty()) {
                item(key = "section_upcoming_header") {
                    SectionHeader(
                        title = "Upcoming",
                        countText = "${uiState.upcomingTasks.size} scheduled",
                        sectionType = SectionType.UPCOMING,
                        isExpanded = uiState.upcomingExpanded,
                        onToggle = { viewModel.toggleUpcoming() }
                    )
                }

                if (uiState.upcomingExpanded) {
                    items(uiState.upcomingTasks, key = { it.id }) { task ->
                        TaskCard(
                            task = task,
                            onToggleCompletion = { viewModel.toggleTask(task.id) }
                        )
                    }
                }
            }

            // 4. COMPLETED SECTION
            if (uiState.completedTasks.isNotEmpty()) {
                item(key = "section_completed_header") {
                    SectionHeader(
                        title = "Completed",
                        countText = "${uiState.completedTasks.size} completed",
                        sectionType = SectionType.COMPLETED,
                        isExpanded = uiState.completedExpanded,
                        onToggle = { viewModel.toggleCompleted() }
                    )
                }

                if (uiState.completedExpanded) {
                    items(uiState.completedTasks, key = { it.id }) { task ->
                        TaskCard(
                            task = task,
                            onToggleCompletion = { viewModel.toggleTask(task.id) }
                        )
                    }
                }
            }

            // Bottom spacer for FAB & Navigation bar padding
            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }

        // Dialog for creating tasks
        if (uiState.showCreateDialog) {
            CreateTaskDialog(
                onDismiss = { viewModel.setShowCreateDialog(false) },
                onConfirm = { title, category, priority ->
                    viewModel.createTask(title, category, priority)
                }
            )
        }
    }
}
