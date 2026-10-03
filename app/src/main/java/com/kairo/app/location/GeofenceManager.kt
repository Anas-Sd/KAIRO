package com.kairo.app.location

import android.content.Context
import com.kairo.app.feature.tasks.location.TaskLocationFacade

/**
 * Backward compatibility forwarder.
 * Outside callers are encouraged to use [com.kairo.app.feature.tasks.location.TaskLocationFacade].
 */
object GeofenceManager {
    fun hasLocationPermission(context: Context): Boolean =
        TaskLocationFacade.hasLocationPermission(context)

    fun syncGeofencesWithActiveTasks(context: Context, forceImmediateCheck: Boolean = false) =
        TaskLocationFacade.syncGeofencesWithActiveTasks(context, forceImmediateCheck)
}
