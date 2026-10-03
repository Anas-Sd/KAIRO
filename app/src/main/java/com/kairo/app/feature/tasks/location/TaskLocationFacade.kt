package com.kairo.app.feature.tasks.location

import android.content.Context
import androidx.compose.runtime.Composable
import com.kairo.app.data.model.Task

/**
 * TaskLocationFacade
 *
 * The single public service window ("door") for the Task Location Box.
 * The rest of the app (Task CRUD, UI Cards, Repositories) ONLY communicates
 * with the Location feature through this facade.
 */
object TaskLocationFacade {

    /**
     * Synchronize hardware geofences with active (uncompleted) tasks having location coordinates.
     */
    fun syncGeofencesWithActiveTasks(context: Context, forceImmediateCheck: Boolean = false) {
        GeofenceManager.syncGeofencesWithActiveTasks(context, forceImmediateCheck)
    }

    /**
     * Get the device's current GPS location coordinates (Latitude, Longitude), or null if unavailable.
     */
    suspend fun getUserLocation(context: Context): Pair<Double, Double>? {
        return LocationSearchHelper.getUserLocation(context)
    }

    /**
     * Calculate direct distance in meters between user location and destination coordinates.
     */
    fun calculateDistance(userLat: Double, userLng: Double, destLat: Double, destLng: Double): Float {
        return LocationSearchHelper.calculateDistance(userLat, userLng, destLat, destLng)
    }

    /**
     * Format distance into human-friendly string (e.g. "450 m" or "2.3 km").
     */
    fun formatDistance(meters: Float?): String? {
        return LocationSearchHelper.formatDistance(meters)
    }

    /**
     * Search locations by query using native geocoder and OpenStreetMap bias.
     */
    suspend fun searchLocations(context: Context, query: String): List<LocationSearchResult> {
        return LocationSearchHelper.searchLocations(context, query)
    }

    /**
     * Check if location permission is granted.
     */
    fun hasLocationPermission(context: Context): Boolean {
        return GeofenceManager.hasLocationPermission(context)
    }
}
