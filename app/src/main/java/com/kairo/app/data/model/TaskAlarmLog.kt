package com.kairo.app.data.model

data class TaskAlarmLog(
    val id: Long = 0,
    val taskId: String,
    val action: String, // "RING", "COMPLETED", "SNOOZED", "DISMISSED", "POSTPONED", "RESTORED"
    val details: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
