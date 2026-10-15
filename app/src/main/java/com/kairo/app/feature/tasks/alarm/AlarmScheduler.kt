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
import com.kairo.app.data.repository.TaskRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

internal object AlarmScheduler {

    private const val TAG = "AlarmScheduler"

    fun calculateTriggerMillis(dueDate: String, dueTime: String?, dueDateMillis: Long?): Long? {
        val calendar = Calendar.getInstance()

        // 1. Determine base date using DateUtils
        val baseMillis = dueDateMillis ?: com.kairo.app.ui.utils.DateUtils.parseDueDateMillis(dueDate)
        val dueCal = Calendar.getInstance().apply { timeInMillis = baseMillis }
        val targetYear = dueCal.get(Calendar.YEAR)
        val targetMonth = dueCal.get(Calendar.MONTH)
        val targetDay = dueCal.get(Calendar.DAY_OF_MONTH)

        // 2. Parse time (e.g. "09:41", "10:45 AM", "7:30 PM", "21:30")
        var targetHour = 9
        var targetMinute = 0

        if (!dueTime.isNullOrBlank()) {
            val cleanTime = dueTime.trim()
            val formats = listOf(
                SimpleDateFormat("hh:mm a", Locale.US),
                SimpleDateFormat("h:mm a", Locale.US),
                SimpleDateFormat("hh:mm a", Locale.getDefault()),
                SimpleDateFormat("h:mm a", Locale.getDefault()),
                SimpleDateFormat("HH:mm", Locale.US),
                SimpleDateFormat("H:mm", Locale.US),
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
                val upper = cleanTime.uppercase(Locale.US)
                val isPM = upper.contains("PM")
                val isAM = upper.contains("AM")
                val digitsOnly = cleanTime.replace("[^0-9:]".toRegex(), "")
                val parts = digitsOnly.split(":")
                if (parts.size >= 2) {
                    var h = parts[0].toIntOrNull() ?: 9
                    val m = parts[1].toIntOrNull() ?: 0
                    if (isPM && h < 12) h += 12
                    else if (isAM && h == 12) h = 0
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
            data = Uri.parse("kairo://task/${task.id}")
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
        val showIntent = Intent(context, MainActivity::class.java).apply {
            data = Uri.parse("kairo://task_show/${task.id}")
        }
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
        val requestCode = taskId.hashCode()

        // 1. Cancel exact matching PendingIntent (Standard format)
        val standardIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TRIGGER_ALARM
            data = Uri.parse("kairo://task/$taskId")
        }
        val standardPendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            standardIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(standardPendingIntent)
        standardPendingIntent.cancel()

        // 2. Cancel legacy / fallback format (where data was null)
        val legacyIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TRIGGER_ALARM
        }
        val legacyPendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            legacyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(legacyPendingIntent)
        legacyPendingIntent.cancel()

        // 3. Cancel if task had custom alarmToneUri as intent data (legacy format)
        runCatching {
            val task = TaskRepository.getTaskById(taskId)
            if (!task?.alarmToneUri.isNullOrBlank() && task?.alarmToneUri != "NONE") {
                val toneIntent = Intent(context, AlarmReceiver::class.java).apply {
                    action = AlarmReceiver.ACTION_TRIGGER_ALARM
                    data = Uri.parse(task.alarmToneUri)
                }
                val tonePendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    toneIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.cancel(tonePendingIntent)
                tonePendingIntent.cancel()
            }
        }

        // 4. Cancel showPendingIntent (MainActivity status bar alarm icon)
        val showIntent = Intent(context, MainActivity::class.java).apply {
            data = Uri.parse("kairo://task_show/$taskId")
        }
        val showPendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(showPendingIntent)
        showPendingIntent.cancel()

        val legacyShowIntent = Intent(context, MainActivity::class.java)
        val legacyShowPendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            legacyShowIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(legacyShowPendingIntent)
        legacyShowPendingIntent.cancel()

        Log.d(TAG, "Cancelled all alarm intent variations for task id: $taskId")
    }
}
