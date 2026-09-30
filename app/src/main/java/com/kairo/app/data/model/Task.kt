package com.kairo.app.data.model

import java.util.UUID

enum class Priority(val label: String) {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High"),
    URGENT("Urgent")
}

enum class TaskCategory(val label: String) {
    WORK("Work"),
    FINANCE("Finance"),
    STRATEGY("Strategy"),
    DESIGN("Design"),
    HABITS("Habits"),
    LEGAL("Legal"),
    PERSONAL("Personal")
}

enum class ContextType {
    MEETING,
    LOCATION,
    ATTACHMENT,
    NONE
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
    val description: String? = null,
    val priority: Priority = Priority.MEDIUM,
    val category: TaskCategory = TaskCategory.WORK,
    val secondaryTag: String? = null,
    val dueDisplay: String = "Today",
    val contextLabel: String? = null,
    val contextType: ContextType = ContextType.NONE,
    val isCompleted: Boolean = false,
    val section: TaskSection = TaskSection.TODAY,
    val createdAt: Long = System.currentTimeMillis()
)
