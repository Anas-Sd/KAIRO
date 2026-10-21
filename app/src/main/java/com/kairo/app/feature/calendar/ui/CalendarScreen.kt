package com.kairo.app.feature.calendar.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairo.app.data.model.Priority
import com.kairo.app.data.model.Task
import com.kairo.app.data.repository.TaskRepository
import com.kairo.app.feature.habits.data.HabitRepository
import com.kairo.app.feature.habits.model.Habit
import com.kairo.app.feature.habits.model.HabitDayRecord
import com.kairo.app.feature.habits.model.HabitDayState
import com.kairo.app.feature.habits.model.HabitNoteHelper
import com.kairo.app.feature.habits.model.HabitRepeatType
import com.kairo.app.feature.habits.model.HabitType
import com.kairo.app.feature.habits.ui.HabitIncrementNoteBottomSheet
import com.kairo.app.ui.components.AppHeader
import com.kairo.app.ui.components.SettingsDialog
import com.kairo.app.ui.theme.KairoBackground
import com.kairo.app.ui.theme.KairoCardSurface
import com.kairo.app.ui.theme.KairoPrimary
import com.kairo.app.ui.theme.KairoPrimaryContainer
import com.kairo.app.ui.theme.KairoSecondary
import com.kairo.app.ui.theme.KairoSurfaceContainer
import com.kairo.app.ui.theme.KairoSurfaceContainerHigh
import com.kairo.app.ui.theme.KairoSurfaceContainerHighest
import com.kairo.app.ui.theme.KairoTertiary
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.MonthDay
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private enum class CalendarViewFilter {
    ALL, TASKS, LISTS, HABITS
}

private data class CalendarActiveNoteDialog(
    val habit: Habit,
    val date: LocalDate,
    val state: HabitDayState,
    val initialNote: String? = null,
    val isAppend: Boolean = false,
    val countIndex: Int? = null
)

