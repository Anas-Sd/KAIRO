package com.kairo.app.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.kairo.app.R
import com.kairo.app.ui.screens.alarm.AlarmActivity

class AlarmRingingService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    companion object {
        const val CHANNEL_ID = "kairo_alarm_channel"
        const val NOTIFICATION_ID = 20261001

        const val ACTION_START_RINGING = "com.kairo.app.ACTION_START_RINGING"
        const val ACTION_STOP_RINGING = "com.kairo.app.ACTION_STOP_RINGING"

        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_TASK_NOTES = "extra_task_notes"
        const val EXTRA_TASK_PRIORITY = "extra_task_priority"
        const val EXTRA_TASK_DUE_DATE = "extra_task_due_date"
        const val EXTRA_TASK_DUE_TIME = "extra_task_due_time"
        const val EXTRA_TASK_LOCATION = "extra_task_location"
        const val EXTRA_TASK_ATTACHMENT = "extra_task_attachment"
        const val EXTRA_ALARM_URI = "extra_alarm_uri"

        private const val TAG = "AlarmRingingService"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initVibrator()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "KAIRO Task Alarms"
            val descriptionText = "Critical ringing task reminders and alarms"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableLights(true)
                lightColor = android.graphics.Color.MAGENTA
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 600, 300, 600, 300, 1000)
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                // Silence notification sound since MediaPlayer plays looping alarm audio directly on USAGE_ALARM
                setSound(null, null)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun initVibrator() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action == ACTION_STOP_RINGING) {
            stopRingingAndSelf()
            return START_NOT_STICKY
        }

        val taskId = intent.getStringExtra(EXTRA_TASK_ID).orEmpty()
        val title = intent.getStringExtra(EXTRA_TASK_TITLE).orEmpty().ifBlank { "Task Reminder" }
        val notes = intent.getStringExtra(EXTRA_TASK_NOTES)
        val priority = intent.getStringExtra(EXTRA_TASK_PRIORITY).orEmpty()
        val dueTime = intent.getStringExtra(EXTRA_TASK_DUE_TIME).orEmpty()
        val dueDate = intent.getStringExtra(EXTRA_TASK_DUE_DATE).orEmpty()
        val location = intent.getStringExtra(EXTRA_TASK_LOCATION)
        val attachment = intent.getStringExtra(EXTRA_TASK_ATTACHMENT)
        val toneUriStr = intent.getStringExtra(EXTRA_ALARM_URI)

        // 1. Play Alarm Audio via MediaPlayer on USAGE_ALARM
        playAlarmSound(toneUriStr)

        // 2. Start Alarm Vibration
        startVibration()

        // 3. Build & Show Heads-Up Notification with FullScreenIntent
        val notification = buildAlarmNotification(
            taskId = taskId,
            title = title,
            notes = notes,
            priority = priority,
            dueDate = dueDate,
            dueTime = dueTime,
            location = location,
            attachment = attachment
        )

        startForeground(NOTIFICATION_ID, notification)

        return START_STICKY
    }

    private fun playAlarmSound(customUriStr: String?) {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null

            val ringtoneUri = if (!customUriStr.isNullOrBlank()) {
                Uri.parse(customUriStr)
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, ringtoneUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
            Log.i(TAG, "Playing alarm sound on USAGE_ALARM: $ringtoneUri")
        } catch (e: Exception) {
            Log.e(TAG, "Error playing alarm sound, fallback to default notification sound", e)
            try {
                val fallbackUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(applicationContext, fallbackUri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    isLooping = true
                    prepare()
                    start()
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Fatal failure in fallback alarm player", ex)
            }
        }
    }

    private fun startVibration() {
        val pattern = longArrayOf(0, 600, 300, 600, 300, 1000)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting vibration", e)
        }
    }

    private fun buildAlarmNotification(
        taskId: String,
        title: String,
        notes: String?,
        priority: String,
        dueDate: String,
        dueTime: String,
        location: String?,
        attachment: String?
    ): Notification {
        // PendingIntent for FullScreen Alarm Activity (when phone locked/screen off)
        val fullScreenIntent = Intent(this, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TASK_TITLE, title)
            putExtra(EXTRA_TASK_NOTES, notes)
            putExtra(EXTRA_TASK_PRIORITY, priority)
            putExtra(EXTRA_TASK_DUE_DATE, dueDate)
            putExtra(EXTRA_TASK_DUE_TIME, dueTime)
            putExtra(EXTRA_TASK_LOCATION, location)
            putExtra(EXTRA_TASK_ATTACHMENT, attachment)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            taskId.hashCode(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 1: "Done"
        val doneIntent = Intent(this, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_COMPLETE_ALARM
            putExtra(EXTRA_TASK_ID, taskId)
        }
        val donePendingIntent = PendingIntent.getBroadcast(
            this,
            (taskId + "_done").hashCode(),
            doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 2: "10m" Snooze
        val snoozeIntent = Intent(this, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_SNOOZE_ALARM
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(AlarmReceiver.EXTRA_SNOOZE_MINUTES, 10)
            putExtra(EXTRA_TASK_TITLE, title)
            putExtra(EXTRA_TASK_NOTES, notes)
            putExtra(EXTRA_TASK_PRIORITY, priority)
            putExtra(EXTRA_TASK_DUE_DATE, dueDate)
            putExtra(EXTRA_TASK_DUE_TIME, dueTime)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            this,
            (taskId + "_snooze").hashCode(),
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 3: "Dismiss" (directly moves to Overdue)
        val dismissIntent = Intent(this, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_DISMISS_ALARM
            putExtra(EXTRA_TASK_ID, taskId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            this,
            (taskId + "_dismiss").hashCode(),
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val displayText = buildString {
            if (!notes.isNullOrBlank()) {
                append(notes)
            }
            if (dueTime.isNotBlank()) {
                if (isNotEmpty()) append(" • ")
                append("Due $dueTime")
            }
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(displayText.ifBlank { "Task alarm is ringing" })
            .setSubText("KAIRO • $priority")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setOngoing(true)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .addAction(R.mipmap.ic_launcher, "Done", donePendingIntent)
            .addAction(R.mipmap.ic_launcher, "10m", snoozePendingIntent)
            .addAction(R.mipmap.ic_launcher, "Dismiss", dismissPendingIntent)
            .build()
    }

    private fun stopRingingAndSelf() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (_: Exception) {}

        try {
            vibrator?.cancel()
        } catch (_: Exception) {}

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopRingingAndSelf()
        super.onDestroy()
    }
}
