package com.kairo.app.ui.screens.alarm

/**
 * Backward compatibility subclass for [com.kairo.app.feature.tasks.alarm.AlarmActivity].
 * The entire Alarm & Ringing engine now lives in its encapsulated container `com.kairo.app.feature.tasks.alarm`.
 */
class AlarmActivity : com.kairo.app.feature.tasks.alarm.AlarmActivity() {
    companion object {
        const val ACTION_FINISH_ALARM_OVERLAY = com.kairo.app.feature.tasks.alarm.AlarmActivity.ACTION_FINISH_ALARM_OVERLAY
    }
}
