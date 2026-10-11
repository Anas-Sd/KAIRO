package com.kairo.app.feature.tasks.location

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.kairo.app.MainActivity
import com.kairo.app.data.auth.AuthManager
import com.kairo.app.data.local.LocalTaskDatabase
import com.kairo.app.data.model.Task

internal object LocationAiNotifier {

    private const val TAG = "LocationAiNotifier"
    private const val CHANNEL_ID = "kairo_location_channel"
    private const val CHANNEL_NAME = "Location Reminders"

    suspend fun notifyUserForLocation(
        context: Context,
        locationName: String,
        latitude: Double,
        longitude: Double
    ) {
        val userCode = AuthManager.getUserCode()
        val localDb = LocalTaskDatabase.getInstance(context)

        // Find active tasks matching this location coordinate or location string
        val allActive = localDb.getActiveTasksWithLocation(userCode).ifEmpty {
            localDb.getActiveTasksWithLocation(null)
        }

        val matchingTasks = allActive.filter { task ->
            val taskLat = task.latitude
            val taskLng = task.longitude
            if (taskLat != null && taskLng != null) {
                LocationSearchHelper.calculateDistance(latitude, longitude, taskLat, taskLng) <= (task.locationRadius + 50)
            } else {
                task.location?.equals(locationName, ignoreCase = true) == true
            }
        }

        if (matchingTasks.isEmpty()) {
            Log.d(TAG, "No matching active tasks found for location '$locationName'")
            return
        }

        Log.d(TAG, "Found ${matchingTasks.size} tasks for arrived location: $locationName")

        val taskTitles = matchingTasks.joinToString(", ") { "\"${it.title}\"" }
        val reminderMessage = if (matchingTasks.size == 1) {
            "You have arrived at $locationName! Remember to complete: $taskTitles."
        } else {
            "You have arrived at $locationName! You have ${matchingTasks.size} tasks here: $taskTitles."
        }

        postNotification(context, locationName, reminderMessage, matchingTasks)
    }

    private fun postNotification(
        context: Context,
        locationName: String,
        message: String,
        tasks: List<Task>
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Proactive reminders triggered when arriving near task locations"
                enableVibration(true)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            locationName.hashCode(),
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle("📍 Arrived at $locationName")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .build()

        notificationManager.notify(locationName.hashCode(), notification)
    }
}
