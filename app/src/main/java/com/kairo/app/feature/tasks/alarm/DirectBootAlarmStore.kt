package com.kairo.app.feature.tasks.alarm

import android.content.Context
import android.os.Build
import android.os.UserManager
import android.util.Log
import com.kairo.app.data.model.Priority
import com.kairo.app.data.model.Task
import org.json.JSONArray
import org.json.JSONObject

/**
 * Representation of an active task alarm cached in Device-Protected (DE) storage.
 * In Direct Boot mode (after scheduled power-on or reboot before the user enters their PIN/pattern),
 * standard Credential-Encrypted (CE) storage is cryptographically locked by Android.
 * This item provides all required metadata to ring the alarm and display the full-screen overlay.
 */
data class DirectBootAlarmItem(
    val taskId: String,
    val title: String,
    val notes: String? = null,
    val priority: String = "MEDIUM",
    val dueDate: String = "",
    val dueTime: String? = null,
    val dueDateMillis: Long? = null,
    val triggerMillis: Long,
    val alarmToneUri: String? = null,
    val location: String? = null,
    val attachmentName: String? = null
) {
    fun toTask(): Task = Task(
        id = taskId,
        title = title,
        notes = notes,
        priority = runCatching { Priority.valueOf(priority.uppercase()) }.getOrDefault(Priority.MEDIUM),
        dueDate = dueDate,
        dueDateMillis = dueDateMillis,
        dueTime = dueTime,
        location = location,
        attachmentName = attachmentName,
        alarmToneUri = alarmToneUri,
        isCompleted = false
    )

    fun toJson(): JSONObject = JSONObject().apply {
        put("taskId", taskId)
        put("title", title)
        put("notes", notes ?: "")
        put("priority", priority)
        put("dueDate", dueDate)
        put("dueTime", dueTime ?: "")
        put("dueDateMillis", dueDateMillis ?: -1L)
        put("triggerMillis", triggerMillis)
        put("alarmToneUri", alarmToneUri ?: "")
        put("location", location ?: "")
        put("attachmentName", attachmentName ?: "")
    }

    companion object {
        fun fromJson(json: JSONObject): DirectBootAlarmItem {
            return DirectBootAlarmItem(
                taskId = json.getString("taskId"),
                title = json.optString("title", "Task Reminder"),
                notes = json.optString("notes").takeIf { it.isNotBlank() },
                priority = json.optString("priority", "MEDIUM"),
                dueDate = json.optString("dueDate", ""),
                dueTime = json.optString("dueTime").takeIf { it.isNotBlank() },
                dueDateMillis = json.optLong("dueDateMillis", -1L).takeIf { it > 0 },
                triggerMillis = json.getLong("triggerMillis"),
                alarmToneUri = json.optString("alarmToneUri").takeIf { it.isNotBlank() },
                location = json.optString("location").takeIf { it.isNotBlank() },
                attachmentName = json.optString("attachmentName").takeIf { it.isNotBlank() }
            )
        }
    }
}

/**
 * DirectBootAlarmStore
 *
 * Manages upcoming task alarms in Android's Device-Protected Storage (DE).
 * Allows alarms to be rescheduled and fired when the phone powers on (e.g., via scheduled power on/off)
 * while the device is still at the lockscreen before first user credential unlock.
 */
object DirectBootAlarmStore {

    private const val TAG = "DirectBootAlarmStore"
    private const val PREFS_NAME = "kairo_direct_boot_alarms"
    private const val KEY_ALARMS_JSON = "active_alarms_json"
    private const val KEY_PENDING_ACTIONS = "pending_boot_actions_json"

