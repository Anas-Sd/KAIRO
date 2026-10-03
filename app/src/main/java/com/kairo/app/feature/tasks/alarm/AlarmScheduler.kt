package com.kairo.app.feature.tasks.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.kairo.app.MainActivity
import com.kairo.app.data.model.Task
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

internal object AlarmScheduler {

    private const val TAG = "AlarmScheduler"

    fun calculateTriggerMillis(dueDate: String, dueTime: String?, dueDateMillis: Long?): Long? {
        val calendar = Calendar.getInstance()

        // 1. Determine base date
        var targetYear = calendar.get(Calendar.YEAR)
        var targetMonth = calendar.get(Calendar.MONTH)
        var targetDay = calendar.get(Calendar.DAY_OF_MONTH)

        if (dueDateMillis != null) {
            val dueCal = Calendar.getInstance().apply { timeInMillis = dueDateMillis }
            targetYear = dueCal.get(Calendar.YEAR)
            targetMonth = dueCal.get(Calendar.MONTH)
            targetDay = dueCal.get(Calendar.DAY_OF_MONTH)
        } else {
            when {
                dueDate.contains("Today", ignoreCase = true) -> {
                    // today is default
                }
                dueDate.contains("Tomorrow", ignoreCase = true) -> {
                    val tomCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
                    targetYear = tomCal.get(Calendar.YEAR)
                    targetMonth = tomCal.get(Calendar.MONTH)
                    targetDay = tomCal.get(Calendar.DAY_OF_MONTH)
                }
                dueDate.contains("Yesterday", ignoreCase = true) -> {
                    val yestCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
                    targetYear = yestCal.get(Calendar.YEAR)
                    targetMonth = yestCal.get(Calendar.MONTH)
                    targetDay = yestCal.get(Calendar.DAY_OF_MONTH)
                }
                else -> {
                    try {
                        val parsed = SimpleDateFormat("MMM d", Locale.getDefault()).parse(dueDate)
                        if (parsed != null) {
                            val parsedCal = Calendar.getInstance().apply { time = parsed }
                            targetMonth = parsedCal.get(Calendar.MONTH)
                            targetDay = parsedCal.get(Calendar.DAY_OF_MONTH)
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        // 2. Parse time (e.g. "09:41", "10:45 AM", "21:30")
        var targetHour = 9
        var targetMinute = 0

        if (!dueTime.isNullOrBlank()) {
            val cleanTime = dueTime.trim()
            val formats = listOf(
                SimpleDateFormat("hh:mm a", Locale.getDefault()),
                SimpleDateFormat("h:mm a", Locale.getDefault()),
                SimpleDateFormat("HH:mm", Locale.getDefault()),
                SimpleDateFormat("H:mm", Locale.getDefault())
            )
            var parsed = false
            for (fmt in formats) {
                try {
                    val timeDate = fmt.parse(cleanTime)
                    if (timeDate != null) {
                        val timeCal = Calendar.getInstance().apply { time = timeDate }
                        targetHour = timeCal.get(Calendar.HOUR_OF_DAY)
                        targetMinute = timeCal.get(Calendar.MINUTE)
                        parsed = true
                        break
                    }
                } catch (_: Exception) {}
            }
            if (!parsed) {
                // Try simple colon split
                val parts = cleanTime.split(":")
                if (parts.size >= 2) {
                    val h = parts[0].filter { it.isDigit() }.toIntOrNull() ?: 9
                    val m = parts[1].filter { it.isDigit() }.toIntOrNull() ?: 0
                    targetHour = h
                    targetMinute = m
                }
            }
        }

        val targetCalendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, targetYear)
            set(Calendar.MONTH, targetMonth)
            set(Calendar.DAY_OF_MONTH, targetDay)
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, targetMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        return targetCalendar.timeInMillis
    }

    fun scheduleAlarm(context: Context, task: Task, overrideTriggerMillis: Long? = null) {
        if (task.isCompleted) {
            cancelAlarm(context, task.id)
            return
        }

        val triggerTime = overrideTriggerMillis ?: calculateTriggerMillis(task.dueDate, task.dueTime, task.dueDateMillis)
        if (triggerTime == null) {
            Log.w(TAG, "Cannot determine trigger time for task: ${task.title}")
            return
        }

        val now = System.currentTimeMillis()
        if (triggerTime <= now - 60000L) {
            Log.d(TAG, "Alarm time already passed for task: ${task.title}, skipping.")
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        // Check exact alarm permission on Android 12+ (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.w(TAG, "Exact alarm permission not granted yet. Will attempt standard exact alarm.")
            }
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TRIGGER_ALARM
            if (!task.alarmToneUri.isNullOrBlank() && task.alarmToneUri != "NONE") {
                data = Uri.parse(task.alarmToneUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            putExtra(AlarmReceiver.EXTRA_TASK_ID, task.id)
            putExtra(AlarmReceiver.EXTRA_TASK_TITLE, task.title)
            putExtra(AlarmReceiver.EXTRA_TASK_NOTES, task.notes)
            putExtra(AlarmReceiver.EXTRA_TASK_PRIORITY, task.priority.name)
            putExtra(AlarmReceiver.EXTRA_TASK_DUE_DATE, task.dueDate)
            putExtra(AlarmReceiver.EXTRA_TASK_DUE_TIME, task.dueTime ?: "")
            putExtra(AlarmReceiver.EXTRA_TASK_LOCATION, task.location ?: "")
            putExtra(AlarmReceiver.EXTRA_TASK_ATTACHMENT, task.attachmentName ?: "")
            putExtra(AlarmReceiver.EXTRA_ALARM_URI, task.alarmToneUri)
        }

        val requestCode = task.id.hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent opening MainActivity when user taps the status bar alarm icon
        val showIntent = Intent(context, MainActivity::class.java)
        val showPendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                Log.i(TAG, "Scheduled alarm with setAndAllowWhileIdle for '${task.title}' at $triggerTime")
            } else {
                val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, showPendingIntent)
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
                Log.i(TAG, "Successfully scheduled alarm clock for '${task.title}' at $triggerTime")
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException on setAlarmClock, falling back to setAndAllowWhileIdle", e)
            try {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } catch (ex: Exception) {
                Log.e(TAG, "Failed fallback alarm scheduling", ex)
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to schedule alarm", ex)
        }
    }

    fun cancelAlarm(context: Context, taskId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TRIGGER_ALARM
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "Cancelled alarm for task id: $taskId")
    }
}
