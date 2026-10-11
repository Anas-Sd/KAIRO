package com.kairo.app.feature.sync

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.kairo.app.alarm.AlarmScheduler
import com.kairo.app.data.auth.AuthManager
import com.kairo.app.data.local.LocalTaskDatabase
import com.kairo.app.data.model.Priority
import com.kairo.app.data.model.Task
import com.kairo.app.data.model.TaskSection
import com.kairo.app.feature.tasks.location.TaskLocationFacade
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class KairoFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "New FCM Registration Token: $token")
        FcmTokenManager.saveLocalToken(token)
        FcmTokenManager.syncTokenIfLoggedIn(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.i(TAG, "FCM Message received from: ${remoteMessage.from}")
        val data = remoteMessage.data
        Log.d(TAG, "FCM Message data payload: $data")

        val action = data["action"] ?: "SYNC_AND_SCHEDULE"
        val userCode = AuthManager.getUserCode()

        serviceScope.launch {
            try {
                // 1. Direct task payload support (can instantly schedule hardware alarm)
                val taskId = data["id"] ?: data["taskId"]
                val title = data["title"]

                if (!taskId.isNullOrBlank() && !title.isNullOrBlank()) {
                    val priorityStr = data["priority"] ?: "LOW"
                    val priority = runCatching { Priority.valueOf(priorityStr.uppercase()) }.getOrDefault(Priority.LOW)
                    val sectionStr = data["section"] ?: "TODAY"
                    val section = runCatching { TaskSection.valueOf(sectionStr.uppercase()) }.getOrDefault(TaskSection.TODAY)
                    val dueDate = data["dueDate"] ?: data["due_date"] ?: "Today"
                    val dueTime = data["dueTime"] ?: data["due_time"]
                    val dueDateMillis = data["dueDateMillis"]?.toLongOrNull() ?: data["due_date_millis"]?.toLongOrNull()
                    val notes = data["notes"]
                    val location = data["location"]
                    val lat = data["latitude"]?.toDoubleOrNull() ?: data["lat"]?.toDoubleOrNull()
                    val lng = data["longitude"]?.toDoubleOrNull() ?: data["lng"]?.toDoubleOrNull()
                    val alarmToneUri = data["alarmToneUri"] ?: data["alarm_tone_uri"]
                    val alarmToneTitle = data["alarmToneTitle"] ?: data["alarm_tone_title"]

                    val isCompleted = data["isCompleted"]?.toBooleanStrictOrNull()
                        ?: data["is_completed"]?.toBooleanStrictOrNull()
                        ?: (section == TaskSection.COMPLETED)

                    val directTask = Task(
                        id = taskId,
                        title = title,
                        notes = notes,
                        priority = priority,
                        dueDate = dueDate,
                        dueDateMillis = dueDateMillis,
                        dueTime = dueTime,
                        location = location,
                        isCompleted = isCompleted,
                        section = section,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        alarmToneUri = alarmToneUri,
                        alarmToneTitle = alarmToneTitle,
                        latitude = lat,
                        longitude = lng
                    )

                    val localDb = LocalTaskDatabase.getInstance(applicationContext)
                    localDb.saveTask(directTask, userCode, syncStatus = "SYNCED")
                    Log.i(TAG, "Direct task '$title' ($taskId, isCompleted=$isCompleted) written to local SQLite database.")

                    if (isCompleted) {
                        AlarmScheduler.cancelAlarm(applicationContext, taskId)
                    } else {
                        val trigger = AlarmScheduler.calculateTriggerMillis(dueDate, dueTime, dueDateMillis)
                        if (trigger != null && trigger > System.currentTimeMillis()) {
                            AlarmScheduler.scheduleAlarm(applicationContext, directTask, trigger)
                            Log.i(TAG, "Exact hardware alarm scheduled for '$title' at $trigger")
                        }
                    }

                    if (lat != null && lng != null) {
                        TaskLocationFacade.syncGeofencesWithActiveTasks(applicationContext)
                    }

                    SyncManager.onSyncComplete?.invoke()
                }

                // 2. Remote synchronization and alarm reconciliation
                if (action == "SYNC" || action == "SYNC_AND_SCHEDULE" || action == "WAKE_SYNC") {
                    Log.i(TAG, "Triggering cloud sync from FCM wakeup...")
                    SyncManager.performSync(forceCheckNetwork = true)

                    val localDb = LocalTaskDatabase.getInstance(applicationContext)
                    val allTasks = localDb.getTasksForUser(userCode)
                    val now = System.currentTimeMillis()

                    var scheduledCount = 0
                    allTasks.filter { !it.isCompleted }.forEach { task ->
                        val trigger = AlarmScheduler.calculateTriggerMillis(task.dueDate, task.dueTime, task.dueDateMillis)
                        if (trigger != null && trigger > now) {
                            AlarmScheduler.scheduleAlarm(applicationContext, task, trigger)
                            scheduledCount++
                        }
                    }
                    Log.i(TAG, "FCM wakeup sync complete. Verified/scheduled alarms for $scheduledCount active tasks.")

                    TaskLocationFacade.syncGeofencesWithActiveTasks(applicationContext)
                    SyncManager.onSyncComplete?.invoke()
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error handling FCM message: ${e.message}", e)
            }
        }
    }

    companion object {
        private const val TAG = "KairoFCMService"
    }
}
