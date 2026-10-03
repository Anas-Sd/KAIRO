package com.kairo.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairo.app.KairoApplication
import com.kairo.app.alarm.AlarmScheduler
import com.kairo.app.data.model.DateFilter
import com.kairo.app.data.model.FilterCriteria
import com.kairo.app.data.model.Priority
import com.kairo.app.data.model.StatusFilter
import com.kairo.app.data.model.Task
import com.kairo.app.data.model.TaskSection
import com.kairo.app.data.model.TaskSort
import com.kairo.app.data.repository.TaskRepository
import com.kairo.app.data.repository.UndoAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class UiToggles(
    val overdueExpanded: Boolean = true,
    val todayExpanded: Boolean = true,
    val upcomingExpanded: Boolean = false, // Collapsed by default
    val completedExpanded: Boolean = false, // Collapsed by default
    val isSearchActive: Boolean = false,
    val showCreateDialog: Boolean = false,
    val showFilterSheet: Boolean = false,
    val showSortSheet: Boolean = false,
    val selectedTaskForDetails: Task? = null,
    val selectedTaskForEdit: Task? = null,
    val subtaskParentForCreate: Task? = null,
    val taskForAdjustParent: Task? = null,
    val taskForDeleteConfirm: Task? = null,
    val breadcrumbStack: List<Task> = emptyList()
)

data class TasksUiState(
    val overdueTasks: List<Task> = emptyList(),
    val todayTasks: List<Task> = emptyList(),
    val upcomingTasks: List<Task> = emptyList(),
    val completedTasks: List<Task> = emptyList(),
    val allTasks: List<Task> = emptyList(),
    val filterCriteria: FilterCriteria = FilterCriteria(),
    val activeSort: TaskSort = TaskSort.DATE_NEAR,
    val overdueExpanded: Boolean = true,
    val todayExpanded: Boolean = true,
    val upcomingExpanded: Boolean = false, // Collapsed by default
    val completedExpanded: Boolean = false, // Collapsed by default
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val showCreateDialog: Boolean = false,
    val showFilterSheet: Boolean = false,
    val showSortSheet: Boolean = false,
    val selectedTaskForDetails: Task? = null,
    val selectedTaskForEdit: Task? = null,
    val subtaskParentForCreate: Task? = null,
    val taskForAdjustParent: Task? = null,
    val taskForDeleteConfirm: Task? = null,
    val breadcrumbStack: List<Task> = emptyList(),
    val undoAction: UndoAction? = null,
    val isConnected: Boolean = true
)

