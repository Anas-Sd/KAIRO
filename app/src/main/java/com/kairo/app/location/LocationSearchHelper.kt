package com.kairo.app.location

import android.content.Context
import com.kairo.app.feature.tasks.location.TaskLocationFacade

typealias LocationSearchResult = com.kairo.app.feature.tasks.location.LocationSearchResult

/**
 * Backward compatibility forwarder.
 * Outside callers are encouraged to use [com.kairo.app.feature.tasks.location.TaskLocationFacade].
 */
object LocationSearchHelper {
    fun calculateDistance(userLat: Double, userLng: Double, destLat: Double, destLng: Double): Float =
        TaskLocationFacade.calculateDistance(userLat, userLng, destLat, destLng)

    fun formatDistance(meters: Float?): String? =
        TaskLocationFacade.formatDistance(meters)

    suspend fun getUserLocation(context: Context): Pair<Double, Double>? =
        TaskLocationFacade.getUserLocation(context)

    suspend fun searchLocations(context: Context, query: String): List<LocationSearchResult> =
        TaskLocationFacade.searchLocations(context, query)
}
