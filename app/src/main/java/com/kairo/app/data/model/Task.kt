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
    val dueTime: String? = null,
    val location: String? = null,
    val attachmentName: String? = null,
    val isCompleted: Boolean = false,
    val section: TaskSection = TaskSection.TODAY,
    val createdAt: Long = System.currentTimeMillis()
)
