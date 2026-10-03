package com.kairo.app.feature.sync

import android.content.Context
import com.kairo.app.data.local.OfflineTaskItem
import com.kairo.app.data.model.Task
import kotlinx.coroutines.flow.StateFlow

/**
 * TaskSyncFacade
 *
 * The single public service window ("door") for the Cloud Sync & Remote Database Box.
 * The rest of the app (UI, Repositories, ViewModels) ONLY communicates
 * with synchronization and cloud storage through this facade.
 */
object TaskSyncFacade {

    val isOnline: StateFlow<Boolean>
        get() = SyncManager.isOnline

    val isSyncing: StateFlow<Boolean>
        get() = SyncManager.isSyncing

    val offlineTasks: StateFlow<List<OfflineTaskItem>>
        get() = SyncManager.offlineTasks

    val pendingSyncCount: StateFlow<Int>
        get() = SyncManager.pendingSyncCount

    var onSyncComplete: (() -> Unit)?
        get() = SyncManager.onSyncComplete
        set(value) {
            SyncManager.onSyncComplete = value
        }

    fun initialize(context: Context) {
        SyncManager.initialize(context)
    }

    fun triggerSync() {
        SyncManager.triggerSync()
    }

    suspend fun performSync(forceCheckNetwork: Boolean = false): Result<Int> {
        return SyncManager.performSync(forceCheckNetwork)
    }

    fun refreshOfflineQueueStatus() {
        SyncManager.refreshOfflineQueueStatus()
    }

    fun checkCurrentOnlineStatus(context: Context): Boolean {
        return SyncManager.checkCurrentOnlineStatus(context)
    }

    // Remote operations
    suspend fun validateAccessCode(code: String): Result<AccessCodeDto> {
        return SupabaseClient.validateAccessCode(code)
    }

    suspend fun rotateAccessCode(oldCode: String, newCode: String): Result<Unit> {
        return SupabaseClient.rotateAccessCode(oldCode, newCode)
    }

    suspend fun deleteAllUserData(userCode: String): Result<Unit> {
        return SupabaseClient.deleteAllUserData(userCode)
    }

    suspend fun deleteUserCodeAndData(userCode: String): Result<Unit> {
        return SupabaseClient.deleteUserCodeAndData(userCode)
    }
}
