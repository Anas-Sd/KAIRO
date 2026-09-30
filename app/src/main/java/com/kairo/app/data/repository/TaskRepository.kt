package com.kairo.app.data.repository

import com.kairo.app.data.model.ContextType
import com.kairo.app.data.model.Priority
import com.kairo.app.data.model.Task
import com.kairo.app.data.model.TaskCategory
import com.kairo.app.data.model.TaskSection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class TaskRepository {

    private val _tasks = MutableStateFlow<List<Task>>(initialMockTasks())
    val tasks: Flow<List<Task>> = _tasks.asStateFlow()

    fun toggleTaskCompletion(taskId: String) {
        _tasks.update { currentList ->
            currentList.map { task ->
                if (task.id == taskId) {
                    val willComplete = !task.isCompleted
                    task.copy(
                        isCompleted = willComplete,
                        section = if (willComplete) TaskSection.COMPLETED else TaskSection.TODAY
                    )
                } else task
            }
        }
    }

    fun addTask(task: Task) {
        _tasks.update { current -> listOf(task) + current }
    }

    fun deleteTask(taskId: String) {
        _tasks.update { current -> current.filterNot { it.id == taskId } }
    }

    private fun initialMockTasks(): List<Task> = listOf(
        Task(
            title = "Submit monthly expense receipts & invoice audit",
            priority = Priority.URGENT,
            category = TaskCategory.FINANCE,
            dueDisplay = "Yesterday, 5:00 PM",
            contextLabel = "3 files",
            contextType = ContextType.ATTACHMENT,
            isCompleted = false,
            section = TaskSection.OVERDUE
        ),
        Task(
            title = "Q4 Growth Roadmap Strategy Sync",
            priority = Priority.MEDIUM,
            category = TaskCategory.WORK,
            secondaryTag = "Strategy",
            dueDisplay = "2:00 PM",
            contextLabel = "Google Meet",
            contextType = ContextType.MEETING,
            isCompleted = false,
            section = TaskSection.TODAY
        ),
        Task(
            title = "Quarterly budget presentation",
            priority = Priority.HIGH,
            category = TaskCategory.FINANCE,
            dueDisplay = "4:30 PM",
            contextLabel = "Boardroom B",
            contextType = ContextType.LOCATION,
            isCompleted = false,
            section = TaskSection.TODAY
        ),
        Task(
            title = "Mobile Design System V2 Specs handoff",
            priority = Priority.MEDIUM,
            category = TaskCategory.DESIGN,
            secondaryTag = "Sprint 15",
            dueDisplay = "Mon, 11:00 AM",
            contextLabel = "Figma doc",
            contextType = ContextType.ATTACHMENT,
            isCompleted = false,
            section = TaskSection.UPCOMING
        ),
        Task(
            title = "Morning routine & hydration checklist",
            priority = Priority.LOW,
            category = TaskCategory.HABITS,
            dueDisplay = "8:00 AM Today",
            isCompleted = true,
            section = TaskSection.COMPLETED
        ),
        Task(
            title = "Weekly sprint planning kick-off",
            priority = Priority.MEDIUM,
            category = TaskCategory.WORK,
            secondaryTag = "Sprint 14",
            dueDisplay = "9:15 AM Today",
            isCompleted = true,
            section = TaskSection.COMPLETED
        ),
        Task(
            title = "Review client contract changes",
            priority = Priority.HIGH,
            category = TaskCategory.LEGAL,
            dueDisplay = "10:30 AM Today",
            isCompleted = true,
            section = TaskSection.COMPLETED
        )
    )
}
