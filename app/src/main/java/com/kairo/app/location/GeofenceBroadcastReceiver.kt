package com.kairo.app.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "GeofenceReceiver"
        private const val PREFS_COOLDOWN = "kairo_geofence_cooldown"
        private const val COOLDOWN_DURATION_MS = 2 * 60 * 60 * 1000L // 2 Hours cooldown

        fun shouldAlertLocation(context: Context, locationKey: String): Boolean {
            val prefs = context.getSharedPreferences(PREFS_COOLDOWN, Context.MODE_PRIVATE)
            val lastAlertTime = prefs.getLong(locationKey, 0L)
            val now = System.currentTimeMillis()
            return (now - lastAlertTime) >= COOLDOWN_DURATION_MS
        }

        fun recordAlertTriggered(context: Context, locationKey: String) {
            val prefs = context.getSharedPreferences(PREFS_COOLDOWN, Context.MODE_PRIVATE)
            prefs.edit().putLong(locationKey, System.currentTimeMillis()).apply()
        }

        fun clearCooldown(context: Context, locationKey: String) {
            val prefs = context.getSharedPreferences(PREFS_COOLDOWN, Context.MODE_PRIVATE)
            prefs.edit().remove(locationKey).apply()
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent == null) {
            Log.e(TAG, "Null geofencing event received")
            return
        }

        if (geofencingEvent.hasError()) {
            Log.e(TAG, "Geofencing error code: ${geofencingEvent.errorCode}")
            return
        }

        val transitionType = geofencingEvent.geofenceTransition
        val isRelevantTransition = transitionType == Geofence.GEOFENCE_TRANSITION_ENTER ||
                transitionType == Geofence.GEOFENCE_TRANSITION_DWELL

        if (!isRelevantTransition) {
            Log.d(TAG, "Ignoring transition type: $transitionType")
            return
        }

        val triggeringGeofences = geofencingEvent.triggeringGeofences.orEmpty()
        Log.d(TAG, "Received geofence transition ($transitionType) for ${triggeringGeofences.size} geofences")

        val triggeringLocation = geofencingEvent.triggeringLocation

        val pendingResult = goAsync()
        scope.launch {
            try {
                for (geofence in triggeringGeofences) {
                    val requestId = geofence.requestId
                    // RequestId format: "LOCATION_NAME|LAT|LNG"
                    val parts = requestId.split("|")
                    val locationName = parts.getOrNull(0) ?: "Saved Location"
                    val lat = parts.getOrNull(1)?.toDoubleOrNull() ?: triggeringLocation?.latitude ?: 0.0
                    val lng = parts.getOrNull(2)?.toDoubleOrNull() ?: triggeringLocation?.longitude ?: 0.0

                    val locationKey = "cooldown_${locationName.lowercase().trim()}"
                    if (!shouldAlertLocation(context, locationKey)) {
                        Log.d(TAG, "2-hour cooldown is active for '$locationName'. Skipping notification.")
                        continue
                    }

                    // Record cooldown trigger
                    recordAlertTriggered(context, locationKey)

                    // Trigger proactive AI notification
                    LocationAiNotifier.notifyUserForLocation(
                        context = context,
                        locationName = locationName,
                        latitude = lat,
                        longitude = lng
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing geofences: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
