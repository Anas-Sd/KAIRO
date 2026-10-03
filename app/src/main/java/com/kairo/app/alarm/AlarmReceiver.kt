package com.kairo.app.alarm

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.kairo.app.data.model.Priority
import com.kairo.app.data.model.Task
import com.kairo.app.data.model.TaskSection
import com.kairo.app.data.repository.TaskRepository
import com.kairo.app.ui.screens.alarm.AlarmActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TRIGGER_ALARM = "com.kairo.app.ACTION_TRIGGER_ALARM"
        const val ACTION_COMPLETE_ALARM = "com.kairo.app.ACTION_COMPLETE_ALARM"
        const val ACTION_SNOOZE_ALARM = "com.kairo.app.ACTION_SNOOZE_ALARM"
        const val ACTION_DISMISS_ALARM = "com.kairo.app.ACTION_DISMISS_ALARM"

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
        }
    }

    private fun handleTriggerAlarm(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID).orEmpty()
        val title = intent.getStringExtra(EXTRA_TASK_TITLE).orEmpty().ifBlank { "Task Reminder" }
        val notes = intent.getStringExtra(EXTRA_TASK_NOTES)
        val priority = intent.getStringExtra(EXTRA_TASK_PRIORITY).orEmpty()
        val dueDate = intent.getStringExtra(EXTRA_TASK_DUE_DATE).orEmpty()
        val dueTime = intent.getStringExtra(EXTRA_TASK_DUE_TIME).orEmpty()
        val location = intent.getStringExtra(EXTRA_TASK_LOCATION)
        val attachment = intent.getStringExtra(EXTRA_TASK_ATTACHMENT)
        val alarmToneUri = intent.getStringExtra(EXTRA_ALARM_URI)

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

        // 2. If phone is locked or screen is off, launch AlarmActivity directly
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isScreenOn = powerManager?.isInteractive ?: true
        val isLocked = keyguardManager?.isKeyguardLocked ?: false

        if (!isScreenOn || isLocked) {
            val activityIntent = Intent(context, AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_TASK_TITLE, title)
                putExtra(EXTRA_TASK_NOTES, notes)
                putExtra(EXTRA_TASK_PRIORITY, priority)
                putExtra(EXTRA_TASK_DUE_DATE, dueDate)
                putExtra(EXTRA_TASK_DUE_TIME, dueTime)
                putExtra(EXTRA_TASK_LOCATION, location)
                putExtra(EXTRA_TASK_ATTACHMENT, attachment)
            }
            context.startActivity(activityIntent)
        }
    }

    private fun handleCompleteAlarm(context: Context, taskId: String) {
        Log.i(TAG, "handleCompleteAlarm triggered for taskId: $taskId")
        stopRinging(context)
        notifyAlarmOverlayFinish(context)

        if (taskId.isNotBlank()) {
            AlarmScheduler.cancelAlarm(context, taskId)
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
            // User requested: When dismissed, the task directly moves to the OVERDUE category
            AlarmScheduler.cancelAlarm(context, taskId)
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
            Toast.makeText(context, "Alarm dismissed • Task moved to Overdue", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRinging(context: Context) {
        AlarmRingingService.stop(context)
    }

    private fun notifyAlarmOverlayFinish(context: Context) {
        val finishBroadcast = Intent(AlarmActivity.ACTION_FINISH_ALARM_OVERLAY).apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(finishBroadcast)
    }
}