@Composable
fun CalendarScreen(
    resetTrigger: Int = 0,
    modifier: Modifier = Modifier
) {
    // Current displayed YearMonth and active selected Date
    var currentYearMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }

    val listState = rememberLazyListState()

    // Dialog & UI states
    var showYearMonthPicker by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showQuickAddTaskDialog by remember { mutableStateOf(false) }
    var activeHabitNoteDialog by remember { mutableStateOf<CalendarActiveNoteDialog?>(null) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(CalendarViewFilter.ALL) }

    LaunchedEffect(resetTrigger) {
        if (resetTrigger > 0) {
            currentYearMonth = YearMonth.now()
            selectedDate = LocalDate.now()
            selectedFilter = CalendarViewFilter.ALL
            isSearchActive = false
            searchQuery = ""
            showYearMonthPicker = false
            showSettingsDialog = false
            showQuickAddTaskDialog = false
            activeHabitNoteDialog = null
            listState.animateScrollToItem(0)
        }
    }

    // Collapsible states (default collapsed as requested)
    var tasksExpanded by remember { mutableStateOf(false) }
    var listsExpanded by remember { mutableStateOf(false) }
    var habitsExpanded by remember { mutableStateOf(false) }

    // Live Data Sources
    val allTasks by TaskRepository.tasks.collectAsState(initial = emptyList())
    val habitsList by HabitRepository.habitsList.collectAsState()
    val logsMap by HabitRepository.logsMap.collectAsState()

    // 1. Standalone Tasks scheduled for selectedDate (no children, no parent list)
    val dayTasks = remember(allTasks, selectedDate, searchQuery) {
        allTasks.filter { task ->
            task.parentId == null &&
            allTasks.none { it.parentId == task.id } &&
            isTaskScheduledForDate(task, selectedDate) &&
            (searchQuery.isBlank() || task.title.contains(searchQuery.trim(), ignoreCase = true))
        }
    }

    // 2. Lists / Projects relevant for selectedDate (tasks that HAVE subtasks)
    val dayLists = remember(allTasks, selectedDate, searchQuery) {
        val parentTasks = allTasks.filter { task ->
            task.parentId == null && allTasks.any { it.parentId == task.id }
        }
        parentTasks.filter { parent ->
            val subtasks = allTasks.filter { it.parentId == parent.id }
            val isParentScheduled = isTaskScheduledForDate(parent, selectedDate)
            val hasScheduledSubtask = subtasks.any { isTaskScheduledForDate(it, selectedDate) }
            val hasActiveSubtasks = subtasks.any { !it.isCompleted }
            val matchesSearch = searchQuery.isBlank() ||
                parent.title.contains(searchQuery.trim(), ignoreCase = true) ||
                subtasks.any { it.title.contains(searchQuery.trim(), ignoreCase = true) }

            matchesSearch && (isParentScheduled || hasScheduledSubtask || (selectedDate == LocalDate.now() && hasActiveSubtasks))
        }
    }

    // 3. Habits scheduled for selectedDate
    val dayHabits = remember(habitsList, logsMap, selectedDate, searchQuery) {
        habitsList.filter { habit ->
            isHabitScheduledForDate(habit, selectedDate) &&
            (searchQuery.isBlank() || habit.title.contains(searchQuery.trim(), ignoreCase = true))
        }
    }

    // Progress metrics for selectedDate
    val tasksCompletedCount = dayTasks.count { it.isCompleted }
    val tasksTotalCount = dayTasks.size

    val allSubtasksInDayLists = dayLists.flatMap { parent -> allTasks.filter { it.parentId == parent.id } }
    val subtasksCompletedCount = allSubtasksInDayLists.count { it.isCompleted }
    val subtasksTotalCount = allSubtasksInDayLists.size

    val selectedDateStr = selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
    val habitsCompletedCount = dayHabits.count { habit ->
        val log = logsMap[habit.id]?.get(selectedDateStr)
        when (habit.type) {
            HabitType.YES_NO -> log?.state == HabitDayState.DONE
            HabitType.TARGET -> (log?.value ?: 0) >= habit.targetValue || log?.state == HabitDayState.DONE
        }
    }
    val habitsTotalCount = dayHabits.size

    val totalItems = tasksTotalCount + subtasksTotalCount + habitsTotalCount
    val totalDone = tasksCompletedCount + subtasksCompletedCount + habitsCompletedCount
    val overallProgressFraction = if (totalItems > 0) totalDone.toFloat() / totalItems.toFloat() else 0f

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KairoBackground)
    ) {
        // App Header
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
            searchPlaceholder = "Search day tasks, lists & habits...",
            onSettingsClick = { showSettingsDialog = true }
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Month Header Controls: Previous Month, Clickable Month-Year Picker, Next Month, Today jump
            item {
                Spacer(modifier = Modifier.height(2.dp))
                CalendarMonthHeader(
                    yearMonth = currentYearMonth,
                    onPrevMonth = { currentYearMonth = currentYearMonth.minusMonths(1) },
                    onNextMonth = { currentYearMonth = currentYearMonth.plusMonths(1) },
                    onMonthYearClick = { showYearMonthPicker = true },
                    onJumpToToday = {
                        val today = LocalDate.now()
                        currentYearMonth = YearMonth.from(today)
                        selectedDate = today
                    }
                )
            }

            // Month Grid Card
            item {
                MonthCalendarGrid(
                    yearMonth = currentYearMonth,
                    selectedDate = selectedDate,
                    onDateSelected = { selectedDate = it },
                    allTasks = allTasks,
                    habitsList = habitsList,
                    logsMap = logsMap
                )
            }

            // Quick View Filter Chips (All, Tasks, Lists, Habits)
            item {
                FilterCategoryChips(
                    selectedFilter = selectedFilter,
                    onSelectFilter = { selectedFilter = it }
                )
            }

            // Selected Day Summary Card
            item {
                SelectedDaySummaryCard(
                    date = selectedDate,
                    totalDone = totalDone,
                    totalItems = totalItems,
                    progressFraction = overallProgressFraction,
                    tasksDone = tasksCompletedCount,
                    tasksTotal = tasksTotalCount,
                    listsCount = dayLists.size,
                    subtasksDone = subtasksCompletedCount,
                    subtasksTotal = subtasksTotalCount,
                    habitsDone = habitsCompletedCount,
                    habitsTotal = habitsTotalCount,
                    onQuickAddTask = { showQuickAddTaskDialog = true }
                )
            }

            // SECTION 1: TASKS (Collapsible)
            if (selectedFilter == CalendarViewFilter.ALL || selectedFilter == CalendarViewFilter.TASKS) {
                item {
                    CollapsibleSectionCard(
                        title = "Tasks",
                        icon = Icons.Default.TaskAlt,
                        badgeText = "$tasksCompletedCount / $tasksTotalCount done",
                        progressFraction = if (tasksTotalCount > 0) tasksCompletedCount.toFloat() / tasksTotalCount else 0f,
                        isExpanded = tasksExpanded,
                        onToggleExpand = { tasksExpanded = !tasksExpanded },
                        accentColor = KairoPrimary
                    ) {
                        if (dayTasks.isEmpty()) {
                            Text(
                                text = "No standalone tasks scheduled for this day.",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8),
                                modifier = Modifier.padding(14.dp)
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                dayTasks.forEach { task ->
                                    CalendarTaskItemRow(
                                        task = task,
                                        onToggleCompletion = { TaskRepository.toggleTaskCompletion(task.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 2: LISTS & PROJECTS (Collapsible)
            if (selectedFilter == CalendarViewFilter.ALL || selectedFilter == CalendarViewFilter.LISTS) {
                item {
                    CollapsibleSectionCard(
                        title = "Lists & Projects",
                        icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                        badgeText = "${dayLists.size} active · $subtasksCompletedCount/$subtasksTotalCount items",
                        progressFraction = if (subtasksTotalCount > 0) subtasksCompletedCount.toFloat() / subtasksTotalCount else 0f,
                        isExpanded = listsExpanded,
                        onToggleExpand = { listsExpanded = !listsExpanded },
                        accentColor = KairoTertiary
                    ) {
                        if (dayLists.isEmpty()) {
                            Text(
                                text = "No project lists active on this day.",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8),
                                modifier = Modifier.padding(14.dp)
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                dayLists.forEach { parent ->
                                    val subtasks = allTasks.filter { it.parentId == parent.id }
                                    CalendarListItemCard(
                                        parentTask = parent,
                                        subtasks = subtasks,
                                        onToggleSubtask = { subtaskId -> TaskRepository.toggleTaskCompletion(subtaskId) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 3: HABITS (Collapsible)
            if (selectedFilter == CalendarViewFilter.ALL || selectedFilter == CalendarViewFilter.HABITS) {
                item {
                    CollapsibleSectionCard(
                        title = "Habits",
                        icon = Icons.Default.LocalFireDepartment,
                        badgeText = "$habitsCompletedCount / $habitsTotalCount done",
                        progressFraction = if (habitsTotalCount > 0) habitsCompletedCount.toFloat() / habitsTotalCount else 0f,
                        isExpanded = habitsExpanded,
                        onToggleExpand = { habitsExpanded = !habitsExpanded },
                        accentColor = KairoSecondary
                    ) {
                        if (dayHabits.isEmpty()) {
                            Text(
                                text = "No habits scheduled for this day.",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8),
                                modifier = Modifier.padding(14.dp)
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                dayHabits.forEach { habit ->
                                    val rawLog = logsMap[habit.id]?.get(selectedDateStr)
                                    val isPastDay = selectedDate.isBefore(LocalDate.now())
                                    val log = rawLog ?: if (isPastDay) HabitDayRecord(state = HabitDayState.MISSED, value = 0, note = "missed") else HabitDayRecord()
                                    CalendarHabitItemRow(
                                        habit = habit,
                                        date = selectedDate,
                                        record = log,
                                        onIncrement = {
                                            val targetVal = habit.targetValue.coerceAtLeast(1)
                                            if (log.value < targetVal) {
                                                val nextVal = log.value + 1
                                                HabitRepository.incrementHabitDay(habit.id, selectedDate, 1)
                                                if (habit.type == HabitType.TARGET) {
                                                    activeHabitNoteDialog = CalendarActiveNoteDialog(
                                                        habit = habit,
                                                        date = selectedDate,
                                                        state = HabitDayState.DONE,
                                                        initialNote = null,
                                                        countIndex = nextVal
                                                    )
                                                }
                                            }
                                        },
                                        onDecrement = {
                                            HabitRepository.incrementHabitDay(habit.id, selectedDate, -1)
                                        },
                                        onToggleDone = {
                                            if (isPastDay) {
                                                // For previous days, ONLY 2 options: DONE / MISSED (no unmarked box)
                                                if (log.state == HabitDayState.DONE) {
                                                    HabitRepository.setHabitDayState(habit.id, selectedDate, HabitDayState.MISSED)
                                                    activeHabitNoteDialog = CalendarActiveNoteDialog(
                                                        habit = habit,
                                                        date = selectedDate,
                                                        state = HabitDayState.MISSED,
                                                        initialNote = log.note,
                                                        isAppend = false
                                                    )
                                                } else {
                                                    HabitRepository.setHabitDayState(habit.id, selectedDate, HabitDayState.DONE)
                                                    activeHabitNoteDialog = CalendarActiveNoteDialog(
                                                        habit = habit,
                                                        date = selectedDate,
                                                        state = HabitDayState.DONE,
                                                        initialNote = log.note,
                                                        isAppend = false
                                                    )
                                                }
                                            } else {
                                                if (log.state == HabitDayState.DONE) {
                                                    HabitRepository.setHabitDayState(habit.id, selectedDate, HabitDayState.MISSED)
                                                    activeHabitNoteDialog = CalendarActiveNoteDialog(
                                                        habit = habit,
                                                        date = selectedDate,
                                                        state = HabitDayState.MISSED,
                                                        initialNote = log.note,
                                                        isAppend = false
                                                    )
                                                } else if (log.state == HabitDayState.MISSED) {
                                                    HabitRepository.setHabitDayState(habit.id, selectedDate, HabitDayState.UNMARKED, clearNote = true)
                                                } else {
                                                    HabitRepository.setHabitDayState(habit.id, selectedDate, HabitDayState.DONE)
                                                    activeHabitNoteDialog = CalendarActiveNoteDialog(
                                                        habit = habit,
                                                        date = selectedDate,
                                                        state = HabitDayState.DONE,
                                                        initialNote = log.note,
                                                        isAppend = false
                                                    )
                                                }
                                            }
                                        },
                                        onNoteClick = {
                                            if (!selectedDate.isAfter(LocalDate.now())) {
                                                activeHabitNoteDialog = CalendarActiveNoteDialog(
                                                    habit = habit,
                                                    date = selectedDate,
                                                    state = if (log.state == HabitDayState.MISSED) HabitDayState.MISSED else HabitDayState.DONE,
                                                    initialNote = log.note,
                                                    isAppend = false
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // Interactive Year & Month Picker Dialog
    if (showYearMonthPicker) {
        YearMonthPickerDialog(
            initialYearMonth = currentYearMonth,
            onDismiss = { showYearMonthPicker = false },
            onSelect = { selectedYm ->
                currentYearMonth = selectedYm
                showYearMonthPicker = false
            }
        )
    }

    // Quick Add Task Dialog for the selected date
    if (showQuickAddTaskDialog) {
        QuickAddTaskForDateDialog(
            targetDate = selectedDate,
            onDismiss = { showQuickAddTaskDialog = false },
            onConfirm = { taskTitle ->
                val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
                val task = Task(
                    title = taskTitle,
                    dueDate = selectedDate.format(formatter),
                    dueDateMillis = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    priority = Priority.MEDIUM
                )
                TaskRepository.addTask(task)
                showQuickAddTaskDialog = false
            }
        )
    }

    // Settings Dialog
    if (showSettingsDialog) {
        SettingsDialog(onDismissRequest = { showSettingsDialog = false })
    }

    // Habit Note BottomSheet for selected date
    activeHabitNoteDialog?.let { dialogState ->
        HabitIncrementNoteBottomSheet(
            habitTitle = dialogState.habit.title,
            habitId = dialogState.habit.id,
            countIndex = dialogState.countIndex,
            targetValue = dialogState.habit.targetValue,
            targetUnit = dialogState.habit.targetUnit,
            isMissed = dialogState.state == HabitDayState.MISSED,
            initialNote = dialogState.initialNote,
            onDismiss = { activeHabitNoteDialog = null },
            onSaveNote = { noteText ->
                if (dialogState.countIndex != null) {
                    HabitRepository.setIncrementNote(dialogState.habit.id, dialogState.date, dialogState.countIndex, noteText)
                } else {
                    HabitRepository.setHabitDayNote(dialogState.habit.id, dialogState.date, noteText)
                }
                activeHabitNoteDialog = null
            }
        )
    }
}

// ==========================================================================================
// 1. Month Header & Navigation
// ==========================================================================================

@Composable
private fun CalendarMonthHeader(
    yearMonth: YearMonth,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onMonthYearClick: () -> Unit,
    onJumpToToday: () -> Unit
) {
    val monthTitle = "${yearMonth.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${yearMonth.year}"
    val isCurrentMonth = yearMonth == YearMonth.now()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Month & Year Clickable Selector
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable { onMonthYearClick() }
                .background(Color.White.copy(alpha = 0.04f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = monthTitle,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Select month & year",
                tint = KairoPrimary,
                modifier = Modifier.size(20.dp)
            )
        }

        // Action controls: Jump to Today + Month Navigation Arrows
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Jump to Today button
            Surface(
                onClick = onJumpToToday,
                shape = RoundedCornerShape(8.dp),
                color = if (isCurrentMonth) KairoPrimaryContainer.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
                border = BorderStroke(1.dp, if (isCurrentMonth) KairoPrimary.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Today,
                        contentDescription = "Today",
                        tint = if (isCurrentMonth) KairoPrimary else Color(0xFFC8C4D9),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Today",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isCurrentMonth) KairoPrimary else Color(0xFFC8C4D9)
                    )
                }
            }

            // Previous Month (<)
            IconButton(
                onClick = onPrevMonth,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.04f))
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = "Previous Month",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Next Month (>)
            IconButton(
                onClick = onNextMonth,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.04f))
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Next Month",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ==========================================================================================
// 2. Month Calendar Grid with Activity Dots & Heatmap
// ==========================================================================================

@Composable
private fun MonthCalendarGrid(
    yearMonth: YearMonth,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    allTasks: List<Task>,
    habitsList: List<Habit>,
    logsMap: Map<String, Map<String, HabitDayRecord>>
) {
    val today = LocalDate.now()
    val firstOfMonth = yearMonth.atDay(1)
    val daysInMonth = yearMonth.lengthOfMonth()

    // Monday start: 1 (Mon) to 7 (Sun)
    val startDayOffset = (firstOfMonth.dayOfWeek.value - 1) % 7
    val totalCells = startDayOffset + daysInMonth
    val numRows = (totalCells + 6) / 7

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = KairoSurfaceContainer),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Day-of-week header row (Mon to Sun)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach { dayLabel ->
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = dayLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(thickness = 1.dp, color = Color.White.copy(alpha = 0.06f))
            Spacer(modifier = Modifier.height(8.dp))

            // Calendar Day Cells Rows
            for (row in 0 until numRows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    for (col in 0..6) {
                        val cellIndex = row * 7 + col
                        val dayNumber = cellIndex - startDayOffset + 1

                        if (dayNumber in 1..daysInMonth) {
                            val cellDate = yearMonth.atDay(dayNumber)
                            val isSelected = cellDate == selectedDate
                            val isToday = cellDate == today

                            // Count activity on this cell date
                            val cellDateStr = cellDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                            val taskCount = allTasks.count { isTaskScheduledForDate(it, cellDate) }
                            val habitCount = habitsList.count { isHabitScheduledForDate(it, cellDate) }

                            val hasTasks = taskCount > 0
                            val hasHabits = habitCount > 0

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 3.dp, horizontal = 2.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .then(
                                        when {
                                            isSelected -> Modifier
                                                .background(KairoPrimary.copy(alpha = 0.22f))
                                                .border(1.5.dp, KairoPrimary, RoundedCornerShape(10.dp))
                                            isToday -> Modifier
                                                .background(Color.White.copy(alpha = 0.06f))
                                                .border(1.dp, KairoPrimary.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                            else -> Modifier
                                        }
                                    )
                                    .clickable { onDateSelected(cellDate) }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "$dayNumber",
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected || isToday) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = when {
                                            isSelected -> Color.White
                                            isToday -> KairoPrimary
                                            else -> Color(0xFFC8C4D9)
                                        }
                                    )

                                    Spacer(modifier = Modifier.height(3.dp))

                                    // Activity Dots Indicator (Tasks: Blue/Primary, Habits: Green/Secondary)
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (hasTasks) {
                                            Box(
                                                modifier = Modifier
                                                    .size(4.dp)
                                                    .clip(CircleShape)
                                                    .background(KairoPrimary)
                                            )
                                        }
                                        if (hasHabits) {
                                            Box(
                                                modifier = Modifier
                                                    .size(4.dp)
                                                    .clip(CircleShape)
                                                    .background(KairoSecondary)
                                            )
                                        }
                                        if (!hasTasks && !hasHabits) {
                                            Box(modifier = Modifier.size(4.dp))
                                        }
                                    }
                                }
                            }
                        } else {
                            // Empty space before 1st or after last day
                            Box(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

// ==========================================================================================
// 3. Category Filter Chips
// ==========================================================================================

@Composable
private fun FilterCategoryChips(
    selectedFilter: CalendarViewFilter,
    onSelectFilter: (CalendarViewFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CalendarViewFilter.values().forEach { filter ->
            val isSelected = filter == selectedFilter
            Surface(
                onClick = { onSelectFilter(filter) },
                shape = RoundedCornerShape(100.dp),
                color = if (isSelected) KairoPrimary.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.04f),
                border = BorderStroke(
                    1.dp,
                    if (isSelected) KairoPrimary.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.08f)
                )
            ) {
                Text(
                    text = when (filter) {
                        CalendarViewFilter.ALL -> "All Items"
                        CalendarViewFilter.TASKS -> "Tasks"
                        CalendarViewFilter.LISTS -> "Lists"
                        CalendarViewFilter.HABITS -> "Habits"
                    },
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) KairoPrimary else Color(0xFFC8C4D9),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

// ==========================================================================================
// 4. Selected Day Summary Card
// ==========================================================================================

@Composable
private fun SelectedDaySummaryCard(
    date: LocalDate,
    totalDone: Int,
    totalItems: Int,
    progressFraction: Float,
    tasksDone: Int,
    tasksTotal: Int,
    listsCount: Int,
    subtasksDone: Int,
    subtasksTotal: Int,
    habitsDone: Int,
    habitsTotal: Int,
    onQuickAddTask: () -> Unit
) {
    val today = LocalDate.now()
    val dateLabel = when {
        date == today -> "Today"
        date == today.plusDays(1) -> "Tomorrow"
        date == today.minusDays(1) -> "Yesterday"
        else -> date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    }
    val fullFormattedDate = "$dateLabel, ${date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))}"

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = KairoSurfaceContainerHigh),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Date Title + Quick Add Task Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = fullFormattedDate,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = if (totalItems > 0) "$totalDone of $totalItems completed (${(progressFraction * 100).toInt()}%)" else "Nothing scheduled",
                        fontSize = 11.sp,
                        color = if (progressFraction >= 1f && totalItems > 0) KairoSecondary else Color(0xFF94A3B8)
                    )
                }

                // Quick Add for this day
                Surface(
                    onClick = onQuickAddTask,
                    shape = RoundedCornerShape(8.dp),
                    color = KairoPrimary.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, KairoPrimary.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = KairoPrimary, modifier = Modifier.size(13.dp))
                        Text("+ Task", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = KairoPrimary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Overall Linear Progress Bar
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (progressFraction >= 1f) KairoSecondary else KairoPrimary,
                trackColor = Color.White.copy(alpha = 0.08f)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Stat Badges Row: Tasks | Lists | Habits
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricChip(label = "Tasks", value = "$tasksDone/$tasksTotal", color = KairoPrimary, modifier = Modifier.weight(1f))
                MetricChip(label = "Lists", value = "$subtasksDone/$subtasksTotal", color = KairoTertiary, modifier = Modifier.weight(1f))
                MetricChip(label = "Habits", value = "$habitsDone/$habitsTotal", color = KairoSecondary, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MetricChip(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, fontSize = 9.sp, fontWeight = FontWeight.Medium, color = Color(0xFF94A3B8))
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

// ==========================================================================================
// 5. Collapsible Section Card Framework
// ==========================================================================================

@Composable
private fun CollapsibleSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badgeText: String,
    progressFraction: Float,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    accentColor: Color,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = KairoCardSurface),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // Header Row (Clickable to toggle collapse)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(accentColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                    }

                    Column {
                        Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(text = badgeText, fontSize = 11.sp, color = Color(0xFF94A3B8))
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Mini progress indicator in header
                    Box(
                        modifier = Modifier
                            .width(42.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressFraction)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (progressFraction >= 1f) KairoSecondary else accentColor)
                        )
                    }

                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    HorizontalDivider(thickness = 1.dp, color = Color.White.copy(alpha = 0.06f))
                    Spacer(modifier = Modifier.height(8.dp))
                    content()
                }
            }
        }
    }
}

// ==========================================================================================
// 6. Item Rows: Tasks, Lists, Habits
// ==========================================================================================

@Composable
private fun CalendarTaskItemRow(
    task: Task,
    onToggleCompletion: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = KairoSurfaceContainerHigh,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Checkbox
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (task.isCompleted) KairoSecondary else Color.White.copy(alpha = 0.04f))
                    .border(
                        1.2.dp,
                        if (task.isCompleted) KairoSecondary else Color.White.copy(alpha = 0.20f),
                        RoundedCornerShape(6.dp)
                    )
                    .clickable { onToggleCompletion() },
                contentAlignment = Alignment.Center
            ) {
                if (task.isCompleted) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = "Done", tint = Color(0xFF003822), modifier = Modifier.size(14.dp))
                }
            }

            // Task Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (task.isCompleted) Color(0xFF64748B) else Color.White,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!task.notes.isNullOrBlank()) {
                    Text(
                        text = task.notes,
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Priority badge
            if (task.priority != Priority.LOW) {
                val pColor = when (task.priority) {
                    Priority.URGENT -> Color(0xFFEF4444)
                    Priority.HIGH -> Color(0xFFF97316)
                    Priority.MEDIUM -> Color(0xFFEAB308)
                    else -> Color(0xFF94A3B8)
                }
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = pColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, pColor.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = task.priority.label,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = pColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarListItemCard(
    parentTask: Task,
    subtasks: List<Task>,
    onToggleSubtask: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val doneCount = subtasks.count { it.isCompleted }
    val totalCount = subtasks.size
    val fraction = if (totalCount > 0) doneCount.toFloat() / totalCount else 0f

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = KairoSurfaceContainerHigh,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Parent Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "Project List",
                        tint = KairoTertiary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = parentTask.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "$doneCount / $totalCount",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (fraction >= 1f) KairoSecondary else Color(0xFFC8C4D9)
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Subtask Mini Progress Bar
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = if (fraction >= 1f) KairoSecondary else KairoTertiary,
                trackColor = Color.White.copy(alpha = 0.08f)
            )

            // Expanded Subtasks List
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    subtasks.forEach { subtask ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.03f))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (subtask.isCompleted) KairoSecondary else Color.White.copy(alpha = 0.04f))
                                    .border(1.dp, if (subtask.isCompleted) KairoSecondary else Color.White.copy(alpha = 0.20f), RoundedCornerShape(4.dp))
                                    .clickable { onToggleSubtask(subtask.id) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (subtask.isCompleted) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF003822), modifier = Modifier.size(11.dp))
                                }
                            }
                            Text(
                                text = subtask.title,
                                fontSize = 12.sp,
                                color = if (subtask.isCompleted) Color(0xFF64748B) else Color.White,
                                textDecoration = if (subtask.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarHabitItemRow(
    habit: Habit,
    date: LocalDate,
    record: HabitDayRecord,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onToggleDone: () -> Unit,
    onNoteClick: () -> Unit = {}
) {
    val isFuture = date.isAfter(LocalDate.now())
    val isTarget = habit.type == HabitType.TARGET
    val isDone = if (isTarget) record.value >= habit.targetValue || record.state == HabitDayState.DONE else record.state == HabitDayState.DONE
    val isMissed = record.state == HabitDayState.MISSED

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = KairoSurfaceContainerHigh,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Habit Title & Note Info (tap to view or edit notes)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = !isFuture) { onNoteClick() }
            ) {
                Text(
                    text = habit.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!record.note.isNullOrBlank()) {
                    Text(
                        text = "Note: ${HabitNoteHelper.formatDisplayNote(record.note)}",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Actions for Future Date vs Present/Past Date
            if (isFuture) {
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = Color.White.copy(alpha = 0.04f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = "Locked", tint = Color(0xFF94A3B8), modifier = Modifier.size(11.dp))
                        Text("Locked", fontSize = 10.sp, color = Color(0xFF94A3B8))
                    }
                }
            } else if (isTarget) {
                // Interactive Stepper for Target habit
                val canIncrement = record.value < habit.targetValue
                val canDecrement = record.value > 0

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White.copy(alpha = 0.04f),
                    border = BorderStroke(1.dp, if (isDone) KairoSecondary.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.1f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .clickable(enabled = canDecrement) { onDecrement() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Remove,
                                contentDescription = "Decrease",
                                tint = if (canDecrement) Color.White else Color.Gray.copy(alpha = 0.4f),
                                modifier = Modifier.size(12.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        val isPast = date.isBefore(LocalDate.now())
                        val isItemMissed = isMissed || (isPast && !isDone)
                        Text(
                            text = if (isItemMissed) "${record.value}✕/${habit.targetValue}" else "${record.value}/${habit.targetValue}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDone) KairoSecondary else if (isItemMissed) Color(0xFFFB7185) else Color.White
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (canIncrement) KairoPrimary.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.03f))
                                .clickable(enabled = canIncrement) { onIncrement() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Increase",
                                tint = if (canIncrement) KairoPrimary else Color.Gray.copy(alpha = 0.4f),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            } else {
                // Yes/No status button
                Surface(
                    onClick = onToggleDone,
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        isDone -> KairoSecondary.copy(alpha = 0.20f)
                        isMissed -> Color(0xFFFFB77D).copy(alpha = 0.20f)
                        else -> Color.White.copy(alpha = 0.04f)
                    },
                    border = BorderStroke(
                        1.dp,
                        when {
                            isDone -> KairoSecondary
                            isMissed -> Color(0xFFFFB77D)
                            else -> Color.White.copy(alpha = 0.12f)
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isDone) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = KairoSecondary, modifier = Modifier.size(13.dp))
                        }
                        Text(
                            text = when {
                                isDone -> "Done"
                                isMissed -> "Missed"
                                else -> "Mark"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                isDone -> KairoSecondary
                                isMissed -> Color(0xFFFFB77D)
                                else -> Color(0xFFC8C4D9)
                            }
                        )
                    }
                }
            }
        }
    }
}

// ==========================================================================================
// 7. Interactive Year & Month Picker Dialog
// ==========================================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun YearMonthPickerDialog(
    initialYearMonth: YearMonth,
    onDismiss: () -> Unit,
    onSelect: (YearMonth) -> Unit
) {
    var selectedYear by remember { mutableIntStateOf(initialYearMonth.year) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KairoSurfaceContainer,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Select Month & Year", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Year selector stepper
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { selectedYear-- },
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.05f))
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Year", tint = Color.White)
                    }

                    Text(
                        text = "$selectedYear",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = KairoPrimary
                    )

                    IconButton(
                        onClick = { selectedYear++ },
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.05f))
                    ) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next Year", tint = Color.White)
                    }
                }

                HorizontalDivider(thickness = 1.dp, color = Color.White.copy(alpha = 0.08f))

                // 4x3 Month Grid
                val months = Month.values()
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 3
                ) {
                    months.forEach { month ->
                        val isSelected = (month.value == initialYearMonth.monthValue && selectedYear == initialYearMonth.year)
                        Surface(
                            onClick = { onSelect(YearMonth.of(selectedYear, month)) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) KairoPrimaryContainer.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.04f),
                            border = BorderStroke(1.dp, if (isSelected) KairoPrimary else Color.White.copy(alpha = 0.08f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) KairoPrimary else Color(0xFFC8C4D9)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )
}

// ==========================================================================================
// 8. Quick Add Task Dialog for Selected Date
// ==========================================================================================

@Composable
private fun QuickAddTaskForDateDialog(
    targetDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    val formattedDate = targetDate.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH))

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KairoSurfaceContainer,
        title = {
            Column {
                Text(text = "Schedule Task", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = "for $formattedDate", fontSize = 12.sp, color = KairoPrimary)
            }
        },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Task Title", fontSize = 12.sp) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = KairoPrimary,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) onConfirm(title.trim())
                },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = KairoPrimaryContainer)
            ) {
                Text("Schedule", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF94A3B8))
            }
        }
    )
}

