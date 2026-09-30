package com.kairo.app.data.repository

import android.util.Log
import com.kairo.app.data.model.ContextType
import com.kairo.app.data.model.Priority
import com.kairo.app.data.model.Task
import com.kairo.app.data.model.TaskCategory
import com.kairo.app.data.model.TaskSection
import com.kairo.app.data.remote.SupabaseClient
import com.kairo.app.data.remote.TaskDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TaskRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _tasks = MutableStateFlow<List<Task>>(initialMockTasks())
    val tasks: Flow<List<Task>> = _tasks.asStateFlow()

    init {
        refreshFromSupabase()
    }

    fun refreshFromSupabase() {
        scope.launch {
            SupabaseClient.getTasks()
                .onSuccess { remoteList ->
                    if (remoteList.isNotEmpty()) {
                        _tasks.value = remoteList.map { it.toDomain() }
                        Log.d("TaskRepository", "Loaded ${remoteList.size} tasks live from Supabase")
                    }
                }
                .onFailure { error ->
                    Log.e("TaskRepository", "Failed loading from Supabase, using local cache: ${error.message}")
                }
        }
    }

    fun toggleTaskCompletion(taskId: String) {
        var updatedTask: Task? = null
        _tasks.update { currentList ->
            currentList.map { task ->
                if (task.id == taskId) {
                    val willComplete = !task.isCompleted
                    val newSection = if (willComplete) TaskSection.COMPLETED else TaskSection.TODAY
                    val modified = task.copy(
                        isCompleted = willComplete,
                        section = newSection
                    )
                    updatedTask = modified
                    modified
                } else task
            }
        }

        // Push update to Supabase
        updatedTask?.let { task ->
            scope.launch {
                SupabaseClient.updateTaskCompletion(
                    taskId = task.id,
                    isCompleted = task.isCompleted,
                    section = task.section.name
                ).onFailure { error ->
                    Log.e("TaskRepository", "Error syncing toggle to Supabase: ${error.message}")
                }
            }
        }
    }

    fun addTask(task: Task) {
        // Optimistic UI update
        _tasks.update { current -> listOf(task) + current }

        // Sync to Supabase
        scope.launch {
            SupabaseClient.insertTask(TaskDto.fromDomain(task))
                .onFailure { error ->
                    Log.e("TaskRepository", "Error inserting task to Supabase: ${error.message}")
                }
        }
    }

    fun deleteTask(taskId: String) {
        // Optimistic UI update
        _tasks.update { current -> current.filterNot { it.id == taskId } }

        // Sync to Supabase
        scope.launch {
            SupabaseClient.deleteTask(taskId)
                .onFailure { error ->
                    Log.e("TaskRepository", "Error deleting task from Supabase: ${error.message}")
                }
        }
    }

    private fun initialMockTasks(): List<Task> = listOf(
        Task(
            id = "task-1",
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
            id = "task-2",
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
            id = "task-3",
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
            id = "task-4",
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
            id = "task-5",
            title = "Morning routine & hydration checklist",
            priority = Priority.LOW,
            category = TaskCategory.HABITS,
            dueDisplay = "8:00 AM Today",
            isCompleted = true,
            section = TaskSection.COMPLETED
        ),
        Task(
            id = "task-6",
            title = "Weekly sprint planning kick-off",
            priority = Priority.MEDIUM,
            category = TaskCategory.WORK,
            secondaryTag = "Sprint 14",
            dueDisplay = "9:15 AM Today",
            isCompleted = true,
            section = TaskSection.COMPLETED
        ),
        Task(
            id = "task-7",
            title = "Review client contract changes",
            priority = Priority.HIGH,
            category = TaskCategory.LEGAL,
            dueDisplay = "10:30 AM Today",
            isCompleted = true,
            section = TaskSection.COMPLETED
        )
    )
}
