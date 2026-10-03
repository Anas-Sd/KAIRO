package com.kairo.app.alarm

import android.content.Context

/**
 * Backward compatibility subclass for [com.kairo.app.feature.tasks.alarm.AlarmRingingService].
 * The entire Alarm & Ringing engine now lives in `com.kairo.app.feature.tasks.alarm`.
 */
class AlarmRingingService : com.kairo.app.feature.tasks.alarm.AlarmRingingService() {

    companion object {
        const val CHANNEL_ID = com.kairo.app.feature.tasks.alarm.AlarmRingingService.CHANNEL_ID
        const val NOTIFICATION_ID = com.kairo.app.feature.tasks.alarm.AlarmRingingService.NOTIFICATION_ID
        const val ACTION_START_RINGING = com.kairo.app.feature.tasks.alarm.AlarmRingingService.ACTION_START_RINGING
        const val ACTION_STOP_RINGING = com.kairo.app.feature.tasks.alarm.AlarmRingingService.ACTION_STOP_RINGING

        const val EXTRA_TASK_ID = com.kairo.app.feature.tasks.alarm.AlarmRingingService.EXTRA_TASK_ID
        const val EXTRA_TASK_TITLE = com.kairo.app.feature.tasks.alarm.AlarmRingingService.EXTRA_TASK_TITLE
        const val EXTRA_TASK_NOTES = com.kairo.app.feature.tasks.alarm.AlarmRingingService.EXTRA_TASK_NOTES
        const val EXTRA_TASK_PRIORITY = com.kairo.app.feature.tasks.alarm.AlarmRingingService.EXTRA_TASK_PRIORITY
        const val EXTRA_TASK_DUE_DATE = com.kairo.app.feature.tasks.alarm.AlarmRingingService.EXTRA_TASK_DUE_DATE
        const val EXTRA_TASK_DUE_TIME = com.kairo.app.feature.tasks.alarm.AlarmRingingService.EXTRA_TASK_DUE_TIME
        const val EXTRA_TASK_LOCATION = com.kairo.app.feature.tasks.alarm.AlarmRingingService.EXTRA_TASK_LOCATION
        const val EXTRA_TASK_ATTACHMENT = com.kairo.app.feature.tasks.alarm.AlarmRingingService.EXTRA_TASK_ATTACHMENT
        const val EXTRA_ALARM_URI = com.kairo.app.feature.tasks.alarm.AlarmRingingService.EXTRA_ALARM_URI

        fun stop(context: Context) {
            com.kairo.app.feature.tasks.alarm.AlarmRingingService.stop(context)
        }
    }
}
