package com.kairo.app.feature.habits.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairo.app.feature.habits.data.HabitRepository
import com.kairo.app.feature.habits.model.Habit
import com.kairo.app.feature.habits.model.HabitDayRecord
import com.kairo.app.feature.habits.model.HabitDayState
import com.kairo.app.feature.habits.model.HabitType
import com.kairo.app.feature.habits.model.HabitWithStats
import com.kairo.app.ui.components.AppHeader
import com.kairo.app.ui.components.SettingsDialog
import com.kairo.app.ui.theme.KairoBackground
import com.kairo.app.ui.theme.KairoError
import com.kairo.app.ui.theme.KairoPrimary
import com.kairo.app.ui.theme.KairoPrimaryContainer
import com.kairo.app.ui.theme.KairoSecondary
import com.kairo.app.ui.theme.KairoSurfaceContainer
import com.kairo.app.ui.theme.KairoSurfaceContainerHigh
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private data class ActiveIncrementNote(
    val habit: Habit,
    val date: LocalDate,
    val countIndex: Int? = null,
    val isMissed: Boolean = false,
    val initialNote: String? = null
)

private data class ActiveTargetDayPicker(
    val habit: Habit,
    val date: LocalDate,
    val initialRecord: HabitDayRecord
)

@Composable
fun HabitsScreen(
    modifier: Modifier = Modifier
) {
    val habitsWithStats by HabitRepository.habitsWithStats.collectAsState()
    val disciplineOverview by HabitRepository.disciplineOverview.collectAsState()
    val context = LocalContext.current

    // Automatically check and mark any past scheduled habits that were not completed as missed
    LaunchedEffect(Unit) {
        HabitRepository.autoMarkMissedPastDays()
    }

    var selectedHabitForDetail by remember { mutableStateOf<HabitWithStats?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var activeIncrementNote by remember { mutableStateOf<ActiveIncrementNote?>(null) }
    var activeTargetPicker by remember { mutableStateOf<ActiveTargetDayPicker?>(null) }

    // Search query state for Habits tab
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    // Selection mode state
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedHabitIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showCompleteConfirmDialog by remember { mutableStateOf(false) }

    // Drag-to-reorder state
    var draggingHabitId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var localReorderList by remember { mutableStateOf<List<HabitWithStats>?>(null) }

    // Filter habits according to search query (completed habits sink to bottom when not dragging)
    val activeList = if (draggingHabitId != null && localReorderList != null) {
        localReorderList!!
    } else {
        habitsWithStats.sortedBy { item ->
            val isDoneToday = item.todayProgressFraction >= 1f ||
                item.days.find { it.isToday }?.state == HabitDayState.DONE
            if (isDoneToday) 1 else 0
        }
    }

    val filteredHabits = remember(activeList, searchQuery) {
        if (searchQuery.isBlank()) {
            activeList
        } else {
            activeList.filter {
                it.habit.title.contains(searchQuery.trim(), ignoreCase = true)
            }
        }
    }

    fun buildDragHandleModifier(habitItem: HabitWithStats, currentList: List<HabitWithStats>): Modifier {
        return Modifier.pointerInput(habitItem.habit.id) {
            detectDragGestures(
                onDragStart = {
                    draggingHabitId = habitItem.habit.id
                    dragOffsetY = 0f
                    localReorderList = currentList
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    dragOffsetY += dragAmount.y
                    val list = localReorderList
                    val currentId = draggingHabitId
                    if (list != null && currentId != null) {
                        val currentIndex = list.indexOfFirst { it.habit.id == currentId }
                        val itemThreshold = 100f
                        if (dragOffsetY > itemThreshold && currentIndex in 0 until list.lastIndex) {
                            val mutable = list.toMutableList()
                            val targetIndex = currentIndex + 1
                            val item = mutable.removeAt(currentIndex)
                            mutable.add(targetIndex, item)
                            localReorderList = mutable
                            dragOffsetY -= itemThreshold
                        } else if (dragOffsetY < -itemThreshold && currentIndex > 0) {
                            val mutable = list.toMutableList()
                            val targetIndex = currentIndex - 1
                            val item = mutable.removeAt(currentIndex)
                            mutable.add(targetIndex, item)
                            localReorderList = mutable
                            dragOffsetY += itemThreshold
                        }
                    }
                },
                onDragEnd = {
                    localReorderList?.let { reordered ->
                        HabitRepository.reorderHabitsByIds(reordered.map { it.habit.id })
                    }
                    draggingHabitId = null
                    dragOffsetY = 0f
                    localReorderList = null
                },
                onDragCancel = {
                    draggingHabitId = null
                    dragOffsetY = 0f
                    localReorderList = null
                }
            )
        }
    }

    // Back button handling
    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedHabitIds = emptySet()
    }

    BackHandler(enabled = !isSelectionMode && isSearchActive) {
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

    // Main Habits List Screen - NO statusBarsPadding so it matches Tasks exactly
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(KairoBackground)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Header: Selection Mode vs Normal AppHeader
            if (isSelectionMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(KairoBackground)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = {
                            isSelectionMode = false
                            selectedHabitIds = emptySet()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel selection",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = if (selectedHabitIds.isEmpty()) "Select habits" else "${selectedHabitIds.size} selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Select All / Deselect All
                    TextButton(onClick = {
                        selectedHabitIds = if (selectedHabitIds.size == habitsWithStats.size) {
                            emptySet()
                        } else {
                            habitsWithStats.map { it.habit.id }.toSet()
                        }
                    }) {
                        Text(
                            text = if (selectedHabitIds.size == habitsWithStats.size) "Deselect All" else "Select All",
                            color = KairoPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
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
                                    text = "Select",
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.SelectAll,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                dismissMenu()
                                isSelectionMode = true
                                selectedHabitIds = emptySet()
                            }
                        )
                    }
                )
            }

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
                        val isBeingDragged = (item.habit.id == draggingHabitId)
                        HabitCard(
                            habitWithStats = item,
                            onTitleClick = {
                                if (isSelectionMode) {
                                    val isSel = selectedHabitIds.contains(item.habit.id)
                                    selectedHabitIds = if (isSel) selectedHabitIds - item.habit.id else selectedHabitIds + item.habit.id
                                } else {
                                    selectedHabitForDetail = item
                                }
                            },
                            onDayClick = { date ->
                                if (date.isAfter(LocalDate.now())) {
                                    return@HabitCard
                                }
                                val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                                val logs = HabitRepository.getHabitLogs(item.habit.id)
                                val currentRecord = logs[dateStr] ?: HabitDayRecord()

                                if (item.habit.type == HabitType.TARGET) {
                                    // Target habit: open Micro Value Picker BottomSheet
                                    activeTargetPicker = ActiveTargetDayPicker(
                                        habit = item.habit,
                                        date = date,
                                        initialRecord = currentRecord
                                    )
                                } else {
                                    // Yes/No habit: toggle UNMARKED -> DONE -> MISSED -> UNMARKED
                                    when (currentRecord.state) {
                                        HabitDayState.UNMARKED -> {
                                            HabitRepository.setHabitDayState(item.habit.id, date, HabitDayState.DONE)
                                        }
                                        HabitDayState.DONE -> {
                                            HabitRepository.setHabitDayState(item.habit.id, date, HabitDayState.MISSED)
                                            activeIncrementNote = ActiveIncrementNote(
                                                habit = item.habit,
                                                date = date,
                                                countIndex = null,
                                                isMissed = true,
                                                initialNote = currentRecord.note
                                            )
                                        }
                                        HabitDayState.MISSED -> {
                                            HabitRepository.setHabitDayState(item.habit.id, date, HabitDayState.UNMARKED, clearNote = true)
                                        }
                                    }
                                }
                            },
                            onIncrement = {
                                val today = LocalDate.now()
                                val isTarget = item.habit.type == HabitType.TARGET

                                if (item.todayValue < item.habit.targetValue) {
                                    val nextVal = item.todayValue + 1
                                    HabitRepository.incrementHabitDay(item.habit.id, today, 1)

                                    // For target habits: prompt for note on EVERY increment!
                                    if (isTarget) {
                                        activeIncrementNote = ActiveIncrementNote(
                                            habit = item.habit,
                                            date = today,
                                            countIndex = nextVal,
                                            isMissed = false,
                                            initialNote = null
                                        )
                                    }
                                }
                            },
                            onDecrement = {
                                HabitRepository.incrementHabitDay(item.habit.id, LocalDate.now(), -1)
                            },
                            dragHandleModifier = buildDragHandleModifier(item, filteredHabits),
                            isBeingDragged = isBeingDragged,
                            dragOffsetY = if (isBeingDragged) dragOffsetY else 0f,
                            isSelectionMode = isSelectionMode,
                            isSelected = selectedHabitIds.contains(item.habit.id),
                            onSelectionChange = { checked ->
                                selectedHabitIds = if (checked) selectedHabitIds + item.habit.id else selectedHabitIds - item.habit.id
                            }
                        )
                    }
                }

                // Bottom padding for FAB & Selection bar
                item {
                    Spacer(modifier = Modifier.height(96.dp))
                }
            }
        }

        // Selection Bottom Action Bar (Complete & Delete)
        if (isSelectionMode) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                shape = RoundedCornerShape(20.dp),
                color = KairoSurfaceContainerHigh,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Selected count badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedHabitIds.isNotEmpty()) KairoPrimary.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
                            border = androidx.compose.foundation.BorderStroke(
                                width = 1.dp,
                                color = if (selectedHabitIds.isNotEmpty()) KairoPrimary.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.1f)
                            )
                        ) {
                            Text(
                                text = "${selectedHabitIds.size}",
                                color = if (selectedHabitIds.isNotEmpty()) KairoPrimary else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Text(
                            text = if (selectedHabitIds.size == 1) "habit selected" else "habits selected",
                            color = Color.White.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }

                    // Right: Actions
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Complete Button
                        Surface(
                            onClick = {
                                if (selectedHabitIds.isEmpty()) {
                                    Toast.makeText(context, "No habits selected", Toast.LENGTH_SHORT).show()
                                } else {
                                    showCompleteConfirmDialog = true
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = KairoSecondary.copy(alpha = 0.18f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, KairoSecondary.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = KairoSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Complete",
                                    color = KairoSecondary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        // Delete Button
                        Surface(
                            onClick = {
                                if (selectedHabitIds.isEmpty()) {
                                    Toast.makeText(context, "No habits selected", Toast.LENGTH_SHORT).show()
                                } else {
                                    showDeleteConfirmDialog = true
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = KairoError.copy(alpha = 0.18f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, KairoError.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = KairoError,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Delete",
                                    color = KairoError,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Floating Action Button (+) when not in selection mode
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
    }

    // Batch Complete Warning Dialog
    if (showCompleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showCompleteConfirmDialog = false },
            title = {
                Text(
                    text = "Complete ${selectedHabitIds.size} habit${if (selectedHabitIds.size > 1) "s" else ""} today?",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "This will mark all ${selectedHabitIds.size} selected habit(s) as completed for today.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val count = selectedHabitIds.size
                        HabitRepository.completeHabitsToday(selectedHabitIds)
                        isSelectionMode = false
                        selectedHabitIds = emptySet()
                        showCompleteConfirmDialog = false
                        Toast.makeText(context, "$count habit(s) completed today", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(
                        text = "Yes, Complete",
                        color = KairoSecondary,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showCompleteConfirmDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                }
            },
            containerColor = KairoSurfaceContainerHigh,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Batch Delete Warning Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text(
                    text = "Delete ${selectedHabitIds.size} habit${if (selectedHabitIds.size > 1) "s" else ""}?",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "You are deleting ${selectedHabitIds.size} habit${if (selectedHabitIds.size > 1) "s" else ""}. Are you sure you want to delete them permanently from Supabase?",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val count = selectedHabitIds.size
                        HabitRepository.deleteHabits(selectedHabitIds)
                        isSelectionMode = false
                        selectedHabitIds = emptySet()
                        showDeleteConfirmDialog = false
                        Toast.makeText(context, "$count habit(s) deleted", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(
                        text = "Yes, Delete",
                        color = KairoError,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                }
            },
            containerColor = KairoSurfaceContainerHigh,
            shape = RoundedCornerShape(20.dp)
        )
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

    // Habit Note BottomSheet (Prompt for Increment Note, Done note or Missed reason)
    activeIncrementNote?.let { noteState ->
        HabitIncrementNoteBottomSheet(
            habitTitle = noteState.habit.title,
            habitId = noteState.habit.id,
            countIndex = noteState.countIndex,
            targetValue = noteState.habit.targetValue,
            targetUnit = noteState.habit.targetUnit,
            isMissed = noteState.isMissed,
            initialNote = noteState.initialNote,
            onDismiss = { activeIncrementNote = null },
            onSaveNote = { noteText ->
                if (noteState.countIndex != null) {
                    HabitRepository.setIncrementNote(noteState.habit.id, noteState.date, noteState.countIndex, noteText)
                } else {
                    HabitRepository.setHabitDayNote(noteState.habit.id, noteState.date, noteText)
                }
                activeIncrementNote = null
            }
        )
    }

    // Target Day Picker Bottom Sheet (Pattern 2 for past & target days)
    activeTargetPicker?.let { pickerState ->
        TargetDayPickerBottomSheet(
            habit = pickerState.habit,
            date = pickerState.date,
            initialRecord = pickerState.initialRecord,
            onDismiss = { activeTargetPicker = null },
            onSave = { value, state, note ->
                if (state == HabitDayState.MISSED) {
                    HabitRepository.setHabitDayState(pickerState.habit.id, pickerState.date, HabitDayState.MISSED, note = note)
                } else {
                    HabitRepository.setHabitDayValue(pickerState.habit.id, pickerState.date, value, note = note)
                }
                activeTargetPicker = null
            }
        )
    }
}
