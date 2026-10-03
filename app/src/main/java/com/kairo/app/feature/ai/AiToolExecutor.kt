package com.kairo.app.feature.ai

import com.kairo.app.KairoApplication
import com.kairo.app.alarm.AlarmScheduler
import com.kairo.app.data.model.Priority
import com.kairo.app.data.model.Task
import com.kairo.app.data.model.TaskSection
import com.kairo.app.data.repository.TaskRepository
import com.kairo.app.ui.utils.DateUtils
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Executes agentic task operations on [TaskRepository] on behalf of the AI.
 */
object AiToolExecutor {

    data class ToolExecutionResult(
        val success: Boolean,
        val message: String,
        val requiresConfirmation: Boolean = false,
        val confirmationPrompt: String? = null,
        val pendingActionJson: String? = null
    )

    /**
     * Executes a tool call by name and arguments JSON.
     */
    fun execute(toolName: String, args: JSONObject): ToolExecutionResult {
        return try {
            when (toolName) {
                "create_task" -> handleCreateTask(args)
                "batch_create_tasks" -> handleBatchCreateTasks(args)
                "update_task" -> handleUpdateTask(args)
                "toggle_task_completion" -> handleToggleCompletion(args)
                "delete_task" -> handleDeleteTask(args)
                "batch_delete_tasks" -> handleBatchDeleteTasks(args)
                "snooze_task" -> handleSnoozeTask(args)
                "adjust_task_hierarchy" -> handleAdjustHierarchy(args)
                "query_task_analytics" -> handleQueryAnalytics()
                "remember_user_rule" -> handleRememberUserRule(args)
                else -> ToolExecutionResult(false, "Unknown tool: $toolName")
            }
        } catch (e: Exception) {
            ToolExecutionResult(false, "Error executing $toolName: ${e.message}")
        }
    }

    private fun JSONObject.optNullableString(key: String): String? {
        return if (has(key) && !isNull(key)) optString(key).trim().takeIf { it.isNotBlank() } else null
    }

    private fun computeSection(dueDate: String, dueDateMillis: Long?): TaskSection {
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val todayEnd = todayStart + 86400000L

        val targetMillis = dueDateMillis ?: if (dueDate.isNotBlank()) DateUtils.parseDueDateMillis(dueDate) else null
        return when {
            dueDate.contains("Yesterday", ignoreCase = true) -> TaskSection.OVERDUE
            dueDate.contains("Today", ignoreCase = true) -> TaskSection.TODAY
            dueDate.contains("Tomorrow", ignoreCase = true) -> TaskSection.UPCOMING
            targetMillis != null && targetMillis < todayStart -> TaskSection.OVERDUE
            targetMillis != null && targetMillis in todayStart until todayEnd -> TaskSection.TODAY
            targetMillis != null && targetMillis >= todayEnd -> TaskSection.UPCOMING
            else -> TaskSection.TODAY
        }
    }

    private fun handleCreateTask(args: JSONObject): ToolExecutionResult {
        val title = args.optString("title", "").trim()
        if (title.isBlank()) {
            return ToolExecutionResult(false, "Missing required field: title")
        }

        val notes = args.optNullableString("notes")
        val priorityStr = args.optString("priority", "LOW").uppercase()
        val priority = try { Priority.valueOf(priorityStr) } catch (_: Exception) { Priority.LOW }
        val dueDateRaw = args.optNullableString("dueDate")
        val dueDate = DateUtils.formatDisplayDate(dueDateRaw)
        val dueDateMillis = DateUtils.parseDueDateMillis(dueDateRaw)
        val dueTime = args.optNullableString("dueTime")
        val location = args.optNullableString("location")
        val repeatType = args.optNullableString("repeatType")
        var parentId = args.optNullableString("parentId")
        val parentTitle = args.optNullableString("parentTitle")

        // Subtask resolution: resolve parent by title if ID is not matching or parentTitle is supplied
        val allTasks = TaskRepository.getAllTasks()
        if (parentId != null && TaskRepository.getTaskById(parentId) == null) {
            val matched = allTasks.find {
                it.title.equals(parentId, ignoreCase = true) || it.title.contains(parentId, ignoreCase = true)
            }
            if (matched != null) parentId = matched.id
        }
        if (parentId == null && parentTitle != null) {
            val matched = allTasks.find {
                it.title.equals(parentTitle, ignoreCase = true) || it.title.contains(parentTitle, ignoreCase = true)
            }
            if (matched != null) parentId = matched.id
        }

        val section = computeSection(dueDate, dueDateMillis)

        val task = Task(
            title = title,
            notes = notes,
            priority = priority,
            section = section,
            dueDate = dueDate,
            dueDateMillis = dueDateMillis,
            dueTime = dueTime,
            location = location,
            repeatType = repeatType,
            parentId = parentId
        )

        TaskRepository.addTask(task)
        try {
            AlarmScheduler.scheduleAlarm(KairoApplication.instance, task)
        } catch (_: Exception) {}

        val parentName = parentId?.let { pid -> TaskRepository.getTaskById(pid)?.title }
        val parentNote = if (parentName != null) " under '$parentName'" else if (parentId != null) " as subtask" else ""
        return ToolExecutionResult(true, "Created task '$title' ($dueDate, $priority Priority)$parentNote.")
    }

