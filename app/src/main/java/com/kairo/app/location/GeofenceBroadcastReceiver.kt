package com.kairo.app.location

/**
 * Backward compatibility subclass for [com.kairo.app.feature.tasks.location.GeofenceBroadcastReceiver].
 * The entire Location engine now lives in its encapsulated container `com.kairo.app.feature.tasks.location`.
 */
class GeofenceBroadcastReceiver : com.kairo.app.feature.tasks.location.GeofenceBroadcastReceiver() {
    companion object {
        fun shouldAlertLocation(context: android.content.Context, locationKey: String): Boolean =
            com.kairo.app.feature.tasks.location.GeofenceBroadcastReceiver.shouldAlertLocation(context, locationKey)

        fun recordAlertTriggered(context: android.content.Context, locationKey: String) =
            com.kairo.app.feature.tasks.location.GeofenceBroadcastReceiver.recordAlertTriggered(context, locationKey)

        fun clearCooldown(context: android.content.Context, locationKey: String) =
            com.kairo.app.feature.tasks.location.GeofenceBroadcastReceiver.clearCooldown(context, locationKey)
    }
}
