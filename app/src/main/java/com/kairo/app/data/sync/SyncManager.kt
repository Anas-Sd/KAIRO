package com.kairo.app.data.sync

import android.content.Context
import com.kairo.app.data.local.OfflineTaskItem
import com.kairo.app.feature.sync.TaskSyncFacade
import kotlinx.coroutines.flow.StateFlow

/**
 * Backward compatibility forwarder for [com.kairo.app.feature.sync.TaskSyncFacade].
 * All synchronization logic now lives in the Cloud Sync feature box `com.kairo.app.feature.sync`.
 */
object SyncManager {

    val isOnline: StateFlow<Boolean>
        get() = TaskSyncFacade.isOnline

    val isSyncing: StateFlow<Boolean>
        get() = TaskSyncFacade.isSyncing

    val offlineTasks: StateFlow<List<OfflineTaskItem>>
        get() = TaskSyncFacade.offlineTasks

    val pendingSyncCount: StateFlow<Int>
        get() = TaskSyncFacade.pendingSyncCount

    var onSyncComplete: (() -> Unit)?
        get() = TaskSyncFacade.onSyncComplete
        set(value) {
            TaskSyncFacade.onSyncComplete = value
        }

    fun initialize(context: Context) = TaskSyncFacade.initialize(context)

    fun triggerSync() = TaskSyncFacade.triggerSync()

    suspend fun performSync(forceCheckNetwork: Boolean = false): Result<Int> =
        TaskSyncFacade.performSync(forceCheckNetwork)

    fun refreshOfflineQueueStatus() = TaskSyncFacade.refreshOfflineQueueStatus()

    fun checkCurrentOnlineStatus(context: Context): Boolean =
        TaskSyncFacade.checkCurrentOnlineStatus(context)
}
