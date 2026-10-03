package com.kairo.app.location

import android.content.Context

/**
 * Backward compatibility forwarder.
 */
object LocationAiNotifier {
    suspend fun notifyUserForLocation(
        context: Context,
        locationName: String,
        latitude: Double,
        longitude: Double
    ) {
        com.kairo.app.feature.tasks.location.LocationAiNotifier.notifyUserForLocation(
            context, locationName, latitude, longitude
        )
    }
}
