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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
        SyncManager.onSyncComplete = {
            loadFromLocalDb()
        }
        loadFromLocalDb()
        refreshFromSupabase()
    }

    fun loadFromLocalDb() {
        val userCode = AuthManager.getUserCode()
        val localList = localDb.getTasksForUser(userCode)
        _tasks.value = localList
        Log.d("TaskRepository", "Loaded ${localList.size} tasks immediately from local SQLite database")
        com.kairo.app.location.GeofenceManager.syncGeofencesWithActiveTasks(KairoApplication.instance)
    }

    fun clearTasksLocally() {
        val userCode = AuthManager.getUserCode()
        localDb.clearTasksForUser(userCode)
        _tasks.value = emptyList()
        _undoAction.value = null
    }

    fun refreshFromSupabase() {
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
                if (result.add(c.id)) {
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
        val visited = mutableSetOf<String>()
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

        // 1. Target and all recursive descendants become COMPLETED
        val toComplete = mutableSetOf(taskId)
        toComplete.addAll(getDescendantIds(taskId, allTasks))

        // 2. Cascade upward: A parent becomes completed if ALL its direct children are completed
        val byId = allTasks.associateBy { it.id }.toMutableMap()
        for (id in toComplete) {
            byId[id]?.let {
                byId[id] = it.copy(isCompleted = true, section = TaskSection.COMPLETED, completedAt = now, updatedAt = now)
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
                byId[currentParentId] = parentTask.copy(
                    isCompleted = true,
                    section = TaskSection.COMPLETED,
                    completedAt = now,
                    updatedAt = now
                )
                currentParentId = parentTask.parentId
            } else {
                break
            }
        }

        // Apply state update
        val updatedList = allTasks.map { task ->
            if (toComplete.contains(task.id)) {
                task.copy(
                    isCompleted = true,
                    section = TaskSection.COMPLETED,
                    completedAt = now,
                    updatedAt = now
                )
            } else task
        }
        _tasks.value = updatedList

        // Cancel alarms for auto-completed tasks
        for (id in toComplete) {
            runCatching {
                AlarmScheduler.cancelAlarm(KairoApplication.instance, id)
            }
        }

        // Save to local SQLite and queue sync
        val affected = updatedList.filter { toComplete.contains(it.id) }
        val userCode = AuthManager.getUserCode() ?: ""
        localDb.saveTasksBatch(affected, userCode, syncStatus = "PENDING")
        for (item in affected) {
            localDb.enqueueSyncAction(item.id, "UPDATE", json.encodeToString(TaskDto.fromDomain(item, userCode)), userCode)
        }
        SyncManager.triggerSync()
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
            val toReopen = mutableSetOf(taskId)

            // Rule 1: Unticking a parent -> all its subtasks become not done
            toReopen.addAll(getDescendantIds(taskId, currentTasks))

            // Rule 2: Unticking a subtask -> parent & all done ancestors become not done
            toReopen.addAll(getAncestorIds(taskId, currentTasks))

            fun determineOpenSection(dueDate: String): TaskSection {
                return when {
                    dueDate.contains("Yesterday", ignoreCase = true) -> TaskSection.OVERDUE
                    dueDate.contains("Today", ignoreCase = true) -> TaskSection.TODAY
                    else -> TaskSection.UPCOMING
                }
            }

            val updatedList = currentTasks.map { t ->
                if (toReopen.contains(t.id)) {
                    t.copy(
                        isCompleted = false,
                        section = determineOpenSection(t.dueDate),
                        completedAt = null,
                        updatedAt = now
                    )
                } else t
            }
            _tasks.value = updatedList

            // Save to local SQLite and queue sync
            val affected = updatedList.filter { toReopen.contains(it.id) }
            val userCode = AuthManager.getUserCode() ?: ""
            localDb.saveTasksBatch(affected, userCode, syncStatus = "PENDING")
            for (item in affected) {
                localDb.enqueueSyncAction(item.id, "UPDATE", json.encodeToString(TaskDto.fromDomain(item, userCode)), userCode)
            }
            SyncManager.triggerSync()
        }
    }

    fun markTaskOverdue(taskId: String) {
        val now = System.currentTimeMillis()
        var updatedTask: Task? = null

        _tasks.update { currentList ->
            currentList.map { task ->
                if (task.id == taskId) {
                    val modified = task.copy(
                        isCompleted = false,
                        section = TaskSection.OVERDUE,
                        completedAt = null,
                        updatedAt = now
                    )
                    updatedTask = modified
                    modified
                } else task
            }
        }

        if (updatedTask != null) {
            val userCode = AuthManager.getUserCode() ?: ""
            localDb.saveTask(updatedTask!!, userCode, syncStatus = "PENDING")
            localDb.enqueueSyncAction(taskId, "UPDATE", json.encodeToString(TaskDto.fromDomain(updatedTask!!, userCode)), userCode)
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
                    section = if (t.dueDate.contains("Yesterday", true)) TaskSection.OVERDUE else TaskSection.TODAY,
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
    }

    fun updateTask(task: Task) {
        _tasks.update { currentList ->
            currentList.map { if (it.id == task.id) task else it }
        }

        val userCode = AuthManager.getUserCode() ?: ""
        localDb.saveTask(task, userCode, syncStatus = "PENDING")
        localDb.enqueueSyncAction(task.id, "UPDATE", json.encodeToString(TaskDto.fromDomain(task, userCode)), userCode)
        SyncManager.triggerSync()
    }

    fun reorderTasks(orderedTaskIds: List<String>) {
        val now = System.currentTimeMillis()
        val current = _tasks.value
        val positionMap = orderedTaskIds.mapIndexed { index, id -> id to index }.toMap()
        val updated = current.map { task ->
            val newPos = positionMap[task.id]
            if (newPos != null && newPos != task.position) {
                task.copy(position = newPos, updatedAt = now)
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
            localDb.enqueueSyncAction(id, "DELETE", null, userCode)
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
                        section = TaskSection.TODAY,
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
        _tasks.value = restored
        _undoAction.value = null

        val userCode = AuthManager.getUserCode() ?: ""
        localDb.saveTasksBatch(restored, userCode, syncStatus = "PENDING")
        for (t in restored) {
            localDb.enqueueSyncAction(t.id, "UPDATE", json.encodeToString(TaskDto.fromDomain(t, userCode)), userCode)
        }
        SyncManager.triggerSync()
    }

    fun commitUndo() {
        _undoAction.value = null
    }

    fun getTaskById(taskId: String): Task? {
        return _tasks.value.firstOrNull { it.id == taskId }
    }

    fun getAllTasks(): List<Task> {
        return _tasks.value
    }
}
