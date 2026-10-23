package com.kairo.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.ui.graphics.Color
import com.kairo.app.data.sync.SyncManager
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.foundation.BorderStroke
import com.kairo.app.ui.components.AppHeader
import com.kairo.app.ui.theme.KairoError
import com.kairo.app.ui.theme.KairoSurfaceContainerHigh
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kairo.app.data.repository.TaskRepository
import com.kairo.app.ui.components.AdjustParentDialog
import com.kairo.app.ui.components.BreadcrumbBar
import com.kairo.app.ui.components.CreateTaskDialog
import com.kairo.app.ui.components.DeleteTaskConfirmDialog
import com.kairo.app.ui.components.EditTaskDialog
import com.kairo.app.ui.components.FilterBottomSheet
import com.kairo.app.ui.components.FilterSortBar
import com.kairo.app.ui.components.SectionHeader
import com.kairo.app.ui.components.SectionType
import com.kairo.app.ui.components.SortBottomSheet
import com.kairo.app.ui.components.TaskCard
import com.kairo.app.ui.components.TaskDetailsBottomSheet
import com.kairo.app.ui.components.TaskExportBottomSheet
import com.kairo.app.ui.components.UndoDoneBar
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
import com.kairo.app.data.auth.AuthManager
import com.kairo.app.ui.components.SettingsDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    resetTrigger: Int = 0,
    modifier: Modifier = Modifier,
    viewModel: TaskViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val session by AuthManager.sessionState.collectAsState()
    val isOnline by SyncManager.isOnline.collectAsState()
    val isSyncing by SyncManager.isSyncing.collectAsState()
    val pendingSyncCount by SyncManager.pendingSyncCount.collectAsState()
    val displayName = session?.name ?: "Tasks"
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val currentDateStr = SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date())

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedTaskIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteWarningDialog by remember { mutableStateOf(false) }
    var showCompleteWarningDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    LaunchedEffect(resetTrigger) {
        if (resetTrigger > 0) {
            isSelectionMode = false
            selectedTaskIds = emptySet()
            if (uiState.isSearchActive) {
                viewModel.closeSearchBar()
            }
            if (uiState.breadcrumbStack.isNotEmpty()) {
                viewModel.navigateBreadcrumbTo(-1)
            }
            viewModel.setShowCreateDialog(false)
            viewModel.dismissTaskDetails()
            viewModel.dismissEditTask()
            listState.animateScrollToItem(0)
        }
    }

    var tickerMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30000L) // updates every 30 seconds
            tickerMillis = System.currentTimeMillis()
        }
    }

    val nextAlarmPair = remember(uiState.allTasks, tickerMillis) {
        com.kairo.app.ui.utils.DateUtils.getNextUpcomingAlarmTask(uiState.allTasks, tickerMillis)
    }

    // Back button handling:
    // 0. If in selection mode, exit selection mode
    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedTaskIds = emptySet()
    }

    // 1. If search box is open, hitting back closes the search box
    BackHandler(enabled = !isSelectionMode && uiState.isSearchActive) {
        viewModel.closeSearchBar()
    }

    // 2. If inside subtasks breadcrumb stack, drill up a level
    BackHandler(enabled = !isSelectionMode && !uiState.isSearchActive && uiState.breadcrumbStack.isNotEmpty()) {
        viewModel.popBreadcrumb()
    }

    // Drag-to-reorder state across sections
    var draggingTaskId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var localReorderList by remember { mutableStateOf<List<com.kairo.app.data.model.Task>?>(null) }

    fun buildDragHandleModifier(task: com.kairo.app.data.model.Task, sectionTasks: List<com.kairo.app.data.model.Task>): Modifier {
        return Modifier.pointerInput(task.id) {
            detectDragGestures(
                onDragStart = {
                    draggingTaskId = task.id
                    dragOffsetY = 0f
                    localReorderList = sectionTasks
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    dragOffsetY += dragAmount.y
                    val currentList = localReorderList
                    val currentId = draggingTaskId
                    if (currentList != null && currentId != null) {
                        val currentIndex = currentList.indexOfFirst { it.id == currentId }
                        val itemThreshold = 140f
                        if (dragOffsetY > itemThreshold && currentIndex in 0 until currentList.lastIndex) {
                            val mutable = currentList.toMutableList()
                            val targetIndex = currentIndex + 1
                            val item = mutable.removeAt(currentIndex)
                            mutable.add(targetIndex, item)
                            localReorderList = mutable
                            dragOffsetY -= itemThreshold
                        } else if (dragOffsetY < -itemThreshold && currentIndex > 0) {
                            val mutable = currentList.toMutableList()
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
                        viewModel.reorderTasks(reordered.map { it.id })
                    }
                    draggingTaskId = null
                    dragOffsetY = 0f
                    localReorderList = null
                },
                onDragCancel = {
                    draggingTaskId = null
                    dragOffsetY = 0f
                    localReorderList = null
                }
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = KairoBackground,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(KairoBackground)
                ) {
                    // Top Header Row
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
                                    selectedTaskIds = emptySet()
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Cancel selection",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = if (selectedTaskIds.isEmpty()) "Select tasks" else "${selectedTaskIds.size} selected",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Select All / Deselect All
                            TextButton(onClick = {
                                selectedTaskIds = if (selectedTaskIds.size == uiState.allTasks.size) {
                                    emptySet()
                                } else {
                                    uiState.allTasks.map { it.id }.toSet()
                                }
                            }) {
                                Text(
                                    text = if (selectedTaskIds.size == uiState.allTasks.size) "Deselect All" else "Select All",
                                    color = KairoPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else {
                        AppHeader(
                            searchQuery = uiState.searchQuery,
                            isSearchActive = uiState.isSearchActive,
                            onSearchQueryChange = { viewModel.updateSearch(it) },
                            onToggleSearch = { viewModel.toggleSearchBar() },
                            onCloseSearch = { viewModel.closeSearchBar() },
                            searchPlaceholder = "Search tasks...",
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
                                        selectedTaskIds = emptySet()
                                    }
                                )
                            }
                        )
                    }

                    // Filter & Sort Control Bar with 16.dp horizontal padding
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 6.dp)
                    ) {
                        FilterSortBar(
                            filterCriteria = uiState.filterCriteria,
                            activeSort = uiState.activeSort,
                            onOpenFilter = { viewModel.openFilterSheet() },
                            onOpenSort = { viewModel.openSortSheet() },
                            onExportClicked = {
                                showExportSheet = true
                            },
                            onResetFilter = {
                                viewModel.resetFilters()
                                Toast.makeText(context, "Filters reset", Toast.LENGTH_SHORT).show()
                            }
                        )

                        // Breadcrumbs Navigation Bar (shown when drilled into subtasks)
                        if (uiState.breadcrumbStack.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            BreadcrumbBar(
                                breadcrumbStack = uiState.breadcrumbStack,
                                onNavigateBack = { viewModel.popBreadcrumb() },
                                onNavigateToCrumb = { index -> viewModel.navigateBreadcrumbTo(index) }
                            )
                        }

                        // Next Upcoming Alarm Indicator (Below Filter Row)
                        if (nextAlarmPair != null) {
                            val (nextTask, nextAlarmMillis) = nextAlarmPair
                            val countdownText = com.kairo.app.ui.utils.DateUtils.formatRemainingTime(nextAlarmMillis, tickerMillis)
                            val targetTimeFormatted = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(nextAlarmMillis))
                            val targetDateFormatted = com.kairo.app.ui.utils.DateUtils.formatDisplayDate(null, nextAlarmMillis)

                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFF0F172A),
                                border = BorderStroke(1.2.dp, Color(0xFF0284C7).copy(alpha = 0.55f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.showTaskDetails(nextTask)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .background(Color(0xFF0284C7).copy(alpha = 0.2f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Alarm,
                                                contentDescription = "Next Alarm",
                                                tint = Color(0xFF38BDF8),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = "Next Alarm in",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Color(0xFF94A3B8)
                                                )
                                                Text(
                                                    text = countdownText ?: "Soon",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF38BDF8)
                                                )
                                            }
                                            Text(
                                                text = "${nextTask.title} • $targetDateFormatted at $targetTimeFormatted",
                                                fontSize = 12.sp,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = "View Task Details",
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            floatingActionButton = {
                if (!isSelectionMode) {
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
            }
        ) { innerPadding ->
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 0. SNOOZED ALARMS SECTION (At top, above Overdue)
                val snoozedList = uiState.snoozedTasks
                if (snoozedList.isNotEmpty()) {
                    val isSnoozedAllSelected = snoozedList.isNotEmpty() && snoozedList.all { selectedTaskIds.contains(it.id) }
                    item(key = "section_snoozed_header") {
                        SectionHeader(
                            title = "Snoozed Alarms",
                            countText = "${snoozedList.size} snoozed",
                            sectionType = SectionType.SNOOZED,
                            isExpanded = uiState.snoozedExpanded,
                            onToggle = { viewModel.toggleSnoozed() },
                            showCheckbox = isSelectionMode,
                            isChecked = isSnoozedAllSelected,
                            onCheckedChange = { checked ->
                                val ids = snoozedList.map { it.id }.toSet()
                                selectedTaskIds = if (checked) selectedTaskIds + ids else selectedTaskIds - ids
                            }
                        )
                    }

                    if (uiState.snoozedExpanded) {
                        items(snoozedList, key = { it.id }) { task ->
                            val hasSubtasks = uiState.allTasks.any { it.parentId == task.id }
                            val subtaskProgress = if (hasSubtasks) TaskRepository.getSubtaskProgress(task.id, uiState.allTasks) else null
                            val parentTitle = if (uiState.breadcrumbStack.isEmpty() && task.parentId != null) {
                                TaskRepository.getParentTask(task, uiState.allTasks)?.title
                            } else null
                            val isBeingDragged = (task.id == draggingTaskId)

                            TaskCard(
                                task = task,
                                hasSubtasks = hasSubtasks,
                                subtaskProgress = subtaskProgress,
                                parentTitle = parentTitle,
                                onToggleCompletion = {
                                    viewModel.toggleTask(task.id)
                                    val msg = if (task.isCompleted) "Task marked as active" else "Task completed"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                },
                                onDeleteTask = { viewModel.openDeleteConfirm(task) },
                                onShowDetails = { viewModel.showTaskDetails(task) },
                                onEditTask = { viewModel.showEditTask(task) },
                                onAddSubtask = { viewModel.openAddSubtask(task) },
                                onAdjustParent = { viewModel.openAdjustParent(task) },
                                onDrillDown = { viewModel.drillDown(task) },
                                onNavigateToParent = { parentId -> viewModel.navigateToParent(parentId) },
                                dragHandleModifier = Modifier,
                                isBeingDragged = isBeingDragged,
                                dragOffsetY = if (isBeingDragged) dragOffsetY else 0f,
                                isSelectionMode = isSelectionMode,
                                isSelected = selectedTaskIds.contains(task.id),
                                onSelectionChange = { checked ->
                                    selectedTaskIds = if (checked) selectedTaskIds + task.id else selectedTaskIds - task.id
                                }
                            )
                        }
                    }
                }

                // 1. OVERDUE SECTION
                val overdueList = if (draggingTaskId != null && localReorderList?.any { task -> uiState.overdueTasks.any { it.id == task.id } } == true) {
                    localReorderList ?: uiState.overdueTasks
                } else uiState.overdueTasks

                if (overdueList.isNotEmpty()) {
                    val isOverdueAllSelected = overdueList.isNotEmpty() && overdueList.all { selectedTaskIds.contains(it.id) }
                    item(key = "section_overdue_header") {
                        SectionHeader(
                            title = "Overdue",
                            countText = "${overdueList.size} overdue",
                            sectionType = SectionType.OVERDUE,
                            isExpanded = uiState.overdueExpanded,
                            onToggle = { viewModel.toggleOverdue() },
                            showCheckbox = isSelectionMode,
                            isChecked = isOverdueAllSelected,
                            onCheckedChange = { checked ->
                                val ids = overdueList.map { it.id }.toSet()
                                selectedTaskIds = if (checked) selectedTaskIds + ids else selectedTaskIds - ids
                            }
                        )
                    }

                    if (uiState.overdueExpanded) {
                        items(overdueList, key = { it.id }) { task ->
                            val hasSubtasks = uiState.allTasks.any { it.parentId == task.id }
                            val subtaskProgress = if (hasSubtasks) TaskRepository.getSubtaskProgress(task.id, uiState.allTasks) else null
                            val parentTitle = if (uiState.breadcrumbStack.isEmpty() && task.parentId != null) {
                                TaskRepository.getParentTask(task, uiState.allTasks)?.title
                            } else null
                            val isBeingDragged = (task.id == draggingTaskId)

                            TaskCard(
                                task = task,
                                hasSubtasks = hasSubtasks,
                                subtaskProgress = subtaskProgress,
                                parentTitle = parentTitle,
                                onToggleCompletion = {
                                    viewModel.toggleTask(task.id)
                                    val msg = if (task.isCompleted) "Task marked as active" else "Task completed"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                },
                                onDeleteTask = { viewModel.openDeleteConfirm(task) },
                                onShowDetails = { viewModel.showTaskDetails(task) },
                                onEditTask = { viewModel.showEditTask(task) },
                                onAddSubtask = { viewModel.openAddSubtask(task) },
                                onAdjustParent = { viewModel.openAdjustParent(task) },
                                onDrillDown = { viewModel.drillDown(task) },
                                onNavigateToParent = { parentId -> viewModel.navigateToParent(parentId) },
                                dragHandleModifier = buildDragHandleModifier(task, overdueList),
                                isBeingDragged = isBeingDragged,
                                dragOffsetY = if (isBeingDragged) dragOffsetY else 0f,
                                isSelectionMode = isSelectionMode,
                                isSelected = selectedTaskIds.contains(task.id),
                                onSelectionChange = { checked ->
                                    selectedTaskIds = if (checked) selectedTaskIds + task.id else selectedTaskIds - task.id
                                },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }

                // 2. TODAY SECTION
                val todayList = if (draggingTaskId != null && localReorderList?.any { task -> uiState.todayTasks.any { it.id == task.id } } == true) {
                    localReorderList ?: uiState.todayTasks
                } else uiState.todayTasks

                if (todayList.isNotEmpty()) {
                    val isTodayAllSelected = todayList.isNotEmpty() && todayList.all { selectedTaskIds.contains(it.id) }
                    item(key = "section_today_header") {
                        SectionHeader(
                            title = "Today",
                            countText = "${todayList.size} tasks",
                            sectionType = SectionType.TODAY,
                            isExpanded = uiState.todayExpanded,
                            onToggle = { viewModel.toggleToday() },
                            showCheckbox = isSelectionMode,
                            isChecked = isTodayAllSelected,
                            onCheckedChange = { checked ->
                                val ids = todayList.map { it.id }.toSet()
                                selectedTaskIds = if (checked) selectedTaskIds + ids else selectedTaskIds - ids
                            }
                        )
                    }

                    if (uiState.todayExpanded) {
                        items(todayList, key = { it.id }) { task ->
                            val hasSubtasks = uiState.allTasks.any { it.parentId == task.id }
                            val subtaskProgress = if (hasSubtasks) TaskRepository.getSubtaskProgress(task.id, uiState.allTasks) else null
                            val parentTitle = if (uiState.breadcrumbStack.isEmpty() && task.parentId != null) {
                                TaskRepository.getParentTask(task, uiState.allTasks)?.title
                            } else null
                            val isBeingDragged = (task.id == draggingTaskId)

                            TaskCard(
                                task = task,
                                hasSubtasks = hasSubtasks,
                                subtaskProgress = subtaskProgress,
                                parentTitle = parentTitle,
                                onToggleCompletion = {
                                    viewModel.toggleTask(task.id)
                                    val msg = if (task.isCompleted) "Task marked as active" else "Task completed"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                },
                                onDeleteTask = { viewModel.openDeleteConfirm(task) },
                                onShowDetails = { viewModel.showTaskDetails(task) },
                                onEditTask = { viewModel.showEditTask(task) },
                                onAddSubtask = { viewModel.openAddSubtask(task) },
                                onAdjustParent = { viewModel.openAdjustParent(task) },
                                onDrillDown = { viewModel.drillDown(task) },
                                onNavigateToParent = { parentId -> viewModel.navigateToParent(parentId) },
                                dragHandleModifier = buildDragHandleModifier(task, todayList),
                                isBeingDragged = isBeingDragged,
                                dragOffsetY = if (isBeingDragged) dragOffsetY else 0f,
                                isSelectionMode = isSelectionMode,
                                isSelected = selectedTaskIds.contains(task.id),
                                onSelectionChange = { checked ->
                                    selectedTaskIds = if (checked) selectedTaskIds + task.id else selectedTaskIds - task.id
                                },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }

                // 3. TOMORROW SECTION
                val tomorrowList = if (draggingTaskId != null && localReorderList?.any { task -> uiState.tomorrowTasks.any { it.id == task.id } } == true) {
                    localReorderList ?: uiState.tomorrowTasks
                } else uiState.tomorrowTasks

                if (tomorrowList.isNotEmpty()) {
                    val isTomorrowAllSelected = tomorrowList.isNotEmpty() && tomorrowList.all { selectedTaskIds.contains(it.id) }
                    item(key = "section_tomorrow_header") {
                        SectionHeader(
                            title = "Tomorrow",
                            countText = "${tomorrowList.size} scheduled",
                            sectionType = SectionType.TOMORROW,
                            isExpanded = uiState.tomorrowExpanded,
                            onToggle = { viewModel.toggleTomorrow() },
                            showCheckbox = isSelectionMode,
                            isChecked = isTomorrowAllSelected,
                            onCheckedChange = { checked ->
                                val ids = tomorrowList.map { it.id }.toSet()
                                selectedTaskIds = if (checked) selectedTaskIds + ids else selectedTaskIds - ids
                            }
                        )
                    }

                    if (uiState.tomorrowExpanded) {
                        items(tomorrowList, key = { it.id }) { task ->
                            val hasSubtasks = uiState.allTasks.any { it.parentId == task.id }
                            val subtaskProgress = if (hasSubtasks) TaskRepository.getSubtaskProgress(task.id, uiState.allTasks) else null
                            val parentTitle = if (uiState.breadcrumbStack.isEmpty() && task.parentId != null) {
                                TaskRepository.getParentTask(task, uiState.allTasks)?.title
                            } else null
                            val isBeingDragged = (task.id == draggingTaskId)

                            TaskCard(
                                task = task,
                                hasSubtasks = hasSubtasks,
                                subtaskProgress = subtaskProgress,
                                parentTitle = parentTitle,
                                onToggleCompletion = {
                                    viewModel.toggleTask(task.id)
                                    val msg = if (task.isCompleted) "Task marked as active" else "Task completed"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                },
                                onDeleteTask = { viewModel.openDeleteConfirm(task) },
                                onShowDetails = { viewModel.showTaskDetails(task) },
                                onEditTask = { viewModel.showEditTask(task) },
                                onAddSubtask = { viewModel.openAddSubtask(task) },
                                onAdjustParent = { viewModel.openAdjustParent(task) },
                                onDrillDown = { viewModel.drillDown(task) },
                                onNavigateToParent = { parentId -> viewModel.navigateToParent(parentId) },
                                onReactivateForToday = {
                                    viewModel.reactivateTaskForToday(task.id)
                                    Toast.makeText(context, "Task reactivated for Today", Toast.LENGTH_SHORT).show()
                                },
                                dragHandleModifier = buildDragHandleModifier(task, tomorrowList),
                                isBeingDragged = isBeingDragged,
                                dragOffsetY = if (isBeingDragged) dragOffsetY else 0f,
                                isSelectionMode = isSelectionMode,
                                isSelected = selectedTaskIds.contains(task.id),
                                onSelectionChange = { checked ->
                                    selectedTaskIds = if (checked) selectedTaskIds + task.id else selectedTaskIds - task.id
                                },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }

                // 4. UPCOMING SECTION
                val upcomingList = if (draggingTaskId != null && localReorderList?.any { task -> uiState.upcomingTasks.any { it.id == task.id } } == true) {
                    localReorderList ?: uiState.upcomingTasks
                } else uiState.upcomingTasks

                if (upcomingList.isNotEmpty()) {
                    val isUpcomingAllSelected = upcomingList.isNotEmpty() && upcomingList.all { selectedTaskIds.contains(it.id) }
                    item(key = "section_upcoming_header") {
                        SectionHeader(
                            title = "Upcoming",
                            countText = "${upcomingList.size} scheduled",
                            sectionType = SectionType.UPCOMING,
                            isExpanded = uiState.upcomingExpanded,
                            onToggle = { viewModel.toggleUpcoming() },
                            showCheckbox = isSelectionMode,
                            isChecked = isUpcomingAllSelected,
                            onCheckedChange = { checked ->
                                val ids = upcomingList.map { it.id }.toSet()
                                selectedTaskIds = if (checked) selectedTaskIds + ids else selectedTaskIds - ids
                            }
                        )
                    }

                    if (uiState.upcomingExpanded) {
                        items(upcomingList, key = { it.id }) { task ->
                            val hasSubtasks = uiState.allTasks.any { it.parentId == task.id }
                            val subtaskProgress = if (hasSubtasks) TaskRepository.getSubtaskProgress(task.id, uiState.allTasks) else null
                            val parentTitle = if (uiState.breadcrumbStack.isEmpty() && task.parentId != null) {
                                TaskRepository.getParentTask(task, uiState.allTasks)?.title
                            } else null
                            val isBeingDragged = (task.id == draggingTaskId)

                            TaskCard(
                                task = task,
                                hasSubtasks = hasSubtasks,
                                subtaskProgress = subtaskProgress,
                                parentTitle = parentTitle,
                                onToggleCompletion = {
                                    viewModel.toggleTask(task.id)
                                    val msg = if (task.isCompleted) "Task marked as active" else "Task completed"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                },
                                onDeleteTask = { viewModel.openDeleteConfirm(task) },
                                onShowDetails = { viewModel.showTaskDetails(task) },
                                onEditTask = { viewModel.showEditTask(task) },
                                onAddSubtask = { viewModel.openAddSubtask(task) },
                                onAdjustParent = { viewModel.openAdjustParent(task) },
                                onDrillDown = { viewModel.drillDown(task) },
                                onNavigateToParent = { parentId -> viewModel.navigateToParent(parentId) },
                                onReactivateForToday = {
                                    viewModel.reactivateTaskForToday(task.id)
                                    Toast.makeText(context, "Task reactivated for Today", Toast.LENGTH_SHORT).show()
                                },
                                dragHandleModifier = buildDragHandleModifier(task, upcomingList),
                                isBeingDragged = isBeingDragged,
                                dragOffsetY = if (isBeingDragged) dragOffsetY else 0f,
                                isSelectionMode = isSelectionMode,
                                isSelected = selectedTaskIds.contains(task.id),
                                onSelectionChange = { checked ->
                                    selectedTaskIds = if (checked) selectedTaskIds + task.id else selectedTaskIds - task.id
                                },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }

                // 5. COMPLETED SECTION
                val completedList = if (draggingTaskId != null && localReorderList?.any { task -> uiState.completedTasks.any { it.id == task.id } } == true) {
                    localReorderList ?: uiState.completedTasks
                } else uiState.completedTasks

                if (completedList.isNotEmpty()) {
                    val isCompletedAllSelected = completedList.isNotEmpty() && completedList.all { selectedTaskIds.contains(it.id) }
                    item(key = "section_completed_header") {
                        SectionHeader(
                            title = "Completed",
                            countText = "${completedList.size} completed",
                            sectionType = SectionType.COMPLETED,
                            isExpanded = uiState.completedExpanded,
                            onToggle = { viewModel.toggleCompleted() },
                            showCheckbox = isSelectionMode,
                            isChecked = isCompletedAllSelected,
                            onCheckedChange = { checked ->
                                val ids = completedList.map { it.id }.toSet()
                                selectedTaskIds = if (checked) selectedTaskIds + ids else selectedTaskIds - ids
                            }
                        )
                    }

                    if (uiState.completedExpanded) {
                        items(completedList, key = { it.id }) { task ->
                            val hasSubtasks = uiState.allTasks.any { it.parentId == task.id }
                            val subtaskProgress = if (hasSubtasks) TaskRepository.getSubtaskProgress(task.id, uiState.allTasks) else null
                            val parentTitle = if (uiState.breadcrumbStack.isEmpty() && task.parentId != null) {
                                TaskRepository.getParentTask(task, uiState.allTasks)?.title
                            } else null
                            val isBeingDragged = (task.id == draggingTaskId)

                            TaskCard(
                                task = task,
                                hasSubtasks = hasSubtasks,
                                subtaskProgress = subtaskProgress,
                                parentTitle = parentTitle,
                                onToggleCompletion = {
                                    viewModel.toggleTask(task.id)
                                    val msg = if (task.id.startsWith("snapshot_")) {
                                        "Task reactivated for Today"
                                    } else if (task.isCompleted) {
                                        "Task marked as active"
                                    } else {
                                        "Task completed"
                                    }
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                },
                                onDeleteTask = { viewModel.openDeleteConfirm(task) },
                                onShowDetails = { viewModel.showTaskDetails(task) },
                                onEditTask = { viewModel.showEditTask(task) },
                                onAddSubtask = { viewModel.openAddSubtask(task) },
                                onAdjustParent = { viewModel.openAdjustParent(task) },
                                onDrillDown = { viewModel.drillDown(task) },
                                onNavigateToParent = { parentId -> viewModel.navigateToParent(parentId) },
                                dragHandleModifier = buildDragHandleModifier(task, completedList),
                                isBeingDragged = isBeingDragged,
                                dragOffsetY = if (isBeingDragged) dragOffsetY else 0f,
                                isSelectionMode = isSelectionMode,
                                isSelected = selectedTaskIds.contains(task.id),
                                onSelectionChange = { checked ->
                                    selectedTaskIds = if (checked) selectedTaskIds + task.id else selectedTaskIds - task.id
                                },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }

                // Empty state if all sections are empty due to filtering
                if (uiState.snoozedTasks.isEmpty() && uiState.overdueTasks.isEmpty() && uiState.todayTasks.isEmpty() &&
                    uiState.tomorrowTasks.isEmpty() && uiState.upcomingTasks.isEmpty() && uiState.completedTasks.isEmpty()
                ) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (uiState.breadcrumbStack.isNotEmpty()) "No subtasks under this task yet."
                                else "No tasks match your filter criteria.",
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
                    onDismiss = { viewModel.closeFilterSheet() }
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
                    onDismiss = { viewModel.closeSortSheet() }
                )
            }

            // Export Modal Bottom Sheet
            if (showExportSheet) {
                val isFiltered = uiState.filterCriteria.selectedStatuses.isNotEmpty() ||
                    uiState.filterCriteria.selectedPriorities.isNotEmpty() ||
                    uiState.filterCriteria.selectedDate != null ||
                    uiState.searchQuery.isNotBlank() ||
                    uiState.breadcrumbStack.isNotEmpty()

                val tasksToExport = if (isFiltered) {
                    (uiState.snoozedTasks + uiState.overdueTasks + uiState.todayTasks + uiState.tomorrowTasks + uiState.upcomingTasks + uiState.completedTasks).distinctBy { it.id }
                } else {
                    uiState.allTasks
                }

                TaskExportBottomSheet(
                    tasksToExport = tasksToExport,
                    allTasks = uiState.allTasks,
                    isFiltered = isFiltered,
                    onDismissRequest = { showExportSheet = false }
                )
            }

            // Dialog for creating tasks (support subtasks via parentTask)
            if (uiState.showCreateDialog) {
                CreateTaskDialog(
                    parentTask = uiState.subtaskParentForCreate,
                    onDismiss = { viewModel.setShowCreateDialog(false) },
                    onConfirm = { title, notes, priority, dueDate, dueDateMillis, dueTime, location, attachmentName, attachmentUri, alarmToneUri, alarmToneTitle, repeatType, repeatDays, repeatDates, parentId, latitude, longitude, locationRadius ->
                        viewModel.createTask(
                            title = title,
                            notes = notes,
                            priority = priority,
                            dueDate = dueDate,
                            dueDateMillis = dueDateMillis,
                            dueTime = dueTime,
                            location = location,
                            attachmentName = attachmentName,
                            attachmentUri = attachmentUri,
                            alarmToneUri = alarmToneUri,
                            alarmToneTitle = alarmToneTitle,
                            repeatType = repeatType,
                            repeatDays = repeatDays,
                            repeatDates = repeatDates,
                            parentId = parentId,
                            latitude = latitude,
                            longitude = longitude,
                            locationRadius = locationRadius
                        )
                        val msg = if (isOnline) "Task created" else "Saved offline"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // Bottom sheet for task details
            val detailsTask = uiState.selectedTaskForDetails
            if (detailsTask != null) {
                TaskDetailsBottomSheet(
                    task = detailsTask,
                    onDismiss = { viewModel.dismissTaskDetails() },
                    onEditClicked = { viewModel.showEditTask(detailsTask) },
                    onReactivateForToday = {
                        viewModel.reactivateTaskForToday(detailsTask.id)
                        Toast.makeText(context, "Task reactivated for Today", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // Dialog for editing tasks
            val editTask = uiState.selectedTaskForEdit
            if (editTask != null) {
                EditTaskDialog(
                    task = editTask,
                    onDismiss = { viewModel.dismissEditTask() },
                    onConfirm = { title, notes, priority, dueDate, dueDateMillis, dueTime, location, attachmentName, attachmentUri, alarmToneUri, alarmToneTitle, repeatType, repeatDays, repeatDates, latitude, longitude, locationRadius ->
                        viewModel.updateTask(
                            task = editTask,
                            title = title,
                            notes = notes,
                            priority = priority,
                            dueDate = dueDate,
                            dueDateMillis = dueDateMillis,
                            dueTime = dueTime,
                            location = location,
                            attachmentName = attachmentName,
                            attachmentUri = attachmentUri,
                            alarmToneUri = alarmToneUri,
                            alarmToneTitle = alarmToneTitle,
                            repeatType = repeatType,
                            repeatDays = repeatDays,
                            repeatDates = repeatDates,
                            latitude = latitude,
                            longitude = longitude,
                            locationRadius = locationRadius
                        )
                        val msg = if (isOnline) "Task updated" else "Saved offline"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // Dialog for Adjusting Parent
            val taskForAdjust = uiState.taskForAdjustParent
            if (taskForAdjust != null) {
                AdjustParentDialog(
                    task = taskForAdjust,
                    allTasks = uiState.allTasks,
                    onDismiss = { viewModel.dismissAdjustParent() },
                    onMoveConfirmed = { newParentId, moveSubtasks ->
                        viewModel.moveTask(taskForAdjust.id, newParentId, moveSubtasks)
                        val msg = if (isOnline) "Task moved" else "Moved offline"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // Dialog for Deleting Task (confirm with/without subtasks)
            val taskForDelete = uiState.taskForDeleteConfirm
            if (taskForDelete != null) {
                val hasSubtasks = uiState.allTasks.any { it.parentId == taskForDelete.id }
                DeleteTaskConfirmDialog(
                    task = taskForDelete,
                    hasSubtasks = hasSubtasks,
                    onDismiss = { viewModel.dismissDeleteConfirm() },
                    onConfirmDelete = { deleteSubtasks ->
                        viewModel.confirmDelete(taskForDelete, deleteSubtasks)
                        val msg = if (isOnline) "Task deleted" else "Deleted offline"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // Settings Dialog
            if (showSettingsDialog) {
                SettingsDialog(
                    onDismissRequest = { showSettingsDialog = false }
                )
            }

            // Batch Delete Confirmation Dialog
            if (showDeleteWarningDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteWarningDialog = false },
                    title = {
                        Text(
                            text = "Delete ${selectedTaskIds.size} task${if (selectedTaskIds.size > 1) "s" else ""}?",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    text = {
                        Text(
                            text = "You are deleting ${selectedTaskIds.size} task${if (selectedTaskIds.size > 1) "s" else ""}. Are you sure you want to delete them permanently?",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val count = selectedTaskIds.size
                                viewModel.deleteTasks(selectedTaskIds)
                                isSelectionMode = false
                                selectedTaskIds = emptySet()
                                showDeleteWarningDialog = false
                                Toast.makeText(context, "$count task(s) deleted", Toast.LENGTH_SHORT).show()
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
                        TextButton(
                            onClick = { showDeleteWarningDialog = false }
                        ) {
                            Text(
                                text = "Cancel",
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    containerColor = KairoSurfaceContainerHigh,
                    shape = RoundedCornerShape(20.dp)
                )
            }

            // Batch Complete Confirmation Dialog
            if (showCompleteWarningDialog) {
                AlertDialog(
                    onDismissRequest = { showCompleteWarningDialog = false },
                    title = {
                        Text(
                            text = "Complete ${selectedTaskIds.size} task${if (selectedTaskIds.size > 1) "s" else ""}?",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    text = {
                        Text(
                            text = "This will mark all ${selectedTaskIds.size} selected task(s) as completed.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val count = selectedTaskIds.size
                                viewModel.completeTasks(selectedTaskIds)
                                isSelectionMode = false
                                selectedTaskIds = emptySet()
                                showCompleteWarningDialog = false
                                Toast.makeText(context, "$count task(s) completed", Toast.LENGTH_SHORT).show()
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
                        TextButton(
                            onClick = { showCompleteWarningDialog = false }
                        ) {
                            Text(
                                text = "Cancel",
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    containerColor = KairoSurfaceContainerHigh,
                    shape = RoundedCornerShape(20.dp)
                )
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
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Selected count badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedTaskIds.isNotEmpty()) KairoPrimary.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (selectedTaskIds.isNotEmpty()) KairoPrimary.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.1f)
                            )
                        ) {
                            Text(
                                text = "${selectedTaskIds.size}",
                                color = if (selectedTaskIds.isNotEmpty()) KairoPrimary else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                        Text(
                            text = "selected",
                            color = Color.White.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
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
                                if (selectedTaskIds.isEmpty()) {
                                    Toast.makeText(context, "No tasks selected", Toast.LENGTH_SHORT).show()
                                } else {
                                    showCompleteWarningDialog = true
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = KairoSecondary.copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, KairoSecondary.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = KairoSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Complete",
                                    color = KairoSecondary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        // Delete Button
                        Surface(
                            onClick = {
                                if (selectedTaskIds.isEmpty()) {
                                    Toast.makeText(context, "No tasks selected", Toast.LENGTH_SHORT).show()
                                } else {
                                    showDeleteWarningDialog = true
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = KairoError.copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, KairoError.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = KairoError,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Delete",
                                    color = KairoError,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }

        // Persistent Undo / Done Bar at the bottom
        val currentUndo = uiState.undoAction
        if (currentUndo != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 76.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                UndoDoneBar(
                    message = currentUndo.message,
                    onUndo = { viewModel.performUndo() },
                    onDone = { viewModel.commitUndo() }
                )
            }
        }
    }
}