// ==========================================================================================
// 9. Schedule Matcher Helpers
// ==========================================================================================

private fun isTaskScheduledForDate(task: Task, date: LocalDate): Boolean {
    // 1. Check repeat type
    val repeatType = task.repeatType
    if (!repeatType.isNullOrBlank() && repeatType != "NONE") {
        when {
            repeatType.equals("DAILY", ignoreCase = true) -> return true
            repeatType.contains("DAYS", ignoreCase = true) || !task.repeatDays.isNullOrBlank() -> {
                val dayCode = when (date.dayOfWeek) {
                    DayOfWeek.MONDAY -> "MON"
                    DayOfWeek.TUESDAY -> "TUE"
                    DayOfWeek.WEDNESDAY -> "WED"
                    DayOfWeek.THURSDAY -> "THU"
                    DayOfWeek.FRIDAY -> "FRI"
                    DayOfWeek.SATURDAY -> "SAT"
                    DayOfWeek.SUNDAY -> "SUN"
                }
                val daysList = task.repeatDays?.split(",")?.map { it.trim().uppercase() } ?: emptyList()
                if (daysList.contains(dayCode)) return true
            }
            repeatType.contains("DATES", ignoreCase = true) || !task.repeatDates.isNullOrBlank() -> {
                val dateOfMonthStr = date.dayOfMonth.toString()
                val datesList = task.repeatDates?.split(",")?.map { it.trim() } ?: emptyList()
                if (datesList.contains(dateOfMonthStr)) return true

                val monthDayFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
                if (datesList.any { it.equals(date.format(monthDayFormatter), ignoreCase = true) }) return true
            }
        }
    }

    // 2. Exact dueDateMillis
    if (task.dueDateMillis != null) {
        val taskDate = Instant.ofEpochMilli(task.dueDateMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        if (taskDate == date) return true
    }

    // 3. Due Date String (e.g. "Today", "Tomorrow", "Yesterday", or formatted date)
    val today = LocalDate.now()
    val dueStr = task.dueDate.trim()
    if (dueStr.equals("Today", ignoreCase = true) && date == today) return true
    if (dueStr.equals("Tomorrow", ignoreCase = true) && date == today.plusDays(1)) return true
    if (dueStr.equals("Yesterday", ignoreCase = true) && date == today.minusDays(1)) return true

    val formats = listOf(
        DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("MMM dd", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH)
    )
    for (fmt in formats) {
        try {
            val parsed = LocalDate.parse(dueStr, fmt)
            if (parsed == date) return true
        } catch (_: Exception) {}
        try {
            val parsedMd = MonthDay.parse(dueStr, fmt)
            if (parsedMd.month == date.month && parsedMd.dayOfMonth == date.dayOfMonth) return true
        } catch (_: Exception) {}
    }

    // 4. If completed on this date:
    if (task.completedAt != null) {
        val completedDate = Instant.ofEpochMilli(task.completedAt)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        if (completedDate == date) return true
    }

    return false
}

private fun isHabitScheduledForDate(habit: Habit, date: LocalDate): Boolean {
    val habitCreatedDate = runCatching {
        Instant.ofEpochMilli(habit.createdAt)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
    }.getOrDefault(LocalDate.now())

    val logs = HabitRepository.getHabitLogs(habit.id)
    val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
    if (logs.containsKey(dateStr)) return true

    if (date.isBefore(habitCreatedDate)) return false

    return when (habit.repeatType) {
        HabitRepeatType.EVERYDAY -> true
        HabitRepeatType.SPECIFIC_DAYS -> habit.repeatDays.contains(date.dayOfWeek.value)
        HabitRepeatType.WEEKLY_X -> true
    }
}
