package com.kairo.app.alarm

import android.content.Context
import com.kairo.app.data.model.Task
import com.kairo.app.feature.tasks.alarm.TaskAlarmFacade

/**
 * Backward compatibility forwarder.
 * Outside callers are encouraged to use [com.kairo.app.feature.tasks.alarm.TaskAlarmFacade].
 */
object AlarmScheduler {

    fun calculateTriggerMillis(dueDate: String, dueTime: String?, dueDateMillis: Long?): Long? =
        TaskAlarmFacade.calculateTriggerMillis(dueDate, dueTime, dueDateMillis)

    fun scheduleAlarm(context: Context, task: Task, overrideTriggerMillis: Long? = null) =
        TaskAlarmFacade.scheduleAlarm(context, task, overrideTriggerMillis)

    fun cancelAlarm(context: Context, taskId: String) =
        TaskAlarmFacade.cancelAlarm(context, taskId)
}
