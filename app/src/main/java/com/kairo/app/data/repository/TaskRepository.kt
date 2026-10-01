package com.kairo.app.data.repository

import android.util.Log
import com.kairo.app.data.model.Priority
import com.kairo.app.data.model.Task
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

object TaskRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _tasks = MutableStateFlow<List<Task>>(initialMockTasks())
    val tasks: Flow<List<Task>> = _tasks.asStateFlow()

    operator fun invoke(): TaskRepository = this
    fun getInstance(): TaskRepository = this

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

    fun markTaskCompleted(taskId: String) {
        val now = System.currentTimeMillis()
        var found = false
        _tasks.update { currentList ->
            currentList.map { task ->
                if (task.id == taskId) {
                    found = true
                    task.copy(
                        isCompleted = true,
                        section = TaskSection.COMPLETED,
                        completedAt = now,
                        updatedAt = now
                    )
                } else task
            }
        }

        scope.launch {
            Log.d("TaskRepository", "Syncing task $taskId as COMPLETED to Supabase")
            SupabaseClient.updateTaskCompletion(
                taskId = taskId,
                isCompleted = true,
                section = TaskSection.COMPLETED.name
            ).onSuccess {
                Log.d("TaskRepository", "Successfully marked task $taskId as COMPLETED in Supabase")
            }.onFailure { error ->
                Log.e("TaskRepository", "Failed marking task $taskId COMPLETED in Supabase: ${error.message}")
            }

            if (!found) {
                refreshFromSupabase()
            }
        }
    }

    fun markTaskOverdue(taskId: String) {
        val now = System.currentTimeMillis()
        var found = false
        _tasks.update { currentList ->
            currentList.map { task ->
                if (task.id == taskId) {
                    found = true
                    task.copy(
                        isCompleted = false,
                        section = TaskSection.OVERDUE,
                        updatedAt = now
                    )
                } else task
            }
        }

        scope.launch {
            Log.d("TaskRepository", "Syncing task $taskId as OVERDUE to Supabase")
            SupabaseClient.updateTaskCompletion(
                taskId = taskId,
                isCompleted = false,
                section = TaskSection.OVERDUE.name
            ).onSuccess {
                Log.d("TaskRepository", "Successfully marked task $taskId as OVERDUE in Supabase")
            }.onFailure { error ->
                Log.e("TaskRepository", "Failed marking task $taskId OVERDUE in Supabase: ${error.message}")
            }

            if (!found) {
                refreshFromSupabase()
            }
        }
    }

    fun toggleTaskCompletion(taskId: String) {
        var updatedTask: Task? = null
        val now = System.currentTimeMillis()
        _tasks.update { currentList ->
            currentList.map { task ->
                if (task.id == taskId) {
                    val willComplete = !task.isCompleted
                    val newSection = if (willComplete) TaskSection.COMPLETED else {
                        if (task.dueDate.contains("Yesterday", ignoreCase = true)) TaskSection.OVERDUE
                        else if (task.dueDate.contains("Today", ignoreCase = true)) TaskSection.TODAY
                        else TaskSection.UPCOMING
                    }
                    val modified = task.copy(
                        isCompleted = willComplete,
                        section = newSection,
                        completedAt = if (willComplete) now else null,
                        updatedAt = now
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

    fun updateTask(task: Task) {
        // Optimistic UI update
        _tasks.update { currentList ->
            currentList.map { if (it.id == task.id) task else it }
        }

        // Sync to Supabase
        scope.launch {
            SupabaseClient.updateTask(TaskDto.fromDomain(task))
                .onFailure { error ->
                    Log.e("TaskRepository", "Error updating task in Supabase: ${error.message}")
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

    fun getTaskById(taskId: String): Task? {
        return _tasks.value.firstOrNull { it.id == taskId }
    }

    fun getAllTasks(): List<Task> {
        return _tasks.value
    }

    private fun initialMockTasks(): List<Task> = listOf(
        Task(
            id = "task-1",
            title = "Submit monthly expense receipts & invoice audit",
            priority = Priority.URGENT,
            dueDate = "Yesterday",
            dueTime = "5:00 PM",
            attachmentName = "3 files",
            isCompleted = false,
            section = TaskSection.OVERDUE
        ),
        Task(
            id = "task-2",
            title = "Q4 Growth Roadmap Strategy Sync",
            priority = Priority.MEDIUM,
            dueDate = "Today",
            dueTime = "2:00 PM",
            location = "Google Meet",
            isCompleted = false,
            section = TaskSection.TODAY
        ),
        Task(
            id = "task-3",
            title = "Quarterly budget presentation",
            priority = Priority.HIGH,
            dueDate = "Today",
            dueTime = "4:30 PM",
            location = "Boardroom B",
            isCompleted = false,
            section = TaskSection.TODAY
        ),
        Task(
            id = "task-4",
            title = "Mobile Design System V2 Specs handoff",
            priority = Priority.MEDIUM,
            dueDate = "Mon",
            dueTime = "11:00 AM",
            attachmentName = "Figma doc",
            isCompleted = false,
            section = TaskSection.UPCOMING
        ),
        Task(
            id = "task-5",
            title = "Morning routine & hydration checklist",
            priority = Priority.LOW,
            dueDate = "Today",
            dueTime = "8:00 AM",
            isCompleted = true,
            section = TaskSection.COMPLETED
        ),
        Task(
            id = "task-6",
            title = "Weekly sprint planning kick-off",
            priority = Priority.MEDIUM,
            dueDate = "Today",
            dueTime = "9:15 AM",
            isCompleted = true,
            section = TaskSection.COMPLETED
        ),
        Task(
            id = "task-7",
            title = "Review client contract changes",
            priority = Priority.HIGH,
            dueDate = "Today",
            dueTime = "10:30 AM",
            isCompleted = true,
            section = TaskSection.COMPLETED
        )
    )
}
