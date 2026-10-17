package com.kairo.app.data.repository

import android.util.Log
import com.kairo.app.KairoApplication
import com.kairo.app.alarm.AlarmScheduler
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

import com.kairo.app.data.auth.AuthManager
import com.kairo.app.data.local.LocalTaskDatabase
import com.kairo.app.data.sync.SyncManager
import com.kairo.app.feature.tasks.location.TaskLocationFacade
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class UndoAction(
    val previousTasks: List<Task>,
    val message: String
)

object TaskRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: Flow<List<Task>> = _tasks.asStateFlow()

    private val _undoAction = MutableStateFlow<UndoAction?>(null)
    val undoAction: Flow<UndoAction?> = _undoAction.asStateFlow()

    private val localDb: LocalTaskDatabase
        get() = LocalTaskDatabase.getInstance(KairoApplication.instance)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    operator fun invoke(): TaskRepository = this
    fun getInstance(): TaskRepository = this

    init {
        if (KairoApplication.isInitialized) {
            SyncManager.onSyncComplete = {
                loadFromLocalDb()
            }
            loadFromLocalDb()
            refreshFromSupabase()
        }
    }

    fun loadFromLocalDb() {
        if (!KairoApplication.isInitialized) return
        val userCode = AuthManager.getUserCode()
        val localList = localDb.getTasksForUser(userCode)
        val sanitizedList = localList.map { task ->
            if (task.dueDate.equals("SNOOZED", ignoreCase = true)) {
                val repairedDate = com.kairo.app.ui.utils.DateUtils.formatDisplayDate(null, task.dueDateMillis ?: task.createdAt)
                task.copy(dueDate = repairedDate)
            } else {
                task
            }
        }
        _tasks.value = sanitizedList
        Log.d("TaskRepository", "Loaded ${sanitizedList.size} tasks immediately from local SQLite database")
        TaskLocationFacade.syncGeofencesWithActiveTasks(KairoApplication.instance)
    }

    fun clearTasksLocally() {
        if (!KairoApplication.isInitialized) return
        val userCode = AuthManager.getUserCode()
        localDb.clearTasksForUser(userCode)
        _tasks.value = emptyList()
        _undoAction.value = null
    }

    fun refreshFromSupabase() {
        if (!KairoApplication.isInitialized) return
        val userCode = AuthManager.getUserCode()
        if (userCode.isNullOrBlank()) {
            _tasks.value = emptyList()
            return
        }
        loadFromLocalDb()
        SyncManager.triggerSync()
    }

    // ==========================================
    // HIERARCHY HELPER QUERIES
    // ==========================================

    fun getDirectSubtasks(parentId: String?): List<Task> {
        return _tasks.value.filter { it.parentId == parentId }.sortedBy { it.position }
    }

    fun getDescendantIds(taskId: String, allTasks: List<Task> = _tasks.value): Set<String> {
        val result = mutableSetOf<String>()
        val queue = ArrayDeque<String>()
        queue.add(taskId)
        while (queue.isNotEmpty()) {
            val curr = queue.removeFirst()
            val children = allTasks.filter { it.parentId == curr }
            for (c in children) {
                if (c.id != taskId && result.add(c.id)) {
                    queue.add(c.id)
                }
            }
        }
        return result
    }

    fun getAncestorIds(taskId: String, allTasks: List<Task> = _tasks.value): List<String> {
        val byId = allTasks.associateBy { it.id }
        val ancestors = mutableListOf<String>()
        var curr = byId[taskId]?.parentId
        val visited = mutableSetOf(taskId)
        while (curr != null && visited.add(curr)) {
            ancestors.add(curr)
            curr = byId[curr]?.parentId
        }
        return ancestors
    }

    fun getTaskPath(taskId: String, allTasks: List<Task> = _tasks.value): String {
        val byId = allTasks.associateBy { it.id }
        val chain = mutableListOf<String>()
        var curr: Task? = byId[taskId]
        val visited = mutableSetOf<String>()
        while (curr != null && visited.add(curr.id)) {
            chain.add(0, curr.title)
            curr = curr.parentId?.let { byId[it] }
        }
        return chain.joinToString(" > ")
    }

    fun getParentTask(task: Task, allTasks: List<Task> = _tasks.value): Task? {
        val pId = task.parentId ?: return null
        return allTasks.firstOrNull { it.id == pId }
    }

    fun getSubtaskProgress(taskId: String, allTasks: List<Task> = _tasks.value): Pair<Int, Int> {
        val direct = allTasks.filter { it.parentId == taskId }
        val doneCount = direct.count { it.isCompleted }
        return Pair(doneCount, direct.size)
    }

    // ==========================================
    // BI-DIRECTIONAL COMPLETION LOGIC
    // ==========================================

    fun markTaskCompleted(taskId: String) {
        val now = System.currentTimeMillis()
        val allTasks = _tasks.value
        val target = allTasks.find { it.id == taskId } ?: return

        commitUndo()
        val previousTasksSnapshot = allTasks.toList()

        // 1. Target and all recursive descendants become COMPLETED
        val toComplete = mutableSetOf(taskId)
        toComplete.addAll(getDescendantIds(taskId, allTasks))

        // 2. Cascade upward: A parent becomes completed if ALL its direct children are completed
        val byId = allTasks.associateBy { it.id }.toMutableMap()
        for (id in toComplete) {
            byId[id]?.let {
                val safeDueDate = if (it.dueDate.equals("SNOOZED", ignoreCase = true)) {
                    com.kairo.app.ui.utils.DateUtils.formatDisplayDate(null, it.dueDateMillis ?: it.createdAt)
                } else {
                    it.dueDate
                }
                byId[id] = it.copy(isCompleted = true, dueDate = safeDueDate, section = TaskSection.COMPLETED, snoozeUntilMillis = null, completedAt = now, updatedAt = now)
            }
        }

        var currentParentId = target.parentId
        val visited = mutableSetOf<String>()
        while (currentParentId != null && visited.add(currentParentId)) {
            val parentTask = byId[currentParentId] ?: break
            val siblings = byId.values.filter { it.parentId == currentParentId }
            val allSiblingsDone = siblings.isNotEmpty() && siblings.all { it.isCompleted || toComplete.contains(it.id) }
            if (allSiblingsDone) {
                toComplete.add(currentParentId)
                val safeDueDate = if (parentTask.dueDate.equals("SNOOZED", ignoreCase = true)) {
                    com.kairo.app.ui.utils.DateUtils.formatDisplayDate(null, parentTask.dueDateMillis ?: parentTask.createdAt)
                } else {
                    parentTask.dueDate
                }
                byId[currentParentId] = parentTask.copy(
                    isCompleted = true,
                    dueDate = safeDueDate,
                    section = TaskSection.COMPLETED,
                    snoozeUntilMillis = null,
                    completedAt = now,
                    updatedAt = now
                )
                currentParentId = parentTask.parentId
            } else {
                break
            }
        }

        val nextOccurrence = if (!target.repeatType.isNullOrBlank() && !target.repeatType.equals("NONE", ignoreCase = true)) {
            com.kairo.app.ui.utils.DateUtils.calculateNextRepeatDueDate(
                currentDueDateMillis = target.dueDateMillis,
                currentDueDateStr = target.dueDate,
                repeatType = target.repeatType,
                repeatDays = target.repeatDays,
                repeatDates = target.repeatDates
            )
        } else null

        // Apply state update
        val updatedList = mutableListOf<Task>()
        val affected = mutableListOf<Task>()

        if (nextOccurrence != null) {
            // REPEATING TASK ROLLING RECURRENCE
            val (nextDueDateStr, nextDueDateMillis) = nextOccurrence
            val nextSection = determineOpenSection(nextDueDateStr, nextDueDateMillis, target.dueTime)

            // 1. Snapshot completed instance in history with traceable ID
            val snapshotId = "snapshot_${target.id}_$now"
            val completedSnapshot = target.copy(
                id = snapshotId,
                isCompleted = true,
                dueDate = if (target.dueDate.equals("SNOOZED", ignoreCase = true)) {
                    com.kairo.app.ui.utils.DateUtils.formatDisplayDate(null, target.dueDateMillis ?: target.createdAt)
                } else target.dueDate,
                section = TaskSection.COMPLETED,
                snoozeUntilMillis = null,
                completedAt = now,
                repeatType = null,
                updatedAt = now
            )
            updatedList.add(completedSnapshot)
            affected.add(completedSnapshot)

            // 2. Roll over active target task to next occurrence
            val rolledOverTarget = target.copy(
                dueDate = nextDueDateStr,
                dueDateMillis = nextDueDateMillis,
                isCompleted = false,
                completedAt = null,
                snoozeUntilMillis = null,
                section = nextSection,
                updatedAt = now
            )

            // 3. Update existing tasks
            for (t in allTasks) {
                if (t.id == target.id) {
                    updatedList.add(rolledOverTarget)
                    affected.add(rolledOverTarget)
                } else if (toComplete.contains(t.id)) {
                    val safeDueDate = if (t.dueDate.equals("SNOOZED", ignoreCase = true)) {
                        com.kairo.app.ui.utils.DateUtils.formatDisplayDate(null, t.dueDateMillis ?: t.createdAt)
                    } else t.dueDate
                    val modified = t.copy(
                        isCompleted = false,
                        dueDate = safeDueDate,
                        section = determineOpenSection(safeDueDate, t.dueDateMillis, t.dueTime),
                        snoozeUntilMillis = null,
                        completedAt = null,
                        updatedAt = now
                    )
                    updatedList.add(modified)
                    affected.add(modified)
                } else {
                    updatedList.add(t)
                }
            }

            _tasks.value = updatedList
            _undoAction.value = UndoAction(previousTasksSnapshot, "Completed '${target.title}'")

            // Cancel old alarm and schedule next alarm for tomorrow/next recurrence
            AlarmScheduler.cancelAlarm(KairoApplication.instance, target.id)
            com.kairo.app.feature.tasks.alarm.TaskAlarmFacade.stopAlarmRinging(KairoApplication.instance)
            AlarmScheduler.scheduleAlarm(KairoApplication.instance, rolledOverTarget)
        } else {
            // STANDARD ONE-OFF TASK COMPLETION
            for (task in allTasks) {
                if (toComplete.contains(task.id)) {
                    val safeDueDate = if (task.dueDate.equals("SNOOZED", ignoreCase = true)) {
                        com.kairo.app.ui.utils.DateUtils.formatDisplayDate(null, task.dueDateMillis ?: task.createdAt)
                    } else task.dueDate
                    val modified = task.copy(
                        isCompleted = true,
                        dueDate = safeDueDate,
                        section = TaskSection.COMPLETED,
                        snoozeUntilMillis = null,
                        completedAt = now,
                        updatedAt = now
                    )
                    updatedList.add(modified)
                    affected.add(modified)
                } else {
                    updatedList.add(task)
                }
            }
            _tasks.value = updatedList
            _undoAction.value = UndoAction(previousTasksSnapshot, "Completed '${target.title}'")

            // Cancel alarms for auto-completed tasks
            for (id in toComplete) {
                runCatching {
                    AlarmScheduler.cancelAlarm(KairoApplication.instance, id)
                    com.kairo.app.feature.tasks.alarm.TaskAlarmFacade.stopAlarmRinging(KairoApplication.instance)
                }
            }
        }

        // Save to local SQLite and queue sync
        val userCode = AuthManager.getUserCode() ?: ""
        localDb.insertAlarmLog(taskId, "COMPLETED", "Task marked as completed")
        localDb.saveTasksBatch(affected, userCode, syncStatus = "PENDING")
        for (item in affected) {
            localDb.enqueueSyncAction(item.id, "UPDATE", json.encodeToString(TaskDto.fromDomain(item, userCode)), userCode)
        }
        SyncManager.triggerSync()
        TaskLocationFacade.syncGeofencesWithActiveTasks(KairoApplication.instance)
    }

    fun toggleTaskCompletion(taskId: String) {
        val currentTasks = _tasks.value
        val task = currentTasks.find { it.id == taskId } ?: return

        if (!task.isCompleted) {
            // Marking as COMPLETED
            markTaskCompleted(taskId)
        } else {
            // Reopening (UNTICKING)
            val now = System.currentTimeMillis()

            // Special handling for recurring task completion snapshots:
            // When unticking a completed snapshot of a recurring task, rewind the master task back to this date!
            if (task.id.startsWith("snapshot_")) {
                val originTaskId = task.id.removePrefix("snapshot_").substringBeforeLast("_")
                val masterTask = currentTasks.find { it.id == originTaskId }
                if (masterTask != null) {
                    val targetDueDate = if (task.dueDate.isNotBlank() && !task.dueDate.equals("SNOOZED", ignoreCase = true)) {
                        task.dueDate
                    } else "Today"
                    val targetDueDateMillis = task.dueDateMillis ?: Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis

                    val rewoundMaster = masterTask.copy(
                        dueDate = targetDueDate,
                        dueDateMillis = targetDueDateMillis,
                        isCompleted = false,
                        completedAt = null,
                        snoozeUntilMillis = null,
                        section = determineOpenSection(targetDueDate, targetDueDateMillis, masterTask.dueTime),
                        updatedAt = now
                    )

                    // Remove snapshot task and update master task
                    val updatedList = currentTasks.filterNot { it.id == task.id }.map {
                        if (it.id == masterTask.id) rewoundMaster else it
                    }
                    _tasks.value = updatedList

                    val userCode = AuthManager.getUserCode() ?: ""
                    localDb.deleteTask(task.id)
                    localDb.enqueueSyncAction(task.id, "DELETE", "", userCode)

                    localDb.saveTask(rewoundMaster, userCode, syncStatus = "PENDING")
                    localDb.enqueueSyncAction(rewoundMaster.id, "UPDATE", json.encodeToString(TaskDto.fromDomain(rewoundMaster, userCode)), userCode)
                    localDb.insertAlarmLog(masterTask.id, "RESTORED", "Recurrence rewound back to $targetDueDate")

                    // Reschedule alarm for the rewound date/time
                    AlarmScheduler.cancelAlarm(KairoApplication.instance, masterTask.id)
                    val trigger = AlarmScheduler.calculateTriggerMillis(targetDueDate, rewoundMaster.dueTime, targetDueDateMillis)
                    if (trigger != null && trigger > now) {
                        AlarmScheduler.scheduleAlarm(KairoApplication.instance, rewoundMaster, trigger)
                    }

                    SyncManager.triggerSync()
                    TaskLocationFacade.syncGeofencesWithActiveTasks(KairoApplication.instance)
                    return
                }
            }

            val toReopen = mutableSetOf(taskId)

            // Rule 1: Unticking a parent -> all its subtasks become not done
            toReopen.addAll(getDescendantIds(taskId, currentTasks))

            // Rule 2: Unticking a subtask -> parent & all done ancestors become not done
            toReopen.addAll(getAncestorIds(taskId, currentTasks))

            val updatedList = currentTasks.map { t ->
                if (toReopen.contains(t.id)) {
                    t.copy(
                        isCompleted = false,
                        section = determineOpenSection(t.dueDate, t.dueDateMillis, t.dueTime),
                        completedAt = null,
                        updatedAt = now
                    )
                } else t
            }
            _tasks.value = updatedList

            localDb.insertAlarmLog(taskId, "RESTORED", "Task marked as active")

            // Save to local SQLite and queue sync
            val affected = updatedList.filter { toReopen.contains(it.id) }
            val userCode = AuthManager.getUserCode() ?: ""
            localDb.saveTasksBatch(affected, userCode, syncStatus = "PENDING")
            for (item in affected) {
                localDb.enqueueSyncAction(item.id, "UPDATE", json.encodeToString(TaskDto.fromDomain(item, userCode)), userCode)
                val trigger = AlarmScheduler.calculateTriggerMillis(item.dueDate, item.dueTime, item.dueDateMillis)
                if (trigger != null && trigger > System.currentTimeMillis()) {
                    AlarmScheduler.scheduleAlarm(KairoApplication.instance, item, trigger)
                }
            }
            SyncManager.triggerSync()
            TaskLocationFacade.syncGeofencesWithActiveTasks(KairoApplication.instance)
        }
    }

    fun completeTasks(taskIds: Set<String>) {
        if (taskIds.isEmpty()) return
        taskIds.forEach { id ->
            markTaskCompleted(id)
        }
    }

    fun markTaskOverdue(taskId: String) {
        runCatching {
            AlarmScheduler.cancelAlarm(KairoApplication.instance, taskId)
        }
        val now = System.currentTimeMillis()
        var updatedTask: Task? = null

        _tasks.update { currentList ->
            currentList.map { task ->
                if (task.id == taskId) {
                    val modified = task.copy(
                        isCompleted = false,
                        section = TaskSection.OVERDUE,
                        snoozeUntilMillis = null,
                        completedAt = null,
                        updatedAt = now
                    )
                    updatedTask = modified
                    modified
                } else task
            }
        }

        val userCode = AuthManager.getUserCode() ?: ""
        if (updatedTask == null) {
            val fromDb = localDb.getTasksForUser(userCode).find { it.id == taskId }
            if (fromDb != null) {
                val modified = fromDb.copy(
                    isCompleted = false,
                    section = TaskSection.OVERDUE,
                    snoozeUntilMillis = null,
                    completedAt = null,
                    updatedAt = now
                )
                updatedTask = modified
                _tasks.update { current ->
                    if (current.any { it.id == taskId }) {
                        current.map { if (it.id == taskId) modified else it }
                    } else {
                        current + modified
                    }
                }
            }
        }

        if (updatedTask != null) {
            localDb.saveTask(updatedTask!!, userCode, syncStatus = "PENDING")
            localDb.enqueueSyncAction(taskId, "UPDATE", json.encodeToString(TaskDto.fromDomain(updatedTask!!, userCode)), userCode)
            SyncManager.triggerSync()
        }
    }

    fun snoozeTask(taskId: String, snoozeMillis: Long) {
        val now = System.currentTimeMillis()
        var updatedTask: Task? = null

        _tasks.update { currentList ->
            currentList.map { task ->
                if (task.id == taskId) {
                    val safeDueDate = if (task.dueDate.equals("SNOOZED", ignoreCase = true)) {
                        com.kairo.app.ui.utils.DateUtils.formatDisplayDate(null, task.dueDateMillis ?: now)
                    } else {
                        task.dueDate
                    }
                    val modified = task.copy(
                        dueDate = safeDueDate,
                        snoozeUntilMillis = snoozeMillis,
                        updatedAt = now
                    )
                    updatedTask = modified
                    modified
                } else task
            }
        }

        val userCode = AuthManager.getUserCode() ?: ""
        if (updatedTask == null) {
            val fromDb = localDb.getTasksForUser(userCode).find { it.id == taskId }
            if (fromDb != null) {
                val safeDueDate = if (fromDb.dueDate.equals("SNOOZED", ignoreCase = true)) {
                    com.kairo.app.ui.utils.DateUtils.formatDisplayDate(null, fromDb.dueDateMillis ?: now)
                } else {
                    fromDb.dueDate
                }
                val modified = fromDb.copy(
                    dueDate = safeDueDate,
                    snoozeUntilMillis = snoozeMillis,
                    updatedAt = now
                )
                updatedTask = modified
                _tasks.update { current ->
                    if (current.any { it.id == taskId }) {
                        current.map { if (it.id == taskId) modified else it }
                    } else {
                        current + modified
                    }
                }
            }
        }

        if (updatedTask != null) {
            val mins = ((snoozeMillis - now + 59000L) / 60000L).coerceAtLeast(1)
            val formattedTime = SimpleDateFormat("h:mm a", Locale.getDefault()).format(java.util.Date(snoozeMillis))
            localDb.insertAlarmLog(taskId, "SNOOZED", "Snoozed for $mins min (until $formattedTime)")
            localDb.saveTask(updatedTask!!, userCode, syncStatus = "PENDING")
            localDb.enqueueSyncAction(taskId, "UPDATE", json.encodeToString(TaskDto.fromDomain(updatedTask!!, userCode)), userCode)
            AlarmScheduler.scheduleAlarm(KairoApplication.instance, updatedTask!!, snoozeMillis)
            SyncManager.triggerSync()
        }
    }

    // ==========================================
    // CRUD OPERATIONS
    // ==========================================

    fun addTask(task: Task) {
        val current = _tasks.value
        val now = System.currentTimeMillis()

        // If added under a completed parent, reopen the parent and its ancestors
        val toReopen = mutableSetOf<String>()
        if (task.parentId != null) {
            val parent = current.find { it.id == task.parentId }
            if (parent != null && parent.isCompleted) {
                toReopen.add(parent.id)
                toReopen.addAll(getAncestorIds(parent.id, current))
            }
        }

        val updatedList = current.map { t ->
            if (toReopen.contains(t.id)) {
                t.copy(
                    isCompleted = false,
                    section = determineOpenSection(t.dueDate, t.dueDateMillis),
                    completedAt = null,
                    updatedAt = now
                )
            } else t
        }

        _tasks.value = listOf(task) + updatedList

        val userCode = AuthManager.getUserCode() ?: ""
        localDb.saveTask(task, userCode, syncStatus = "PENDING")
        localDb.enqueueSyncAction(task.id, "INSERT", json.encodeToString(TaskDto.fromDomain(task, userCode)), userCode)
        if (toReopen.isNotEmpty()) {
            val reopenedTasks = _tasks.value.filter { toReopen.contains(it.id) }
            localDb.saveTasksBatch(reopenedTasks, userCode, syncStatus = "PENDING")
            for (rt in reopenedTasks) {
                localDb.enqueueSyncAction(rt.id, "UPDATE", json.encodeToString(TaskDto.fromDomain(rt, userCode)), userCode)
            }
        }
        SyncManager.triggerSync()
        val hasLocation = task.latitude != null && task.longitude != null
        TaskLocationFacade.syncGeofencesWithActiveTasks(
            KairoApplication.instance,
            forceImmediateCheck = hasLocation
        )
    }

    fun updateTask(task: Task) {
        _tasks.update { currentList ->
            currentList.map { if (it.id == task.id) task else it }
        }

        val userCode = AuthManager.getUserCode() ?: ""
        localDb.saveTask(task, userCode, syncStatus = "PENDING")
        localDb.enqueueSyncAction(task.id, "UPDATE", json.encodeToString(TaskDto.fromDomain(task, userCode)), userCode)
        SyncManager.triggerSync()

        // Synchronize alarm with task state
        runCatching {
            if (KairoApplication.isInitialized) {
                if (task.isCompleted) {
                    AlarmScheduler.cancelAlarm(KairoApplication.instance, task.id)
                } else {
                    AlarmScheduler.scheduleAlarm(KairoApplication.instance, task)
                }
            }
        }

        val hasLocation = task.latitude != null && task.longitude != null
        TaskLocationFacade.syncGeofencesWithActiveTasks(
            KairoApplication.instance,
            forceImmediateCheck = hasLocation
        )
    }

    fun reorderTasks(orderedTaskIds: List<String>) {
        val now = System.currentTimeMillis()
        val current = _tasks.value
        val positionMap = orderedTaskIds.mapIndexed { index, id -> id to index }.toMap()
        val updated = current.map { task ->
            val newPos = positionMap[task.id]
            if (newPos != null) {
                task.copy(position = newPos, updatedAt = now - (newPos * 1000L))
            } else task
        }
        _tasks.value = updated
        val changed = updated.filter { positionMap.containsKey(it.id) }
        val userCode = AuthManager.getUserCode() ?: ""
        localDb.saveTasksBatch(changed, userCode, syncStatus = "PENDING")
        for (ct in changed) {
            localDb.enqueueSyncAction(ct.id, "UPDATE", json.encodeToString(TaskDto.fromDomain(ct, userCode)), userCode)
        }
        SyncManager.triggerSync()
    }

    // ==========================================
    // MOVE TASK & ADJUST PARENT
    // ==========================================

    fun moveTask(taskId: String, newParentId: String?, moveSubtasks: Boolean): Result<Unit> {
        val current = _tasks.value
        val target = current.find { it.id == taskId } ?: return Result.failure(Exception("Task not found"))

        // Check for loop safety: cannot set self or any descendant as parent
        if (newParentId == taskId || (newParentId != null && getDescendantIds(taskId, current).contains(newParentId))) {
            return Result.failure(Exception("Cannot move task inside its own subtasks (cycle detected)"))
        }

        // Check if new parent exists (if not null)
        if (newParentId != null && current.none { it.id == newParentId }) {
            return Result.failure(Exception("The chosen parent task no longer exists"))
        }

        // Commit any previous undo action before taking new snapshot
        commitUndo()

        // Save snapshot for Undo bar
        val snapshot = current.toList()

        val oldParentId = target.parentId
        val directChildren = current.filter { it.parentId == taskId }.sortedBy { it.position }
        val now = System.currentTimeMillis()

        // Calculate target position in new parent
        val siblingCount = current.count { it.parentId == newParentId && it.id != taskId }

        val modifiedList = current.map { item ->
            when {
                item.id == taskId -> {
                    item.copy(parentId = newParentId, position = siblingCount, updatedAt = now)
                }
                !moveSubtasks && item.parentId == taskId -> {
                    // Subtasks move up to task's old parent, inheriting position
                    val childIndex = directChildren.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
                    item.copy(
                        parentId = oldParentId,
                        position = target.position + childIndex,
                        updatedAt = now
                    )
                }
                else -> item
            }
        }.toMutableList()

        // Re-evaluate parent completion states
        recheckParentCompletion(oldParentId, modifiedList, now)
        recheckParentCompletion(newParentId, modifiedList, now)

        _tasks.value = modifiedList
        _undoAction.value = UndoAction(snapshot, "Moved '${target.title}'")

        val userCode = AuthManager.getUserCode() ?: ""
        val changed = modifiedList.filter { updated ->
            val prev = snapshot.find { it.id == updated.id }
            prev == null || prev != updated
        }
        localDb.saveTasksBatch(changed, userCode, syncStatus = "PENDING")
        for (ct in changed) {
            localDb.enqueueSyncAction(ct.id, "UPDATE", json.encodeToString(TaskDto.fromDomain(ct, userCode)), userCode)
        }
        SyncManager.triggerSync()

        return Result.success(Unit)
    }

    // ==========================================
    // DELETE TASK (WITH OR WITHOUT SUBTASKS)
    // ==========================================

    fun deleteTask(taskId: String) {
        deleteTaskWithSubtasks(taskId, deleteSubtasks = false)
    }

    fun deleteTasks(taskIds: Set<String>) {
        if (taskIds.isEmpty()) return
        val current = _tasks.value
        commitUndo()
        val snapshot = current.toList()

        val toDeleteIds = mutableSetOf<String>()
        toDeleteIds.addAll(taskIds)
        for (id in taskIds) {
            toDeleteIds.addAll(getDescendantIds(id, current))
        }

        val modifiedList = current.filterNot { toDeleteIds.contains(it.id) }.toMutableList()
        _tasks.value = modifiedList
        _undoAction.value = UndoAction(snapshot, "Deleted ${taskIds.size} task(s)")

        val userCode = AuthManager.getUserCode() ?: ""
        localDb.deleteTasksBatch(toDeleteIds.toList())
        for (id in toDeleteIds) {
            val taskTitle = current.find { it.id == id }?.title ?: "Task"
            localDb.enqueueSyncAction(id, "DELETE", taskTitle, userCode)
        }
        SyncManager.triggerSync()
        TaskLocationFacade.syncGeofencesWithActiveTasks(KairoApplication.instance)
    }

    fun deleteTaskWithSubtasks(taskId: String, deleteSubtasks: Boolean) {
        val current = _tasks.value
        val target = current.find { it.id == taskId } ?: return

        commitUndo()
        val snapshot = current.toList()

        val oldParentId = target.parentId
        val directChildren = current.filter { it.parentId == taskId }.sortedBy { it.position }
        val now = System.currentTimeMillis()

        val toDeleteIds = mutableSetOf(taskId)
        if (deleteSubtasks) {
            toDeleteIds.addAll(getDescendantIds(taskId, current))
        }

        val modifiedList = current.filterNot { toDeleteIds.contains(it.id) }.map { item ->
            if (!deleteSubtasks && item.parentId == taskId) {
                val childIdx = directChildren.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
                item.copy(
                    parentId = oldParentId,
                    position = target.position + childIdx,
                    updatedAt = now
                )
            } else item
        }.toMutableList()

        // Re-evaluate old parent completion
        recheckParentCompletion(oldParentId, modifiedList, now)

        _tasks.value = modifiedList
        _undoAction.value = UndoAction(snapshot, "Deleted '${target.title}'")

        // Persist deletion and reparenting to local SQLite and queue sync
        val userCode = AuthManager.getUserCode() ?: ""
        localDb.deleteTasksBatch(toDeleteIds.toList())
        for (id in toDeleteIds) {
            val taskTitle = current.find { it.id == id }?.title ?: target.title
            localDb.enqueueSyncAction(id, "DELETE", taskTitle, userCode)
        }
        val changed = modifiedList.filter { updated ->
            val prev = snapshot.find { it.id == updated.id }
            prev == null || prev != updated
        }
        if (changed.isNotEmpty()) {
            localDb.saveTasksBatch(changed, userCode, syncStatus = "PENDING")
            for (ct in changed) {
                localDb.enqueueSyncAction(ct.id, "UPDATE", json.encodeToString(TaskDto.fromDomain(ct, userCode)), userCode)
            }
        }
        SyncManager.triggerSync()
        TaskLocationFacade.syncGeofencesWithActiveTasks(KairoApplication.instance)
    }

    // Recheck completion upward if all remaining subtasks under a parent are done
    private fun recheckParentCompletion(parentId: String?, tasks: MutableList<Task>, now: Long) {
        var curr = parentId
        val visited = mutableSetOf<String>()
        while (curr != null && visited.add(curr)) {
            val parentIdx = tasks.indexOfFirst { it.id == curr }
            if (parentIdx == -1) break
            val parent = tasks[parentIdx]
            val children = tasks.filter { it.parentId == curr }

            if (children.isNotEmpty() && children.all { it.isCompleted }) {
                if (!parent.isCompleted) {
                    tasks[parentIdx] = parent.copy(
                        isCompleted = true,
                        section = TaskSection.COMPLETED,
                        completedAt = now,
                        updatedAt = now
                    )
                }
                curr = parent.parentId
            } else if (children.any { !it.isCompleted }) {
                if (parent.isCompleted) {
                    tasks[parentIdx] = parent.copy(
                        isCompleted = false,
                        section = determineOpenSection(parent.dueDate, parent.dueDateMillis, parent.dueTime),
                        completedAt = null,
                        updatedAt = now
                    )
                }
                curr = parent.parentId
            } else {
                break
            }
        }
    }

    // ==========================================
    // UNDO & DONE BAR ACTIONS
    // ==========================================

    fun performUndo() {
        val action = _undoAction.value ?: return
        val restored = action.previousTasks
        val currentIds = _tasks.value.map { it.id }.toSet()
        val restoredIds = restored.map { it.id }.toSet()
        val toDeleteIds = currentIds - restoredIds

        _tasks.value = restored
        _undoAction.value = null

        val userCode = AuthManager.getUserCode() ?: ""
        if (toDeleteIds.isNotEmpty()) {
            localDb.deleteTasksBatch(toDeleteIds.toList())
            for (id in toDeleteIds) {
                localDb.enqueueSyncAction(id, "DELETE", "", userCode)
            }
        }
        localDb.saveTasksBatch(restored, userCode, syncStatus = "PENDING")
        for (t in restored) {
            localDb.enqueueSyncAction(t.id, "UPDATE", json.encodeToString(TaskDto.fromDomain(t, userCode)), userCode)
            if (!t.isCompleted) {
                val trigger = AlarmScheduler.calculateTriggerMillis(t.dueDate, t.dueTime, t.dueDateMillis)
                if (trigger != null && trigger > System.currentTimeMillis()) {
                    AlarmScheduler.scheduleAlarm(KairoApplication.instance, t, trigger)
                }
            }
        }
        SyncManager.triggerSync()
        TaskLocationFacade.syncGeofencesWithActiveTasks(KairoApplication.instance)
    }

    fun reactivateRecurringTaskForToday(taskId: String): Boolean {
        val now = System.currentTimeMillis()
        val allTasks = _tasks.value
        val target = allTasks.find { it.id == taskId } ?: return false

        // Must be a recurring task
        if (target.repeatType.isNullOrBlank() || target.repeatType.equals("NONE", ignoreCase = true)) {
            return false
        }

        val todayCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayStartMillis = todayCal.timeInMillis

        // 1. Find and remove any completed snapshots for this recurring task
        val snapshotPrefix = "snapshot_${target.id}_"
        val snapshotsToRemove = allTasks.filter { it.id.startsWith(snapshotPrefix) }
        val toDeleteIds = snapshotsToRemove.map { it.id }

        // 2. Rewind target task back to Today
        val rewoundTarget = target.copy(
            dueDate = "Today",
            dueDateMillis = todayStartMillis,
            section = TaskSection.TODAY,
            isCompleted = false,
            completedAt = null,
            snoozeUntilMillis = null,
            updatedAt = now
        )

        val updatedList = allTasks.filterNot { toDeleteIds.contains(it.id) }.map {
            if (it.id == target.id) rewoundTarget else it
        }

        _tasks.value = updatedList

        val userCode = AuthManager.getUserCode() ?: ""
        if (toDeleteIds.isNotEmpty()) {
            localDb.deleteTasksBatch(toDeleteIds)
            for (id in toDeleteIds) {
                localDb.enqueueSyncAction(id, "DELETE", "", userCode)
            }
        }

        localDb.saveTask(rewoundTarget, userCode, syncStatus = "PENDING")
        localDb.enqueueSyncAction(rewoundTarget.id, "UPDATE", json.encodeToString(TaskDto.fromDomain(rewoundTarget, userCode)), userCode)
        localDb.insertAlarmLog(target.id, "RESTORED", "Recurring task reactivated for Today")

        // 3. Reschedule alarm for today if dueTime is set
        AlarmScheduler.cancelAlarm(KairoApplication.instance, target.id)
        val trigger = AlarmScheduler.calculateTriggerMillis("Today", rewoundTarget.dueTime, todayStartMillis)
        if (trigger != null && trigger > now) {
            AlarmScheduler.scheduleAlarm(KairoApplication.instance, rewoundTarget, trigger)
        }

        SyncManager.triggerSync()
        TaskLocationFacade.syncGeofencesWithActiveTasks(KairoApplication.instance)
        return true
    }

    fun rescheduleTaskToToday(taskId: String): Boolean {
        val now = System.currentTimeMillis()
        val allTasks = _tasks.value
        val target = allTasks.find { it.id == taskId } ?: return false

        val todayCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayStartMillis = todayCal.timeInMillis

        val rescheduled = target.copy(
            dueDate = "Today",
            dueDateMillis = todayStartMillis,
            section = TaskSection.TODAY,
            snoozeUntilMillis = null,
            updatedAt = now
        )

        _tasks.update { current ->
            current.map { if (it.id == taskId) rescheduled else it }
        }

        val userCode = AuthManager.getUserCode() ?: ""
        localDb.saveTask(rescheduled, userCode, syncStatus = "PENDING")
        localDb.enqueueSyncAction(rescheduled.id, "UPDATE", json.encodeToString(TaskDto.fromDomain(rescheduled, userCode)), userCode)
        localDb.insertAlarmLog(taskId, "POSTPONED", "Rescheduled to Today")

        val trigger = AlarmScheduler.calculateTriggerMillis("Today", rescheduled.dueTime, todayStartMillis)
        if (trigger != null && trigger > now) {
            AlarmScheduler.scheduleAlarm(KairoApplication.instance, rescheduled, trigger)
        } else {
            AlarmScheduler.cancelAlarm(KairoApplication.instance, taskId)
        }

        SyncManager.triggerSync()
        TaskLocationFacade.syncGeofencesWithActiveTasks(KairoApplication.instance)
        return true
    }

    fun rescheduleTasksToToday(taskIds: Collection<String>): Int {
        if (taskIds.isEmpty()) return 0
        val now = System.currentTimeMillis()
        val allTasks = _tasks.value
        val idSet = taskIds.toSet()

        val todayCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayStartMillis = todayCal.timeInMillis

        val affected = mutableListOf<Task>()
        val updatedList = allTasks.map { task ->
            if (idSet.contains(task.id)) {
                val rescheduled = task.copy(
                    dueDate = "Today",
                    dueDateMillis = todayStartMillis,
                    section = TaskSection.TODAY,
                    snoozeUntilMillis = null,
                    updatedAt = now
                )
                affected.add(rescheduled)
                rescheduled
            } else task
        }

        if (affected.isEmpty()) return 0

        _tasks.value = updatedList

        val userCode = AuthManager.getUserCode() ?: ""
        localDb.saveTasksBatch(affected, userCode, syncStatus = "PENDING")
        for (item in affected) {
            localDb.enqueueSyncAction(item.id, "UPDATE", json.encodeToString(TaskDto.fromDomain(item, userCode)), userCode)
            localDb.insertAlarmLog(item.id, "POSTPONED", "Rescheduled to Today")
            val trigger = AlarmScheduler.calculateTriggerMillis("Today", item.dueTime, todayStartMillis)
            if (trigger != null && trigger > now) {
                AlarmScheduler.scheduleAlarm(KairoApplication.instance, item, trigger)
            } else {
                AlarmScheduler.cancelAlarm(KairoApplication.instance, item.id)
            }
        }

        SyncManager.triggerSync()
        TaskLocationFacade.syncGeofencesWithActiveTasks(KairoApplication.instance)
        return affected.size
    }

    fun commitUndo() {
        _undoAction.value = null
    }

    fun getTaskById(taskId: String): Task? {
        return _tasks.value.firstOrNull { it.id == taskId } ?: runCatching { if (KairoApplication.isInitialized) localDb.getTaskById(taskId) else null }.getOrNull()
    }

    fun getAllTasks(): List<Task> {
        return _tasks.value
    }

    private fun determineOpenSection(dueDate: String, dueDateMillis: Long? = null, dueTime: String? = null): TaskSection {
        if (dueDate.equals("SNOOZED", ignoreCase = true)) {
            return TaskSection.TODAY
        }
        val now = System.currentTimeMillis()
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val todayEnd = todayStart + (24 * 60 * 60 * 1000L)

        val targetMillis = dueDateMillis ?: com.kairo.app.ui.utils.DateUtils.parseDueDateMillis(dueDate)

        if (dueDate.contains("Yesterday", ignoreCase = true) || targetMillis < todayStart) {
            return TaskSection.OVERDUE
        }

        if (!dueTime.isNullOrBlank()) {
            val trigger = com.kairo.app.alarm.AlarmScheduler.calculateTriggerMillis(dueDate, dueTime, targetMillis)
            if (trigger != null && trigger < now) {
                return TaskSection.OVERDUE
            }
        }

        return when {
            targetMillis < todayEnd -> TaskSection.TODAY
            else -> TaskSection.UPCOMING
        }
    }
}
