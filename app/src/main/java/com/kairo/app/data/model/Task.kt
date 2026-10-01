package com.kairo.app.data.model

import java.util.UUID

enum class Priority(val label: String) {
    LOW("Low"),
    MEDIUM("Med"),
    HIGH("High"),
    URGENT("Urgent")
}

enum class TaskSection(val title: String) {
    OVERDUE("Overdue"),
    TODAY("Today"),
    UPCOMING("Upcoming"),
    COMPLETED("Completed")
}

data class Task(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val notes: String? = null,
    val priority: Priority = Priority.MEDIUM,
    val dueDate: String = "Today",
    val dueDateMillis: Long? = null,
    val dueTime: String? = null,
    val location: String? = null,
    val attachmentName: String? = null,
    val attachmentUri: String? = null,
    val isCompleted: Boolean = false,
    val section: TaskSection = TaskSection.TODAY,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val alarmToneUri: String? = null,
    val alarmToneTitle: String? = null,
    val repeatType: String? = null,
    val repeatDays: String? = null,
    val repeatDates: String? = null
)