    private fun getStorageContext(context: Context): Context {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createDeviceProtectedStorageContext()
        } else {
            context
        }
    }

    fun isUserUnlocked(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager
            userManager?.isUserUnlocked ?: true
        } else {
            true
        }
    }

    @Synchronized
    fun saveAlarm(context: Context, task: Task, triggerMillis: Long) {
        try {
            val deContext = getStorageContext(context)
            val prefs = deContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentList = getAllAlarmsInternal(prefs).toMutableList()

            currentList.removeAll { it.taskId == task.id }
            currentList.add(
                DirectBootAlarmItem(
                    taskId = task.id,
                    title = task.title,
                    notes = task.notes,
                    priority = task.priority.name,
                    dueDate = task.dueDate,
                    dueTime = task.dueTime,
                    dueDateMillis = task.dueDateMillis,
                    triggerMillis = triggerMillis,
                    alarmToneUri = task.alarmToneUri,
                    location = task.location,
                    attachmentName = task.attachmentName
                )
            )

            saveAllAlarmsInternal(prefs, currentList)
            Log.d(TAG, "Saved alarm to DirectBoot storage for task '${task.title}' at $triggerMillis")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving alarm to DirectBoot storage: ${e.message}", e)
        }
    }

    @Synchronized
    fun removeAlarm(context: Context, taskId: String) {
        try {
            val deContext = getStorageContext(context)
            val prefs = deContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentList = getAllAlarmsInternal(prefs).toMutableList()
            val removed = currentList.removeAll { it.taskId == taskId }
            if (removed) {
                saveAllAlarmsInternal(prefs, currentList)
                Log.d(TAG, "Removed alarm from DirectBoot storage for task '$taskId'")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing alarm from DirectBoot storage: ${e.message}", e)
        }
    }

    @Synchronized
    fun getAlarm(context: Context, taskId: String): DirectBootAlarmItem? {
        return try {
            val deContext = getStorageContext(context)
            val prefs = deContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            getAllAlarmsInternal(prefs).firstOrNull { it.taskId == taskId }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving alarm from DirectBoot storage: ${e.message}", e)
            null
        }
    }

    @Synchronized
    fun getAllActiveAlarms(context: Context): List<DirectBootAlarmItem> {
        return try {
            val deContext = getStorageContext(context)
            val prefs = deContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val all = getAllAlarmsInternal(prefs)
            val now = System.currentTimeMillis()
            // Keep alarms that haven't expired more than 15 minutes ago
            val active = all.filter { it.triggerMillis > (now - 15 * 60 * 1000L) }
            if (active.size != all.size) {
                saveAllAlarmsInternal(prefs, active)
            }
            active
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all active alarms: ${e.message}", e)
            emptyList()
        }
    }

    @Synchronized
    fun syncFromTasks(context: Context, tasks: List<Task>) {
        try {
            val deContext = getStorageContext(context)
            val prefs = deContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()

            val activeItems = tasks
                .filter { !it.isCompleted }
                .mapNotNull { task ->
                    val trigger = AlarmScheduler.calculateTriggerMillis(task.dueDate, task.dueTime, task.dueDateMillis)
                    if (trigger != null && trigger > (now - 15 * 60 * 1000L)) {
                        DirectBootAlarmItem(
                            taskId = task.id,
                            title = task.title,
                            notes = task.notes,
                            priority = task.priority.name,
                            dueDate = task.dueDate,
                            dueTime = task.dueTime,
                            dueDateMillis = task.dueDateMillis,
                            triggerMillis = trigger,
                            alarmToneUri = task.alarmToneUri,
                            location = task.location,
                            attachmentName = task.attachmentName
                        )
                    } else null
                }

            saveAllAlarmsInternal(prefs, activeItems)
            Log.d(TAG, "Synchronized ${activeItems.size} active alarms to DirectBoot storage")
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing tasks to DirectBoot storage: ${e.message}", e)
        }
    }

    @Synchronized
    fun recordPendingAction(context: Context, taskId: String, action: String) {
        try {
            val deContext = getStorageContext(context)
            val prefs = deContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString(KEY_PENDING_ACTIONS, "[]")
            val array = JSONArray(jsonStr)
            val obj = JSONObject().apply {
                put("taskId", taskId)
                put("action", action)
                put("timestamp", System.currentTimeMillis())
            }
            array.put(obj)
            prefs.edit().putString(KEY_PENDING_ACTIONS, array.toString()).apply()
            Log.i(TAG, "Recorded pending DirectBoot action: $action for task $taskId")
        } catch (e: Exception) {
            Log.e(TAG, "Error recording pending action: ${e.message}", e)
        }
    }

    @Synchronized
    fun processPendingActions(context: Context) {
        try {
            val deContext = getStorageContext(context)
            val prefs = deContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString(KEY_PENDING_ACTIONS, null) ?: return
            val array = JSONArray(jsonStr)
            if (array.length() == 0) return

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val taskId = obj.getString("taskId")
                val action = obj.getString("action")
                when (action) {
                    "COMPLETE" -> {
                        runCatching {
                            com.kairo.app.data.repository.TaskRepository.markTaskCompleted(taskId)
                        }
                        Log.i(TAG, "Processed pending DirectBoot COMPLETE for task $taskId")
                    }
                    "DISMISS" -> {
                        runCatching {
                            com.kairo.app.data.repository.TaskRepository.markTaskOverdue(taskId)
                        }
                        Log.i(TAG, "Processed pending DirectBoot DISMISS for task $taskId")
                    }
                }
            }
            prefs.edit().remove(KEY_PENDING_ACTIONS).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error processing pending actions: ${e.message}", e)
        }
    }

    private fun getAllAlarmsInternal(prefs: android.content.SharedPreferences): List<DirectBootAlarmItem> {
        val jsonStr = prefs.getString(KEY_ALARMS_JSON, null) ?: return emptyList()
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<DirectBootAlarmItem>()
            for (i in 0 until array.length()) {
                list.add(DirectBootAlarmItem.fromJson(array.getJSONObject(i)))
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing alarms JSON: ${e.message}")
            emptyList()
        }
    }

    private fun saveAllAlarmsInternal(prefs: android.content.SharedPreferences, list: List<DirectBootAlarmItem>) {
        val array = JSONArray()
        for (item in list) {
            array.put(item.toJson())
        }
        prefs.edit().putString(KEY_ALARMS_JSON, array.toString()).apply()
    }
}
