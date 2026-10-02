package com.kairo.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.kairo.app.data.auth.AuthManager
import com.kairo.app.data.local.LocalTaskDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object GeofenceManager {

    private const val TAG = "GeofenceManager"
    private const val ACTION_GEOFENCE_EVENT = "com.kairo.app.ACTION_GEOFENCE_EVENT"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun getGeofencingClient(context: Context): GeofencingClient {
        return LocationServices.getGeofencingClient(context.applicationContext)
    }

    private fun getGeofencePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context.applicationContext, GeofenceBroadcastReceiver::class.java).apply {
            action = ACTION_GEOFENCE_EVENT
        }
        val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(
            context.applicationContext,
            9001,
            intent,
            flags
        )
    }

    fun hasLocationPermission(context: Context): Boolean {
        return try {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun syncGeofencesWithActiveTasks(context: Context) {
        scope.launch {
            try {
                if (!hasLocationPermission(context)) {
                    Log.d(TAG, "Location permission not granted. Skipping geofence registration.")
                    return@launch
                }

                val client = getGeofencingClient(context)
                val pendingIntent = getGeofencePendingIntent(context)
                val userCode = AuthManager.getUserCode()
                val localDb = LocalTaskDatabase.getInstance(context)

                // Get uncompleted tasks with valid coordinates
                val locationTasks = localDb.getActiveTasksWithLocation(userCode)
                if (locationTasks.isEmpty()) {
                    Log.d(TAG, "No active location tasks. Removing all active geofences.")
                    client.removeGeofences(pendingIntent)
                    return@launch
                }

                // Group by location coordinates (rounded to ~10m) to avoid duplicate fences
                val uniqueLocations = locationTasks.groupBy { task ->
                    val latKey = (Math.round((task.latitude ?: 0.0) * 10000.0) / 10000.0).toString()
                    val lngKey = (Math.round((task.longitude ?: 0.0) * 10000.0) / 10000.0).toString()
                    "$latKey,$lngKey"
                }

                val geofences = mutableListOf<Geofence>()
                for ((_, tasksAtSpot) in uniqueLocations) {
                    val sample = tasksAtSpot.first()
                    val lat = sample.latitude ?: continue
                    val lng = sample.longitude ?: continue
                    val radius = sample.locationRadius.toFloat().coerceAtLeast(100f)
                    val locationName = sample.location ?: "Saved Place"

                    val requestId = "$locationName|$lat|$lng"

                    val geofence = Geofence.Builder()
                        .setRequestId(requestId)
                        .setCircularRegion(lat, lng, radius)
                        .setExpirationDuration(Geofence.NEVER_EXPIRE)
                        .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_DWELL)
                        .setLoiteringDelay(60 * 1000) // 1 minute dwell time
                        .build()

                    geofences.add(geofence)
                }

                if (geofences.isNotEmpty()) {
                    val request = GeofencingRequest.Builder()
                        .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER or GeofencingRequest.INITIAL_TRIGGER_DWELL)
                        .addGeofences(geofences)
                        .build()

                    client.addGeofences(request, pendingIntent)
                        .addOnSuccessListener {
                            Log.d(TAG, "Successfully registered ${geofences.size} hardware geofences!")
                        }
                        .addOnFailureListener { error ->
                            Log.e(TAG, "Failed registering geofences: ${error.message}")
                        }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception syncing geofences: ${e.message}", e)
            }
        }
    }
}
