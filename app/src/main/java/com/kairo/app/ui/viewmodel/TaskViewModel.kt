package com.kairo.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairo.app.data.model.Priority
import com.kairo.app.data.model.Task
import com.kairo.app.data.model.TaskCategory
import com.kairo.app.data.model.TaskFilter
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
    val showCreateDialog: Boolean = false
)

data class TasksUiState(
    val overdueTasks: List<Task> = emptyList(),
    val todayTasks: List<Task> = emptyList(),
    val upcomingTasks: List<Task> = emptyList(),
    val completedTasks: List<Task> = emptyList(),
    val activeFilter: TaskFilter = TaskFilter.ALL,
    val activeSort: TaskSort = TaskSort.DUE_DATE,
    val overdueExpanded: Boolean = true,
    val todayExpanded: Boolean = true,
    val upcomingExpanded: Boolean = true,
    val completedExpanded: Boolean = true,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val showCreateDialog: Boolean = false
)

class TaskViewModel(
    private val repository: TaskRepository = TaskRepository()
) : ViewModel() {

    private val _activeFilter = MutableStateFlow(TaskFilter.ALL)
    private val _activeSort = MutableStateFlow(TaskSort.DUE_DATE)
    private val _searchQuery = MutableStateFlow("")
    private val _uiToggles = MutableStateFlow(UiToggles())

    // Flow of filtered & sorted tasks
    private val _filteredTasks = combine(
        repository.tasks,
        _activeFilter,
        _activeSort,
        _searchQuery
    ) { tasks: List<Task>, filter: TaskFilter, sort: TaskSort, search: String ->
        var list = tasks

        // Search filtering
        if (search.isNotBlank()) {
            list = list.filter { it.title.contains(search, ignoreCase = true) }
        }

        // Tag / status filtering
        list = when (filter) {
            TaskFilter.ALL -> list
            TaskFilter.TODAY -> list.filter { it.section == TaskSection.TODAY }
            TaskFilter.URGENT -> list.filter { it.priority == Priority.URGENT }
            TaskFilter.WORK -> list.filter { it.category == TaskCategory.WORK }
            TaskFilter.FINANCE -> list.filter { it.category == TaskCategory.FINANCE }
            TaskFilter.COMPLETED -> list.filter { it.isCompleted }
        }

        // Sorting
        when (sort) {
            TaskSort.DUE_DATE -> list
            TaskSort.PRIORITY -> list.sortedByDescending { it.priority.ordinal }
            TaskSort.TITLE -> list.sortedBy { it.title.lowercase() }
        }
    }

    val uiState: StateFlow<TasksUiState> = combine(
        _filteredTasks,
        _activeFilter,
        _activeSort,
        _searchQuery,
        _uiToggles
    ) { filtered: List<Task>, filter: TaskFilter, sort: TaskSort, search: String, toggles: UiToggles ->
        TasksUiState(
            overdueTasks = filtered.filter { it.section == TaskSection.OVERDUE && !it.isCompleted },
            todayTasks = filtered.filter { it.section == TaskSection.TODAY && !it.isCompleted },
            upcomingTasks = filtered.filter { it.section == TaskSection.UPCOMING && !it.isCompleted },
            completedTasks = filtered.filter { it.isCompleted || it.section == TaskSection.COMPLETED },
            activeFilter = filter,
            activeSort = sort,
            overdueExpanded = toggles.overdueExpanded,
            todayExpanded = toggles.todayExpanded,
            upcomingExpanded = toggles.upcomingExpanded,
            completedExpanded = toggles.completedExpanded,
            searchQuery = search,
            isSearchActive = toggles.isSearchActive,
            showCreateDialog = toggles.showCreateDialog
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

    fun setFilter(filter: TaskFilter) {
        _activeFilter.value = filter
    }

    fun setSort(sort: TaskSort) {
        _activeSort.value = sort
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
