package com.kairo.app.data.remote

import com.kairo.app.data.model.Priority
import com.kairo.app.data.model.Task
import com.kairo.app.data.model.TaskSection
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TaskDto(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("notes") val notes: String? = null,
    @SerialName("priority") val priority: String = "MEDIUM",
    @SerialName("due_date") val dueDate: String = "Today",
    @SerialName("due_time") val dueTime: String? = null,
    @SerialName("location") val location: String? = null,
    @SerialName("attachment_name") val attachmentName: String? = null,
    @SerialName("is_completed") val isCompleted: Boolean = false,
    @SerialName("section") val section: String = "TODAY",
    @SerialName("created_at") val createdAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): Task {
        return Task(
            id = id,
            title = title,
            notes = notes,
            priority = runCatching { Priority.valueOf(priority) }.getOrDefault(Priority.MEDIUM),
            dueDate = dueDate,
            dueTime = dueTime,
            location = location,
            attachmentName = attachmentName,
            isCompleted = isCompleted,
            section = runCatching { TaskSection.valueOf(section) }.getOrDefault(TaskSection.TODAY),
            createdAt = createdAt
        )
    }

    companion object {
        fun fromDomain(task: Task): TaskDto {
            return TaskDto(
                id = task.id,
                title = task.title,
                notes = task.notes,
                priority = task.priority.name,
                dueDate = task.dueDate,
                dueTime = task.dueTime,
                location = task.location,
                attachmentName = task.attachmentName,
                isCompleted = task.isCompleted,
                section = task.section.name,
                createdAt = task.createdAt
            )
        }
    }
}