    private fun handleBatchCreateTasks(args: JSONObject): ToolExecutionResult {
        val tasksArray = args.optJSONArray("tasks") ?: return ToolExecutionResult(false, "No tasks provided in batch.")
        val parentTitle = args.optNullableString("parentTitle")
        var parentId: String? = null

        // If a group/parent title is provided (e.g. "Groceries"), create the parent task first
        if (parentTitle != null) {
            val parentTask = Task(
                title = parentTitle,
                priority = Priority.MEDIUM,
                section = TaskSection.TODAY,
                dueDate = DateUtils.getTodayDisplayDate(),
                dueDateMillis = System.currentTimeMillis()
            )
            TaskRepository.addTask(parentTask)
            parentId = parentTask.id
        }

        var count = 0
        for (i in 0 until tasksArray.length()) {
            val item = tasksArray.optJSONObject(i) ?: continue
            val title = item.optString("title", "").trim()
            if (title.isBlank()) continue

            val notes = item.optNullableString("notes")
            val priorityStr = item.optString("priority", "LOW").uppercase()
            val priority = try { Priority.valueOf(priorityStr) } catch (_: Exception) { Priority.LOW }
            val dueDateRaw = item.optNullableString("dueDate")
            val dueDate = DateUtils.formatDisplayDate(dueDateRaw)
            val dueDateMillis = DateUtils.parseDueDateMillis(dueDateRaw)
            val dueTime = item.optNullableString("dueTime")
            val section = computeSection(dueDate, dueDateMillis)

            val task = Task(
                title = title,
                notes = notes,
                priority = priority,
                section = section,
                dueDate = dueDate,
                dueDateMillis = dueDateMillis,
                dueTime = dueTime,
                parentId = parentId
            )
            TaskRepository.addTask(task)
            try {
                AlarmScheduler.scheduleAlarm(KairoApplication.instance, task)
            } catch (_: Exception) {}
            count++
        }

        val suffix = if (parentTitle != null) " under parent list '$parentTitle'" else ""
        return ToolExecutionResult(true, "Successfully added $count tasks$suffix.")
    }

    private fun handleUpdateTask(args: JSONObject): ToolExecutionResult {
        val taskId = args.optString("taskId", "").trim()
        val allTasks = TaskRepository.getAllTasks().sortedByDescending { it.createdAt }
        val task = TaskRepository.getTaskById(taskId)
            ?: allTasks.find { it.title.equals(taskId, ignoreCase = true) }
            ?: allTasks.find { it.title.contains(taskId, ignoreCase = true) }
            ?: return ToolExecutionResult(false, "Task with ID or title '$taskId' not found.")

        var updated = task
        if (args.has("title")) {
            val newTitle = args.getString("title").trim()
            if (newTitle.isNotBlank()) updated = updated.copy(title = newTitle)
        }
        if (args.has("notes")) updated = updated.copy(notes = args.optNullableString("notes"))
        if (args.has("priority")) {
            val pStr = args.getString("priority").uppercase()
            val p = try { Priority.valueOf(pStr) } catch (_: Exception) { updated.priority }
            updated = updated.copy(priority = p)
        }
        if (args.has("dueDate")) {
            val dRaw = args.optNullableString("dueDate")
            val d = DateUtils.formatDisplayDate(dRaw)
            val dMillis = DateUtils.parseDueDateMillis(dRaw)
            val sec = computeSection(d, dMillis)
            updated = updated.copy(dueDate = d, dueDateMillis = dMillis, section = sec)
        }
        if (args.has("dueTime")) updated = updated.copy(dueTime = args.optNullableString("dueTime"))
        if (args.has("location")) updated = updated.copy(location = args.optNullableString("location"))

        TaskRepository.updateTask(updated)
        try {
            AlarmScheduler.scheduleAlarm(KairoApplication.instance, updated)
        } catch (_: Exception) {}
        return ToolExecutionResult(true, "Updated task '${updated.title}'.")
    }

