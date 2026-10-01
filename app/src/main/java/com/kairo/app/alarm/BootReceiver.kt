package com.kairo.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.kairo.app.data.repository.TaskRepository

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            Log.i("BootReceiver", "Device rebooted. Rescheduling active task alarms...")
            val repository = TaskRepository()
            val tasks = repository.getAllTasks()
            val now = System.currentTimeMillis()

            tasks.filter { !it.isCompleted }.forEach { task ->
                val trigger = AlarmScheduler.calculateTriggerMillis(task.dueDate, task.dueTime, task.dueDateMillis)
                if (trigger != null && trigger > now) {
                    AlarmScheduler.scheduleAlarm(context, task, trigger)
                    Log.d("BootReceiver", "Rescheduled alarm for '${task.title}' at $trigger")
                }
            }
        }
    }
}
