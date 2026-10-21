package com.kairo.app.feature.tasks.alarm

import android.app.ActivityOptions
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.kairo.app.data.local.LocalTaskDatabase
import com.kairo.app.data.model.Priority
import com.kairo.app.data.model.Task
import com.kairo.app.data.repository.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

open class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TRIGGER_ALARM = "com.kairo.app.ACTION_TRIGGER_ALARM"
        const val ACTION_COMPLETE_ALARM = "com.kairo.app.ACTION_COMPLETE_ALARM"
        const val ACTION_SNOOZE_ALARM = "com.kairo.app.ACTION_SNOOZE_ALARM"
        const val ACTION_DISMISS_ALARM = "com.kairo.app.ACTION_DISMISS_ALARM"
        const val ACTION_POSTPONE_ALARM = "com.kairo.app.ACTION_POSTPONE_ALARM"

        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_TASK_NOTES = "extra_task_notes"
        const val EXTRA_TASK_PRIORITY = "extra_task_priority"
        const val EXTRA_TASK_DUE_DATE = "extra_task_due_date"
        const val EXTRA_TASK_DUE_TIME = "extra_task_due_time"
        const val EXTRA_TASK_LOCATION = "extra_task_location"
        const val EXTRA_TASK_ATTACHMENT = "extra_task_attachment"
        const val EXTRA_SNOOZE_MINUTES = "extra_snooze_minutes"
        const val EXTRA_ALARM_URI = "extra_alarm_uri"
        const val EXTRA_POSTPONE_MILLIS = "extra_postpone_millis"
        const val EXTRA_POSTPONE_DATE = "extra_postpone_date"
        const val EXTRA_POSTPONE_TIME = "extra_postpone_time"

        private const val TAG = "AlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val taskId = intent.getStringExtra(EXTRA_TASK_ID).orEmpty()
        Log.i(TAG, "onReceive triggered with action: $action for taskId: $taskId")

        when (action) {
            ACTION_TRIGGER_ALARM -> handleTriggerAlarm(context, intent)
            ACTION_COMPLETE_ALARM -> handleCompleteAlarm(context, taskId)
            ACTION_SNOOZE_ALARM -> handleSnoozeAlarm(context, intent)
            ACTION_DISMISS_ALARM -> handleDismissAlarm(context, taskId)
            ACTION_POSTPONE_ALARM -> handlePostponeAlarm(context, intent)
        }
    }

    private fun handleTriggerAlarm(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID).orEmpty()

        // 0. Active Validation Guard: Never ring for completed, deleted, or future-rescheduled tasks
        if (taskId.isNotBlank()) {
            val isUnlocked = DirectBootAlarmStore.isUserUnlocked(context)
            if (isUnlocked) {
                val task = TaskRepository.getTaskById(taskId)
                    ?: runCatching { LocalTaskDatabase.getInstance(context).getTaskById(taskId) }.getOrNull()

                if (task == null) {
                    Log.i(TAG, "Task '$taskId' no longer exists in database. Aborting alarm.")
                    AlarmScheduler.cancelAlarm(context, taskId)
                    return
                }

                if (task.isCompleted) {
                    Log.i(TAG, "Task '$taskId' ('${task.title}') is already completed. Aborting alarm.")
                    AlarmScheduler.cancelAlarm(context, taskId)
                    return
                }

                val currentTrigger = AlarmScheduler.calculateTriggerMillis(task.dueDate, task.dueTime, task.dueDateMillis)
                if (currentTrigger != null && currentTrigger > System.currentTimeMillis() + 60000L) {
                    Log.i(TAG, "Task '$taskId' ('${task.title}') was rescheduled to the future ($currentTrigger). Skipping obsolete alarm.")
                    AlarmScheduler.cancelAlarm(context, taskId)
                    AlarmScheduler.scheduleAlarm(context, task, currentTrigger)
                    return
                }
            } else {
                // Direct Boot mode: User has not unlocked the phone yet. Validate against DirectBootAlarmStore
                val directBootItem = DirectBootAlarmStore.getAlarm(context, taskId)
                Log.i(TAG, "Alarm triggered in Direct Boot mode for task '$taskId' (found in DE store: ${directBootItem != null})")
            }
        }

        val title = intent.getStringExtra(EXTRA_TASK_TITLE).orEmpty().ifBlank { "Task Reminder" }
        val notes = intent.getStringExtra(EXTRA_TASK_NOTES)
        val priority = intent.getStringExtra(EXTRA_TASK_PRIORITY).orEmpty()
        val dueDate = intent.getStringExtra(EXTRA_TASK_DUE_DATE).orEmpty()
        val dueTime = intent.getStringExtra(EXTRA_TASK_DUE_TIME).orEmpty()
        val location = intent.getStringExtra(EXTRA_TASK_LOCATION)
        val attachment = intent.getStringExtra(EXTRA_TASK_ATTACHMENT)
        val alarmToneUri = intent.getStringExtra(EXTRA_ALARM_URI)

        // 0. Acquire reliable WakeLock to turn on the screen immediately even if device is sleeping or locked
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        @Suppress("DEPRECATION")
        val wakeLock = pm?.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
            PowerManager.ACQUIRE_CAUSES_WAKEUP or
            PowerManager.ON_AFTER_RELEASE,
            "kairo:AlarmWakeLock"
        )
        try {
            wakeLock?.acquire(30000L) // 30 seconds screen wake lock
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire wake lock: ${e.message}")
        }

        // 1. Start foreground ringing audio service
        val serviceIntent = Intent(context, AlarmRingingService::class.java).apply {
            action = AlarmRingingService.ACTION_START_RINGING
            if (!alarmToneUri.isNullOrBlank() && alarmToneUri != "NONE") {
                data = Uri.parse(alarmToneUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            putExtra(AlarmRingingService.EXTRA_TASK_ID, taskId)
            putExtra(AlarmRingingService.EXTRA_TASK_TITLE, title)
            putExtra(AlarmRingingService.EXTRA_TASK_NOTES, notes)
            putExtra(AlarmRingingService.EXTRA_TASK_PRIORITY, priority)
            putExtra(AlarmRingingService.EXTRA_TASK_DUE_DATE, dueDate)
            putExtra(AlarmRingingService.EXTRA_TASK_DUE_TIME, dueTime)
            putExtra(AlarmRingingService.EXTRA_TASK_LOCATION, location)
            putExtra(AlarmRingingService.EXTRA_TASK_ATTACHMENT, attachment)
            putExtra(AlarmRingingService.EXTRA_ALARM_URI, alarmToneUri)
        }
        ContextCompat.startForegroundService(context, serviceIntent)

        if (taskId.isNotBlank() && DirectBootAlarmStore.isUserUnlocked(context)) {
            try {
                LocalTaskDatabase.getInstance(context).insertAlarmLog(taskId, "RING", "Alarm triggered and ringing")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to log RING event: ${e.message}")
            }
        }

        // 2. Always launch full-screen AlarmActivity directly over any screen/lockscreen
        val activityIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TASK_TITLE, title)
            putExtra(EXTRA_TASK_NOTES, notes)
            putExtra(EXTRA_TASK_PRIORITY, priority)
            putExtra(EXTRA_TASK_DUE_DATE, dueDate)
            putExtra(EXTRA_TASK_DUE_TIME, dueTime)
            putExtra(EXTRA_TASK_LOCATION, location)
            putExtra(EXTRA_TASK_ATTACHMENT, attachment)
            putExtra(EXTRA_ALARM_URI, alarmToneUri)
        }

        val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityOptions.makeBasic().apply {
                pendingIntentBackgroundActivityStartMode = ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            }.toBundle()
        } else {
            null
        }

        try {
            if (options != null) {
                context.startActivity(activityIntent, options)
            } else {
                context.startActivity(activityIntent)
            }
            Log.i(TAG, "Direct full-screen AlarmActivity started from receiver successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed direct startActivity from receiver: ${e.message}")
        }

        // Secondary dispatch via PendingIntent for Android 14+ background launch guarantees
        try {
            val pending = PendingIntent.getActivity(
                context,
                taskId.hashCode(),
                activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                options
            )
            if (options != null) {
                pending.send(context, 0, null, null, null, null, options)
            } else {
                pending.send()
            }
        } catch (e: Exception) {
            Log.d(TAG, "PendingIntent send fallback attempted: ${e.message}")
        }
    }

    private fun handleCompleteAlarm(context: Context, taskId: String) {
        Log.i(TAG, "handleCompleteAlarm triggered for taskId: $taskId")
        stopRinging(context)
        notifyAlarmOverlayFinish(context)

        if (taskId.isNotBlank()) {
            DirectBootAlarmStore.removeAlarm(context, taskId)
            AlarmScheduler.cancelAlarm(context, taskId)
            if (DirectBootAlarmStore.isUserUnlocked(context)) {
                try {
                    LocalTaskDatabase.getInstance(context).insertAlarmLog(taskId, "COMPLETED", "Completed via alarm")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to log COMPLETED event: ${e.message}")
                }
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        TaskRepository.markTaskCompleted(taskId)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in handleCompleteAlarm: ${e.message}", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
            } else {
                DirectBootAlarmStore.recordPendingAction(context, taskId, "COMPLETE")
            }
            Toast.makeText(context, "Task marked as completed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleSnoozeAlarm(context: Context, intent: Intent) {
        stopRinging(context)
        notifyAlarmOverlayFinish(context)

        val taskId = intent.getStringExtra(EXTRA_TASK_ID).orEmpty()
        val snoozeMinutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 10)
        val snoozeMillis = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000L)

        if (taskId.isNotBlank()) {
            try {
                val formattedTime = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(snoozeMillis))
                LocalTaskDatabase.getInstance(context).insertAlarmLog(taskId, "SNOOZED", "Snoozed for $snoozeMinutes min (until $formattedTime)")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to log SNOOZED event: ${e.message}")
            }
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    TaskRepository.snoozeTask(taskId, snoozeMillis)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in handleSnoozeAlarm: ${e.message}", e)
                } finally {
                    pendingResult.finish()
                }
            }
        } else {
            val title = intent.getStringExtra(EXTRA_TASK_TITLE).orEmpty().ifBlank { "Task Reminder" }
            val notes = intent.getStringExtra(EXTRA_TASK_NOTES)
            val priorityStr = intent.getStringExtra(EXTRA_TASK_PRIORITY).orEmpty()
            val priority = runCatching { Priority.valueOf(priorityStr) }.getOrDefault(Priority.MEDIUM)
            val dueDate = intent.getStringExtra(EXTRA_TASK_DUE_DATE).orEmpty()
            val dueTime = intent.getStringExtra(EXTRA_TASK_DUE_TIME).orEmpty()
            val location = intent.getStringExtra(EXTRA_TASK_LOCATION)
            val attachment = intent.getStringExtra(EXTRA_TASK_ATTACHMENT)
            val alarmToneUri = intent.getStringExtra(EXTRA_ALARM_URI)

            val placeholderTask = Task(
                id = taskId,
                title = title,
                notes = notes,
                priority = priority,
                dueDate = dueDate,
                dueTime = dueTime,
                dueDateMillis = snoozeMillis,
                location = location,
                attachmentName = attachment,
                alarmToneUri = alarmToneUri
            )

            AlarmScheduler.scheduleAlarm(context, placeholderTask, overrideTriggerMillis = snoozeMillis)
        }
        Toast.makeText(context, "Alarm snoozed for $snoozeMinutes minutes", Toast.LENGTH_SHORT).show()
    }

    private fun handleDismissAlarm(context: Context, taskId: String) {
        Log.i(TAG, "handleDismissAlarm triggered for taskId: $taskId")
        stopRinging(context)
        notifyAlarmOverlayFinish(context)

        if (taskId.isNotBlank()) {
            DirectBootAlarmStore.removeAlarm(context, taskId)
            AlarmScheduler.cancelAlarm(context, taskId)
            if (DirectBootAlarmStore.isUserUnlocked(context)) {
                try {
                    LocalTaskDatabase.getInstance(context).insertAlarmLog(taskId, "DISMISSED", "Alarm dismissed (moved to Overdue)")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to log DISMISSED event: ${e.message}")
                }
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        TaskRepository.markTaskOverdue(taskId)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in handleDismissAlarm: ${e.message}", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
            } else {
                DirectBootAlarmStore.recordPendingAction(context, taskId, "DISMISS")
            }
            Toast.makeText(context, "Alarm dismissed • Task moved to Overdue", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handlePostponeAlarm(context: Context, intent: Intent) {
        stopRinging(context)
        notifyAlarmOverlayFinish(context)

        val taskId = intent.getStringExtra(EXTRA_TASK_ID).orEmpty()
        val newDateStr = intent.getStringExtra(EXTRA_POSTPONE_DATE).orEmpty()
        val newTimeStr = intent.getStringExtra(EXTRA_POSTPONE_TIME).orEmpty()
        val targetMillis = intent.getLongExtra(EXTRA_POSTPONE_MILLIS, 0L)

        if (taskId.isNotBlank() && targetMillis > 0L) {
            try {
                LocalTaskDatabase.getInstance(context).insertAlarmLog(taskId, "POSTPONED", "Postponed to $newDateStr at $newTimeStr")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to log POSTPONED event: ${e.message}")
            }
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val task = TaskRepository.getTaskById(taskId)
                        ?: LocalTaskDatabase.getInstance(context).getTaskById(taskId)
                    if (task != null) {
                        val todayStart = java.util.Calendar.getInstance().apply {
                            set(java.util.Calendar.HOUR_OF_DAY, 0)
                            set(java.util.Calendar.MINUTE, 0)
                            set(java.util.Calendar.SECOND, 0)
                            set(java.util.Calendar.MILLISECOND, 0)
                        }.timeInMillis
                        val todayEnd = todayStart + (24 * 60 * 60 * 1000L)
                        val assignedSection = when {
                            targetMillis in todayStart until todayEnd -> com.kairo.app.data.model.TaskSection.TODAY
                            else -> com.kairo.app.data.model.TaskSection.UPCOMING
                        }
                        val updated = task.copy(
                            dueDate = newDateStr,
                            dueDateMillis = targetMillis,
                            dueTime = newTimeStr,
                            section = assignedSection,
                            isCompleted = false,
                            completedAt = null,
                            snoozeUntilMillis = null,
                            updatedAt = System.currentTimeMillis()
                        )
                        TaskRepository.updateTask(updated)
                        AlarmScheduler.cancelAlarm(context, taskId)
                        AlarmScheduler.scheduleAlarm(context, updated, targetMillis)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in handlePostponeAlarm: ${e.message}", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private fun stopRinging(context: Context) {
        AlarmRingingService.stop(context)
    }

    private fun notifyAlarmOverlayFinish(context: Context) {
        try {
            AlarmOverlayManager.dismiss()
        } catch (_: Exception) {}
        val finishBroadcast = Intent(AlarmActivity.ACTION_FINISH_ALARM_OVERLAY).apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(finishBroadcast)
    }
}
