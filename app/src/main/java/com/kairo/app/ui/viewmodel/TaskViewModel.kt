package com.kairo.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairo.app.data.model.DateFilter
import com.kairo.app.data.model.FilterCriteria
import com.kairo.app.data.model.Priority
import com.kairo.app.data.model.StatusFilter
import com.kairo.app.data.model.Task
import com.kairo.app.data.model.TaskCategory
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

data class UiToggles(
    val overdueExpanded: Boolean = true,
    val todayExpanded: Boolean = true,
    val upcomingExpanded: Boolean = true,
    val completedExpanded: Boolean = true,
    val isSearchActive: Boolean = false,
    val showCreateDialog: Boolean = false,
    val showFilterSheet: Boolean = false,
    val showSortSheet: Boolean = false
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
    val upcomingExpanded: Boolean = true,
    val completedExpanded: Boolean = true,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val showCreateDialog: Boolean = false,
    val showFilterSheet: Boolean = false,
    val showSortSheet: Boolean = false,
    val isConnected: Boolean = true
)

class TaskViewModel(
    private val repository: TaskRepository = TaskRepository()
) : ViewModel() {

    private val _filterCriteria = MutableStateFlow(FilterCriteria())
    private val _activeSort = MutableStateFlow(TaskSort.DATE_NEAR)
    private val _searchQuery = MutableStateFlow("")
    private val _uiToggles = MutableStateFlow(UiToggles())

    // Flow of filtered & sorted tasks
    private val _filteredTasks = combine(
        repository.tasks,
        _filterCriteria,
        _activeSort,
        _searchQuery
    ) { tasks: List<Task>, criteria: FilterCriteria, sort: TaskSort, search: String ->
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
                DateFilter.TODAY -> list.filter {
                    it.dueDisplay.contains("Today", ignoreCase = true) || it.section == TaskSection.TODAY
                }
                DateFilter.TOMORROW -> list.filter {
                    it.dueDisplay.contains("Tomorrow", ignoreCase = true) || it.dueDisplay.contains("Mon", ignoreCase = true)
                }
                DateFilter.RANGE -> list
            }
        }

        // 5. Sorting
        when (sort) {
            TaskSort.DATE_NEAR -> list.sortedBy { it.createdAt }
            TaskSort.DATE_LATE -> list.sortedByDescending { it.createdAt }
            TaskSort.PRIORITY_HIGH -> list.sortedByDescending { it.priority.ordinal }
            TaskSort.PRIORITY_LOW -> list.sortedBy { it.priority.ordinal }
            TaskSort.TITLE_AZ -> list.sortedBy { it.title.lowercase() }
            TaskSort.TITLE_ZA -> list.sortedByDescending { it.title.lowercase() }
        }
    }

    val uiState: StateFlow<TasksUiState> = combine(
        _filteredTasks,
        _filterCriteria,
        _activeSort,
        _searchQuery,
        _uiToggles
    ) { filtered: List<Task>, criteria: FilterCriteria, sort: TaskSort, search: String, toggles: UiToggles ->
        TasksUiState(
            overdueTasks = filtered.filter { it.section == TaskSection.OVERDUE && !it.isCompleted },
            todayTasks = filtered.filter { it.section == TaskSection.TODAY && !it.isCompleted },
            upcomingTasks = filtered.filter { it.section == TaskSection.UPCOMING && !it.isCompleted },
            completedTasks = filtered.filter { it.isCompleted || it.section == TaskSection.COMPLETED },
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

    fun createTask(title: String, category: TaskCategory, priority: Priority) {
        if (title.isBlank()) return
        val newTask = Task(
            title = title.trim(),
            category = category,
            priority = priority,
            dueDisplay = "Today",
            section = TaskSection.TODAY
        )
        viewModelScope.launch {
            repository.addTask(newTask)
            _uiToggles.update { it.copy(showCreateDialog = false) }
        }
    }
}