    private fun handleToggleCompletion(args: JSONObject): ToolExecutionResult {
        val taskId = args.optString("taskId", "").trim()
        val allTasks = TaskRepository.getAllTasks().sortedByDescending { it.createdAt }
        val task = TaskRepository.getTaskById(taskId)
            ?: allTasks.find { it.title.equals(taskId, ignoreCase = true) }
            ?: allTasks.find { it.title.contains(taskId, ignoreCase = true) }
            ?: allTasks.firstOrNull()
            ?: return ToolExecutionResult(false, "Task with ID '$taskId' not found.")

        TaskRepository.toggleTaskCompletion(task.id)
        val newState = if (task.isCompleted) "active" else "completed"
        return ToolExecutionResult(true, "Marked task '${task.title}' as $newState.")
    }

    private fun handleDeleteTask(args: JSONObject): ToolExecutionResult {
        val rawTaskId = args.optString("taskId", "").trim()
        val titleQuery = args.optString("title", "").trim()
        val allTasks = TaskRepository.getAllTasks().sortedByDescending { it.createdAt }

        val task = when {
            rawTaskId.equals("latest", ignoreCase = true) || rawTaskId.equals("last", ignoreCase = true) -> {
                allTasks.firstOrNull()
            }
            rawTaskId.isNotBlank() -> {
                TaskRepository.getTaskById(rawTaskId)
                    ?: allTasks.find { it.title.equals(rawTaskId, ignoreCase = true) }
                    ?: allTasks.find { it.title.contains(rawTaskId, ignoreCase = true) }
            }
            titleQuery.isNotBlank() -> {
                allTasks.find { it.title.equals(titleQuery, ignoreCase = true) }
                    ?: allTasks.find { it.title.contains(titleQuery, ignoreCase = true) }
            }
            else -> allTasks.firstOrNull()
        } ?: return ToolExecutionResult(false, "No task found to delete.")

        val actualTaskId = task.id
        val subtasks = TaskRepository.getAllTasks().filter { it.parentId == actualTaskId }
        val confirmed = args.optBoolean("confirmed", false)

        if (subtasks.isNotEmpty() && !confirmed) {
            return ToolExecutionResult(
                success = false,
                message = "Task '${task.title}' has ${subtasks.size} subtasks. Confirmation required.",
                requiresConfirmation = true,
                confirmationPrompt = "Task '${task.title}' contains ${subtasks.size} subtask(s). Are you sure you want to delete it and all its subtasks?",
                pendingActionJson = args.put("confirmed", true).put("taskId", actualTaskId).toString()
            )
        }

        TaskRepository.deleteTask(actualTaskId)
        try {
            AlarmScheduler.cancelAlarm(KairoApplication.instance, actualTaskId)
        } catch (_: Exception) {}
        return ToolExecutionResult(true, "Deleted task '${task.title}'.")
    }

    private fun handleBatchDeleteTasks(args: JSONObject): ToolExecutionResult {
        val ids = args.optJSONArray("taskIds") ?: return ToolExecutionResult(false, "No taskIds provided.")
        val confirmed = args.optBoolean("confirmed", false)

        if (!confirmed && ids.length() > 2) {
            return ToolExecutionResult(
                success = false,
                message = "Confirmation required for deleting ${ids.length()} tasks.",
                requiresConfirmation = true,
                confirmationPrompt = "Are you sure you want to delete ${ids.length()} tasks?",
                pendingActionJson = args.put("confirmed", true).toString()
            )
        }

        var count = 0
        for (i in 0 until ids.length()) {
            val id = ids.optString(i)
            if (id.isNotBlank()) {
                TaskRepository.deleteTask(id)
                try {
                    AlarmScheduler.cancelAlarm(KairoApplication.instance, id)
                } catch (_: Exception) {}
                count++
            }
        }
        return ToolExecutionResult(true, "Deleted $count tasks.")
    }