class TaskViewModel(
    private val repository: TaskRepository = TaskRepository()
) : ViewModel() {

    init {
        viewModelScope.launch {
            repository.tasks.collect { taskList ->
                val now = System.currentTimeMillis()
                taskList.filter { !it.isCompleted }.forEach { task ->
                    val trigger = AlarmScheduler.calculateTriggerMillis(task.dueDate, task.dueTime, task.dueDateMillis)
                    if (trigger != null && trigger > now) {
                        AlarmScheduler.scheduleAlarm(KairoApplication.instance, task, trigger)
                    }
                }
            }
        }
    }

    private val _filterCriteria = MutableStateFlow(FilterCriteria())
    private val _activeSort = MutableStateFlow(TaskSort.DATE_NEAR)
    private val _searchQuery = MutableStateFlow("")
    private val _uiToggles = MutableStateFlow(UiToggles())

    private fun getEffectiveDueDateMillis(task: Task): Long {
        task.dueDateMillis?.let { return it }
        return computeDueDateMillis(task.dueDate, task.createdAt)
    }

    private fun computeDueDateMillis(dueDateStr: String, fallback: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return when {
            dueDateStr.contains("Today", ignoreCase = true) -> cal.timeInMillis
            dueDateStr.contains("Tomorrow", ignoreCase = true) -> {
                cal.add(Calendar.DAY_OF_YEAR, 1)
                cal.timeInMillis
            }
            dueDateStr.contains("Yesterday", ignoreCase = true) -> {
                cal.add(Calendar.DAY_OF_YEAR, -1)
                cal.timeInMillis
            }
            else -> {
                try {
                    val formats = arrayOf("MMM d", "MMM dd", "dd/MM/yyyy", "yyyy-MM-dd", "MMM dd, yyyy", "dd MMM yyyy", "EEE, MMM d")
                    var parsedTime: Long? = null
                    for (fmt in formats) {
                        try {
                            val parsedDate = SimpleDateFormat(fmt, Locale.getDefault()).parse(dueDateStr)
                            if (parsedDate != null) {
                                val pCal = Calendar.getInstance().apply { time = parsedDate }
                                if (pCal.get(Calendar.YEAR) <= 1970) {
                                    pCal.set(Calendar.YEAR, Calendar.getInstance().get(Calendar.YEAR))
                                }
                                pCal.set(Calendar.HOUR_OF_DAY, 12)
                                pCal.set(Calendar.MINUTE, 0)
                                pCal.set(Calendar.SECOND, 0)
                                pCal.set(Calendar.MILLISECOND, 0)
                                parsedTime = pCal.timeInMillis
                                break
                            }
                        } catch (_: Exception) {}
                    }
                    parsedTime ?: fallback
                } catch (_: Exception) {
                    fallback
                }
            }
        }
    }

    private fun isOverdue(task: Task): Boolean {
        if (task.isCompleted) return false
        if (task.dueDate.contains("Yesterday", ignoreCase = true)) return true

        val now = System.currentTimeMillis()
        val trigger = AlarmScheduler.calculateTriggerMillis(task.dueDate, task.dueTime, task.dueDateMillis)
        if (trigger != null && trigger < now) {
            return true
        }

        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        if (task.dueDate.isNotBlank() || task.dueDateMillis != null) {
            val taskDue = getEffectiveDueDateMillis(task)
            return taskDue < todayStart
        }
        return task.section == TaskSection.OVERDUE
    }

    private fun isToday(task: Task): Boolean {
        if (task.isCompleted) return false
        if (isOverdue(task)) return false
        if (task.dueDate.contains("Tomorrow", ignoreCase = true) ||
            task.dueDate.contains("Yesterday", ignoreCase = true)) return false

        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val todayEnd = todayStart + 86400000L

        if (task.dueDate.contains("Today", ignoreCase = true)) return true

        if (task.dueDate.isNotBlank() || task.dueDateMillis != null) {
            val taskDue = getEffectiveDueDateMillis(task)
            return taskDue in todayStart until todayEnd
        }

        return task.section == TaskSection.TODAY
    }

    private fun isTomorrow(task: Task): Boolean {
        if (task.isCompleted) return false
        if (isOverdue(task)) return false
        if (task.dueDate.contains("Tomorrow", ignoreCase = true)) return true

        val tomorrowStart = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val tomorrowEnd = tomorrowStart + 86400000L

        val taskDue = getEffectiveDueDateMillis(task)
        return taskDue in tomorrowStart until tomorrowEnd
    }

    private fun isUpcoming(task: Task): Boolean {
        if (task.isCompleted) return false
        if (isOverdue(task)) return false
        if (isToday(task)) return false
        return true
    }

    private fun sortTasks(list: List<Task>, sort: TaskSort): List<Task> {
        return when (sort) {
            TaskSort.DATE_NEAR -> list.sortedWith(compareBy({ it.position }, { getEffectiveDueDateMillis(it) }))
            TaskSort.DATE_LATE -> list.sortedByDescending { getEffectiveDueDateMillis(it) }
            TaskSort.PRIORITY_HIGH -> list.sortedByDescending { it.priority.ordinal }
            TaskSort.PRIORITY_LOW -> list.sortedBy { it.priority.ordinal }
            TaskSort.TITLE_AZ -> list.sortedBy { it.title.lowercase() }
            TaskSort.TITLE_ZA -> list.sortedByDescending { it.title.lowercase() }
        }
    }

    private val _filterState = combine(_filterCriteria, _activeSort, _searchQuery) { criteria, sort, search ->
        Triple(criteria, sort, search)
    }

    val uiState: StateFlow<TasksUiState> = combine(
        repository.tasks,
        repository.undoAction,
        _filterState,
        _uiToggles
    ) { tasks: List<Task>, undo: UndoAction?, filterData: Triple<FilterCriteria, TaskSort, String>, toggles: UiToggles ->
        val (criteria, sort, search) = filterData
        val isSearchingOrFiltering = search.isNotBlank() ||
                criteria.selectedStatuses.isNotEmpty() ||
                criteria.selectedPriorities.isNotEmpty() ||
                criteria.selectedDate != null

        // Hierarchy scoping:
        val scopedTasks = if (toggles.breadcrumbStack.isNotEmpty()) {
            val currentParent = toggles.breadcrumbStack.last()
            tasks.filter { it.parentId == currentParent.id }
        } else if (isSearchingOrFiltering) {
            tasks // Show all matching tasks across hierarchy when searching or filtering
        } else {
            tasks.filter { it.parentId == null } // Root view: show only top-level tasks
        }

        var list = scopedTasks

        // 1. Text Search Filter
        if (search.isNotBlank()) {
            list = list.filter { it.title.contains(search, ignoreCase = true) }
        }

        // 2. Status Filter (Active / Completed)
        if (criteria.selectedStatuses.isNotEmpty()) {
            val showActive = StatusFilter.ACTIVE in criteria.selectedStatuses
            val showCompleted = StatusFilter.COMPLETED in criteria.selectedStatuses
            list = list.filter { task ->
                (showActive && !task.isCompleted) || (showCompleted && task.isCompleted)
            }
        }

        // 3. Priority Filter (Low / Med / High / Urgent)
        if (criteria.selectedPriorities.isNotEmpty()) {
            list = list.filter { task ->
                task.priority in criteria.selectedPriorities
            }
        }

        // 4. Due Date Filter
        criteria.selectedDate?.let { dateFilter ->
            list = when (dateFilter) {
                DateFilter.TODAY -> list.filter { isToday(it) }
                DateFilter.TOMORROW -> list.filter { isTomorrow(it) }
                DateFilter.RANGE -> {
                    if (criteria.dateRangeStart != null && criteria.dateRangeEnd != null) {
                        val start = criteria.dateRangeStart
                        val end = criteria.dateRangeEnd + 86400000L
                        list.filter { task ->
                            val taskDue = getEffectiveDueDateMillis(task)
                            taskDue in start..end
                        }
                    } else {
                        list
                    }
                }
            }
        }

        // 5. Partition into 4 Categories and apply sorting WITHIN each category
        val overdue = sortTasks(list.filter { isOverdue(it) }, sort)
        val today = sortTasks(list.filter { isToday(it) }, sort)
        val upcoming = sortTasks(list.filter { isUpcoming(it) }, sort)
        val completed = list.filter { it.isCompleted }.sortedByDescending { it.completedAt ?: it.updatedAt }

        TasksUiState(
            overdueTasks = overdue,
            todayTasks = today,
            upcomingTasks = upcoming,
            completedTasks = completed,
            allTasks = tasks,
            filterCriteria = criteria,
            activeSort = sort,
            overdueExpanded = toggles.overdueExpanded,
            todayExpanded = toggles.todayExpanded,
            upcomingExpanded = toggles.upcomingExpanded,
            completedExpanded = toggles.completedExpanded,
            searchQuery = search,
            isSearchActive = toggles.isSearchActive,
            showCreateDialog = toggles.showCreateDialog,
            showFilterSheet = toggles.showFilterSheet,
            showSortSheet = toggles.showSortSheet,
            selectedTaskForDetails = toggles.selectedTaskForDetails,
            selectedTaskForEdit = toggles.selectedTaskForEdit,
            subtaskParentForCreate = toggles.subtaskParentForCreate,
            taskForAdjustParent = toggles.taskForAdjustParent,
            taskForDeleteConfirm = toggles.taskForDeleteConfirm,
            breadcrumbStack = toggles.breadcrumbStack,
            undoAction = undo,
            isConnected = true
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TasksUiState()
    )

    fun toggleTask(taskId: String) {
        viewModelScope.launch {
            repository.toggleTaskCompletion(taskId)
            repository.getTaskById(taskId)?.let { updated ->
                if (updated.isCompleted) {
                    AlarmScheduler.cancelAlarm(KairoApplication.instance, taskId)
                } else {
                    AlarmScheduler.scheduleAlarm(KairoApplication.instance, updated)
                }
            }
        }
    }

    // Breadcrumbs Navigation
    fun drillDown(task: Task) {
        _uiToggles.update { it.copy(breadcrumbStack = it.breadcrumbStack + task) }
    }

    fun popBreadcrumb() {
        _uiToggles.update {
            if (it.breadcrumbStack.isNotEmpty()) {
                it.copy(breadcrumbStack = it.breadcrumbStack.dropLast(1))
            } else it
        }
    }

    fun navigateBreadcrumbTo(index: Int) {
        _uiToggles.update {
            if (index < 0) {
                it.copy(breadcrumbStack = emptyList())
            } else {
                it.copy(breadcrumbStack = it.breadcrumbStack.take(index + 1))
            }
        }
    }

    fun navigateToParent(parentId: String) {
        val allTasks = repository.getAllTasks()
        val parentTask = allTasks.find { it.id == parentId } ?: return
        val ancestors = repository.getAncestorIds(parentId, allTasks)
            .mapNotNull { id -> allTasks.find { it.id == id } }
            .reversed()
        _searchQuery.value = ""
        _uiToggles.update {
            it.copy(
                breadcrumbStack = ancestors + parentTask,
                isSearchActive = false
            )
        }
    }

    // Subtasks & Adjust Parent
    fun openAddSubtask(parentTask: Task) {
        _uiToggles.update {
            it.copy(showCreateDialog = true, subtaskParentForCreate = parentTask)
        }
    }

    fun openAdjustParent(task: Task) {
        _uiToggles.update { it.copy(taskForAdjustParent = task) }
    }

    fun dismissAdjustParent() {
        _uiToggles.update { it.copy(taskForAdjustParent = null) }
    }

    fun moveTask(taskId: String, newParentId: String?, moveSubtasks: Boolean) {
        viewModelScope.launch {
            repository.moveTask(taskId, newParentId, moveSubtasks)
            _uiToggles.update { it.copy(taskForAdjustParent = null) }
        }
    }

    fun reorderTasks(orderedTaskIds: List<String>) {
        repository.reorderTasks(orderedTaskIds)
    }

    // Deletion Modal
    fun openDeleteConfirm(task: Task) {
        _uiToggles.update { it.copy(taskForDeleteConfirm = task) }
    }

    fun dismissDeleteConfirm() {
        _uiToggles.update { it.copy(taskForDeleteConfirm = null) }
    }

    fun confirmDelete(task: Task, deleteSubtasks: Boolean) {
        viewModelScope.launch {
            AlarmScheduler.cancelAlarm(KairoApplication.instance, task.id)
            repository.deleteTaskWithSubtasks(task.id, deleteSubtasks)
            _uiToggles.update { it.copy(taskForDeleteConfirm = null) }
        }
    }

    // Undo / Done Bar
    fun performUndo() {
        viewModelScope.launch {
            repository.performUndo()
        }
    }

    fun commitUndo() {
        viewModelScope.launch {
            repository.commitUndo()
        }
    }

    // Filter & Sort
    fun openFilterSheet() {
        _uiToggles.update { it.copy(showFilterSheet = true) }
    }

    fun closeFilterSheet() {
        _uiToggles.update { it.copy(showFilterSheet = false) }
    }

    fun applyFilter(criteria: FilterCriteria) {
        _filterCriteria.value = criteria
        _uiToggles.update { it.copy(showFilterSheet = false) }
    }

    fun resetFilters() {
        _filterCriteria.value = FilterCriteria()
    }

    fun openSortSheet() {
        _uiToggles.update { it.copy(showSortSheet = true) }
    }

    fun closeSortSheet() {
        _uiToggles.update { it.copy(showSortSheet = false) }
    }

    fun applySort(sort: TaskSort) {
        _activeSort.value = sort
        _uiToggles.update { it.copy(showSortSheet = false) }
    }

    fun toggleOverdue() {
        _uiToggles.update { it.copy(overdueExpanded = !it.overdueExpanded) }
    }

    fun toggleToday() {
        _uiToggles.update { it.copy(todayExpanded = !it.todayExpanded) }
    }

    fun toggleUpcoming() {
        _uiToggles.update { it.copy(upcomingExpanded = !it.upcomingExpanded) }
    }

    fun toggleCompleted() {
        _uiToggles.update { it.copy(completedExpanded = !it.completedExpanded) }
    }

    fun updateSearch(query: String) {
        _searchQuery.value = query
    }

    fun toggleSearchBar() {
        _uiToggles.update { current ->
            val nextState = !current.isSearchActive
            if (!nextState) _searchQuery.value = ""
            current.copy(isSearchActive = nextState)
        }
    }

    fun closeSearchBar() {
        _searchQuery.value = ""
        _uiToggles.update { it.copy(isSearchActive = false) }
    }

    fun setShowCreateDialog(show: Boolean) {
        _uiToggles.update {
            it.copy(
                showCreateDialog = show,
                subtaskParentForCreate = if (!show) null else it.subtaskParentForCreate
            )
        }
    }

    fun showTaskDetails(task: Task) {
        _uiToggles.update { it.copy(selectedTaskForDetails = task) }
    }

    fun dismissTaskDetails() {
        _uiToggles.update { it.copy(selectedTaskForDetails = null) }
    }

    fun showEditTask(task: Task) {
        _uiToggles.update { it.copy(selectedTaskForEdit = task, selectedTaskForDetails = null) }
    }

    fun dismissEditTask() {
        _uiToggles.update { it.copy(selectedTaskForEdit = null) }
    }

    fun updateTask(
        task: Task,
        title: String,
        notes: String?,
        priority: Priority,
        dueDate: String,
        dueDateMillis: Long?,
        dueTime: String?,
        location: String?,
        attachmentName: String?,
        attachmentUri: String?,
        alarmToneUri: String? = null,
        alarmToneTitle: String? = null,
        repeatType: String? = null,
        repeatDays: String? = null,
        repeatDates: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        locationRadius: Int = 500
    ) {
        if (title.isBlank()) return
        val targetMillis = dueDateMillis ?: computeDueDateMillis(dueDate)
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val todayEnd = todayStart + 86400000L

        val assignedSection = when {
            dueDate.contains("Yesterday", ignoreCase = true) -> TaskSection.OVERDUE
            dueDate.contains("Today", ignoreCase = true) -> TaskSection.TODAY
            targetMillis < todayStart -> TaskSection.OVERDUE
            targetMillis >= todayStart && targetMillis < todayEnd -> TaskSection.TODAY
            else -> TaskSection.UPCOMING
        }
        val updated = task.copy(
            title = title.trim(),
            notes = notes,
            priority = priority,
            dueDate = dueDate,
            dueDateMillis = targetMillis,
            dueTime = dueTime,
            location = location,
            attachmentName = attachmentName,
            attachmentUri = attachmentUri,
            alarmToneUri = alarmToneUri,
            alarmToneTitle = alarmToneTitle,
            repeatType = repeatType,
            repeatDays = repeatDays,
            repeatDates = repeatDates,
            section = assignedSection,
            latitude = latitude,
            longitude = longitude,
            locationRadius = locationRadius,
            updatedAt = System.currentTimeMillis()
        )
        viewModelScope.launch {
            repository.updateTask(updated)
            AlarmScheduler.scheduleAlarm(KairoApplication.instance, updated)
            _uiToggles.update { it.copy(selectedTaskForEdit = null) }
        }
    }

    fun createTask(
        title: String,
        notes: String?,
        priority: Priority,
        dueDate: String,
        dueDateMillis: Long?,
        dueTime: String?,
        location: String?,
        attachmentName: String?,
        attachmentUri: String? = null,
        alarmToneUri: String? = null,
        alarmToneTitle: String? = null,
        repeatType: String? = null,
        repeatDays: String? = null,
        repeatDates: String? = null,
        parentId: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        locationRadius: Int = 500
    ) {
        if (title.isBlank()) return
        val targetMillis = dueDateMillis ?: computeDueDateMillis(dueDate)
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val todayEnd = todayStart + 86400000L

        val assignedSection = when {
            dueDate.contains("Yesterday", ignoreCase = true) -> TaskSection.OVERDUE
            dueDate.contains("Today", ignoreCase = true) -> TaskSection.TODAY
            targetMillis < todayStart -> TaskSection.OVERDUE
            targetMillis >= todayStart && targetMillis < todayEnd -> TaskSection.TODAY
            else -> TaskSection.UPCOMING
        }
        val siblingCount = repository.getAllTasks().count { it.parentId == parentId }
        val newTask = Task(
            title = title.trim(),
            notes = notes,
            priority = priority,
            dueDate = dueDate,
            dueDateMillis = targetMillis,
            dueTime = dueTime,
            location = location,
            attachmentName = attachmentName,
            attachmentUri = attachmentUri,
            alarmToneUri = alarmToneUri,
            alarmToneTitle = alarmToneTitle,
            repeatType = repeatType,
            repeatDays = repeatDays,
            repeatDates = repeatDates,
            section = assignedSection,
            parentId = parentId,
            position = siblingCount,
            latitude = latitude,
            longitude = longitude,
            locationRadius = locationRadius
        )
        viewModelScope.launch {
            repository.addTask(newTask)
            AlarmScheduler.scheduleAlarm(KairoApplication.instance, newTask)

            // Open parent after saving so user can see new subtask
            if (parentId != null) {
                val parentTask = repository.getTaskById(parentId)
                if (parentTask != null && _uiToggles.value.breadcrumbStack.none { it.id == parentId }) {
                    val ancestors = repository.getAncestorIds(parentId, repository.getAllTasks())
                        .mapNotNull { repository.getTaskById(it) }
                        .reversed()
                    _uiToggles.update {
                        it.copy(
                            breadcrumbStack = ancestors + parentTask,
                            showCreateDialog = false,
                            subtaskParentForCreate = null
                        )
                    }
                } else {
                    _uiToggles.update { it.copy(showCreateDialog = false, subtaskParentForCreate = null) }
                }
            } else {
                _uiToggles.update { it.copy(showCreateDialog = false, subtaskParentForCreate = null) }
            }
        }
    }
}
