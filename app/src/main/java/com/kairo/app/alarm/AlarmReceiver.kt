package com.kairo.app.alarm

/**
 * Backward compatibility subclass for [com.kairo.app.feature.tasks.alarm.AlarmReceiver].
 * The entire Alarm & Ringing engine now lives in `com.kairo.app.feature.tasks.alarm`.
 */
class AlarmReceiver : com.kairo.app.feature.tasks.alarm.AlarmReceiver() {

    companion object {
        const val ACTION_TRIGGER_ALARM = com.kairo.app.feature.tasks.alarm.AlarmReceiver.ACTION_TRIGGER_ALARM
        const val ACTION_COMPLETE_ALARM = com.kairo.app.feature.tasks.alarm.AlarmReceiver.ACTION_COMPLETE_ALARM
        const val ACTION_SNOOZE_ALARM = com.kairo.app.feature.tasks.alarm.AlarmReceiver.ACTION_SNOOZE_ALARM
        const val ACTION_DISMISS_ALARM = com.kairo.app.feature.tasks.alarm.AlarmReceiver.ACTION_DISMISS_ALARM

        const val EXTRA_TASK_ID = com.kairo.app.feature.tasks.alarm.AlarmReceiver.EXTRA_TASK_ID
        const val EXTRA_TASK_TITLE = com.kairo.app.feature.tasks.alarm.AlarmReceiver.EXTRA_TASK_TITLE
        const val EXTRA_TASK_NOTES = com.kairo.app.feature.tasks.alarm.AlarmReceiver.EXTRA_TASK_NOTES
        const val EXTRA_TASK_PRIORITY = com.kairo.app.feature.tasks.alarm.AlarmReceiver.EXTRA_TASK_PRIORITY
        const val EXTRA_TASK_DUE_DATE = com.kairo.app.feature.tasks.alarm.AlarmReceiver.EXTRA_TASK_DUE_DATE
        const val EXTRA_TASK_DUE_TIME = com.kairo.app.feature.tasks.alarm.AlarmReceiver.EXTRA_TASK_DUE_TIME
        const val EXTRA_TASK_LOCATION = com.kairo.app.feature.tasks.alarm.AlarmReceiver.EXTRA_TASK_LOCATION
        const val EXTRA_TASK_ATTACHMENT = com.kairo.app.feature.tasks.alarm.AlarmReceiver.EXTRA_TASK_ATTACHMENT
        const val EXTRA_SNOOZE_MINUTES = com.kairo.app.feature.tasks.alarm.AlarmReceiver.EXTRA_SNOOZE_MINUTES
        const val EXTRA_ALARM_URI = com.kairo.app.feature.tasks.alarm.AlarmReceiver.EXTRA_ALARM_URI
    }
}
