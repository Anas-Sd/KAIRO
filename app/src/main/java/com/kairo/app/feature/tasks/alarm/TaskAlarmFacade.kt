package com.kairo.app.feature.tasks.alarm

import android.content.Context
import com.kairo.app.data.model.Task

/**
 * TaskAlarmFacade
 *
 * The single public service window ("door") for the Task Alarm Box.
 * The rest of the app (Task CRUD, UI Cards, Repositories, ViewModels) ONLY communicates
 * with the Alarm & Ringing feature through this facade.
 */
object TaskAlarmFacade {

    /**
     * Schedule an exact alarm for a task using its due date and due time.
     */
    fun scheduleAlarm(context: Context, task: Task, overrideTriggerMillis: Long? = null) {
        AlarmScheduler.scheduleAlarm(context, task, overrideTriggerMillis)
    }

    /**
     * Cancel any active alarm scheduled for a given task ID.
     */
    fun cancelAlarm(context: Context, taskId: String) {
        AlarmScheduler.cancelAlarm(context, taskId)
    }

    /**
     * Calculate trigger timestamp in epoch milliseconds from human-readable due date/time strings.
     */
    fun calculateTriggerMillis(dueDate: String, dueTime: String?, dueDateMillis: Long?): Long? {
        return AlarmScheduler.calculateTriggerMillis(dueDate, dueTime, dueDateMillis)
    }

    /**
     * Stop active alarm ringing audio, vibration, and clear foreground notification.
     */
    fun stopAlarmRinging(context: Context) {
        AlarmRingingService.stop(context)
    }
}
