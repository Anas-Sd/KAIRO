package com.kairo.app.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.kairo.app.KairoApplication
import com.kairo.app.data.auth.AuthManager
import com.kairo.app.data.local.LocalTaskDatabase
import com.kairo.app.data.remote.SupabaseClient
import com.kairo.app.data.remote.TaskDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

object SyncManager {

    private const val TAG = "SyncManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    // Callback invoked when local database has been updated by remote changes
    var onSyncComplete: (() -> Unit)? = null

    fun initialize(context: Context) {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (connectivityManager != null) {
                val activeNetwork = connectivityManager.activeNetwork
                val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
                val initialOnline = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                _isOnline.value = initialOnline

                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()

                connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        Log.d(TAG, "Network became available. Triggering background sync.")
                        _isOnline.value = true
                        triggerSync()
                    }

                    override fun onLost(network: Network) {
                        Log.d(TAG, "Network connection lost.")
                        _isOnline.value = false
                    }
                })
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error initializing ConnectivityManager callback: ${e.message}", e)
        }
    }

    fun triggerSync() {
        scope.launch {
            performSync()
        }
    }

    suspend fun performSync(): Result<Unit> = syncMutex.withLock {
        val userCode = AuthManager.getUserCode()
        if (userCode.isNullOrBlank()) {
            return Result.success(Unit)
        }

        if (!_isOnline.value) {
            Log.d(TAG, "Device is offline. Skipping remote sync.")
            return Result.failure(Exception("Device is offline"))
        }

        _isSyncing.value = true
        val localDb = LocalTaskDatabase.getInstance(KairoApplication.instance)

        try {
            // ==========================================
            // 1. OUTBOUND SYNC: Drain Local Queue
            // ==========================================
            val pendingActions = localDb.getPendingSyncActions(userCode)
            Log.d(TAG, "Processing ${pendingActions.size} pending outbound sync actions for $userCode")

            for (action in pendingActions) {
                try {
                    when (action.action) {
                        "INSERT", "UPDATE" -> {
                            val dto = if (!action.payloadJson.isNullOrBlank()) {
                                json.decodeFromString<TaskDto>(action.payloadJson)
                            } else {
                                val localTask = localDb.getTasksForUser(userCode).find { it.id == action.taskId }
                                if (localTask != null) TaskDto.fromDomain(localTask, userCode) else null
                            }

                            if (dto != null) {
                                if (action.action == "INSERT") {
                                    SupabaseClient.insertTask(dto).getOrThrow()
                                } else {
                                    SupabaseClient.updateTask(dto).getOrThrow()
                                }
                                localDb.updateSyncStatus(action.taskId, "SYNCED")
                            }
                            localDb.removeSyncAction(action.id)
                        }

                        "DELETE" -> {
                            SupabaseClient.deleteTask(action.taskId).getOrThrow()
                            localDb.removeSyncAction(action.id)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync action ${action.id} (${action.action}): ${e.message}")
                    // Keep in queue to retry on next sync cycle
                }
            }

            // ==========================================
            // 2. INBOUND SYNC: Pull Cloud Changes
            // ==========================================
            val remoteResult = SupabaseClient.getTasks(userCode)
            remoteResult.onSuccess { remoteList ->
                val domainTasks = remoteList.map { it.toDomain() }
                localDb.mergeRemoteTasks(domainTasks, userCode)
                Log.d(TAG, "Successfully synced ${domainTasks.size} remote tasks to local database.")
                onSyncComplete?.invoke()
            }.onFailure { error ->
                Log.e(TAG, "Failed to pull remote tasks: ${error.message}")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error during sync cycle: ${e.message}", e)
            Result.failure(e)
        } finally {
            _isSyncing.value = false
        }
    }
}
