package com.kairo.app.data.remote

import com.kairo.app.data.model.ContextType
import com.kairo.app.data.model.Priority
import com.kairo.app.data.model.Task
import com.kairo.app.data.model.TaskCategory
import com.kairo.app.data.model.TaskSection
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TaskDto(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String? = null,
    @SerialName("priority") val priority: String = "MEDIUM",
    @SerialName("category") val category: String = "WORK",
    @SerialName("secondary_tag") val secondaryTag: String? = null,
    @SerialName("due_display") val dueDisplay: String = "Today",
    @SerialName("context_label") val contextLabel: String? = null,
    @SerialName("context_type") val contextType: String = "NONE",
    @SerialName("is_completed") val isCompleted: Boolean = false,
    @SerialName("section") val section: String = "TODAY",
    @SerialName("created_at") val createdAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): Task {
        val parsedContextType = runCatching { ContextType.valueOf(contextType) }.getOrDefault(ContextType.NONE)
        val locationValue = if (parsedContextType == ContextType.LOCATION) contextLabel else null
        val attachmentValue = if (parsedContextType == ContextType.ATTACHMENT) contextLabel else null

        return Task(
            id = id,
            title = title,
            notes = description,
            priority = runCatching { Priority.valueOf(priority) }.getOrDefault(Priority.MEDIUM),
            category = runCatching { TaskCategory.valueOf(category) }.getOrDefault(TaskCategory.WORK),
            secondaryTag = secondaryTag,
            dueDisplay = dueDisplay,
            dueTime = if (dueDisplay.contains(":")) dueDisplay.substringAfter(", ").trim() else null,
            location = locationValue,
            attachmentName = attachmentValue,
            contextLabel = contextLabel,
            contextType = parsedContextType,
            isCompleted = isCompleted,
            section = runCatching { TaskSection.valueOf(section) }.getOrDefault(TaskSection.TODAY),
            createdAt = createdAt
        )
    }

    companion object {
        fun fromDomain(task: Task): TaskDto {
            val effContextLabel = task.location ?: task.attachmentName ?: task.contextLabel
            val effContextType = when {
                task.location != null -> ContextType.LOCATION
                task.attachmentName != null -> ContextType.ATTACHMENT
                else -> task.contextType
            }

            return TaskDto(
                id = task.id,
                title = task.title,
                description = task.notes,
                priority = task.priority.name,
                category = task.category.name,
                secondaryTag = task.secondaryTag,
                dueDisplay = if (task.dueTime != null && !task.dueDisplay.contains(task.dueTime)) {
                    "${task.dueDisplay}, ${task.dueTime}"
                } else {
                    task.dueDisplay
                },
                contextLabel = effContextLabel,
                contextType = effContextType.name,
                isCompleted = task.isCompleted,
                section = task.section.name,
                createdAt = task.createdAt
            )
        }
    }
}
