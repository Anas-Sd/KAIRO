package com.kairo.app.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.kairo.app.data.model.Priority
import com.kairo.app.data.model.Task
import com.kairo.app.data.model.TaskSection

data class PendingSyncAction(
    val id: Long,
    val taskId: String,
    val action: String, // "INSERT", "UPDATE", "DELETE"
    val payloadJson: String?,
    val userCode: String,
    val createdAt: Long
)

data class OfflineTaskItem(
    val taskId: String,
    val title: String,
    val action: String, // "Created Offline", "Updated Offline", "Deleted Offline", "Stored Offline"
    val timestamp: Long,
    val notes: String? = null,
    val location: String? = null
)

class LocalTaskDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val TAG = "LocalTaskDatabase"
        private const val DATABASE_NAME = "kairo_local_tasks.db"
        private const val DATABASE_VERSION = 2

        // Table local_tasks
        const val TABLE_TASKS = "local_tasks"
        const val COL_ID = "id"
        const val COL_TITLE = "title"
        const val COL_NOTES = "notes"
        const val COL_PRIORITY = "priority"
        const val COL_DUE_DATE = "due_date"
        const val COL_DUE_DATE_MILLIS = "due_date_millis"
        const val COL_DUE_TIME = "due_time"
        const val COL_LOCATION = "location"
        const val COL_ATTACHMENT_NAME = "attachment_name"
        const val COL_ATTACHMENT_URI = "attachment_uri"
        const val COL_IS_COMPLETED = "is_completed"
        const val COL_SECTION = "section"
        const val COL_CREATED_AT = "created_at"
        const val COL_UPDATED_AT = "updated_at"
        const val COL_COMPLETED_AT = "completed_at"
        const val COL_ALARM_TONE_URI = "alarm_tone_uri"
        const val COL_ALARM_TONE_TITLE = "alarm_tone_title"
        const val COL_REPEAT_TYPE = "repeat_type"
        const val COL_REPEAT_DAYS = "repeat_days"
        const val COL_REPEAT_DATES = "repeat_dates"
        const val COL_PARENT_ID = "parent_id"
        const val COL_POSITION = "position"
        const val COL_USER_CODE = "user_code"
        const val COL_SYNC_STATUS = "sync_status" // "SYNCED", "PENDING"
        const val COL_LATITUDE = "latitude"
        const val COL_LONGITUDE = "longitude"
        const val COL_LOCATION_RADIUS = "location_radius"

        // Table sync_queue
        const val TABLE_SYNC_QUEUE = "sync_queue"
        const val COL_QUEUE_ID = "queue_id"
        const val COL_QUEUE_TASK_ID = "task_id"
        const val COL_QUEUE_ACTION = "action"
        const val COL_QUEUE_PAYLOAD = "payload"
        const val COL_QUEUE_USER_CODE = "user_code"
        const val COL_QUEUE_CREATED_AT = "created_at"

        @Volatile
        private var instance: LocalTaskDatabase? = null

        fun getInstance(context: Context): LocalTaskDatabase {
            return instance ?: synchronized(this) {
                instance ?: LocalTaskDatabase(context.applicationContext).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTasksTable = """
            CREATE TABLE IF NOT EXISTS $TABLE_TASKS (
                $COL_ID TEXT PRIMARY KEY,
                $COL_TITLE TEXT NOT NULL,
                $COL_NOTES TEXT,
                $COL_PRIORITY TEXT NOT NULL,
                $COL_DUE_DATE TEXT NOT NULL,
                $COL_DUE_DATE_MILLIS INTEGER,
                $COL_DUE_TIME TEXT,
                $COL_LOCATION TEXT,
                $COL_ATTACHMENT_NAME TEXT,
                $COL_ATTACHMENT_URI TEXT,
                $COL_IS_COMPLETED INTEGER NOT NULL DEFAULT 0,
                $COL_SECTION TEXT NOT NULL,
                $COL_CREATED_AT INTEGER NOT NULL,
                $COL_UPDATED_AT INTEGER NOT NULL,
                $COL_COMPLETED_AT INTEGER,
                $COL_ALARM_TONE_URI TEXT,
                $COL_ALARM_TONE_TITLE TEXT,
                $COL_REPEAT_TYPE TEXT,
                $COL_REPEAT_DAYS TEXT,
                $COL_REPEAT_DATES TEXT,
                $COL_PARENT_ID TEXT,
                $COL_POSITION INTEGER NOT NULL DEFAULT 0,
                $COL_USER_CODE TEXT,
                $COL_SYNC_STATUS TEXT NOT NULL DEFAULT 'SYNCED',
                $COL_LATITUDE REAL,
                $COL_LONGITUDE REAL,
                $COL_LOCATION_RADIUS INTEGER DEFAULT 500
            );
        """.trimIndent()

        val createSyncQueueTable = """
            CREATE TABLE IF NOT EXISTS $TABLE_SYNC_QUEUE (
                $COL_QUEUE_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_QUEUE_TASK_ID TEXT NOT NULL,
                $COL_QUEUE_ACTION TEXT NOT NULL,
                $COL_QUEUE_PAYLOAD TEXT,
                $COL_QUEUE_USER_CODE TEXT NOT NULL,
                $COL_QUEUE_CREATED_AT INTEGER NOT NULL
            );
        """.trimIndent()

        val indexUserCode = "CREATE INDEX IF NOT EXISTS idx_tasks_user_code ON $TABLE_TASKS ($COL_USER_CODE);"
        val indexParentId = "CREATE INDEX IF NOT EXISTS idx_tasks_parent_id ON $TABLE_TASKS ($COL_PARENT_ID);"

        db.execSQL(createTasksTable)
        db.execSQL(createSyncQueueTable)
        db.execSQL(indexUserCode)
        db.execSQL(indexParentId)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            try { db.execSQL("ALTER TABLE $TABLE_TASKS ADD COLUMN $COL_LATITUDE REAL;") } catch (_: Throwable) {}
            try { db.execSQL("ALTER TABLE $TABLE_TASKS ADD COLUMN $COL_LONGITUDE REAL;") } catch (_: Throwable) {}
            try { db.execSQL("ALTER TABLE $TABLE_TASKS ADD COLUMN $COL_LOCATION_RADIUS INTEGER DEFAULT 500;") } catch (_: Throwable) {}
        }
    }

    // ==========================================
    // TASK CRUD OPERATIONS
    // ==========================================

    @Synchronized
    fun getTasksForUser(userCode: String?): List<Task> {
        val tasks = mutableListOf<Task>()
        val db = readableDatabase
        val query = if (userCode.isNullOrBlank()) {
            "SELECT * FROM $TABLE_TASKS WHERE $COL_USER_CODE IS NULL OR $COL_USER_CODE = '' ORDER BY $COL_POSITION ASC, $COL_CREATED_AT DESC"
        } else {
            "SELECT * FROM $TABLE_TASKS WHERE $COL_USER_CODE = ? ORDER BY $COL_POSITION ASC, $COL_CREATED_AT DESC"
        }
        val args = if (userCode.isNullOrBlank()) null else arrayOf(userCode)

        db.rawQuery(query, args).use { cursor ->
            val colId = cursor.getColumnIndex(COL_ID)
            val colTitle = cursor.getColumnIndex(COL_TITLE)
            val colNotes = cursor.getColumnIndex(COL_NOTES)
            val colPriority = cursor.getColumnIndex(COL_PRIORITY)
            val colDueDate = cursor.getColumnIndex(COL_DUE_DATE)
            val colDueDateMillis = cursor.getColumnIndex(COL_DUE_DATE_MILLIS)
            val colDueTime = cursor.getColumnIndex(COL_DUE_TIME)
            val colLocation = cursor.getColumnIndex(COL_LOCATION)
            val colAttName = cursor.getColumnIndex(COL_ATTACHMENT_NAME)
            val colAttUri = cursor.getColumnIndex(COL_ATTACHMENT_URI)
            val colIsCompleted = cursor.getColumnIndex(COL_IS_COMPLETED)
            val colSection = cursor.getColumnIndex(COL_SECTION)
            val colCreatedAt = cursor.getColumnIndex(COL_CREATED_AT)
            val colUpdatedAt = cursor.getColumnIndex(COL_UPDATED_AT)
            val colCompletedAt = cursor.getColumnIndex(COL_COMPLETED_AT)
            val colToneUri = cursor.getColumnIndex(COL_ALARM_TONE_URI)
            val colToneTitle = cursor.getColumnIndex(COL_ALARM_TONE_TITLE)
            val colRepeatType = cursor.getColumnIndex(COL_REPEAT_TYPE)
            val colRepeatDays = cursor.getColumnIndex(COL_REPEAT_DAYS)
            val colRepeatDates = cursor.getColumnIndex(COL_REPEAT_DATES)
            val colParentId = cursor.getColumnIndex(COL_PARENT_ID)
            val colPos = cursor.getColumnIndex(COL_POSITION)
            val colLat = cursor.getColumnIndex(COL_LATITUDE)
            val colLng = cursor.getColumnIndex(COL_LONGITUDE)
            val colRadius = cursor.getColumnIndex(COL_LOCATION_RADIUS)

            while (cursor.moveToNext()) {
                val task = Task(
                    id = if (colId >= 0) cursor.getString(colId) else java.util.UUID.randomUUID().toString(),
                    title = if (colTitle >= 0) cursor.getString(colTitle) else "",
                    notes = if (colNotes >= 0 && !cursor.isNull(colNotes)) cursor.getString(colNotes) else null,
                    priority = if (colPriority >= 0 && !cursor.isNull(colPriority)) {
                        runCatching { Priority.valueOf(cursor.getString(colPriority)) }.getOrDefault(Priority.LOW)
                    } else Priority.LOW,
                    dueDate = if (colDueDate >= 0) cursor.getString(colDueDate) else "Today",
                    dueDateMillis = if (colDueDateMillis >= 0 && !cursor.isNull(colDueDateMillis)) cursor.getLong(colDueDateMillis) else null,
                    dueTime = if (colDueTime >= 0 && !cursor.isNull(colDueTime)) cursor.getString(colDueTime) else null,
                    location = if (colLocation >= 0 && !cursor.isNull(colLocation)) cursor.getString(colLocation) else null,
                    attachmentName = if (colAttName >= 0 && !cursor.isNull(colAttName)) cursor.getString(colAttName) else null,
                    attachmentUri = if (colAttUri >= 0 && !cursor.isNull(colAttUri)) cursor.getString(colAttUri) else null,
                    isCompleted = colIsCompleted >= 0 && cursor.getInt(colIsCompleted) == 1,
                    section = if (colSection >= 0 && !cursor.isNull(colSection)) {
                        runCatching { TaskSection.valueOf(cursor.getString(colSection)) }.getOrDefault(TaskSection.TODAY)
                    } else TaskSection.TODAY,
                    createdAt = if (colCreatedAt >= 0) cursor.getLong(colCreatedAt) else System.currentTimeMillis(),
                    updatedAt = if (colUpdatedAt >= 0) cursor.getLong(colUpdatedAt) else System.currentTimeMillis(),
                    completedAt = if (colCompletedAt >= 0 && !cursor.isNull(colCompletedAt)) cursor.getLong(colCompletedAt) else null,
                    alarmToneUri = if (colToneUri >= 0 && !cursor.isNull(colToneUri)) cursor.getString(colToneUri) else null,
                    alarmToneTitle = if (colToneTitle >= 0 && !cursor.isNull(colToneTitle)) cursor.getString(colToneTitle) else null,
                    repeatType = if (colRepeatType >= 0 && !cursor.isNull(colRepeatType)) cursor.getString(colRepeatType) else null,
                    repeatDays = if (colRepeatDays >= 0 && !cursor.isNull(colRepeatDays)) cursor.getString(colRepeatDays) else null,
                    repeatDates = if (colRepeatDates >= 0 && !cursor.isNull(colRepeatDates)) cursor.getString(colRepeatDates) else null,
                    parentId = if (colParentId >= 0 && !cursor.isNull(colParentId)) cursor.getString(colParentId) else null,
                    position = if (colPos >= 0) cursor.getInt(colPos) else 0,
                    latitude = if (colLat >= 0 && !cursor.isNull(colLat)) cursor.getDouble(colLat) else null,
                    longitude = if (colLng >= 0 && !cursor.isNull(colLng)) cursor.getDouble(colLng) else null,
                    locationRadius = if (colRadius >= 0 && !cursor.isNull(colRadius)) cursor.getInt(colRadius) else 500
                )
                tasks.add(task)
            }
        }
        return tasks
    }

    @Synchronized
    fun getActiveTasksWithLocation(userCode: String?): List<Task> {
        val all = getTasksForUser(userCode)
        return all.filter { !it.isCompleted && it.latitude != null && it.longitude != null }
    }

    @Synchronized
    fun getActiveTasksForGeofence(userCode: String?, lat: Double, lng: Double, toleranceMeters: Double = 300.0): List<Task> {
        val active = getActiveTasksWithLocation(userCode)
        return active.filter { task ->
            val tLat = task.latitude ?: return@filter false
            val tLng = task.longitude ?: return@filter false
            val results = FloatArray(1)
            android.location.Location.distanceBetween(lat, lng, tLat, tLng, results)
            val distance = results[0]
            distance <= (task.locationRadius + toleranceMeters)
        }
    }

    @Synchronized
    fun saveTask(task: Task, userCode: String?, syncStatus: String = "SYNCED") {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_ID, task.id)
            put(COL_TITLE, task.title)
            put(COL_NOTES, task.notes)
            put(COL_PRIORITY, task.priority.name)
            put(COL_DUE_DATE, task.dueDate)
            put(COL_DUE_DATE_MILLIS, task.dueDateMillis)
            put(COL_DUE_TIME, task.dueTime)
            put(COL_LOCATION, task.location)
            put(COL_ATTACHMENT_NAME, task.attachmentName)
            put(COL_ATTACHMENT_URI, task.attachmentUri)
            put(COL_IS_COMPLETED, if (task.isCompleted) 1 else 0)
            put(COL_SECTION, task.section.name)
            put(COL_CREATED_AT, task.createdAt)
            put(COL_UPDATED_AT, task.updatedAt)
            put(COL_COMPLETED_AT, task.completedAt)
            put(COL_ALARM_TONE_URI, task.alarmToneUri)
            put(COL_ALARM_TONE_TITLE, task.alarmToneTitle)
            put(COL_REPEAT_TYPE, task.repeatType)
            put(COL_REPEAT_DAYS, task.repeatDays)
            put(COL_REPEAT_DATES, task.repeatDates)
            put(COL_PARENT_ID, task.parentId)
            put(COL_POSITION, task.position)
            put(COL_USER_CODE, userCode)
            put(COL_SYNC_STATUS, syncStatus)
            put(COL_LATITUDE, task.latitude)
            put(COL_LONGITUDE, task.longitude)
            put(COL_LOCATION_RADIUS, task.locationRadius)
        }
        db.insertWithOnConflict(TABLE_TASKS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized
    fun saveTasksBatch(tasks: List<Task>, userCode: String?, syncStatus: String = "SYNCED") {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (task in tasks) {
                saveTask(task, userCode, syncStatus)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun deleteTask(taskId: String) {
        val db = writableDatabase
        db.delete(TABLE_TASKS, "$COL_ID = ?", arrayOf(taskId))
    }

    @Synchronized
    fun deleteTasksBatch(taskIds: List<String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (id in taskIds) {
                db.delete(TABLE_TASKS, "$COL_ID = ?", arrayOf(id))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun clearTasksForUser(userCode: String?) {
        val db = writableDatabase
        if (userCode.isNullOrBlank()) {
            db.delete(TABLE_TASKS, "$COL_USER_CODE IS NULL OR $COL_USER_CODE = ''", null)
            db.delete(TABLE_SYNC_QUEUE, "$COL_QUEUE_USER_CODE IS NULL OR $COL_QUEUE_USER_CODE = ''", null)
        } else {
            db.delete(TABLE_TASKS, "$COL_USER_CODE = ?", arrayOf(userCode))
            db.delete(TABLE_SYNC_QUEUE, "$COL_QUEUE_USER_CODE = ?", arrayOf(userCode))
        }
    }

    @Synchronized
    fun getTaskSyncStatus(taskId: String): String? {
        val db = readableDatabase
        db.rawQuery("SELECT $COL_SYNC_STATUS FROM $TABLE_TASKS WHERE $COL_ID = ?", arrayOf(taskId)).use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(0)
            }
        }
        return null
    }

    @Synchronized
    fun updateSyncStatus(taskId: String, status: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_SYNC_STATUS, status)
        }
        db.update(TABLE_TASKS, values, "$COL_ID = ?", arrayOf(taskId))
    }

    // ==========================================
    // SYNC QUEUE OPERATIONS
    // ==========================================

    @Synchronized
    fun getSyncActionForTask(taskId: String): PendingSyncAction? {
        val db = readableDatabase
        db.rawQuery("SELECT * FROM $TABLE_SYNC_QUEUE WHERE $COL_QUEUE_TASK_ID = ? LIMIT 1", arrayOf(taskId)).use { cursor ->
            if (cursor.moveToFirst()) {
                return PendingSyncAction(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_QUEUE_ID)),
                    taskId = cursor.getString(cursor.getColumnIndexOrThrow(COL_QUEUE_TASK_ID)),
                    action = cursor.getString(cursor.getColumnIndexOrThrow(COL_QUEUE_ACTION)),
                    payloadJson = cursor.getString(cursor.getColumnIndexOrThrow(COL_QUEUE_PAYLOAD)),
                    userCode = cursor.getString(cursor.getColumnIndexOrThrow(COL_QUEUE_USER_CODE)),
                    createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_QUEUE_CREATED_AT))
                )
            }
        }
        return null
    }

    @Synchronized
    fun enqueueSyncAction(taskId: String, action: String, payloadJson: String?, userCode: String) {
        val db = writableDatabase
        val existingAction = getSyncActionForTask(taskId)
        if (existingAction != null) {
            if (existingAction.action == "INSERT") {
                if (action == "DELETE") {
                    // Task was created offline and deleted offline; simply remove from queue
                    db.delete(TABLE_SYNC_QUEUE, "$COL_QUEUE_TASK_ID = ?", arrayOf(taskId))
                    Log.d(TAG, "Task $taskId created and deleted offline. Dropped from sync queue.")
                    runCatching { com.kairo.app.data.sync.SyncManager.refreshOfflineQueueStatus() }
                    return
                } else if (action == "UPDATE") {
                    // Task was created offline and updated offline; update payload of INSERT
                    val values = ContentValues().apply {
                        put(COL_QUEUE_PAYLOAD, payloadJson)
                        put(COL_QUEUE_CREATED_AT, System.currentTimeMillis())
                    }
                    db.update(TABLE_SYNC_QUEUE, values, "$COL_QUEUE_TASK_ID = ?", arrayOf(taskId))
                    Log.d(TAG, "Updated pending INSERT payload for task: $taskId")
                    runCatching { com.kairo.app.data.sync.SyncManager.refreshOfflineQueueStatus() }
                    return
                }
            } else if (existingAction.action == "UPDATE") {
                if (action == "UPDATE") {
                    // Update latest payload
                    val values = ContentValues().apply {
                        put(COL_QUEUE_PAYLOAD, payloadJson)
                        put(COL_QUEUE_CREATED_AT, System.currentTimeMillis())
                    }
                    db.update(TABLE_SYNC_QUEUE, values, "$COL_QUEUE_TASK_ID = ?", arrayOf(taskId))
                    Log.d(TAG, "Updated pending UPDATE payload for task: $taskId")
                    runCatching { com.kairo.app.data.sync.SyncManager.refreshOfflineQueueStatus() }
                    return
                } else if (action == "DELETE") {
                    // Replace UPDATE with DELETE
                    db.delete(TABLE_SYNC_QUEUE, "$COL_QUEUE_TASK_ID = ?", arrayOf(taskId))
                }
            }
        }

        val values = ContentValues().apply {
            put(COL_QUEUE_TASK_ID, taskId)
            put(COL_QUEUE_ACTION, action)
            put(COL_QUEUE_PAYLOAD, payloadJson)
            put(COL_QUEUE_USER_CODE, userCode)
            put(COL_QUEUE_CREATED_AT, System.currentTimeMillis())
        }
        db.insert(TABLE_SYNC_QUEUE, null, values)
        Log.d(TAG, "Enqueued sync action: $action for task: $taskId")
        runCatching { com.kairo.app.data.sync.SyncManager.refreshOfflineQueueStatus() }
    }

    @Synchronized
    fun getPendingSyncActions(userCode: String?): List<PendingSyncAction> {
        val list = mutableListOf<PendingSyncAction>()
        val db = readableDatabase
        val query = if (userCode.isNullOrBlank()) {
            "SELECT * FROM $TABLE_SYNC_QUEUE WHERE $COL_QUEUE_USER_CODE IS NULL OR $COL_QUEUE_USER_CODE = '' ORDER BY $COL_QUEUE_ID ASC"
        } else {
            "SELECT * FROM $TABLE_SYNC_QUEUE WHERE $COL_QUEUE_USER_CODE = ? OR $COL_QUEUE_USER_CODE IS NULL OR $COL_QUEUE_USER_CODE = '' ORDER BY $COL_QUEUE_ID ASC"
        }
        val args = if (userCode.isNullOrBlank()) null else arrayOf(userCode)
        db.rawQuery(query, args).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    PendingSyncAction(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_QUEUE_ID)),
                        taskId = cursor.getString(cursor.getColumnIndexOrThrow(COL_QUEUE_TASK_ID)),
                        action = cursor.getString(cursor.getColumnIndexOrThrow(COL_QUEUE_ACTION)),
                        payloadJson = cursor.getString(cursor.getColumnIndexOrThrow(COL_QUEUE_PAYLOAD)),
                        userCode = cursor.getString(cursor.getColumnIndexOrThrow(COL_QUEUE_USER_CODE)),
                        createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_QUEUE_CREATED_AT))
                    )
                )
            }
        }
        return list
    }

    @Synchronized
    fun removeSyncAction(actionId: Long) {
        val db = writableDatabase
        db.delete(TABLE_SYNC_QUEUE, "$COL_QUEUE_ID = ?", arrayOf(actionId.toString()))
        runCatching { com.kairo.app.data.sync.SyncManager.refreshOfflineQueueStatus() }
    }

    @Synchronized
    fun getPendingSyncCount(userCode: String?): Int {
        val db = readableDatabase
        val query = if (userCode.isNullOrBlank()) {
            "SELECT COUNT(*) FROM $TABLE_SYNC_QUEUE WHERE $COL_QUEUE_USER_CODE IS NULL OR $COL_QUEUE_USER_CODE = ''"
        } else {
            "SELECT COUNT(*) FROM $TABLE_SYNC_QUEUE WHERE $COL_QUEUE_USER_CODE = ? OR $COL_QUEUE_USER_CODE IS NULL OR $COL_QUEUE_USER_CODE = ''"
        }
        val args = if (userCode.isNullOrBlank()) null else arrayOf(userCode)
        db.rawQuery(query, args).use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getInt(0)
            }
        }
        return 0
    }

    @Synchronized
    fun getOfflinePendingTasks(userCode: String?): List<OfflineTaskItem> {
        val pendingActions = getPendingSyncActions(userCode)
        val allTasksMap = getTasksForUser(userCode).associateBy { it.id }
        val result = mutableListOf<OfflineTaskItem>()
        val processedTaskIds = mutableSetOf<String>()

        for (action in pendingActions) {
            processedTaskIds.add(action.taskId)
            val actionLabel = when (action.action) {
                "INSERT" -> "Created Offline"
                "UPDATE" -> "Updated Offline"
                "DELETE" -> "Deleted Offline"
                else -> action.action
            }

            val localTask = allTasksMap[action.taskId]
            val title = if (localTask != null && localTask.title.isNotBlank()) {
                localTask.title
            } else if (!action.payloadJson.isNullOrBlank()) {
                try {
                    val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }
                    val dto = json.decodeFromString<com.kairo.app.data.remote.TaskDto>(action.payloadJson)
                    dto.title.ifBlank { "Task" }
                } catch (_: Exception) {
                    action.payloadJson.take(35)
                }
            } else {
                "Task (${action.taskId.take(6)})"
            }

            result.add(
                OfflineTaskItem(
                    taskId = action.taskId,
                    title = title,
                    action = actionLabel,
                    timestamp = action.createdAt,
                    notes = localTask?.notes,
                    location = localTask?.location
                )
            )
        }

        // Also check if any task in tasks table has PENDING status not captured in queue
        val pendingStatusTasks = allTasksMap.values.filter { it.id !in processedTaskIds }
        for (task in pendingStatusTasks) {
            val status = getTaskSyncStatus(task.id)
            if (status == "PENDING") {
                result.add(
                    OfflineTaskItem(
                        taskId = task.id,
                        title = task.title,
                        action = "Stored Offline",
                        timestamp = task.updatedAt,
                        notes = task.notes,
                        location = task.location
                    )
                )
            }
        }

        return result.sortedByDescending { it.timestamp }
    }

    // ==========================================
    // MERGE REMOTE TASKS WITH LOCAL STATE
    // ==========================================

    @Synchronized
    fun mergeRemoteTasks(remoteTasks: List<Task>, userCode: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val localTasks = getTasksForUser(userCode).associateBy { it.id }
            val remoteIds = remoteTasks.map { it.id }.toSet()

            // 1. Process Remote Tasks
            for (remote in remoteTasks) {
                val local = localTasks[remote.id]
                if (local == null) {
                    // Check if pending deletion exists in queue
                    val isPendingDelete = isTaskPendingDeletion(remote.id)
                    if (!isPendingDelete) {
                        saveTask(remote, userCode, syncStatus = "SYNCED")
                    }
                } else {
                    // If local has pending changes, keep local changes until uploaded
                    val syncStatus = getTaskSyncStatus(remote.id)
                    if (syncStatus == "SYNCED") {
                        // Remote is authoritative if local was synced
                        saveTask(remote, userCode, syncStatus = "SYNCED")
                    }
                }
            }

            // 2. Remove local tasks that were deleted on remote (only if local status is SYNCED)
            for (local in localTasks.values) {
                if (!remoteIds.contains(local.id)) {
                    val syncStatus = getTaskSyncStatus(local.id)
                    if (syncStatus == "SYNCED") {
                        deleteTask(local.id)
                    }
                }
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun isTaskPendingDeletion(taskId: String): Boolean {
        val db = readableDatabase
        db.rawQuery(
            "SELECT 1 FROM $TABLE_SYNC_QUEUE WHERE $COL_QUEUE_TASK_ID = ? AND $COL_QUEUE_ACTION = 'DELETE'",
            arrayOf(taskId)
        ).use { cursor ->
            return cursor.moveToFirst()
        }
    }
}
