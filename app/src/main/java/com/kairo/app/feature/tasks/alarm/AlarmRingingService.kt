package com.kairo.app.feature.tasks.alarm

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.kairo.app.R
import com.kairo.app.data.local.LocalTaskDatabase
import com.kairo.app.data.repository.TaskRepository
import java.io.File

open class AlarmRingingService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentTaskId: String? = null

    companion object {
        const val CHANNEL_ID = "kairo_alarm_fullscreen_channel_v7"
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

        @Volatile
        private var instance: AlarmRingingService? = null

        fun stop(context: Context) {
            Log.i(TAG, "AlarmRingingService.stop called")
            try {
                AlarmOverlayManager.dismiss()
            } catch (_: Exception) {}
            try {
                instance?.stopRingingAndSelf()
            } catch (_: Exception) {}
            try {
                context.stopService(Intent(context, AlarmRingingService::class.java))
            } catch (_: Exception) {}
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.cancel(NOTIFICATION_ID)
            } catch (_: Exception) {}
        }

        fun duckVolume(ducked: Boolean) {
            try {
                val player = instance?.mediaPlayer
                if (player != null) {
                    if (ducked) {
                        player.setVolume(0.12f, 0.12f)
                        Log.i(TAG, "Ducked alarm volume to 12% for voice recognition")
                    } else {
                        player.setVolume(1.0f, 1.0f)
                        Log.i(TAG, "Restored alarm volume to 100%")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error adjusting alarm volume: ${e.message}")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        initVibrator()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "KAIRO Task Alarms"
            val descriptionText = "Full-screen alarm alerts and lock screen overlays"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableLights(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 600, 300, 600, 300, 1000)
                // Use bundled silent audio so the notification channel has a valid alarm sound
                // (preventing Android from treating the notification as silent and suppressing fullScreenIntent),
                // while ensuring no unwanted tone is played over the MediaPlayer.
                val silentUri = Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${packageName}/${R.raw.kairo_silent}")
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()
                setSound(silentUri, audioAttributes)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            runCatching {
                notificationManager.deleteNotificationChannel("kairo_alarm_fullscreen_channel")
                notificationManager.deleteNotificationChannel("kairo_alarm_fullscreen_channel_v2")
                notificationManager.deleteNotificationChannel("kairo_alarm_fullscreen_channel_v3")
                notificationManager.deleteNotificationChannel("kairo_alarm_fullscreen_channel_v4")
                notificationManager.deleteNotificationChannel("kairo_alarm_fullscreen_channel_v5")
                notificationManager.deleteNotificationChannel("kairo_alarm_fullscreen_channel_v6")
            }
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
        currentTaskId = taskId

        // Validation guard: Terminate immediately if task is completed or deleted
        if (taskId.isNotBlank()) {
            val task = TaskRepository.getTaskById(taskId)
                ?: LocalTaskDatabase.getInstance(this).getTaskById(taskId)
            if (task == null || task.isCompleted) {
                Log.i(TAG, "Task '$taskId' is completed or deleted. Terminating AlarmRingingService immediately.")
                stopRingingAndSelf()
                return START_NOT_STICKY
            }
        }
        val title = intent.getStringExtra(EXTRA_TASK_TITLE).orEmpty().ifBlank { "Task Reminder" }
        val notes = intent.getStringExtra(EXTRA_TASK_NOTES)
        val priority = intent.getStringExtra(EXTRA_TASK_PRIORITY).orEmpty()
        val dueTime = intent.getStringExtra(EXTRA_TASK_DUE_TIME).orEmpty()
        val dueDate = intent.getStringExtra(EXTRA_TASK_DUE_DATE).orEmpty()
        val location = intent.getStringExtra(EXTRA_TASK_LOCATION)
        val attachment = intent.getStringExtra(EXTRA_TASK_ATTACHMENT)
        val toneUriStr = intent.getStringExtra(EXTRA_ALARM_URI)

        // 0. Acquire Service WakeLock to keep screen on & CPU alive while alarm is ringing
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        try {
            if (wakeLock == null) {
                @Suppress("DEPRECATION")
                wakeLock = pm?.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
                    "kairo:AlarmRingingServiceWakeLock"
                )
                wakeLock?.acquire(10 * 60 * 1000L) // 10 minutes maximum ringing
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire ringing WakeLock: ${e.message}")
        }

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // 3.5. Direct full-screen WindowManager overlay directly over ANY foreground app (Instagram, YouTube, etc.)
        try {
            AlarmOverlayManager.show(
                context = applicationContext,
                taskId = taskId,
                title = title,
                notes = notes,
                priority = priority,
                dueDate = dueDate,
                dueTime = dueTime,
                location = location,
                attachment = attachment
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to display full-screen WindowManager overlay: ${e.message}")
        }

        // 4. Force AlarmActivity directly to the foreground for full-screen alarm experience
        try {
            val fullScreenIntent = Intent(this, AlarmActivity::class.java).apply {
                setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                )
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_TASK_TITLE, title)
                putExtra(EXTRA_TASK_NOTES, notes)
                putExtra(EXTRA_TASK_PRIORITY, priority)
                putExtra(EXTRA_TASK_DUE_DATE, dueDate)
                putExtra(EXTRA_TASK_DUE_TIME, dueTime)
                putExtra(EXTRA_TASK_LOCATION, location)
                putExtra(EXTRA_TASK_ATTACHMENT, attachment)
                putExtra(EXTRA_ALARM_URI, toneUriStr)
            }

            val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ActivityOptions.makeBasic().apply {
                    pendingIntentBackgroundActivityStartMode = ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                }.toBundle()
            } else {
                null
            }

            if (options != null) {
                startActivity(fullScreenIntent, options)
            } else {
                startActivity(fullScreenIntent)
            }
            Log.i(TAG, "Direct full-screen AlarmActivity launched from service")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AlarmActivity directly from service: ${e.message}")
        }

        return START_STICKY
    }

    private fun playAlarmSound(customUriStr: String?) {
        if (customUriStr == "NONE") {
            Log.i(TAG, "Alarm tone set to NONE: running in silent mode")
            return
        }

        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null

            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                isLooping = true
            }

            var sourceConfigured = false

            if (!customUriStr.isNullOrBlank()) {
                val uri = Uri.parse(customUriStr)
                Log.i(TAG, "Attempting to play custom alarm URI: $customUriStr (scheme: ${uri.scheme})")

                if (uri.scheme == "file") {
                    val path = uri.path
                    if (path != null && File(path).exists()) {
                        player.setDataSource(path)
                        sourceConfigured = true
                        Log.i(TAG, "Configured dataSource from local file: $path")
                    }
                }

                if (!sourceConfigured && uri.scheme == "content") {
                    try {
                        val tonesDir = File(filesDir, "custom_tones").apply { mkdirs() }
                        val md = java.security.MessageDigest.getInstance("MD5")
                        val hash = md.digest(customUriStr.toByteArray()).joinToString("") { "%02x".format(it) }
                        val cachedFile = File(tonesDir, "tone_$hash.mp3")
                        if (!cachedFile.exists() || cachedFile.length() == 0L) {
                            applicationContext.contentResolver.openInputStream(uri)?.use { input ->
                                java.io.FileOutputStream(cachedFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                        if (cachedFile.exists() && cachedFile.length() > 0L) {
                            player.setDataSource(cachedFile.absolutePath)
                            sourceConfigured = true
                            Log.i(TAG, "Configured dataSource from cached content file: ${cachedFile.absolutePath}")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Content caching attempt failed: ${e.message}")
                    }
                }

                if (!sourceConfigured && uri.scheme == "content") {
                    try {
                        applicationContext.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                            player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                            sourceConfigured = true
                            Log.i(TAG, "Configured dataSource via openAssetFileDescriptor")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "openAssetFileDescriptor failed: ${e.message}")
                    }

                    if (!sourceConfigured) {
                        try {
                            applicationContext.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                                player.setDataSource(pfd.fileDescriptor)
                                sourceConfigured = true
                                Log.i(TAG, "Configured dataSource via openFileDescriptor")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "openFileDescriptor failed: ${e.message}")
                        }
                    }

                    if (!sourceConfigured) {
                        try {
                            player.setDataSource(applicationContext, uri)
                            sourceConfigured = true
                            Log.i(TAG, "Configured dataSource via setDataSource(context, uri)")
                        } catch (e: Exception) {
                            Log.w(TAG, "setDataSource(context, uri) failed: ${e.message}")
                        }
                    }
                }
            }

            if (!sourceConfigured) {
                val defaultAlarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                Log.w(TAG, "Custom tone not loaded, falling back to system alarm: $defaultAlarmUri")
                player.setDataSource(applicationContext, defaultAlarmUri)
            }

            player.prepare()
            player.start()
            mediaPlayer = player
            Log.i(TAG, "Successfully started alarm audio playback")
        } catch (e: Exception) {
            Log.e(TAG, "Error playing alarm sound, fallback to default alarm/ringtone", e)
            try {
                val fallbackUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
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
        val fullScreenIntent = Intent(this, AlarmActivity::class.java).apply {
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
        }

        val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityOptions.makeBasic().apply {
                pendingIntentBackgroundActivityStartMode = ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            }.toBundle()
        } else {
            null
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            taskId.hashCode(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            options
        )

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

        val deleteIntent = Intent(this, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_DISMISS_ALARM
            putExtra(EXTRA_TASK_ID, taskId)
        }
        val deletePendingIntent = PendingIntent.getBroadcast(
            this,
            (taskId + "_delete").hashCode(),
            deleteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val silentUri = Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${packageName}/${R.raw.kairo_silent}")

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(displayText.ifBlank { "Task alarm ringing" })
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setOngoing(true)
            .setSound(silentUri)
            .setVibrate(longArrayOf(0, 600, 300, 600, 300, 1000))
            .setDeleteIntent(deletePendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .addAction(0, "Mark as Done", donePendingIntent)
            .addAction(0, "Snooze 10m", snoozePendingIntent)
            .addAction(0, "Dismiss", dismissPendingIntent)
            .build()

        notification.flags = notification.flags or
                Notification.FLAG_ONGOING_EVENT or
                Notification.FLAG_NO_CLEAR or
                Notification.FLAG_FOREGROUND_SERVICE

        return notification
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.i(TAG, "onTaskRemoved called - app swiped from recents")
        val taskId = currentTaskId
        if (!taskId.isNullOrBlank()) {
            val dismissIntent = Intent(this, AlarmReceiver::class.java).apply {
                action = AlarmReceiver.ACTION_DISMISS_ALARM
                putExtra(EXTRA_TASK_ID, taskId)
            }
            sendBroadcast(dismissIntent)
        }
        stopRingingAndSelf()
        super.onTaskRemoved(rootIntent)
    }

    private fun stopRingingAndSelf() {
        try {
            AlarmOverlayManager.dismiss()
        } catch (_: Exception) {}

        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        wakeLock = null

        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (_: Exception) {}

        try {
            vibrator?.cancel()
        } catch (_: Exception) {}

        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {}

        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {}

        stopSelf()
    }

    override fun onDestroy() {
        stopRingingAndSelf()
        if (instance == this) {
            instance = null
        }
        super.onDestroy()
    }
}
