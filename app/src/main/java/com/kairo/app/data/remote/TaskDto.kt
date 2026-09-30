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
    fun toDomain(): Task = Task(
        id = id,
        title = title,
        description = description,
        priority = runCatching { Priority.valueOf(priority) }.getOrDefault(Priority.MEDIUM),
        category = runCatching { TaskCategory.valueOf(category) }.getOrDefault(TaskCategory.WORK),
        secondaryTag = secondaryTag,
        dueDisplay = dueDisplay,
        contextLabel = contextLabel,
        contextType = runCatching { ContextType.valueOf(contextType) }.getOrDefault(ContextType.NONE),
        isCompleted = isCompleted,
        section = runCatching { TaskSection.valueOf(section) }.getOrDefault(TaskSection.TODAY),
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(task: Task): TaskDto = TaskDto(
            id = task.id,
            title = task.title,
            description = task.description,
            priority = task.priority.name,
            category = task.category.name,
            secondaryTag = task.secondaryTag,
            dueDisplay = task.dueDisplay,
            contextLabel = task.contextLabel,
            contextType = task.contextType.name,
            isCompleted = task.isCompleted,
            section = task.section.name,
            createdAt = task.createdAt
        )
    }
}
