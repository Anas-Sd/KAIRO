package com.kairo.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kairo.app.ui.components.CreateTaskDialog
import com.kairo.app.ui.components.EditTaskDialog
import com.kairo.app.ui.components.FilterBottomSheet
import com.kairo.app.ui.components.FilterSortBar
import com.kairo.app.ui.components.SectionHeader
import com.kairo.app.ui.components.SectionType
import com.kairo.app.ui.components.SortBottomSheet
import com.kairo.app.ui.components.TaskCard
import com.kairo.app.ui.components.TaskDetailsBottomSheet
import com.kairo.app.ui.theme.KairoBackground
import com.kairo.app.ui.theme.KairoOutlineVariant
import com.kairo.app.ui.theme.KairoPrimary
import com.kairo.app.ui.theme.KairoPrimaryContainer
import com.kairo.app.ui.theme.KairoSecondary
import com.kairo.app.ui.viewmodel.TaskViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    onNavigateToAi: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TaskViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val currentDateStr = SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date())

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
                    // Title and Date
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Tasks",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            // Connected to Supabase Status Pill
                            if (uiState.isConnected) {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = KairoSecondary.copy(alpha = 0.12f),
                                            shape = RoundedCornerShape(100.dp)
                                        )
                                        .border(
                                            1.dp,
                                            KairoSecondary.copy(alpha = 0.35f),
                                            RoundedCornerShape(100.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CloudDone,
                                            contentDescription = "Connected",
                                            tint = KairoSecondary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = "Connected",
                                            color = KairoSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = currentDateStr,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
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
                    filterCriteria = uiState.filterCriteria,
                    activeSort = uiState.activeSort,
                    onOpenFilter = { viewModel.openFilterSheet() },
                    onOpenSort = { viewModel.openSortSheet() },
                    onExportClicked = {
                        Toast.makeText(context, "Exporting tasks...", Toast.LENGTH_SHORT).show()
                    },
                    onResetFilter = {
                        viewModel.resetFilters()
                        Toast.makeText(context, "Filters reset", Toast.LENGTH_SHORT).show()
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
                            onToggleCompletion = {
                                viewModel.toggleTask(task.id)
                                val msg = if (task.isCompleted) "Task marked as active" else "Task completed"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            },
                            onDeleteTask = {
                                viewModel.deleteTask(task.id)
                                Toast.makeText(context, "Task deleted", Toast.LENGTH_SHORT).show()
                            },
                            onShowDetails = { viewModel.showTaskDetails(task) },
                            onEditTask = { viewModel.showEditTask(task) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }

            // 2. TODAY SECTION
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
                if (uiState.todayTasks.isEmpty()) {
                    item(key = "today_empty_state") {
                        Text(
                            text = "No tasks for today",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(start = 12.dp, top = 2.dp, bottom = 6.dp)
                        )
                    }
                } else {
                    items(uiState.todayTasks, key = { it.id }) { task ->
                        TaskCard(
                            task = task,
                            onToggleCompletion = {
                                viewModel.toggleTask(task.id)
                                val msg = if (task.isCompleted) "Task marked as active" else "Task completed"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            },
                            onDeleteTask = {
                                viewModel.deleteTask(task.id)
                                Toast.makeText(context, "Task deleted", Toast.LENGTH_SHORT).show()
                            },
                            onShowDetails = { viewModel.showTaskDetails(task) },
                            onEditTask = { viewModel.showEditTask(task) },
                            modifier = Modifier.animateItem()
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
                            onToggleCompletion = {
                                viewModel.toggleTask(task.id)
                                val msg = if (task.isCompleted) "Task marked as active" else "Task completed"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            },
                            onDeleteTask = {
                                viewModel.deleteTask(task.id)
                                Toast.makeText(context, "Task deleted", Toast.LENGTH_SHORT).show()
                            },
                            onShowDetails = { viewModel.showTaskDetails(task) },
                            onEditTask = { viewModel.showEditTask(task) },
                            modifier = Modifier.animateItem()
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
                            onToggleCompletion = {
                                viewModel.toggleTask(task.id)
                                val msg = if (task.isCompleted) "Task marked as active" else "Task completed"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            },
                            onDeleteTask = {
                                viewModel.deleteTask(task.id)
                                Toast.makeText(context, "Task deleted", Toast.LENGTH_SHORT).show()
                            },
                            onShowDetails = { viewModel.showTaskDetails(task) },
                            onEditTask = { viewModel.showEditTask(task) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }

            // Empty state if all sections are empty due to filtering
            if (uiState.overdueTasks.isEmpty() && uiState.todayTasks.isEmpty() &&
                uiState.upcomingTasks.isEmpty() && uiState.completedTasks.isEmpty()
            ) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No tasks match your filter criteria.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Bottom spacer for FAB & Navigation bar padding
            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }

        // Filter Modal Bottom Sheet
        if (uiState.showFilterSheet) {
            FilterBottomSheet(
                initialFilter = uiState.filterCriteria,
                onApplyFilter = { criteria ->
                    viewModel.applyFilter(criteria)
                    Toast.makeText(context, "Filters applied", Toast.LENGTH_SHORT).show()
                },
                onDismiss = {
                    viewModel.closeFilterSheet()
                }
            )
        }

        // Sort Modal Bottom Sheet
        if (uiState.showSortSheet) {
            SortBottomSheet(
                initialSort = uiState.activeSort,
                onApplySort = { sort ->
                    viewModel.applySort(sort)
                    Toast.makeText(context, "Sorted by ${sort.displayName}", Toast.LENGTH_SHORT).show()
                },
                onDismiss = {
                    viewModel.closeSortSheet()
                }
            )
        }

        // Dialog for creating tasks
        if (uiState.showCreateDialog) {
            CreateTaskDialog(
                onDismiss = { viewModel.setShowCreateDialog(false) },
                onConfirm = { title, notes, priority, dueDate, dueDateMillis, dueTime, location, attachmentName, attachmentUri, alarmToneUri, alarmToneTitle, repeatType, repeatDays, repeatDates ->
                    viewModel.createTask(
                        title,
                        notes,
                        priority,
                        dueDate,
                        dueDateMillis,
                        dueTime,
                        location,
                        attachmentName,
                        attachmentUri,
                        alarmToneUri,
                        alarmToneTitle,
                        repeatType,
                        repeatDays,
                        repeatDates
                    )
                    Toast.makeText(context, "Task created successfully", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Bottom sheet for task details
        val detailsTask = uiState.selectedTaskForDetails
        if (detailsTask != null) {
            TaskDetailsBottomSheet(
                task = detailsTask,
                onDismiss = { viewModel.dismissTaskDetails() },
                onEditClicked = { viewModel.showEditTask(detailsTask) }
            )
        }

        // Dialog for editing tasks
        val editTask = uiState.selectedTaskForEdit
        if (editTask != null) {
            EditTaskDialog(
                task = editTask,
                onDismiss = { viewModel.dismissEditTask() },
                onConfirm = { title, notes, priority, dueDate, dueDateMillis, dueTime, location, attachmentName, attachmentUri, alarmToneUri, alarmToneTitle, repeatType, repeatDays, repeatDates ->
                    viewModel.updateTask(
                        editTask,
                        title,
                        notes,
                        priority,
                        dueDate,
                        dueDateMillis,
                        dueTime,
                        location,
                        attachmentName,
                        attachmentUri,
                        alarmToneUri,
                        alarmToneTitle,
                        repeatType,
                        repeatDays,
                        repeatDates
                    )
                    Toast.makeText(context, "Task updated successfully", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}