    private fun handleSnoozeTask(args: JSONObject): ToolExecutionResult {
        val taskId = args.optString("taskId", "").trim()
        val task = TaskRepository.getTaskById(taskId)
            ?: return ToolExecutionResult(false, "Task with ID '$taskId' not found.")

        val minutes = args.optInt("minutes", 5)
        val snoozeMillis = System.currentTimeMillis() + (minutes * 60 * 1000L)
        TaskRepository.snoozeTask(taskId, snoozeMillis)
        return ToolExecutionResult(true, "Snoozed task '${task.title}' for $minutes minutes.")
    }

    private fun handleAdjustHierarchy(args: JSONObject): ToolExecutionResult {
        val taskId = args.optString("taskId", "").trim()
        val newParentId = args.optNullableString("newParentId")
        val moveSubtasks = args.optBoolean("moveSubtasks", true)

        val result = TaskRepository.moveTask(taskId, newParentId, moveSubtasks)
        return if (result.isSuccess) {
            val targetStr = if (newParentId != null) "under new parent" else "as top-level task"
            ToolExecutionResult(true, "Moved task $targetStr.")
        } else {
            ToolExecutionResult(false, result.exceptionOrNull()?.message ?: "Failed to move task.")
        }
    }

    private fun handleQueryAnalytics(): ToolExecutionResult {
        val allTasks = TaskRepository.getAllTasks()
        val total = allTasks.size
        val completed = allTasks.count { it.isCompleted }
        val active = total - completed
        val percent = if (total > 0) (completed * 100) / total else 0

        val todayDate = DateUtils.getTodayDisplayDate()
        val todayCount = allTasks.count { !it.isCompleted && (it.section == TaskSection.TODAY || it.dueDate.contains(todayDate)) }
        val overdueCount = allTasks.count { !it.isCompleted && it.section == TaskSection.OVERDUE }
        val highPriority = allTasks.count { !it.isCompleted && (it.priority == Priority.HIGH || it.priority == Priority.URGENT) }

        val summary = """
            Tasks Analytics:
            • Total Tasks: $total
            • Completed: $completed ($percent%)
            • Active Tasks: $active
            • Due Today: $todayCount
            • Overdue: $overdueCount
            • High/Urgent Priority: $highPriority
        """.trimIndent()

        return ToolExecutionResult(true, summary)
    }

    private fun handleRememberUserRule(args: JSONObject): ToolExecutionResult {
        val rule = args.optString("rule", "").trim()
        if (rule.isBlank()) return ToolExecutionResult(false, "No rule specified.")
        AiMemoryManager.learnRule(rule)
        return ToolExecutionResult(true, "Remembered preference: '$rule'. I will strictly adhere to this going forward.")
    }

    /**
     * Builds a compact JSON context of active tasks to supply to the LLM
     * without burning excess tokens.
     */
    fun buildCompactTasksContext(): String {
        val all = TaskRepository.getAllTasks()
        if (all.isEmpty()) return "Currently no tasks in workspace."

        val sorted = all.sortedByDescending { it.createdAt }
        val jsonArray = JSONArray()
        for ((idx, t) in sorted.take(40).withIndex()) {
            val obj = JSONObject()
            obj.put("id", t.id)
            obj.put("title", t.title)
            obj.put("priority", t.priority.name)
            obj.put("dueDate", t.dueDate)
            if (!t.dueTime.isNullOrBlank()) obj.put("dueTime", t.dueTime)
            obj.put("done", t.isCompleted)
            if (t.parentId != null) obj.put("parentId", t.parentId)
            if (idx == 0) obj.put("isLatestCreated", true)
            jsonArray.put(obj)
        }
        return jsonArray.toString()
    }
}
