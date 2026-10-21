package com.kairo.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.kairo.app.data.repository.TaskRepository
import com.kairo.app.feature.tasks.alarm.AlarmScheduler
import com.kairo.app.feature.tasks.alarm.DirectBootAlarmStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i("BootReceiver", "BootReceiver received action: $action")

        val isBoot = action == Intent.ACTION_BOOT_COMPLETED ||
                action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
                action == "android.intent.action.QUICKBOOT_POWERON" ||
                action == "com.htc.intent.action.QUICKBOOT_POWERON" ||
                action == Intent.ACTION_MY_PACKAGE_REPLACED ||
                action == Intent.ACTION_TIME_CHANGED ||
                action == Intent.ACTION_TIMEZONE_CHANGED

        if (!isBoot) return

        val isUnlocked = DirectBootAlarmStore.isUserUnlocked(context)
        Log.i("BootReceiver", "Device boot/time event received (action: $action, isUserUnlocked: $isUnlocked). Rescheduling active task alarms...")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val now = System.currentTimeMillis()

                if (!isUnlocked) {
                    // DIRECT BOOT MODE (Device locked after reboot, e.g. scheduled power-on while user is sleeping)
                    // CE storage is locked. Reschedule strictly using Device-Protected (DE) storage!
                    val activeAlarms = DirectBootAlarmStore.getAllActiveAlarms(context)
                    Log.i("BootReceiver", "Direct Boot mode active. Found ${activeAlarms.size} cached alarms in Device-Protected storage.")

                    activeAlarms.forEach { item ->
                        if (item.triggerMillis > now) {
                            AlarmScheduler.scheduleAlarm(context, item.toTask(), item.triggerMillis)
                            Log.i("BootReceiver", "Rescheduled Direct Boot alarm for '${item.title}' at ${item.triggerMillis}")
                        }
                    }
                } else {
                    // USER UNLOCKED MODE:
                    // Process any actions deferred while device was locked in Direct Boot
                    DirectBootAlarmStore.processPendingActions(context)

                    // CE storage is accessible. Reschedule from LocalTaskDatabase / TaskRepository
                    val userCode = runCatching { com.kairo.app.data.auth.AuthManager.getUserCode() }.getOrNull()
                    val tasks = TaskRepository.getAllTasks().ifEmpty {
                        runCatching {
                            com.kairo.app.data.local.LocalTaskDatabase.getInstance(context).getTasksForUser(userCode)
                        }.getOrDefault(emptyList())
                    }

                    tasks.filter { !it.isCompleted }.forEach { task ->
                        val trigger = AlarmScheduler.calculateTriggerMillis(task.dueDate, task.dueTime, task.dueDateMillis)
                        if (trigger != null && trigger > now) {
                            AlarmScheduler.scheduleAlarm(context, task, trigger)
                            Log.d("BootReceiver", "Rescheduled alarm for '${task.title}' at $trigger")
                        }
                    }

                    // Keep Device-Protected storage in sync
                    DirectBootAlarmStore.syncFromTasks(context, tasks)

                    // Resync location geofences
                    runCatching {
                        com.kairo.app.feature.tasks.location.TaskLocationFacade.syncGeofencesWithActiveTasks(context)
                    }
                }
            } catch (e: Exception) {
                Log.e("BootReceiver", "Error rescheduling alarms on boot: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
