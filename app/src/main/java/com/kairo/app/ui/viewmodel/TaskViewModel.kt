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
    val selectedTaskForEdit: Task? = null
)

data class TasksUiState(
    val overdueTasks: List<Task> = emptyList(),
    val todayTasks: List<Task> = emptyList(),
    val upcomingTasks: List<Task> = emptyList(),
    val completedTasks: List<Task> = emptyList(),
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
                    val fmt = SimpleDateFormat("MMM d", Locale.getDefault())
                    val parsed = fmt.parse(dueDateStr)
                    if (parsed != null) {
                        val parsedCal = Calendar.getInstance().apply {
                            time = parsed
                            set(Calendar.YEAR, cal.get(Calendar.YEAR))
                            set(Calendar.HOUR_OF_DAY, 12)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        parsedCal.timeInMillis
                    } else fallback
                } catch (_: Exception) {
                    fallback
                }
            }
        }
    }

    private fun isOverdue(task: Task): Boolean {
        if (task.isCompleted) return false
        if (task.section == TaskSection.OVERDUE || task.dueDate.contains("Yesterday", ignoreCase = true)) return true
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return getEffectiveDueDateMillis(task) < startOfToday
    }

    private fun isToday(task: Task): Boolean {
        if (task.isCompleted || isOverdue(task)) return false
        if (task.section == TaskSection.TODAY || task.dueDate.contains("Today", ignoreCase = true)) return true

        val dueMillis = getEffectiveDueDateMillis(task)
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val endOfToday = startOfToday + 86400000L - 1L
        return dueMillis in startOfToday..endOfToday
    }

    private fun isTomorrow(task: Task): Boolean {
        if (task.dueDate.contains("Tomorrow", ignoreCase = true)) return true
        val tomorrowCal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
        }
        val tomorrowFmt = SimpleDateFormat("MMM d", Locale.getDefault()).format(tomorrowCal.time)
        if (task.dueDate.contains(tomorrowFmt, ignoreCase = true)) return true

        val dueMillis = getEffectiveDueDateMillis(task)
        val startOfTomorrow = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val endOfTomorrow = startOfTomorrow + 86400000L - 1L
        return dueMillis in startOfTomorrow..endOfTomorrow
    }

    private fun isUpcoming(task: Task): Boolean {
        if (task.isCompleted || isOverdue(task) || isToday(task)) return false
        return true
    }

    private fun sortTasks(list: List<Task>, sort: TaskSort): List<Task> {
        return when (sort) {
            TaskSort.DATE_NEAR -> list.sortedBy { getEffectiveDueDateMillis(it) }
            TaskSort.DATE_LATE -> list.sortedByDescending { getEffectiveDueDateMillis(it) }
            TaskSort.PRIORITY_HIGH -> list.sortedByDescending { it.priority.ordinal }
            TaskSort.PRIORITY_LOW -> list.sortedBy { it.priority.ordinal }
            TaskSort.TITLE_AZ -> list.sortedBy { it.title.lowercase() }
            TaskSort.TITLE_ZA -> list.sortedByDescending { it.title.lowercase() }
        }
    }

    val uiState: StateFlow<TasksUiState> = combine(
        repository.tasks,
        _filterCriteria,
        _activeSort,
        _searchQuery,
        _uiToggles
    ) { tasks: List<Task>, criteria: FilterCriteria, sort: TaskSort, search: String, toggles: UiToggles ->
        var list = tasks

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
                        val end = criteria.dateRangeEnd + 86400000L // inclusive of end date
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
        val completed = sortTasks(list.filter { it.isCompleted }, sort)

        TasksUiState(
            overdueTasks = overdue,
            todayTasks = today,
            upcomingTasks = upcoming,
            completedTasks = completed,
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

    fun setShowCreateDialog(show: Boolean) {
        _uiToggles.update { it.copy(showCreateDialog = show) }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            AlarmScheduler.cancelAlarm(KairoApplication.instance, taskId)
            repository.deleteTask(taskId)
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
        attachmentUri: String?
    ) {
        if (title.isBlank()) return
        val assignedSection = when {
            dueDate.contains("Today", ignoreCase = true) -> TaskSection.TODAY
            dueDate.contains("Yesterday", ignoreCase = true) -> TaskSection.OVERDUE
            else -> TaskSection.UPCOMING
        }
        val updated = task.copy(
            title = title.trim(),
            notes = notes,
            priority = priority,
            dueDate = dueDate,
            dueDateMillis = dueDateMillis ?: computeDueDateMillis(dueDate),
            dueTime = dueTime,
            location = location,
            attachmentName = attachmentName,
            attachmentUri = attachmentUri,
            section = assignedSection,
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
        attachmentUri: String? = null
    ) {
        if (title.isBlank()) return
        val assignedSection = when {
            dueDate.contains("Today", ignoreCase = true) -> TaskSection.TODAY
            dueDate.contains("Yesterday", ignoreCase = true) -> TaskSection.OVERDUE
            else -> TaskSection.UPCOMING
        }
        val newTask = Task(
            title = title.trim(),
            notes = notes,
            priority = priority,
            dueDate = dueDate,
            dueDateMillis = dueDateMillis ?: computeDueDateMillis(dueDate),
            dueTime = dueTime,
            location = location,
            attachmentName = attachmentName,
            attachmentUri = attachmentUri,
            section = assignedSection
        )
        viewModelScope.launch {
            repository.addTask(newTask)
            AlarmScheduler.scheduleAlarm(KairoApplication.instance, newTask)
            _uiToggles.update { it.copy(showCreateDialog = false) }
        }
    }
}
